package com.aotv.app.ui.settings

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.vpn.VPN_MAX_CONFIG_BYTES
import com.aotv.app.data.vpn.VpnConnectionState
import com.aotv.app.data.vpn.VpnScope
import com.aotv.app.data.vpn.VpnType
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.SettingsGroup
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.common.SummaryCard
import com.aotv.app.ui.common.SummaryRow
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela "VPN" (Lote 10, v1.20.0; conexão no Lote VPN, v1.40.0) — gerenciar
 * perfis WireGuard/OpenVPN trazidos pelo usuário e conectar/desconectar
 * o túnel (motores WireGuard/OpenVPN3 + `VpnService` dos AARs, ver
 * `VpnConnectionManager`). O primeiro Conectar pede o consentimento do
 * sistema (`VpnService.prepare()`) — a tela lança a activity e só
 * conecta depois da autorização.
 *
 * Ponto de entrada p/ a navegação (o coordenador faz a fiação da rota e do
 * hub; esta tela NÃO se conecta à navegação sozinha):
 * `VpnScreen(onBack = { ... })`.
 */
@Composable
fun VpnScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: VpnViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = st.profiles.firstOrNull { it.id == st.activeProfileId }

    // Nome de exibição do arquivo escolhido (o SAF nem sempre devolve extensão confiável).
    fun displayName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx).orEmpty()
            }
        }
        return ""
    }

    // Importar perfil por SAF: lê o texto (limite de 256 KB) e manda p/ o parser.
    val pickProfileFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val data = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            require(output.length + n <= VPN_MAX_CONFIG_BYTES)
                            output.append(buffer, 0, n)
                        }
                        output.toString()
                    } ?: error("Arquivo não disponível")
                }
            }
            data.onSuccess { text -> viewModel.importConfig(text, displayName(uri).ifBlank { "perfil" }) }
                .onFailure { e ->
                    // O único require() da leitura é o limite de 256 KB.
                    if (e is IllegalArgumentException) viewModel.importTooLarge() else viewModel.importReadError()
                }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )

    // Consentimento do sistema (VpnService.prepare()): a primeira conexão
    // devolve o Intent e a tela lança a activity; o resultado volta aqui
    // e o ViewModel tenta conectar de novo (ou mostra a negativa).
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    val pendingConsent = st.pendingConsent
    if (pendingConsent != null) {
        LaunchedEffect(pendingConsent) {
            consentLauncher.launch(pendingConsent)
            viewModel.clearPendingConsent()
        }
    }

    VpnPartyScaffold(
        title = "VPN",
        sub = "Perfis WireGuard e OpenVPN do seu provedor",
        onBack = onBack,
        highlightAnchor = highlightAnchor,
    ) {
        val message = st.message
        if (message != null) {
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 14.sp)
                IconButton(onClick = viewModel::clearMessage) { Icon(Icons.Default.Close, "Fechar", tint = TextSecondary) }
            }
        }
        val error = st.error
        if (error != null) {
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = DangerText, modifier = Modifier.size(20.dp))
                Text(error, color = TextPrimary, modifier = Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp)
                IconButton(onClick = viewModel::clearMessage) { Icon(Icons.Default.Close, "Fechar", tint = TextSecondary) }
            }
        }

        SummaryCard {
            Text("Status", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            SummaryRow("Conexão", vpnStateLabel(st.connectionState), TextPrimary)
            SummaryRow("Perfil ativo", active?.name ?: "Nenhum", TextPrimary)
            if (st.connectionState == VpnConnectionState.CONNECTED) {
                SummaryRow("Servidor", st.connectedEndpoint.ifBlank { active?.endpoint ?: "—" }, TextPrimary)
            }
            SummaryRow("Perfis", "${st.profiles.size} importado(s)", TextPrimary)
            if (st.connectionState == VpnConnectionState.FAILED && st.lastError != null) {
                Spacer(Modifier.height(10.dp))
                Text(st.lastError, color = DangerText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "A VPN protege a reprodução sem app externo: o perfil é do seu provedor, nada de servidor do AOTV. " +
                    "Na primeira conexão o sistema pede autorização — sem ela o túnel não sobe.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        VpnPartySection("Importar perfil", anchor = SettingsAnchors.VPN_IMPORT)
        Text(
            "Escolha o arquivo .conf do WireGuard ou .ovpn do OpenVPN que o seu provedor forneceu. O formato é detectado pelo conteúdo do arquivo. As chaves do perfil ficam só neste aparelho e só entram no backup com o toggle de segredos ligado.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        AotvButton(
            "Importar arquivo .conf ou .ovpn",
            onClick = { pickProfileFile.launch(arrayOf("*/*")) },
            icon = Icons.Default.Upload,
            tone = ButtonTone.PRIMARY,
        )

        VpnPartySection("Perfis", anchor = SettingsAnchors.VPN_PROFILES)
        if (st.profiles.isEmpty()) {
            Text(
                "Nenhum perfil importado. Sem perfil, não há o que conectar — importe o arquivo do seu provedor acima.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        } else {
            SettingsGroup {
                st.profiles.forEach { profile ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = profile.id == st.activeProfileId,
                            onClick = { viewModel.setActiveProfile(profile.id) },
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(profile.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                vpnTypeLabel(profile.type) + " · " + profile.endpoint.ifBlank { "sem servidor" },
                                color = TextSecondary, fontSize = 13.sp,
                            )
                            val secretsNote = when {
                                profile.needsCredentials && !viewModel.hasSecrets(profile.id) -> "precisa de usuário e senha"
                                viewModel.hasSecrets(profile.id) -> "chave configurada"
                                else -> null
                            }
                            if (secretsNote != null) Text(secretsNote, color = TextTertiary, fontSize = 13.sp)
                        }
                        IconButton(onClick = { viewModel.startEdit(profile) }) {
                            Icon(Icons.Default.Edit, "Editar ${profile.name}", tint = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.askRemove(profile) }) {
                            Icon(Icons.Default.Delete, "Remover ${profile.name}", tint = DangerText)
                        }
                    }
                }
            }
        }

        VpnPartySection("Escopo e ligar automático", anchor = SettingsAnchors.VPN_SCOPE)
        Text(
            "O escopo é do perfil ativo (${active?.name ?: "nenhum"}). \"Só o AOTV\" passa só o tráfego do app pela VPN; \"Todo o aparelho\" passa tudo. Os escopos \"só HTTP\" e \"HTTP+HTTPS\" da fork não existem no Android — um serviço de VPN tunela o aparelho ou os apps escolhidos, não filtra por protocolo.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        SettingsGroup {
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = active?.scope == VpnScope.APP,
                    onClick = { val id = active?.id; if (id != null) viewModel.setScope(id, VpnScope.APP) },
                ).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = active?.scope == VpnScope.APP, onClick = null)
                Text("Só o AOTV", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = active?.scope == VpnScope.DEVICE,
                    onClick = { val id = active?.id; if (id != null) viewModel.setScope(id, VpnScope.DEVICE) },
                ).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = active?.scope == VpnScope.DEVICE, onClick = null)
                Text("Todo o aparelho", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        SettingsRow(
            icon = Icons.Default.VpnKey,
            title = "Ligar automaticamente",
            sub = if (st.autoConnect) "Ligado: conecta o perfil ativo ao abrir o app"
            else "Desligado: a VPN só conecta quando você tocar em Conectar",
            trailing = { Switch(checked = st.autoConnect, onCheckedChange = viewModel::setAutoConnect, modifier = Modifier.focusProperties { canFocus = false }) },
            onClick = { viewModel.setAutoConnect(!st.autoConnect) },
        )

        VpnPartySection("Conexão", anchor = SettingsAnchors.VPN_STATUS)
        Text(
            "Conecta o perfil ativo. Na primeira vez o sistema pede autorização p/ a VPN — autorize e o túnel sobe. " +
                "Perfis importados antes da v1.40.0 não guardam o arquivo completo: importe o .conf/.ovpn de novo p/ conectar.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AotvButton(
                "Conectar",
                onClick = viewModel::connectAttempt,
                icon = Icons.Default.CheckCircle,
                tone = ButtonTone.PRIMARY,
                enabled = active != null &&
                    (st.connectionState == VpnConnectionState.DISCONNECTED ||
                        st.connectionState == VpnConnectionState.FAILED),
            )
            AotvButton(
                "Desconectar",
                onClick = viewModel::disconnect,
                icon = Icons.Default.Close,
                tone = ButtonTone.DANGER,
                enabled = st.connectionState == VpnConnectionState.CONNECTED ||
                    st.connectionState == VpnConnectionState.CONNECTING,
            )
        }
        if (active == null) {
            Text(
                "Escolha um perfil acima para ativar o botão Conectar.",
                color = TextTertiary, fontSize = 13.sp,
            )
        }

        VpnPartySection("Kill switch", anchor = null)
        Text(
            "Enquanto conectado, o túnel WireGuard bloqueia o tráfego fora da VPN (o motor aplica o bloqueio no túnel). " +
                "No OpenVPN o bloqueio total depende do sistema: ligue \"Sempre ligada\" + \"Bloquear conexões sem VPN\" nas configurações de VPN — " +
                "o app não pode ligar isso sozinho, e sem ele o aparelho volta p/ a rede normal se o túnel cair.",
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        AotvButton(
            "Abrir configurações de VPN do sistema",
            onClick = { viewModel.openSystemVpnSettings(context) },
            icon = Icons.Default.Settings,
            tone = ButtonTone.SECONDARY,
        )
    }

    // ── Diálogo de edição ────────────────────────────────────────────────
    val editing = st.editingProfile
    if (editing != null) {
        var editName by remember(editing) { mutableStateOf(editing.name) }
        var editScope by remember(editing) { mutableStateOf(editing.scope) }
        var editUser by remember(editing) { mutableStateOf(editing.secrets.username) }
        var editPass by remember(editing) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Editar perfil", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it.take(40) },
                        label = { Text("Nome") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Escopo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = editScope == VpnScope.APP, onClick = { editScope = VpnScope.APP }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = editScope == VpnScope.APP, onClick = null)
                        Text("Só o AOTV", color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = editScope == VpnScope.DEVICE, onClick = null)
                        Text("Todo o aparelho", color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (editing.needsCredentials) {
                        OutlinedTextField(
                            value = editUser,
                            onValueChange = { editUser = it },
                            label = { Text("Usuário (OpenVPN)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = editPass,
                            onValueChange = { editPass = it },
                            label = { Text("Senha (vazia = mantém a atual)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveEdit(editing.id, editName, editScope, editUser, editPass) }) {
                    Text("Salvar", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEdit) { Text("Cancelar", color = TextSecondary) }
            },
        )
    }

    // ── Confirmar remoção ────────────────────────────────────────────────
    val removing = st.confirmRemove
    if (removing != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            title = { Text("Remover perfil?", color = TextPrimary) },
            text = {
                Text(
                    "O perfil \"${removing.name}\" e as chaves dele serão apagados deste aparelho. O arquivo original não é afetado.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemove) { Text("Remover", color = DangerText) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRemove) { Text("Cancelar", color = TextSecondary) }
            },
        )
    }
}

/** Rótulo do tipo de perfil. */
private fun vpnTypeLabel(type: VpnType): String = when (type) {
    VpnType.WIREGUARD -> "WireGuard"
    VpnType.OPENVPN -> "OpenVPN"
}

/** Rótulo do estado da conexão. */
private fun vpnStateLabel(state: VpnConnectionState): String = when (state) {
    VpnConnectionState.DISCONNECTED -> "Desconectado"
    VpnConnectionState.CONNECTING -> "Conectando…"
    VpnConnectionState.CONNECTED -> "Conectado"
    VpnConnectionState.DISCONNECTING -> "Desconectando…"
    VpnConnectionState.FAILED -> "Falhou"
}
