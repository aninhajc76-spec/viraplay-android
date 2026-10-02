package com.viraplay.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    large: Boolean = false
) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.viraplay_wordmark),
        contentDescription = "ViraPlay",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .width(if (large) 300.dp else 178.dp)
            .height(if (large) 92.dp else 54.dp)
    )
}

@Composable
fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = if (focused) VpPanelAlt else VpPanel,
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) VpCyan else VpBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .focusable()
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
    Column(
        modifier = Modifier
            .width(width)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) VpCyan else VpBorder,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(VpPanel)
        ) {
            AsyncImage(
                model = item.image,
                contentDescription = item.name,
                placeholder = painterResource(R.drawable.viraplay_logo),
                error = painterResource(R.drawable.viraplay_logo),
                fallback = painterResource(R.drawable.viraplay_logo),
                contentScale = if (item.image.isNullOrBlank()) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (item.image.isNullOrBlank()) 22.dp else 0.dp)
            )

            if (item.favorite || onFavorite != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .then(if (onFavorite != null) Modifier.clickable { onFavorite?.invoke() } else Modifier)
                ) {
                    Text(
                        if (item.favorite) "FAV" else "+FAV",
                        color = if (item.favorite) VpCyan else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            if (item.progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progressFraction)
                            .background(VpCyan)
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            item.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        item.rating?.takeIf { it.isNotBlank() }?.let {
            Text("Nota $it", color = VpMuted, fontSize = 10.sp)
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
    val highlight = selected || focused
    Surface(
        color = if (highlight) VpPanelAlt else VpPanel,
        border = BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) VpCyan else VpBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
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
            TextButton(onClick = onFavorite) {
                Text(if (item.favorite) "FAV" else "+FAV", color = if (item.favorite) VpCyan else VpMuted, fontSize = 10.sp)
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
