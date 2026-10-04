package com.viraplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.viraplay.shared.ContentType
import kotlinx.coroutines.delay

private data class ScreenMode(val label: String, val resizeMode: Int)

private val screenModes = listOf(
    ScreenMode("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ScreenMode("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ScreenMode("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL)
)

private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}


@Composable
private fun TvPlayerControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.height(44.dp).widthIn(min = 105.dp, max = 190.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerScreen(
    item: CatalogItem,
    db: CatalogDb,
    isTv: Boolean,
    startPosition: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val identity = remember { DeviceIdentity(context) }
    val prefs = remember { PlaybackPreferences(context) }
    val bandwidthMeter = remember { DefaultBandwidthMeter.getSingletonInstance(context) }

    var currentItem by remember(item.itemKey) { mutableStateOf(item) }
    var currentUrl by remember(item.itemKey) { mutableStateOf(item.url.orEmpty()) }
    var error by remember(item.itemKey) { mutableStateOf<String?>(null) }
    var retryCount by remember(item.itemKey) { mutableIntStateOf(0) }
    var rotationIndex by remember(item.itemKey) { mutableIntStateOf(0) }
    var screenModeIndex by remember(item.itemKey) { mutableIntStateOf(0) }
    var controlsVisible by remember(item.itemKey) { mutableStateOf(true) }
    var isPlaying by remember(item.itemKey) { mutableStateOf(true) }
    var currentPositionMs by remember(item.itemKey) { mutableLongStateOf(0L) }
    var durationMs by remember(item.itemKey) { mutableLongStateOf(0L) }
    var autoQuality by remember(item.itemKey) { mutableStateOf(prefs.autoQuality) }
    var qualityLabelState by remember(item.itemKey) {
        mutableStateOf(qualityLabel(item.name))
    }
    var variants by remember(item.itemKey) {
        mutableStateOf(
            if (item.type == ContentType.LIVE) db.liveVariants(item)
            else listOf(item)
        )
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val videoFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }

    val player = remember(item.itemKey) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(playbackException: PlaybackException) {
                    val raw = playbackException.message ?: "Falha na fonte de vídeo"
                    error = when {
                        raw.contains("403", true) || raw.contains("401", true) ->
                            "O servidor recusou esta reprodução. Verifique se a conta já está em uso em outro aparelho."
                        raw.contains("MediaCodec", true) || raw.contains("VideoRenderer", true) || raw.contains("Decoder", true) ->
                            "Esta TV não conseguiu decodificar este vídeo. O ViraPlay tentou um decodificador alternativo; tente novamente ou escolha outra fonte/qualidade."
                        else -> "Não foi possível reproduzir este conteúdo nesta TV. Tente novamente."
                    }
                }

                override fun onIsPlayingChanged(value: Boolean) {
                    isPlaying = value
                }
            })
            setMediaItem(MediaItem.fromUri(currentUrl))
            prepare()
            if (startPosition > 0L && item.type != ContentType.LIVE) seekTo(startPosition)
            playWhenReady = true
        }
    }

    fun persistProgress() {
        if (currentItem.type == ContentType.LIVE) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        db.saveProgress(currentItem.itemKey, player.currentPosition, duration)
    }

    fun play(newItem: CatalogItem) {
        val newUrl = newItem.url.orEmpty()
        if (newUrl.isBlank()) return

        persistProgress()
        currentItem = newItem
        currentUrl = newUrl
        qualityLabelState = qualityLabel(newItem.name)
        error = null
        retryCount = 0

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(newUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun refreshVariants() {
        if (currentItem.type == ContentType.LIVE) {
            variants = db.liveVariants(currentItem)
        }
    }

    fun chooseForTarget(target: String): CatalogItem? {
        val exact = variants.firstOrNull {
            qualityLabel(it.name).equals(target, true)
        }
        if (exact != null) return exact

        val targetRank = qualityRank(target)
        return variants
            .filter { qualityRank(qualityLabel(it.name)) <= targetRank }
            .maxByOrNull { qualityRank(qualityLabel(it.name)) }
            ?: variants.minByOrNull { qualityRank(qualityLabel(it.name)) }
    }

    fun autoTarget(): String {
        val bps = bandwidthMeter.bitrateEstimate
        return when {
            bps >= 15_000_000L -> "4K"
            bps >= 7_000_000L -> "FHD"
            bps >= 3_000_000L -> "HD"
            bps > 0L -> "SD"
            else -> "FHD"
        }
    }

    fun applyAutomaticQuality() {
        if (
            !autoQuality ||
            currentItem.type != ContentType.LIVE ||
            variants.size <= 1
        ) return

        val target = chooseForTarget(autoTarget()) ?: return
        if (target.itemKey != currentItem.itemKey) play(target)
    }

    fun cycleQuality() {
        if (currentItem.type != ContentType.LIVE || variants.isEmpty()) return

        val labels = variants
            .map { qualityLabel(it.name) }
            .distinct()
            .sortedByDescending(::qualityRank)

        if (autoQuality) {
            autoQuality = false
            prefs.autoQuality = false
            val target = labels.firstOrNull() ?: return
            prefs.manualQuality = target
            chooseForTarget(target)?.let(::play)
            return
        }

        val current = prefs.manualQuality
        val index = labels.indexOf(current)

        if (index >= 0 && index < labels.lastIndex) {
            val next = labels[index + 1]
            prefs.manualQuality = next
            chooseForTarget(next)?.let(::play)
        } else {
            autoQuality = true
            prefs.autoQuality = true
            applyAutomaticQuality()
        }
    }

    fun changeLive(next: Boolean) {
        if (currentItem.type != ContentType.LIVE) return

        val neighbor = if (prefs.groupChannels) {
            val grouped = groupLiveItems(
                db.query(
                    type = ContentType.LIVE,
                    categoryId = currentItem.categoryId,
                    limit = 5000
                )
            )
            val currentBase = channelBaseName(currentItem.name)
            val index = grouped.indexOfFirst {
                channelBaseName(it.name).equals(currentBase, true)
            }

            if (grouped.isEmpty()) null
            else if (next) {
                grouped[if (index < 0 || index >= grouped.lastIndex) 0 else index + 1]
            } else {
                grouped[if (index <= 0) grouped.lastIndex else index - 1]
            }
        } else {
            db.liveNeighbor(currentItem, next)
        }

        neighbor?.let {
            play(it)
            refreshVariants()
            if (autoQuality) applyAutomaticQuality()
        }
    }

    fun rotate() {
        if (isTv) return

        rotationIndex = (rotationIndex + 1) % 3
        activity?.requestedOrientation = when (rotationIndex) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            1 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    BackHandler {
        persistProgress()
        onBack()
    }

    LaunchedEffect(Unit) {
        if (!isTv) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(controlsVisible, isTv, currentItem.itemKey) {
        if (!isTv) return@LaunchedEffect
        delay(80)
        if (controlsVisible) {
            runCatching { controlsFocus.requestFocus() }
            delay(7_000)
            controlsVisible = false
        } else {
            runCatching { videoFocus.requestFocus() }
        }
    }

    LaunchedEffect(currentItem.itemKey) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (currentItem.type != ContentType.LIVE) persistProgress()
            delay(500)
        }
    }

    LaunchedEffect(currentItem.itemKey, autoQuality) {
        if (currentItem.type == ContentType.LIVE && autoQuality && !isTv) {
            delay(4_000)
            while (true) {
                applyAutomaticQuality()
                delay(15_000)
            }
        }
    }

    LaunchedEffect(error) {
        if (error != null && retryCount < 2) {
            retryCount++
            delay(1_200)
            error = null

            if (
                currentItem.type == ContentType.LIVE &&
                autoQuality &&
                variants.size > 1
            ) {
                val currentRank = qualityRank(qualityLabel(currentItem.name))
                val lower = variants
                    .filter { qualityRank(qualityLabel(it.name)) < currentRank }
                    .maxByOrNull { qualityRank(qualityLabel(it.name)) }

                if (lower != null) {
                    play(lower)
                    return@LaunchedEffect
                }
            }

            if (currentItem.type == ContentType.LIVE) {
                val alternate = when {
                    currentUrl.endsWith(".ts", ignoreCase = true) ->
                        currentUrl.dropLast(3) + ".m3u8"
                    currentUrl.endsWith(".m3u8", ignoreCase = true) ->
                        currentUrl.dropLast(5) + ".ts"
                    else -> currentUrl
                }

                if (alternate != currentUrl) {
                    currentUrl = alternate
                    player.setMediaItem(MediaItem.fromUri(alternate))
                }
            }

            player.prepare()
            player.playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            persistProgress()
            player.release()
            if (!isTv) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(videoFocus)
                .onPreviewKeyEvent { event ->
                    if (!isTv || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    val navigationKey = event.key == Key.DirectionUp ||
                        event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight ||
                        event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter

                    if (!controlsVisible && navigationKey) {
                        controlsVisible = true
                        true
                    } else {
                        false
                    }
                }
                .focusable()
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        playerViewRef = this
                        this.player = player
                        useController = !isTv
                        controllerShowTimeoutMs = 4_000
                        controllerAutoShow = !isTv
                        if (isTv) {
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                        keepScreenOn = true
                        setBackgroundColor(AndroidColor.BLACK)
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        setKeepContentOnPlayerReset(true)
                        alpha = 1f
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        resizeMode = screenModes[screenModeIndex].resizeMode
                        if (!isTv) {
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            )
                        }
                    }
                },
                update = { view ->
                    playerViewRef = view
                    view.player = player
                    view.resizeMode = screenModes[screenModeIndex].resizeMode
                    view.requestLayout()
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(if (isTv) Alignment.BottomCenter else Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = if (isTv) 0.90f else 0.82f),
                    shape = if (isTv) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isTv) {
                                TvPlayerControl(
                                    label = "Voltar",
                                    onClick = { persistProgress(); onBack() },
                                    modifier = Modifier.focusRequester(controlsFocus)
                                )
                            } else {
                                TextButton(
                                    onClick = { persistProgress(); onBack() },
                                    modifier = Modifier.focusRequester(controlsFocus)
                                ) { Text("Voltar") }
                            }

                            Spacer(Modifier.width(10.dp))
                            Text(
                                channelBaseName(currentItem.name),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTv) {
                                if (currentItem.type != ContentType.LIVE) {
                                    TvPlayerControl("-10 s", { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) })
                                }
                                TvPlayerControl(if (isPlaying) "Pausar" else "Reproduzir", {
                                    if (player.isPlaying) player.pause() else player.play()
                                    controlsVisible = true
                                })
                                if (currentItem.type != ContentType.LIVE) {
                                    TvPlayerControl("+10 s", {
                                        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
                                    })
                                }
                                TvPlayerControl("Tela: ${screenModes[screenModeIndex].label}", {
                                    screenModeIndex = (screenModeIndex + 1) % screenModes.size
                                    playerViewRef?.let { pv ->
                                        pv.resizeMode = screenModes[screenModeIndex].resizeMode
                                        pv.requestLayout()
                                        pv.invalidate()
                                    }
                                    controlsVisible = true
                                })
                                if (currentItem.type == ContentType.LIVE) {
                                    TvPlayerControl(
                                        if (autoQuality) "Qualidade: AUTO" else "Qualidade: $qualityLabelState",
                                        { cycleQuality(); controlsVisible = true }
                                    )
                                    TvPlayerControl("Canal anterior", { changeLive(false); controlsVisible = true })
                                    TvPlayerControl("Próximo canal", { changeLive(true); controlsVisible = true })
                                }
                            } else {
                                AssistChip(onClick = ::rotate, label = { Text("Girar", fontSize = 11.sp) })
                                AssistChip(
                                    onClick = {
                                        screenModeIndex = (screenModeIndex + 1) % screenModes.size
                                        playerViewRef?.let { pv ->
                                            pv.resizeMode = screenModes[screenModeIndex].resizeMode
                                            pv.requestLayout()
                                            pv.invalidate()
                                        }
                                    },
                                    label = { Text("Tela: ${screenModes[screenModeIndex].label}", fontSize = 11.sp) }
                                )
                                if (currentItem.type == ContentType.LIVE) {
                                    AssistChip(
                                        onClick = ::cycleQuality,
                                        label = { Text(if (autoQuality) "Qualidade: AUTO" else "Qualidade: $qualityLabelState", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        if (currentItem.type != ContentType.LIVE) {
                            val fraction = if (durationMs > 0L) {
                                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(formatPlayerTime(currentPositionMs), color = Color.White, fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .background(VpSoft, RoundedCornerShape(50))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .background(VpCyan, RoundedCornerShape(50))
                                    )
                                }
                                Text(formatPlayerTime(durationMs), color = VpMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.90f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 520.dp)
                        .padding(24.dp)
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Não foi possível reproduzir",
                            color = VpDanger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(message.take(160), color = VpMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                error = null
                                player.prepare()
                                player.playWhenReady = true
                            }) {
                                Text("Tentar novamente")
                            }

                            OutlinedButton(
                                onClick = {
                                    Support.openWhatsApp(context, identity.pairingCode)
                                }
                            ) {
                                Text("Suporte")
                            }
                        }
                    }
                }
            }
        }
    }
}
