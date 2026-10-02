package com.viraplay.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viraplay.shared.SupportConfig

@Composable
fun ActivationScreen(
    code: String,
    status: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSupport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpBg)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandWordmark(large = true)
        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = VpPanel),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ative seu dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text("Envie este código ao suporte ViraPlay.", color = VpMuted, fontSize = 14.sp)
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
                    OutlinedButton(onClick = onSupport, modifier = Modifier.weight(1f)) {
                        Text("Suporte")
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedScreen(
    code: String,
    onSupport: () -> Unit,
    onRefresh: () -> Unit
) {
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
fun SupportScreen(
    code: String,
    isTv: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Surface(color = VpBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(if (isTv) 42.dp else 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandWordmark(large = true)
            Spacer(Modifier.height(22.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = VpPanel),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Suporte ViraPlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("WhatsApp", color = VpCyan, fontWeight = FontWeight.SemiBold)
                    Text(SupportConfig.WHATSAPP_DISPLAY, color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Código deste aparelho: $code", color = VpMuted)
                    Spacer(Modifier.height(18.dp))
                    if (isTv) {
                        Text(
                            "Abra o WhatsApp no celular e envie seu código para o número acima.",
                            color = VpMuted,
                            fontSize = 14.sp
                        )
                    } else {
                        Button(
                            onClick = { Support.openWhatsApp(context, code) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Abrir WhatsApp") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    code: String,
    db: CatalogDb,
    status: String,
    isTv: Boolean,
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onRefresh: () -> Unit
) {
    BackHandler(onBack = onBack)
    Surface(color = VpBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(if (isTv) 36.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandWordmark()
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onBack) { Text("Voltar") }
            }
            Text("Configurações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)

            SettingsCard("Status", status)
            SettingsCard("Código do aparelho", code)
            SettingsCard("Fonte", "${db.getMeta("source_kind") ?: "--"} • ${db.getMeta("source_name") ?: "--"}")
            SettingsCard("Versão", "ViraPlay 3.0.0")

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar catálogo") }
            OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Suporte ViraPlay") }
        }
    }
}

@Composable
private fun SettingsCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VpPanel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = VpMuted, fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
