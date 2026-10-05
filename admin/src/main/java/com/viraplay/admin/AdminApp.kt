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
    DASHBOARD("Painel"), ACTIVATE("Ativar"), CLIENTS("Clientes"), PARTNERS("Parceiros"), CREDITS("Créditos"), UPDATES("Atualizações")
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
                Text("MASTER e parceiros usam o mesmo aplicativo.", color = Muted, fontSize = 13.sp)
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
    var credits by remember { mutableStateOf<List<CreditEntry>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var status by remember { mutableStateOf("Conectando...") }
    var firstLoad by remember { mutableStateOf(true) }
    var selectedClient by remember { mutableStateOf<AdminDevice?>(null) }
    var newPartnerDialog by remember { mutableStateOf(false) }
    var grantPartner by remember { mutableStateOf<PartnerInfo?>(null) }
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
                credits = emptyList()
                val partnerResult = withContext(Dispatchers.IO) { runCatching { repo.partners(token) } }
                partners = partnerResult.getOrDefault(emptyList())
                partnerBackendReady = partnerResult.isSuccess
            } else {
                partners = emptyList()
                partnerBackendReady = true
                credits = withContext(Dispatchers.IO) { repo.creditHistory(token) }
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
            listOf(Tab.DASHBOARD, Tab.ACTIVATE, Tab.CLIENTS, Tab.CREDITS, Tab.UPDATES)
        }
    }
    if (tab !in tabs) tab = Tab.DASHBOARD

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Brand()
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(currentProfile?.name ?: status, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (currentProfile?.isMaster == false) "${currentProfile.credits} créditos" else status,
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
            NavigationBar(containerColor = Panel) {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(navGlyph(item), fontSize = 10.sp, fontWeight = FontWeight.Black) },
                        label = { Text(item.label, fontSize = 9.sp) }
                    )
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
                    onGrant = { grantPartner = it }
                )
                Tab.CREDITS -> CreditsTab(currentProfile, credits)
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
                                when (updateManager.launchInstaller(apk)) {
                                    AdminInstallLaunchResult.STARTED -> Unit
                                    AdminInstallLaunchResult.NEED_PERMISSION -> updateError = "Autorize a instalação e tente novamente."
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

    grantPartner?.let { partner ->
        GrantCreditsDialog(partner, token, repo, onDismiss = { grantPartner = null }, onSaved = {
            grantPartner = null
            reload()
        })
    }
}

private fun navGlyph(tab: Tab): String = when (tab) {
    Tab.DASHBOARD -> "ADM"
    Tab.ACTIVATE -> "+"
    Tab.CLIENTS -> "CL"
    Tab.PARTNERS -> "P"
    Tab.CREDITS -> "CR"
    Tab.UPDATES -> "UPD"
}

