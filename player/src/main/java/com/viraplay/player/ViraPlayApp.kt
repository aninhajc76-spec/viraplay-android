package com.viraplay.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.viraplay.shared.ContentType
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
fun ViraPlayApp() {
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
    val scope = rememberCoroutineScope()

    var section by remember {
        mutableStateOf(runCatching { MainSection.valueOf(uiStore.section()) }.getOrDefault(MainSection.HOME))
    }
    var enabled by remember { mutableStateOf(uiStore.lastEnabled()) }
    var status by remember {
        mutableStateOf(if (db.hasCatalog()) "${db.countAll()} títulos prontos" else "Conectando...")
    }
    var syncing by remember { mutableStateOf(!db.hasCatalog()) }
    var catalogVersion by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var resumeItem by remember { mutableStateOf<CatalogItem?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    suspend fun refresh(forceCatalog: Boolean) {
        val hadCatalog = db.hasCatalog()
        if (!hadCatalog) syncing = true
        lastError = null

        try {
            val cfg = withContext(Dispatchers.IO) {
                repository.registerIfNeeded(
                    identity,
                    if (isTv) "ANDROID_TV" else "ANDROID_MOBILE"
                )
                repository.config(identity)
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

            val cachedUrl = db.getMeta("playlist_url")
            val lastSync = db.getMetaLong("last_sync")
            val stale = System.currentTimeMillis() - lastSync > 6L * 60L * 60L * 1000L
            val needsSync = forceCatalog || !hadCatalog || cachedUrl != remoteUrl || stale

            if (needsSync) {
                status = if (hadCatalog) "Atualizando catálogo em segundo plano..." else "Preparando catálogo pela primeira vez..."
                val result = withContext(Dispatchers.IO) {
                    repository.syncCatalog(remoteUrl) { progress ->
                        scope.launch { status = progress }
                    }
                }
                catalogVersion += 1
                status = "${db.countAll()} títulos • ${result.sourceKind}"
            } else {
                status = "${db.countAll()} títulos disponíveis"
            }
        } catch (e: Throwable) {
            val message = (e.message ?: "falha de conexão").replace('\n', ' ').take(120)
            lastError = message
            if (db.hasCatalog()) {
                status = "Modo offline • catálogo salvo"
            } else {
                status = "Falha: $message"
            }
        } finally {
            syncing = false
        }
    }

    fun openItem(item: CatalogItem) {
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
        val resumable = position > 30_000L && (duration <= 0L || position.toDouble() / duration.toDouble() < 0.95)
        if (resumable) {
            resumeItem = item.copy(progressMs = position, durationMs = duration)
        } else {
            overlay = Overlay.Player(item, 0L)
        }
    }

    LaunchedEffect(Unit) {
        refresh(forceCatalog = false)
        while (true) {
            delay(10L * 60L * 1000L)
            refresh(forceCatalog = false)
        }
    }

    MaterialTheme(colorScheme = ViraPlayColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VpBg)
        ) {
            when {
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
                    status = status,
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
                    section = section,
                    catalogVersion = catalogVersion,
                    status = status,
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
                        overlay = null
                        catalogVersion += 1
                    }
                )

                is Overlay.Series -> SeriesDetailScreen(
                    parent = current.item,
                    db = db,
                    repository = repository,
                    isTv = isTv,
                    onBack = { overlay = null },
                    onPlay = ::openItem,
                    onChanged = { catalogVersion += 1 }
                )

                Overlay.Support -> SupportScreen(
                    code = identity.pairingCode,
                    isTv = isTv,
                    onBack = { overlay = null }
                )

                Overlay.Settings -> SettingsScreen(
                    code = identity.pairingCode,
                    db = db,
                    status = status,
                    isTv = isTv,
                    onBack = { overlay = null },
                    onSupport = { overlay = Overlay.Support },
                    onRefresh = { scope.launch { refresh(true) } }
                )

                null -> Unit
            }

            resumeItem?.let { item ->
                AlertDialog(
                    onDismissRequest = { resumeItem = null },
                    title = { Text("Continuar assistindo?") },
                    text = {
                        Text(
                            "Você parou em ${formatProgress(item.progressMs)}. Deseja continuar dali?"
                        )
                    },
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
        }
    }
}
