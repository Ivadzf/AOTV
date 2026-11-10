package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.debrid.DebridAccount
import com.aotv.app.data.debrid.DebridProvider
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Debrid. Conecta Real-Debrid, TorBox ou Premiumize para os torrents tocarem por link direto. */
@Composable
fun DebridScreen(onBack: () -> Unit, viewModel: DebridViewModel = hiltViewModel()) {
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
            com.aotv.app.ui.common.PageHeader(
                "Debrid", Modifier.weight(1f),
                sub = st.active?.let { "${it.provider.label} em uso" } ?: if (st.accounts.isEmpty()) "Nenhuma conta conectada" else "Desligado",
            )
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Torrent sem espera", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                val a = st.active
                com.aotv.app.ui.common.SummaryRow("Em uso", a?.provider?.label ?: "Nenhum", if (a != null) Ok else TextPrimary)
                if (a != null) com.aotv.app.ui.common.SummaryRow("Premium", premiumLabel(a), if (premiumLeftDays(a) in 0..5) DangerText else TextPrimary)
                com.aotv.app.ui.common.SummaryRow("Contas", if (st.accounts.isEmpty()) "Nenhuma" else st.accounts.joinToString(" · ") { it.provider.short })
                com.aotv.app.ui.common.SummaryRow("Sem cache", if (st.settings.hideUncached) "Esconde" else "Toca por P2P")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Com debrid, o serviço já tem o arquivo e entrega o vídeo direto. O aparelho não compartilha nada com ninguém.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                st.message?.let { m -> item { Notice(m, ok = true, onClose = viewModel::clearMessage) } }
                st.error?.let { e -> item { Notice(e, ok = false, onClose = viewModel::clearMessage) } }

                item { com.aotv.app.ui.common.SettingsSection("Serviços") }
                items(DebridProvider.entries.toList(), key = { it.id }) { p ->
                    ProviderCard(
                        provider = p,
                        account = st.accounts.firstOrNull { it.provider == p },
                        inUse = st.active?.provider == p,
                        many = st.accounts.size > 1,
                        busy = st.busy == p,
                        locked = st.busy != null && st.busy != p,
                        onKey = { viewModel.connectKey(p, it) },
                        onDevice = { viewModel.startDevice(p) },
                        onUse = { viewModel.setPreferred(p) },
                        onDisconnect = { viewModel.disconnect(p) },
                        onOpenKeyPage = { open(p.keyPage) },
                    )
                }

                if (st.accounts.isNotEmpty()) {
                    item { com.aotv.app.ui.common.SettingsSection("Ajustes") }
                    item {
                        com.aotv.app.ui.common.SettingsRow(
                            icon = Icons.Default.Bolt,
                            iconTint = Accent,
                            title = "Usar debrid nos torrents",
                            sub = "Desligado, as fontes de torrent voltam a tocar por P2P",
                            trailing = { Switch(checked = st.settings.enabled, onCheckedChange = viewModel::setEnabled, colors = debridSwitch()) },
                            onClick = { viewModel.setEnabled(!st.settings.enabled) },
                        )
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Torrent que o serviço não tem pronto", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.aotv.app.ui.common.FilterChipPill("Tocar por P2P", !st.settings.hideUncached, { viewModel.setHideUncached(false) })
                                com.aotv.app.ui.common.FilterChipPill("Esconder", st.settings.hideUncached, { viewModel.setHideUncached(true) })
                            }
                            Text(
                                "O Real-Debrid não informa o cache antes. Ele confere as melhores fontes de cada título e o resto toca por P2P.",
                                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                }
                below?.let { b -> item { Spacer(Modifier.height(8.dp)); b() } }
            }
        }
    }

    st.device?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDevice,
            containerColor = BackgroundCard,
            title = { Text("Entrar no ${d.provider.label}", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Abra o endereço no celular ou no computador e digite o código", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text(
                        d.userCode, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    QrCode(d.verifyUrl, Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp), "Código QR para entrar no ${d.provider.label}")
                    Text(d.manualUrl.removePrefix("https://").removePrefix("http://"), color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("Aguardando a confirmação…", color = TextTertiary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open(d.verifyUrl) }) { Text("Abrir no navegador", color = Accent, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDevice) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: DebridProvider,
    account: DebridAccount?,
    inUse: Boolean,
    many: Boolean,
    busy: Boolean,
    locked: Boolean,
    onKey: (String) -> Unit,
    onDevice: () -> Unit,
    onUse: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenKeyPage: () -> Unit,
) {
    var keyOpen by remember(provider) { mutableStateOf(false) }
    var key by remember(provider) { mutableStateOf("") }
    LaunchedEffect(account) { if (account != null) { keyOpen = false; key = "" } }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard2)
            .then(if (inUse) Modifier.border(1.dp, Accent.copy(alpha = .45f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (account != null) AccentSoft else Surface2),
                contentAlignment = Alignment.Center,
            ) { Text(provider.short, color = if (account != null) Accent else TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                Text(provider.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        account == null -> if (provider.device) "Entre com código ou cole a chave da API" else "Cole a chave da API"
                        else -> listOf(account.user.ifBlank { "Conectado" }, premiumLabel(account)).joinToString(" · ")
                    },
                    color = TextTertiary, fontSize = 13.sp, maxLines = 2,
                )
            }
            if (inUse) Icon(Icons.Default.CheckCircle, "Em uso", tint = Accent, modifier = Modifier.size(22.dp))
        }

        if (account == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (provider.device) com.aotv.app.ui.common.AotvButton(
                    "Entrar com código", onDevice, icon = Icons.Default.QrCode2,
                    tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, busy = busy && !keyOpen, busyLabel = "Gerando código…",
                    enabled = !locked, compact = true,
                )
                com.aotv.app.ui.common.AotvButton(
                    if (keyOpen) "Fechar" else "Colar chave", { keyOpen = !keyOpen }, icon = Icons.Default.Key,
                    enabled = !locked && !busy, compact = true,
                )
            }
            if (keyOpen) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim() }, singleLine = true,
                    label = { Text("Chave da API do ${provider.label}") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.aotv.app.ui.common.AotvButton(
                        "Conectar", { onKey(key) }, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                        busy = busy, busyLabel = "Conectando…", enabled = key.length >= 8 && !locked, compact = true,
                    )
                    TextButton(onClick = onOpenKeyPage) {
                        Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Onde pego a chave", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (many && !inUse) com.aotv.app.ui.common.AotvButton("Usar este", onUse, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY, compact = true)
                com.aotv.app.ui.common.AotvButton("Desconectar", onDisconnect, compact = true)
            }
        }
    }
}

@Composable
private fun Notice(text: String, ok: Boolean, onClose: () -> Unit) {
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

private fun premiumLeftDays(a: DebridAccount): Int =
    if (a.premiumUntil <= 0) -1 else ((a.premiumUntil - System.currentTimeMillis()) / 86_400_000L).toInt()

private fun premiumLabel(a: DebridAccount): String {
    if (a.premiumUntil <= 0) return "Premium ativo"
    val d = premiumLeftDays(a)
    val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(a.premiumUntil))
    return if (d < 0) "Premium vencido" else "Premium até $date"
}

@Composable
private fun debridSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BackgroundCard2, uncheckedBorderColor = Border,
)
