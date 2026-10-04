package com.viraplay.admin

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.AdminDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF081831)
private val PanelAlt = Color(0xFF0D2345)
private val Cyan = Color(0xFF11C8F5)
private val Purple = Color(0xFF8F3CFF)
private val Green = Color(0xFF46DB8B)
private val Danger = Color(0xFFFF5E78)
private val Muted = Color(0xFF98A3B8)

private enum class Tab(val label: String) {
    DASHBOARD("Painel"), PENDING("Pendentes"), CLIENTS("Clientes"), UPDATES("Atualizações")
}

private enum class ClientFilter(val label: String) {
    ALL("Todos"), EXPIRING("Vencendo"), EXPIRED("Vencidos")
}

@Composable
fun AdminApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("viraplay_admin", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("admin_token", "").orEmpty()) }
    var logged by remember { mutableStateOf(token.isNotBlank()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Purple,
            background = Bg,
            surface = Panel,
            error = Danger
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            if (logged) {
                Dashboard(
                    token = token,
                    onLogout = {
                        prefs.edit().remove("admin_token").apply()
                        token = ""
                        logged = false
                    }
                )
            } else {
                Login(
                    token = token,
                    onToken = { token = it },
                    onLogin = {
                        if (token.isNotBlank()) {
                            prefs.edit().putString("admin_token", token.trim()).apply()
                            token = token.trim()
                            logged = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.viraplay_wordmark),
        contentDescription = "ViraPlay ADM",
        contentScale = ContentScale.Fit,
        modifier = modifier.width(190.dp).height(60.dp)
    )
}

@Composable
private fun Login(token: String, onToken: (String) -> Unit, onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Brand(Modifier.width(280.dp).height(100.dp))
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("ViraPlay ADM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Text("Gerencie seus clientes e aparelhos.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = token,
                    onValueChange = onToken,
                    label = { Text("Chave do administrador") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                )
                Button(
                    onClick = onLogin,
                    enabled = token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Entrar") }
            }
        }
    }
}

@Composable
private fun Dashboard(token: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AdminRepository() }
    val updateManager = remember { AdminUpdateManager(context) }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<AdminDevice>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Conectando...") }
    var activating by remember { mutableStateOf<AdminDevice?>(null) }
    var editing by remember { mutableStateOf<AdminDevice?>(null) }
    var firstLoad by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<AdminUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var lastUpdateCheck by remember { mutableStateOf("Ainda não verificado") }

    suspend fun load() {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val base = repo.list(token)
                repo.enrichMissingExpiries(token, base)
            }
            devices = loaded
            status = "${loaded.size} aparelho(s)"
        } catch (e: Throwable) {
            status = "Falha: ${(e.message ?: "sem conexão").take(80)}"
        } finally {
            firstLoad = false
        }
    }

    fun reload() { scope.launch { load() } }

    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(20_000)
            load()
        }
    }

    suspend fun checkForUpdate(showNoUpdate: Boolean = false) {
        checkingUpdate = true
        updateError = null
        try {
            val found = withContext(Dispatchers.IO) { updateManager.check() }
            updateInfo = found
            if (found != null) showUpdateDialog = true
            lastUpdateCheck = if (found != null) {
                "Nova versão ${found.versionName} disponível"
            } else {
                "ViraPlay ADM ${BuildConfig.VERSION_NAME} está atualizado"
            }
            if (showNoUpdate && found == null) {
                updateError = "Nenhuma atualização disponível no momento."
            }
        } catch (e: Throwable) {
            updateError = (e.message ?: "Não foi possível verificar atualizações.").take(120)
            lastUpdateCheck = "Falha ao verificar"
        } finally {
            checkingUpdate = false
        }
    }

    LaunchedEffect(Unit) {
        delay(2_000)
        checkForUpdate(false)
        while (true) {
            delay(30L * 60L * 1000L)
            checkForUpdate(false)
        }
    }

    val pending = devices.filter { it.playlistUrl.isNullOrBlank() }
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() }
    val active = clients.count { it.enabled }
    val blocked = clients.size - active
    val visibleClients = clients.filter { device ->
        val meta = ClientMetaCodec.decode(device.label)
        search.isBlank() ||
            meta.name.contains(search, true) ||
            meta.identifier.orEmpty().contains(search, true) ||
            device.pairingCode.contains(search, true) ||
            playlistUsername(device.playlistUrl).orEmpty().contains(search, true)
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Brand()
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(status, color = Muted, fontSize = 10.sp, maxLines = 1)
                    Row {
                        TextButton(onClick = { reload() }) { Text("Atualizar") }
                        TextButton(onClick = onLogout) { Text("Sair") }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Text(
                                when (item) {
                                    Tab.DASHBOARD -> "ADM"
                                    Tab.PENDING -> pending.size.toString()
                                    Tab.CLIENTS -> clients.size.toString()
                                    Tab.UPDATES -> if (updateInfo != null) "NOVO" else "UPD"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardTab(
                    clients = clients.size,
                    active = active,
                    blocked = blocked,
                    pending = pending,
                    onActivate = { activating = it },
                    onPending = { tab = Tab.PENDING },
                    onClients = { tab = Tab.CLIENTS }
                )
                Tab.PENDING -> PendingTab(pending) { activating = it }
                Tab.CLIENTS -> ClientsTab(
                    items = visibleClients,
                    search = search,
                    onSearch = { search = it },
                    onOpen = { editing = it }
                )
                Tab.UPDATES -> UpdatesTab(
                    info = updateInfo,
                    checking = checkingUpdate,
                    busy = updateBusy,
                    lastCheck = lastUpdateCheck,
                    error = updateError,
                    onCheck = { scope.launch { checkForUpdate(true) } },
                    onInstall = { info ->
                        updateBusy = true
                        updateError = null
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching { updateManager.download(info) }
                            }
                            file.onSuccess { apk ->
                                updateBusy = false
                                when (updateManager.launchInstaller(apk)) {
                                    AdminInstallLaunchResult.STARTED -> Unit
                                    AdminInstallLaunchResult.NEED_PERMISSION ->
                                        updateError = "Autorize a instalação e toque em Instalar novamente."
                                }
                            }.onFailure { e ->
                                updateBusy = false
                                updateError = e.message ?: "Não foi possível baixar a atualização."
                            }
                        }
                    }
                )
            }

            if (firstLoad) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showUpdateDialog) updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!info.mandatory && !updateBusy) showUpdateDialog = false },
            title = { Text("Atualização do ViraPlay ADM") },
            text = {
                Column {
                    Text("Versão ${info.versionName} disponível.", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(info.message, color = Muted)
                    if (info.mandatory) {
                        Spacer(Modifier.height(8.dp))
                        Text("Atualização obrigatória.", color = Cyan, fontWeight = FontWeight.Bold)
                    }
                    if (updateBusy) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Baixando atualização...", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    updateError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Danger, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !updateBusy,
                    onClick = {
                        updateBusy = true
                        updateError = null
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { runCatching { updateManager.download(info) } }
                            file.onSuccess { apk ->
                                updateBusy = false
                                when (updateManager.launchInstaller(apk)) {
                                    AdminInstallLaunchResult.STARTED -> Unit
                                    AdminInstallLaunchResult.NEED_PERMISSION ->
                                        updateError = "Autorize a instalação e toque em Atualizar novamente."
                                }
                            }.onFailure { e ->
                                updateBusy = false
                                updateError = e.message ?: "Não foi possível baixar a atualização."
                            }
                        }
                    }
                ) { Text("Atualizar agora") }
            },
            dismissButton = {
                if (!info.mandatory) {
                    TextButton(enabled = !updateBusy, onClick = { showUpdateDialog = false }) { Text("Depois") }
                }
            }
        )
    }

    activating?.let { device ->
        ActivateDialog(
            device = device,
            token = token,
            repo = repo,
            onDismiss = { activating = null },
            onSaved = {
                activating = null
                tab = Tab.CLIENTS
                reload()
            }
        )
    }

    editing?.let { device ->
        EditDialog(
            device = device,
            token = token,
            repo = repo,
            onDismiss = { editing = null },
            onChanged = {
                editing = null
                reload()
            }
        )
    }
}

