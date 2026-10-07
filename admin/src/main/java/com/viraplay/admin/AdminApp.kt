package com.viraplay.admin

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
    DASHBOARD("Painel"), ACTIVATE("Ativar"), CLIENTS("Clientes"), PARTNERS("Provedores"), PROVIDER("Servidor"), UPDATES("Atualizações")
}

@Composable
fun AdminApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("viraplay_admin", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("admin_token", "").orEmpty()) }
    var logged by remember { mutableStateOf(token.isNotBlank()) }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = Cyan, secondary = Purple, background = Bg, surface = Panel, error = Danger)
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
                            val clean = token.trim()
                            prefs.edit().putString("admin_token", clean).apply()
                            token = clean
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
        contentDescription = "VPlayo ADM",
        contentScale = ContentScale.Fit,
        modifier = modifier.width(170.dp).height(52.dp)
    )
}

@Composable
private fun Login(token: String, onToken: (String) -> Unit, onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Brand(Modifier.width(270.dp).height(90.dp))
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("VPlayo ADM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Text("MASTER e provedores usam o mesmo aplicativo.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = token,
                    onValueChange = onToken,
                    label = { Text("Chave de acesso") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                )
                Button(onClick = onLogin, enabled = token.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Entrar") }
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

    var profile by remember { mutableStateOf<AdminProfile?>(null) }
    var devices by remember { mutableStateOf<List<AdminDevice>>(emptyList()) }
    var partners by remember { mutableStateOf<List<PartnerInfo>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var status by remember { mutableStateOf("Conectando...") }
    var firstLoad by remember { mutableStateOf(true) }
    var selectedClient by remember { mutableStateOf<AdminDevice?>(null) }
    var newPartnerDialog by remember { mutableStateOf(false) }
    var editPartner by remember { mutableStateOf<PartnerInfo?>(null) }
    var deletePartner by remember { mutableStateOf<PartnerInfo?>(null) }
    var partnerBackendReady by remember { mutableStateOf(true) }

    var updateInfo by remember { mutableStateOf<AdminUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val p = withContext(Dispatchers.IO) { repo.profile(token) }
            val ds = withContext(Dispatchers.IO) {
                val base = repo.list(token)
                repo.enrichMissingExpiries(token, base)
            }
            profile = p
            devices = ds

            if (p.isMaster) {
                val partnerResult = withContext(Dispatchers.IO) { runCatching { repo.partners(token) } }
                partners = partnerResult.getOrDefault(emptyList())
                partnerBackendReady = partnerResult.isSuccess
            } else {
                partners = emptyList()
                partnerBackendReady = true
            }

            status = "${ds.size} cliente(s)"
        } catch (e: Throwable) {
            status = "Falha: ${(e.message ?: "sem conexão").take(70)}"
        } finally {
            firstLoad = false
        }
    }

    fun reload() { scope.launch { load() } }

    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(30_000)
            load()
        }
    }

    val currentProfile = profile
    val tabs = remember(currentProfile?.role) {
        if (currentProfile?.isMaster == true) {
            listOf(Tab.DASHBOARD, Tab.ACTIVATE, Tab.CLIENTS, Tab.PARTNERS, Tab.UPDATES)
        } else {
            listOf(Tab.DASHBOARD, Tab.PROVIDER, Tab.UPDATES)
        }
    }
    if (tab !in tabs) tab = Tab.DASHBOARD

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.horizontalGradient(listOf(Color(0xFF05162E), Color(0xFF0B1D3C), Color(0xFF130C2E)))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Brand()
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(currentProfile?.name ?: status, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (currentProfile?.isMaster == false) "${currentProfile.activeWindow} ativos / ${currentProfile.activeWindowDays} dias" else status,
                            color = if (currentProfile?.isMaster == false) Cyan else Muted,
                            fontSize = 10.sp
                        )
                    }
                    TextButton(onClick = { reload() }) { Text("↻") }
                    TextButton(onClick = onLogout) { Text("Sair") }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF061B36),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    imageVector = navIcon(item),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = { Text(item.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan,
                                selectedTextColor = Cyan,
                                indicatorColor = Purple.copy(alpha = .26f),
                                unselectedIconColor = Color.White.copy(alpha = .70f),
                                unselectedTextColor = Color.White.copy(alpha = .68f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardTab(currentProfile, devices, partners.size) { tab = it }
                Tab.ACTIVATE -> ActivateByCodeTab(
                    token = token,
                    repo = repo,
                    profile = currentProfile,
                    onActivated = { reload(); tab = Tab.CLIENTS }
                )
                Tab.CLIENTS -> ClientsTab(devices) { selectedClient = it }
                Tab.PARTNERS -> PartnersTab(
                    partners = partners,
                    backendReady = partnerBackendReady,
                    onNew = { newPartnerDialog = true },
                    onEdit = { editPartner = it },
                    onDelete = { deletePartner = it }
                )
                Tab.PROVIDER -> ProviderConfigTab(
                    profile = currentProfile,
                    token = token,
                    repo = repo,
                    onSaved = { reload() }
                )
                Tab.UPDATES -> UpdatesTab(
                    info = updateInfo,
                    checking = checkingUpdate,
                    busy = updateBusy,
                    error = updateError,
                    onCheck = {
                        checkingUpdate = true
                        updateError = null
                        scope.launch {
                            try { updateInfo = withContext(Dispatchers.IO) { updateManager.check() } }
                            catch (e: Throwable) { updateError = e.message ?: "Falha ao verificar" }
                            finally { checkingUpdate = false }
                        }
                    },
                    onInstall = { info ->
                        updateBusy = true
                        updateError = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { updateManager.download(info) } }
                            result.onSuccess { apk ->
                                updateBusy = false
                                runCatching { updateManager.launchInstaller(apk) }
                                    .onSuccess { launch ->
                                        when (launch) {
                                            AdminInstallLaunchResult.STARTED -> Unit
                                            AdminInstallLaunchResult.NEED_PERMISSION ->
                                                updateError = "Autorize a instalação de apps desta fonte e toque novamente."
                                        }
                                    }
                                    .onFailure { e ->
                                        updateError = e.message ?: "Não foi possível abrir o instalador."
                                    }
                            }.onFailure {
                                updateBusy = false
                                updateError = it.message ?: "Falha ao baixar atualização"
                            }
                        }
                    }
                )
            }
            if (firstLoad) CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.Center))
        }
    }

    selectedClient?.let { device ->
        EditClientDialog(device, token, repo, onDismiss = { selectedClient = null }, onChanged = {
            selectedClient = null
            reload()
        })
    }

    if (newPartnerDialog) {
        NewPartnerDialog(token, repo, onDismiss = { newPartnerDialog = false }, onCreated = {
            newPartnerDialog = false
            reload()
        })
    }


    editPartner?.let { partner ->
        EditPartnerDialog(
            partner = partner,
            token = token,
            repo = repo,
            onDismiss = { editPartner = null },
            onSaved = {
                editPartner = null
                reload()
            }
        )
    }

    deletePartner?.let { partner ->
        DeletePartnerDialog(
            partner = partner,
            token = token,
            repo = repo,
            onDismiss = { deletePartner = null },
            onDeleted = {
                deletePartner = null
                reload()
            }
        )
    }
}

