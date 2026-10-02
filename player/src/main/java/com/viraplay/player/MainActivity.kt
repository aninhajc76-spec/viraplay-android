package com.viraplay.player

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.viraplay.shared.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Panel2 = Color(0xFF0A2040)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF9A35FF)
private val Green = Color(0xFF4BE38A)
private val Danger = Color(0xFFFF5874)
private val Muted = Color(0xFF94A0B8)

private enum class Section(val label: String) {
    HOME("Início"), LIVE("Ao vivo"), MOVIES("Filmes"), SERIES("Séries"), FAVORITES("Favoritos")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ViraPlayApp() }
    }
}

@Composable
private fun ViraPlayApp() {
    val context = LocalContext.current
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val identity = remember { DeviceIdentity(context) }
    val repo = remember { PlayerRepository() }
    val db = remember { CatalogDb(context) }
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(Section.HOME) }
    var status by remember { mutableStateOf(if (db.hasCatalog()) "${db.countAll()} itens disponíveis" else "Conectando...") }
    var enabled by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(!db.hasCatalog()) }
    var version by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var selectedSeries by remember { mutableStateOf<String?>(null) }
    var group by remember { mutableStateOf("Todos") }
    var search by remember { mutableStateOf("") }

    suspend fun sync(force: Boolean) {
        try {
            val cfg = withContext(Dispatchers.IO) {
                repo.register(identity.deviceId, identity.deviceSecret, identity.pairingCode, if (isTv) "ANDROID_TV" else "ANDROID_MOBILE")
                repo.config(identity.deviceId, identity.deviceSecret)
            }
            enabled = cfg.enabled
            if (!cfg.enabled) {
                status = "Dispositivo bloqueado"
                return
            }
            val remote = cfg.playlistUrl
            if (remote.isNullOrBlank()) {
                status = "Aguardando ativação"
                return
            }
            val hasCache = withContext(Dispatchers.IO) { db.hasCatalog() }
            val cachedUrl = withContext(Dispatchers.IO) { db.getMeta("playlist_url") }
            if (force || !hasCache || cachedUrl != remote) {
                loading = !hasCache
                status = if (hasCache) "Atualizando catálogo em segundo plano..." else "Preparando catálogo pela primeira vez..."
                val total = withContext(Dispatchers.IO) { repo.syncCatalog(remote, db) }
                status = "$total itens disponíveis"
                version++
            } else {
                status = "${withContext(Dispatchers.IO) { db.countAll() }} itens disponíveis"
            }
        } catch (e: Throwable) {
            status = "Falha: ${(e.message ?: "não foi possível conectar").replace("\n", " ").take(100)}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        sync(false)
        while (true) {
            delay(30_000)
            try {
                val cfg = withContext(Dispatchers.IO) { repo.config(identity.deviceId, identity.deviceSecret) }
                enabled = cfg.enabled
                if (!cfg.enabled) status = "Dispositivo bloqueado"
            } catch (_: Throwable) { }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cyan, secondary = Purple, background = Bg, surface = Panel)
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            when {
                !enabled -> ActivationScreen(
                    title = "Acesso bloqueado",
                    status = "Entre em contato com o atendimento ViraPlay.",
                    code = identity.pairingCode,
                    loading = false,
                    onRefresh = { scope.launch { sync(false) } }
                )
                !db.hasCatalog() -> ActivationScreen(
                    title = "Ative seu dispositivo",
                    status = status,
                    code = identity.pairingCode,
                    loading = loading,
                    onRefresh = { scope.launch { sync(true) } }
                )
                else -> MainScreen(
                    db = db,
                    isTv = isTv,
                    section = section,
                    status = status,
                    group = group,
                    search = search,
                    version = version,
                    onSection = { section = it; group = "Todos"; search = "" },
                    onGroup = { group = it },
                    onSearch = { search = it },
                    onPlay = { selected = it },
                    onSeries = { selectedSeries = it },
                    onRefresh = { scope.launch { sync(true) } },
                    onChanged = { version++ }
                )
            }

            selectedSeries?.let { key ->
                SeriesScreen(db, key, isTv, onBack = { selectedSeries = null }, onPlay = { selected = it })
            }

            selected?.let { item ->
                VideoScreen(item, db, isTv, onBack = { selected = null; version++ })
            }
        }
    }
}

@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.viraplay_wordmark),
        contentDescription = "ViraPlay",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
