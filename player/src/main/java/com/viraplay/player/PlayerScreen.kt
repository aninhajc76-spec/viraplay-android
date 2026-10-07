package com.viraplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.viraplay.shared.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout

private data class ScreenMode(
    val label: String,
    val resizeMode: Int,
    val surfaceScale: Float = 1f
)

private data class AudioTrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val supported: Boolean
)

private val screenModes = listOf(
    ScreenMode("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ScreenMode("Tela cheia", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ScreenMode("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ScreenMode("Zoom +", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.12f)
)

private fun audioChoices(tracks: Tracks): List<AudioTrackChoice> {
    val result = mutableListOf<AudioTrackChoice>()
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val language = format.language?.uppercase()?.takeIf { it.isNotBlank() }
                val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase()
                val base = format.label?.takeIf { it.isNotBlank() }
                    ?: language
                    ?: codec
                    ?: "Áudio ${result.size + 1}"
                val supported = group.isTrackSupported(i)
                val label = if (supported) base else "$base • incompatível"
                result += AudioTrackChoice(group, i, label, supported)
            }
        }
    return result
}

private fun selectedAudioLabel(tracks: Tracks): String {
    val choices = audioChoices(tracks)
    return choices.firstOrNull { it.group.isTrackSelected(it.trackIndex) }?.label ?: "AUTO"
}

private fun portugueseAudioChoice(tracks: Tracks): AudioTrackChoice? =
    audioChoices(tracks).firstOrNull { choice ->
        if (!choice.supported) return@firstOrNull false
        val format = choice.group.getTrackFormat(choice.trackIndex)
        val lang = format.language
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            .orEmpty()
        val label = format.label?.lowercase().orEmpty()

        lang in setOf("pt", "ptbr", "por", "pob", "ptb") ||
            lang.startsWith("pt") ||
            label.contains("portugu") ||
            label.contains("brasil") ||
            label.contains("brazil") ||
            label.contains("pt-br") ||
            label.contains("pt_br") ||
            label.contains("dublado") ||
            label.contains("dub")
    }

