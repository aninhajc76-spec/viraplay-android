package com.viraplay.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viraplay.shared.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun TvShell(
    db: CatalogDb,
    repository: ContentRepository,
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
    var showContinue by remember { mutableStateOf(false) }

    if (section == MainSection.HOME && showContinue) {
        BackHandler { showContinue = false }
        TvContinue(
            db = db,
            catalogVersion = catalogVersion,
            onBack = { showContinue = false },
            onOpen = onOpen
        )
        return
    }

    if (section == MainSection.HOME) {
        TvHome(
            db = db,
            catalogVersion = catalogVersion,
            status = status,
            onSection = onSection,
            onContinue = { showContinue = true },
            onSupport = onSupport,
            onSettings = onSettings
        )
        return
    }

    BackHandler { onSection(MainSection.HOME) }

    Column(Modifier.fillMaxSize()) {
        TvTopNav(
            section = section,
            onSection = onSection,
            onHome = { onSection(MainSection.HOME) },
            onSupport = onSupport,
            onSettings = onSettings,
            onRefresh = onRefresh
        )

        when (section) {
            MainSection.LIVE -> TvLive(
                db = db,
                repository = repository,
                catalogVersion = catalogVersion,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.MOVIES -> TvVod(
                db = db,
                type = ContentType.MOVIE,
                catalogVersion = catalogVersion,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.SERIES -> TvVod(
                db = db,
                type = ContentType.SERIES,
                catalogVersion = catalogVersion,
                onOpen = onOpen,
                onChanged = onChanged
            )
            MainSection.FAVORITES -> TvFavorites(db, catalogVersion, onOpen, onChanged)
            MainSection.HOME -> Unit
        }
    }
}

@Composable
private fun TvHome(
    db: CatalogDb,
    catalogVersion: Int,
    status: String,
    onSection: (MainSection) -> Unit,
    onContinue: () -> Unit,
    onSupport: () -> Unit,
    onSettings: () -> Unit
) {
    var continueCount by remember { mutableIntStateOf(0) }
    var liveCount by remember { mutableIntStateOf(0) }
    var movieCount by remember { mutableIntStateOf(0) }
    var seriesCount by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    LaunchedEffect(catalogVersion) {
        val counts = withContext(Dispatchers.IO) {
            intArrayOf(
                db.continueWatching(100).size,
                db.count(ContentType.LIVE),
                db.count(ContentType.MOVIE),
                db.count(ContentType.SERIES)
            )
        }
        continueCount = counts[0]
        liveCount = counts[1]
        movieCount = counts[2]
        seriesCount = counts[3]
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandWordmark(large = true)
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FocusTile(
                onClick = { onSection(MainSection.LIVE) },
                modifier = Modifier.weight(1.25f).fillMaxHeight().focusRequester(firstFocus)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TV", color = VpCyan, fontSize = 58.sp, fontWeight = FontWeight.Black)
                        Text("Ao Vivo", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("$liveCount canais", color = VpMuted, fontSize = 12.sp)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeTile("Filmes", "$movieCount títulos", Modifier.weight(1f)) {
                        onSection(MainSection.MOVIES)
                    }
                    HomeTile("Séries", "$seriesCount títulos", Modifier.weight(1f)) {
                        onSection(MainSection.SERIES)
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeTile("Favoritos", "Sua seleção", Modifier.weight(1f)) {
                        onSection(MainSection.FAVORITES)
                    }
                    HomeTile("Continuar", "$continueCount em andamento", Modifier.weight(1f)) {
                        onContinue()
                    }
                }
            }

            Column(
                modifier = Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeTile("Suporte", "WhatsApp ViraPlay", Modifier.weight(1f), onSupport)
                HomeTile("Configurações", "Atualização e aparelho", Modifier.weight(1f), onSettings)
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            color = VpPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("ViraPlay 3.1 Beta", color = VpCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(20.dp))
                Text(status, color = VpMuted)
                Spacer(Modifier.weight(1f))
                Text("Experiência ViraPlay • versão de testes", color = VpMuted, fontSize = 11.sp)
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
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.CenterStart) {
            Column {
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
    catalogVersion: Int,
    onBack: () -> Unit,
    onOpen: (CatalogItem) -> Unit
) {
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    LaunchedEffect(catalogVersion) {
        items = withContext(Dispatchers.IO) { db.continueWatching(100) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Voltar") }
            Spacer(Modifier.width(14.dp))
            Text("Continuar assistindo", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada em andamento.", color = VpMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.itemKey }) { item ->
                    PosterCard(item = item, width = 170.dp, onClick = { onOpen(item) })
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(onClick = onHome) { Text("Voltar") }
        listOf(MainSection.LIVE, MainSection.MOVIES, MainSection.SERIES, MainSection.FAVORITES).forEach { item ->
            if (section == item) {
                Button(onClick = { onSection(item) }) { Text(item.label) }
            } else {
                OutlinedButton(onClick = { onSection(item) }) { Text(item.label) }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) { Text("Atualizar") }
        TextButton(onClick = onSupport) { Text("Suporte") }
        TextButton(onClick = onSettings) { Text("Ajustes") }
    }
}

@Composable
private fun TvLive(
    db: CatalogDb,
    repository: ContentRepository,
    catalogVersion: Int,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var category by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var epg by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var liveCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(category, search, catalogVersion) {
        withContext(Dispatchers.IO) {
            categories = db.categories(ContentType.LIVE)
            liveCount = db.count(ContentType.LIVE)
            channels = db.query(ContentType.LIVE, category, search, limit = 650)
        }
        if (selected == null || channels.none { it.itemKey == selected?.itemKey }) {
            selected = channels.firstOrNull()
        }
    }

    LaunchedEffect(selected?.itemKey) {
        epg = emptyList()
        val item = selected ?: return@LaunchedEffect
        delay(450)
        epg = withContext(Dispatchers.IO) { repository.epg(item) }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Column(Modifier.width(285.dp).fillMaxHeight()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Pesquisar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CategoryButton("Todos", liveCount, category == "ALL") { category = "ALL" }
                }
                items(categories, key = { it.id }) { c ->
                    CategoryButton(c.name, c.count, category == c.id) { category = c.id }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(channels, key = { it.itemKey }) { item ->
                LiveRow(
                    item = item,
                    selected = selected?.itemKey == item.itemKey,
                    onClick = { onOpen(item) },
                    onFavorite = {
                        db.toggleFavorite(item.itemKey)
                        onChanged()
                    },
                    onFocused = { selected = item }
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        TvLivePreview(
            item = selected,
            epg = epg,
            modifier = Modifier.weight(0.9f).fillMaxHeight(),
            onPlay = { selected?.let(onOpen) }
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
    item: CatalogItem?,
    epg: List<EpgProgram>,
    modifier: Modifier,
    onPlay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VpPanel),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Selecione um canal", color = VpMuted)
                }
                return@Column
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    placeholder = painterResource(R.drawable.viraplay_logo),
                    error = painterResource(R.drawable.viraplay_logo),
                    fallback = painterResource(R.drawable.viraplay_logo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                )
            }
            Text(item.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.categoryName, color = VpMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Assistir agora") }
            Spacer(Modifier.height(14.dp))
            Text("Programação", color = VpCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            if (epg.isEmpty()) {
                Text("Programação não disponível para este canal.", color = VpMuted, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(epg) { program ->
                        Surface(color = VpPanelAlt, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(program.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                if (program.start.isNotBlank()) {
                                    Text("${shortClock(program.start)} - ${shortClock(program.end)}", color = VpCyan, fontSize = 9.sp)
                                }
                                if (program.description.isNotBlank()) {
                                    Text(program.description, color = VpMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    catalogVersion: Int,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var categories by remember { mutableStateOf<List<CategoryEntry>>(emptyList()) }
    var selection by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var limit by remember(selection, search) { mutableIntStateOf(420) }
    var totalCount by remember { mutableIntStateOf(0) }
    var favoriteCount by remember { mutableIntStateOf(0) }
    var continueCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(type, selection, search, limit, catalogVersion) {
        withContext(Dispatchers.IO) {
            categories = db.categories(type)
            totalCount = db.count(type)
            favoriteCount = db.favorites(limit = 9999).count { it.type == type }
            continueCount = db.continueWatching(100).count {
                if (type == ContentType.MOVIE) it.type == ContentType.MOVIE else it.type == ContentType.EPISODE
            }
            items = when (selection) {
                "FAVORITES" -> db.favorites(search, 800).filter { it.type == type }
                "CONTINUE" -> db.continueWatching(100).filter {
                    if (type == ContentType.MOVIE) it.type == ContentType.MOVIE else it.type == ContentType.EPISODE
                }
                else -> db.query(type, selection, search, limit)
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Column(Modifier.width(285.dp).fillMaxHeight()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Pesquisar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { CategoryButton("Favoritos", favoriteCount, selection == "FAVORITES") { selection = "FAVORITES" } }
                item { CategoryButton("Continuar assistindo", continueCount, selection == "CONTINUE") { selection = "CONTINUE" } }
                item { CategoryButton("Todos", totalCount, selection == "ALL") { selection = "ALL" } }
                items(categories, key = { it.id }) { c ->
                    CategoryButton(c.name, c.count, selection == c.id) { selection = c.id }
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum conteúdo encontrado", color = VpMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.itemKey }) { item ->
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
                if (selection != "FAVORITES" && selection != "CONTINUE" && items.size >= limit) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(5) }) {
                        OutlinedButton(onClick = { limit += 420 }, modifier = Modifier.fillMaxWidth()) {
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
    catalogVersion: Int,
    onOpen: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }

    LaunchedEffect(search, catalogVersion) {
        items = withContext(Dispatchers.IO) { db.favorites(search, 1000) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Pesquisar favoritos") },
            singleLine = true,
            modifier = Modifier.width(420.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.itemKey }) { item ->
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
