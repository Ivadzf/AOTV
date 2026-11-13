package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.domain.model.EpisodeItem
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

// ── Navegador de episódios (Lote 2, v1.10.0) ───────────────────────────────
// Busca + ordenação + Rating Map, construídos SOBRE os cards de episódio da
// Fase B (EpisodeRowCard): nada aqui recria card — só filtra/ordena a lista
// que os cards já exibem e pinta o mapa por cima dela.
//
// DECISÕES (registradas p/ o relatório):
// - ESCOPO = TEMPORADA SELECIONADA. As temporadas carregam sob demanda
//   (loadSeason, uma chamada TMDB por temporada); buscar "em todas" exigiria
//   baixar todas as temporadas de uma vez e mudaria o carregamento da tela.
//   A busca filtra a temporada do chip selecionado, sem recarregar nada.
// - ORDENAÇÃO = 3 modos: por número (padrão, a ordem de exibição), por data
//   de exibição (mais antiga primeiro; sem data vai p/ o fim) e por nota
//   (maior primeiro; sem nota vai p/ o fim). Não há "por duração" nem "por
//   nome": duração o TMDB quase nunca preenche por episódio, e nome é o que
//   a busca já faz.
// - BUSCA = título OU sinopse (contém, sem diferenciar maiúsculas) OU número
//   do episódio: "3", "e3", "ep 3" e "t1 e3" acham o E3 (vale o ÚLTIMO
//   número da consulta, p/ "t1 e3" não casar o E1). Filtra em memória, sem
//   recarregar os dados da série.

/** Ordenação do navegador de episódios. O id é o que fica guardado (string). */
enum class EpisodeSort(val id: String, val label: String) {
    NUMBER("number", "Nº"),
    AIR_DATE("air_date", "Data"),
    RATING("rating", "Nota");

    companion object {
        fun fromId(id: String): EpisodeSort = entries.firstOrNull { it.id == id } ?: NUMBER
    }
}

/**
 * Filtra os episódios pela consulta, em memória (sem recarregar a série).
 * Casa título, sinopse ou número do episódio (ver o comentário do arquivo).
 */
fun filterEpisodes(episodes: List<EpisodeItem>, query: String): List<EpisodeItem> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return episodes
    // Último número da consulta ("3", "e3", "ep 3", "t1 e3" → 3).
    val number = Regex("""\d+""").findAll(q).lastOrNull()?.value?.toIntOrNull()
    return episodes.filter { ep ->
        ep.name.lowercase().contains(q) ||
            ep.overview.lowercase().contains(q) ||
            (number != null && ep.episodeNumber == number)
    }
}

/** Ordena os episódios (já filtrados) pelo modo escolhido. */
fun sortEpisodes(episodes: List<EpisodeItem>, sort: EpisodeSort): List<EpisodeItem> = when (sort) {
    EpisodeSort.NUMBER -> episodes.sortedBy { it.episodeNumber }
    // Sem data vai p/ o fim (blank = true ordena depois), senão por data crescente.
    EpisodeSort.AIR_DATE -> episodes.sortedWith(compareBy({ it.airDate.isBlank() }, { it.airDate }))
    // Sem nota vai p/ o fim (rating <= 0 ordena depois), senão maior nota primeiro.
    EpisodeSort.RATING -> episodes.sortedWith(compareBy({ it.rating <= 0 }, { -it.rating }))
}

// ── Rating Map (Lote 2 item 4) ─────────────────────────────────────────────
// Uma célula por episódio da lista exibida (mesma ordem dos cards), pintada
// pela nota do TMDB (`vote_average`, 0–10). Tocar a célula abre o episódio
// (o mesmo onSelect do card).
//
// ESCALA DE CORES (limites explícitos, em `ratingColor`):
//   sem nota (rating <= 0)  → cinza neutro  #5A5F6A — NÃO é nota baixa:
//                             o TMDB devolve 0.0 quando ninguém votou, e
//                             pintar de vermelho diria "ruim" sem ninguém
//                             ter avaliado.
//   nota < 5,0              → vermelho     #D92D20
//   5,0 – 6,4               → laranja      #DC6803
//   6,5 – 7,4               → amarelo      #CA8A04 (escuro, p/ o nº branco ler)
//   7,5 – 8,4               → verde        #2E9E5B
//   nota >= 8,5             → turquesa     #009E94 (o turquesa do app)
// O mapa fica NO navegador de episódios, acima da lista (não no hero do
// detalhe): ele é a visão da temporada, célula a célula, na mesma ordem dos
// cards — e some junto quando as notas são desligadas (item 5), porque o
// mapa É a nota, em cor.

/** Cor da nota no Rating Map, pela escala documentada acima. */
fun ratingColor(rating: Double): Color = when {
    rating <= 0 -> Color(0xFF5A5F6A) // sem nota: neutro, não vermelho
    rating < 5.0 -> Color(0xFFD92D20)
    rating < 6.5 -> Color(0xFFDC6803)
    rating < 7.5 -> Color(0xFFCA8A04)
    rating < 8.5 -> Color(0xFF2E9E5B)
    else -> Color(0xFF009E94)
}

/** Rótulo da faixa de nota, p/ a legenda do mapa (mesmos limites de [ratingColor]). */
fun ratingBandLabel(rating: Double): String = when {
    rating <= 0 -> "Sem nota"
    rating < 5.0 -> "< 5,0"
    rating < 6.5 -> "5,0–6,4"
    rating < 7.5 -> "6,5–7,4"
    rating < 8.5 -> "7,5–8,4"
    else -> "≥ 8,5"
}

/** Faixas da legenda, na ordem do mapa (da melhor nota p/ a pior + sem nota). */
private val RATING_LEGEND_BANDS = listOf(9.0, 8.0, 7.0, 6.0, 4.0, 0.0)

/**
 * Mapa de notas da lista exibida: uma célula por episódio, com o número,
 * pintada por [ratingColor]. Tocar a célula abre o episódio (onSelect).
 * Abaixo, a legenda com as 6 faixas da escala.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingMap(
    episodes: List<EpisodeItem>,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(episodes, key = { it.episodeNumber }) { ep ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ratingColor(ep.rating))
                        .clickable { onEpisodeClick(ep) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${ep.episodeNumber}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ManropeFontFamily,
                    )
                }
            }
        }
        // Legenda: bolinha + faixa, na ordem da escala.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RATING_LEGEND_BANDS.forEach { band ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(ratingColor(band)),
                    )
                    Text(
                        ratingBandLabel(band),
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ManropeFontFamily,
                    )
                }
            }
            // Nota média da lista (só episódios avaliados contam).
            val rated = episodes.filter { it.rating > 0 }
            if (rated.isNotEmpty()) {
                Text(
                    "Média %.1f".format(rated.map { it.rating }.average()),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}
