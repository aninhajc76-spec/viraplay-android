package com.viraplay.player

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

@androidx.media3.common.util.UnstableApi
@Composable
fun LivePreviewPlayer(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    muted: Boolean = true
) {
    val context = LocalContext.current

    var ready by remember(item.itemKey) { mutableStateOf(false) }
    var compatMode by remember(item.itemKey) { mutableStateOf(false) }
    var compatStartedUrl by remember(item.itemKey) { mutableStateOf<String?>(null) }

    val compatEngine = remember { CompatVlcEngine(context) }

    val player = remember {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("VPlayo/${BuildConfig.VERSION_NAME} Android")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)

        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                volume = if (muted) 0f else 1f
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && !compatMode) ready = true
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (!compatMode) {
                            ready = false
                            compatMode = true
                        }
                    }
                })
            }
    }

    LaunchedEffect(item.itemKey, item.url) {
        ready = false
        compatMode = false
        compatStartedUrl = null
        compatEngine.stop()

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        val url = item.url
        if (url.isNullOrBlank()) return@LaunchedEffect

        delay(450)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true

        delay(6_500)
        if (!ready && !compatMode && item.url == url) compatMode = true
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.release() }
            runCatching { compatEngine.release() }
        }
    }

    if (compatMode) {
        val url = item.url.orEmpty()
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { view ->
                    view.keepScreenOn = true
                    view.setBackgroundColor(AndroidColor.BLACK)
                    compatEngine.attach(view)
                    if (url.isNotBlank() && compatStartedUrl != url) {
                        player.stop()
                        compatEngine.open(url)
                        compatStartedUrl = url
                    }
                }
            },
            update = { view ->
                compatEngine.attach(view)
                if (url.isNotBlank() && compatStartedUrl != url) {
                    player.stop()
                    compatEngine.open(url)
                    compatStartedUrl = url
                }
            },
            modifier = modifier.background(Color.Black)
        )
        return
    }

    if (!ready) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                setBackgroundColor(AndroidColor.BLACK)
                setShutterBackgroundColor(AndroidColor.BLACK)
                setKeepContentOnPlayerReset(true)
                alpha = 1f
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.setBackgroundColor(AndroidColor.BLACK)
            view.requestLayout()
        },
        modifier = modifier.background(Color.Black)
    )
}
