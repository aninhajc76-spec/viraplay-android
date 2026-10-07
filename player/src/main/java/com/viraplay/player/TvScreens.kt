package com.viraplay.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.ContentType
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TvHomeCounts(
    val continueWatching: Int,
    val live: Int,
    val movies: Int,
    val series: Int
)

private data class TvVodLoad(
    val categories: List<CategoryEntry>,
    val total: Int,
    val favorites: Int,
    val continueWatching: Int,
    val items: List<CatalogItem>
)

private object TvLiveSession {
    var categoryId: String = "ALL"
    var channelKey: String? = null
    var categoryIndex: Int = 0
    var categoryOffset: Int = 0
    var channelIndex: Int = 0
    var channelOffset: Int = 0
}

@Composable
fun TvShell(
    db: CatalogDb,
    repository: ContentRepository,
    section: MainSection,
    catalogVersion: Int,
    playbackVersion: Int,
    status: String,
    accessNotice: String?,
    adultUnlocked: Boolean,
    onSection: (MainSection) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onChanged: () -> Unit
) {
    var showContinue by remember { mutableStateOf(false) }

    if (section == MainSection.HOME && showContinue) {
        BackHandler { showContinue = false }
        TvContinue(
            db = db,
            version = catalogVersion + playbackVersion,
            adultUnlocked = adultUnlocked,
            onBack = { showContinue = false },
            onOpen = onOpen
        )
        return
    }

    if (section == MainSection.HOME) {
        TvHome(
            db = db,
            version = catalogVersion + playbackVersion,
            status = status,
            accessNotice = accessNotice,
            onSection = onSection,
            onContinue = { showContinue = true },
            onOpen = onOpen,
            onSupport = onSupport,
            onSettings = onSettings
        )
        return
    }

    BackHandler { onSection(MainSection.HOME) }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(VpBg, Color(0xFF071A35), Color(0xFF080A25), VpBg))
        )
    ) {
        TvTopNav(section, onSection, { onSection(MainSection.HOME) }, onSupport, onSettings, onRefresh)
        when (section) {
            MainSection.LIVE -> TvLive(
                db = db,
                repository = repository,
                catalogVersion = catalogVersion,
                adultUnlocked = adultUnlocked,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.MOVIES -> TvVod(
                db = db,
                type = ContentType.MOVIE,
                version = catalogVersion + playbackVersion,
                adultUnlocked = adultUnlocked,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.SERIES -> TvVod(
                db = db,
                type = ContentType.SERIES,
                version = catalogVersion + playbackVersion,
                adultUnlocked = adultUnlocked,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.FAVORITES -> TvFavorites(
                db = db,
                version = catalogVersion + playbackVersion,
                adultUnlocked = adultUnlocked,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.HOME -> Unit
        }
    }
}

@Composable
private fun TvHome(
    db: CatalogDb,
    version: Int,
    status: String,
    accessNotice: String?,
    onSection: (MainSection) -> Unit,
    onContinue: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit
) {
    fun readCounts() = TvHomeCounts(
        continueWatching = db.continueWatching(100).size,
        live = db.count(ContentType.LIVE),
        movies = db.count(ContentType.MOVIE),
        series = db.count(ContentType.SERIES)
    )

    var counts by remember { mutableStateOf(readCounts()) }
    var featured by remember { mutableStateOf<CatalogItem?>(null) }
    var continueItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(140)
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            counts = readCounts()
            continueItems = db.continueWatching(10)
            featured = continueItems.firstOrNull()
                ?: db.query(ContentType.MOVIE, limit = 20).firstOrNull()
                ?: db.query(ContentType.SERIES, limit = 20).firstOrNull()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF020714), Color(0xFF071A35), Color(0xFF0B092B))
                )
            )
    ) {
        // Menu lateral: pensado para controle remoto e inspirado no mockup aprovado.
        Column(
            Modifier
                .width(225.dp)
                .fillMaxHeight()
                .background(Color(0xFF040A1C).copy(alpha = .985f))
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            BrandWordmark(large = false)
            Text(
                "TV EXPERIENCE",
                color = VpCyan.copy(alpha = .82f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 18.dp)
            )

            TvSideRailButton("Início", Icons.Filled.Home, true, Modifier.focusRequester(firstFocus)) { }
            TvSideRailButton("Ao vivo", Icons.Filled.LiveTv, false) { onSection(MainSection.LIVE) }
            TvSideRailButton("Filmes", Icons.Filled.Movie, false) { onSection(MainSection.MOVIES) }
            TvSideRailButton("Séries", Icons.Filled.VideoLibrary, false) { onSection(MainSection.SERIES) }
            TvSideRailButton("Favoritos", Icons.Filled.Favorite, false) { onSection(MainSection.FAVORITES) }
            TvSideRailButton("Continuar", Icons.Filled.History, false) { onContinue() }

            Spacer(Modifier.weight(1f))

            TvSideRailButton("Configurações", Icons.Filled.Settings, false) { onSettings() }
            Spacer(Modifier.height(8.dp))
            Text(
                "VPlayo ${BuildConfig.VERSION_NAME}",
                color = VpMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Bem-vindo ao VPlayo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(status, color = VpMuted, fontSize = 10.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                accessNotice?.let {
                    Surface(
                        color = VpPurple.copy(alpha = .14f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VpPurple.copy(alpha = .36f))
                    ) {
                        Text(it, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White) }
            }

            Spacer(Modifier.height(12.dp))

            // Hero principal, com backdrop real do catálogo quando disponível.
            FocusTile(
                onClick = { featured?.let(onOpen) ?: onSection(MainSection.MOVIES) },
                modifier = Modifier.fillMaxWidth().weight(1.12f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = featured?.backdrop ?: featured?.image,
                        contentDescription = featured?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF04101F).copy(alpha = .98f),
                                    Color(0xFF07152A).copy(alpha = .80f),
                                    Color.Transparent
                                )
                            )
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xFF030817).copy(alpha = .85f))
                            )
                        )
                    )

                    Column(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 28.dp, end = 18.dp)
                            .fillMaxWidth(.54f)
                    ) {
                        Surface(
                            color = VpCyan.copy(alpha = .14f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VpCyan.copy(alpha = .68f))
                        ) {
                            Text(
                                if (featured?.type == ContentType.LIVE) "AO VIVO" else "DESTAQUE VPLAYO",
                                color = VpCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            featured?.name ?: "Seu conteúdo em uma experiência premium",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        featured?.plot?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                color = Color.White.copy(alpha = .76f),
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 7.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = VpPurple, shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Assistir agora", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = .46f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    featured?.categoryName ?: "VPlayo",
                                    color = Color.White.copy(alpha = .88f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue assistindo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("${counts.live} canais • ${counts.movies} filmes • ${counts.series} séries", color = VpMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (continueItems.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(108.dp)) {
                    PremiumActionCard("Ao vivo", "${counts.live} canais", Modifier.weight(1f)) { onSection(MainSection.LIVE) }
                    PremiumActionCard("Filmes", "${counts.movies} disponíveis", Modifier.weight(1f)) { onSection(MainSection.MOVIES) }
                    PremiumActionCard("Séries", "${counts.series} disponíveis", Modifier.weight(1f)) { onSection(MainSection.SERIES) }
                    PremiumActionCard("Favoritos", "Acesso rápido", Modifier.weight(1f)) { onSection(MainSection.FAVORITES) }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(118.dp)
                ) {
                    items(continueItems.take(6), key = { it.itemKey }) { item ->
                        TvLandscapeCard(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSideRailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusTile(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    if (selected) Brush.horizontalGradient(listOf(VpPurple.copy(alpha = .96f), Color(0xFF3C25A8), Color(0xFF132B63)))
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label, tint = if (selected) Color.White else VpCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun TvLandscapeCard(item: CatalogItem, onClick: () -> Unit) {
    FocusTile(onClick = onClick, modifier = Modifier.width(210.dp).fillMaxHeight()) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdrop ?: item.image,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (item.type == ContentType.LIVE) "Ao vivo" else item.categoryName,
                    color = VpCyan,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PremiumHomeCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    accent: Color,
    onClick: () -> Unit
) {
    FocusTile(onClick = onClick, modifier = modifier.fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = .16f),
                            Color(0xFF0A1728),
                            Color(0xFF07111F)
                        )
                    )
                )
                .padding(17.dp)
        ) {
            Surface(
                color = accent.copy(alpha = .16f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    accent.copy(alpha = .24f)
                ),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box(
                    Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    title,
                    color = accent,
                    fontSize = 9.sp,
                    letterSpacing = .6.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    value,
                    color = Color.White,
                    fontSize = if (value.length > 4) 22.sp else 29.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(subtitle, color = VpMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PremiumActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FocusTile(onClick = onClick, modifier = modifier.fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0A182A), Color(0xFF07111F))
                    )
                )
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(listOf(VpCyan, VpPurple)),
                        RoundedCornerShape(50)
                    )
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text(subtitle, color = VpMuted, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TvContinue(
    db: CatalogDb,
    version: Int,
    adultUnlocked: Boolean,
    onBack: () -> Unit,
    onOpen: (CatalogItem) -> Unit
) {
    var list by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(version, adultUnlocked) {
        list = withContext(Dispatchers.IO) {
            db.continueWatching(100).filter { adultUnlocked || !isAdultContent(it) }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Voltar") }
            Spacer(Modifier.width(14.dp))
            Text(
                "Continuar assistindo",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada em andamento.", color = VpMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(list, key = { it.itemKey }) { item ->
                    PosterCard(item, 170.dp, { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun TvTopNav(
    section: MainSection,
    onSection: (MainSection) -> Unit,
    onHome: () -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = Color(0xFF030817).copy(alpha = .995f),
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandWordmark()
            Spacer(Modifier.width(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TvNavChip("Início", section == MainSection.HOME, onHome)
                TvNavChip("Ao vivo", section == MainSection.LIVE) { onSection(MainSection.LIVE) }
                TvNavChip("Filmes", section == MainSection.MOVIES) { onSection(MainSection.MOVIES) }
                TvNavChip("Séries", section == MainSection.SERIES) { onSection(MainSection.SERIES) }
                TvNavChip("Favoritos", section == MainSection.FAVORITES) { onSection(MainSection.FAVORITES) }
            }
            Spacer(Modifier.weight(1f))
            TvNavChip("Atualizar", false, onRefresh)
            Spacer(Modifier.width(5.dp))
            TvNavChip("Ajustes", false, onSettings)
            Spacer(Modifier.width(5.dp))
            TvNavChip("Suporte", false, onSupport)
        }
    }
}

@Composable
private fun TvNavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.height(40.dp).widthIn(min = 78.dp, max = 114.dp)
    ) {
        Box(
            Modifier.fillMaxSize().background(
                if (selected) Brush.horizontalGradient(listOf(VpPurple, Color(0xFF4F35E7), Color(0xFF162E67)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(alpha = .72f),
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TvLive(
    db: CatalogDb,
    repository: ContentRepository,
    catalogVersion: Int,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PlaybackPreferences(context) }

    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf(TvLiveSession.categoryId) }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var rawItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var previewed by remember { mutableStateOf<CatalogItem?>(null) }
    var epg by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var epgLoading by remember { mutableStateOf(false) }
    var totalCount by remember { mutableIntStateOf(db.count(ContentType.LIVE)) }
    var limit by remember(selectedCategory, search) { mutableIntStateOf(700) }
    val categoryListState = rememberLazyListState(TvLiveSession.categoryIndex, TvLiveSession.categoryOffset)
    val channelListState = rememberLazyListState(TvLiveSession.channelIndex, TvLiveSession.channelOffset)

    LaunchedEffect(categoryListState) {
        snapshotFlow { categoryListState.firstVisibleItemIndex to categoryListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                TvLiveSession.categoryIndex = index
                TvLiveSession.categoryOffset = offset
            }
    }
    LaunchedEffect(channelListState) {
        snapshotFlow { channelListState.firstVisibleItemIndex to channelListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                TvLiveSession.channelIndex = index
                TvLiveSession.channelOffset = offset
            }
    }

    LaunchedEffect(selectedCategory, search, limit, catalogVersion, adultUnlocked, prefs.groupChannels) {
        val result = withContext(Dispatchers.IO) {
            val cats = db.categories(ContentType.LIVE)
                .filter { adultUnlocked || !isAdultCategory(it.name) }
            val total = db.count(ContentType.LIVE)
            val items = db.query(
                type = ContentType.LIVE,
                categoryId = selectedCategory,
                search = search,
                limit = limit
            ).filter { adultUnlocked || !isAdultContent(it) }
            Triple(cats, total, items)
        }

        categories = result.first
        totalCount = result.second
        rawItems = result.third

        val shown = if (prefs.groupChannels) groupLiveItems(rawItems) else rawItems
        val saved = TvLiveSession.channelKey?.let { key -> shown.firstOrNull { it.itemKey == key } }
        if (saved != null) {
            selected = saved
        } else if (selected == null || shown.none { it.itemKey == selected?.itemKey }) {
            selected = shown.firstOrNull()
            previewed = null
        }
    }

    val displayItems = remember(rawItems, prefs.groupChannels, catalogVersion) {
        if (prefs.groupChannels) groupLiveItems(rawItems) else rawItems
    }

    LaunchedEffect(displayItems, selectedCategory) {
        val savedKey = TvLiveSession.channelKey
        val index = if (savedKey == null) -1 else displayItems.indexOfFirst { it.itemKey == savedKey }
        if (index >= 0) {
            selected = displayItems[index]
            runCatching { channelListState.scrollToItem(index, TvLiveSession.channelOffset) }
        }
    }

    LaunchedEffect(selected?.itemKey) {
        val item = selected ?: run {
            epg = emptyList()
            epgLoading = false
            return@LaunchedEffect
        }

        epgLoading = true
        delay(350)
        epg = withContext(Dispatchers.IO) { repository.epg(item) }
        epgLoading = false
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.width(245.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Categorias",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(
                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        "Pesquisar",
                        tint = VpCyan
                    )
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(state = categoryListState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CategoryButton("Todos", totalCount, selectedCategory == "ALL") {
                        selectedCategory = "ALL"
                        TvLiveSession.categoryId = "ALL"
                        TvLiveSession.channelKey = null
                        TvLiveSession.channelIndex = 0
                        TvLiveSession.channelOffset = 0
                    }
                }
                items(categories, key = { it.id }) { c ->
                    CategoryButton(c.name, c.count, selectedCategory == c.id) {
                        selectedCategory = c.id
                        TvLiveSession.categoryId = c.id
                        TvLiveSession.channelKey = null
                        TvLiveSession.channelIndex = 0
                        TvLiveSession.channelOffset = 0
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        LazyColumn(
            state = channelListState,
            modifier = Modifier.weight(1.08f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(displayItems, key = { it.itemKey }) { item ->
                LiveRow(
                    item = item,
                    selected = selected?.itemKey == item.itemKey,
                    onClick = {
                        selected = item
                        TvLiveSession.channelKey = item.itemKey
                        if (previewed?.itemKey == item.itemKey) {
                            onOpen(item)
                        } else {
                            previewed = item
                        }
                    },
                    onFavorite = {
                        db.toggleFavorite(item.itemKey)
                        onChanged()
                    },
                    onFocused = { selected = item; TvLiveSession.channelKey = item.itemKey }
                )
            }

            if (rawItems.size >= limit) {
                item {
                    OutlinedButton(
                        onClick = { limit += 700 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Carregar mais canais")
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        TvLivePreview(
            db = db,
            item = selected,
            previewItem = previewed,
            epg = epg,
            epgLoading = epgLoading,
            modifier = Modifier.weight(1.02f).fillMaxHeight(),
            onPlay = { selected?.let(onOpen) },
            onFavorite = {
                selected?.let {
                    db.toggleFavorite(it.itemKey)
                    onChanged()
                }
            }
        )
    }
}

@Composable
private fun CategoryButton(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusTile(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) Brush.horizontalGradient(listOf(VpPurple.copy(alpha = .90f), Color(0xFF33248B), Color(0xFF0D3552)))
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (selected) VpCyan else VpBorder, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(9.dp))
            Text(
                name,
                color = Color.White,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (selected) Color.White.copy(alpha = .13f) else Color.Black.copy(alpha = .16f),
                shape = RoundedCornerShape(50)
            ) {
                Text(count.toString(), color = if (selected) Color.White else VpMuted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun TvLivePreview(
    db: CatalogDb,
    item: CatalogItem?,
    previewItem: CatalogItem?,
    epg: List<EpgProgram>,
    epgLoading: Boolean,
    modifier: Modifier,
    onPlay: () -> Unit,
    onFavorite: () -> Unit
) {
    var labels by remember(item?.itemKey) {
        mutableStateOf(item?.let { listOf(qualityLabel(it.name)) } ?: emptyList())
    }

    LaunchedEffect(item?.itemKey) {
        val current = item ?: return@LaunchedEffect
        delay(900)
        labels = withContext(Dispatchers.IO) {
            db.liveVariants(current)
                .map { qualityLabel(it.name) }
                .distinct()
                .sortedByDescending(::qualityRank)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071426)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VpCyan.copy(alpha = .34f)),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Selecione um canal", color = VpMuted)
                }
                return@Column
            }

            if (previewItem?.itemKey == item.itemKey) {
                LivePreviewPlayer(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.image.isNullOrBlank()) {
                        AsyncImage(
                            model = item.image,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            alpha = .52f,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        )
                    }

                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = .20f),
                                    Color.Black.copy(alpha = .78f)
                                )
                            )
                        )
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Prévia do canal", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text(
                            "OK: iniciar prévia • OK novamente: tela cheia",
                            color = Color.White.copy(alpha = .72f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                channelBaseName(item.name),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(item.categoryName, color = VpMuted, fontSize = 10.sp)

            if (labels.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    items(labels) { q ->
                        SuggestionChip(onClick = {}, label = { Text(q, fontSize = 9.sp) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Text("Assistir")
                }
                OutlinedButton(onClick = onFavorite) {
                    Text(if (item.favorite) "Favorito" else "+ Favorito")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Programação", color = VpCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))

            if (epgLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = VpCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Carregando programação...", color = VpMuted, fontSize = 11.sp)
                }
            } else if (epg.isEmpty()) {
                Text(
                    "EPG não fornecido pelo servidor para este canal.",
                    color = VpMuted,
                    fontSize = 11.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(epg) { program ->
                        Surface(
                            color = if (program == epg.first()) {
                                VpGreen.copy(alpha = 0.14f)
                            } else {
                                VpPanelAlt
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                Text(
                                    program.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                                if (program.start.isNotBlank()) {
                                    Text(
                                        "${shortClock(program.start)} - ${shortClock(program.end)}",
                                        color = VpCyan,
                                        fontSize = 9.sp
                                    )
                                }
                                if (program.description.isNotBlank()) {
                                    Text(
                                        program.description,
                                        color = VpMuted,
                                        fontSize = 9.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvVod(
    db: CatalogDb,
    type: ContentType,
    version: Int,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selection by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var limit by remember(selection, search) { mutableIntStateOf(180) }
    var totalCount by remember { mutableIntStateOf(db.count(type)) }
    var favoriteCount by remember { mutableIntStateOf(0) }
    var continueCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(type, selection, search, limit, version, adultUnlocked) {
        val result = withContext(Dispatchers.IO) {
            val cats = db.categories(type).filter { adultUnlocked || !isAdultCategory(it.name) }
            val total = db.count(type)
            val favCount = db.favorites(limit = 1500)
                .count { it.type == type && (adultUnlocked || !isAdultContent(it)) }
            val contCount = db.continueWatching(100).count {
                val correctType =
                    if (type == ContentType.MOVIE) it.type == ContentType.MOVIE
                    else it.type == ContentType.EPISODE
                correctType && (adultUnlocked || !isAdultContent(it))
            }
            val loaded = when (selection) {
                "FAVORITES" -> db.favorites(search, 800).filter { it.type == type }
                "CONTINUE" -> db.continueWatching(100).filter {
                    if (type == ContentType.MOVIE) it.type == ContentType.MOVIE
                    else it.type == ContentType.EPISODE
                }
                else -> db.query(type, selection, search, limit)
            }.filter { adultUnlocked || !isAdultContent(it) }

            TvVodLoad(
                categories = cats,
                total = total,
                favorites = favCount,
                continueWatching = contCount,
                items = loaded
            )
        }

        categories = result.categories
        totalCount = result.total
        favoriteCount = result.favorites
        continueCount = result.continueWatching
        list = result.items
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Column(Modifier.width(285.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (type == ContentType.MOVIE) "Filmes" else "Séries",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(
                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        "Pesquisar",
                        tint = VpCyan
                    )
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CategoryButton("Favoritos", favoriteCount, selection == "FAVORITES") {
                        selection = "FAVORITES"
                    }
                }
                item {
                    CategoryButton("Continuar assistindo", continueCount, selection == "CONTINUE") {
                        selection = "CONTINUE"
                    }
                }
                item {
                    CategoryButton("Todos", totalCount, selection == "ALL") {
                        selection = "ALL"
                    }
                }
                items(categories, key = { it.id }) { c ->
                    CategoryButton(c.name, c.count, selection == c.id) {
                        selection = c.id
                    }
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VpCyan)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(list, key = { it.itemKey }) { item ->
                    PosterCard(
                        item = item,
                        width = 180.dp,
                        onClick = { onOpen(item) },
                        onFavorite = {
                            if (item.type != ContentType.EPISODE) {
                                db.toggleFavorite(item.itemKey)
                                onChanged()
                            }
                        }
                    )
                }

                if (
                    selection != "FAVORITES" &&
                    selection != "CONTINUE" &&
                    list.size >= limit
                ) {
                    item(span = { GridItemSpan(5) }) {
                        OutlinedButton(
                            onClick = { limit += 180 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Carregar mais")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFavorites(
    db: CatalogDb,
    version: Int,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(search, version, adultUnlocked) {
        list = withContext(Dispatchers.IO) {
            db.favorites(search, 1000).filter { adultUnlocked || !isAdultContent(it) }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Favoritos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    "Pesquisar",
                    tint = VpCyan
                )
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Pesquisar favoritos") },
                singleLine = true,
                modifier = Modifier.width(420.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(list, key = { it.itemKey }) { item ->
                PosterCard(
                    item = item,
                    width = 170.dp,
                    onClick = { onOpen(item) },
                    onFavorite = {
                        db.toggleFavorite(item.itemKey)
                        onChanged()
                    }
                )
            }
        }
    }
}

private fun shortClock(value: String): String {
    val v = value.trim()
    val time = if (v.contains(' ')) v.substringAfterLast(' ') else v
    return if (time.length >= 5) time.take(5) else time
}
