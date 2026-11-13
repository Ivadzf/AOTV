package com.aotv.app.ui.movies

import com.aotv.app.data.local.entity.MovieHistoryEntity
import com.aotv.app.data.local.entity.WatchlistEntity
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem

/**
 * Modo aleatório (Lote 2, v1.10.0) — lógica pura do sorteio, sem Android nem rede.
 *
 * DECISÃO DO CONJUNTO (documentada aqui e no relatório): o sorteio só usa séries
 * que o usuário ACOMPANHA, ou seja, a união (sem duplicar por tmdbId) de:
 *  1. séries do Continuar assistindo ([MovieHistoryEntity] com type SERIES), e
 *  2. séries da Minha Lista ([WatchlistEntity] com type SERIES).
 * Filmes NUNCA entram — só episódios de série, sem exceção.
 *
 * O que ficou FORA, e por quê: séries que só têm episódios marcados como vistos
 * ([WatchedEpisodeEntity]) mas não estão nem no Continuar nem na Minha Lista.
 * (a) Não existe query "todas as séries com episódios vistos" no DAO; (b) na
 * prática, tocar um episódio grava o playback, então a série já cai no
 * Continuar assistindo — o conjunto não perde nada no uso real.
 *
 * DECISÃO DO SORTEIO: em 2 passos — sorteia a série (uniforme entre as
 * elegíveis), depois a temporada (uniforme 1..N) e o episódio dentro dela
 * (uniforme 1..total da temporada). Temporadas com mais episódios NÃO pesam
 * mais; é mais simples e só precisa de 2 chamadas ao TMDB (detalhe + temporada).
 * Sem chave TMDB ou com a rede falhando, o fallback toca o episódio salvo da
 * série sorteada (lastEpisode) ou T1 E1 — nunca inventa um episódio que pode
 * não existir.
 */
enum class RandomPoolSource { CONTINUE, WATCHLIST }

/** Uma série elegível para o sorteio. [sources] diz de onde ela veio. */
data class RandomSeriesCandidate(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String,
    val lastEpisode: String = "",
    val sources: Set<RandomPoolSource>,
) {
    fun toMovieItem(): MovieItem = MovieItem(
        tmdbId = tmdbId, embedId = tmdbId.toString(), title = title, overview = "",
        posterUrl = posterUrl, backdropUrl = "", year = "", rating = 0.0, type = MediaType.SERIES,
    )
}

/**
 * Monta o conjunto do sorteio. Filmes são descartados aqui (só SERIES entra).
 * A mesma série nas duas fontes aparece UMA vez, com as duas origens marcadas.
 */
fun buildRandomPool(
    history: List<MovieHistoryEntity>,
    watchlist: List<WatchlistEntity>,
    includeContinue: Boolean = true,
    includeWatchlist: Boolean = true,
): List<RandomSeriesCandidate> {
    val byId = LinkedHashMap<Int, RandomSeriesCandidate>()
    if (includeContinue) {
        history.filter { it.type == MediaType.SERIES.name && it.tmdbId > 0 }.forEach { h ->
            val prev = byId[h.tmdbId]
            byId[h.tmdbId] = RandomSeriesCandidate(
                tmdbId = h.tmdbId,
                title = h.title.ifBlank { prev?.title.orEmpty() },
                posterUrl = h.posterUrl.ifBlank { prev?.posterUrl.orEmpty() },
                lastEpisode = h.lastEpisode.ifBlank { prev?.lastEpisode.orEmpty() },
                sources = (prev?.sources.orEmpty()) + RandomPoolSource.CONTINUE,
            )
        }
    }
    if (includeWatchlist) {
        watchlist.filter { it.type == MediaType.SERIES.name && it.tmdbId > 0 }.forEach { w ->
            val prev = byId[w.tmdbId]
            byId[w.tmdbId] = RandomSeriesCandidate(
                tmdbId = w.tmdbId,
                title = w.title.ifBlank { prev?.title.orEmpty() },
                posterUrl = w.posterUrl.ifBlank { prev?.posterUrl.orEmpty() },
                lastEpisode = prev?.lastEpisode.orEmpty(),
                sources = (prev?.sources.orEmpty()) + RandomPoolSource.WATCHLIST,
            )
        }
    }
    return byId.values.toList()
}

/** "T2:E5" (ou "T2 E5") -> (2, 5). null quando não dá p/ ler. */
fun parseSeasonEpisode(lastEpisode: String): Pair<Int, Int>? {
    val m = Regex("T(\\d+)[: ]E(\\d+)").find(lastEpisode.trim()) ?: return null
    val season = m.groupValues[1].toIntOrNull() ?: return null
    val episode = m.groupValues[2].toIntOrNull() ?: return null
    return if (season > 0 && episode > 0) season to episode else null
}

/** Rótulo "T2 E5" p/ mostrar e p/ gravar como lastEpisode. */
fun formatSeasonEpisode(season: Int, episode: Int): String = "T$season E$episode"

/**
 * Sorteia (temporada, episódio). Temporada uniforme em 1..[seasonCount],
 * episódio uniforme em 1..[episodeCount]. Retorna null com contagem inválida.
 */
fun pickRandomEpisode(
    seasonCount: Int,
    episodeCount: Int,
    random: kotlin.random.Random = kotlin.random.Random.Default,
): Pair<Int, Int>? {
    if (seasonCount <= 0 || episodeCount <= 0) return null
    return (random.nextInt(seasonCount) + 1) to (random.nextInt(episodeCount) + 1)
}
