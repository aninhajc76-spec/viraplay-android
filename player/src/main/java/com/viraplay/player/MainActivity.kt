package com.viraplay.player

import android.app.UiModeManager
import android.content.Context
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
import androidx.compose.ui.layout.ContentScale
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
import com.viraplay.shared.ChannelItem
import com.viraplay.shared.ContentType
import com.viraplay.shared.ParsedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Panel2 = Color(0xFF0A2040)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF8B3DFF)
private val Green = Color(0xFF4BE38A)
private val Danger = Color(0xFFFF5D73)

private enum class HomeSection(val label: String) {
    LIVE("Ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    FAVORITES("Favoritos")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ViraPlayApp() }
    }
}

private data class RefreshResult(
    val enabled: Boolean,
    val playlistUrl: String?,
    val playlist: ParsedPlaylist?,
    val status: String
)

@Composable
fun ViraPlayApp() {
    val context = LocalContext.current
    val uiMode =
        context.getSystemService(
            Context.UI_MODE_SERVICE
        ) as UiModeManager

    val isTv =
        uiMode.currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION

    val identity =
        remember {
            DeviceIdentity(context)
        }

    val repo =
        remember {
            PlayerRepository()
        }

    val scope =
        rememberCoroutineScope()

    var status by remember {
        mutableStateOf("Conectando...")
    }

    var enabled by remember {
        mutableStateOf(false)
    }

    var parsed by remember {
        mutableStateOf<ParsedPlaylist?>(null)
    }

    var playlistUrl by remember {
        mutableStateOf<String?>(null)
    }

    var selected by remember {
        mutableStateOf<ChannelItem?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    suspend fun refresh(
        forcePlaylist: Boolean = false
    ) {
        if (loading) return

        loading = true
        status = "Conectando..."

        try {
            val currentParsed = parsed
            val currentUrl = playlistUrl

            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    repo.register(
                        identity.deviceId,
                        identity.deviceSecret,
                        identity.pairingCode,
                        if (isTv) {
                            "ANDROID_TV"
                        } else {
                            "ANDROID_MOBILE"
                        }
                    )

                    val cfg =
                        repo.config(
                            identity.deviceId,
                            identity.deviceSecret
                        )

                    if (!cfg.enabled) {
                        RefreshResult(
                            enabled = false,
                            playlistUrl =
                                cfg.playlistUrl,
                            playlist = null,
                            status =
                                "Dispositivo aguardando liberação"
                        )
                    } else if (
                        cfg.playlistUrl
                            .isNullOrBlank()
                    ) {
                        RefreshResult(
                            enabled = true,
                            playlistUrl = null,
                            playlist = null,
                            status =
                                "Ativado. Aguardando lista."
                        )
                    } else {
                        val shouldReload =
                            forcePlaylist ||
                                currentParsed == null ||
                                cfg.playlistUrl !=
                                    currentUrl

                        if (shouldReload) {
                            val p =
                                repo.playlist(
                                    cfg.playlistUrl
                                )

                            RefreshResult(
                                enabled = true,
                                playlistUrl =
                                    cfg.playlistUrl,
                                playlist = p,
                                status =
                                    "${p.channels.size} itens carregados"
                            )
                        } else {
                            RefreshResult(
                                enabled = true,
                                playlistUrl =
                                    cfg.playlistUrl,
                                playlist =
                                    currentParsed,
                                status =
                                    "${currentParsed.channels.size} itens carregados"
                            )
                        }
                    }
                }

            enabled =
                result.enabled

            playlistUrl =
                result.playlistUrl

            parsed =
                result.playlist

            status =
                result.status

            if (!enabled) {
                selected = null
            }

        } catch (e: Throwable) {
            status =
                when (e) {
                    is OutOfMemoryError ->
                        "Lista muito grande para a memória deste aparelho"

                    else ->
                        "Falha: " +
                            (e.message
                                ?: "não foi possível carregar")
                                .replace("\n", " ")
                                .take(90)
                }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh(
            forcePlaylist = true
        )

        while (true) {
            delay(
                if (parsed == null) {
                    10_000
                } else {
                    30_000
                }
            )

            refresh(
                forcePlaylist = false
            )
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
            modifier =
                Modifier.fillMaxSize(),
            color = Bg
        ) {
            when {
                selected != null -> {
                    VideoScreen(
                        channel =
                            selected!!,
                        onBack = {
                            selected = null
                        }
                    )
                }

                parsed != null &&
                    enabled -> {
                    HomeScreen(
                        playlist =
                            parsed!!,
                        isTv =
                            isTv,
                        status =
                            status,
                        onReload = {
                            scope.launch {
                                refresh(
                                    forcePlaylist =
                                        true
                                )
                            }
                        },
                        onPlay = {
                            selected = it
                        }
                    )
                }

                else -> {
                    ActivationScreen(
                        code =
                            identity
                                .pairingCode,
                        status =
                            status,
                        loading =
                            loading,
                        onRefresh = {
                            scope.launch {
                                refresh(
                                    forcePlaylist =
                                        true
                                )
                            }
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
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(28.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {
        Image(
            painter =
                painterResource(
                    R.drawable.viraplay_logo
                ),
            contentDescription =
                "ViraPlay",
            modifier =
                Modifier.size(118.dp),
            contentScale =
                ContentScale.Fit
        )

        Spacer(
            Modifier.height(8.dp)
        )

        Text(
            "ViraPlay",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Text(
            "ENTRETENIMENTO SEM LIMITES",
            color = Cyan,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )

        Spacer(
            Modifier.height(24.dp)
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Panel
                ),
            shape =
                RoundedCornerShape(
                    22.dp
                ),
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.padding(22.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    "Ative seu dispositivo",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight =
                        FontWeight.Medium
                )

                Text(
                    "Informe este código ao atendimento ViraPlay",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                Surface(
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    color = Panel2
                ) {
                    Text(
                        code,
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    30.dp,
                                vertical =
                                    18.dp
                            ),
                        color =
                            Color.White,
                        fontSize =
                            34.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            3.sp
                    )
                }

                Spacer(
                    Modifier.height(16.dp)
                )

                if (loading) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        color = Cyan
                    )
                }

                Text(
                    status,
                    color =
                        if (
                            status.startsWith(
                                "Falha"
                            )
                        ) {
                            Danger
                        } else {
                            Color.LightGray
                        },
                    modifier =
                        Modifier.padding(
                            top = 12.dp
                        ),
                    fontSize =
                        13.sp
                )

                Button(
                    onClick =
                        onRefresh,
                    enabled =
                        !loading,
                    modifier =
                        Modifier
                            .padding(
                                top = 16.dp
                            )
                            .fillMaxWidth()
                ) {
                    Text(
                        if (loading) {
                            "Carregando..."
                        } else {
                            "Atualizar agora"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    playlist: ParsedPlaylist,
    isTv: Boolean,
    status: String,
    onReload: () -> Unit,
    onPlay: (ChannelItem) -> Unit
) {
    val context =
        LocalContext.current

    var favorites by remember {
        mutableStateOf(
            loadFavorites(
                context
            )
        )
    }

    var section by remember {
        mutableStateOf(
            HomeSection.LIVE
        )
    }

    var search by remember {
        mutableStateOf("")
    }

    val sectionItems =
        remember(
            section,
            playlist,
            favorites
        ) {
            when (section) {
                HomeSection.LIVE ->
                    playlist.channels
                        .filter {
                            it.type ==
                                ContentType.LIVE
                        }

                HomeSection.MOVIES ->
                    playlist.channels
                        .filter {
                            it.type ==
                                ContentType.MOVIE
                        }

                HomeSection.SERIES ->
                    playlist.channels
                        .filter {
                            it.type ==
                                ContentType.SERIES
                        }

                HomeSection.FAVORITES ->
                    playlist.channels
                        .filter {
                            favorites
                                .contains(
                                    it.url
                                )
                        }
            }
        }

    val searched =
        remember(
            sectionItems,
            search
        ) {
            if (search.isBlank()) {
                sectionItems
            } else {
                sectionItems.filter {
                    it.name.contains(
                        search,
                        true
                    ) ||
                        it.group.contains(
                            search,
                            true
                        )
                }
            }
        }

    val groups =
        remember(searched) {
            listOf("Todos") +
                searched
                    .map { it.group }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .sorted()
        }

    var group by remember(section) {
        mutableStateOf("Todos")
    }

    if (group !in groups) {
        group = "Todos"
    }

    val visible =
        remember(
            searched,
            group
        ) {
            if (group == "Todos") {
                searched
            } else {
                searched.filter {
                    it.group == group
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    if (isTv) {
                        24.dp
                    } else {
                        12.dp
                    }
                )
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Image(
                painter =
                    painterResource(
                        R.drawable
                            .viraplay_logo
                    ),
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        if (isTv) {
                            58.dp
                        } else {
                            44.dp
                        }
                    )
            )

            Spacer(
                Modifier.width(
                    10.dp
                )
            )

            Column {
                Row {
                    Text(
                        "Vira",
                        color =
                            Color.White,
                        fontSize =
                            if (isTv) {
                                32.sp
                            } else {
                                25.sp
                            },
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Play",
                        color = Cyan,
                        fontSize =
                            if (isTv) {
                                32.sp
                            } else {
                                25.sp
                            },
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Text(
                    status,
                    color =
                        Color.Gray,
                    fontSize =
                        11.sp
                )
            }

            Spacer(
                Modifier.weight(1f)
            )

            TextButton(
                onClick =
                    onReload
            ) {
                Text("Atualizar")
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            items(
                HomeSection.entries
            ) { item ->
                FilterChip(
                    selected =
                        section == item,
                    onClick = {
                        section = item
                        group = "Todos"
                        search = ""
                    },
                    label = {
                        Text(item.label)
                    }
                )
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
            },
            modifier =
                Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    "Buscar conteúdo"
                )
            }
        )

        Spacer(
            Modifier.height(8.dp)
        )

        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            items(groups) { g ->
                FilterChip(
                    selected =
                        group == g,
                    onClick = {
                        group = g
                    },
                    label = {
                        Text(
                            g,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Spacer(
            Modifier.height(10.dp)
        )

        if (visible.isEmpty()) {
            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    if (
                        section ==
                        HomeSection.FAVORITES
                    ) {
                        "Nenhum favorito ainda"
                    } else {
                        "Nenhum conteúdo encontrado"
                    },
                    color = Color.Gray
                )
            }
        } else {
            LazyVerticalGrid(
                columns =
                    GridCells.Fixed(
                        if (isTv) 5 else 2
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
                modifier =
                    Modifier.fillMaxSize()
            ) {
                items(
                    visible,
                    key = {
                        it.name +
                            it.url
                    }
                ) { item ->
                    ChannelCard(
                        channel =
                            item,
                        isTv =
                            isTv,
                        favorite =
                            favorites
                                .contains(
                                    item.url
                                ),
                        onFavorite = {
                            favorites =
                                toggleFavorite(
                                    context,
                                    favorites,
                                    item.url
                                )
                        },
                        onClick = {
                            onPlay(item)
                        }
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
    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        Panel
                ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        modifier =
            Modifier
                .height(
                    if (isTv) {
                        132.dp
                    } else {
                        124.dp
                    }
                )
                .fillMaxWidth()
                .focusable()
                .clickable(
                    onClick =
                        onClick
                )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement =
                Arrangement
                    .SpaceBetween
        ) {
            Row {
                Text(
                    channel.name,
                    color =
                        Color.White,
                    fontSize =
                        if (isTv) {
                            17.sp
                        } else {
                            15.sp
                        },
                    fontWeight =
                        FontWeight.Medium,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f)
                )

                Text(
                    if (favorite) {
                        "★"
                    } else {
                        "☆"
                    },
                    color =
                        if (favorite) {
                            Cyan
                        } else {
                            Color.Gray
                        },
                    fontSize = 23.sp,
                    modifier =
                        Modifier
                            .padding(
                                start = 6.dp
                            )
                            .clickable {
                                onFavorite()
                            }
                )
            }

            Column {
                Text(
                    when (
                        channel.type
                    ) {
                        ContentType.LIVE ->
                            "AO VIVO"

                        ContentType.MOVIE ->
                            "FILME"

                        ContentType.SERIES ->
                            "SÉRIE"
                    },
                    color =
                        when (
                            channel.type
                        ) {
                            ContentType.LIVE ->
                                Green

                            ContentType.MOVIE ->
                                Cyan

                            ContentType.SERIES ->
                                Purple
                        },
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    channel.group,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
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
    BackHandler {
        onBack()
    }

    val context =
        LocalContext.current

    var error by
        remember(channel.url) {
            mutableStateOf<String?>(
                null
            )
        }

    val player =
        remember(channel.url) {
            ExoPlayer
                .Builder(context)
                .build()
                .apply {
                    addListener(
                        object :
                            Player.Listener {
                            override fun onPlayerError(
                                playbackException:
                                    PlaybackException
                            ) {
                                error =
                                    playbackException
                                        .message
                                        ?.take(100)
                                        ?: "Falha ao reproduzir"
                            }
                        }
                    )

                    setMediaItem(
                        MediaItem
                            .fromUri(
                                channel.url
                            )
                    )

                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx)
                    .apply {
                        this.player =
                            player
                        useController =
                            true
                    }
            },
            modifier =
                Modifier.fillMaxSize()
        )

        Row(
            modifier =
                Modifier
                    .align(
                        Alignment.TopStart
                    )
                    .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Button(
                onClick =
                    onBack
            ) {
                Text("Voltar")
            }

            Spacer(
                Modifier.width(10.dp)
            )

            Text(
                channel.name,
                color = Color.White,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )
        }

        error?.let {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                Color.Black
                                    .copy(
                                        alpha =
                                            0.82f
                                    )
                        ),
                modifier =
                    Modifier
                        .align(
                            Alignment.Center
                        )
                        .padding(24.dp)
            ) {
                Column(
                    modifier =
                        Modifier.padding(18.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        "Não foi possível reproduzir",
                        color = Danger,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        it,
                        color =
                            Color.LightGray,
                        fontSize =
                            12.sp,
                        modifier =
                            Modifier.padding(
                                top = 8.dp
                            )
                    )
                }
            }
        }
    }
}

private fun loadFavorites(
    context: Context
): Set<String> =
    context
        .getSharedPreferences(
            "viraplay_favorites",
            Context.MODE_PRIVATE
        )
        .getStringSet(
            "urls",
            emptySet()
        )
        ?.toSet()
        ?: emptySet()

private fun toggleFavorite(
    context: Context,
    current: Set<String>,
    url: String
): Set<String> {
    val updated =
        current
            .toMutableSet()
            .apply {
                if (!add(url)) {
                    remove(url)
                }
            }
            .toSet()

    context
        .getSharedPreferences(
            "viraplay_favorites",
            Context.MODE_PRIVATE
        )
        .edit()
        .putStringSet(
            "urls",
            updated
        )
        .apply()

    return updated
}
