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
}

@Composable
fun VPlayoApp() {
    val context = LocalContext.current
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTv =
        uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature("android.software.leanback") ||
            context.packageManager.hasSystemFeature("android.hardware.type.television")

    val identity = remember { DeviceIdentity(context) }
    val db = remember { CatalogDb(context) }
    val repository = remember { ContentRepository(context, db) }
    val uiStore = remember { UiStateStore(context) }
    val preferences = remember { PlaybackPreferences(context) }
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()

    // Na TV sempre abre na Home. No celular preserva a seção anterior.
    var section by remember {
        mutableStateOf(
            if (isTv) MainSection.HOME
            else runCatching { MainSection.valueOf(uiStore.section()) }.getOrDefault(MainSection.HOME)
        )
    }

    var enabled by remember { mutableStateOf(uiStore.lastEnabled()) }
    var status by remember {
        mutableStateOf(if (db.hasCatalog()) "${db.countAll()} títulos disponíveis" else "Conectando...")
    }
    var accountInfo by remember { mutableStateOf<XtreamAccountInfo?>(null) }
    var syncing by remember { mutableStateOf(!db.hasCatalog()) }
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
        val hadCatalog = db.hasCatalog()
        if (!hadCatalog) syncing = true

        try {
            val cfg = withContext(Dispatchers.IO) {
                repository.registerIfNeeded(
                    identity,
                    if (isTv) "ANDROID_TV" else "ANDROID_MOBILE"
                )
                repository.config(identity, forceCatalog)
            }

            enabled = cfg.enabled
            uiStore.setLastEnabled(cfg.enabled)

            if (!cfg.enabled) {
                status = "Acesso bloqueado"
                return
            }

            val remoteUrl = cfg.playlistUrl
            if (remoteUrl.isNullOrBlank()) {
                status = "Aguardando ativação"
                return
            }

            accountInfo = withContext(Dispatchers.IO) { repository.accountInfo(remoteUrl) }
            ExpiryNotifier.notifyIfNeeded(context, accountInfo)

            val cachedUrl = db.getMeta("playlist_url")
            val lastSync = db.getMetaLong("last_sync")
            val stale = System.currentTimeMillis() - lastSync > 6L * 60L * 60L * 1000L
            val needsSync = forceCatalog || !hadCatalog || cachedUrl != remoteUrl || stale

            if (needsSync) {
                status = if (hadCatalog) {
                    "${db.countAll()} títulos • atualizando em segundo plano..."
                } else {
                    "Preparando catálogo pela primeira vez..."
                }

                withContext(Dispatchers.IO) {
                    repository.syncCatalog(remoteUrl) { progress ->
                        if (!hadCatalog) scope.launch { status = progress }
                    }
                }
                catalogVersion += 1
            }

            status = buildHeaderStatus(db.countAll(), accountInfo)
        } catch (e: Throwable) {
            status = if (db.hasCatalog()) {
                "${db.countAll()} títulos • modo offline"
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
                !enabled -> BlockedScreen(
                    code = identity.pairingCode,
                    onSupport = { overlay = Overlay.Support },
                    onRefresh = { scope.launch { refresh(false) } }
                )

                !db.hasCatalog() -> ActivationScreen(
                    code = identity.pairingCode,
                    status = status,
                    loading = syncing,
                    onRefresh = { scope.launch { refresh(true) } },
                    onSupport = { overlay = Overlay.Support }
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
                        if (current.item.type != ContentType.LIVE) playbackVersion += 1
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
                    isTv = isTv,
                    onBack = { overlay = null },
                    onSupport = { overlay = Overlay.Support },
                    onRefresh = { scope.launch { refresh(true) } },
                    onParentalUnlocked = { adultUnlocked = true }
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

private fun buildHeaderStatus(count: Int, account: XtreamAccountInfo?): String {
    val notice = ExpiryNotifier.notice(account)
    return if (notice != null) "$count títulos • $notice" else "$count títulos"
}

private fun accountDisplay(account: XtreamAccountInfo?): String? {
    val date = ExpiryNotifier.displayDate(account) ?: return null
    val notice = ExpiryNotifier.notice(account)
    return if (notice != null) "$notice • $date" else "Ativo até $date"
}
