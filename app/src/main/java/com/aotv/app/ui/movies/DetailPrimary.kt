package com.aotv.app.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem

/**
 * Botão principal do detalhe (Assistir, Continuar, Próximo episódio, Próxima temporada).
 * Mesma regra no celular e na TV, só o desenho muda.
 */
internal class DetailPrimary(
    val label: String,
    /** Fontes e histórico prontos. Antes disso o clique não faz nada. */
    val ready: Boolean,
    /** Pode tocar agora (pronto, com fonte e não é só outro filme). */
    val enabled: Boolean,
    val play: () -> Unit,
    /** Todas as fontes do filme, na ordem, para o seletor do player. */
    val playerSources: List<MoviePlayerSource>,
    /** Série terminada e a primeira série relacionada, para o "Próximo seriado". */
    val nextSeries: MovieItem?,
    val numberOfSeasons: Int,
)

@Composable
internal fun rememberDetailPrimary(
    item: MovieItem,
    state: MovieDetailViewModel.UiState,
    viewModel: MovieDetailViewModel,
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit,
): DetailPrimary {
    val isSeries = item.type == MediaType.SERIES
    val playerSources = state.stremioStreams.map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) }
    val numberOfSeasons = state.series?.numberOfSeasons ?: 1
    val seriesFinished = isSeries && state.resumeDurationMs > 0 && state.resumePositionMs >= state.resumeDurationMs * 0.9
    val hasAnySource = if (isSeries) state.seasons.values.any { it.isNotEmpty() } else playerSources.isNotEmpty()

    // Há progresso salvo para retomar? (posição entre 10 s e 95% da duração, com fonte salva)
    val canResume = state.resumeSourceUrl.isNotBlank() && state.resumePositionMs > 10_000 &&
        (state.resumeDurationMs <= 0 || state.resumePositionMs < state.resumeDurationMs * 0.95)

    // Episódio terminado leva ao PRÓXIMO episódio, ou à próxima temporada no fim dela.
    val histMatch = Regex("T(\\d+):E(\\d+)").find(state.resumeLastEpisode)
    val histSeason = histMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val histEpisode = histMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
    val episodeFinished = isSeries && histSeason > 0 &&
        state.resumeDurationMs > 0 && state.resumePositionMs >= state.resumeDurationMs * 0.9
    val epsInHistSeason = state.seasons[histSeason]?.size ?: 0
    val nextEpisodeInSeason = if (episodeFinished && epsInHistSeason > 0 && histEpisode in 1 until epsInHistSeason)
        histEpisode + 1 else 0
    val nextSeasonNumber = if (episodeFinished && nextEpisodeInSeason == 0 && histSeason in 1 until numberOfSeasons)
        histSeason + 1 else 0

    // Abre os episódios na temporada que a pessoa está vendo (antes abria sempre na 1ª).
    LaunchedEffect(state.watchingSeason) {
        val w = state.watchingSeason
        if (w > 1 && state.seasons[w] == null) viewModel.loadSeason(item.tmdbId, w)
    }

    // Só libera com detalhe, addons e histórico prontos. Sem isso o Continuar saía com a lista
    // de fontes pela metade e o player gravava posição 0 por cima do progresso real.
    val ready = !state.sourcesLoading && !state.stremioLoading && state.resumeLoaded
    // Todas as fontes testadas são de outro filme com nome parecido. Sem retomada, não toca.
    val onlyWrongTitle = !isSeries && ready && state.allWrongTitle && !canResume
    val enabled = ready && hasAnySource && !onlyWrongTitle

    val label = when {
        !ready -> "Carregando fontes…"
        !hasAnySource -> "Indisponível"
        onlyWrongTitle -> "Só achei outro filme"
        nextEpisodeInSeason > 0 -> "Próximo episódio · T$histSeason:E$nextEpisodeInSeason"
        nextSeasonNumber > 0 -> "Próxima temporada · T$nextSeasonNumber"
        canResume -> {
            val s = state.resumePositionMs / 1000
            val ep = if (state.resumeLastEpisode.isNotBlank()) "${state.resumeLastEpisode} · " else ""
            "Continuar · $ep%d:%02d".format(s / 60, s % 60)
        }
        else -> "Assistir"
    }

    fun play() {
        if (!enabled) return
        viewModel.recordPlayback()
        if (nextEpisodeInSeason > 0) {
            onPlayEpisode(item.tmdbId, histSeason, nextEpisodeInSeason, epsInHistSeason)
            return
        }
        if (nextSeasonNumber > 0) {
            val eps = state.seasons[nextSeasonNumber]
            if (eps == null) { viewModel.loadSeason(item.tmdbId, nextSeasonNumber); return }
            val ep = eps.firstOrNull() ?: return
            onPlayEpisode(item.tmdbId, nextSeasonNumber, ep.episodeNumber, eps.size)
            return
        }
        if (canResume) {
            val total = if (histSeason > 0) state.seasons[histSeason]?.size ?: 0 else 0
            // A fonte salva PRECISA estar na lista do player, senão ele acha que toca a fonte 0
            // e a troca automática pula para outra coisa.
            val saved = playerSources.any { it.url == state.resumeSourceUrl }
            val sources = if (saved) playerSources
                else listOf(MoviePlayerSource("Fonte salva", state.resumeSourceUrl)) + playerSources
            onPlayDirect(state.resumeSourceUrl, state.resumePositionMs, state.resumeLastEpisode, sources, histSeason, histEpisode, total)
            return
        }
        if (isSeries) {
            // Série sem progresso toca o 1º episódio da temporada escolhida.
            val ep = state.seasons[state.selectedSeason]?.firstOrNull()
            if (ep != null) onPlayEpisode(item.tmdbId, ep.seasonNumber, ep.episodeNumber, state.seasons[ep.seasonNumber]?.size ?: 0)
            return
        }
        val first = playerSources.getOrNull(viewModel.bestSourceIndex()) ?: return
        onPlayDirect(first.url, 0, "", playerSources, 0, 0, 0)
    }

    return DetailPrimary(
        label = label, ready = ready, enabled = enabled, play = ::play,
        playerSources = playerSources,
        nextSeries = if (seriesFinished) state.related.firstOrNull { it.type == MediaType.SERIES } else null,
        numberOfSeasons = numberOfSeasons,
    )
}
