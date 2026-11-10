package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Contas Stremio e Nuvio. Entra, importa addons, Minha Lista, progresso e chaves de debrid. */
@Composable
fun AccountsScreen(onBack: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    val context = LocalContext.current
    // Só abre link web. O endereço vem da resposta do serviço e não deve abrir outro tipo de app.
    fun open(url: String) = runCatching {
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return@runCatching
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader("Contas", Modifier.weight(1f), sub = "Stremio e Nuvio no perfil ${st.profileName}")
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Suas contas", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                com.aotv.app.ui.common.SummaryRow("Stremio", st.stremio?.email?.ifBlank { "Conectado" } ?: "Fora", if (st.stremio != null) Ok else TextPrimary)
                com.aotv.app.ui.common.SummaryRow("Nuvio", st.nuvio?.email?.ifBlank { "Conectado" } ?: "Fora", if (st.nuvio != null) Ok else TextPrimary)
                com.aotv.app.ui.common.SummaryRow("Importa para", st.profileName)
                Spacer(Modifier.height(12.dp))
                Text(
                    "A importação só soma. Nada do que já está no AOTV é apagado, e o progresso daqui vale quando é mais novo.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                st.message?.let { Banner(it, ok = true, onClose = viewModel::clearMessage) }
                st.error?.let { Banner(it, ok = false, onClose = viewModel::clearMessage) }
                st.progress?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(it, color = TextSecondary, fontSize = 14.sp)
                    }
                }

                com.aotv.app.ui.common.SettingsSection("Stremio")
                AccountCard(
                    badge = "ST", title = "Conta Stremio",
                    connected = st.stremio?.let { "Conectado como ${it.email.ifBlank { "conta Stremio" }}" },
                    busy = st.busy,
                    prefix = "stremio",
                    hint = "Traz os addons da conta, a biblioteca e o que você estava assistindo.",
                    onLogin = { e, p, _ -> viewModel.stremioLogin(e, p) },
                    onCode = { viewModel.stremioCode() },
                    showServer = false,
                ) {
                    ActionRow {
                        AotvButton("Importar addons", viewModel::stremioImportAddons, icon = Icons.Default.Extension, tone = ButtonTone.PRIMARY,
                            busy = st.busy == "stremio_addons", busyLabel = "Importando…", enabled = st.busy == null || st.busy == "stremio_addons", compact = true)
                        AotvButton("Importar lista e progresso", viewModel::stremioImportLibrary, icon = Icons.Default.CloudDownload,
                            busy = st.busy == "stremio_library", busyLabel = "Importando…", enabled = st.busy == null || st.busy == "stremio_library", compact = true)
                        AotvButton("Enviar meus addons", viewModel::stremioPushAddons, icon = Icons.Default.CloudUpload,
                            busy = st.busy == "stremio_push", busyLabel = "Enviando…", enabled = st.busy == null || st.busy == "stremio_push", compact = true)
                        AotvButton("Sair", viewModel::stremioLogout, icon = Icons.AutoMirrored.Filled.Logout, enabled = st.busy == null, compact = true)
                    }
                }

                com.aotv.app.ui.common.SettingsSection("Nuvio")
                AccountCard(
                    badge = "NV", title = "Conta Nuvio",
                    connected = st.nuvio?.let { "Conectado como ${it.email.ifBlank { "conta Nuvio" }}" },
                    busy = st.busy,
                    prefix = "nuvio",
                    hint = "Traz addons, Minha Lista, progresso e as chaves de debrid salvas na conta.",
                    onLogin = { e, p, s -> viewModel.nuvioLogin(e, p, s) },
                    onCode = { s -> viewModel.nuvioCode(s) },
                    showServer = true,
                ) {
                    if (st.nuvioProfiles.size > 1) {
                        Text("Perfil do Nuvio", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            st.nuvioProfiles.forEach { p ->
                                com.aotv.app.ui.common.FilterChipPill(p.name, st.nuvioProfile == p.index, { viewModel.pickNuvioProfile(p.index) })
                            }
                        }
                    }
                    ActionRow {
                        AotvButton("Importar addons", viewModel::nuvioImportAddons, icon = Icons.Default.Extension, tone = ButtonTone.PRIMARY,
                            busy = st.busy == "nuvio_addons", busyLabel = "Importando…", enabled = st.busy == null || st.busy == "nuvio_addons", compact = true)
                        AotvButton("Importar lista e progresso", viewModel::nuvioImportLibrary, icon = Icons.Default.CloudDownload,
                            busy = st.busy == "nuvio_library", busyLabel = "Importando…", enabled = st.busy == null || st.busy == "nuvio_library", compact = true)
                        AotvButton("Importar debrid", viewModel::nuvioImportDebrid, icon = Icons.Default.Key,
                            busy = st.busy == "nuvio_debrid", busyLabel = "Conferindo…", enabled = st.busy == null || st.busy == "nuvio_debrid", compact = true)
                        AotvButton("Importar coleções", viewModel::nuvioImportCollections, icon = Icons.Default.ViewModule,
                            busy = st.busy == "nuvio_collections", busyLabel = "Importando…", enabled = st.busy == null || st.busy == "nuvio_collections", compact = true)
                        AotvButton("Sair", viewModel::nuvioLogout, icon = Icons.AutoMirrored.Filled.Logout, enabled = st.busy == null, compact = true)
                    }
                }
                below?.let { Spacer(Modifier.height(8.dp)); it() }
            }
        }
    }

    val link = st.stremioLink
    val nv = st.nuvioCode
    if (link != null || nv != null) {
        val code = link?.code ?: nv!!.userCode
        val url = link?.url ?: nv!!.url
        val service = if (link != null) "Stremio" else "Nuvio"
        AlertDialog(
            onDismissRequest = viewModel::cancelCode,
            containerColor = BackgroundCard,
            title = { Text("Entrar no $service", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Aponte a câmera para o código ou abra o endereço com a conta já aberta", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    QrCode(url, Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp), "Código QR para entrar no $service")
                    Text(code, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
                    Text(url.removePrefix("https://"), color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("Aguardando a confirmação…", color = TextTertiary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open(url) }) { Text("Abrir no navegador", color = Accent, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelCode) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

/** Cartão de uma conta. Fora dela mostra o login. Conectado mostra as ações em [connectedContent]. */
@Composable
internal fun AccountCard(
    badge: String,
    title: String,
    connected: String?,
    busy: String?,
    prefix: String,
    hint: String,
    onLogin: (String, String, String) -> Unit,
    onCode: (String) -> Unit,
    showServer: Boolean,
    connectedContent: @Composable ColumnScope.() -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var serverOpen by remember { mutableStateOf(false) }
    val television = com.aotv.app.ui.common.LocalIsTv.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val loginFocus = remember { FocusRequester() }
    val nextField = if (television) KeyboardActions(onNext = {
        keyboard?.hide()
        focusManager.moveFocus(FocusDirection.Down)
    }) else KeyboardActions.Default
    val finishField = if (television) KeyboardActions(onDone = {
        keyboard?.hide()
        runCatching { loginFocus.requestFocus() }
    }) else KeyboardActions.Default
    val loggingIn = busy == "${prefix}_login"
    val coding = busy == "${prefix}_code"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .then(if (connected != null) Modifier.border(1.dp, Accent.copy(alpha = .35f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (connected != null) AccentSoft else Surface2),
                contentAlignment = Alignment.Center,
            ) { Text(badge, color = if (connected != null) Accent else TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(connected ?: hint, color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (connected != null) Icon(Icons.Default.CheckCircle, "Conectado", tint = Accent, modifier = Modifier.size(22.dp))
        }
        if (connected != null) {
            connectedContent()
            return@Column
        }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
        )
        com.aotv.app.ui.common.TvTextFieldGate(Modifier.fillMaxWidth(), label = "E-mail") { gate -> OutlinedTextField(
            value = email, onValueChange = { email = it.trim() }, singleLine = true, label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = nextField,
            modifier = gate, colors = fieldColors,
        ) }
        com.aotv.app.ui.common.TvTextFieldGate(Modifier.fillMaxWidth(), label = "Senha") { gate -> OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true, label = { Text("Senha") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (showServer && serverOpen) ImeAction.Next else ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = if (showServer && serverOpen) nextField else finishField,
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPass) "Esconder senha" else "Mostrar senha", tint = TextTertiary)
                }
            },
            modifier = gate, colors = fieldColors,
        ) }
        if (showServer) {
            if (serverOpen) com.aotv.app.ui.common.TvTextFieldGate(Modifier.fillMaxWidth(), label = "Servidor próprio") { gate -> OutlinedTextField(
                value = server, onValueChange = { server = it.trim() }, singleLine = true,
                label = { Text("Servidor próprio (vazio usa o oficial)") }, placeholder = { Text("api.nuvio.tv") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = finishField,
                modifier = gate, colors = fieldColors,
            ) } else TextButton(onClick = { serverOpen = true }) { Text("Usar servidor próprio", color = TextSecondary) }
        }
        ActionRow {
            AotvButton(
                "Entrar", { onLogin(email, pass, server) }, tone = ButtonTone.PRIMARY,
                modifier = Modifier.focusRequester(loginFocus),
                busy = loggingIn, busyLabel = "Entrando…", enabled = (busy == null || loggingIn) && email.contains('@') && pass.isNotBlank(), compact = true,
            )
            AotvButton(
                "Entrar com código", { onCode(server) }, icon = Icons.Default.QrCode2,
                busy = coding, busyLabel = "Gerando código…", enabled = busy == null || coding, compact = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun Banner(text: String, ok: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background((if (ok) Ok else DangerText).copy(alpha = .12f)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (ok) Ok else DangerText, modifier = Modifier.size(18.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = onClose) { Text("Ok", color = TextSecondary) }
    }
}
