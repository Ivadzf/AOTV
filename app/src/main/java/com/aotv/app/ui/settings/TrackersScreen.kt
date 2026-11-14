package com.aotv.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.remote.SimklService
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.ContentWithSummary
import com.aotv.app.ui.common.PageHeader
import com.aotv.app.ui.common.SettingsSection
import com.aotv.app.ui.common.SummaryCard
import com.aotv.app.ui.common.SummaryRow
import com.aotv.app.ui.common.pagePadding
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Tela "Trackers e Sincronização" (v1.13.1, Frente B — só Simkl, device flow V2).
 *
 * Ponto de entrada p/ a navegação (o coordenador faz a fiação da rota e do
 * hub; esta tela NÃO se conecta à navegação sozinha):
 * `TrackersScreen(onBack = { ... })`.
 *
 * - Client ID do Simkl: campo configurável, VAZIO por padrão (pref
 *   existente `simklClientId`, por perfil). Nenhum ID embutido no código.
 * - Botão Conectar: abre o diálogo do PIN (código grande + QR da URL com
 *   o código + "Esperando você confirmar no celular" + Cancelar).
 * - Status: conectado como / expira em / desconectado + Desconectar.
 */
@Composable
fun TrackersScreen(onBack: () -> Unit, viewModel: TrackersViewModel = hiltViewModel()) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val pad = pagePadding()
    val context = LocalContext.current
    var clientId by remember(st.clientId) { mutableStateOf(st.clientId) }
    var confirmLogout by remember { mutableStateOf(false) }
    // Só abre link web. O endereço é a página oficial de cadastro do Simkl.
    fun open(url: String) = runCatching {
        if (!url.startsWith("https://", true)) return@runCatching
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
            PageHeader("Trackers e Sincronização", Modifier.weight(1f), sub = "Simkl no perfil ${st.profileName}")
        }

        val summary: @Composable () -> Unit = {
            SummaryCard {
                Text("Simkl", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "Status",
                    st.connectedUser?.let { "Conectado como ${it.ifBlank { "conta Simkl" }}" } ?: "Desconectado",
                    if (st.connectedUser != null) Ok else TextPrimary,
                )
                if (st.connectedUser != null && st.expiresAt > 0L) {
                    SummaryRow("Expira em", formatSimklDate(st.expiresAt), TextPrimary)
                }
                SummaryRow("Client ID", if (st.clientId.isBlank()) "Não configurado" else "Configurado", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "O Simkl marca o que você assiste e sincroniza a Minha Lista, independente do Trakt. " +
                        "Tirar um título da Minha Lista nunca apaga o histórico assistido no Simkl.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        ContentWithSummary(summary) { _ ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                st.message?.let { SimklBanner(it, ok = true, onClose = viewModel::clearMessage) }
                st.error?.let { SimklBanner(it, ok = false, onClose = viewModel::clearMessage) }

                SettingsSection("Simkl")
                Text(
                    "Para conectar, crie um app grátis no site do Simkl e cole a API Key (o Client ID) aqui. " +
                        "No formulário, escolha o tipo \"TV, devices & command line\" — ele não pede segredo nem endereço de retorno, " +
                        "que é o certo para o login por PIN.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton(
                    "Abrir o cadastro de apps do Simkl", { open(SimklService.DEVELOPER_URL) },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    SimklService.DEVELOPER_URL,
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                )
                OutlinedTextField(
                    value = clientId, onValueChange = { clientId = it.trim() }, singleLine = true,
                    label = { Text("Client ID do Simkl (API Key)") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Salvar Client ID", { viewModel.saveClientId(clientId) },
                        icon = Icons.Default.CheckCircle, tone = ButtonTone.SECONDARY,
                        enabled = clientId != st.clientId, compact = true,
                    )
                    AotvButton(
                        "Conectar", viewModel::startConnect,
                        icon = Icons.Default.Link, tone = ButtonTone.PRIMARY, busy = st.busy, busyLabel = "Conectando…",
                        enabled = !st.busy && st.connectedUser == null, compact = true,
                    )
                }
                if (st.clientId.isBlank()) {
                    Text(
                        "Sem o Client ID o botão Conectar só mostra esta instrução — o AOTV precisa do próprio cadastro, " +
                            "não usa o ID de nenhum outro app.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                if (st.connectedUser != null) {
                    AotvButton(
                        "Desconectar", { confirmLogout = true },
                        icon = Icons.AutoMirrored.Filled.Logout, tone = ButtonTone.SECONDARY, compact = true,
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Desconectar o Simkl?", color = TextPrimary) },
            text = { Text("Só a sessão deste perfil é apagada. O Client ID continua salvo.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; viewModel.disconnect() }) {
                    Text("Desconectar", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Voltar", color = TextSecondary) }
            },
        )
    }

    st.pin?.let { pin ->
        SimklPinDialog(pin = pin, onCancel = viewModel::cancelConnect)
    }
}

/** Faixa de aviso/erro com botão Ok (o Banner do AccountsScreen é privado daquele arquivo). */
@Composable
private fun SimklBanner(text: String, ok: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background((if (ok) Ok else DangerText).copy(alpha = .12f)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
            tint = if (ok) Ok else DangerText, modifier = Modifier.size(18.dp),
        )
        Text(text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = onClose) { Text("Ok", color = TextSecondary) }
    }
}

/** Data no formato do app (dd/MM/yyyy, pt-BR). Sem data, não inventa nada. */
private fun formatSimklDate(at: Long): String = runCatching {
    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(at))
}.getOrNull().orEmpty()

/**
 * Diálogo "Conectar ao Simkl", igual à foto de referência: URL com o código
 * + código grande + QR da URL + "Esperando você confirmar no celular" +
 * Cancelar. No device flow V2 (v1.13.1) o user_code tem o formato
 * "XXXX-YYYY" (9 caracteres com o hífen) e a URL é a verification_uri_complete
 * — a URL completa com o código embutido, que o QR codifica e o texto mostra
 * (pin.pinUrl); apontar a câmera abre a página já com o código.
 */
@Composable
private fun SimklPinDialog(pin: SimklService.DeviceCode, onCancel: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Conectar ao Simkl", color = TextPrimary) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pin.pinUrl, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text(
                    pin.userCode,
                    color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                SimklQrCode(pin.pinUrl, Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                // Lote 5 (v1.14.0): copiar a URL completa com o código, sem digitar.
                AotvButton(
                    "Copiar link",
                    {
                        clipboard.setText(AnnotatedString(pin.pinUrl))
                        Toast.makeText(context, "Link copiado", Toast.LENGTH_SHORT).show()
                    },
                    icon = Icons.Default.ContentCopy, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "Esperando você confirmar no celular",
                    color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Text(
                    "Abra o endereço acima no celular ou aponte a câmera para o código, digite o PIN e confirme.",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = Accent) }
        },
    )
}

/** QR code preto no branco, um ponto por módulo e sem suavizar (mesmo padrão de AddonSiteConfig). */
@Composable
private fun SimklQrCode(text: String, modifier: Modifier) {
    val img = remember(text) {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    androidx.compose.foundation.Image(img, "Código QR da página do Simkl", modifier, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
}