@Composable
private fun UpdatesTab(
    info: AdminUpdateInfo?,
    checking: Boolean,
    busy: Boolean,
    lastCheck: String,
    error: String?,
    onCheck: () -> Unit,
    onInstall: (AdminUpdateInfo) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Atualizações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("O ViraPlay ADM também recebe novas versões sem precisar procurar APK manualmente.", color = Muted, fontSize = 12.sp)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Versão instalada", color = Muted, fontSize = 11.sp)
                    Text("ViraPlay ADM ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(lastCheck, color = if (info != null) Cyan else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Button(
                        onClick = onCheck,
                        enabled = !checking && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    ) {
                        Text(if (checking) "Verificando..." else "Verificar agora")
                    }
                }
            }
        }
        info?.let { update ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Nova versão ${update.versionName}", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(update.message, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        if (update.mandatory) {
                            Text("Obrigatória", color = Danger, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Button(
                            onClick = { onInstall(update) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        ) {
                            Text(if (busy) "Baixando..." else "Baixar e instalar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTab(
    clients: Int,
    active: Int,
    blocked: Int,
    pending: List<AdminDevice>,
    onActivate: (AdminDevice) -> Unit,
    onPending: () -> Unit,
    onClients: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Visão geral", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("Controle simples dos seus aparelhos ViraPlay.", color = Muted, fontSize = 12.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Clientes", clients, Cyan, Modifier.weight(1f))
                Metric("Ativos", active, Green, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Pendentes", pending.size, Purple, Modifier.weight(1f))
                Metric("Bloqueados", blocked, Danger, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPending, modifier = Modifier.weight(1f)) { Text("Ver pendentes") }
                OutlinedButton(onClick = onClients, modifier = Modifier.weight(1f)) { Text("Ver clientes") }
            }
        }
        if (pending.isNotEmpty()) {
            item { Text("Aguardando ativação", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(pending.take(6), key = { it.deviceId }) { item -> PendingCard(item) { onActivate(item) } }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), color = color, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PendingTab(items: List<AdminDevice>, onActivate: (AdminDevice) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Novos dispositivos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text("O cliente instala o app e o código aparece aqui automaticamente.", color = Muted, fontSize = 12.sp)
        }
        if (items.isEmpty()) item { EmptyCard("Nenhum aparelho aguardando ativação.") }
        items(items, key = { it.deviceId }) { device -> PendingCard(device) { onActivate(device) } }
    }
}

@Composable
private fun PendingCard(device: AdminDevice, onActivate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Purple.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    device.pairingCode,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Novo aparelho", color = Color.White, fontWeight = FontWeight.Bold)
                Text(device.platform ?: "Android", color = Muted, fontSize = 11.sp)
            }
            Button(onClick = onActivate) { Text("Ativar") }
        }
    }
}

