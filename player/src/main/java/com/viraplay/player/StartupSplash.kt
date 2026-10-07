package com.viraplay.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StartupGate(content: @Composable () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    if (finished) {
        content()
        return
    }

    val iconAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0.70f) }
    val iconRotation = remember { Animatable(-10f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordOffset = remember { Animatable(18f) }
    val glowScale = remember { Animatable(0.55f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { iconAlpha.animateTo(1f, tween(260)) }
        launch { iconScale.animateTo(1.06f, tween(520, easing = FastOutSlowInEasing)) }
        launch { iconRotation.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
        launch { glowScale.animateTo(1.18f, tween(720, easing = LinearEasing)) }
        delay(300)
        launch { wordAlpha.animateTo(1f, tween(330)) }
        launch { wordOffset.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
        delay(720)
        exitAlpha.animateTo(0f, tween(210))
        finished = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF102A5E),
                        Color(0xFF090B24),
                        Color(0xFF02040B),
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(310.dp)
                .graphicsLayer {
                    scaleX = glowScale.value
                    scaleY = glowScale.value
                    alpha = (1.25f - glowScale.value).coerceIn(.08f, .44f)
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            VpCyan.copy(alpha = .40f),
                            VpPurple.copy(alpha = .18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(178.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                            rotationZ = iconRotation.value
                            alpha = iconAlpha.value
                        }
                        .border(1.dp, VpCyan.copy(alpha = .18f), CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF030817).copy(alpha = .70f))
                )
                Image(
                    painter = painterResource(R.drawable.vplayo_splash_icon),
                    contentDescription = "VPlayo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(170.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                            rotationZ = iconRotation.value
                            alpha = iconAlpha.value
                        }
                )
            }

            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(R.drawable.viraplay_wordmark),
                contentDescription = "VPlayo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(.58f)
                    .height(74.dp)
                    .alpha(wordAlpha.value)
                    .graphicsLayer { translationY = wordOffset.value }
            )

            Text(
                "ANDROID • ANDROID TV",
                color = Color.White.copy(alpha = .72f * wordAlpha.value),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .width(116.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, VpCyan, VpPurple, Color.Transparent)
                    )
                )
                .alpha(wordAlpha.value)
        )
    }
}
