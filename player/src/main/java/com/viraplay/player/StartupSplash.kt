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
    val scale = remember { Animatable(0.92f) }
    val logoOffset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(280)) }
        launch { scale.animateTo(1f, tween(460)) }
        launch { logoOffset.animateTo(0f, tween(460)) }
        delay(760)
        alpha.animateTo(0f, tween(220))
        finished = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D2747), Color(0xFF04101F), Color.Black)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha.value)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationY = logoOffset.value
                }
        ) {
            Image(
                painter = painterResource(R.drawable.viraplay_logo),
                contentDescription = "VPlayo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(94.dp)
            )
            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(R.drawable.viraplay_wordmark),
                contentDescription = "VPlayo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.56f)
            )
        }
    }
}
