package com.viraplay.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.viraplay.shared.SupportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private fun createSupportQrBitmap(content: String, size: Int = 360): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1)
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) {
            for (x in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

@Composable
fun ActivationScreen(
    code: String,
    status: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSupport: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(VpBg, VpPanel.copy(alpha = 0.78f), VpBg))
        ).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandWordmark(large = true)
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = VpPanel),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ative seu dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Envie este código ao suporte VPlayo.", color = VpMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Surface(color = VpPanelAlt, shape = RoundedCornerShape(18.dp)) {
                        Text(
                            code,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            letterSpacing = 4.sp,
                            modifier = Modifier.padding(horizontal = 30.dp, vertical = 18.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VpCyan)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(status, color = if (status.startsWith("Falha")) VpDanger else VpMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.weight(1f)) {
                            Text(if (loading) "Carregando..." else "Atualizar")
                        }
                        OutlinedButton(onClick = onSupport, modifier = Modifier.weight(1f)) { Text("Suporte") }
                    }
                }
            }
        }
    }
}


private enum class AccessChoice(val label: String, val icon: ImageVector) {
    PROVIDER("Provedor", Icons.Filled.Storage),
    DNS("DNS", Icons.Filled.Language),
    M3U("M3U", Icons.Filled.PlaylistPlay),
    DEVICE("Código", Icons.Filled.QrCode2)
}

