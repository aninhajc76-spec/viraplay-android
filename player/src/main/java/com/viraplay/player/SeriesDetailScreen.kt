package com.viraplay.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SeriesDetailScreen(
    parent: CatalogItem,
    db: CatalogDb,
    repository: ContentRepository,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlay: (CatalogItem) -> Unit,
    onChanged: () -> Unit
) {
    BackHandler(onBack = onBack)

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(parent.itemKey, refreshToken) {
        loading = true
        error = null
        try {
            episodes = withContext(Dispatchers.IO) {
                repository.episodes(parent, force = refreshToken > 0)
            }
            if (selectedSeason == null) {
                selectedSeason = episodes.mapNotNull { it.season }.minOrNull() ?: 1
            }
        } catch (e: Throwable) {
            error = (e.message ?: "Não foi possível carregar os episódios").take(120)
            episodes = withContext(Dispatchers.IO) { db.episodes(parent) }
        } finally {
            loading = false
        }
    }

    val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
    val visibleEpisodes = if (seasons.isEmpty()) episodes else episodes.filter { (it.season ?: 1) == selectedSeason }
    val continueEpisode = episodes
        .filter { it.progressMs > 30_000L && !it.watched }
        .maxByOrNull { it.progressMs }

    Surface(color = VpBg, modifier = Modifier.fillMaxSize()) {
        if (isTv) {
            Row(Modifier.fillMaxSize().padding(30.dp)) {
                SeriesInfoPane(
                    parent = parent,
                    continueEpisode = continueEpisode,
                    onBack = onBack,
                    onContinue = { continueEpisode?.let(onPlay) },
                    modifier = Modifier.width(330.dp).fillMaxHeight()
                )
                Spacer(Modifier.width(22.dp))
                EpisodesPane(
                    loading = loading,
                    error = error,
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    onSeason = { selectedSeason = it },
                    episodes = visibleEpisodes,
                    onPlay = onPlay,
                    onRetry = { refreshToken += 1 },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onBack) { Text("Voltar") }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        parent.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    AsyncImage(
                        model = parent.image,
                        contentDescription = parent.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(112.dp).aspectRatio(0.68f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        parent.rating?.let { Text("Nota $it", color = VpCyan, fontSize = 12.sp) }
                        Text(
                            parent.plot.orEmpty().ifBlank { "Série disponível na sua lista." },
                            color = VpMuted,
                            fontSize = 12.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                        continueEpisode?.let { episode ->
                            Button(
                                onClick = { onPlay(episode) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                Text("Continuar T${episode.season ?: 1} E${episode.episode ?: 1}")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                EpisodesPane(
                    loading = loading,
                    error = error,
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    onSeason = { selectedSeason = it },
                    episodes = visibleEpisodes,
                    onPlay = onPlay,
                    onRetry = { refreshToken += 1 },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SeriesInfoPane(
    parent: CatalogItem,
    continueEpisode: CatalogItem?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VpPanel),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(18.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            Spacer(Modifier.height(14.dp))
            AsyncImage(
                model = parent.image,
                contentDescription = parent.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(parent.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            parent.rating?.let { Text("Nota $it", color = VpCyan, fontSize = 12.sp) }
            Spacer(Modifier.height(8.dp))
            Text(
                parent.plot.orEmpty().ifBlank { "Série disponível na sua lista." },
                color = VpMuted,
                fontSize = 12.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            continueEpisode?.let { episode ->
                Spacer(Modifier.height(14.dp))
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuar T${episode.season ?: 1} E${episode.episode ?: 1}")
                }
            }
        }
    }
}

@Composable
private fun EpisodesPane(
    loading: Boolean,
    error: String?,
    seasons: List<Int>,
    selectedSeason: Int?,
    onSeason: (Int) -> Unit,
    episodes: List<CatalogItem>,
    onPlay: (CatalogItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        if (seasons.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { season ->
                    if (season == selectedSeason) {
                        Button(onClick = { onSeason(season) }) { Text("Temporada $season") }
                    } else {
                        OutlinedButton(onClick = { onSeason(season) }) { Text("Temporada $season") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VpCyan)
                    Spacer(Modifier.height(10.dp))
                    Text("Carregando episódios...", color = VpMuted)
                }
            }

            error != null && episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = VpDanger)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRetry) { Text("Tentar novamente") }
                }
            }

            episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum episódio encontrado.", color = VpMuted)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(episodes, key = { it.itemKey }) { episode ->
                    EpisodeRow(episode) { onPlay(episode) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(item: CatalogItem, onClick: () -> Unit) {
    FocusTile(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = VpPanelAlt, shape = RoundedCornerShape(10.dp)) {
                Text(
                    "T${item.season ?: 1} E${item.episode ?: 1}",
                    color = VpCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    item.watched -> Text("Assistido", color = VpGreen, fontSize = 10.sp)
                    item.progressMs > 30_000L -> Text("Continuar em ${formatProgress(item.progressMs)}", color = VpCyan, fontSize = 10.sp)
                    else -> Unit
                }
            }
            Text("Assistir", color = VpMuted, fontSize = 11.sp)
        }
    }
}
