package com.viraplay.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.viraplay.shared.ContentType
import kotlinx.coroutines.delay

private data class ScreenMode(
    val label: String,
    val resizeMode: Int
)

private val screenModes = listOf(
    ScreenMode("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ScreenMode("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ScreenMode("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL)
)

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

    var currentItem by remember(item.itemKey) { mutableStateOf(item) }
    var currentUrl by remember(item.itemKey) { mutableStateOf(item.url.orEmpty()) }
    var error by remember(item.itemKey) { mutableStateOf<String?>(null) }
    var retryCount by remember(item.itemKey) { mutableIntStateOf(0) }
    var landscape by remember(item.itemKey) { mutableStateOf(!isTv) }
    var screenModeIndex by remember(item.itemKey) { mutableIntStateOf(0) }

    val player = remember(item.itemKey) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(playbackException: PlaybackException) {
                    error = playbackException.message ?: "Falha na fonte de vídeo"
                }
            })
            setMediaItem(MediaItem.fromUri(currentUrl))
            prepare()
            if (startPosition > 0L && item.type != ContentType.LIVE) {
                seekTo(startPosition)
            }
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
        error = null
        retryCount = 0
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(newUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun changeLive(next: Boolean) {
        if (currentItem.type != ContentType.LIVE) return
        db.liveNeighbor(currentItem, next)?.let(::play)
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

    LaunchedEffect(currentItem.itemKey) {
        while (true) {
            delay(5_000)
            persistProgress()
        }
    }

    LaunchedEffect(error) {
        if (error != null && retryCount < 1) {
            retryCount++
            delay(1_500)
            error = null

            if (currentItem.type == ContentType.LIVE) {
                val alternate = when {
                    currentUrl.endsWith(".ts", ignoreCase = true) -> currentUrl.dropLast(3) + ".m3u8"
                    currentUrl.endsWith(".m3u8", ignoreCase = true) -> currentUrl.dropLast(5) + ".ts"
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
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        controllerShowTimeoutMs = 5_000
                        controllerAutoShow = true
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        resizeMode = screenModes[screenModeIndex].resizeMode
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = screenModes[screenModeIndex].resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            persistProgress()
                            onBack()
                        }) {
                            Text("Voltar")
                        }

                        Text(
                            currentItem.name,
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
                                onClick = {
                                    landscape = !landscape
                                    activity?.requestedOrientation = if (landscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    }
                                },
                                label = { Text("Girar", fontSize = 11.sp) }
                            )
                        }

                        AssistChip(
                            onClick = {
                                screenModeIndex = (screenModeIndex + 1) % screenModes.size
                            },
                            label = {
                                Text("Tela: ${screenModes[screenModeIndex].label}", fontSize = 11.sp)
                            }
                        )

                        if (currentItem.type == ContentType.LIVE) {
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

            error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.90f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 520.dp)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Não foi possível reproduzir",
                            color = VpDanger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(message.take(140), color = VpMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                error = null
                                player.prepare()
                                player.playWhenReady = true
                            }) {
                                Text("Tentar novamente")
                            }
                            OutlinedButton(onClick = {
                                Support.openWhatsApp(context, identity.pairingCode)
                            }) {
                                Text("Suporte")
                            }
                        }
                    }
                }
            }
        }
    }
}
