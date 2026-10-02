package com.viraplay.admin

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Panel2 = Color(0xFF0A2040)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF9A35FF)
private val Green = Color(0xFF4BE38A)
private val Danger = Color(0xFFFF5874)
private val Muted = Color(0xFF94A0B8)

private enum class Tab(val label: String) { HOME("Painel"), PENDING("Pendentes"), CLIENTS("Clientes") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminApp() }
    }
}

@Composable
private fun AdminApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("viraplay_admin", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("admin_token", "") ?: "") }
    var logged by remember { mutableStateOf(token.isNotBlank()) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Purple, background = Bg, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (logged) {
                Dashboard(token) {
                    prefs.edit().remove("admin_token").apply()
                    logged = false
                }
            } else {
                Login(token, onToken = { token = it }) {
                    if (token.isNotBlank()) {
                        prefs.edit().putString("admin_token", token).apply()
                        logged = true
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Image(
        painter = painterResource(R.drawable.viraplay_wordmark),
        contentDescription = "ViraPlay",
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(190.dp).height(62.dp)
    )
}

@Composable
private fun Login(token: String, onToken: (String) -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Brand()
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Painel administrativo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Gerencie aparelhos e clientes ViraPlay.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = token,
                    onValueChange = onToken,
                    label = { Text("Chave do administrador") },
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
    val repo = remember { AdminRepository() }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<AdminDevice>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Carregando...") }
    var activating by remember { mutableStateOf<AdminDevice?>(null) }
    var editing by remember { mutableStateOf<AdminDevice?>(null) }

    fun reload() {
        scope.launch {
            status = "Atualizando..."
            try {
                devices = withContext(Dispatchers.IO) { repo.list(token) }
                status = "${devices.size} aparelho(s)"
            } catch (e: Exception) {
                status = "Falha: ${(e.message ?: "sem conexão").take(80)}"
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val pending = devices.filter { it.playlistUrl.isNullOrBlank() }
    val clients = devices.filter { !it.playlistUrl.isNullOrBlank() }
    val active = clients.count { it.enabled }
    val blocked = clients.size - active
    val visibleClients = clients.filter {
        search.isBlank() || (it.label ?: "").contains(search, true) || it.pairingCode.contains(search, true)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(when (item) { Tab.HOME -> "⌂"; Tab.PENDING -> "＋"; Tab.CLIENTS -> "◎" }, fontSize = 20.sp) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(status, color = Muted, fontSize = 10.sp)
                    Row {
                        TextButton(onClick = { reload() }) { Text("Atualizar") }
                        TextButton(onClick = onLogout) { Text("Sair") }
                    }
                }
            }

            when (tab) {
                Tab.HOME -> HomeTab(clients.size, active, blocked, pending, onActivate = { activating = it }, onPending = { tab = Tab.PENDING }, onClients = { tab = Tab.CLIENTS })
                Tab.PENDING -> PendingTab(pending, onActivate = { activating = it })
                Tab.CLIENTS -> ClientsTab(visibleClients, search, onSearch = { search = it }, onOpen = { editing = it })
            }
        }
    }

    activating?.let { device ->
        ActivateDialog(device, token, repo, onDismiss = { activating = null }) {
            activating = null
            tab = Tab.CLIENTS
            reload()
        }
    }

    editing?.let { device ->
        EditDialog(device, token, repo, onDismiss = { editing = null }) {
            editing = null
            reload()
        }
    }
}

@Composable
private fun HomeTab(total: Int, active: Int, blocked: Int, pending: List<AdminDevice>, onActivate: (AdminDevice) -> Unit, onPending: () -> Unit, onClients: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Visão geral", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Clientes", total, Cyan, Modifier.weight(1f))
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
            items(pending.take(5), key = { it.deviceId }) { device -> PendingCard(device) { onActivate(device) } }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PendingTab(items: List<AdminDevice>, onActivate: (AdminDevice) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Novos dispositivos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("O cliente instala o ViraPlay e aparece aqui automaticamente. Você não digita o código novamente.", color = Muted, fontSize = 13.sp)
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
                Text(device.pairingCode, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
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
private fun ClientsTab(items: List<AdminDevice>, search: String, onSearch: (String) -> Unit, onOpen: (AdminDevice) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Clientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("Buscar cliente ou código") },
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
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.label?.takeIf { it.isNotBlank() } ?: "Sem nome", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Código ${device.pairingCode} • ${device.platform ?: "Android"}", color = Muted, fontSize = 11.sp)
                Text("Lista configurada", color = Cyan, fontSize = 11.sp)
            }
            Surface(color = if (device.enabled) Green.copy(alpha = 0.16f) else Danger.copy(alpha = 0.16f), shape = RoundedCornerShape(20.dp)) {
                Text(if (device.enabled) "ATIVO" else "BLOQUEADO", color = if (device.enabled) Green else Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ActivateDialog(device: AdminDevice, token: String, repo: AdminRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
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
                Text("O código já veio do aparelho. Preencha apenas nome e lista.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do cliente/aparelho") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                OutlinedTextField(value = playlist, onValueChange = { playlist = it.trim() }, label = { Text("URL M3U") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                error?.let { Text(it, color = Danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || playlist.isBlank()) { error = "Preencha nome e lista."; return@Button }
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.claim(token, device.pairingCode, name.trim(), playlist.trim()) }
                            onSaved()
                        } catch (e: Exception) {
                            error = e.message?.take(90) ?: "Falha ao ativar"
                        } finally { busy = false }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "Ativando..." else "Ativar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EditDialog(device: AdminDevice, token: String, repo: AdminRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(device.label.orEmpty()) }
    var playlist by remember { mutableStateOf(device.playlistUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name.ifBlank { "Cliente" }) },
        text = {
            Column {
                Text("Código ${device.pairingCode}", color = Cyan, fontWeight = FontWeight.Bold)
                Text(device.platform ?: "Android", color = Muted, fontSize = 11.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                OutlinedTextField(value = playlist, onValueChange = { playlist = it.trim() }, label = { Text("URL M3U") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repo.update(token, device.deviceId, !device.enabled, playlist, name) }
                                    onChanged()
                                } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (device.enabled) "Bloquear" else "Liberar") }
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger), modifier = Modifier.weight(1f)) { Text("Excluir") }
                }
                if (confirmDelete) {
                    Card(colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Excluir este aparelho?", color = Color.White, fontWeight = FontWeight.Bold)
                            Row {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
                                TextButton(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { repo.delete(token, device.deviceId) }
                                                onChanged()
                                            } finally { busy = false }
                                        }
                                    }
                                ) { Text("Confirmar", color = Danger) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.update(token, device.deviceId, device.enabled, playlist, name) }
                            onChanged()
                        } finally { busy = false }
                    }
                },
                enabled = !busy
            ) { Text("Salvar") }
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
