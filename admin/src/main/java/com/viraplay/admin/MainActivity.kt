package com.viraplay.admin

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
private val Purple = Color(0xFF8B3DFF)
private val Green = Color(0xFF4BE38A)
private val Danger = Color(0xFFFF5D73)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminApp() }
    }
}

@Composable
fun AdminApp() {
    val context =
        LocalContext.current

    val prefs =
        remember {
            context.getSharedPreferences(
                "viraplay_admin",
                Context.MODE_PRIVATE
            )
        }

    var token by remember {
        mutableStateOf(
            prefs.getString(
                "admin_token",
                ""
            ) ?: ""
        )
    }

    var saved by remember {
        mutableStateOf(
            token.isNotBlank()
        )
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
            if (!saved) {
                LoginScreen(
                    token = token,
                    onTokenChange = {
                        token = it
                    },
                    onLogin = {
                        prefs.edit()
                            .putString(
                                "admin_token",
                                token
                            )
                            .apply()

                        saved =
                            token.isNotBlank()
                    }
                )
            } else {
                Dashboard(
                    token = token,
                    onLogout = {
                        saved = false

                        prefs.edit()
                            .remove(
                                "admin_token"
                            )
                            .apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    token: String,
    onTokenChange: (String) -> Unit,
    onLogin: () -> Unit
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
                Modifier.size(110.dp),
            contentScale =
                ContentScale.Fit
        )

        Text(
            "ViraPlay ADM",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            "Painel de dispositivos",
            color = Cyan,
            fontSize = 13.sp
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
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    20.dp
                )
        ) {
            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {
                Text(
                    "Acesso administrativo",
                    color = Color.White,
                    fontWeight =
                        FontWeight.Medium
                )

                OutlinedTextField(
                    value = token,
                    onValueChange =
                        onTokenChange,
                    label = {
                        Text(
                            "Chave do administrador"
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 10.dp
                            ),
                    singleLine = true
                )

                Button(
                    onClick =
                        onLogin,
                    enabled =
                        token.isNotBlank(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 14.dp
                            )
                ) {
                    Text("Entrar")
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    token: String,
    onLogout: () -> Unit
) {
    val repo =
        remember {
            AdminRepository()
        }

    val scope =
        rememberCoroutineScope()

    var devices by remember {
        mutableStateOf<List<AdminDevice>>(
            emptyList()
        )
    }

    var status by remember {
        mutableStateOf(
            "Carregando..."
        )
    }

    var code by remember {
        mutableStateOf("")
    }

    var label by remember {
        mutableStateOf("")
    }

    var playlist by remember {
        mutableStateOf("")
    }

    var search by remember {
        mutableStateOf("")
    }

    var busy by remember {
        mutableStateOf(false)
    }

    fun reload() =
        scope.launch {
            status =
                "Atualizando..."

            try {
                devices =
                    withContext(
                        Dispatchers.IO
                    ) {
                        repo.list(token)
                    }

                status =
                    "${devices.size} dispositivo(s)"

            } catch (e: Exception) {
                status =
                    "Falha: " +
                        (e.message
                            ?: "sem conexão")
                            .take(70)
            }
        }

    LaunchedEffect(Unit) {
        reload()
    }

    val visible =
        remember(
            devices,
            search
        ) {
            if (search.isBlank()) {
                devices
            } else {
                devices.filter {
                    (it.label ?: "")
                        .contains(
                            search,
                            true
                        ) ||
                        it.pairingCode
                            .contains(
                                search,
                                true
                            ) ||
                        (it.platform ?: "")
                            .contains(
                                search,
                                true
                            )
                }
            }
        }

    val activeCount =
        devices.count {
            it.enabled
        }

    val blockedCount =
        devices.size -
            activeCount

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(14.dp)
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
                    Modifier.size(48.dp)
            )

            Spacer(
                Modifier.width(10.dp)
            )

            Column {
                Text(
                    "ViraPlay ADM",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    status,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.weight(1f)
            )

            TextButton(
                onClick = {
                    reload()
                }
            ) {
                Text("Atualizar")
            }

            TextButton(
                onClick =
                    onLogout
            ) {
                Text("Sair")
            }
        }

        Spacer(
            Modifier.height(10.dp)
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            StatCard(
                title = "Total",
                value =
                    devices.size
                        .toString(),
                color = Cyan,
                modifier =
                    Modifier.weight(1f)
            )

            StatCard(
                title = "Ativos",
                value =
                    activeCount
                        .toString(),
                color = Green,
                modifier =
                    Modifier.weight(1f)
            )

            StatCard(
                title = "Bloqueados",
                value =
                    blockedCount
                        .toString(),
                color = Danger,
                modifier =
                    Modifier.weight(1f)
            )
        }

        Spacer(
            Modifier.height(10.dp)
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Panel
                ),
            shape =
                RoundedCornerShape(
                    18.dp
                ),
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {
                Text(
                    "Ativar novo dispositivo",
                    color = Cyan,
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Medium
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code =
                            it.uppercase()
                                .filter(
                                    Char::isLetterOrDigit
                                )
                                .take(8)
                    },
                    label = {
                        Text(
                            "Código da TV/celular"
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 8.dp
                            ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                    },
                    label = {
                        Text(
                            "Nome do cliente/aparelho"
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 6.dp
                            ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = playlist,
                    onValueChange = {
                        playlist = it.trim()
                    },
                    label = {
                        Text("URL M3U")
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 6.dp
                            ),
                    minLines = 2
                )

                Button(
                    onClick = {
                        if (
                            code.isBlank() ||
                            label.isBlank() ||
                            playlist.isBlank()
                        ) {
                            status =
                                "Preencha código, nome e M3U"

                            return@Button
                        }

                        busy = true

                        scope.launch {
                            try {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    repo.claim(
                                        token,
                                        code.trim(),
                                        label.trim(),
                                        playlist.trim()
                                    )
                                }

                                code = ""
                                label = ""
                                playlist = ""
                                status =
                                    "Dispositivo ativado"

                                reload()

                            } catch (
                                e: Exception
                            ) {
                                status =
                                    "Erro ao ativar: " +
                                        (e.message
                                            ?: "")
                                            .take(60)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 12.dp
                            )
                ) {
                    Text(
                        if (busy) {
                            "Salvando..."
                        } else {
                            "Ativar dispositivo"
                        }
                    )
                }
            }
        }

        Spacer(
            Modifier.height(10.dp)
        )

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
            },
            label = {
                Text(
                    "Buscar dispositivo"
                )
            },
            modifier =
                Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(
            Modifier.height(10.dp)
        )

        LazyColumn(
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
            contentPadding =
                PaddingValues(
                    bottom = 28.dp
                )
        ) {
            items(
                visible,
                key = {
                    it.deviceId
                }
            ) { device ->
                DeviceCard(
                    device =
                        device,
                    token =
                        token,
                    repo =
                        repo,
                    onStatus = {
                        status = it
                    },
                    reload = {
                        reload()
                    }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier =
        Modifier
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Panel2
            ),
        modifier =
            modifier,
        shape =
            RoundedCornerShape(
                14.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {
            Text(
                value,
                color = color,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                title,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: AdminDevice,
    token: String,
    repo: AdminRepository,
    onStatus: (String) -> Unit,
    reload: () -> Unit
) {
    val scope =
        rememberCoroutineScope()

    var label by
        remember(
            device.deviceId
        ) {
            mutableStateOf(
                device.label.orEmpty()
            )
        }

    var playlist by
        remember(
            device.deviceId
        ) {
            mutableStateOf(
                device.playlistUrl
                    .orEmpty()
            )
        }

    var confirmDelete by
        remember {
            mutableStateOf(false)
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
            },
            title = {
                Text(
                    "Excluir aparelho?"
                )
            },
            text = {
                Text(
                    "Ele será removido do painel e bloqueado. " +
                        "Para voltar, será necessário ativá-lo novamente pelo código."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete =
                            false

                        busy = true

                        scope.launch {
                            try {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    repo.delete(
                                        token,
                                        device.deviceId
                                    )
                                }

                                onStatus(
                                    "Aparelho excluído"
                                )

                                reload()

                            } catch (
                                e: Exception
                            ) {
                                onStatus(
                                    "Erro ao excluir: " +
                                        (e.message
                                            ?: "")
                                            .take(60)
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(
                        "Excluir",
                        color = Danger
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDelete =
                            false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Panel
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        label.ifBlank {
                            "Sem nome"
                        },
                        color =
                            Color.White,
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Medium,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Text(
                        "Código ${device.pairingCode} • " +
                            (device.platform
                                ?: "Android"),
                        color =
                            Color.Gray,
                        fontSize =
                            11.sp
                    )
                }

                Surface(
                    color =
                        if (
                            device.enabled
                        ) {
                            Green.copy(
                                alpha =
                                    0.15f
                            )
                        } else {
                            Danger.copy(
                                alpha =
                                    0.15f
                            )
                        },
                    shape =
                        RoundedCornerShape(
                            30.dp
                        )
                ) {
                    Text(
                        if (
                            device.enabled
                        ) {
                            "ATIVO"
                        } else {
                            "BLOQUEADO"
                        },
                        color =
                            if (
                                device.enabled
                            ) {
                                Green
                            } else {
                                Danger
                            },
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    10.dp,
                                vertical =
                                    5.dp
                            ),
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                },
                label = {
                    Text(
                        "Cliente/aparelho"
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 10.dp
                        ),
                singleLine = true
            )

            OutlinedTextField(
                value = playlist,
                onValueChange = {
                    playlist = it.trim()
                },
                label = {
                    Text("URL M3U")
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 6.dp
                        ),
                minLines = 2
            )

            Row(
                modifier =
                    Modifier.padding(
                        top = 10.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                Button(
                    onClick = {
                        busy = true

                        scope.launch {
                            try {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    repo.update(
                                        token,
                                        device.deviceId,
                                        device.enabled,
                                        playlist,
                                        label
                                    )
                                }

                                onStatus(
                                    "Alterações salvas"
                                )

                                reload()

                            } catch (
                                e: Exception
                            ) {
                                onStatus(
                                    "Erro ao salvar: " +
                                        (e.message
                                            ?: "")
                                            .take(50)
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("Salvar")
                }

                OutlinedButton(
                    onClick = {
                        busy = true

                        scope.launch {
                            try {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    repo.update(
                                        token,
                                        device.deviceId,
                                        !device.enabled,
                                        playlist,
                                        label
                                    )
                                }

                                onStatus(
                                    if (
                                        device.enabled
                                    ) {
                                        "Aparelho bloqueado"
                                    } else {
                                        "Aparelho liberado"
                                    }
                                )

                                reload()

                            } catch (
                                e: Exception
                            ) {
                                onStatus(
                                    "Erro: " +
                                        (e.message
                                            ?: "")
                                            .take(50)
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        if (
                            device.enabled
                        ) {
                            "Bloquear"
                        } else {
                            "Liberar"
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    confirmDelete =
                        true
                },
                enabled = !busy,
                colors =
                    ButtonDefaults
                        .outlinedButtonColors(
                            contentColor =
                                Danger
                        ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 8.dp
                        )
            ) {
                Text(
                    "Excluir aparelho"
                )
            }
        }
    }
}
