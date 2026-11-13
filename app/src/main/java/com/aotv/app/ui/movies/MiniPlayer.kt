package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Mini player (Fase C): quando o usuário minimiza o player, a sessão
 * ([PlayerSession], com o ExoPlayer dentro) sobrevive à saída da tela e a
 * reprodução continua. A barra compacta fica acima da pílula inferior.
 *
 * Por que a sessão mora aqui e não na tela: o ExoPlayer é criado dentro da
 * [MoviePlayerScreen] e o `onDispose` da tela libera tudo ao sair. Ao
 * minimizar, a sessão marca [PlayerSession.minimizedByUser] e o dispose pula
 * a liberação; a referência fica guardada aqui até expandir (a tela reusa a
 * mesma sessão) ou fechar (libera de vez). Sem isso, minimizar mataria o áudio.
 *
 * Só existe 1 player ativo por vez (mesmo padrão do [PipState]).
 */
object MiniPlayerState {
    /** Sessão minimizada (null = nenhum mini player ativo). Só o pacote movies mexe. */
    internal var session: PlayerSession? = null
        private set

    /** playId da rota do player minimizado (-1 = nenhum). É ele que o expandir reabre. */
    var playId: Int = -1
        private set

    /** true enquanto há um mini player ativo (a barra aparece). */
    val minimized = mutableStateOf(false)

    /** Guarda a sessão p/ sobreviver à saída da tela. Chamar ANTES do popBackStack. */
    fun minimize(s: PlayerSession, id: Int) {
        session = s
        playId = id
        s.minimizedByUser = true
        minimized.value = true
    }

    /** Sessão a reutilizar ao reabrir o player, só se for o mesmo playId. Não limpa. */
    internal fun sessionFor(id: Int): PlayerSession? =
        if (minimized.value && playId == id) session else null

    /** A sessão voltou para a tela cheia: ela passa a ser dona de novo. */
    internal fun clearStored() {
        session = null
        playId = -1
        minimized.value = false
    }

    /** Para a reprodução e libera tudo (botão fechar da barra, ou novo play). */
    fun close() {
        val s = session
        session = null
        playId = -1
        minimized.value = false
        s?.let {
            it.minimizedByUser = false
            it.saveNow()
            it.releaseAll()
        }
    }
}

/**
 * Barra compacta do mini player, acima da pílula inferior: pôster pequeno,
 * título (+ episódio), play/pause, expandir (volta ao player cheio) e fechar
 * (para e libera o player). A posição avança por poll do ExoPlayer (o poll da
 * tela cheia morre junto com a composição dela ao minimizar).
 */
@Composable
fun MiniPlayerBar(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val session = MiniPlayerState.session ?: return
    var isPlaying by remember(session) { mutableStateOf(false) }
    var position by remember(session) { mutableLongStateOf(0L) }
    var duration by remember(session) { mutableLongStateOf(0L) }
    LaunchedEffect(session) {
        while (true) {
            val p = runCatching { session.currentPlayer() }.getOrNull()
            if (p == null) return@LaunchedEffect
            isPlaying = p.isPlaying
            position = p.currentPosition.coerceAtLeast(0L)
            duration = p.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard)
            .clickable(role = Role.Button, onClick = onExpand),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (session.posterUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = session.posterUrl, contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session.displayTitle, color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (duration > 0) "${formatTime(position)} / ${formatTime(duration)}" else "Tocando",
                    color = TextSecondary, fontSize = 12.sp, maxLines = 1,
                )
            }
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "Pausar" else "Assistir", tint = TextPrimary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button) { session.togglePlay() }.padding(2.dp),
            )
            Icon(
                Icons.Default.OpenInFull, "Voltar ao player", tint = TextPrimary,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClick = onExpand).padding(2.dp),
            )
            Icon(
                Icons.Default.Close, "Fechar e parar", tint = TextSecondary,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button) { MiniPlayerState.close() }.padding(2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Accent))
        }
    }
}
