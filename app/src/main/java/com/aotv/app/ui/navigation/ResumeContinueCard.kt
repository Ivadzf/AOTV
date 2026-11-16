// ─── ui/navigation/ResumeContinueCard.kt (Lote 15 — Bloco 4, item 6, v1.25.0) ───
// Mini cartão "Continuar assistindo" ao abrir o app, no formato do Nuvio
// ("Continue de onde parou — título, T/E • nome do episódio", com progresso
// e botão play). Flutua acima da pílula, some sozinho em ~5 s (o
// auto-dismiss vive em MoviesNavigation) e o toque retoma a reprodução.
//
// Fonte: o mesmo dado da fileira Continuar assistindo —
// MinhaListaViewModel.recentlyPlayed() (LibraryRepository.recentlyPlayed(),
// histórico Room, com o oculto por perfil filtrado, igual à fileira) + a
// arte de recentArtwork. O vidro é o mesmo tratamento da pílula (GlassBg +
// borda sutil, com o toggle `pref_panel_blur`); blur real não alcança aqui
// (o RenderEffect desfoca o próprio conteúdo, não o que está atrás).

package com.aotv.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.data.local.entity.MovieHistoryEntity
import com.aotv.app.data.local.posterKey
import com.aotv.app.data.repository.ContinueArtwork
import com.aotv.app.domain.model.MediaType
import com.aotv.app.ui.live.LivePlayerViewModel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BorderSubtle
import com.aotv.app.ui.theme.GlassBg
import com.aotv.app.ui.theme.TextOnLight
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

// ─── Funções puras (mesmos critérios da fileira Continuar assistindo) ───────

/** Fração assistida (0..1); null sem duração conhecida. */
fun resumeProgressFraction(h: MovieHistoryEntity): Float? =
    if (h.durationMs > 0) (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else null

/** Minutos restantes; 0 sem duração ou já no fim. */
fun resumeRemainingMin(h: MovieHistoryEntity): Int =
    if (h.durationMs > h.positionMs) ((h.durationMs - h.positionMs) / 60000L).toInt() else 0

/**
 * Terminado some da fileira (MoviesHomeViewModel: posição >= 90% da
 * duração) — o cartão pula p/ o próximo item inacabado em vez de mostrar
 * um título já visto. P/ série terminada a fileira avança p/ o próximo
 * episódio (precisa do TMDB); o cartão não resolve o próximo, só pula.
 */
fun isResumeFinished(h: MovieHistoryEntity): Boolean =
    h.durationMs > 0 && h.positionMs >= h.durationMs * 0.9

/** "T1 E3" (série), "Filme", "Série" ou "Ao vivo" — igual ao ContinueCard. */
fun resumeEpisodeLabel(h: MovieHistoryEntity): String = when {
    h.lastEpisode.isNotBlank() -> h.lastEpisode.replace(':', ' ')
    h.type == MediaType.SERIES.name -> "Série"
    h.type == LivePlayerViewModel.LIVE_TYPE -> "Ao vivo"
    else -> "Filme"
}

/**
 * "T1:E3" -> (1, 3); null sem episódio válido. Aceita as variantes que
 * aparecem no histórico ("T1 E3", "S1E3", "1x03"): sem isso a série abre
 * com temporada/episódio 0 e as legendas externas voltam vazias (Lote 17
 * item 14, causa raiz 3). Não inventa episódio: sem formato válido, null.
 */
fun parseResumeEpisode(lastEpisode: String): Pair<Int, Int>? {
    val patterns = listOf(
        Regex("T(\\d+):E(\\d+)"),
        Regex("T(\\d+) E(\\d+)"),
        Regex("S(\\d+)E(\\d+)", RegexOption.IGNORE_CASE),
        Regex("(\\d+)x(\\d+)", RegexOption.IGNORE_CASE),
    )
    for (pattern in patterns) {
        val m = pattern.find(lastEpisode) ?: continue
        val season = m.groupValues[1].toIntOrNull() ?: continue
        val episode = m.groupValues[2].toIntOrNull() ?: continue
        if (season <= 0 || episode <= 0) continue
        return season to episode
    }
    return null
}

/** Chave da arte em recentArtwork p/ esta linha do histórico. */
fun resumeArtworkKey(h: MovieHistoryEntity): String {
    val type = runCatching { MediaType.valueOf(h.type) }.getOrDefault(MediaType.MOVIE)
    return posterKey(type, h.tmdbId)
}

// ─── Cartão ─────────────────────────────────────────────────────────────────

/**
 * Mini cartão flutuante: miniatura + "Continue de onde parou" + título +
 * episódio/nome + barra de progresso, botão play (retoma) e X (dispensa).
 * O toque no corpo também retoma; o toque fora dispensa (scrim em
 * MoviesNavigation). Vidro igual ao da pílula ([GlassBg] + borda sutil).
 */
@Composable
fun ResumeContinueCard(
    item: MovieHistoryEntity,
    artwork: ContinueArtwork?,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    /** Vidro fosco (Lote 4): toggle `pref_panel_blur`. */
    frosted: Boolean = false,
) {
    val progress = resumeProgressFraction(item)
    val episodeLabel = resumeEpisodeLabel(item)
    val episodeName = artwork?.episodeName.orEmpty()
    val episodeLine = when {
        episodeLabel.isNotBlank() && episodeName.isNotBlank() -> "$episodeLabel • $episodeName"
        episodeLabel.isNotBlank() -> episodeLabel
        episodeName.isNotBlank() -> episodeName
        else -> ""
    }
    val pctText = when {
        progress != null -> "${(progress * 100).toInt()}% assistido"
        resumeRemainingMin(item) > 0 -> "Faltam ${resumeRemainingMin(item)} min"
        else -> ""
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(if (frosted) GlassBg.copy(alpha = 0.62f) else GlassBg)
            .border(1.dp, BorderSubtle, shape)
            .clickable(role = Role.Button, onClick = onResume)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            artwork?.imageUrl.orEmpty().ifBlank { item.posterUrl },
            null,
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Continue de onde parou", color = TextSecondary,
                fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.title, color = TextPrimary,
                fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (episodeLine.isNotBlank()) Text(
                episodeLine, color = TextSecondary,
                fontSize = 12.sp, lineHeight = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .24f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
            if (pctText.isNotBlank()) Text(
                pctText, color = TextSecondary,
                fontSize = 11.sp, lineHeight = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onResume,
            modifier = Modifier.size(40.dp).background(Accent, CircleShape),
        ) {
            Icon(Icons.Default.PlayArrow, "Continuar", tint = TextOnLight, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Dispensar", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
