package com.viraplay.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.viraplay.shared.ContentType
import com.viraplay.shared.XtreamAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainSection(val label: String) {
    HOME("Início"),
    LIVE("Ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    FAVORITES("Favoritos")
}

private sealed interface Overlay {
    data class Player(val item: CatalogItem, val startPosition: Long) : Overlay
    data class Series(val item: CatalogItem) : Overlay
    data object Support : Overlay
    data object Settings : Overlay
    data object Access : Overlay
}

@Composable
fun VPlayoApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val hardwareTv =
        uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature("android.software.leanback") ||
            context.packageManager.hasSystemFeature("android.hardware.type.television")
    val wideLandscape =
        configuration.screenWidthDp >= 840 &&
            configuration.screenWidthDp > configuration.screenHeightDp * 1.35f
    val isTv = hardwareTv || wideLandscape

    val identity = remember { DeviceIdentity(context) }
    val db = remember { CatalogDb(context) }
    val repository = remember { ContentRepository(context, db) }
    val uiStore = remember { UiStateStore(context) }
    val preferences = remember { PlaybackPreferences(context) }
    val updateManager = remember { UpdateManager(context) }
    val directStore = remember { DirectAccessStore(context) }
    val scope = rememberCoroutineScope()

    // Na TV sempre abre na Home. No celular preserva a seção anterior.
    var section by remember {
        mutableStateOf(
            if (isTv) MainSection.HOME
            else runCatching { MainSection.valueOf(uiStore.section()) }.getOrDefault(MainSection.HOME)
        )
    }

    var enabled by remember { mutableStateOf(uiStore.lastEnabled()) }
    // A primeira composição não deve abrir/contar o SQLite na thread principal.
    var status by remember { mutableStateOf("Abrindo VPlayo...") }
    var hasCatalog by remember { mutableStateOf<Boolean?>(null) }
    var accountInfo by remember { mutableStateOf<XtreamAccountInfo?>(null) }
    var syncing by remember { mutableStateOf(true) }
    var catalogVersion by remember { mutableIntStateOf(0) }
    var playbackVersion by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var resumeItem by remember { mutableStateOf<CatalogItem?>(null) }
    var protectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    var adultUnlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

suspend fun refresh(forceCatalog: Boolean) {
    val hadCatalog = withContext(Dispatchers.IO) { db.hasCatalog() }
    hasCatalog = hadCatalog
    if (!hadCatalog) syncing = true

    try {
        val platform = if (hardwareTv) "ANDROID_TV" else if (isTv) "ANDROID_LARGE" else "ANDROID_MOBILE"
        withContext(Dispatchers.IO) {
            repository.registerIfNeeded(identity, platform)
        }

        var direct = directStore.session()
        val providerSession = direct
        if (providerSession?.mode == DirectAccessMode.PROVIDER && directStore.shouldRefreshProvider()) {
            val refreshAttempt = withContext(Dispatchers.IO) {
                runCatching { ProviderAccessClient.rebuildWithCurrentDns(providerSession) }
            }
            refreshAttempt.onSuccess { refreshed ->
                directStore.updateProviderPlaylist(refreshed.provider.name, refreshed.playlistUrl)
                direct = directStore.session()
            }.onFailure { error ->
                directStore.markProviderChecked()
                val message = error.message.orEmpty()
                if (message.contains("404") || message.contains("409")) throw error
            }
        }

        val remoteUrl: String?
        if (direct != null) {
            enabled = true
            uiStore.setLastEnabled(true)
            remoteUrl = direct.playlistUrl

            if (direct.mode == DirectAccessMode.PROVIDER && directStore.shouldTouchProvider()) {
                direct.providerCode?.let { providerCode ->
                    withContext(Dispatchers.IO) {
                        ProviderAccessClient.touch(providerCode, identity.deviceId, platform)
                    }
                    directStore.markProviderTouched()
                }
            }
        } else {
            val cfg = withContext(Dispatchers.IO) { repository.config(identity, forceCatalog) }
            enabled = cfg.enabled
            uiStore.setLastEnabled(cfg.enabled)
            if (!cfg.enabled) {
                status = "Acesso por código ainda não ativado"
                return
            }
            remoteUrl = cfg.playlistUrl
        }

        if (remoteUrl.isNullOrBlank()) {
            status = "Escolha uma forma de acesso"
            return
        }

        accountInfo = withContext(Dispatchers.IO) { repository.accountInfo(remoteUrl) }
        ExpiryNotifier.notifyIfNeeded(context, accountInfo)

        val (cachedUrl, lastSync) = withContext(Dispatchers.IO) {
            db.getMeta("playlist_url") to db.getMetaLong("last_sync")
        }
        val stale = System.currentTimeMillis() - lastSync > 6L * 60L * 60L * 1000L
        val needsSync = forceCatalog || !hadCatalog || cachedUrl != remoteUrl || stale

        if (needsSync) {
            status = if (hadCatalog) {
                val localCount = withContext(Dispatchers.IO) { db.countAll() }
                "$localCount títulos • atualizando em segundo plano..."
            } else {
                "Preparando catálogo pela primeira vez..."
            }

            withContext(Dispatchers.IO) {
                repository.syncCatalog(remoteUrl) { progress ->
                    if (!hadCatalog) scope.launch { status = progress }
                }
            }
            hasCatalog = withContext(Dispatchers.IO) { db.hasCatalog() }
            catalogVersion += 1
        }

        val source = directStore.description()
        val finalCount = withContext(Dispatchers.IO) { db.countAll() }
        hasCatalog = true
        status = buildHeaderStatus(finalCount, accountInfo) + (source?.let { " • $it" } ?: "")
    } catch (e: Throwable) {
        val localCatalog = withContext(Dispatchers.IO) { db.hasCatalog() }
        hasCatalog = localCatalog
        enabled = false
        status = if (localCatalog) {
            val localCount = withContext(Dispatchers.IO) { db.countAll() }
            "$localCount títulos • conexão pendente"
        } else {
            val message = (e.message ?: "falha de conexão").replace('\n', ' ').take(120)
            "Falha: $message"
        }
    } finally {
        syncing = false
    }
}
    fun reallyOpen(item: CatalogItem) {
        if (item.type == ContentType.SERIES) {
            overlay = Overlay.Series(item)
            return
        }
        if (item.url.isNullOrBlank()) return

        if (item.type == ContentType.LIVE) {
            overlay = Overlay.Player(item, 0L)
            return
        }

        val (position, duration) = db.progress(item.itemKey)
        val resumable =
            position > 30_000L &&
                (duration <= 0L || position.toDouble() / duration.toDouble() < 0.95)

        if (resumable) {
            resumeItem = item.copy(progressMs = position, durationMs = duration)
        } else {
            overlay = Overlay.Player(item, 0L)
        }
    }

    fun openItem(item: CatalogItem) {
        if (preferences.parentalEnabled && isAdultContent(item) && !adultUnlocked) {
            protectedItem = item
            pinInput = ""
            pinError = null
            return
        }
        reallyOpen(item)
    }

    LaunchedEffect(Unit) {
        refresh(forceCatalog = false)
        while (true) {
            delay(10L * 60L * 1000L)
            refresh(forceCatalog = false)
        }
    }

    LaunchedEffect(Unit) {
        delay(2_500)
        updateInfo = withContext(Dispatchers.IO) {
            runCatching { updateManager.check() }.getOrNull()
        }
    }

    val accessNotice = ExpiryNotifier.notice(accountInfo)
    val adultAccess = adultUnlocked || !preferences.parentalEnabled

    MaterialTheme(colorScheme = VPlayoColors) {
        Box(Modifier.fillMaxSize().background(VpBg)) {
            // Não mantém a tela anterior viva por trás do player/configurações.
            // Em TV isso evita disputa de foco e evita o SurfaceView da prévia aparecer
            // como uma segunda imagem por cima do vídeo em tela cheia.
            if (overlay == null) when {
                hasCatalog == null -> FastBootScreen(status)
                hasCatalog == false -> AccessPortalScreen(
                    code = identity.pairingCode,
                    status = status,
                    loading = syncing,
                    isTv = isTv,
                    onRefresh = { scope.launch { refresh(true) } },
                    onSupport = { overlay = Overlay.Support },
                    onProviderLogin = { providerCode, username, password ->
                        runCatching {
                            val login = withContext(Dispatchers.IO) { ProviderAccessClient.login(providerCode, username, password) }
                            directStore.saveProvider(login.provider.code, login.provider.name, login.playlistUrl)
                            withContext(Dispatchers.IO) {
                                ProviderAccessClient.touch(
                                    login.provider.code,
                                    identity.deviceId,
                                    if (hardwareTv) "ANDROID_TV" else if (isTv) "ANDROID_LARGE" else "ANDROID_MOBILE"
                                )
                            }
                            directStore.markProviderTouched()
                            refresh(true)
                            login.provider.name
                        }
                    },
                    onDnsLogin = { dns, username, password ->
                        runCatching {
                            val playlist = withContext(Dispatchers.IO) { ProviderAccessClient.loginDirectDns(dns, username, password) }
                            val label = runCatching { java.net.URI(dns.trim().let { if (it.startsWith("http", true)) it else "http://$it" }).host }.getOrNull() ?: "Servidor"
                            directStore.saveDns(playlist, label)
                            refresh(true)
                            "Conectado ao servidor"
                        }
                    },
                    onM3uLogin = { playlist ->
                        runCatching {
                            val clean = playlist.trim()
                            require(clean.startsWith("http", true)) { "Cole uma URL válida iniciando com http:// ou https://" }
                            SourceResolver.xtreamFromPlaylist(clean)?.let {
                                val info = withContext(Dispatchers.IO) { repository.accountInfo(clean) }
                                require(info != null) { "Não foi possível validar esse acesso Xtream." }
                            }
                            directStore.saveM3u(clean)
                            refresh(true)
                            "Lista adicionada com sucesso"
                        }
                    },
                    onUseDeviceCode = {
                        directStore.clear()
                        refresh(true)
                    }
                )

                !enabled -> BlockedScreen(
                    code = identity.pairingCode,
                    onSupport = { overlay = Overlay.Support },
                    onRefresh = { scope.launch { refresh(false) } }
                )

                isTv -> TvShell(
                    db = db,
                    repository = repository,
                    section = section,
                    catalogVersion = catalogVersion,
                    playbackVersion = playbackVersion,
                    status = status,
                    accessNotice = accessNotice,
                    adultUnlocked = adultAccess,
                    onSection = {
                        section = it
                        uiStore.setSection(it.name)
                    },
                    onOpen = ::openItem,
                    onSupport = { overlay = Overlay.Support },
                    onSettings = { overlay = Overlay.Settings },
                    onRefresh = { scope.launch { refresh(true) } },
                    onChanged = { catalogVersion += 1 }
                )

                else -> MobileShell(
                    db = db,
                    repository = repository,
                    section = section,
                    catalogVersion = catalogVersion + playbackVersion,
                    status = status,
                    accessNotice = accessNotice,
                    adultUnlocked = adultAccess,
                    onSection = {
                        section = it
                        uiStore.setSection(it.name)
                    },
                    onOpen = ::openItem,
                    onSupport = { overlay = Overlay.Support },
                    onSettings = { overlay = Overlay.Settings },
                    onRefresh = { scope.launch { refresh(true) } },
                    onChanged = { catalogVersion += 1 }
                )
            }

            when (val current = overlay) {
                is Overlay.Player -> PlayerScreen(
                    item = current.item,
                    db = db,
                    isTv = isTv,
                    startPosition = current.startPosition,
                    onBack = {
                        if (current.item.type == ContentType.LIVE) {
                            section = MainSection.LIVE
                            uiStore.setSection(MainSection.LIVE.name)
                        } else {
                            playbackVersion += 1
                        }
                        overlay = null
                    }
                )

                is Overlay.Series -> SeriesDetailScreen(
                    parent = current.item,
                    db = db,
                    repository = repository,
                    isTv = isTv,
                    onBack = { overlay = null },
                    onPlay = ::openItem,
                    onChanged = { playbackVersion += 1 }
                )

                Overlay.Support -> SupportScreen(
                    code = identity.pairingCode,
                    isTv = isTv,
                    onBack = { overlay = null }
                )

                Overlay.Settings -> SettingsScreen(
                    code = identity.pairingCode,
                    status = status,
                    accessText = accountDisplay(accountInfo),
                    sourceText = directStore.description() ?: "Ativação por código",
                    isTv = isTv,
                    onBack = { overlay = null },
                    onSupport = { overlay = Overlay.Support },
                    onRefresh = { scope.launch { refresh(true) } },
                    onChangeAccess = { overlay = Overlay.Access },
                    onParentalUnlocked = { adultUnlocked = true }
                )

                Overlay.Access -> AccessPortalScreen(
                    code = identity.pairingCode,
                    status = status,
                    loading = syncing,
                    isTv = isTv,
                    onRefresh = { scope.launch { refresh(true) } },
                    onSupport = { overlay = Overlay.Support },
                    onProviderLogin = { providerCode, username, password ->
                        runCatching {
                            val login = withContext(Dispatchers.IO) { ProviderAccessClient.login(providerCode, username, password) }
                            directStore.saveProvider(login.provider.code, login.provider.name, login.playlistUrl)
                            withContext(Dispatchers.IO) { ProviderAccessClient.touch(login.provider.code, identity.deviceId, if (hardwareTv) "ANDROID_TV" else if (isTv) "ANDROID_LARGE" else "ANDROID_MOBILE") }
                            directStore.markProviderTouched()
                            refresh(true)
                            overlay = null
                            login.provider.name
                        }
                    },
                    onDnsLogin = { dns, username, password ->
                        runCatching {
                            val playlist = withContext(Dispatchers.IO) { ProviderAccessClient.loginDirectDns(dns, username, password) }
                            val label = runCatching { java.net.URI(dns.trim().let { if (it.startsWith("http", true)) it else "http://$it" }).host }.getOrNull() ?: "Servidor"
                            directStore.saveDns(playlist, label)
                            refresh(true)
                            "Conectado ao servidor"
                        }
                    },
                    onM3uLogin = { playlist ->
                        runCatching {
                            val clean = playlist.trim()
                            require(clean.startsWith("http", true)) { "Cole uma URL válida iniciando com http:// ou https://" }
                            SourceResolver.xtreamFromPlaylist(clean)?.let { require(withContext(Dispatchers.IO) { repository.accountInfo(clean) } != null) { "Não foi possível validar esse acesso Xtream." } }
                            directStore.saveM3u(clean)
                            refresh(true)
                            overlay = null
                            "Lista adicionada com sucesso"
                        }
                    },
                    onUseDeviceCode = {
                        directStore.clear()
                        refresh(true)
                        overlay = null
                    },
                    onClose = { overlay = null }
                )

                null -> Unit
            }

            resumeItem?.let { item ->
                AlertDialog(
                    onDismissRequest = { resumeItem = null },
                    title = { Text("Continuar assistindo?") },
                    text = { Text("Você parou em ${formatProgress(item.progressMs)}. Deseja continuar dali?") },
                    confirmButton = {
                        Button(onClick = {
                            resumeItem = null
                            overlay = Overlay.Player(item, item.progressMs)
                        }) { Text("Continuar") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            resumeItem = null
                            db.clearProgress(item.itemKey)
                            overlay = Overlay.Player(item, 0L)
                        }) { Text("Do início") }
                    }
                )
            }

            protectedItem?.let { item ->
                val creating = !preferences.hasPin()
                AlertDialog(
                    onDismissRequest = { protectedItem = null },
                    title = { Text(if (creating) "Criar PIN parental" else "Conteúdo protegido") },
                    text = {
                        Column {
                            Text(
                                if (creating) "Crie um PIN de 4 números para proteger conteúdo adulto."
                                else "Digite seu PIN de 4 números para continuar."
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    if (it.length <= 4 && it.all(Char::isDigit)) pinInput = it
                                },
                                label = { Text("PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            pinError?.let { Text(it, color = VpDanger) }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val ok =
                                if (creating) preferences.setPin(pinInput)
                                else preferences.verifyPin(pinInput)
                            if (ok) {
                                adultUnlocked = true
                                protectedItem = null
                                reallyOpen(item)
                            } else {
                                pinError = "PIN inválido. Use 4 números."
                            }
                        }) { Text(if (creating) "Criar e continuar" else "Desbloquear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { protectedItem = null }) { Text("Cancelar") }
                    }
                )
            }

            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = {
                        if (!info.mandatory && !updateBusy) updateInfo = null
                    },
                    title = { Text("Atualização VPlayo ${info.versionName}") },
                    text = {
                        Column {
                            Text(info.message)
                            if (info.mandatory) {
                                Spacer(Modifier.height(8.dp))
                                Text("Esta atualização é necessária.", color = VpCyan)
                            }
                            if (updateBusy) {
                                Spacer(Modifier.height(14.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Baixando atualização...", modifier = Modifier.padding(top = 8.dp))
                            }
                            updateError?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(it, color = VpDanger)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = !updateBusy,
                            onClick = {
                                updateBusy = true
                                updateError = null
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        runCatching { updateManager.download(info) }
                                    }
                                    file.onSuccess { apk ->
                                        updateBusy = false
                                        when (updateManager.launchInstaller(apk)) {
                                            InstallLaunchResult.STARTED -> updateInfo = null
                                            InstallLaunchResult.NEED_PERMISSION ->
                                                updateError = "Autorize a instalação e toque em Atualizar novamente."
                                        }
                                    }.onFailure { e ->
                                        updateBusy = false
                                        updateError = e.message ?: "Não foi possível baixar a atualização."
                                    }
                                }
                            }
                        ) { Text("Atualizar") }
                    },
                    dismissButton = {
                        if (!info.mandatory) {
                            TextButton(
                                enabled = !updateBusy,
                                onClick = { updateInfo = null }
                            ) { Text("Depois") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FastBootScreen(status: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(VpBg),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrandWordmark()
            CircularProgressIndicator(
                color = VpCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Text(status, color = VpMuted, fontSize = 11.sp)
        }
    }
}

private fun buildHeaderStatus(count: Int, account: XtreamAccountInfo?): String {
    val notice = ExpiryNotifier.notice(account)
    return if (notice != null) "$count títulos • $notice" else "$count títulos"
}

private fun accountDisplay(account: XtreamAccountInfo?): String? {
    val date = ExpiryNotifier.displayDate(account) ?: return null
    val notice = ExpiryNotifier.notice(account)
    return if (notice != null) "$notice • $date" else "Ativo até $date"
}
