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
    DASHBOARD("Painel"), PENDING("Pendentes"), CLIENTS("Clientes")
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
    val repo = remember { AdminRepository() }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<AdminDevice>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Conectando...") }
    var activating by remember { mutableStateOf<AdminDevice?>(null) }
    var editing by remember { mutableStateOf<AdminDevice?>(null) }
    var firstLoad by remember { mutableStateOf(true) }

    suspend fun load() {
        try {
            val loaded = withContext(Dispatchers.IO) { repo.list(token) }
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

    val pending = devices.filter { it.playlistUrl.isNullOrBlank() }
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() }
    val active = clients.count { it.enabled }
    val blocked = clients.size - active
    val visibleClients = clients.filter {
        search.isBlank() ||
            it.label.orEmpty().contains(search, true) ||
            it.pairingCode.contains(search, true) ||
            playlistUsername(it.playlistUrl).orEmpty().contains(search, true)
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
            }

            if (firstLoad) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.Center))
            }
        }
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
    Column(Modifier.fillMaxSize()) {
        Text("Clientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("Buscar nome, código ou usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) item { EmptyCard("Nenhum cliente encontrado.") }
            items(items, key = { it.deviceId }) { device -> ClientCard(device) { onOpen(device) } }
        }
    }
}

@Composable
private fun ClientCard(device: AdminDevice, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.label?.takeIf { it.isNotBlank() } ?: "Sem nome",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Código ${device.pairingCode} • ${device.platform ?: "Android"}", color = Muted, fontSize = 11.sp)
                playlistUsername(device.playlistUrl)?.let {
                    Text("Usuário: $it", color = Cyan, fontSize = 11.sp)
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
    var playlist by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ativar ${device.pairingCode}") },
        text = {
            Column {
                Text("O código já veio do aparelho. Informe apenas o cliente e a lista.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do cliente/aparelho") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it.trim() },
                    label = { Text("URL M3U") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                playlistUsername(playlist)?.let { Text("Usuário detectado: $it", color = Cyan, fontSize = 11.sp) }
                error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || playlist.isBlank()) {
                        error = "Preencha nome e lista."
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.claim(token, device.pairingCode, name.trim(), playlist.trim()) }
                            onSaved()
                        } catch (e: Throwable) {
                            error = (e.message ?: "Falha ao ativar").take(100)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "Ativando..." else "Ativar") }
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
    var name by remember { mutableStateOf(device.label.orEmpty()) }
    var playlist by remember { mutableStateOf(device.playlistUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runAction(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                onChanged()
            } catch (e: Throwable) {
                error = (e.message ?: "Falha na operação").take(100)
            } finally {
                busy = false
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
                playlistUsername(playlist)?.let { Text("Usuário: $it", color = Muted, fontSize = 11.sp) }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it.trim() },
                    label = { Text("URL M3U") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            runAction {
                                withContext(Dispatchers.IO) {
                                    repo.update(token, device.deviceId, !device.enabled, playlist, name)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (device.enabled) "Bloquear" else "Liberar") }

                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                        modifier = Modifier.weight(1f)
                    ) { Text("Excluir") }
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
                                }) { Text("Confirmar exclusão", color = Danger) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runAction {
                        withContext(Dispatchers.IO) {
                            repo.update(token, device.deviceId, device.enabled, playlist, name)
                        }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "Salvando..." else "Salvar") }
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
