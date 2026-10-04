package com.viraplay.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MobileShell(
    db: CatalogDb,
    repository: ContentRepository,
    section: MainSection,
    catalogVersion: Int,
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
    if (section != MainSection.HOME) BackHandler { onSection(MainSection.HOME) }

    Scaffold(
        containerColor = VpBg,
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.horizontalGradient(listOf(VpBg, VpPanel.copy(alpha = 0.92f), VpBg))
                ).padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandWordmark()
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(accessNotice ?: status, color = if (accessNotice != null) VpCyan else VpMuted, fontSize = 9.sp, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Atualizar", tint = VpCyan) }
                            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = VpPanel, tonalElevation = 10.dp) {
                listOf(
                    MainSection.HOME to "Início",
                    MainSection.LIVE to "TV",
                    MainSection.MOVIES to "Filmes",
                    MainSection.SERIES to "Séries",
                    MainSection.FAVORITES to "Favoritos"
                ).forEach { (item, label) ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { onSection(item) },
                        icon = { Icon(navIcon(item), label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = VpCyan,
                            indicatorColor = VpPurple.copy(alpha = 0.28f),
                            unselectedIconColor = VpMuted,
                            unselectedTextColor = VpMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(VpBg, Color(0xFF041126), VpBg)))
        ) {
            when (section) {
                MainSection.HOME -> MobileHome(db, catalogVersion, accessNotice, adultUnlocked, onOpen, onSection, onSupport, onChanged)
                MainSection.LIVE -> MobileLibrary(db, repository, ContentType.LIVE, catalogVersion, adultUnlocked, onOpen, onChanged)
                MainSection.MOVIES -> MobileLibrary(db, repository, ContentType.MOVIE, catalogVersion, adultUnlocked, onOpen, onChanged)
                MainSection.SERIES -> MobileLibrary(db, repository, ContentType.SERIES, catalogVersion, adultUnlocked, onOpen, onChanged)
                MainSection.FAVORITES -> MobileFavorites(db, catalogVersion, adultUnlocked, onOpen, onChanged)
            }
        }
    }
}

private fun navIcon(section: MainSection): ImageVector = when (section) {
    MainSection.HOME -> Icons.Filled.Home
    MainSection.LIVE -> Icons.Filled.LiveTv
    MainSection.MOVIES -> Icons.Filled.Movie
    MainSection.SERIES -> Icons.Filled.VideoLibrary
    MainSection.FAVORITES -> Icons.Filled.Favorite
}

@Composable
private fun MobileHome(
    db: CatalogDb,
    catalogVersion: Int,
    accessNotice: String?,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onSection: (MainSection) -> Unit,
    onSupport: () -> Unit,
    onChanged: () -> Unit
) {
    var continueItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(catalogVersion, adultUnlocked) {
        withContext(Dispatchers.IO) {
            fun clean(list: List<CatalogItem>) = if (adultUnlocked) list else list.filterNot(::isAdultContent)
            continueItems = clean(db.continueWatching(20))
            movies = clean(db.query(ContentType.MOVIE, limit = 24))
            series = clean(db.query(ContentType.SERIES, limit = 24))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        accessNotice?.let { notice ->
            item {
                Surface(color = VpPurple.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(notice, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSupport) { Text("Renovar") }
                    }
                }
            }
        }

        item {
            Text("O que você quer assistir?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuick("TV ao vivo", Icons.Filled.LiveTv, Modifier.weight(1f)) { onSection(MainSection.LIVE) }
                HomeQuick("Filmes", Icons.Filled.Movie, Modifier.weight(1f)) { onSection(MainSection.MOVIES) }
                HomeQuick("Séries", Icons.Filled.VideoLibrary, Modifier.weight(1f)) { onSection(MainSection.SERIES) }
            }
        }

        if (continueItems.isNotEmpty()) item {
            ContentStrip("Continuar assistindo", continueItems, onOpen) {
                db.toggleFavorite(it.itemKey); onChanged()
            }
        }
        if (movies.isNotEmpty()) item {
            ContentStrip("Filmes", movies, onOpen) { db.toggleFavorite(it.itemKey); onChanged() }
        }
        if (series.isNotEmpty()) item {
            ContentStrip("Séries", series, onOpen) { db.toggleFavorite(it.itemKey); onChanged() }
        }
        item { OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Suporte ViraPlay") } }
    }
}

@Composable
private fun HomeQuick(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth().height(98.dp).background(
                Brush.linearGradient(listOf(Color(0xFF0A3157), VpPanel, VpPurple.copy(alpha = 0.18f)))
            )
        ) {
            TextButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, null, tint = VpCyan, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ContentStrip(title: String, items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit, onFavorite: (CatalogItem) -> Unit) {
    Column {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.itemKey }) { item ->
                PosterCard(item, 132.dp, { onOpen(item) }, onFavorite = { onFavorite(item) })
            }
        }
    }
}