private fun ActivationScreen(title: String, status: String, code: String, loading: Boolean, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogo(Modifier.fillMaxWidth(0.78f).height(120.dp))
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(status, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 20.dp)) {
                    Text(code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 3.sp, modifier = Modifier.padding(horizontal = 30.dp, vertical = 18.dp))
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 18.dp), color = Cyan)
                Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(if (loading) "Carregando..." else "Atualizar")
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    db: CatalogDb,
    isTv: Boolean,
    section: Section,
    status: String,
    group: String,
    search: String,
    version: Int,
    onSection: (Section) -> Unit,
    onGroup: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onSeries: (String) -> Unit,
    onRefresh: () -> Unit,
    onChanged: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(Modifier.width(190.dp).height(60.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(status, color = Muted, fontSize = 10.sp, maxLines = 1)
                TextButton(onClick = onRefresh) { Text("Atualizar") }
            }
        }

        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Section.entries) { item ->
                FilterChip(selected = section == item, onClick = { onSection(item) }, label = { Text(item.label) })
            }
        }

        if (section != Section.HOME) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                singleLine = true,
                label = { Text("Buscar conteúdo") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        when (section) {
            Section.HOME -> HomeContent(db, version, isTv, onPlay, onSection)
            Section.LIVE -> LibraryContent(db, ContentType.LIVE, group, search, isTv, version, onGroup, onPlay, onSeries, onChanged)
            Section.MOVIES -> LibraryContent(db, ContentType.MOVIE, group, search, isTv, version, onGroup, onPlay, onSeries, onChanged)
            Section.SERIES -> LibraryContent(db, ContentType.SERIES, group, search, isTv, version, onGroup, onPlay, onSeries, onChanged)
            Section.FAVORITES -> FavoritesContent(db, search, isTv, version, onPlay, onSeries, onChanged)
        }
    }
}

@Composable
private fun HomeContent(db: CatalogDb, version: Int, isTv: Boolean, onPlay: (CatalogItem) -> Unit, onSection: (Section) -> Unit) {
    var continueItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(version) {
        val data = withContext(Dispatchers.IO) {
            Triple(db.continueWatching(), db.queryItems(ContentType.MOVIE, limit = 24), db.querySeries(limit = 24))
        }
        continueItems = data.first
        movies = data.second
        series = data.third
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Seu entretenimento, organizado.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("TV ao vivo, filmes, séries, favoritos e continuação automática.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                        item { Button(onClick = { onSection(Section.LIVE) }) { Text("TV ao vivo") } }
                        item { OutlinedButton(onClick = { onSection(Section.MOVIES) }) { Text("Filmes") } }
                        item { OutlinedButton(onClick = { onSection(Section.SERIES) }) { Text("Séries") } }
                    }
                }
            }
        }
        if (continueItems.isNotEmpty()) item { PosterRow("Continuar assistindo", continueItems, isTv, onPlay) }
        if (movies.isNotEmpty()) item { PosterRow("Filmes", movies, isTv, onPlay) }
        if (series.isNotEmpty()) item {
            PosterRow("Séries", series, isTv) { onSection(Section.SERIES) }
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<CatalogItem>, isTv: Boolean, onClick: (CatalogItem) -> Unit) {
    Column {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            items(list, key = { it.url }) { item -> PosterCard(item, if (isTv) 180.dp else 138.dp) { onClick(item) } }
        }
    }
}

@Composable
private fun LibraryContent(
    db: CatalogDb,
    type: ContentType,
    group: String,
    search: String,
    isTv: Boolean,
    version: Int,
    onGroup: (String) -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onSeries: (String) -> Unit,
    onChanged: () -> Unit
) {
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var data by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(type, group, search, version) {
        val loaded = withContext(Dispatchers.IO) {
            val g = listOf("Todos") + db.groups(type)
            val d = if (type == ContentType.SERIES) db.querySeries(group, search) else db.queryItems(type, group, search)
            g to d
        }
        groups = loaded.first
        data = loaded.second
    }

    val wide = isTv || LocalConfiguration.current.screenWidthDp >= 900
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(235.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups) { g ->
                    Surface(
                        color = if (group == g) Purple.copy(alpha = 0.30f) else Panel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onGroup(g) }
                    ) { Text(g, color = if (group == g) Color.White else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp)) }
                }
            }
            ContentGrid(data, type, if (type == ContentType.LIVE) 3 else 5, onPlay, onSeries, db, onChanged)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { g -> FilterChip(selected = group == g, onClick = { onGroup(g) }, label = { Text(g, maxLines = 1) }) }
            }
            ContentGrid(data, type, if (type == ContentType.LIVE) 2 else 3, onPlay, onSeries, db, onChanged)
        }
    }
}

