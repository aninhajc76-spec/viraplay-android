package com.viraplay.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viraplay.shared.ChannelItem
import com.viraplay.shared.ParsedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF8B3DFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ViraPlayApp() }
    }
}

@Composable
fun ViraPlayApp() {
    val context = LocalContext.current
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val identity = remember { DeviceIdentity(context) }
    val repo = remember { PlayerRepository() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Conectando...") }
    var parsed by remember { mutableStateOf<ParsedPlaylist?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChannelItem?>(null) }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            repo.register(identity.deviceId, identity.deviceSecret, identity.pairingCode, if (isTv) "ANDROID_TV" else "ANDROID_MOBILE")
            val cfg = repo.config(identity.deviceId, identity.deviceSecret)
            enabled = cfg.enabled
            if (cfg.enabled && !cfg.playlistUrl.isNullOrBlank()) {
                status = "Carregando lista..."
                parsed = repo.playlist(cfg.playlistUrl)
                status = "OK"
            } else {
                status = if (cfg.enabled) "Aguardando lista" else "Aguardando ativação"
            }
        } catch (e: Exception) {
            status = "Sem conexão com o servidor"
        }
    }

    LaunchedEffect(Unit) {
        while (parsed == null) {
            refresh()
            delay(10_000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Purple,
            background = Bg,
            surface = Panel
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            when {
                selected != null -> VideoScreen(selected!!) { selected = null }
                parsed != null && enabled -> HomeScreen(parsed!!, isTv) { selected = it }
                else -> ActivationScreen(identity.pairingCode, status) {
                    scope.launch { refresh() }
                }
            }
        }
    }
}

@Composable
private fun ActivationScreen(code: String, status: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ViraPlay", color = Color.White, fontSize = 42.sp)
        Spacer(Modifier.height(10.dp))
        Text("Ative seu dispositivo", color = Cyan, fontSize = 24.sp)
        Spacer(Modifier.height(26.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = Panel) {
            Text(code, modifier = Modifier.padding(horizontal = 34.dp, vertical = 20.dp), color = Color.White, fontSize = 34.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Envie este código para o atendimento ViraPlay.", color = Color.LightGray)
        Text(status, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onRefresh, modifier = Modifier.padding(top = 20.dp)) { Text("Atualizar") }
    }
}

@Composable
private fun HomeScreen(playlist: ParsedPlaylist, isTv: Boolean, onPlay: (ChannelItem) -> Unit) {
    val groups = remember(playlist) { playlist.channels.map { it.group }.distinct().sorted() }
    var group by remember { mutableStateOf(groups.firstOrNull() ?: "Outros") }
    val visible = remember(group, playlist) { playlist.channels.filter { it.group == group } }

    Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vira", color = Color.White, fontSize = if (isTv) 34.sp else 26.sp)
            Text("Play", color = Cyan, fontSize = if (isTv) 34.sp else 26.sp)
            Spacer(Modifier.weight(1f))
            playlist.epgUrl?.let { Text("EPG detectado", color = Color(0xFF69F0AE), fontSize = 12.sp) }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { g ->
                FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g) })
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isTv) 5 else 2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visible, key = { it.name + it.url }) { ch ->
                ChannelCard(ch, isTv) { onPlay(ch) }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: ChannelItem, isTv: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        modifier = Modifier
            .height(if (isTv) 112.dp else 104.dp)
            .fillMaxWidth()
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(channel.name, color = Color.White, maxLines = 2, fontSize = if (isTv) 17.sp else 15.sp)
            Text(channel.group, color = Cyan, maxLines = 1, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VideoScreen(channel: ChannelItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(channel.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize()
        )
        Button(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) { Text("Voltar") }
    }
}