@Composable
fun AccessPortalScreen(
    code: String,
    status: String,
    loading: Boolean,
    isTv: Boolean,
    onRefresh: () -> Unit,
    onSupport: () -> Unit,
    onProviderLogin: suspend (String, String, String) -> Result<String>,
    onDnsLogin: suspend (String, String, String) -> Result<String>,
    onM3uLogin: suspend (String) -> Result<String>,
    onUseDeviceCode: suspend () -> Unit,
    onClose: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(AccessChoice.PROVIDER) }
    var providerCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showProviderPassword by remember { mutableStateOf(false) }
    var dns by remember { mutableStateOf("") }
    var dnsUsername by remember { mutableStateOf("") }
    var dnsPassword by remember { mutableStateOf("") }
    var showDnsPassword by remember { mutableStateOf(false) }
    var m3u by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0B2B4B), Color(0xFF07152B), VpBg)
                )
            )
            .padding(if (isTv) 34.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xF2071831)),
            border = BorderStroke(1.dp, VpCyan.copy(alpha = .22f)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.widthIn(max = if (isTv) 760.dp else 620.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(if (isTv) 30.dp else 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandWordmark(large = true)
                    Spacer(Modifier.weight(1f))
                    onClose?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = VpCyan)
                        }
                    }
                }
                Text("Escolha como entrar", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (isTv) 28.sp else 24.sp)
                Text("VPlayo não inclui conteúdo. Use os dados fornecidos pelo seu serviço.", color = VpMuted, fontSize = 11.sp)

                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccessChoice.entries.forEach { item ->
                        val selected = choice == item
                        Surface(
                            onClick = {
                                choice = item
                                error = null
                                message = null
                            },
                            color = if (selected) VpCyan.copy(alpha = .18f) else VpPanelAlt.copy(alpha = .70f),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) VpCyan else VpBorder
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) VpCyan else Color.White.copy(alpha = .72f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.label,
                                    color = if (selected) Color.White else VpMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                when (choice) {
                    AccessChoice.PROVIDER -> {
                        Text("Acesso por provedor", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text("Informe o código do provedor e os dados da sua conta.", color = VpMuted, fontSize = 11.sp)
                        OutlinedTextField(
                            value = providerCode,
                            onValueChange = { providerCode = it.uppercase().filter(Char::isLetterOrDigit).take(10) },
                            label = { Text("Código do provedor") },
                            leadingIcon = { Icon(Icons.Filled.Dns, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Usuário") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Senha") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            visualTransformation = if (showProviderPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showProviderPassword = !showProviderPassword }) {
                                    Icon(
                                        if (showProviderPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showProviderPassword) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Button(
                            enabled = !busy && providerCode.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                            onClick = {
                                busy = true; error = null; message = "Validando acesso..."
                                scope.launch {
                                    val result = onProviderLogin(providerCode, username, password)
                                    result.onSuccess { message = "Conectado a $it" }
                                        .onFailure { error = friendlyAccessError(it); message = null }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(if (busy) "Conectando..." else "Entrar") }
                    }

                    AccessChoice.DNS -> {
                        Text("Acesso direto por DNS", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text("Para quem recebeu DNS, usuário e senha do próprio serviço.", color = VpMuted, fontSize = 11.sp)
                        OutlinedTextField(
                            value = dns,
                            onValueChange = { dns = it },
                            label = { Text("DNS / servidor") },
                            leadingIcon = { Icon(Icons.Filled.Language, null) },
                            placeholder = { Text("http://servidor.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                        OutlinedTextField(
                            value = dnsUsername,
                            onValueChange = { dnsUsername = it },
                            label = { Text("Usuário") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = dnsPassword,
                            onValueChange = { dnsPassword = it },
                            label = { Text("Senha") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            visualTransformation = if (showDnsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showDnsPassword = !showDnsPassword }) {
                                    Icon(
                                        if (showDnsPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showDnsPassword) "Ocultar senha" else "Mostrar senha"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Button(
                            enabled = !busy && dns.isNotBlank() && dnsUsername.isNotBlank() && dnsPassword.isNotBlank(),
                            onClick = {
                                busy = true; error = null; message = "Validando servidor..."
                                scope.launch {
                                    val result = onDnsLogin(dns, dnsUsername, dnsPassword)
                                    result.onSuccess { message = it }
                                        .onFailure { error = friendlyAccessError(it); message = null }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(if (busy) "Conectando..." else "Entrar com DNS") }
                    }

                    AccessChoice.DEVICE -> {
                        Text("Ativação por código", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text("Para ativações individuais pelo VPlayo ADM.", color = VpMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(14.dp))
                        Surface(color = VpPanelAlt, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                code,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = if (isTv) 38.sp else 32.sp,
                                letterSpacing = 4.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                            )
                        }
                        Text(status, color = VpMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Button(
                                enabled = !busy && !loading,
                                onClick = {
                                    busy = true
                                    scope.launch { onUseDeviceCode(); busy = false }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Usar código") }
                            OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Atualizar") }
                        }
                    }

                    AccessChoice.M3U -> {
                        Text("Lista própria", color = VpCyan, fontWeight = FontWeight.Bold)
                        Text("Cole sua URL M3U ou Xtream completa.", color = VpMuted, fontSize = 11.sp)
                        OutlinedTextField(
                            value = m3u,
                            onValueChange = { m3u = it },
                            label = { Text("URL M3U / Xtream") },
                            leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                        Button(
                            enabled = !busy && m3u.trim().startsWith("http", true),
                            onClick = {
                                busy = true; error = null; message = "Preparando lista..."
                                scope.launch {
                                    val result = onM3uLogin(m3u)
                                    result.onSuccess { message = it }
                                        .onFailure { error = friendlyAccessError(it); message = null }
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) { Text(if (busy) "Validando..." else "Adicionar lista") }
                    }
                }

                if (loading || busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = VpCyan)
                }
                message?.let { Text(it, color = VpGreen, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
                error?.let { Text(it, color = VpDanger, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
                TextButton(onClick = onSupport, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Filled.HeadsetMic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Suporte VPlayo")
                }
            }
        }
    }
}

private fun friendlyAccessError(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("404") -> "Código do provedor não encontrado."
        raw.contains("409") -> "O provedor ainda não configurou o DNS."
        raw.contains("401") || raw.contains("403") -> "Usuário ou senha recusados pelo servidor."
        raw.isBlank() -> "Não foi possível conectar. Verifique os dados e tente novamente."
        else -> raw.take(160)
    }
}

@Composable
fun BlockedScreen(code: String, onSupport: () -> Unit, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(VpBg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandWordmark(large = true)
        Spacer(Modifier.height(20.dp))
        Text("Acesso temporariamente bloqueado", color = VpDanger, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text("Código do aparelho: $code", color = VpMuted)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSupport) { Text("Falar com suporte") }
            OutlinedButton(onClick = onRefresh) { Text("Tentar novamente") }
        }
    }
}

@Composable
fun SupportScreen(code: String, isTv: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val qrBitmap = remember(code) { createSupportQrBitmap(SupportConfig.whatsappUrl(code)) }

    Surface(color = VpBg, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF0B2B4B), VpBg)))
                .padding(if (isTv) 38.dp else 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandWordmark(large = true)
                Spacer(Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF071A34)),
                    border = BorderStroke(1.dp, VpCyan.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.widthIn(max = if (isTv) 820.dp else 620.dp).fillMaxWidth()
                ) {
                    if (isTv) {
                        Row(
                            Modifier.fillMaxWidth().padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(26.dp)
                        ) {
                            qrBitmap?.let { bmp ->
                                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "QR Code do suporte",
                                        modifier = Modifier.size(210.dp).padding(10.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("Suporte VPlayo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Aponte a câmera do celular para o QR Code", color = VpCyan, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(14.dp))
                                Text("WhatsApp", color = VpMuted, fontSize = 12.sp)
                                Text(SupportConfig.WHATSAPP_DISPLAY, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text("Código do aparelho", color = VpMuted, fontSize = 12.sp)
                                Surface(color = VpPanelAlt, shape = RoundedCornerShape(12.dp)) {
                                    Text(code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                                }
                                Spacer(Modifier.height(18.dp))
                                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
                            }
                        }
                    } else {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Suporte VPlayo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                            Spacer(Modifier.height(12.dp))
                            qrBitmap?.let { bmp ->
                                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR Code do suporte", modifier = Modifier.size(190.dp).padding(10.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(SupportConfig.WHATSAPP_DISPLAY, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Código: $code", color = VpMuted)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { Support.openWhatsApp(context, code) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir WhatsApp") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    code: String,
    status: String,
    accessText: String?,
    sourceText: String?,
    isTv: Boolean,
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onRefresh: () -> Unit,
    onChangeAccess: () -> Unit,
    onParentalUnlocked: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember { PlaybackPreferences(context) }
    var groupChannels by remember { mutableStateOf(prefs.groupChannels) }
    var autoQuality by remember { mutableStateOf(prefs.autoQuality) }
    var manualQuality by remember { mutableStateOf(prefs.manualQuality) }
    var parentalEnabled by remember { mutableStateOf(prefs.parentalEnabled) }
    var pinDialog by remember { mutableStateOf(false) }
    var unlockDialog by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val updateManager = remember { UpdateManager(context) }
    val updateScope = rememberCoroutineScope()
    var manualUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var installingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember {
        mutableStateOf("Toque em Verificar agora para procurar uma nova versão.")
    }
    var updateError by remember { mutableStateOf<String?>(null) }

    fun checkUpdateNow() {
        if (checkingUpdate || installingUpdate) return
        checkingUpdate = true
        updateError = null
        updateScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { updateManager.check() }
            }
            result.onSuccess { found ->
                manualUpdate = found
                updateStatus = if (found == null) {
                    "Você já está na versão mais recente."
                } else {
                    "Nova versão ${found.versionName} disponível."
                }
            }.onFailure { e ->
                updateError = (e.message ?: "Não foi possível verificar atualizações.").take(120)
                updateStatus = "Falha ao verificar atualização."
            }
            checkingUpdate = false
        }
    }

    fun installManualUpdate(info: AppUpdateInfo) {
        if (installingUpdate) return
        installingUpdate = true
        updateError = null
        updateStatus = "Baixando atualização..."
        updateScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { updateManager.download(info) }
            }
            result.onSuccess { apk ->
                installingUpdate = false
                when (updateManager.launchInstaller(apk)) {
                    InstallLaunchResult.STARTED -> {
                        updateStatus = "Instalador aberto. Conclua a atualização do VPlayo."
                    }
                    InstallLaunchResult.NEED_PERMISSION -> {
                        updateStatus = "Autorize a instalação de apps desta fonte e volte para tentar novamente."
                    }
                }
            }.onFailure { e ->
                installingUpdate = false
                updateError = (e.message ?: "Não foi possível baixar a atualização.").take(120)
                updateStatus = "Falha ao baixar atualização."
            }
        }
    }

    Surface(color = VpBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(if (isTv) 36.dp else 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandWordmark()
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onBack) { Text("Voltar") }
            }

            Text("Configurações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Informações e preferências do seu VPlayo", color = VpMuted, fontSize = 12.sp)

            SettingsCard("Status", status)
            SettingsCard("Código do aparelho", code)
            accessText?.let { SettingsCard("Vencimento", it) }
            SettingsCard("Versão", "VPlayo ${BuildConfig.VERSION_NAME}")
            sourceText?.let { SettingsCard("Forma de acesso", it) }
            OutlinedButton(
                onClick = onChangeAccess,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Trocar conta / forma de acesso") }

            Card(
                colors = CardDefaults.cardColors(containerColor = VpPanel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Atualizações", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Versão instalada: ${BuildConfig.VERSION_NAME}",
                        color = VpMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        updateStatus,
                        color = if (manualUpdate != null) VpCyan else VpMuted,
                        fontSize = 12.sp
                    )
                    updateError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = VpDanger, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { checkUpdateNow() },
                        enabled = !checkingUpdate && !installingUpdate,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(if (checkingUpdate) "Verificando..." else "Verificar agora")
                    }

                    manualUpdate?.let { info ->
                        OutlinedButton(
                            onClick = { installManualUpdate(info) },
                            enabled = !installingUpdate && !checkingUpdate,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(if (installingUpdate) "Baixando..." else "Baixar e instalar ${info.versionName}")
                        }
                    }
                }
            }

            ToggleCard(
                title = "Agrupar canais",
                subtitle = "Junta SD, HD, FHD e 4K do mesmo canal em uma única opção.",
                checked = groupChannels,
                onChecked = {
                    groupChannels = it
                    prefs.groupChannels = it
                }
            )

            ToggleCard(
                title = "Qualidade automática",
                subtitle = "Ativada por padrão. O app prioriza a melhor qualidade e reduz se a conexão não acompanhar.",
                checked = autoQuality,
                onChecked = {
                    autoQuality = it
                    prefs.autoQuality = it
                }
            )

            if (!autoQuality) {
                Card(colors = CardDefaults.cardColors(containerColor = VpPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Qualidade preferida", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("4K", "FHD", "HD", "SD").forEach { q ->
                                FilterChip(
                                    selected = manualQuality == q,
                                    onClick = {
                                        manualQuality = q
                                        prefs.manualQuality = q
                                    },
                                    label = { Text(q) }
                                )
                            }
                        }
                    }
                }
            }

            ToggleCard(
                title = "Controle parental",
                subtitle = "Protege categorias e canais adultos com um PIN de 4 números.",
                checked = parentalEnabled,
                onChecked = {
                    parentalEnabled = it
                    prefs.parentalEnabled = it
                }
            )

            OutlinedButton(onClick = {
                currentPin = ""
                newPin = ""
                pinError = null
                pinDialog = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (prefs.hasPin()) "Alterar PIN parental" else "Criar PIN parental")
            }

            if (parentalEnabled && prefs.hasPin()) {
                OutlinedButton(onClick = {
                    currentPin = ""
                    pinError = null
                    unlockDialog = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desbloquear conteúdo adulto nesta sessão")
                }
            }

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar catálogo") }
            OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Suporte VPlayo") }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (pinDialog) {
        val changing = prefs.hasPin()
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text(if (changing) "Alterar PIN" else "Criar PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (changing) {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPin = it },
                            label = { Text("PIN atual") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Novo PIN (4 números)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    pinError?.let { Text(it, color = VpDanger) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (changing && !prefs.verifyPin(currentPin)) {
                        pinError = "PIN atual incorreto."
                    } else if (!prefs.setPin(newPin)) {
                        pinError = "Digite exatamente 4 números."
                    } else {
                        onParentalUnlocked()
                        pinDialog = false
                    }
                }) { Text("Salvar PIN") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancelar") } }
        )
    }
    if (unlockDialog) {
        AlertDialog(
            onDismissRequest = { unlockDialog = false },
            title = { Text("Desbloquear conteúdo adulto") },
            text = {
                Column {
                    Text("Digite seu PIN de 4 números. O desbloqueio vale até fechar o aplicativo.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPin = it },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    pinError?.let { Text(it, color = VpDanger) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (prefs.verifyPin(currentPin)) {
                        onParentalUnlocked()
                        unlockDialog = false
                    } else pinError = "PIN incorreto."
                }) { Text("Desbloquear") }
            },
            dismissButton = { TextButton(onClick = { unlockDialog = false }) { Text("Cancelar") } }
        )
    }

}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VpPanel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = VpMuted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun SettingsCard(title: String, value: String) {
    var focused by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (focused) VpCyan.copy(alpha = 0.16f) else VpPanel
        ),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else VpBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = if (focused) VpCyan else VpMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
