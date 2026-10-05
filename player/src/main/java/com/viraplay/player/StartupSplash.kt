package com.viraplay.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StartupGate(content: @Composable () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    if (finished) {
        content()
        return
    }

    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.90f) }
    val offset = remember { Animatable(14f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(260)) }
        launch { scale.animateTo(1f, tween(420)) }
        launch { offset.animateTo(0f, tween(420)) }
        delay(720)
        alpha.animateTo(0f, tween(180))
        finished = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0B2441), Color(0xFF020914), Color.Black)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.viraplay_wordmark),
            contentDescription = "VPlayo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth(0.64f)
                .alpha(alpha.value)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationY = offset.value
                }
        )
    }
}
