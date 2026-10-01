package com.viraplay.admin

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.AdminDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF020711)
private val Panel = Color(0xFF07162C)
private val Cyan = Color(0xFF00C8FF)
private val Purple = Color(0xFF8B3DFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminApp() }
    }
}

@Composable
fun AdminApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("viraplay_admin", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("admin_token", "") ?: "") }
    var saved by remember { mutableStateOf(token.isNotBlank()) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Purple, background = Bg, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (!saved) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("ViraPlay ADM", color = Color.White, fontSize = 30.sp)
                    Text("Digite a chave privada configurada no Worker.", color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Chave do administrador") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { prefs.edit().putString("admin_token", token).apply(); saved = token.isNotBlank() }, modifier = Modifier.padding(top = 14.dp)) { Text("Entrar") }
                }
            } else {
                Dashboard(token) { saved = false; prefs.edit().remove("admin_token").apply() }
            }
        }
    }
}

@Composable
private fun Dashboard(token: String, onLogout: () -> Unit) {
    val repo = remember { AdminRepository() }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<AdminDevice>>(emptyList()) }
    var status by remember { mutableStateOf("Carregando...") }
    var code by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf("") }

    fun reload() = scope.launch {
        status = "Atualizando..."
        try {
            devices = withContext(Dispatchers.IO) { repo.list(token) }
            status = "${devices.size} dispositivo(s)"
        } catch (e: Exception) { status = "Falha ao conectar" }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Text("ViraPlay ADM", color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLogout) { Text("Sair") }
        }
        Text(status, color = Color.Gray)

        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Ativar novo dispositivo", color = Cyan)
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Código da TV/celular") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it }, label = { Text("Nome do cliente/aparelho") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(playlist, { playlist = it }, label = { Text("URL M3U") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.claim(token, code.trim(), label.trim(), playlist.trim()) }
                            code = ""; label = ""; playlist = ""; reload()
                        } catch (_: Exception) { status = "Erro ao ativar" }
                    }
                }, modifier = Modifier.padding(top = 10.dp)) { Text("Ativar") }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.deviceId }) { d -> DeviceCard(d, token, repo, ::reload) }
        }
    }
}

@Composable
private fun DeviceCard(d: AdminDevice, token: String, repo: AdminRepository, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var playlist by remember(d.deviceId) { mutableStateOf(d.playlistUrl.orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(d.label ?: "Sem nome", color = Color.White, fontSize = 18.sp)
            Text("Código: ${d.pairingCode} • ${d.platform ?: "Android"}", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(playlist, { playlist = it }, label = { Text("M3U") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { repo.update(token, d.deviceId, d.enabled, playlist) }; reload() }
                }) { Text("Salvar") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { repo.update(token, d.deviceId, !d.enabled, playlist) }; reload() }
                }) { Text(if (d.enabled) "Bloquear" else "Liberar") }
            }
        }
    }
}
