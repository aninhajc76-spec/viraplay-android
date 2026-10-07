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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.ContentType
import coil.compose.AsyncImage
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
            Surface(
                color = Color(0xF2020711),
                tonalElevation = 8.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF020714),
                                    Color(0xFF071B35),
                                    Color(0xFF100A2E)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandWordmark()
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = VpPanelAlt.copy(alpha = .72f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VpBorder.copy(alpha = .7f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRefresh, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Filled.Refresh, "Atualizar", tint = VpCyan)
                            }
                            IconButton(onClick = onSettings, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF06172C), tonalElevation = 12.dp) {
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
                            indicatorColor = VpPurple.copy(alpha = 0.42f),
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
                .background(Brush.verticalGradient(listOf(VpBg, Color(0xFF061A34), Color(0xFF050A20), VpBg)))
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
            fun clean(list: List<CatalogItem>) =
                if (adultUnlocked) list else list.filterNot(::isAdultContent)

            continueItems = clean(db.continueWatching(20))
            movies = clean(db.query(ContentType.MOVIE, limit = 24))
            series = clean(db.query(ContentType.SERIES, limit = 24))
        }
    }

    val featured = remember(continueItems, movies) {
        continueItems.firstOrNull() ?: movies.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(336.dp)
                    .background(Color(0xFF06111F))
            ) {
                if (!featured?.image.isNullOrBlank()) {
                    AsyncImage(
                        model = featured?.backdrop ?: featured?.image,
                        contentDescription = featured?.name,
                        contentScale = ContentScale.Crop,
                        alpha = .72f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .06f),
                                Color(0xFF020714).copy(alpha = .42f),
                                VpBg
                            )
                        )
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF020714).copy(alpha = .96f),
                                Color(0xFF071B35).copy(alpha = .40f),
                                Color.Transparent
                            )
                        )
                    )
                )

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                        .fillMaxWidth(.90f)
                ) {
                    Surface(
                        color = VpCyan.copy(alpha = .14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VpCyan.copy(alpha = .38f)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            if (featured != null) "DESTAQUE VPLAYO" else "VPLAYO PREMIUM",
                            color = VpCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        featured?.name ?: "Filmes, séries e TV ao vivo",
                        color = Color.White,
                        fontSize = 29.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (featured != null) "Continue de onde parou ou descubra algo novo."
                        else "Sua programação em uma experiência mais rápida e elegante.",
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (featured != null) onOpen(featured)
                                else onSection(MainSection.MOVIES)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VpPurple)
                        ) {
                            Text(
                                if (featured != null) "▶ Assistir" else "Explorar",
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                        OutlinedButton(
                            onClick = { onSection(MainSection.LIVE) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, VpCyan.copy(alpha = .62f))
                        ) {
                            Text("TV ao vivo", color = Color.White)
                        }
                    }
                }
            }
        }

        accessNotice?.let { notice ->
            item {
                Surface(
                    color = VpPurple.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VpPurple.copy(alpha = .28f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Aviso da sua assinatura", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(notice, color = VpMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = onSupport) { Text("Suporte") }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Navegar", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuick("TV", "Ao vivo", Icons.Filled.LiveTv, Modifier.weight(1f), VpCyan) {
                        onSection(MainSection.LIVE)
                    }
                    HomeQuick("Filmes", "Cinema", Icons.Filled.Movie, Modifier.weight(1f), VpPurple) {
                        onSection(MainSection.MOVIES)
                    }
                    HomeQuick("Séries", "Episódios", Icons.Filled.VideoLibrary, Modifier.weight(1f), VpGreen) {
                        onSection(MainSection.SERIES)
                    }
                }
            }
        }

        if (continueItems.isNotEmpty()) item {
            PremiumContentStrip("Continuar assistindo", "Retome rapidamente", continueItems, onOpen) {
                db.toggleFavorite(it.itemKey)
                onChanged()
            }
        }
        if (movies.isNotEmpty()) item {
            PremiumContentStrip("Filmes para você", "Explore o catálogo", movies, onOpen) {
                db.toggleFavorite(it.itemKey)
                onChanged()
            }
        }
        if (series.isNotEmpty()) item {
            PremiumContentStrip("Séries em destaque", "Maratone seus episódios", series, onOpen) {
                db.toggleFavorite(it.itemKey)
                onChanged()
            }
        }

        item {
            OutlinedButton(
                onClick = onSupport,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VpBorder)
            ) {
                Text("Suporte VPlayo")
            }
        }
    }
}

@Composable
private fun HomeQuick(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .26f)),
        modifier = modifier
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = .30f),
                            Color(0xFF0B2340),
                            Color(0xFF07162A)
                        )
                    )
                )
        ) {
            Column(
                Modifier.fillMaxSize().padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    color = accent.copy(alpha = .18f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        icon,
                        null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(subtitle, color = VpMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PremiumContentStrip(
    title: String,
    subtitle: String,
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    onFavorite: (CatalogItem) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = VpMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.itemKey }) { item ->
                PosterCard(
                    item,
                    138.dp,
                    { onOpen(item) },
                    onFavorite = { onFavorite(item) }
                )
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
    val uiStore = remember { UiStateStore(context) }
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selectedCategory by remember(type) {
        mutableStateOf(uiStore.category("mobile_${type.name}"))
    }
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
            Text(sectionTitle(type), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
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
                    onClick = {
                        selectedCategory = "ALL"
                        uiStore.setCategory("mobile_${type.name}", "ALL")
                    },
                    label = { Text("Todos ($totalCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VpPurple.copy(alpha = .62f),
                        selectedLabelColor = Color.White,
                        containerColor = VpPanelAlt.copy(alpha = .78f),
                        labelColor = VpMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == "ALL",
                        borderColor = VpBorder,
                        selectedBorderColor = VpCyan.copy(alpha = .72f)
                    )
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    selected = selectedCategory == category.id,
                    onClick = {
                        selectedCategory = category.id
                        uiStore.setCategory("mobile_${type.name}", category.id)
                    },
                    label = { Text("${category.name} (${category.count})", maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VpPurple.copy(alpha = .62f),
                        selectedLabelColor = Color.White,
                        containerColor = VpPanelAlt.copy(alpha = .78f),
                        labelColor = VpMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == category.id,
                        borderColor = VpBorder,
                        selectedBorderColor = VpCyan.copy(alpha = .72f)
                    )
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
                        width = 148.dp,
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
