package com.aotv.app.ui.lote24

import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import kotlin.random.Random

/**
 * Lote 24 (v1.34.0), item 5 — MK Channel: "liga e já tem algo tocando".
 *
 * Três fontes, sem escolher nada:
 * - Up next: o primeiro título do Continuar assistindo (o próximo a retomar).
 * - Watchlist: um título aleatório da Minha Lista.
 * - Maratona: o próximo episódio da série em andamento (o episódio seguinte
 *   ao marcado no Continuar; sem série em andamento, a primeira série da
 *   Minha Lista, T1 E1).
 *
 * Tocar direto reusa o player existente: episódio vai pelo `onPlayEpisode`
 * (o mesmo caminho do modo aleatório — o player resolve as fontes); filme
 * abre o detalhe, de onde o play resolve as fontes como sempre. Não há
 * autoplay de filme sem passar pelo detalhe porque o play direto precisa
 * das fontes do título, que só o detalhe resolve.
 */
enum class MkChannelSource(val id: String, val label: String) {
    UP_NEXT("up_next", "Up next"),
    WATCHLIST("watchlist", "Minha Lista"),
    MARATHON("marathon", "Maratona");

    companion object {
        fun fromId(id: String): MkChannelSource? = entries.firstOrNull { it.id == id }
    }
}

/** O que o MK Channel vai tocar: item + de onde veio + episódio (séries). */
data class MkChannelTarget(
    val item: MovieItem,
    val source: MkChannelSource,
    val label: String,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeTotal: Int = 0,
)

/** "T2 E5" (selo do Continuar) p/ (temporada, episódio); null quando não casa. */
fun parseContinueEpisodeLabel(label: String?): Pair<Int, Int>? {
    if (label.isNullOrBlank()) return null
    val m = Regex("T(\\d+)\\s*E(\\d+)").find(label.trim()) ?: return null
    val season = m.groupValues[1].toIntOrNull() ?: return null
    val episode = m.groupValues[2].toIntOrNull() ?: return null
    if (season <= 0 || episode <= 0) return null
    return season to episode
}

/**
 * Resolve as 3 fontes do MK Channel a partir do que a Home já tem.
 * [continueEpisode] é o mapa tmdbId -> selo ("T2 E5") do UiState.
 * [watchlist] são os itens da Minha Lista como [MovieItem].
 * [random] injetável p/ teste (o sorteio da watchlist usa ele).
 */
fun pickMkChannelTargets(
    continueWatching: List<MovieItem>,
    continueEpisode: Map<Int, String> = emptyMap(),
    watchlist: List<MovieItem> = emptyList(),
    random: Random = Random.Default,
): Map<MkChannelSource, MkChannelTarget?> {
    val upNextItem = continueWatching.firstOrNull()
    val upNext = upNextItem?.let { item ->
        val ep = parseContinueEpisodeLabel(continueEpisode[item.tmdbId])
        MkChannelTarget(
            item = item,
            source = MkChannelSource.UP_NEXT,
            label = if (ep != null) "Continuar T${ep.first} E${ep.second}" else "Continuar assistindo",
            season = ep?.first ?: 0,
            episode = ep?.second ?: 0,
        )
    }
    val watchlistTarget = watchlist.takeIf { it.isNotEmpty() }?.let { list ->
        val picked = list[random.nextInt(list.size)]
        MkChannelTarget(item = picked, source = MkChannelSource.WATCHLIST, label = "Sorteado da Minha Lista")
    }
    val inProgressSeries = continueWatching.firstOrNull { it.type == MediaType.SERIES }
    val marathon = if (inProgressSeries != null) {
        val ep = parseContinueEpisodeLabel(continueEpisode[inProgressSeries.tmdbId])
        val nextSeason = ep?.first ?: 1
        val nextEpisode = (ep?.second ?: 0) + 1
        MkChannelTarget(
            item = inProgressSeries,
            source = MkChannelSource.MARATHON,
            label = "Próximo episódio — T${nextSeason} E${nextEpisode}",
            season = nextSeason,
            episode = nextEpisode,
        )
    } else {
        watchlist.firstOrNull { it.type == MediaType.SERIES }?.let { series ->
            MkChannelTarget(
                item = series,
                source = MkChannelSource.MARATHON,
                label = "Começar maratona — T1 E1",
                season = 1,
                episode = 1,
            )
        }
    }
    return mapOf(
        MkChannelSource.UP_NEXT to upNext,
        MkChannelSource.WATCHLIST to watchlistTarget,
        MkChannelSource.MARATHON to marathon,
    )
}
