package com.viraplay.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showBeta: Boolean = false
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val logoWidth = when {
        large && wide -> 248.dp
        large -> 198.dp
        else -> 150.dp
    }
    val logoHeight = when {
        large && wide -> 72.dp
        large -> 62.dp
        else -> 44.dp
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.viraplay_wordmark),
            contentDescription = "VPlayo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(logoWidth).height(logoHeight)
        )

        if (showBeta) {
            Surface(
                color = VpPurple.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, VpPurple.copy(alpha = 0.72f)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = if (large) 10.dp else 4.dp)
            ) {
                Text(
                    "BETA",
                    color = Color.White,
                    fontSize = if (large) 10.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(120),
        label = "vp_focus_scale"
    )

    Surface(
        color = if (focused) Color(0xFF102B4A) else Color(0xFF081628),
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) VpCyan else VpBorder.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = if (focused) 8.dp else 1.dp,
        shadowElevation = if (focused) 16.dp else 3.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable(onClick = onClick)
    ) {
        Column(content = content)
    }
}

@Composable
fun PosterCard(
    item: CatalogItem,
    width: Dp,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(110),
        label = "poster_focus_scale"
    )

    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable(onClick = onClick)
    ) {
        Surface(
            color = VpPanel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                if (focused) 2.dp else 1.dp,
                if (focused) VpCyan else VpBorder.copy(alpha = .78f)
            ),
            shadowElevation = if (focused) 16.dp else 5.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF12243B), Color(0xFF08111F))
                        )
                    )
            ) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    placeholder = painterResource(R.drawable.viraplay_logo),
                    error = painterResource(R.drawable.viraplay_logo),
                    fallback = painterResource(R.drawable.viraplay_logo),
                    contentScale = if (item.image.isNullOrBlank()) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (item.image.isNullOrBlank()) 22.dp else 0.dp)
                )

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .82f))
                            )
                        )
                )

                if (item.favorite || onFavorite != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .then(
                                if (onFavorite != null) {
                                    Modifier.pointerInput(item.itemKey, item.favorite) {
                                        detectTapGestures(onTap = { onFavorite() })
                                    }
                                } else Modifier
                            )
                    ) {
                        Text(
                            if (item.favorite) "♥" else "+",
                            color = if (item.favorite) VpCyan else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                if (item.progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progressFraction)
                                .background(
                                    Brush.horizontalGradient(listOf(VpCyan, VpPurple))
                                )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        item.rating?.takeIf { it.isNotBlank() }?.let {
            Text("★ $it", color = VpGold, fontSize = 10.sp)
        }
    }
}

@Composable
fun LiveRow(
    item: CatalogItem,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onFocused: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        animationSpec = tween(85),
        label = "live_row_focus"
    )

    val container = when {
        focused -> VpCyan.copy(alpha = 0.22f)
        selected -> VpGreen.copy(alpha = 0.12f)
        else -> VpPanel
    }
    val borderColor = when {
        focused -> Color.White
        selected -> VpGreen
        else -> VpBorder
    }
    val borderWidth = when {
        focused -> 4.dp
        selected -> 2.dp
        else -> 1.dp
    }

    Surface(
        color = container,
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                placeholder = painterResource(R.drawable.viraplay_logo),
                error = painterResource(R.drawable.viraplay_logo),
                fallback = painterResource(R.drawable.viraplay_logo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.categoryName,
                    color = VpMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = Color.Transparent,
                modifier = Modifier.pointerInput(item.itemKey, item.favorite) {
                    detectTapGestures(onTap = { onFavorite() })
                }
            ) {
                Text(
                    if (item.favorite) "FAV" else "+FAV",
                    color = if (item.favorite) VpCyan else VpMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

fun formatProgress(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
