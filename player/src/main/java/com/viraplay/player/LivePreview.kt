package com.viraplay.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var ready by remember(item.itemKey) { mutableStateOf(false) }

    LaunchedEffect(item.itemKey) {
        ready = false
        delay(550)
        ready = true
    }

    if (!ready || item.url.isNullOrBlank()) {
        Box(modifier)
        return
    }

    val player = remember(item.itemKey) {
        ExoPlayer.Builder(context).build().apply {
            volume = if (muted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(item.url!!))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}
