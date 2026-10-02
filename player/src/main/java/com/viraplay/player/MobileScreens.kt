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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MobileShell(
    db: CatalogDb,
    section: MainSection,
    catalogVersion: Int,
    status: String,
    onSection: (MainSection) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onChanged: () -> Unit
) {
    if (section != MainSection.HOME) {
        BackHandler { onSection(MainSection.HOME) }
    }

    Scaffold(
        containerColor = VpBg,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(VpBg, VpPanel.copy(alpha = 0.92f), VpBg)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandWordmark()
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(status, color = VpMuted, fontSize = 9.sp, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = VpCyan)
                            }
                            IconButton(onClick = onSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = VpPanel,
                tonalElevation = 10.dp
            ) {
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
                        icon = {
                            Icon(
                                imageVector = navIcon(item),
                                contentDescription = label
                            )
                        },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (section) {
                MainSection.HOME -> MobileHome(db, catalogVersion, onOpen, onSection, onSupport, onChanged)
                MainSection.LIVE -> MobileLibrary(db, ContentType.LIVE, catalogVersion, onOpen, onChanged)
                MainSection.MOVIES -> MobileLibrary(db, ContentType.MOVIE, catalogVersion, onOpen, onChanged)
                MainSection.SERIES -> MobileLibrary(db, ContentType.SERIES, catalogVersion, onOpen, onChanged)
                MainSection.FAVORITES -> MobileFavorites(db, catalogVersion, onOpen, onChanged)
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
    onOpen: (CatalogItem) -> Unit,
    onSection: (MainSection) -> Unit,
    onSupport: () -> Unit,
    onChanged: () -> Unit
) {
    var continueItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(catalogVersion) {
        withContext(Dispatchers.IO) {
            continueItems = db.continueWatching(20)
            movies = db.query(ContentType.MOVIE, limit = 24)
            series = db.query(ContentType.SERIES, limit = 24)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("O que você quer assistir?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuick("TV ao vivo", Icons.Filled.LiveTv, Modifier.weight(1f)) { onSection(MainSection.LIVE) }
                HomeQuick("Filmes", Icons.Filled.Movie, Modifier.weight(1f)) { onSection(MainSection.MOVIES) }
                HomeQuick("Séries", Icons.Filled.VideoLibrary, Modifier.weight(1f)) { onSection(MainSection.SERIES) }
            }
        }

        if (continueItems.isNotEmpty()) {
            item {
                ContentStrip(
                    title = "Continuar assistindo",
                    items = continueItems,
                    onOpen = onOpen,
                    onFavorite = {
                        db.toggleFavorite(it.itemKey)
                        onChanged()
                    }
                )
            }
        }

        if (movies.isNotEmpty()) {
            item {
                ContentStrip(
                    title = "Filmes",
                    items = movies,
                    onOpen = onOpen,
                    onFavorite = {
                        db.toggleFavorite(it.itemKey)
                        onChanged()
                    }
                )
            }
        }

        if (series.isNotEmpty()) {
            item {
                ContentStrip(
                    title = "Séries",
                    items = series,
                    onOpen = onOpen,
                    onFavorite = {
                        db.toggleFavorite(it.itemKey)
                        onChanged()
                    }
                )
            }
        }

        item {
            OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                Text("Suporte ViraPlay")
            }
        }
    }
}

@Composable
private fun HomeQuick(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    Brush.linearGradient(
                        listOf(VpPanelAlt, VpPanel, VpPurple.copy(alpha = 0.14f))
                    )
                )
        ) {
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = null, tint = VpCyan, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ContentStrip(
    title: String,
    items: List<CatalogItem>,
    onOpen: (CatalogItem) -> Unit,
    onFavorite: (CatalogItem) -> Unit
) {
    Column {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.itemKey }) { item ->
                PosterCard(
                    item = item,
                    width = 132.dp,
                    onClick = { onOpen(item) },
                    onFavorite = { onFavorite(item) }
                )
            }
        }
    }
}

@Composable
private fun MobileLibrary(
    db: CatalogDb,
    type: ContentType,
    catalogVersion: Int,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selectedCategory by remember(type) { mutableStateOf("ALL") }
    var search by remember(type) { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var limit by remember(type, selectedCategory, search) { mutableIntStateOf(300) }
    var totalCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(type, selectedCategory, search, limit, catalogVersion) {
        withContext(Dispatchers.IO) {
            categories = db.categories(type)
            totalCount = db.count(type)
            items = db.query(
                type = type,
                categoryId = selectedCategory,
                search = search,
                limit = limit
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Buscar ${sectionName(type)}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                items(items, key = { it.itemKey }) { item ->
                    LiveRow(
                        item = item,
                        selected = false,
                        onClick = { onOpen(item) },
                        onFavorite = {
                            db.toggleFavorite(item.itemKey)
                            onChanged()
                        }
                    )
                }
                if (items.size >= limit) {
                    item {
                        OutlinedButton(
                            onClick = { limit += 300 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Carregar mais") }
                    }
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
                items(items, key = { it.itemKey }) { item ->
                    PosterCard(
                        item = item,
                        width = 140.dp,
                        onClick = { onOpen(item) },
                        onFavorite = {
                            db.toggleFavorite(item.itemKey)
                            onChanged()
                        }
                    )
                }
                if (items.size >= limit) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        OutlinedButton(
                            onClick = { limit += 300 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Carregar mais") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileFavorites(
    db: CatalogDb,
    catalogVersion: Int,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(search, catalogVersion) {
        items = withContext(Dispatchers.IO) { db.favorites(search, 800) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Buscar nos favoritos") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        )

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum favorito ainda.", color = VpMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items, key = { it.itemKey }) { item ->
                    PosterCard(
                        item = item,
                        width = 140.dp,
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
}

private fun sectionName(type: ContentType): String = when (type) {
    ContentType.LIVE -> "canais"
    ContentType.MOVIE -> "filmes"
    ContentType.SERIES -> "séries"
    ContentType.EPISODE -> "episódios"
}
