package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.live.LiveSourceType
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Fontes da TV ao vivo. Listas M3U, contas Xtream, guia extra e a aba na barra. */
@Composable
fun LiveSourcesScreen(onBack: () -> Unit, viewModel: LiveSourcesViewModel = hiltViewModel()) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var kind by remember { mutableStateOf(LiveSourceType.M3U) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // Deu certo, limpa o formulário e fecha o teclado.
    LaunchedEffect(ui.message) { if (ui.message != null) { name = ""; url = ""; epg = ""; server = ""; user = ""; pass = ""; focus.clearFocus() } }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader("TV ao vivo", Modifier.weight(1f), sub = "${st.sources.size} fontes · ${st.channels.size} canais")
            if (st.sources.isNotEmpty()) com.aotv.app.ui.common.GlassCircleButton(
                Icons.Default.Refresh, viewModel::refresh, contentDescription = "Atualizar canais e guia", busy = ui.busy == "refresh" || st.loading,
            )
        }

        val summary: @Composable () -> Unit = {
            com.aotv.app.ui.common.SummaryCard {
                Text("Canais", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                com.aotv.app.ui.common.SummaryRow("Fontes", "${st.sources.count { it.enabled }} ligadas de ${st.sources.size}")
                com.aotv.app.ui.common.SummaryRow("Canais", "${st.channels.size}")
                com.aotv.app.ui.common.SummaryRow("Com guia", "${st.epg.size}")
                com.aotv.app.ui.common.SummaryRow("Favoritos", "${st.favorites.size}")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Canais e guia ficam salvos por 12 horas. A tela abre na hora e atualiza sozinha depois.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        com.aotv.app.ui.common.ContentWithSummary(summary) { below ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.message?.let { Notice(it, true, viewModel::clearMessage) }
                ui.error?.let { Notice(it, false, viewModel::clearMessage) }

                if (st.sources.isNotEmpty()) {
                    com.aotv.app.ui.common.SettingsSection("Suas fontes")
                    st.sources.forEach { s ->
                        val count = st.channels.count { it.sourceId == s.id }
                        val acc = st.accounts[s.id]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                val parts = listOfNotNull(
                                    if (s.type == LiveSourceType.M3U) "Lista M3U" else "Xtream",
                                    if (s.enabled) "$count canais" else "Desligada",
                                    acc?.let { a -> if (a.expiresAt > 0) "vence em ${date(a.expiresAt)}" else a.status.ifBlank { null } },
                                )
                                Text(parts.joinToString(" · "), color = TextTertiary, fontSize = 13.sp)
                            }
                            Switch(
                                checked = s.enabled, onCheckedChange = { viewModel.setEnabled(s.id, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BackgroundCard2, uncheckedBorderColor = Border),
                            )
                            IconButton(onClick = { confirmRemove = s.id }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Delete, "Remover ${s.name}", tint = LiveRed)
                            }
                        }
                    }
                    com.aotv.app.ui.common.SettingsRow(
                        icon = Icons.Default.LiveTv,
                        title = "Aba Ao vivo na barra",
                        sub = "Mostra os canais na barra de baixo",
                        trailing = {
                            Switch(
                                checked = st.showInBar, onCheckedChange = viewModel::setShowInBar,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = TextTertiary, uncheckedTrackColor = BackgroundCard2, uncheckedBorderColor = Border),
                            )
                        },
                        onClick = { viewModel.setShowInBar(!st.showInBar) },
                    )
                }

                com.aotv.app.ui.common.SettingsSection("Adicionar fonte")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundCard2).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.aotv.app.ui.common.FilterChipPill("Lista M3U", kind == LiveSourceType.M3U, { kind = LiveSourceType.M3U })
                        com.aotv.app.ui.common.FilterChipPill("Conta Xtream", kind == LiveSourceType.XTREAM, { kind = LiveSourceType.XTREAM })
                    }
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome (opcional)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                    if (kind == LiveSourceType.M3U) {
                        OutlinedTextField(
                            url, { url = it.trim() }, singleLine = true, label = { Text("Endereço da lista (.m3u)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                        )
                        OutlinedTextField(
                            epg, { epg = it.trim() }, singleLine = true, label = { Text("Guia XMLTV (opcional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                        )
                        AotvButton(
                            "Adicionar lista", { viewModel.addM3u(name, url, epg) }, tone = ButtonTone.PRIMARY,
                            busy = ui.busy == "m3u", busyLabel = "Carregando canais…", enabled = (ui.busy == null || ui.busy == "m3u") && url.startsWith("http", true), compact = true,
                        )
                    } else {
                        OutlinedTextField(
                            server, { server = it.trim() }, singleLine = true, label = { Text("Servidor (com a porta)") }, placeholder = { Text("http://servidor.com:8080") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                        )
                        OutlinedTextField(user, { user = it.trim() }, singleLine = true, label = { Text("Usuário") }, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                        OutlinedTextField(
                            pass, { pass = it }, singleLine = true, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                        )
                        AotvButton(
                            "Conectar conta", { viewModel.addXtream(name, server, user, pass) }, tone = ButtonTone.PRIMARY,
                            busy = ui.busy == "xtream", busyLabel = "Conferindo…", enabled = (ui.busy == null || ui.busy == "xtream") && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(), compact = true,
                        )
                    }
                    Text(
                        "Use só listas e contas que você tem direito de assistir. O AOTV não fornece canais.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                below?.let { Spacer(Modifier.height(8.dp)); it() }
            }
        }
    }

    confirmRemove?.let { id ->
        val s = st.sources.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = BackgroundCard,
            title = { Text("Remover ${s?.name ?: "fonte"}?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = { Text("Os canais dessa fonte saem da aba Ao vivo. Favoritos dela também deixam de aparecer.", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { viewModel.remove(id); confirmRemove = null }) { Text("Remover", color = LiveRed, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar", color = TextSecondary) } },
        )
    }
}

@Composable
private fun Notice(text: String, ok: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (ok) Ok else DangerText).copy(alpha = .12f)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (ok) Ok else DangerText, modifier = Modifier.size(18.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = onClose) { Text("Ok", color = TextSecondary) }
    }
}

private fun date(ms: Long) = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(ms))
