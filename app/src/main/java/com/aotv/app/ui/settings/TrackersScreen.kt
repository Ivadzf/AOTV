package com.aotv.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.remote.AniListService
import com.aotv.app.data.remote.MDBListService
import com.aotv.app.data.remote.SimklService
import com.aotv.app.data.remote.TraktService
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
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.Ok
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Tela "Trackers e Sincronização" (Simkl v1.13.1, device flow V2; Trakt v1.17.0;
 * MDBList, AniList e TheTVDB v1.17.0).
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
 * - Trakt: mesmo padrão do Simkl — Client ID + Client Secret opcional,
 *   toggle de ativação, botão Conectar com o diálogo do código (código
 *   grande + QR da URL + "Esperando você confirmar no celular" +
 *   Cancelar) e Desconectar. Nenhuma chave embutida no código.
 * - MDBList: chave de API do usuário + toggle + fontes de nota; só consome
 *   notas (read-only), nada é enviado de volta.
 * - AniList: Client ID + botão Autorizar (OAuth no navegador, retorno pelo
 *   deep link aotv://anilist/callback) + toggle + Desconectar.
 * - TheTVDB: chave de API do usuário + toggle + ordenação de episódios
 *   (oficial, DVD ou absoluta).
 * - Avaliação pós-filme: toggle que pede a nota ao fim do título e envia
 *   aos trackers conectados (Trakt e AniList; o MDBList não recebe nota).
 */
@Composable
fun TrackersScreen(onBack: () -> Unit, viewModel: TrackersViewModel = hiltViewModel()) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val pad = pagePadding()
    val context = LocalContext.current
    var clientId by remember(st.clientId) { mutableStateOf(st.clientId) }
    var traktClientId by remember(st.traktClientId) { mutableStateOf(st.traktClientId) }
    var traktClientSecret by remember(st.traktClientSecret) { mutableStateOf(st.traktClientSecret) }
    var mdbApiKey by remember(st.mdbApiKey) { mutableStateOf(st.mdbApiKey) }
    var aniClientId by remember(st.aniClientId) { mutableStateOf(st.aniClientId) }
    var tvdbApiKey by remember(st.tvdbApiKey) { mutableStateOf(st.tvdbApiKey) }
    // Lote 20 (v1.30.0) — olho p/ exibir/mascarar as chaves (só a TMDB
    // tinha; o AniList Client ID já é texto visível, sem máscara).
    var traktSecretVisible by remember { mutableStateOf(false) }
    var mdbKeyVisible by remember { mutableStateOf(false) }
    var tvdbKeyVisible by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmTraktLogout by remember { mutableStateOf(false) }
    var confirmAniLogout by remember { mutableStateOf(false) }
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
            PageHeader("Trackers e Sincronização", Modifier.weight(1f), sub = "Simkl, Trakt, MDBList, AniList e TheTVDB no perfil ${st.profileName}")
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
                SummaryRow("Sync 2 vias", if (st.simklSyncEnabled) "Sim" else "Não", TextPrimary)
                SummaryRow(
                    "Último pull",
                    if (st.simklLastPullMs > 0L) formatSimklDate(st.simklLastPullMs) else "Nunca",
                    TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "O Simkl marca o que você assiste e sincroniza a Minha Lista, independente do Trakt. " +
                        "Tirar um título da Minha Lista nunca apaga o histórico assistido no Simkl.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("Trakt", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "Status",
                    st.traktUser?.let { "Conectado como ${it.ifBlank { "conta Trakt" }}" } ?: "Desconectado",
                    if (st.traktUser != null) Ok else TextPrimary,
                )
                SummaryRow("Client ID", if (st.traktClientId.isBlank()) "Não configurado" else "Configurado", TextPrimary)
                SummaryRow("Ativo", if (st.traktEnabled) "Sim" else "Não", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "O Trakt marca o que você assiste e sincroniza a Minha Lista com o celular, a TV e o site.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("MDBList", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "Status",
                    if (st.mdbApiKey.isBlank()) "Desconectado" else "Conectado",
                    if (st.mdbApiKey.isNotBlank()) Ok else TextPrimary,
                )
                SummaryRow("Chave", if (st.mdbApiKey.isBlank()) "Não configurada" else "Configurada", TextPrimary)
                SummaryRow("Ativo", if (st.mdbEnabled) "Sim" else "Não", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "O MDBList mostra as notas de cada fonte (IMDb, TMDB, Trakt, Letterboxd e outras) nos detalhes do título. Ele só lê — nenhuma nota sai daqui para o MDBList.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("AniList", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "Status",
                    if (st.aniConnected) "Conectado" else "Desconectado",
                    if (st.aniConnected) Ok else TextPrimary,
                )
                SummaryRow("Client ID", if (st.aniClientId.isBlank()) "Não configurado" else "Configurado", TextPrimary)
                SummaryRow("Ativo", if (st.aniEnabled) "Sim" else "Não", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "O AniList marca o que você assiste de anime e guarda a nota que você dá ao fim do episódio ou do filme.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("TheTVDB", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow(
                    "Status",
                    if (st.tvdbApiKey.isBlank()) "Desconectado" else "Conectado",
                    if (st.tvdbApiKey.isNotBlank()) Ok else TextPrimary,
                )
                SummaryRow("Chave", if (st.tvdbApiKey.isBlank()) "Não configurada" else "Configurada", TextPrimary)
                SummaryRow("Ativo", if (st.tvdbEnabled) "Sim" else "Não", TextPrimary)
                SummaryRow("Ordenação", tvdbOrderingLabel(st.tvdbOrdering), TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "O TheTVDB ordena os episódios da série (oficial, DVD ou absoluta) e ajuda o tracking a achar o episódio certo.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("Avaliação pós-filme", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                SummaryRow("Pedir nota ao fim", if (st.postPlayRatingEnabled) "Sim" else "Não", TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ao fim de um título, o AOTV pede uma nota de 1 a 10 e envia aos trackers conectados (Trakt e AniList). O MDBList não recebe nota — ele só lê.",
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
                // Lote 23 (v1.33.0): sync de 2 vias — puxar assistidos e
                // Minha Lista do Simkl (o push já existia desde a v1.13.0).
                if (st.connectedUser != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Sincronização de 2 vias", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (st.simklSyncEnabled) "Ligada: o Simkl puxa assistidos e Minha Lista; o que só existe lá vira \"assistido em outro lugar\"."
                                else "Desligada: o AOTV só envia o que você assiste, não puxa nada.",
                                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                        Switch(checked = st.simklSyncEnabled, onCheckedChange = viewModel::setSimklSyncEnabled, colors = trackerSwitchColors())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AotvButton(
                            "Sincronizar agora", viewModel::syncSimklNow,
                            icon = Icons.Default.Sync, tone = ButtonTone.SECONDARY,
                            busy = st.busy, busyLabel = "Sincronizando…", compact = true,
                        )
                    }
                    if (st.simklLastPullMs > 0L) {
                        Text(
                            "Último pull: ${formatSimklDate(st.simklLastPullMs)}.",
                            color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                    }
                }

                st.traktMessage?.let { SimklBanner(it, ok = true, onClose = viewModel::clearTraktMessage) }
                st.traktError?.let { SimklBanner(it, ok = false, onClose = viewModel::clearTraktMessage) }

                SettingsSection("Trakt")
                Text(
                    "Para conectar, crie um app grátis no site do Trakt e cole o Client ID aqui. " +
                        "No formulário, o Trakt pede um endereço de retorno — use urn:ietf:wg:oauth:2.0:oob. " +
                        "O Client Secret é opcional.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton(
                    "Abrir o cadastro de apps do Trakt", { open("https://trakt.tv/oauth/applications") },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "https://trakt.tv/oauth/applications",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                )
                OutlinedTextField(
                    value = traktClientId, onValueChange = { traktClientId = it.trim() }, singleLine = true,
                    label = { Text("Client ID do Trakt") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                OutlinedTextField(
                    value = traktClientSecret, onValueChange = { traktClientSecret = it.trim() }, singleLine = true,
                    label = { Text("Client Secret do Trakt (opcional)") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { traktSecretVisible = !traktSecretVisible }) {
                            Icon(
                                if (traktSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (traktSecretVisible) "Ocultar Client Secret" else "Mostrar Client Secret",
                            )
                        }
                    },
                    visualTransformation = if (traktSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativar o Trakt", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Desligado, o Trakt não marca o que você assiste nem sincroniza a Minha Lista.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Switch(checked = st.traktEnabled, onCheckedChange = viewModel::setTraktEnabled, colors = trackerSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Salvar dados do Trakt", { viewModel.saveTraktCredentials(traktClientId, traktClientSecret) },
                        icon = Icons.Default.CheckCircle, tone = ButtonTone.SECONDARY,
                        enabled = traktClientId != st.traktClientId || traktClientSecret != st.traktClientSecret, compact = true,
                    )
                    AotvButton(
                        "Conectar", viewModel::startTraktConnect,
                        icon = Icons.Default.Link, tone = ButtonTone.PRIMARY, busy = st.traktBusy, busyLabel = "Conectando…",
                        enabled = !st.traktBusy && st.traktUser == null, compact = true,
                    )
                }
                if (st.traktClientId.isBlank()) {
                    Text(
                        "Sem o Client ID o botão Conectar só mostra esta instrução — o AOTV precisa do próprio cadastro, " +
                            "não usa o ID de nenhum outro app.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                if (st.traktUser != null) {
                    AotvButton(
                        "Desconectar", { confirmTraktLogout = true },
                        icon = Icons.AutoMirrored.Filled.Logout, tone = ButtonTone.SECONDARY, compact = true,
                    )
                }

                st.mdbMessage?.let { SimklBanner(it, ok = true, onClose = viewModel::clearMdbMessage) }
                st.mdbError?.let { SimklBanner(it, ok = false, onClose = viewModel::clearMdbMessage) }

                SettingsSection("MDBList")
                Text(
                    "Para ver as notas por fonte, crie uma conta grátis no MDBList e cole a chave de API aqui. " +
                        "O MDBList só é lido — nenhuma nota ou lista sai daqui para ele.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton(
                    "Abrir o MDBList", { open("https://mdblist.com/") },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "https://mdblist.com/",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                )
                OutlinedTextField(
                    value = mdbApiKey, onValueChange = { mdbApiKey = it.trim() }, singleLine = true,
                    label = { Text("Chave de API do MDBList") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { mdbKeyVisible = !mdbKeyVisible }) {
                            Icon(
                                if (mdbKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (mdbKeyVisible) "Ocultar chave do MDBList" else "Mostrar chave do MDBList",
                            )
                        }
                    },
                    visualTransformation = if (mdbKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativar o MDBList", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Desligado, as notas por fonte não aparecem nos detalhes do título.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Switch(checked = st.mdbEnabled, onCheckedChange = viewModel::setMdbEnabled, colors = trackerSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Salvar chave do MDBList", { viewModel.saveMdbApiKey(mdbApiKey) },
                        icon = Icons.Default.CheckCircle, tone = ButtonTone.SECONDARY,
                        enabled = mdbApiKey != st.mdbApiKey, compact = true,
                    )
                    // Lote 19 (v1.29.0): valida a chave digitada (ainda não
                    // salva, ou a salva) contra o MDBList e mostra o
                    // resultado no banner acima da seção.
                    AotvButton(
                        "Testar chave", { viewModel.testMdbApiKey(mdbApiKey) },
                        icon = Icons.Default.Search, tone = ButtonTone.SECONDARY,
                        enabled = mdbApiKey.isNotBlank() && !st.mdbTesting,
                        busy = st.mdbTesting, busyLabel = "Testando…", compact = true,
                    )
                }
                if (st.mdbApiKey.isBlank()) {
                    Text(
                        "Sem a chave o MDBList fica desligado — o AOTV precisa da sua própria chave, não usa a de nenhum outro app.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                Text(
                    "Fontes de nota",
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                MDBListService.DEFAULT_SOURCES.forEach { source ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            viewModel.toggleMdbSource(source, source !in st.mdbSources)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mdbSourceLabel(source), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = source in st.mdbSources,
                            onCheckedChange = { viewModel.toggleMdbSource(source, it) },
                        )
                    }
                }

                st.aniMessage?.let { SimklBanner(it, ok = true, onClose = viewModel::clearAniMessage) }
                st.aniError?.let { SimklBanner(it, ok = false, onClose = viewModel::clearAniMessage) }

                SettingsSection("AniList")
                Text(
                    "Para conectar, crie um app grátis no site do AniList e cole o Client ID aqui. " +
                        "Depois toque em Autorizar: o navegador abre a página do AniList e o retorno volta sozinho para o AOTV.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton(
                    "Abrir o cadastro de apps do AniList", { open("https://anilist.co/settings/developer") },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "https://anilist.co/settings/developer",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                )
                OutlinedTextField(
                    value = aniClientId, onValueChange = { aniClientId = it.trim() }, singleLine = true,
                    label = { Text("Client ID do AniList") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativar o AniList", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Desligado, o AniList não marca o que você assiste de anime.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Switch(checked = st.aniEnabled, onCheckedChange = viewModel::setAniEnabled, colors = trackerSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Salvar Client ID", { viewModel.saveAniClientId(aniClientId) },
                        icon = Icons.Default.CheckCircle, tone = ButtonTone.SECONDARY,
                        enabled = aniClientId != st.aniClientId, compact = true,
                    )
                    if (!st.aniConnected) {
                        AotvButton(
                            "Autorizar AniList", { if (st.aniOAuthUrl.isNotBlank()) open(st.aniOAuthUrl) },
                            icon = Icons.Default.Link, tone = ButtonTone.PRIMARY,
                            enabled = st.aniClientId.isNotBlank(), compact = true,
                        )
                    }
                }
                if (st.aniClientId.isBlank()) {
                    Text(
                        "Sem o Client ID o botão Autorizar fica apagado — o AOTV precisa do próprio cadastro, não usa o ID de nenhum outro app.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                if (st.aniConnected) {
                    AotvButton(
                        "Desconectar", { confirmAniLogout = true },
                        icon = Icons.AutoMirrored.Filled.Logout, tone = ButtonTone.SECONDARY, compact = true,
                    )
                }

                st.tvdbMessage?.let { SimklBanner(it, ok = true, onClose = viewModel::clearTvdbMessage) }
                st.tvdbError?.let { SimklBanner(it, ok = false, onClose = viewModel::clearTvdbMessage) }

                SettingsSection("TheTVDB")
                Text(
                    "Para ordenar os episódios pela ordem do TheTVDB, crie uma conta grátis no site e cole a chave de API aqui.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton(
                    "Abrir o TheTVDB", { open("https://thetvdb.com/") },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "https://thetvdb.com/",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                )
                OutlinedTextField(
                    value = tvdbApiKey, onValueChange = { tvdbApiKey = it.trim() }, singleLine = true,
                    label = { Text("Chave de API do TheTVDB") },
                    leadingIcon = { Icon(Icons.Default.Key, null, tint = TextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { tvdbKeyVisible = !tvdbKeyVisible }) {
                            Icon(
                                if (tvdbKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (tvdbKeyVisible) "Ocultar chave do TheTVDB" else "Mostrar chave do TheTVDB",
                            )
                        }
                    },
                    visualTransformation = if (tvdbKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ativar o TheTVDB", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Desligado, os episódios seguem a ordem oficial do TMDB.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Switch(checked = st.tvdbEnabled, onCheckedChange = viewModel::setTvdbEnabled, colors = trackerSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton(
                        "Salvar chave do TheTVDB", { viewModel.saveTvdbApiKey(tvdbApiKey) },
                        icon = Icons.Default.CheckCircle, tone = ButtonTone.SECONDARY,
                        enabled = tvdbApiKey != st.tvdbApiKey, compact = true,
                    )
                }
                if (st.tvdbApiKey.isBlank()) {
                    Text(
                        "Sem a chave o TheTVDB fica desligado — o AOTV precisa da sua própria chave, não usa a de nenhum outro app.",
                        color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                Text(
                    "Ordenação",
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                listOf("official" to "Oficial", "dvd" to "DVD", "absolute" to "Absoluta").forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = st.tvdbOrdering == key,
                            onClick = { viewModel.setTvdbOrdering(key) },
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = st.tvdbOrdering == key, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = TextPrimary, fontSize = 14.sp)
                    }
                }

                SettingsSection("Avaliação pós-filme")
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pedir nota ao fim do título", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Ao fim de um título, o AOTV pede uma nota de 1 a 10 e envia aos trackers conectados (Trakt e AniList). Desligado, nenhuma nota é pedida.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                    Switch(checked = st.postPlayRatingEnabled, onCheckedChange = viewModel::setPostPlayRatingEnabled, colors = trackerSwitchColors())
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Desconectar o Simkl?", color = TextPrimary) },
            text = { Text("A sessão deste perfil é apagada e revogada no site do Simkl. O Client ID continua salvo.", color = TextSecondary) },
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

    if (confirmTraktLogout) {
        AlertDialog(
            onDismissRequest = { confirmTraktLogout = false },
            title = { Text("Desconectar o Trakt?", color = TextPrimary) },
            text = { Text("Só a sessão deste perfil é apagada. O Client ID continua salvo.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmTraktLogout = false; viewModel.disconnectTrakt() }) {
                    Text("Desconectar", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTraktLogout = false }) { Text("Voltar", color = TextSecondary) }
            },
        )
    }

    if (confirmAniLogout) {
        AlertDialog(
            onDismissRequest = { confirmAniLogout = false },
            title = { Text("Desconectar o AniList?", color = TextPrimary) },
            text = { Text("Só o token deste perfil é apagado. O Client ID continua salvo.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmAniLogout = false; viewModel.disconnectAni() }) {
                    Text("Desconectar", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAniLogout = false }) { Text("Voltar", color = TextSecondary) }
            },
        )
    }

    st.pin?.let { pin ->
        SimklPinDialog(pin = pin, onCancel = viewModel::cancelConnect)
    }

    st.traktPin?.let { pin ->
        TraktPinDialog(pin = pin, onCancel = viewModel::cancelTraktConnect)
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
                TrackerQrCode(pin.pinUrl, Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp), "Código QR da página do Simkl")
                // Lote 5 (v1.14.0): copiar a URL completa com o código, sem digitar.
                AotvButton(
                    "Copiar link",
                    {
                        clipboard.setText(AnnotatedString(pin.pinUrl))
                        Toast.makeText(context, "Link copiado", Toast.LENGTH_SHORT).show()
                    },
                    icon = Icons.Default.ContentCopy, tone = ButtonTone.SECONDARY, compact = true,
                )
                // Lote 23 (v1.33.0): abrir a página do PIN direto no navegador
                // (a URL já traz o código embutido — nada p/ digitar).
                AotvButton(
                    "Abrir simkl.com/pin",
                    {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(pin.pinUrl),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    icon = Icons.Default.Link, tone = ButtonTone.SECONDARY, compact = true,
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

/**
 * Diálogo "Conectar ao Trakt", espelho do SimklPinDialog: URL com o código
 * + código grande + QR da URL + "Esperando você confirmar no celular" +
 * Cancelar. A URL é a verification_url do device flow (trakt.tv/activate);
 * apontar a câmera abre a página onde o código é digitado.
 */
@Composable
private fun TraktPinDialog(pin: TraktService.DeviceCode, onCancel: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Conectar ao Trakt", color = TextPrimary) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(pin.url, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text(
                    pin.userCode,
                    color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                TrackerQrCode(pin.url, Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp), "Código QR da página do Trakt")
                AotvButton(
                    "Copiar link",
                    {
                        clipboard.setText(AnnotatedString(pin.url))
                        Toast.makeText(context, "Link copiado", Toast.LENGTH_SHORT).show()
                    },
                    icon = Icons.Default.ContentCopy, tone = ButtonTone.SECONDARY, compact = true,
                )
                Text(
                    "Esperando você confirmar no celular",
                    color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Text(
                    "Abra o endereço acima no celular ou aponte a câmera para o código, digite o código e confirme.",
                    color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Cancelar", color = Accent) }
        },
    )
}

/** Rótulo da fonte de nota do MDBList (a chave crua é minúscula, ex.: "tomatoes"). */
private fun mdbSourceLabel(source: String): String = when (source) {
    "imdb" -> "IMDb"
    "tmdb" -> "TMDB"
    "trakt" -> "Trakt"
    "letterboxd" -> "Letterboxd"
    "tomatoes" -> "Rotten Tomatoes"
    "tomatoesaudience" -> "RT Audiência"
    "metacritic" -> "Metacritic"
    "metacriticuser" -> "Metacritic (usuários)"
    "myanimelist" -> "MyAnimeList"
    "mdb" -> "MDBList"
    "rogerebert" -> "RogerEbert"
    else -> source.replaceFirstChar { it.titlecase() }
}

/** Rótulo da ordenação de episódios do TheTVDB ("official", "dvd" ou "absolute"). */
private fun tvdbOrderingLabel(ordering: String): String = when (ordering) {
    "official" -> "Oficial"
    "dvd" -> "DVD"
    "absolute" -> "Absoluta"
    else -> ordering.replaceFirstChar { it.titlecase() }
}

/** Cores do toggle de ativação do Trakt (mesmo padrão dos toggles das Configurações). */
@Composable
private fun trackerSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = BackgroundCard2,
    uncheckedBorderColor = Border,
)

/** QR code preto no branco, um ponto por módulo e sem suavizar (mesmo padrão de AddonSiteConfig). */
@Composable
private fun TrackerQrCode(text: String, modifier: Modifier, description: String) {
    val img = remember(text) {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    androidx.compose.foundation.Image(img, description, modifier, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
}
