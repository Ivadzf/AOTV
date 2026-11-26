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
    /** Mesmo play, mas pela fonte travada (guarda de duração confirmada, v1.21.0). */
    val playLocked: () -> Unit,
    /** URL que o botão principal tocaria num filme (null p/ séries ou sem fonte). */
    val pendingMovieUrl: String?,
    /** Todas as fontes do filme, na ordem, para o seletor do player. */
    val playerSources: List<MoviePlayerSource>,
    /** Série terminada e a primeira série relacionada, para o "Próximo seriado". */
    val nextSeries: MovieItem?,
    val numberOfSeasons: Int,
    /** Há progresso salvo para retomar (Lote 2 item 9d: filme sem retomada abre a folha de fontes). */
    val canResume: Boolean,
)

@Composable
internal fun rememberDetailPrimary(
    item: MovieItem,
    state: MovieDetailViewModel.UiState,
    viewModel: MovieDetailViewModel,
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit,
    /** Play com a fonte travada (guarda de duração confirmada). Padrão = o play normal. */
    onPlayDirectLocked: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit = onPlayDirect,
): DetailPrimary {
    val isSeries = item.type == MediaType.SERIES
    val playerSources = state.stremioStreams.map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) }
    val numberOfSeasons = state.series?.numberOfSeasons ?: 1
    val seriesFinished = isSeries && state.resumeDurationMs > 0 && state.resumePositionMs >= state.resumeDurationMs * 0.9
    val hasAnySource = if (isSeries) state.seasons.values.any { it.isNotEmpty() } else playerSources.isNotEmpty()

    // Há progresso salvo para retomar? (posição entre 10 s e 95% da duração).
    // Lote v1.46.0 item 10: o Continuar NÃO exige mais fonte salva — progresso de outro
    // aparelho (só posição + episódio) também retoma; sem fonte, o play resolve as fontes
    // na hora (ver playInternal). Com fonte salva, igual a antes.
    val canResume = state.resumePositionMs > 10_000 &&
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

    fun playInternal(locked: Boolean) {
        if (!enabled) return
        // Fonte travada: o player abre com userLockedSource (não auto-troca
        // por lentidão nem por duração — o usuário já confirmou esta fonte).
        val direct = if (locked) onPlayDirectLocked else onPlayDirect
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
            // Sem fonte salva (Lote v1.46.0 item 10): resolve na hora pelo caminho normal —
            // a série abre o episódio (o player busca as fontes do episódio e retoma a
            // posição salva sozinho) e o filme toca a melhor fonte a partir da posição salva.
            // Nunca toca direto sem fonte.
            if (state.resumeSourceUrl.isBlank() && isSeries && histSeason > 0 && histEpisode > 0) {
                onPlayEpisode(item.tmdbId, histSeason, histEpisode, total)
                return
            }
            if (state.resumeSourceUrl.isBlank() && !isSeries) {
                val first = playerSources.getOrNull(viewModel.bestSourceIndex()) ?: return
                direct(first.url, state.resumePositionMs, state.resumeLastEpisode, playerSources, 0, 0, 0)
                return
            }
            if (state.resumeSourceUrl.isNotBlank()) {
                // A fonte salva PRECISA estar na lista do player, senão ele acha que toca a fonte 0
                // e a troca automática pula para outra coisa.
                val saved = playerSources.any { it.url == state.resumeSourceUrl }
                val sources = if (saved) playerSources
                    else listOf(MoviePlayerSource("Fonte salva", state.resumeSourceUrl)) + playerSources
                direct(state.resumeSourceUrl, state.resumePositionMs, state.resumeLastEpisode, sources, histSeason, histEpisode, total)
                return
            }
            // Série com posição mas sem episódio no histórico: segue p/ o fluxo normal abaixo.
        }
        if (isSeries) {
            // Série sem progresso toca o 1º episódio da temporada escolhida.
            val ep = state.seasons[state.selectedSeason]?.firstOrNull()
            if (ep != null) onPlayEpisode(item.tmdbId, ep.seasonNumber, ep.episodeNumber, state.seasons[ep.seasonNumber]?.size ?: 0)
            return
        }
        val first = playerSources.getOrNull(viewModel.bestSourceIndex()) ?: return
        direct(first.url, 0, "", playerSources, 0, 0, 0)
    }

    fun play() { playInternal(locked = false) }

    /** Mesmo play, mas a fonte sai travada (o player não auto-troca por duração). */
    fun playLocked() { playInternal(locked = true) }

    /**
     * URL que o botão principal tocaria num filme (retomada ou melhor fonte).
     * Null p/ séries (a guarda de duração não vale p/ episódios, como a
     * checagem de outro filme) ou sem fonte. A tela usa p/ perguntar ANTES
     * de tocar, quando a duração do arquivo destoa da do título.
     */
    fun pendingMovieUrl(): String? {
        if (isSeries || !enabled) return null
        // Lote v1.46.0 item 10: sem fonte salva, a retomada usa a melhor fonte (o play
        // direto já faz isso) — antes voltava a URL vazia e a guarda de duração não tinha o que checar.
        if (canResume) return state.resumeSourceUrl.ifBlank { playerSources.getOrNull(viewModel.bestSourceIndex())?.url }
        return playerSources.getOrNull(viewModel.bestSourceIndex())?.url
    }

    return DetailPrimary(
        label = label, ready = ready, enabled = enabled, play = ::play,
        playLocked = ::playLocked, pendingMovieUrl = pendingMovieUrl(),
        playerSources = playerSources,
        nextSeries = if (seriesFinished) state.related.firstOrNull { it.type == MediaType.SERIES } else null,
        numberOfSeasons = numberOfSeasons,
        canResume = canResume,
    )
}
