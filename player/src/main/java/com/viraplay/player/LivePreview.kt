package com.viraplay.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun LivePreviewPlayer(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    muted: Boolean = true
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = if (muted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(item.itemKey, item.url) {
        ready = false
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        val url = item.url
        if (url.isNullOrBlank()) return@LaunchedEffect

        // Debounce: ao navegar rápido pelo controle, não abre um stream novo para cada foco.
        delay(850)

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        ready = true
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    if (!ready) {
        Box(modifier.background(Color.Black))
        return
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        modifier = modifier
    )
}
