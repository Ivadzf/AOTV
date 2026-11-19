package com.aotv.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.backup.AotvBackup
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.LocalIsTv
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.common.SettingsSection
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.AccentSoft
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Surface2
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela "Backup e restauração" (v1.13.0), no layout da foto do JOTV 1.0.19:
 * CONTEÚDO INCLUÍDO / GOOGLE DRIVE (Lote 10, v1.20.0) / WI-FI DIRETO (SEM
 * SERVIDOR) / ARQUIVO LOCAL (.JSON).
 *
 * Sem servidor externo: o backup sai em .json (Downloads), em texto colado ou
 * pelo servidor Wi-Fi temporário desta tela. O servidor vive SÓ enquanto a
 * tela está aberta — sair daqui desliga ele (DisposableEffect + onCleared).
 *
 * O sync do Drive usa o MESMO JSON do backup local (respeita o toggle
 * "Incluir chaves e sessões") e precisa do Client ID OAuth do projeto do
 * usuário no Google Cloud + da conta Google conectada; sem client ID a
 * função fica desligada e a tela explica o que falta.
 *
 * Ponto de entrada p/ a navegação: `BackupScreen(onBack)`. A rota e a entrada
 * no hub de Configurações são do coordenador.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pasteOpen by remember { mutableStateOf(false) }
    var wifiFetchOpen by remember { mutableStateOf(false) }
    var wifiSendOpen by remember { mutableStateOf(false) }
    var driveClientIdOpen by remember { mutableStateOf(false) }

    // O servidor desliga ao sair da tela (o ViewModel também para no onCleared).
    DisposableEffect(Unit) { onDispose { viewModel.stopServer() } }
    // Confere a sessão Google ao abrir (o e-mail guardado pode estar velho).
    LaunchedEffect(Unit) { viewModel.refreshDriveSession(context) }

    // Login Google p/ o Drive (o erro real vem do Google Sign-In).
    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data) }
            .onSuccess { task ->
                task.addOnSuccessListener { account -> viewModel.onDriveSignIn(account) }
                task.addOnFailureListener { e -> viewModel.onDriveSignInError(e.message ?: "Não foi possível conectar a conta Google.") }
            }
            .onFailure { viewModel.onDriveSignIn(null) }
    }

    // Posição da seção do Drive p/ a busca profunda rolar até aqui: os itens
    // antes dela são os condicionais (ocupado/mensagem/servidor) + a seção
    // "Conteúdo incluído" + a linha do toggle de segredos.
    val listState = rememberLazyListState()
    val driveSectionIndex = (if (state.busy != null) 1 else 0) +
        (if (state.message != null) 1 else 0) +
        (if (state.serverRunning) 1 else 0) + 2
    LaunchedEffect(highlightAnchor) {
        if (highlightAnchor == SettingsAnchors.BACKUP_DRIVE) listState.scrollToItem(driveSectionIndex)
    }
    val driveHighlighted = highlightAnchor == SettingsAnchors.BACKUP_DRIVE
    val driveStatus = when {
        state.driveClientId.isBlank() -> "Desligado: falta o Client ID do Google"
        !state.driveConnected -> "Conta não conectada"
        state.driveLastSyncAt > 0L -> "Último sync: ${formatSyncDate(state.driveLastSyncAt)}"
        else -> "Conectado: nunca sincronizado"
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val built = viewModel.buildBackupJson()
            val result = built.mapCatching { json ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                        ?: error("Arquivo não disponível")
                }
            }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { if (state.includeSecrets) "Backup salvo com chaves e sessões." else "Backup salvo sem senhas e sessões." },
                    onFailure = { e -> e.message ?: "Não foi possível salvar o backup." },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val data = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            require(output.length + n <= AotvBackup.MAX_BACKUP_BYTES)
                            output.append(buffer, 0, n)
                        }
                        output.toString()
                    } ?: error("Arquivo não disponível")
                }
            }
            data.onSuccess { viewModel.prepareRestore(it, "arquivo") }
                .onFailure { Toast.makeText(context, "Não foi possível ler este backup.", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pagePadding(), vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!LocalIsTv.current) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary) }
            PageHeader("Backup e restauração", Modifier.weight(1f), sub = "Sem servidor externo. Tudo fica entre os seus aparelhos.")
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = pagePadding()), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.busy != null) item {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Accent)
                    Text(state.busy!!, color = TextSecondary)
                }
            }
            state.message?.let { msg -> item {
                Row(Modifier.fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    IconButton(onClick = viewModel::clearMessage) { Icon(Icons.Default.Close, "Fechar", tint = TextSecondary) }
                }
            } }
            if (state.serverRunning) item { ServerPanel(url = state.serverUrl.orEmpty(), pin = state.serverPin.orEmpty(), onClose = viewModel::stopServer) }

            item { SettingsSection("Conteúdo incluído") }
            item {
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Incluir chaves e sessões",
                    sub = if (state.includeSecrets) "O backup inclui senhas e sessões. Guarde o arquivo com cuidado."
                    else "Somente addons, histórico, lista, plugins e layout",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.includeSecrets) "Ligado" else "Desligado", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Switch(checked = state.includeSecrets, onCheckedChange = viewModel::setIncludeSecrets)
                        }
                    },
                    onClick = { viewModel.setIncludeSecrets(!state.includeSecrets) },
                )
            }

            item { SettingsSection("Google Drive", modifier = driveSectionModifier(driveHighlighted)) }
            item {
                SettingsRow(
                    icon = Icons.Default.Cloud,
                    title = "Sincronizar com o Google Drive",
                    sub = driveStatus,
                    trailing = null,
                )
            }
            if (state.driveClientId.isBlank()) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Edit,
                        title = "Configurar Client ID do Google",
                        sub = "Crie o projeto no Google Cloud e cole o Client ID aqui",
                        onClick = { driveClientIdOpen = true },
                    )
                }
            } else if (!state.driveConnected) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Cloud,
                        title = "Conectar conta Google",
                        sub = "Login com a conta que vai guardar o backup",
                        onClick = {
                            val intent = viewModel.driveSignInIntent(context)
                            if (intent != null) driveSignInLauncher.launch(intent)
                        },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Edit,
                        title = "Alterar Client ID do Google",
                        sub = "Trocar o projeto do Google Cloud",
                        onClick = { driveClientIdOpen = true },
                    )
                }
            } else {
                item {
                    SettingsRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Subir agora",
                        sub = "Envia o backup atual p/ o Google Drive",
                        onClick = { viewModel.uploadToDrive() },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Download,
                        title = "Baixar agora",
                        sub = "Baixa o backup do Drive e mostra o resumo p/ restaurar",
                        onClick = { viewModel.downloadFromDrive() },
                    )
                }
                // Lote 23 (v1.33.0): sync contínuo — sobe o backup sozinho
                // (worker de 6 h + na abertura, ver ContinuousSync); o botão
                // "Sincronizar agora" roda o mesmo caminho na hora.
                item {
                    SettingsRow(
                        icon = Icons.Default.Sync,
                        title = "Sync contínuo",
                        sub = if (state.continuousSyncEnabled)
                            "Ligado: sobe sozinho a cada 6 h e na abertura" +
                                if (state.continuousSyncLastMs > 0L) " · último sync ${formatSyncDate(state.continuousSyncLastMs)}" else ""
                        else "Desligado: o backup só sobe com \"Subir agora\"",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (state.continuousSyncEnabled) "Ligado" else "Desligado", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = state.continuousSyncEnabled, onCheckedChange = viewModel::setContinuousSyncEnabled)
                            }
                        },
                        onClick = { viewModel.setContinuousSyncEnabled(!state.continuousSyncEnabled) },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "Sincronizar agora",
                        sub = "Sobe o backup atual e grava o último sync",
                        onClick = { viewModel.syncNow() },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Default.Close,
                        title = "Desconectar conta",
                        sub = state.driveAccountEmail.ifBlank { "Sair da conta Google" },
                        onClick = { viewModel.driveSignOut() },
                    )
                }
            }

            item { SettingsSection("Wi-Fi direto (sem servidor)") }
            item {
                SettingsRow(
                    icon = Icons.Default.Wifi,
                    title = if (state.serverRunning) "Fechar servidor Wi-Fi" else "Abrir servidor Wi-Fi (QR e PIN)",
                    sub = "Mostra IP, porta, QR e PIN para TV ou celular",
                    onClick = { if (state.serverRunning) viewModel.stopServer() else viewModel.startServer() },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "Trazer de outro aparelho pelo Wi-Fi",
                    sub = "Digite o IP e PIN mostrados no PC ou celular",
                    onClick = { wifiFetchOpen = true },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Upload,
                    title = "Enviar para outro aparelho pelo Wi-Fi",
                    sub = "Envia o perfil atual para PC ou celular com PIN",
                    onClick = { wifiSendOpen = true },
                )
            }

            item { SettingsSection("Arquivo local (.json)") }
            item {
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    title = "Exportar arquivo (.json)",
                    sub = "Salva na pasta Downloads",
                    onClick = { createBackup.launch("aotv-backup.json") },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Add,
                    title = "Importar arquivo (.json)",
                    sub = "Escolher um arquivo de backup salvo no aparelho",
                    onClick = { importBackup.launch(arrayOf("application/json", "text/plain")) },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Edit,
                    title = "Colar backup JSON",
                    sub = "Cole o texto JSON caso o aparelho não abra seletor de arquivos",
                    onClick = { pasteOpen = true },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (pasteOpen) PasteBackupDialog(onClose = { pasteOpen = false }) { text ->
        pasteOpen = false
        viewModel.prepareRestore(text, "texto")
    }
    if (wifiFetchOpen) WifiDeviceDialog(title = "Trazer de outro aparelho", okLabel = "Buscar backup", onClose = { wifiFetchOpen = false }) { host, port, pin ->
        wifiFetchOpen = false
        viewModel.fetchFrom(host, port, pin)
    }
    if (wifiSendOpen) WifiDeviceDialog(title = "Enviar para outro aparelho", okLabel = "Enviar backup", onClose = { wifiSendOpen = false }) { host, port, pin ->
        wifiSendOpen = false
        viewModel.sendTo(host, port, pin)
    }
    state.pendingRestore?.let { pending ->
        RestoreSummaryDialog(pending, onClose = viewModel::dismissRestore, onConfirm = viewModel::confirmRestore)
    }
    if (driveClientIdOpen) DriveClientIdDialog(
        current = state.driveClientId,
        onClose = { driveClientIdOpen = false },
        onSave = { id ->
            driveClientIdOpen = false
            viewModel.setDriveClientId(id)
        },
    )
    state.driveConflict?.let { conflict ->
        DriveConflictDialog(
            conflict = conflict,
            onClose = viewModel::dismissDriveConflict,
            onOverwrite = viewModel::confirmDriveOverwrite,
            onDownload = {
                viewModel.dismissDriveConflict()
                viewModel.downloadFromDrive()
            },
        )
    }
}