private fun navIcon(tab: Tab): ImageVector = when (tab) {
    Tab.DASHBOARD -> Icons.Filled.Home
    Tab.ACTIVATE -> Icons.Filled.AddCircle
    Tab.CLIENTS -> Icons.Filled.People
    Tab.PARTNERS -> Icons.Filled.Dns
    Tab.PROVIDER -> Icons.Filled.Settings
    Tab.UPDATES -> Icons.Filled.SystemUpdateAlt
}

@Composable
private fun DashboardTab(
    profile: AdminProfile?,
    devices: List<AdminDevice>,
    partnerCount: Int,
    go: (Tab) -> Unit
) {
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() }
    val active = clients.count { it.enabled }
    val blocked = clients.size - active
    val isMaster = profile?.isMaster != false

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                if (isMaster) "Painel MASTER" else "Painel do provedor",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp
            )
            Text(
                if (isMaster) "VPlayo Android + Android TV"
                else "Atividade do aplicativo em tempo real",
                color = Muted,
                fontSize = 12.sp
            )
        }

        if (isMaster) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Clientes", clients.size, Cyan, Icons.Filled.People, Modifier.weight(1f))
                    Metric("Ativos", active, Green, Icons.Filled.CheckCircle, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Bloqueados", blocked, Danger, Icons.Filled.Block, Modifier.weight(1f))
                    Metric("Provedores", partnerCount, Purple, Icons.Filled.Dns, Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = { go(Tab.ACTIVATE) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ativar aparelho por código", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Total", profile?.totalDevices ?: 0, Cyan, Icons.Filled.People, Modifier.weight(1f))
                    Metric("24 horas", profile?.activeToday ?: 0, Green, Icons.Filled.CheckCircle, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("7 dias", profile?.active7d ?: 0, Purple, Icons.Filled.Tune, Modifier.weight(1f))
                    Metric(
                        "${profile?.activeWindowDays ?: 10} dias",
                        profile?.activeWindow ?: 0,
                        Cyan,
                        Icons.Filled.Dns,
                        Modifier.weight(1f)
                    )
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = .08f)),
                    border = BorderStroke(1.dp, Cyan.copy(alpha = .20f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Modelo por aparelho ativo", color = Cyan, fontWeight = FontWeight.Bold)
                        Text(
                            "Um aparelho conta como ativo enquanto tiver atividade dentro da janela de ${profile?.activeWindowDays ?: 10} dias definida pelo MASTER.",
                            color = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { go(Tab.PROVIDER) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar servidor / DNS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, color.copy(alpha = .28f)),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            color.copy(alpha = .16f),
                            Color(0xFF071A34),
                            Color(0xFF0B1933)
                        )
                    )
                )
                .padding(17.dp)
        ) {
            Column {
                Surface(
                    color = color.copy(alpha = .16f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(value.toString(), color = color, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(
                    label.uppercase(),
                    color = Color.White.copy(alpha = .78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActivateByCodeTab(token: String, repo: AdminRepository, profile: AdminProfile?, onActivated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var device by remember { mutableStateOf<AdminDevice?>(null) }
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun lookup() {
        if (code.isBlank()) return
        busy = true; error = null
        scope.launch {
            try {
                device = withContext(Dispatchers.IO) { repo.lookup(token, code) }
            } catch (e: Throwable) {
                error = when {
                    (e.message ?: "").contains("409") -> "Este aparelho já pertence a outro parceiro."
                    else -> "Código não encontrado ou indisponível."
                }
            } finally { busy = false }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Ativar aparelho", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text("O cliente instala o VPlayo e envia o código. Ele só aparece no seu painel depois que você ativar.", color = Muted, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(8); device = null }, label = { Text("Código do aparelho") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { lookup() }, enabled = code.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (busy) "Buscando..." else "Buscar código") }
        }
        device?.let { found ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${found.pairingCode} • ${found.platform ?: "Android"}", color = Cyan, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(value = identifier, onValueChange = { identifier = it }, label = { Text("Identificação (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        OutlinedTextField(value = playlist, onValueChange = { playlist = it }, label = { Text("Lista M3U / URL") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Vencimento da lista (DD/MM/AAAA, opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        Text(
                            "Ativação individual do MASTER",
                            color = Green,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Button(
                            onClick = {
                                if (name.isBlank() || playlist.isBlank()) { error = "Preencha nome e lista."; return@Button }
                                val expiryIso = ClientMetaCodec.inputToIso(expiry)
                                if (expiry.isNotBlank() && expiryIso == null) { error = "Data inválida."; return@Button }
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            repo.claim(token, found.pairingCode, name.trim(), identifier.trim().takeIf { it.isNotBlank() }, expiryIso, playlist.trim(), 12)
                                        }
                                        onActivated()
                                    } catch (e: Throwable) {
                                        error = when {
                                            (e.message ?: "").contains("409") -> "Este aparelho já foi vinculado a outro parceiro."
                                            else -> "Não foi possível ativar: ${(e.message ?: "erro").take(80)}"
                                        }
                                    } finally { busy = false }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(if (busy) "Ativando..." else "Ativar por 12 meses") }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = Danger, fontSize = 12.sp) } }
    }
}

@Composable
private fun ClientsTab(devices: List<AdminDevice>, onOpen: (AdminDevice) -> Unit) {
    var search by remember { mutableStateOf("") }
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() && it.label != AdminRepository.DELETED_MARKER }.filter { d ->
        val m = ClientMetaCodec.decode(d.label)
        search.isBlank() || m.name.contains(search, true) || d.pairingCode.contains(search, true) || m.identifier.orEmpty().contains(search, true)
    }
    Column(Modifier.fillMaxSize()) {
        Text("Clientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Buscar cliente ou código") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (clients.isEmpty()) item { EmptyCard("Nenhum cliente encontrado.") }
            items(clients, key = { it.deviceId }) { d -> ClientCard(d) { onOpen(d) } }
        }
    }
}

@Composable
private fun ClientCard(device: AdminDevice, onClick: () -> Unit) {
    val meta = ClientMetaCodec.decode(device.label)
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meta.name.ifBlank { "Cliente" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Código ${device.pairingCode} • ${device.platform ?: "Android"}", color = Muted, fontSize = 10.sp)
                ClientMetaCodec.isoToDisplay(meta.expiresIso)?.let { Text("Lista vence: $it", color = Cyan, fontSize = 10.sp) }
                playlistUsername(device.playlistUrl)?.let { Text("Usuário: $it", color = Muted, fontSize = 10.sp) }
            }
            StatusPill(device.enabled)
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean) {
    Surface(color = if (enabled) Green.copy(alpha = .16f) else Danger.copy(alpha = .16f), shape = RoundedCornerShape(20.dp)) {
        Text(if (enabled) "ATIVO" else "BLOQUEADO", color = if (enabled) Green else Danger, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

private fun copyPartnerText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
}

@Composable
private fun PartnersTab(
    partners: List<PartnerInfo>,
    backendReady: Boolean,
    onNew: () -> Unit,
    onEdit: (PartnerInfo) -> Unit,
    onDelete: (PartnerInfo) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Provedores", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text(
                "Crie acessos, acompanhe aparelhos ativos e defina a janela usada na parceria.",
                color = Muted,
                fontSize = 12.sp
            )

            if (!backendReady) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = .08f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        "Publique o Worker atualizado para liberar o gerenciamento de provedores.",
                        color = Danger,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Button(
                onClick = onNew,
                enabled = backendReady,
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 10.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.AddCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Cadastrar provedor", fontWeight = FontWeight.Bold)
            }
        }

        if (partners.isEmpty()) item { EmptyCard("Nenhum provedor cadastrado.") }

        items(partners, key = { it.id }) { p ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(
                    1.dp,
                    if (p.status.equals("ACTIVE", true)) Cyan.copy(alpha = .24f) else Danger.copy(alpha = .28f)
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF071A34),
                                    Color(0xFF0A1D3A),
                                    Purple.copy(alpha = .08f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Purple.copy(alpha = .16f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Dns, null, tint = Purple)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (p.status.equals("ACTIVE", true)) "PROVEDOR ATIVO" else "PROVEDOR BLOQUEADO",
                                color = if (p.status.equals("ACTIVE", true)) Green else Danger,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        StatusPill(p.status.equals("ACTIVE", true))
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderMiniMetric("TOTAL", p.totalDevices, Cyan, Modifier.weight(1f))
                        ProviderMiniMetric("24H", p.activeToday, Green, Modifier.weight(1f))
                        ProviderMiniMetric("${p.activeWindowDays}D", p.activeWindow, Purple, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = if (p.dnsConfigured) Green.copy(alpha = .10f) else Danger.copy(alpha = .10f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (p.dnsConfigured) Green.copy(alpha = .22f) else Danger.copy(alpha = .22f)
                        )
                    ) {
                        Text(
                            if (p.dnsConfigured) "DNS configurado • secundário ${if (p.dnsSecondary.isNullOrBlank()) "não definido" else "pronto"}"
                            else "DNS ainda não configurado",
                            color = if (p.dnsConfigured) Green else Danger,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Código do provedor", color = Muted, fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.loginCode, color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        TextButton(onClick = { copyPartnerText(context, "Código do provedor", p.loginCode) }) {
                            Text("Copiar")
                        }
                    }

                    Text("Chave ADM", color = Muted, fontSize = 9.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.accessToken,
                            color = Color.White.copy(alpha = .82f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { copyPartnerText(context, "Chave ADM", p.accessToken) }) {
                            Text("Copiar")
                        }
                    }

                    OutlinedButton(
                        onClick = { onEdit(p) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Settings, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Configurar provedor")
                    }

                    TextButton(onClick = { onDelete(p) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Encerrar e excluir provedor", color = Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderMiniMetric(label: String, value: Int, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = .08f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .18f)),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), color = color, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditPartnerDialog(
    partner: PartnerInfo,
    token: String,
    repo: AdminRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(partner.id) { mutableStateOf(partner.name) }
    var active by remember(partner.id) { mutableStateOf(partner.status.equals("ACTIVE", true)) }
    var dnsPrimary by remember(partner.id) { mutableStateOf(partner.dnsPrimary.orEmpty()) }
    var dnsSecondary by remember(partner.id) { mutableStateOf(partner.dnsSecondary.orEmpty()) }
    var activeWindow by remember(partner.id) { mutableStateOf(partner.activeWindowDays.toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Editar provedor") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome / identificação") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dnsPrimary,
                    onValueChange = { dnsPrimary = it },
                    label = { Text("DNS principal") },
                    placeholder = { Text("http://servidor.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = dnsSecondary,
                    onValueChange = { dnsSecondary = it },
                    label = { Text("DNS secundário (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = activeWindow,
                    onValueChange = { activeWindow = it.filter(Char::isDigit).take(2) },
                    label = { Text("Dias para considerar aparelho ativo") },
                    supportingText = { Text("Ex.: 10 dias. Somente o MASTER define esta regra.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (active) "Provedor ativo" else "Provedor bloqueado", fontWeight = FontWeight.Bold)
                        Text(
                            if (active) "Pode entrar no VPlayo ADM e configurar o DNS."
                            else "O acesso do provedor e o código ficam bloqueados.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
                error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repo.updatePartner(
                                    token,
                                    partner.id,
                                    name.trim(),
                                    if (active) "ACTIVE" else "BLOCKED",
                                    activeWindow.toIntOrNull()?.coerceIn(1, 60) ?: partner.activeWindowDays
                                )
                                if (dnsPrimary.isNotBlank()) {
                                    repo.saveProviderConfig(token, dnsPrimary, dnsSecondary, partner.id)
                                }
                            }
                            onSaved()
                        } catch (e: Throwable) {
                            error = e.message ?: "Falha ao salvar provedor."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Salvando..." else "Salvar") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DeletePartnerDialog(
    partner: PartnerInfo,
    token: String,
    repo: AdminRepository,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Excluir provedor?") },
        text = {
            Column {
                Text("Provedor: ${partner.name}")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta ação é definitiva. A chave ADM e o código do provedor serão apagados. Ativações individuais vinculadas serão bloqueadas e devolvidas ao MASTER.",
                    color = Muted,
                    fontSize = 12.sp
                )
                error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.deletePartner(token, partner.id) }
                            onDeleted()
                        } catch (e: Throwable) {
                            error = e.message ?: "Falha ao excluir provedor."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Excluindo..." else "Excluir definitivamente") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


@Composable
private fun ProviderConfigTab(
    profile: AdminProfile?,
    token: String,
    repo: AdminRepository,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var primary by remember(profile?.dnsPrimary) { mutableStateOf(profile?.dnsPrimary.orEmpty()) }
    var secondary by remember(profile?.dnsSecondary) { mutableStateOf(profile?.dnsSecondary.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Servidor do provedor", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text("Configure os DNS uma vez. Os clientes entram usando código do provedor + usuário + senha.", color = Muted, fontSize = 12.sp)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Cyan.copy(alpha = .22f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Cyan.copy(alpha = .10f), Panel, Purple.copy(alpha = .06f)))
                    ).padding(18.dp)
                ) {
                    Text("Código do provedor", color = Muted, fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile?.providerCode ?: "—", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        if (!profile?.providerCode.isNullOrBlank()) {
                            TextButton(onClick = { copyPartnerText(context, "Código do provedor", profile!!.providerCode!!) }) { Text("Copiar") }
                        }
                    }
                    Text("Esse código identifica seus DNS no aplicativo do cliente.", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ativos em ${profile?.activeWindowDays ?: 10} dias: ${profile?.activeWindow ?: 0} • Total: ${profile?.totalDevices ?: 0}",
                        color = Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "A janela de atividade é definida pelo MASTER e usada para a contagem comercial.",
                        color = Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        value = primary,
                        onValueChange = { primary = it },
                        label = { Text("DNS principal") },
                        placeholder = { Text("http://servidor.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secondary,
                        onValueChange = { secondary = it },
                        label = { Text("DNS secundário (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text("Se o principal falhar no login, o VPlayo tenta o secundário automaticamente.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        enabled = !busy && primary.isNotBlank(),
                        onClick = {
                            busy = true; error = null; message = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repo.saveProviderConfig(token, primary, secondary) }
                                    message = "DNS salvo. O código do provedor já está pronto para teste."
                                    onSaved()
                                } catch (e: Throwable) {
                                    error = e.message ?: "Não foi possível salvar o DNS."
                                } finally { busy = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    ) { Text(if (busy) "Salvando..." else "Salvar configuração") }
                    message?.let { Text(it, color = Green, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                    error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(info: AdminUpdateInfo?, checking: Boolean, busy: Boolean, error: String?, onCheck: () -> Unit, onInstall: (AdminUpdateInfo) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Atualizações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text("VPlayo ADM ${BuildConfig.VERSION_NAME}", color = Muted)
            Button(onClick = onCheck, enabled = !checking && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (checking) "Verificando..." else "Verificar atualização") }
            error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        info?.let { u ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = .10f)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Versão ${u.versionName}", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(u.message, color = Muted, fontSize = 11.sp)
                        Button(onClick = { onInstall(u) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (busy) "Baixando..." else "Baixar e instalar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPartnerDialog(token: String, repo: AdminRepository, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PartnerInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result == null) "Novo provedor" else "Provedor criado") },
        text = {
            Column {
                if (result == null) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Identificação (ex.: Bruno • BRTV Play)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("${result!!.name}\nCódigo do provedor: ${result!!.loginCode}\nChave ADM: ${result!!.accessToken}", color = Color.White)
                    Text("Entregue a chave ADM ao provedor. O código identifica automaticamente os DNS configurados por ele.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                error?.let { Text(it, color = Danger, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            if (result != null) Button(onClick = onCreated) { Text("Concluir") }
            else Button(onClick = {
                if (name.isBlank()) return@Button
                busy = true
                scope.launch {
                    try { result = withContext(Dispatchers.IO) { repo.createPartner(token, name) } }
                    catch (e: Throwable) { error = e.message ?: "Falha ao criar provedor" }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "Criando..." else "Criar") }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditClientDialog(device: AdminDevice, token: String, repo: AdminRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val meta = remember(device.deviceId) { ClientMetaCodec.decode(device.label) }
    var name by remember { mutableStateOf(meta.name) }
    var identifier by remember { mutableStateOf(meta.identifier.orEmpty()) }
    var expiry by remember { mutableStateOf(ClientMetaCodec.isoToDisplay(meta.expiresIso).orEmpty()) }
    var playlist by remember { mutableStateOf(device.playlistUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { action(); onChanged() }
            catch (e: Throwable) { error = e.message ?: "Falha ao salvar" }
            finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.pairingCode) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = identifier, onValueChange = { identifier = it }, label = { Text("Identificação") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(value = playlist, onValueChange = { playlist = it }, label = { Text("Lista M3U") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Vencimento da lista") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val iso = ClientMetaCodec.inputToIso(expiry)
                        run { withContext(Dispatchers.IO) { repo.update(token, device.deviceId, !device.enabled, playlist, name, identifier.takeIf { it.isNotBlank() }, iso) } }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (device.enabled) "Bloquear" else "Liberar") }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) { Text("Excluir") }
                }
                if (confirmDelete) {
                    Text("Confirme a exclusão abaixo.", color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { run { withContext(Dispatchers.IO) { repo.delete(token, device.deviceId) } } }, colors = ButtonDefaults.buttonColors(containerColor = Danger), modifier = Modifier.fillMaxWidth()) { Text("Confirmar exclusão") }
                }
                error?.let { Text(it, color = Danger, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val iso = ClientMetaCodec.inputToIso(expiry)
                if (expiry.isNotBlank() && iso == null) { error = "Data inválida"; return@Button }
                run { withContext(Dispatchers.IO) { repo.update(token, device.deviceId, device.enabled, playlist, name, identifier.takeIf { it.isNotBlank() }, iso) } }
            }, enabled = !busy) { Text(if (busy) "Salvando..." else "Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, modifier = Modifier.padding(16.dp))
    }
}

private fun playlistUsername(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val query = url.substringAfter('?', "")
        query.split('&').firstOrNull { it.startsWith("username=") }
            ?.substringAfter('=')
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