@Composable
private fun ClientsTab(
    items: List<AdminDevice>,
    search: String,
    onSearch: (String) -> Unit,
    onOpen: (AdminDevice) -> Unit
) {
    var filter by remember { mutableStateOf(ClientFilter.ALL) }

    val filtered = items.filter { device ->
        val days = ClientMetaCodec.daysUntil(ClientMetaCodec.decode(device.label).expiresIso)
        when (filter) {
            ClientFilter.ALL -> true
            ClientFilter.EXPIRING -> days != null && days in 0..7
            ClientFilter.EXPIRED -> days != null && days < 0
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Clientes",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("Buscar nome, identificador, código ou usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClientFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) item { EmptyCard("Nenhum cliente encontrado neste filtro.") }
            items(filtered, key = { it.deviceId }) { device ->
                ClientCard(device) { onOpen(device) }
            }
        }
    }
}

@Composable
private fun ClientCard(device: AdminDevice, onClick: () -> Unit) {
    val meta = ClientMetaCodec.decode(device.label)
    val days = ClientMetaCodec.daysUntil(meta.expiresIso)
    val expiryText = ClientMetaCodec.isoToDisplay(meta.expiresIso)

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    meta.name.ifBlank { "Sem nome" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Código ${device.pairingCode} • ${device.platform ?: "Android"}", color = Muted, fontSize = 11.sp)
                meta.identifier?.let {
                    Text("Identificador: $it", color = Cyan, fontSize = 11.sp)
                }
                expiryText?.let {
                    val color = when {
                        days == null -> Muted
                        days < 0 -> Danger
                        days <= 7 -> Purple
                        else -> Green
                    }
                    val suffix = when {
                        days == null -> ""
                        days < 0 -> " • vencido"
                        days == 0 -> " • vence hoje"
                        days <= 7 -> " • vence em $days dia(s)"
                        else -> ""
                    }
                    Text("Vencimento: $it$suffix", color = color, fontSize = 11.sp)
                }
                playlistUsername(device.playlistUrl)?.let {
                    Text("Usuário da lista: $it", color = Muted, fontSize = 10.sp)
                }
            }
            StatusPill(device.enabled)
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean) {
    Surface(
        color = if (enabled) Green.copy(alpha = 0.16f) else Danger.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            if (enabled) "ATIVO" else "BLOQUEADO",
            color = if (enabled) Green else Danger,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ActivateDialog(
    device: AdminDevice,
    token: String,
    repo: AdminRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var expiryInput by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun detectExpiry() {
        if (playlist.isBlank()) return
        detecting = true
        error = null
        scope.launch {
            try {
                val iso = withContext(Dispatchers.IO) { repo.detectExpiryIso(playlist.trim()) }
                if (iso != null) {
                    expiryInput = ClientMetaCodec.isoToDisplay(iso).orEmpty()
                } else {
                    error = "O servidor não informou uma data de vencimento. Você pode preencher manualmente."
                }
            } catch (e: Throwable) {
                error = "Não foi possível consultar o vencimento agora."
            } finally {
                detecting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ativar ${device.pairingCode}") },
        text = {
            Column {
                Text(
                    "O código já veio do aparelho. Complete apenas os dados do cliente.",
                    color = Muted,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do cliente/aparelho") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Identificador interno (opcional)") },
                    supportingText = { Text("Ex.: Rany, Sala, Cliente 004") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it.trim() },
                    label = { Text("URL M3U") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                playlistUsername(playlist)?.let {
                    Text("Usuário detectado: $it", color = Cyan, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = expiryInput,
                    onValueChange = { expiryInput = it },
                    label = { Text("Vencimento (DD/MM/AAAA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                TextButton(
                    onClick = { detectExpiry() },
                    enabled = playlist.isNotBlank() && !detecting
                ) {
                    Text(if (detecting) "Consultando..." else "Detectar vencimento da lista")
                }

                error?.let {
                    Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || playlist.isBlank()) {
                        error = "Preencha nome e lista."
                        return@Button
                    }
                    val expiryIso = ClientMetaCodec.inputToIso(expiryInput)
                    if (expiryInput.isNotBlank() && expiryIso == null) {
                        error = "Data inválida. Use DD/MM/AAAA."
                        return@Button
                    }

                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repo.claim(
                                    token = token,
                                    code = device.pairingCode,
                                    name = name.trim(),
                                    identifier = identifier.trim().takeIf { it.isNotBlank() },
                                    expiresIso = expiryIso,
                                    playlist = playlist.trim()
                                )
                            }
                            onSaved()
                        } catch (e: Throwable) {
                            error = (e.message ?: "Falha ao ativar").take(120)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy
            ) {
                Text(if (busy) "Ativando..." else "Ativar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditDialog(
    device: AdminDevice,
    token: String,
    repo: AdminRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialMeta = remember(device.deviceId, device.label) { ClientMetaCodec.decode(device.label) }

    var name by remember { mutableStateOf(initialMeta.name) }
    var identifier by remember { mutableStateOf(initialMeta.identifier.orEmpty()) }
    var expiryInput by remember { mutableStateOf(ClientMetaCodec.isoToDisplay(initialMeta.expiresIso).orEmpty()) }
    var playlist by remember { mutableStateOf(device.playlistUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun expiryIsoOrError(): String? {
        val parsed = ClientMetaCodec.inputToIso(expiryInput)
        if (expiryInput.isNotBlank() && parsed == null) {
            error = "Data inválida. Use DD/MM/AAAA."
        }
        return parsed
    }

    fun runAction(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                onChanged()
            } catch (e: Throwable) {
                error = (e.message ?: "Falha na operação").take(120)
            } finally {
                busy = false
            }
        }
    }

    fun detectExpiry() {
        if (playlist.isBlank()) return
        detecting = true
        error = null
        scope.launch {
            try {
                val iso = withContext(Dispatchers.IO) { repo.detectExpiryIso(playlist.trim()) }
                if (iso != null) {
                    expiryInput = ClientMetaCodec.isoToDisplay(iso).orEmpty()
                } else {
                    error = "O servidor não informou a data de vencimento."
                }
            } catch (e: Throwable) {
                error = "Não foi possível consultar o vencimento agora."
            } finally {
                detecting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name.ifBlank { "Cliente" }) },
        text = {
            Column {
                Text("Código ${device.pairingCode}", color = Cyan, fontWeight = FontWeight.Bold)
                Text(device.platform ?: "Android", color = Muted, fontSize = 11.sp)
                playlistUsername(playlist)?.let { Text("Usuário da lista: $it", color = Muted, fontSize = 11.sp) }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Identificador interno") },
                    supportingText = { Text("Só aparece no seu ADM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it.trim() },
                    label = { Text("URL M3U") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                OutlinedTextField(
                    value = expiryInput,
                    onValueChange = { expiryInput = it },
                    label = { Text("Vencimento (DD/MM/AAAA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                TextButton(
                    onClick = { detectExpiry() },
                    enabled = playlist.isNotBlank() && !detecting
                ) {
                    Text(if (detecting) "Consultando..." else "Atualizar vencimento pela lista")
                }

                error?.let {
                    Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val expiryIso = expiryIsoOrError()
                            if (expiryInput.isNotBlank() && expiryIso == null) return@OutlinedButton
                            runAction {
                                withContext(Dispatchers.IO) {
                                    repo.update(
                                        token = token,
                                        deviceId = device.deviceId,
                                        enabled = !device.enabled,
                                        playlist = playlist,
                                        name = name,
                                        identifier = identifier.trim().takeIf { it.isNotBlank() },
                                        expiresIso = expiryIso
                                    )
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (device.enabled) "Bloquear" else "Liberar")
                    }

                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Excluir")
                    }
                }

                if (confirmDelete) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Excluir este aparelho da sua lista?", color = Color.White, fontWeight = FontWeight.Bold)
                            Row {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
                                TextButton(onClick = {
                                    runAction {
                                        withContext(Dispatchers.IO) { repo.delete(token, device.deviceId) }
                                    }
                                }) {
                                    Text("Confirmar exclusão", color = Danger)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val expiryIso = expiryIsoOrError()
                    if (expiryInput.isNotBlank() && expiryIso == null) return@Button
                    runAction {
                        withContext(Dispatchers.IO) {
                            repo.update(
                                token = token,
                                deviceId = device.deviceId,
                                enabled = device.enabled,
                                playlist = playlist,
                                name = name,
                                identifier = identifier.trim().takeIf { it.isNotBlank() },
                                expiresIso = expiryIso
                            )
                        }
                    }
                },
                enabled = !busy
            ) {
                Text(if (busy) "Salvando..." else "Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, modifier = Modifier.padding(18.dp))
    }
}

private fun playlistUsername(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val query = url.substringAfter('?', "")
    if (query.isBlank()) return null
    val raw = query.split('&')
        .firstOrNull { it.startsWith("username=", ignoreCase = true) }
        ?.substringAfter('=')
        ?: return null
    return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).takeIf { it.isNotBlank() }
}