/** Data/hora do último sync (dd/MM/yyyy HH:mm, no idioma do aparelho). */
private fun formatSyncDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date(epochMs))

/** Destaque da seção do Drive quando a busca profunda pediu a âncora dela. */
private fun driveSectionModifier(highlighted: Boolean): Modifier =
    if (highlighted) Modifier.background(AccentSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    else Modifier

/** Client ID OAuth do Google Drive (projeto do usuário no Google Cloud). */
@Composable
private fun DriveClientIdDialog(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Client ID do Google Drive", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value, { value = it.trim().take(200) },
                    label = { Text("Client ID OAuth") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "No Google Cloud, crie um projeto, ative a API do Google Drive e crie uma credencial OAuth do tipo \"Aplicativo da Web\". Cole o Client ID aqui. Sem ele, o sync fica desligado e o backup local/Wi-Fi continua funcionando. O upload usa o mesmo backup (respeita \"Incluir chaves e sessões\").",
                    color = TextSecondary, fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

/** O Drive tem um backup mais novo que o último sync: perguntar antes de subir por cima. */
@Composable
private fun DriveConflictDialog(
    conflict: BackupViewModel.DriveConflict,
    onClose: () -> Unit,
    onOverwrite: () -> Unit,
    onDownload: () -> Unit,
) {
    val whenText = if (conflict.remoteModifiedMs > 0L) " (em ${formatSyncDate(conflict.remoteModifiedMs)})" else ""
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("O Drive tem um backup mais novo", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("O arquivo ${conflict.remoteName} no Drive mudou desde o último sync$whenText.", color = TextSecondary)
                Text("Subir por cima apaga a versão do Drive. Baixar traz a versão do Drive p/ cá.", color = TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onOverwrite) { Text("Subir por cima") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDownload) { Text("Baixar do Drive") }
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
        },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

/** Painel do servidor Wi-Fi aberto: QR da página inicial + IP/porta + PIN. */
@Composable
private fun ServerPanel(url: String, pin: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Servidor Wi-Fi aberto", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (url.isNotBlank()) BackupQrCode(url, Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White).padding(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text("Endereço", color = TextSecondary, fontSize = 13.sp)
                Text(url.ifBlank { "Sem endereço" }, color = TextPrimary, fontSize = 15.sp)
                Text("PIN", color = TextSecondary, fontSize = 13.sp)
                Text(pin.ifBlank { "----" }, color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("No outro aparelho, abra o endereço no navegador e digite o PIN.", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AotvButton("Copiar endereço", { clipboard.setText(AnnotatedString(url)) }, icon = Icons.Default.ContentCopy, enabled = url.isNotBlank(), compact = true)
            AotvButton("Fechar servidor", onClose, compact = true)
        }
        Text("O servidor desliga ao sair desta tela.", color = TextSecondary, fontSize = 13.sp)
    }
}

/**
 * QR code preto no branco, um ponto por módulo e sem suavizar (mesmo padrão
 * de AddonSiteConfig.kt — duplicado de propósito: outra frente também precisa
 * de QR e cada uma usa a sua cópia, sem colisão).
 */
@Composable
private fun BackupQrCode(text: String, modifier: Modifier) {
    val img = remember(text) {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(img, "Código QR do servidor Wi-Fi", modifier, filterQuality = FilterQuality.None)
}

/** Cola o backup JSON como texto (caso o aparelho não abra o seletor). */
@Composable
private fun PasteBackupDialog(onClose: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Colar backup JSON", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value, { value = it.take(AotvBackup.MAX_BACKUP_BYTES) },
                label = { Text("Texto JSON do backup") }, singleLine = false, maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Restaurar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

/** IP + porta + PIN do outro aparelho (trazer ou enviar pelo Wi-Fi). */
@Composable
private fun WifiDeviceDialog(title: String, okLabel: String, onClose: () -> Unit, onSave: (String, Int, String) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val portNumber = port.toIntOrNull()
    val valid = host.isNotBlank() && portNumber != null && portNumber in 1..65535 && pin.length == 4 && pin.all(Char::isDigit)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(host, { host = it.trim().take(64) }, label = { Text("IP do outro aparelho (ex.: 192.168.1.10)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Porta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, label = { Text("PIN de 4 dígitos") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("O IP, a porta e o PIN aparecem na tela de Backup do outro aparelho.", color = TextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(host.trim(), portNumber ?: 0, pin) }, enabled = valid) { Text(okLabel) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}

/** Resumo antes de restaurar: o que o backup traz + confirmação. */
@Composable
private fun RestoreSummaryDialog(pending: BackupViewModel.PendingRestore, onClose: () -> Unit, onConfirm: () -> Unit) {
    val lines = pending.summary.lines()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Restaurar backup?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Este backup traz:", color = TextSecondary)
                if (lines.isEmpty()) Text("Nenhum dado (só preferências).", color = TextSecondary)
                lines.forEach { Text("• $it", color = TextPrimary) }
                if (pending.summary.appVersion.isNotBlank()) Text("Feito no app ${pending.summary.appVersion}.", color = TextSecondary, fontSize = 13.sp)
                if (!pending.summary.includesSecrets) {
                    Text("Sem senhas e sessões: depois de restaurar, configure novamente suas senhas e sessões.", color = TextSecondary, fontSize = 13.sp)
                }
                Text("O que já existe continua: nada é apagado sem querer.", color = TextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
        containerColor = BackgroundCard, titleContentColor = TextPrimary, textContentColor = TextSecondary,
    )
}