private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@androidx.media3.common.util.UnstableApi
private fun applyPlayerScreenMode(player: ExoPlayer, view: PlayerView?, index: Int) {
    val mode = screenModes[index.coerceIn(screenModes.indices)]
    view?.let { pv ->
        pv.resizeMode = mode.resizeMode
        pv.videoSurfaceView?.let { surface ->
            surface.scaleX = mode.surfaceScale
            surface.scaleY = mode.surfaceScale
            surface.requestLayout()
        }
        pv.requestLayout()
        pv.invalidate()
    }
    player.videoScalingMode =
        if (mode.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
}


@Composable
private fun TvPlayerControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.height(36.dp).widthIn(min = 78.dp, max = 142.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, maxLines = 1)
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
    var audioTrackCount by remember(item.itemKey) { mutableIntStateOf(0) }
    var audioSupportedCount by remember(item.itemKey) { mutableIntStateOf(0) }
    var audioLabelState by remember(item.itemKey) { mutableStateOf("AUTO") }
    var portugueseApplied by remember(item.itemKey) { mutableStateOf(false) }
    var variants by remember(item.itemKey) {
        mutableStateOf(
            if (item.type == ContentType.LIVE) db.liveVariants(item)
            else listOf(item)
        )
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var compatViewRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var compatMode by remember(item.itemKey) { mutableStateOf(false) }
    var compatStartedForUrl by remember(item.itemKey) { mutableStateOf<String?>(null) }
    var compatAudioLabel by remember(item.itemKey) { mutableStateOf("Compatibilidade") }
    val compatEngine = remember(item.itemKey) { CompatVlcEngine(context) }

    val videoFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }

    val player = remember(item.itemKey) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setConstrainAudioChannelCountToDeviceCapabilities(true)
            )
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) VPlayo/3.2")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity"
                )
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(playbackException: PlaybackException) {
                    // Se o modo COMPAT já assumiu, ignore erros tardios do Media3.
                    if (compatMode) return

                    val raw = buildString {
                        append(playbackException.message.orEmpty())
                        append(" ")
                        append(playbackException.cause?.message.orEmpty())
                    }

                    // Qualquer falha real do Media3 tenta o motor de compatibilidade.
                    // O alerta só aparece se o COMPAT também não iniciar.
                    error = null
                    audioLabelState = if (
                        raw.contains("MediaCodec", true) ||
                        raw.contains("VideoRenderer", true) ||
                        raw.contains("AudioRenderer", true) ||
                        raw.contains("Decoder", true) ||
                        raw.contains("format_supported=no", true)
                    ) "COMPAT" else "AUTO → COMPAT"
                    compatMode = true
                }

                override fun onIsPlayingChanged(value: Boolean) {
                    isPlaying = value
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val choices = audioChoices(tracks)
                    audioTrackCount = choices.size
                    audioSupportedCount = choices.count { it.supported }
                    audioLabelState = selectedAudioLabel(tracks)
                }
            })

            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            volume = 1f
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguages("pt-BR", "pt", "por")
                .build()

            setMediaItem(MediaItem.fromUri(currentUrl))
            playWhenReady = false
        }
    }

    fun persistProgress() {
        if (currentItem.type == ContentType.LIVE) return
        val duration = if (compatMode) {
            compatEngine.duration()
        } else {
            player.duration.takeIf { it > 0L } ?: 0L
        }
        val position = if (compatMode) compatEngine.currentPosition() else player.currentPosition
        db.saveProgress(currentItem.itemKey, position, duration)
    }

    fun play(newItem: CatalogItem) {
        val newUrl = newItem.url.orEmpty()
        if (newUrl.isBlank()) return

        persistProgress()
        currentItem = newItem
        currentUrl = newUrl
        qualityLabelState = qualityLabel(newItem.name)
        portugueseApplied = false
        compatMode = false
        compatStartedForUrl = null
        compatAudioLabel = "Compatibilidade"
        compatEngine.stop()
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

    fun cycleAudio() {
        if (compatMode) {
            compatAudioLabel = compatEngine.cycleAudio()
            audioLabelState = "COMPAT • $compatAudioLabel"
            controlsVisible = true
            return
        }

        val allChoices = audioChoices(player.currentTracks)
        val choices = allChoices.filter { it.supported }

        if (allChoices.isEmpty()) {
            audioLabelState = "Nenhuma faixa detectada"
            return
        }

        if (choices.isEmpty()) {
            compatMode = true
            audioLabelState = "COMPAT"
            return
        }

        val selectedIndex = choices.indexOfFirst { it.group.isTrackSelected(it.trackIndex) }
        val nextIndex = if (selectedIndex < 0) 0 else selectedIndex + 1

        if (nextIndex >= choices.size) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setPreferredAudioLanguages("pt-BR", "pt", "por")
                .build()
            audioLabelState = "AUTO / PT"
        } else {
            val choice = choices[nextIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex)
                )
                .build()
            audioLabelState = choice.label
        }

        player.volume = 1f
        player.playWhenReady = true
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

    LaunchedEffect(currentItem.itemKey) {
        // A interface principal continua composta por baixo do overlay.
        // Encerra qualquer prévia antes de abrir outro stream para não
        // estourar contas com limite de 1 conexão simultânea.
        PlaybackSessionCoordinator.stopPreview()
        delay(900)

        if (!compatMode && player.mediaItemCount > 0) {
            player.prepare()
            if (startPosition > 0L && currentItem.itemKey == item.itemKey && item.type != ContentType.LIVE) {
                player.seekTo(startPosition)
            }
            player.playWhenReady = true
        }
    }

    LaunchedEffect(Unit) {
        activity?.let { act ->
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        if (!isTv) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    LaunchedEffect(controlsVisible, isTv, currentItem.itemKey) {
        delay(80)
        if (controlsVisible) {
            if (isTv) runCatching { controlsFocus.requestFocus() }
            delay(5_500)
            controlsVisible = false
        } else if (isTv) {
            runCatching { videoFocus.requestFocus() }
        }
    }

    LaunchedEffect(
        currentItem.itemKey,
        currentUrl,
        audioTrackCount,
        audioSupportedCount,
        compatMode
    ) {
        val incompatibleAudio =
            audioTrackCount > 0 && audioSupportedCount == 0

        if (incompatibleAudio && !compatMode) {
            compatMode = true
            audioLabelState = "COMPAT"
        }

        if (compatMode && compatStartedForUrl != currentUrl) {
            val resumeAt = player.currentPosition.coerceAtLeast(
                if (currentItem.itemKey == item.itemKey) startPosition else 0L
            )
            player.stop()
            compatEngine.open(currentUrl, resumeAt)
            compatStartedForUrl = currentUrl
            delay(1200)
            compatEngine.selectPortugueseAudio()?.let {
                compatAudioLabel = it
                audioLabelState = "COMPAT • $it"
            }
        }
    }

    LaunchedEffect(compatMode, currentUrl, compatStartedForUrl) {
        if (!compatMode || compatStartedForUrl != currentUrl) return@LaunchedEffect

        delay(12_000)

        if (compatMode && compatStartedForUrl == currentUrl && !compatEngine.isPlaying()) {
            if (currentItem.type == ContentType.LIVE && currentUrl.endsWith(".ts", ignoreCase = true)) {
                currentUrl = currentUrl.dropLast(3) + ".m3u8"
                compatStartedForUrl = null
                error = null
                return@LaunchedEffect
            }

            val probe = withContext(Dispatchers.IO) { PlaybackProbe.check(currentUrl) }
            error = buildString {
                append("O modo normal e o modo de compatibilidade não conseguiram iniciar este conteúdo.")
                append(" Diagnóstico: ")
                append(probe.detail)
                append(".")
            }
        }
    }

    LaunchedEffect(currentItem.itemKey, audioTrackCount, compatMode) {
        if (!compatMode && !portugueseApplied && audioTrackCount > 0) {
            portugueseAudioChoice(player.currentTracks)?.let { choice ->
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(
                        TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex)
                    )
                    .build()
                player.volume = 1f
                audioLabelState = choice.label
                portugueseApplied = true
            }
        }
    }

    LaunchedEffect(currentItem.itemKey, compatMode) {
        while (true) {
            if (compatMode) {
                currentPositionMs = compatEngine.currentPosition()
                durationMs = compatEngine.duration()
                isPlaying = compatEngine.isPlaying()
            } else {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0L } ?: 0L
                isPlaying = player.isPlaying
            }
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
        if (error != null && retryCount < 2 && !compatMode) {
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
            PlaybackSessionCoordinator.stopPreview()
            persistProgress()
            compatEngine.release()
            player.release()
            activity?.let { act ->
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
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
                .pointerInput(currentItem.itemKey) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            if (currentItem.type != ContentType.LIVE) {
                                if (compatMode) {
                                    compatEngine.seekBy(10_000L)
                                } else {
                                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(
                                        player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    ))
                                }
                            }
                        }
                    )
                }
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
            if (compatMode) {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).also { view ->
                            compatViewRef = view
                            compatEngine.attach(view)
                            view.keepScreenOn = true
                            view.setBackgroundColor(AndroidColor.BLACK)
                        }
                    },
                    update = { view ->
                        compatViewRef = view
                        compatEngine.attach(view)
                        val scale = when (screenModeIndex) {
                            0 -> 1f
                            1 -> 1.06f
                            2 -> 1.14f
                            else -> 1.22f
                        }
                        view.scaleX = scale
                        view.scaleY = scale
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx).inflate(
                            R.layout.player_view_tv,
                            null,
                            false
                        ) as PlayerView

                        view.apply {
                            playerViewRef = this
                            this.player = player
                            useController = false
                            controllerShowTimeoutMs = 0
                            controllerAutoShow = false
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
                            applyPlayerScreenMode(player, this, screenModeIndex)
                        }
                    },
                    update = { view ->
                        playerViewRef = view
                        view.player = player
                        applyPlayerScreenMode(player, view, screenModeIndex)
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = if (isTv) 0.78f else 0.82f),
                    shape = if (isTv) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isTv) 7.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 5.dp else 8.dp)
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
                                fontSize = if (isTv) 12.sp else 14.sp,
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
                                    TvPlayerControl("-10 s", {
                                        if (compatMode) compatEngine.seekBy(-10_000L)
                                        else player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                    })
                                }
                                TvPlayerControl(if (isPlaying) "Pausar" else "Reproduzir", {
                                    if (compatMode) {
                                        if (compatEngine.isPlaying()) compatEngine.pause() else compatEngine.play()
                                    } else {
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                    controlsVisible = true
                                })
                                if (currentItem.type != ContentType.LIVE) {
                                    TvPlayerControl("+10 s", {
                                        if (compatMode) {
                                            compatEngine.seekBy(10_000L)
                                        } else {
                                            val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration))
                                        }
                                    })
                                }
                                TvPlayerControl("Tela: ${screenModes[screenModeIndex].label}", {
                                    screenModeIndex = (screenModeIndex + 1) % screenModes.size
                                    if (compatMode) {
                                        compatViewRef?.let { view ->
                                            val scale = when (screenModeIndex) {
                                                0 -> 1f
                                                1 -> 1.06f
                                                2 -> 1.14f
                                                else -> 1.22f
                                            }
                                            view.scaleX = scale
                                            view.scaleY = scale
                                        }
                                    } else {
                                        applyPlayerScreenMode(player, playerViewRef, screenModeIndex)
                                    }
                                    controlsVisible = true
                                })
                                TvPlayerControl(
                                    "Áudio: ${if (compatMode) "COMPAT • $compatAudioLabel" else if (audioTrackCount == 0) "detectar" else audioLabelState}",
                                    {
                                        cycleAudio()
                                        controlsVisible = true
                                    }
                                )
                                if (currentItem.type == ContentType.LIVE) {
                                    TvPlayerControl(
                                        if (autoQuality) "Qualidade: AUTO" else "Qualidade: $qualityLabelState",
                                        { cycleQuality(); controlsVisible = true }
                                    )
                                    TvPlayerControl("Canal anterior", { changeLive(false); controlsVisible = true })
                                    TvPlayerControl("Próximo canal", { changeLive(true); controlsVisible = true })
                                }
                            } else {
                                if (currentItem.type != ContentType.LIVE) {
                                    AssistChip(
                                        onClick = {
                                            if (compatMode) compatEngine.seekBy(-10_000L)
                                            else player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                        },
                                        label = { Text("-10 s", fontSize = 11.sp) }
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        if (compatMode) {
                                            if (compatEngine.isPlaying()) compatEngine.pause() else compatEngine.play()
                                        } else {
                                            if (player.isPlaying) player.pause() else player.play()
                                        }
                                        controlsVisible = true
                                    },
                                    label = { Text(if (isPlaying) "Pausar" else "Reproduzir", fontSize = 11.sp) }
                                )
                                if (currentItem.type != ContentType.LIVE) {
                                    AssistChip(
                                        onClick = {
                                            if (compatMode) {
                                                compatEngine.seekBy(10_000L)
                                            } else {
                                                val d = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(d))
                                            }
                                        },
                                        label = { Text("+10 s", fontSize = 11.sp) }
                                    )
                                }
                                AssistChip(onClick = ::rotate, label = { Text("Girar", fontSize = 11.sp) })
                                AssistChip(
                                    onClick = {
                                        screenModeIndex = (screenModeIndex + 1) % screenModes.size
                                        if (compatMode) {
                                            compatViewRef?.let { view ->
                                                val scale = when (screenModeIndex) {
                                                    0 -> 1f
                                                    1 -> 1.06f
                                                    2 -> 1.14f
                                                    else -> 1.22f
                                                }
                                                view.scaleX = scale
                                                view.scaleY = scale
                                            }
                                        } else {
                                            applyPlayerScreenMode(player, playerViewRef, screenModeIndex)
                                        }
                                        controlsVisible = true
                                    },
                                    label = { Text("Tela: ${screenModes[screenModeIndex].label}", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = { cycleAudio(); controlsVisible = true },
                                    label = {
                                        Text(
                                            "Áudio: ${if (compatMode) "COMPAT • $compatAudioLabel" else if (audioTrackCount == 0) "detectar" else audioLabelState}",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                                if (currentItem.type == ContentType.LIVE) {
                                    AssistChip(
                                        onClick = { changeLive(false); controlsVisible = true },
                                        label = { Text("‹ Canal", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = ::cycleQuality,
                                        label = { Text(if (autoQuality) "Qualidade: AUTO" else "Qualidade: $qualityLabelState", fontSize = 11.sp) }
                                    )
                                    AssistChip(
                                        onClick = { changeLive(true); controlsVisible = true },
                                        label = { Text("Canal ›", fontSize = 11.sp) }
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
                                Slider(
                                    value = fraction,
                                    onValueChange = { newValue ->
                                        if (durationMs > 0L) {
                                            val target = (durationMs * newValue).toLong()
                                            if (compatMode) compatEngine.seekTo(target)
                                            else player.seekTo(target)
                                            controlsVisible = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
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
                                retryCount = 0
                                if (compatMode) {
                                    compatEngine.stop()
                                    compatEngine.open(currentUrl, currentPositionMs)
                                    compatStartedForUrl = currentUrl
                                } else {
                                    player.prepare()
                                    player.playWhenReady = true
                                }
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
