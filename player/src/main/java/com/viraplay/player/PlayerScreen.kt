package com.viraplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
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
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(playbackException: PlaybackException) {
                    val raw = playbackException.message ?: "Falha na fonte de vídeo"
                    error = when {
                        raw.contains("403", true) || raw.contains("401", true) ->
                            "O servidor recusou esta reprodução. Verifique se a conta já está em uso em outro aparelho."
                        else -> raw
                    }
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

    LaunchedEffect(controlsVisible, isTv) {
        if (!isTv) return@LaunchedEffect
        delay(60)
        if (controlsVisible) {
            runCatching { controlsFocus.requestFocus() }
        } else {
            runCatching { videoFocus.requestFocus() }
        }
    }

    LaunchedEffect(currentItem.itemKey) {
        while (true) {
            delay(5_000)
            persistProgress()
        }
    }

    LaunchedEffect(currentItem.itemKey, autoQuality) {
        if (currentItem.type == ContentType.LIVE && autoQuality) {
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

                    val code = event.nativeKeyEvent.keyCode
                    val navigationKey = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                        code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
                        code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                        code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                        code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

                    if (!controlsVisible && navigationKey) {
                        playerViewRef?.showController()
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
                        useController = true
                        controllerShowTimeoutMs = 4_000
                        controllerAutoShow = true
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        resizeMode = screenModes[screenModeIndex].resizeMode
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible = visibility == View.VISIBLE
                            }
                        )
                    }
                },
                update = { view ->
                    playerViewRef = view
                    view.player = player
                    view.resizeMode = screenModes[screenModeIndex].resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    persistProgress()
                                    onBack()
                                },
                                modifier = Modifier.focusRequester(controlsFocus)
                            ) {
                                Text("Voltar")
                            }

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
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isTv) {
                                AssistChip(
                                    onClick = ::rotate,
                                    label = { Text("Girar", fontSize = 11.sp) }
                                )
                            }

                            AssistChip(
                                onClick = {
                                    screenModeIndex = (screenModeIndex + 1) % screenModes.size
                                },
                                label = {
                                    Text(
                                        "Tela: ${screenModes[screenModeIndex].label}",
                                        fontSize = 11.sp
                                    )
                                }
                            )

                            if (currentItem.type == ContentType.LIVE) {
                                AssistChip(
                                    onClick = ::cycleQuality,
                                    label = {
                                        Text(
                                            if (autoQuality) {
                                                "Qualidade: AUTO"
                                            } else {
                                                "Qualidade: $qualityLabelState"
                                            },
                                            fontSize = 11.sp
                                        )
                                    }
                                )

                                AssistChip(
                                    onClick = { changeLive(false) },
                                    label = { Text("‹ Canal", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = { changeLive(true) },
                                    label = { Text("Canal ›", fontSize = 11.sp) }
                                )
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
