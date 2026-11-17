package com.aotv.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.party.PARTY_REACTIONS
import com.aotv.app.data.party.PARTY_SYNC_UNAVAILABLE_REASON
import com.aotv.app.data.party.PartyLobbyPhase
import com.aotv.app.data.party.PartyRole
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.data.party.WatchParty
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.common.SettingsRow
import com.aotv.app.ui.common.SummaryCard
import com.aotv.app.ui.common.SummaryRow
import com.aotv.app.ui.movies.formatPartyMs
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

@Composable
private fun partySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Accent,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = BackgroundCard2,
    uncheckedBorderColor = Border,
)

/**
 * Tela "Watch Party" (Lote 10, v1.20.0; sala real no Lote 19, v1.29.0) —
 * sessão por código + conexão WebRTC P2P de verdade.
 *
 * Cria a sessão (gera o código), entra com o código e abre a sala: na
 * rede local (o anfitrião liga um servidor no próprio aparelho) ou por
 * troca manual de SDP (copiar e colar, sem servidor nenhum). Depois de
 * conectada, a sala sincroniza play/pause/seek (o anfitrião é a verdade),
 * com voz (anel em quem fala), câmera (o celular vira a câmera da TV),
 * chat + reações, "catch up" a 1,5×, "pedir p/ pausar", lobby e conversa
 * pós-filme. Sem servidor de terceiro no caminho (ver PartyRuntime).
 *
 * Deep link: `aotv://party/?code=XXXXXX` (ver AndroidManifest.xml). Quem
 * abre o convite cai no app e, ao abrir esta tela, o código já vem
 * pré-preenchido — entrar continua sendo um toque do usuário.
 *
 * Ponto de entrada p/ a navegação (o coordenador faz a fiação da rota e do
 * hub; esta tela NÃO se conecta à navegação sozinha):
 * `WatchPartyScreen(onBack = { ... })`.
 */
