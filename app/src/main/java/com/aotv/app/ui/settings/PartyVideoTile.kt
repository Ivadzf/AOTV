package com.aotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aotv.app.data.party.PartyChatMessage
import com.aotv.app.data.party.PartyPeer
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard2
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val partyRendererEvents = object : RendererCommon.RendererEvents {
    override fun onFirstFrameRendered() {}
    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
}

/**
 * Tile de vídeo de um participante da Watch Party (Lote 19, v1.29.0).
 *
 * Mostra o vídeo remoto (WebRTC) ou as iniciais quando a câmera está
 * desligada, com o anel turquesa aceso em quem está falando
 * ([PartyPeer.isSpeaking]) e os ícones de microfone/câmera desligados.
 * Sem vídeo e sem câmera: só o círculo com a inicial (nunca inventa imagem).
 */
@Composable
fun PartyVideoTile(
    peer: PartyPeer,
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
) {
    val ringColor = if (peer.isSpeaking) Accent else Border
    // Faixa de vídeo ligada ao renderer (fora do `if`: remember não pode
    // ser condicional — some quando a câmera desliga e volta quando liga).
    var attachedTrack by remember { mutableStateOf<VideoTrack?>(null) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp, 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BackgroundCard2)
                .border(3.dp, ringColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (videoTrack != null && eglContext != null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglContext, partyRendererEvents)
                            setMirror(peer.isSelf)
                            setEnableHardwareScaler(true)
                        }
                    },
                    update = { renderer ->
                        if (attachedTrack != videoTrack) {
                            attachedTrack?.let { old -> runCatching { old.removeSink(renderer) } }
                            videoTrack?.let { track -> runCatching { track.addSink(renderer) } }
                            attachedTrack = videoTrack
                        }
                    },
                    onRelease = { renderer ->
                        attachedTrack?.let { old -> runCatching { old.removeSink(renderer) } }
                        attachedTrack = null
                        runCatching { renderer.release() }
                    },
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Text(
                    peer.name.firstOrNull()?.uppercase().orEmpty(),
                    color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            if (!peer.voiceOn) {
                Icon(
                    Icons.Default.MicOff, "Microfone desligado",
                    tint = TextSecondary, modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(16.dp),
                )
            }
            if (!peer.cameraOn) {
                Icon(
                    Icons.Default.VideocamOff, "Câmera desligada",
                    tint = TextSecondary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (videoTrack == null) {
                Icon(Icons.Default.Person, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
            }
            Text(
                peer.name.ifBlank { "Convidado" } + if (peer.isSelf) " (você)" else "",
                color = if (peer.isSpeaking) Accent else TextSecondary,
                fontSize = 12.sp, fontWeight = if (peer.isSpeaking) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Chat da sala (Lote 19, v1.29.0) — lista + envio. Reutilizado na tela da
 * Watch Party e no overlay do player. Mensagens do sistema (nome "Sala")
 * aparecem em cinza; as demais, com o nome de quem enviou.
 */
@Composable
fun PartyChatBox(
    messages: List<PartyChatMessage>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(messages.size) {
        runCatching { scroll.animateScrollTo(scroll.maxValue) }
    }
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 220.dp)
                .verticalScroll(scroll)
                .background(BackgroundCard2, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                Text("Nenhuma mensagem ainda. Diga oi p/ sala.", color = TextTertiary, fontSize = 13.sp)
            } else {
                messages.forEach { item ->
                    if (item.peerId.isBlank()) {
                        Text(item.text, color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                    } else {
                        Text(
                            "${item.name}: ${item.text}",
                            color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(500) },
                placeholder = { Text("Mensagem p/ a sala…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                },
            ) {
                Icon(Icons.Default.Send, "Enviar", tint = Accent)
            }
        }
    }
}

/** Cor do anel de "falando" (p/ testes e reuso): turquesa quando fala, borda quando não. */
fun partyRingColor(speaking: Boolean): Color = if (speaking) Accent else Border
