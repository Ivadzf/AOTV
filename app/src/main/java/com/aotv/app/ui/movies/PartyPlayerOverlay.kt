package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.party.PARTY_REACTIONS
import com.aotv.app.data.party.PartyLobbyPhase
import com.aotv.app.data.party.PartyRole
import com.aotv.app.data.party.PartySyncState
import com.aotv.app.ui.common.AotvButton
import com.aotv.app.ui.common.ButtonTone
import com.aotv.app.ui.settings.PartyChatBox
import com.aotv.app.ui.settings.PartyVideoTile
import com.aotv.app.ui.settings.WatchPartyViewModel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Overlay da Watch Party dentro do player (Lote 19, v1.29.0).
 *
 * Só aparece com sessão ativa. Fechado, é um botão pequeno com o nº de
 * participantes; aberto, mostra os tiles de vídeo (com o anel de quem
 * fala), o chat + reações, o "pedir p/ pausar" (convidado) ou os pedidos
 * pendentes (anfitrião), o aviso de "alcançando a sala a 1,5×" e a
 * oferta de quem entrou atrasado. Não toca no player: só lê o estado do
 * [WatchPartyViewModel] e chama as ações dele.
 */
@Composable
fun PartyPlayerOverlay(
    viewModel: WatchPartyViewModel,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.ui.collectAsState()
    val peers by viewModel.partyPeers.collectAsState()
    val chat by viewModel.partyChat.collectAsState()
    val pauseRequests by viewModel.partyPauseRequests.collectAsState()
    val lobbyPhase by viewModel.partyLobbyPhase.collectAsState()
    val syncInfo by viewModel.partySyncInfo.collectAsState()
    val lateJoin by viewModel.partyLateJoin.collectAsState()
    val connectionState by viewModel.partyConnectionState.collectAsState()
    val remoteTracks by viewModel.runtime.remoteVideoTracks.collectAsState()
    // Estado do painel (antes do `return`: remember não pode ser condicional).
    var expanded by remember { mutableStateOf(false) }

    if (session.session == null) return

    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
        Row(
            Modifier.background(BackgroundCard2, RoundedCornerShape(20.dp)).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Group, null, tint = Accent, modifier = Modifier.size(18.dp))
            Text(
                "Sala ${session.session?.code.orEmpty()} · ${peers.size}",
                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (connectionState == PartySyncState.CONNECTED) {
                Text(" ●", color = Accent, fontSize = 13.sp)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.Close else Icons.Default.Group, if (expanded) "Fechar sala" else "Abrir sala", tint = TextSecondary)
            }
        }

        if (!expanded) return@Column

        Column(
            Modifier
                .padding(top = 8.dp)
                .background(BackgroundCard2, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (lobbyPhase) {
                    PartyLobbyPhase.LOBBY -> "Sala de espera — o anfitrião ainda não começou o filme."
                    PartyLobbyPhase.PLAYING -> "Assistindo junto"
                    PartyLobbyPhase.POST_FILM -> "Conversa pós-filme — o filme acabou, o chat continua."
                },
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            )
            if (syncInfo.catchingUp) {
                Text("Alcançando a sala a 1,5×…", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else if (syncInfo.lastDecision.isNotBlank() && connectionState == PartySyncState.CONNECTED) {
                Text(
                    "Sincronização: ${syncInfo.lastDecision}",
                    color = TextTertiary, fontSize = 12.sp,
                )
            }

            // Quem entrou atrasado escolhe como entrar (só p/ convidado).
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

            // Tiles de vídeo (o seu usa a câmera local).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                peers.forEach { peer ->
                    val track = if (peer.isSelf) viewModel.runtime.localVideoTrack else remoteTracks[peer.peerId]
                    PartyVideoTile(
                        peer = peer,
                        videoTrack = track,
                        eglContext = viewModel.runtime.eglContext,
                    )
                }
            }

            // Pedidos de pausa (só o anfitrião vê e responde).
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
            if (pauseRequests.isEmpty() && session.session?.role == PartyRole.GUEST) {
                AotvButton(
                    "Pedir p/ pausar",
                    onClick = { viewModel.requestPause("") },
                    icon = Icons.Default.Pause,
                    tone = ButtonTone.SECONDARY,
                )
            }

            PartyChatBox(messages = chat, onSend = viewModel::sendChat)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PARTY_REACTIONS.forEach { emoji ->
                    IconButton(onClick = { viewModel.sendReaction(emoji) }) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
            }

            AotvButton(
                "Sair da sala",
                onClick = { viewModel.leaveParty() },
                tone = ButtonTone.DANGER,
            )
            Text(
                "Sair fecha a conexão e apaga o código da sessão.",
                color = DangerText, fontSize = 12.sp,
            )
        }
    }
}

/** mm:ss p/ o aviso de quem entrou atrasado (ex.: 42:10). */
fun formatPartyMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
