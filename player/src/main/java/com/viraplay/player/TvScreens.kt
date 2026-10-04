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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.ContentType
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
            onSupport = onSupport,
            onSettings = onSettings
        )
        return
    }

    BackHandler { onSection(MainSection.HOME) }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(VpBg, Color(0xFF06152C), VpBg))
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
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(version) {
        counts = withContext(Dispatchers.IO) { readCounts() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(VpBg, VpPanel.copy(alpha = 0.78f), VpBg)
                )
            )
            .padding(horizontal = 42.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandWordmark(large = true)
        Text(
            "Filmes, séries e TV ao vivo em um só lugar",
            color = VpMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        accessNotice?.let {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = VpPurple.copy(alpha = 0.17f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FocusTile(
                onClick = { onSection(MainSection.LIVE) },
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
                    .focusRequester(firstFocus)
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF0A4770), Color(0xFF09203C), Color(0xFF151337)))
                    )
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(26.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(color = VpCyan.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                            Text("●  AO VIVO", color = VpCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Column {
                            Text("TV", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
                            Text("Canais ao vivo", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("${counts.live} canais disponíveis", color = VpMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            Column(
                Modifier.weight(1.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeTile("Filmes", "${counts.movies} títulos", Modifier.weight(1f)) {
                        onSection(MainSection.MOVIES)
                    }
                    HomeTile("Séries", "${counts.series} títulos", Modifier.weight(1f)) {
                        onSection(MainSection.SERIES)
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeTile("Favoritos", "Sua seleção", Modifier.weight(1f)) {
                        onSection(MainSection.FAVORITES)
                    }
                    HomeTile("Continuar", "${counts.continueWatching} em andamento", Modifier.weight(1f)) {
                        onContinue()
                    }
                }
            }

            Column(
                Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeTile("Suporte", "WhatsApp ViraPlay", Modifier.weight(1f), onSupport)
                HomeTile("Configurações", "Qualidade, PIN e aparelho", Modifier.weight(1f), onSettings)
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            color = VpPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("ViraPlay ${BuildConfig.VERSION_NAME}", color = VpCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(20.dp))
                Text(status, color = VpMuted)
                Spacer(Modifier.weight(1f))
                Text("Experiência TV • ViraPlay", color = VpMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusTile(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF0A203C), Color(0xFF08162C), VpPurple.copy(alpha = 0.10f)))
            ).padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Box(Modifier.width(34.dp).height(3.dp).background(VpCyan, RoundedCornerShape(50)))
                Spacer(Modifier.height(10.dp))
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = VpMuted, fontSize = 12.sp)
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvNavChip("Início", section == MainSection.HOME, onHome)
        TvNavChip("Ao vivo", section == MainSection.LIVE) { onSection(MainSection.LIVE) }
        TvNavChip("Filmes", section == MainSection.MOVIES) { onSection(MainSection.MOVIES) }
        TvNavChip("Séries", section == MainSection.SERIES) { onSection(MainSection.SERIES) }
        TvNavChip("Favoritos", section == MainSection.FAVORITES) { onSection(MainSection.FAVORITES) }
        Spacer(Modifier.weight(1f))
        TvNavChip("Atualizar", false, onRefresh)
        TvNavChip("Ajustes", false, onSettings)
        TvNavChip("Suporte", false, onSupport)
    }
}

@Composable
private fun TvNavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.height(44.dp).widthIn(min = 78.dp, max = 112.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) VpCyan else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
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
        epg = emptyList()
        val item = selected ?: return@LaunchedEffect
        delay(900)
        epg = withContext(Dispatchers.IO) { repository.epg(item) }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.width(270.dp).fillMaxHeight()) {
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
            modifier = Modifier.weight(1.1f).fillMaxHeight(),
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
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                name,
                color = if (selected) VpCyan else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(count.toString(), color = VpMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TvLivePreview(
    db: CatalogDb,
    item: CatalogItem?,
    previewItem: CatalogItem?,
    epg: List<EpgProgram>,
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
        colors = CardDefaults.cardColors(containerColor = VpPanel),
        shape = RoundedCornerShape(18.dp),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Prévia", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text("OK: iniciar prévia • OK novamente: tela cheia", color = VpMuted, fontSize = 11.sp)
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

            if (epg.isEmpty()) {
                Text(
                    "Programação não disponível para este canal.",
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