@Composable
fun WatchPartyScreen(
    onBack: () -> Unit,
    highlightAnchor: String? = null,
    viewModel: WatchPartyViewModel = hiltViewModel(),
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val connectionState by viewModel.partyConnectionState.collectAsStateWithLifecycle()
    val peers by viewModel.partyPeers.collectAsStateWithLifecycle()
    val chat by viewModel.partyChat.collectAsStateWithLifecycle()
    val pauseRequests by viewModel.partyPauseRequests.collectAsStateWithLifecycle()
    val lobbyPhase by viewModel.partyLobbyPhase.collectAsStateWithLifecycle()
    val syncInfo by viewModel.partySyncInfo.collectAsStateWithLifecycle()
    val lateJoin by viewModel.partyLateJoin.collectAsStateWithLifecycle()
    val lanUrl by viewModel.partyLanUrl.collectAsStateWithLifecycle()
    val manualOffer by viewModel.manualOffer.collectAsStateWithLifecycle()
    val manualAnswer by viewModel.manualAnswer.collectAsStateWithLifecycle()
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val cameraEnabled by viewModel.cameraEnabled.collectAsStateWithLifecycle()
    val catchUpEnabled by viewModel.catchUpEnabled.collectAsStateWithLifecycle()
    val turnUrl by viewModel.turnUrl.collectAsStateWithLifecycle()
    val turnUsername by viewModel.turnUsername.collectAsStateWithLifecycle()
    val turnPassword by viewModel.turnPassword.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val session = st.session

    // Código que chegou por deep link (o intent da Activity que abriu o app).
    // LocalContext.current NÃO pode ir dentro de remember {} — lê fora e usa no LaunchedEffect.
    val activity = context as? Activity
    val deepLinkCode = activity?.intent?.data?.let { WatchParty.parseDeepLink(it) }
    LaunchedEffect(deepLinkCode) {
        if (deepLinkCode != null) viewModel.onInviteReceived(deepLinkCode)
    }

    var joinCode by remember(st.inviteCode) { mutableStateOf(st.inviteCode.orEmpty()) }
    var hostPort by remember { mutableStateOf("") }
    var partyTitle by remember { mutableStateOf("") }
    var manualOfferField by remember { mutableStateOf("") }
    var manualAnswerField by remember { mutableStateOf("") }
    var pauseReason by remember { mutableStateOf("") }
    var turnUrlField by remember(turnUrl) { mutableStateOf(turnUrl) }
    var turnUserField by remember(turnUsername) { mutableStateOf(turnUsername) }
    var turnPassField by remember(turnPassword) { mutableStateOf(turnPassword) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setVoiceEnabled(true)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setCameraEnabled(true)
    }

    // Só abre o compartilhamento do sistema. O texto é o convite gerado aqui.
    fun share(text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartilhar convite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent, unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
    )

    val syncLabel = when (connectionState) {
        PartySyncState.IDLE -> "Sem sala"
        PartySyncState.LOBBY -> "Sala aberta, esperando"
        PartySyncState.CONNECTING -> "Conectando…"
        PartySyncState.CONNECTED -> "Conectado (WebRTC)"
        PartySyncState.FAILED -> "Falhou (ver motivo)"
    }
    val syncColor = when (connectionState) {
        PartySyncState.CONNECTED -> Accent
        PartySyncState.FAILED -> DangerText
        else -> TextPrimary
    }

    VpnPartyScaffold(
        title = "Watch Party",
        sub = "Assista junto com um código — sem servidor de terceiro",
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
            Text("Sessão", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            SummaryRow("Status", if (session == null) "Sem sessão" else "Na sessão ${session.code}", TextPrimary)
            SummaryRow("Papel", if (session == null) "—" else if (session.role == PartyRole.HOST) "Anfitrião" else "Convidado", TextPrimary)
            SummaryRow("Sincronização", syncLabel, syncColor)
            if (connectionState == PartySyncState.CONNECTED) {
                SummaryRow("Relógio", "offset ${syncInfo.clockOffsetMs} ms · ida-e-volta ${syncInfo.rttMs} ms", TextSecondary)
                SummaryRow("Participantes", "${syncInfo.peersConnected} conectados", TextSecondary)
                if (syncInfo.lastDecision.isNotBlank()) {
                    SummaryRow("Última ação", syncInfo.lastDecision, TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "O anfitrião é a verdade da sala: play, pause e seek dele valem p/ todo mundo (com \"catch up\" a 1,5× p/ atraso pequeno). A conexão é P2P direta, na rede local ou por troca manual — sem servidor de terceiro.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        if (session == null) {
            VpnPartySection("Criar sessão", anchor = SettingsAnchors.PARTY_CREATE)
            Text(
                "Você vira o anfitrião e recebe um código de 6 caracteres p/ compartilhar. Quem entrar com o código cai na mesma sessão.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            AotvButton(
                "Criar sessão",
                onClick = viewModel::createSession,
                icon = Icons.Default.Group,
                tone = ButtonTone.PRIMARY,
            )

            VpnPartySection("Entrar com código", anchor = SettingsAnchors.PARTY_JOIN)
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
                label = { Text("Código da sessão") },
                placeholder = { Text("Ex.: K7M2QX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            AotvButton(
                "Entrar na sessão",
                onClick = { viewModel.joinSession(joinCode) },
                icon = Icons.Default.Login,
                tone = ButtonTone.PRIMARY,
                enabled = joinCode.length == WatchParty.CODE_LENGTH,
            )
        } else {
            VpnPartySection("Código da sessão", anchor = SettingsAnchors.PARTY_CREATE)
            Text(
                "Compartilhe o código ou o link com quem vai assistir junto. Quem abrir o link cai no app com o código já preenchido.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Row(
                Modifier.fillMaxWidth().background(BackgroundCard2, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    session.code.chunked(3).joinToString(" "),
                    color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(session.code)) }) {
                    Icon(Icons.Default.ContentCopy, "Copiar código", tint = TextSecondary)
                }
                IconButton(onClick = { share(viewModel.inviteText()) }) {
                    Icon(Icons.Default.Share, "Compartilhar convite", tint = TextSecondary)
                }
            }
            Text(
                "Link do convite: ${viewModel.inviteLink()}",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            AotvButton(
                "Sair da sessão",
                onClick = viewModel::leaveSession,
                tone = ButtonTone.DANGER,
            )

            VpnPartySection("Assistir junto", anchor = null)
            if (session.role == PartyRole.HOST) {
                Text(
                    "Você é o anfitrião. Abra a sala na rede local: quem está no mesmo Wi-Fi entra com o endereço abaixo. O título é opcional — o player preenche quando o filme começar.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                OutlinedTextField(
                    value = partyTitle,
                    onValueChange = { partyTitle = it },
                    label = { Text("O que vão assistir (opcional)") },
                    placeholder = { Text("Ex.: Duna: Parte 2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                AotvButton(
                    "Abrir sala na rede local",
                    onClick = { viewModel.startHosting(partyTitle) },
                    icon = Icons.Default.Group,
                    tone = ButtonTone.PRIMARY,
                )
                val url = lanUrl
                if (url != null) {
                    Text("Endereço da sala: $url", color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QrCode(url, Modifier.size(180.dp).background(Color.White).padding(8.dp), "Código QR da sala")
                        IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(Icons.Default.ContentCopy, "Copiar endereço", tint = TextSecondary)
                        }
                    }
                    Text(
                        "Quem está no mesmo Wi-Fi digita este endereço (ou lê o QR) na tela da Watch Party dele. Sem internet, sem conta, sem servidor fora de casa.",
                        color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            } else {
                Text(
                    "Digite o endereço da sala que o anfitrião te passou (o mesmo do QR dele). O código da sessão já está valendo.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                OutlinedTextField(
                    value = hostPort,
                    onValueChange = { hostPort = it.trim() },
                    label = { Text("Endereço do anfitrião") },
                    placeholder = { Text("Ex.: 192.168.1.10:12345") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                AotvButton(
                    "Entrar na sala",
                    onClick = { viewModel.joinLan(hostPort, session.code) },
                    icon = Icons.Default.Login,
                    tone = ButtonTone.PRIMARY,
                    enabled = hostPort.isNotBlank(),
                )
            }

            VpnPartySection("Sem a mesma rede Wi-Fi? Troca manual", anchor = null)
            Text(
                "Anfitrião e convidado copiam e colam a oferta e a resposta (SDP + ICE). Funciona através da internet, sem servidor nenhum — mas é manual, e a mídia só existe entre anfitrião e cada convidado.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            if (session.role == PartyRole.HOST) {
                AotvButton(
                    "Criar oferta manual",
                    onClick = viewModel::createManualOffer,
                    icon = Icons.Default.Link,
                    tone = ButtonTone.SECONDARY,
                )
                val offer = manualOffer
                if (offer != null) {
                    OutlinedTextField(
                        value = offer,
                        onValueChange = {},
                        label = { Text("Oferta (copie e mande p/ o convidado)") },
                        readOnly = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AotvButton(
                        "Copiar oferta",
                        onClick = { clipboard.setText(AnnotatedString(offer)) },
                        icon = Icons.Default.ContentCopy,
                        tone = ButtonTone.SECONDARY,
                    )
                    OutlinedTextField(
                        value = manualAnswerField,
                        onValueChange = { manualAnswerField = it.trim() },
                        label = { Text("Resposta do convidado (cole aqui)") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AotvButton(
                        "Aplicar resposta",
                        onClick = { viewModel.completeManualAnswer(manualAnswerField) },
                        icon = Icons.Default.Link,
                        tone = ButtonTone.PRIMARY,
                        enabled = manualAnswerField.isNotBlank(),
                    )
                }
            } else {
                OutlinedTextField(
                    value = manualOfferField,
                    onValueChange = { manualOfferField = it.trim() },
                    label = { Text("Oferta do anfitrião (cole aqui)") },
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                AotvButton(
                    "Entrar com a oferta",
                    onClick = { viewModel.joinWithManualOffer(manualOfferField) },
                    icon = Icons.Default.Login,
                    tone = ButtonTone.PRIMARY,
                    enabled = manualOfferField.isNotBlank(),
                )
                val answer = manualAnswer
                if (answer != null) {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = {},
                        label = { Text("Resposta (copie e mande de volta)") },
                        readOnly = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AotvButton(
                        "Copiar resposta",
                        onClick = { clipboard.setText(AnnotatedString(answer)) },
                        icon = Icons.Default.ContentCopy,
                        tone = ButtonTone.SECONDARY,
                    )
                }
            }

            VpnPartySection("Participantes", anchor = SettingsAnchors.PARTY_MEMBERS)
            if (st.members.isEmpty()) {
                Text("Ninguém na sessão.", color = TextSecondary, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.members.forEach { member ->
                        SettingsRow(
                            icon = Icons.Default.Person,
                            title = member.name,
                            sub = if (member.isSelf) "Você (neste aparelho)" else "Participante",
                            trailing = null,
                        )
                    }
                }
            }
            if (connectionState == PartySyncState.IDLE || connectionState == PartySyncState.LOBBY) {
                Text(
                    "Abra a sala (acima) p/ ver quem mais está conectado. Sem conexão, só você aparece aqui — nenhum participante é inventado.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                peers.forEach { peer ->
                    val track = if (peer.isSelf) viewModel.runtime.localVideoTrack
                    else viewModel.runtime.remoteVideoTracks.value[peer.peerId]
                    PartyVideoTile(
                        peer = peer,
                        videoTrack = track,
                        eglContext = viewModel.runtime.eglContext,
                    )
                }
            }

            VpnPartySection("Lobby", anchor = null)
            when (lobbyPhase) {
                PartyLobbyPhase.LOBBY -> {
                    if (session.role == PartyRole.HOST) {
                        Text(
                            "Sala de espera: todo mundo já pode conversar no chat abaixo. Quando estiverem prontos, comece o filme — ele toca junto p/ todo mundo.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                        AotvButton(
                            "Começar o filme",
                            onClick = viewModel::startFilm,
                            icon = Icons.Default.PlayArrow,
                            tone = ButtonTone.PRIMARY,
                            enabled = connectionState == PartySyncState.CONNECTED,
                        )
                        if (connectionState != PartySyncState.CONNECTED) {
                            Text(
                                "Conecte pelo menos 1 participante antes de começar.",
                                color = TextTertiary, fontSize = 13.sp,
                            )
                        }
                    } else {
                        Text(
                            "Sala de espera: converse no chat abaixo enquanto o anfitrião não começa o filme. Quando ele começar, o player segue sozinho.",
                            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                }
                PartyLobbyPhase.PLAYING -> {
                    Text(
                        "O filme está tocando junto. Abra o player p/ assistir — o play, o pause e o seek do anfitrião valem p/ todo mundo.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    if (session.role == PartyRole.HOST) {
                        AotvButton(
                            "Filme acabou — abrir conversa pós-filme",
                            onClick = viewModel::endFilm,
                            tone = ButtonTone.SECONDARY,
                        )
                    }
                }
                PartyLobbyPhase.POST_FILM -> {
                    Text(
                        "Conversa pós-filme: o filme acabou e o chat continua aberto p/ comentar.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            val late = lateJoin
            if (late != null) {
                Text(
                    "O filme já vai em ${formatPartyMs(late.hostPositionMs)}. Como quer entrar?",
                    color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                AotvButton("Entrar no ponto da sala", onClick = viewModel::joinNow, tone = ButtonTone.PRIMARY)
                AotvButton("Ver do início a 1,5×", onClick = viewModel::catchUpFromStart, tone = ButtonTone.SECONDARY)
                AotvButton("10 min atrás a 1,5×", onClick = viewModel::catchUpFromTenMinAgo, tone = ButtonTone.SECONDARY)
            }
            pauseRequests.forEach { req ->
                Text(
                    "${req.name} pediu p/ pausar${if (req.reason.isNotBlank()) ": “${req.reason}”" else "."}",
                    color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AotvButton("Pausar", onClick = { viewModel.respondPause(req.id, true) }, tone = ButtonTone.PRIMARY)
                    AotvButton("Não", onClick = { viewModel.respondPause(req.id, false) }, tone = ButtonTone.SECONDARY)
                }
            }
            if (session.role == PartyRole.GUEST && connectionState == PartySyncState.CONNECTED) {
                OutlinedTextField(
                    value = pauseReason,
                    onValueChange = { pauseReason = it },
                    label = { Text("Motivo (opcional)") },
                    placeholder = { Text("Ex.: preciso atender a porta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                AotvButton(
                    "Pedir p/ pausar",
                    onClick = { viewModel.requestPause(pauseReason) },
                    icon = Icons.Default.Pause,
                    tone = ButtonTone.SECONDARY,
                )
            }

            VpnPartySection("Voz", anchor = SettingsAnchors.PARTY_VOICE)
            SettingsRow(
                icon = Icons.Default.Mic,
                title = "Microfone",
                sub = "Sua voz na sala. Quem fala ganha o anel turquesa no tile.",
                trailing = {
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.setVoiceEnabled(enabled)
                            }
                        },
                        colors = partySwitchColors(),
                    )
                },
            )
            SettingsRow(
                icon = Icons.Default.Mic,
                title = "Alcançar a sala sozinho (catch-up)",
                sub = "Atraso de 0,5–2 s acelera a 1,5× em vez de pular. Desligado, pula direto.",
                trailing = {
                    Switch(
                        checked = catchUpEnabled,
                        onCheckedChange = viewModel::setCatchUpEnabled,
                        colors = partySwitchColors(),
                    )
                },
            )
            Text(
                "O anel de quem fala mede a energia do microfone — barulho alto também acende. Nada é gravado: o nível vira número e some.",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )

            VpnPartySection("Câmera", anchor = SettingsAnchors.PARTY_CAMERA)
            SettingsRow(
                icon = Icons.Default.Videocam,
                title = "Câmera",
                sub = "Seu vídeo na sala — o celular vira a câmera da TV.",
                trailing = {
                    Switch(
                        checked = cameraEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                viewModel.setCameraEnabled(enabled)
                            }
                        },
                        colors = partySwitchColors(),
                    )
                },
            )
            if (!viewModel.runtime.hasCamera()) {
                Text(
                    "Este aparelho não tem câmera (ex.: TV): a câmera fica indisponível, mas a voz e o chat funcionam.",
                    color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }

            VpnPartySection("Chat e reações", anchor = null)
            PartyChatBox(messages = chat, onSend = viewModel::sendChat)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PARTY_REACTIONS.forEach { emoji ->
                    IconButton(onClick = { viewModel.sendReaction(emoji) }) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }

            VpnPartySection("Relay (TURN)", anchor = SettingsAnchors.PARTY_RELAY)
            Text(
                "Só p/ quando a conexão direta falha fora da rede local (ex.: amigos longe com NAT fechado). É a SUA conta de TURN — nada embutido, nada enviado a ninguém. Vazio = sem relay. Vale na próxima conexão.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            OutlinedTextField(
                value = turnUrlField,
                onValueChange = { turnUrlField = it.trim() },
                label = { Text("URL do TURN") },
                placeholder = { Text("Ex.: turn:seu-servidor:3478") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = turnUserField,
                onValueChange = { turnUserField = it.trim() },
                label = { Text("Usuário do TURN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = turnPassField,
                onValueChange = { turnPassField = it },
                label = { Text("Senha do TURN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            AotvButton(
                "Salvar relay",
                onClick = { viewModel.saveTurn(turnUrlField, turnUserField, turnPassField) },
                icon = Icons.Default.Link,
                tone = ButtonTone.SECONDARY,
            )
        }

        VpnPartySection("Sincronização", anchor = SettingsAnchors.PARTY_SYNC)
        Text(
            PARTY_SYNC_UNAVAILABLE_REASON,
            color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
        )
        Text(
            "Como funciona: o anfitrião manda a posição + o relógio dele (medido por ping-pong) e cada convidado segue — atraso pequeno acelera a 1,5×, atraso grande pula. O canal de dados é criptografado pelo próprio WebRTC (DTLS).",
            color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
        )
    }
}