@Composable
private fun DashboardTab(profile: AdminProfile?, devices: List<AdminDevice>, partnerCount: Int, go: (Tab) -> Unit) {
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() }
    val active = clients.count { it.enabled }
    val blocked = clients.size - active
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                when {
                    profile == null -> "Carregando painel..."
                    profile.isMaster -> "Painel MASTER"
                    else -> "Painel do parceiro"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )
            Text("VPlayo Android + Android TV", color = Muted, fontSize = 12.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Clientes", clients.size, Cyan, Modifier.weight(1f))
                Metric("Ativos", active, Green, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Bloqueados", blocked, Danger, Modifier.weight(1f))
                Metric(if (profile?.isMaster == true) "Parceiros" else "Créditos", if (profile?.isMaster == true) partnerCount else profile?.credits ?: 0, Purple, Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = { go(Tab.ACTIVATE) }, modifier = Modifier.fillMaxWidth()) { Text("Ativar aparelho por código") }
        }
        if (profile?.isMaster == false) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = .08f)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Licença anual", color = Cyan, fontWeight = FontWeight.Bold)
                        Text("Cada nova ativação consome ${profile.annualLicenseCredits} créditos. O aparelho fica vinculado ao seu painel.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 11.sp)
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
                        if (profile?.isMaster == false) {
                            Text("Custo: ${profile.annualLicenseCredits} créditos • licença VPlayo por 12 meses", color = Purple, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                        } else {
                            Text("Cliente MASTER • sem consumo de créditos", color = Green, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                        }
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
                                            (e.message ?: "").contains("insufficient_credits") -> "Créditos insuficientes para esta ativação."
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

@Composable
private fun PartnersTab(
    partners: List<PartnerInfo>,
    backendReady: Boolean,
    onNew: () -> Unit,
    onGrant: (PartnerInfo) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Parceiros", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text("Área exclusiva do MASTER. Cada parceiro enxerga somente os próprios clientes.", color = Muted, fontSize = 12.sp)

            if (!backendReady) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = .08f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Configuração de parceiros pendente", color = Cyan, fontWeight = FontWeight.Bold)
                        Text(
                            "O painel MASTER já está correto. Falta apenas ativar a estrutura de parceiros no Worker e no Supabase para liberar novos cadastros.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Button(
                onClick = onNew,
                enabled = backendReady,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("Cadastrar parceiro") }
        }
        if (partners.isEmpty()) item { EmptyCard("Nenhum parceiro cadastrado.") }
        items(partners, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Login: ${p.loginCode} • ${p.clients} cliente(s)", color = Muted, fontSize = 10.sp)
                        }
                        Text("${p.credits} CR", color = Cyan, fontWeight = FontWeight.Black)
                    }
                    Text("Chave ADM: ${p.accessToken}", color = Purple, fontSize = 10.sp, maxLines = 2)
                    OutlinedButton(onClick = { onGrant(p) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Adicionar créditos") }
                }
            }
        }
    }
}

@Composable
private fun CreditsTab(profile: AdminProfile?, history: List<CreditEntry>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Créditos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text("Saldo atual: ${profile?.credits ?: 0}", color = Cyan, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text("Ativação anual: ${profile?.annualLicenseCredits ?: 15} créditos por aparelho.", color = Muted, fontSize = 11.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = .10f)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Text("Pix automático Asaas", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("A estrutura já está preparada para compra automática. A liberação do Pix será ativada quando a chave da API Asaas for configurada no servidor.", color = Muted, fontSize = 11.sp)
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Comprar créditos por Pix — em preparação") }
                }
            }
        }
        item { Text("Histórico", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (history.isEmpty()) item { EmptyCard("Nenhuma movimentação ainda.") }
        items(history) { h ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (h.amount >= 0) "+${h.amount}" else h.amount.toString(), color = if (h.amount >= 0) Green else Danger, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(h.kind, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        h.note?.let { Text(it, color = Muted, fontSize = 10.sp) }
                    }
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
        title = { Text(if (result == null) "Novo parceiro" else "Parceiro criado") },
        text = {
            Column {
                if (result == null) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do parceiro/revenda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("${result!!.name}\nLogin: ${result!!.loginCode}\nChave ADM: ${result!!.accessToken}", color = Color.White)
                    Text("Guarde a chave. Ela dá acesso somente aos clientes deste parceiro.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
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
                    catch (e: Throwable) { error = e.message ?: "Falha ao criar parceiro" }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(if (busy) "Criando..." else "Criar") }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GrantCreditsDialog(partner: PartnerInfo, token: String, repo: AdminRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créditos • ${partner.name}") },
        text = {
            Column {
                Text("Saldo atual: ${partner.credits}", color = Cyan, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(6) }, label = { Text("Quantidade a adicionar") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                error?.let { Text(it, color = Danger, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = amount.toIntOrNull() ?: return@Button
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { repo.grantCredits(token, partner.id, n) }; onSaved() }
                    catch (e: Throwable) { error = e.message ?: "Falha ao adicionar créditos" }
                    finally { busy = false }
                }
            }, enabled = !busy && (amount.toIntOrNull() ?: 0) > 0) { Text(if (busy) "Salvando..." else "Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
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