@Composable
private fun ContentGrid(
    data: List<CatalogItem>,
    type: ContentType,
    columns: Int,
    onPlay: (CatalogItem) -> Unit,
    onSeries: (String) -> Unit,
    db: CatalogDb,
    onChanged: () -> Unit
) {
    if (data.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum conteúdo encontrado", color = Muted) }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(data, key = { if (type == ContentType.SERIES) (it.seriesKey ?: it.name) else it.url }) { item ->
            if (type == ContentType.LIVE) {
                LiveCard(item, onClick = { onPlay(item) }, onFavorite = { db.toggleFavorite(item.url); onChanged() })
            } else {
                PosterCard(item, 200.dp) {
                    if (type == ContentType.SERIES) onSeries(item.seriesKey ?: item.name) else onPlay(item)
                }
            }
        }
    }
}

@Composable
private fun LiveCard(item: CatalogItem, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(112.dp).focusable().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(Panel2)
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.group, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (item.favorite) "★" else "☆", color = if (item.favorite) Cyan else Muted, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onFavorite))
        }
    }
}

@Composable
private fun PosterCard(item: CatalogItem, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(14.dp)).background(Panel)) {
            AsyncImage(model = item.logo, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.progressFraction > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.45f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.progressFraction).background(Cyan))
                }
            }
        }
        Text(item.seriesKey ?: item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun FavoritesContent(
    db: CatalogDb,
    search: String,
    isTv: Boolean,
    version: Int,
    onPlay: (CatalogItem) -> Unit,
    onSeries: (String) -> Unit,
    onChanged: () -> Unit
) {
    var data by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    LaunchedEffect(search, version) { data = withContext(Dispatchers.IO) { db.queryFavorites(search) } }
    ContentGrid(data, ContentType.MOVIE, if (isTv) 5 else 3, onPlay, onSeries, db, onChanged)
}

@Composable
private fun SeriesScreen(db: CatalogDb, key: String, isTv: Boolean, onBack: () -> Unit, onPlay: (CatalogItem) -> Unit) {
    var episodes by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    LaunchedEffect(key) { episodes = withContext(Dispatchers.IO) { db.episodes(key) } }
    BackHandler(onBack = onBack)

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Voltar") }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(key, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTv) 28.sp else 22.sp)
                    Text("${episodes.size} episódio(s)", color = Muted, fontSize = 12.sp)
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(episodes, key = { it.url }) { ep ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onPlay(ep) }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Panel2, shape = RoundedCornerShape(10.dp)) {
                                Text("T${ep.season ?: 1} • E${ep.episode ?: 1}", color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(ep.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                when {
                                    ep.watched -> Text("Assistido", color = Green, fontSize = 11.sp)
                                    ep.progressFraction > 0f -> Text("Continuar de onde parou", color = Cyan, fontSize = 11.sp)
                                }
                            }
                            Text("▶", color = Purple, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoScreen(item: CatalogItem, db: CatalogDb, isTv: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val progress = remember(item.url) { db.getProgress(item.url) }
    var error by remember(item.url) { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(true) }

    val player = remember(item.url) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(playbackException: PlaybackException) {
                    error = playbackException.message?.take(120) ?: "Falha ao reproduzir"
                }
            })
            setMediaItem(MediaItem.fromUri(item.url))
            prepare()
            if (item.type != ContentType.LIVE && progress.first > 10_000L) seekTo(progress.first)
            playWhenReady = true
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        while (true) {
            delay(4_000)
            if (item.type != ContentType.LIVE) {
                db.saveProgress(item.url, player.currentPosition, player.duration.takeIf { it > 0L } ?: 0L)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            if (item.type != ContentType.LIVE) db.saveProgress(item.url, player.currentPosition, player.duration.takeIf { it > 0L } ?: 0L)
            player.release()
            if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Voltar") }
                if (!isTv) {
                    OutlinedButton(
                        onClick = {
                            landscape = !landscape
                            activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Girar") }
                }
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
            error?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)), modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Não foi possível reproduzir", color = Danger, fontWeight = FontWeight.Bold)
                        Text(msg, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
