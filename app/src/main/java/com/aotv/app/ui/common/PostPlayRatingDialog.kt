package com.aotv.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.remote.AniListService
import com.aotv.app.data.remote.TraktService
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Diálogo de avaliação exibido ao fim de um título (v1.17.0).
 * Escala 1–10 em duas fileiras de 5 estrelas. "Pular" fecha sem enviar.
 * [onRate] recebe a nota (1–10) e é chamado quando o usuário confirma.
 * [onDismiss] é chamado em "Pular" ou ao fechar o diálogo.
 *
 * Quem mostra é o player: [com.aotv.app.ui.movies.PlayerProgressViewModel]
 * expõe o pedido quando o título passa de 90% e a avaliação pós-filme
 * está ligada; o player observa e abre este diálogo com o título atual.
 */
@Composable
fun PostPlayRatingDialog(
    movieTitle: String,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("O que você achou?", color = TextPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = movieTitle,
                    color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2,
                )
                Spacer(Modifier.height(20.dp))
                // Estrelas 1–10 em duas fileiras de 5.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(1..5, 6..10).forEach { range ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            range.forEach { n ->
                                val filled = n <= selected
                                Icon(
                                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$n estrelas",
                                    tint = if (filled) Accent else TextTertiary,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { selected = n },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (selected > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (selected) {
                            in 1..2 -> "Não curtiu"
                            in 3..4 -> "Passou o tempo"
                            in 5..6 -> "Foi razoável"
                            in 7..8 -> "Gostou bastante"
                            in 9..10 -> "Adorou!"
                            else -> ""
                        },
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (selected > 0) onRate(selected) }, enabled = selected > 0) {
                Text("Enviar", color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Pular", color = TextSecondary) }
        },
    )
}

/**
 * Envia a nota para os trackers conectados.
 *
 * - Trakt (escala 1–10, nativa): filme via [TraktService.rateMovie],
 *   episódio via [TraktService.rateEpisode]. Só envia com o Trakt ligado
 *   (o próprio serviço ignora quando desligado).
 * - MDBList é read-only (só consome notas) — não recebe nota.
 * - AniList (escala 0–10): só quando o id AniList do título é conhecido
 *   ([aniListMediaId]) e o título é um filme — marcar uma série como
 *   COMPLETED no meio dela estaria errado, e o player não sabe o total de
 *   episódios. Sem id conhecido, o AniList é pulado sem erro.
 */
suspend fun sendRatingToAllTrackers(
    tmdbId: Int?,
    title: String,
    year: Int?,
    rating: Int, // 1–10
    isMovie: Boolean,
    season: Int = 0,
    episode: Int = 0,
    traktService: TraktService,
    aniListService: AniListService,
    aniListMediaId: Int? = null,
) {
    if (traktService.isEnabled) {
        if (isMovie) traktService.rateMovie(tmdbId, title, year, rating)
        else traktService.rateEpisode(tmdbId, title, season, episode, rating)
    }
    if (isMovie && aniListService.isEnabled && aniListMediaId != null) {
        aniListService.markCompleted(aniListMediaId, 1, rating.toDouble())
    }
}
