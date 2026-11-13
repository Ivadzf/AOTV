package com.aotv.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.data.repository.ContinueArtwork
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.theme.*

/**
 * Card horizontal do "Continuar assistindo" no padrão do Nuvio (Lote 2, v1.10.0):
 * miniatura à esquerda, título da série, "T1 E1", título do episódio, barra de
 * progresso fina e "% assistido" embaixo. Filme mostra "Filme" no lugar de T/E
 * + título do episódio. Sem selo nem botão de play sobre a arte.
 *
 * MIGRAÇÃO: nenhuma. O card só muda o desenho — a fonte continua sendo o
 * [com.aotv.app.data.local.entity.MovieHistoryEntity] (mesma tabela Room,
 * mesmos campos, nada apagado nem recriado). O progresso, o episódio salvo e
 * a fonte salva chegam aqui pelos mesmos mapas de antes (`progress`,
 * `remaining`, `episode`, `artwork.episodeName`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueCard(
    item: MovieItem,
    onClick: () -> Unit,
    artwork: ContinueArtwork? = null,
    progress: Float? = null,
    remaining: Int? = null,
    episode: String = "",
    onLongClick: (() -> Unit)? = null,
    /** Modo compacto (Fase B): card mais estreito. */
    compact: Boolean = false,
) {
    val width = continueCardWidth(compact)
    val font = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val height = maxOf(100f, 100f + 12f * (font - 1f)).dp
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    var failed by remember(artwork?.imageUrl) { mutableStateOf(false) }
    val image = if (failed) artwork?.posterUrl.orEmpty().ifBlank { item.posterUrl }
        else artwork?.imageUrl.orEmpty().ifBlank { item.backdropUrl }.ifBlank { item.posterUrl }
    val isSeries = item.type == MediaType.SERIES
    // Linha 2: "T1 E1" (já vem formatado pelo chamador) ou "Filme" / "Série".
    val episodeLabel = when {
        episode.isNotBlank() -> episode.replace(':', ' ')
        isSeries -> "Série"
        else -> "Filme"
    }
    val pctText = when {
        progress != null -> "${(progress.coerceIn(0f, 1f) * 100).toInt()}% assistido"
        remaining != null && remaining > 0 -> "Faltam $remaining min"
        else -> ""
    }
    Row(
        Modifier.width(width).height(height).clip(shape).background(BackgroundCard)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Border, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniatura: still do episódio (série) ou backdrop/pôster (filme).
        AsyncImage(
            image, null,
            Modifier.width(108.dp).fillMaxHeight(),
            contentScale = ContentScale.Crop, onError = { failed = true },
        )
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.title.ifBlank { artwork?.title.orEmpty() },
                color = Color.White, fontSize = 15.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                episodeLabel, color = TextSecondary, fontSize = 13.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!artwork?.episodeName.isNullOrBlank()) Text(
                artwork!!.episodeName, color = TextSecondary, fontSize = 13.sp, lineHeight = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (progress != null) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .24f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
            if (pctText.isNotBlank()) Text(
                pctText, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
