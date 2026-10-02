package com.viraplay.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.viraplay.shared.ChannelItem
import com.viraplay.shared.ContentType
import com.viraplay.shared.ParsedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF8B3DFF)
private val Green = Color(0xFF4BE38A)

private enum class HomeSection(val label: String) {
    LIVE("Ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    FAVORITES("Favoritos")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViraPlayApp()
        }
    }
}

@Composable
fun ViraPlayApp() {
    val context = LocalContext.current

    val uiMode =
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    val isTv =
        uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    val identity = remember { DeviceIdentity(context) }
    val repo = remember { PlayerRepository() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Conectando...") }
    var parsed by remember { mutableStateOf<ParsedPlaylist?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChannelItem?>(null) }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            status = "Conectando..."

            repo.register(
                identity.deviceId,
                identity.deviceSecret,
                identity.pairingCode,
                if (isTv) "ANDROID_TV" else "ANDROID_MOBILE"
            )

            val cfg = repo.config(identity.deviceId, identity.deviceSecret)
            enabled = cfg.enabled

            val playlistUrl = cfg.playlistUrl

            if (cfg.enabled && !playlistUrl.isNullOrBlank()) {
                status = "Baixando lista..."

                val result = repo.playlist(playlistUrl)

                if (result.channels.isEmpty()) {
                    throw IllegalStateException("Lista vazia")
                }

                parsed = result
                status = "${result.channels.size} itens carregados"
            } else {
                status =
                    if (cfg.enabled) {
                        "Aguardando lista"
                    } else {
                        "Aguardando ativação"
                    }
            }
        } catch (e: Exception) {
            status =
                e.message
                    ?.take(90)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Falha de conexão"
        }
    }

    LaunchedEffect(Unit) {
        while (parsed == null) {
            refresh()
            if (parsed == null) {
                delay(8_000)
            }
        }
    }

    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Cyan,
                secondary = Purple,
                background = Bg,
                surface = Panel
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Bg
        ) {
            when {
                selected != null -> {
                    VideoScreen(
                        channel = selected!!,
                        onBack = { selected = null }
                    )
                }

                parsed != null && enabled -> {
                    HomeScreen(
                        playlist = parsed!!,
                        isTv = isTv,
                        onPlay = { selected = it }
                    )
                }

                else -> {
                    ActivationScreen(
                        code = identity.pairingCode,
                        status = status,
                        onRefresh = {
                            scope.launch { refresh() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivationScreen(
    code: String,
    status: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ViraPlay",
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Ative seu dispositivo",
            color = Cyan,
            fontSize = 24.sp
        )

        Spacer(Modifier.height(26.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Text(
                text = code,
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 20.dp),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Envie este código para o atendimento ViraPlay.",
            color = Color.LightGray
        )

        Text(
            text = status,
            color = Cyan,
            modifier = Modifier.padding(top = 10.dp)
        )

        Button(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text("Atualizar")
        }
    }
}

@Composable
private fun HomeScreen(
    playlist: ParsedPlaylist,
    isTv: Boolean,
    onPlay: (ChannelItem) -> Unit
) {
    val context = LocalContext.current

    var favoriteUrls by remember {
        mutableStateOf(loadFavorites(context))
    }

    var section by remember { mutableStateOf(HomeSection.LIVE) }
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Todos") }

    val sectionItems =
        remember(section, playlist, favoriteUrls) {
            when (section) {
                HomeSection.LIVE ->
                    playlist.channels.filter { it.type == ContentType.LIVE }

                HomeSection.MOVIES ->
                    playlist.channels.filter { it.type == ContentType.MOVIE }

                HomeSection.SERIES ->
                    playlist.channels.filter { it.type == ContentType.SERIES }

                HomeSection.FAVORITES ->
                    playlist.channels.filter { favoriteUrls.contains(it.url) }
            }
        }

    val searched =
        remember(sectionItems, search) {
            if (search.isBlank()) {
                sectionItems
            } else {
                sectionItems.filter {
                    it.name.contains(search, ignoreCase = true) ||
                        it.group.contains(search, ignoreCase = true)
                }
            }
        }

    val groups =
        remember(searched) {
            listOf("Todos") +
                searched
                    .map { it.group }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
        }

    val activeGroup = if (groups.contains(group)) group else "Todos"

    val visible =
        remember(searched, activeGroup) {
            if (activeGroup == "Todos") {
                searched
            } else {
                searched.filter { it.group == activeGroup }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(if (isTv) 24.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Vira",
                color = Color.White,
                fontSize = if (isTv) 34.sp else 28.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Play",
                color = Cyan,
                fontSize = if (isTv) 34.sp else 28.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "${playlist.channels.size} itens",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HomeSection.entries) { item ->
                FilterChip(
                    selected = item == section,
                    onClick = {
                        section = item
                        group = "Todos"
                        search = ""
                    },
                    label = { Text(item.label) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar") }
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { item ->
                FilterChip(
                    selected = item == activeGroup,
                    onClick = { group = item },
                    label = { Text(item, maxLines = 1) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text =
                        if (section == HomeSection.FAVORITES) {
                            "Nenhum favorito ainda"
                        } else {
                            "Nenhum conteúdo encontrado"
                        },
                    color = Color.Gray
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTv) 5 else 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    visible,
                    key = { it.name + it.url }
                ) { item ->
                    val favorite = favoriteUrls.contains(item.url)

                    ChannelCard(
                        channel = item,
                        isTv = isTv,
                        favorite = favorite,
                        onFavorite = {
                            favoriteUrls =
                                toggleFavorite(
                                    context = context,
                                    current = favoriteUrls,
                                    url = item.url
                                )
                        },
                        onClick = { onPlay(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelItem,
    isTv: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        modifier =
            Modifier
                .height(if (isTv) 128.dp else 120.dp)
                .fillMaxWidth()
                .focusable()
                .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = if (isTv) 17.sp else 15.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (favorite) "★" else "☆",
                    color = if (favorite) Cyan else Color.Gray,
                    fontSize = 24.sp,
                    modifier =
                        Modifier
                            .padding(start = 6.dp)
                            .clickable { onFavorite() }
                )
            }

            Column {
                Text(
                    text =
                        when (channel.type) {
                            ContentType.LIVE -> "AO VIVO"
                            ContentType.MOVIE -> "FILME"
                            ContentType.SERIES -> "SÉRIE"
                        },
                    color =
                        when (channel.type) {
                            ContentType.LIVE -> Green
                            ContentType.MOVIE -> Cyan
                            ContentType.SERIES -> Purple
                        },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = channel.group,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun VideoScreen(
    channel: ChannelItem,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current

    val player =
        remember(channel.url) {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(channel.url))
                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                Button(onClick = onBack) {
                    Text("Voltar")
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = channel.name,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun loadFavorites(context: Context): Set<String> {
    val prefs =
        context.getSharedPreferences(
            "viraplay_favorites",
            Context.MODE_PRIVATE
        )

    return prefs
        .getStringSet("urls", emptySet())
        ?.toSet()
        ?: emptySet()
}

private fun toggleFavorite(
    context: Context,
    current: Set<String>,
    url: String
): Set<String> {
    val updated =
        current
            .toMutableSet()
            .apply {
                if (contains(url)) {
                    remove(url)
                } else {
                    add(url)
                }
            }
            .toSet()

    context
        .getSharedPreferences(
            "viraplay_favorites",
            Context.MODE_PRIVATE
        )
        .edit()
        .putStringSet("urls", updated)
        .apply()

    return updated
}