@Composable
private fun MobileLibrary(
    db: CatalogDb,
    repository: ContentRepository,
    type: ContentType,
    catalogVersion: Int,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlaybackPreferences(context) }
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selectedCategory by remember(type) { mutableStateOf("ALL") }
    var search by remember(type) { mutableStateOf("") }
    var searchOpen by remember(type) { mutableStateOf(false) }
    var rawItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var limit by remember(type, selectedCategory, search) { mutableIntStateOf(300) }
    var totalCount by remember { mutableIntStateOf(0) }
    var previewItem by remember { mutableStateOf<CatalogItem?>(null) }

    LaunchedEffect(type, selectedCategory, search, limit, catalogVersion, adultUnlocked) {
        withContext(Dispatchers.IO) {
            categories = db.categories(type).filter { adultUnlocked || !isAdultCategory(it.name) }
            totalCount = db.count(type)
            rawItems = db.query(type, selectedCategory, search, limit).filter { adultUnlocked || !isAdultContent(it) }
        }
    }

    val displayItems = remember(rawItems, type, prefs.groupChannels, catalogVersion) {
        if (type == ContentType.LIVE && prefs.groupChannels) groupLiveItems(rawItems) else rawItems
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sectionTitle(type), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "Pesquisar", tint = VpCyan)
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Buscar ${sectionName(type)}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == "ALL",
                    onClick = { selectedCategory = "ALL" },
                    label = { Text("Todos ($totalCount)") }
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    selected = selectedCategory == category.id,
                    onClick = { selectedCategory = category.id },
                    label = { Text("${category.name} (${category.count})", maxLines = 1) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (type == ContentType.LIVE) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayItems, key = { it.itemKey }) { item ->
                    LiveRow(
                        item = item,
                        selected = false,
                        onClick = { previewItem = item },
                        onFavorite = { db.toggleFavorite(item.itemKey); onChanged() }
                    )
                }
                if (rawItems.size >= limit) item {
                    OutlinedButton(onClick = { limit += 300 }, modifier = Modifier.fillMaxWidth()) { Text("Carregar mais") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(displayItems, key = { it.itemKey }) { item ->
                    PosterCard(
                        item = item,
                        width = 140.dp,
                        onClick = { onOpen(item) },
                        onFavorite = { db.toggleFavorite(item.itemKey); onChanged() }
                    )
                }
                if (rawItems.size >= limit) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    OutlinedButton(onClick = { limit += 300 }, modifier = Modifier.fillMaxWidth()) { Text("Carregar mais") }
                }
            }
        }
    }

    previewItem?.let { initial ->
        MobileLivePreviewSheet(
            initial = initial,
            visibleItems = displayItems,
            repository = repository,
            onDismiss = { previewItem = null },
            onPlay = {
                previewItem = null
                onOpen(it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLivePreviewSheet(
    initial: CatalogItem,
    visibleItems: List<CatalogItem>,
    repository: ContentRepository,
    onDismiss: () -> Unit,
    onPlay: (CatalogItem) -> Unit
) {
    var selected by remember(initial.itemKey) { mutableStateOf(initial) }
    var epg by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }

    LaunchedEffect(selected.itemKey) {
        epg = withContext(Dispatchers.IO) { repository.epg(selected) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VpPanel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(channelBaseName(selected.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${qualityLabel(selected.name)} • ${selected.categoryName}", color = VpMuted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            LivePreviewPlayer(selected, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Spacer(Modifier.height(10.dp))
            epg.firstOrNull()?.let { now ->
                Text("Agora", color = VpCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(now.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (now.start.isNotBlank()) Text("${shortClockMobile(now.start)} - ${shortClockMobile(now.end)}", color = VpMuted, fontSize = 10.sp)
            }
            if (epg.size > 1) {
                Text("Depois: ${epg[1].title}", color = VpMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val i = visibleItems.indexOfFirst { it.itemKey == selected.itemKey }
                    if (visibleItems.isNotEmpty()) selected = visibleItems[(if (i <= 0) visibleItems.lastIndex else i - 1)]
                }, modifier = Modifier.weight(1f)) { Text("‹ Canal") }
                Button(onClick = { onPlay(selected) }, modifier = Modifier.weight(1.4f)) { Text("Assistir") }
                OutlinedButton(onClick = {
                    val i = visibleItems.indexOfFirst { it.itemKey == selected.itemKey }
                    if (visibleItems.isNotEmpty()) selected = visibleItems[(if (i < 0 || i >= visibleItems.lastIndex) 0 else i + 1)]
                }, modifier = Modifier.weight(1f)) { Text("Canal ›") }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun MobileFavorites(
    db: CatalogDb,
    catalogVersion: Int,
    adultUnlocked: Boolean,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(search, catalogVersion, adultUnlocked) {
        items = withContext(Dispatchers.IO) {
            db.favorites(search, 800).filter { adultUnlocked || !isAdultContent(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Favoritos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "Pesquisar", tint = VpCyan)
            }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Buscar nos favoritos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum favorito ainda.", color = VpMuted) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items, key = { it.itemKey }) { item ->
                    PosterCard(item, 140.dp, { onOpen(item) }, onFavorite = { db.toggleFavorite(item.itemKey); onChanged() })
                }
            }
        }
    }
}

private fun sectionTitle(type: ContentType): String = when (type) {
    ContentType.LIVE -> "TV ao vivo"
    ContentType.MOVIE -> "Filmes"
    ContentType.SERIES -> "Séries"
    ContentType.EPISODE -> "Episódios"
}

private fun sectionName(type: ContentType): String = when (type) {
    ContentType.LIVE -> "canais"
    ContentType.MOVIE -> "filmes"
    ContentType.SERIES -> "séries"
    ContentType.EPISODE -> "episódios"
}

private fun shortClockMobile(value: String): String {
    val v = value.trim()
    val time = if (v.contains(' ')) v.substringAfterLast(' ') else v
    return if (time.length >= 5) time.take(5) else time
}
