package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Modo aleatório (Lote 2, v1.10.0): sorteia um episódio de uma série que o
 * usuário acompanha e abre DIRETO no player (decisão do Davi — sem folha de
 * fontes antes do player), pelo mesmo caminho do "tocar episódio" do detalhe
 * (embedUrl vazio; o player busca as fontes do episódio nos addons).
 *
 * Fluxo do sorteio ([roll]): monta o conjunto (ver [buildRandomPool]),
 * sorteia a série, busca o nº de temporadas e os episódios da temporada
 * sorteada no TMDB e sorteia o episódio. Sem chave TMDB ou com erro de rede,
 * cai no fallback seguro: o episódio salvo da série, ou T1 E1.
 * [RandomState.Empty] nunca trava: é só um texto explicando que não há série
 * elegível (ou que tudo já foi visto, com "só não vistos" ligado).
 */
@HiltViewModel
class RandomEpisodeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    sealed interface RandomState {
        data object Idle : RandomState
        data object Rolling : RandomState
        /** Episódio sorteado, pronto p/ abrir direto no player. */
        data class Ready(
            val item: MovieItem,
            val season: Int,
            val episode: Int,
            val episodeTotal: Int,
            val episodeName: String = "",
        ) : RandomState
        /** Nada p/ sortear (ou tudo já visto). [message] é o texto p/ a tela. */
        data class Empty(val message: String) : RandomState
    }

    private val _state = MutableStateFlow<RandomState>(RandomState.Idle)
    val state: StateFlow<RandomState> = _state.asStateFlow()

    /** Volta p/ Idle depois que a tela consumiu o Ready/Empty (p/ não reabrir sozinho). */
    fun consume() {
        _state.value = RandomState.Idle
    }

    /** Sorteia um episódio. Chamadas durante um sorteio em andamento são ignoradas. */
    fun roll() {
        if (_state.value is RandomState.Rolling) return
        viewModelScope.launch {
            _state.value = RandomState.Rolling
            _state.value = runCatching { rollOnce() }.getOrElse {
                RandomState.Empty("Não deu para sortear agora. Verifique a conexão e tente de novo.")
            }
        }
    }

    private suspend fun rollOnce(): RandomState {
        val history = library.recentlyPlayed().first()
        val watchlist = library.watchlist().first()
        val pool = buildRandomPool(
            history, watchlist,
            includeContinue = prefs.randomIncludeContinue,
            includeWatchlist = prefs.randomIncludeWatchlist,
        )
        if (pool.isEmpty()) {
            return RandomState.Empty(
                "Nenhuma série para sortear. Comece a assistir uma série ou salve uma na " +
                    "Minha Lista — o sorteio usa as séries do Continuar assistindo e da Minha Lista.",
            )
        }
        val unwatchedOnly = prefs.randomUnwatchedOnly
        // Até 5 séries tentadas: com "só não vistos" ligado, uma série pode não
        // ter mais episódio novo e a tentativa passa p/ a próxima.
        val shuffled = pool.shuffled()
        for (candidate in shuffled.take(5)) {
            pickEpisode(candidate, unwatchedOnly)?.let { return it }
        }
        return RandomState.Empty(
            "Você já assistiu a todos os episódios das suas séries. " +
                "Desligue \"Só episódios não vistos\" em Configurações → Layout para sortear um repetido.",
        )
    }

    /**
     * Sorteia UM episódio de [candidate]. Null só quando [unwatchedOnly] e a
     * série não tem episódio não visto nas tentativas. Sem TMDB (sem chave ou
     * erro de rede), volta o episódio salvo ou T1 E1.
     */
    private suspend fun pickEpisode(candidate: RandomSeriesCandidate, unwatchedOnly: Boolean): RandomState.Ready? {
        val apiKey = prefs.tmdbApiKey
        val fallback = fallbackPick(candidate)
        if (apiKey.isBlank()) return fallback
        val seasonCount = runCatching {
            tmdb.getSeriesDetail(candidate.tmdbId, apiKey = apiKey).numberOfSeasons
        }.getOrNull()?.takeIf { it > 0 } ?: return fallback
        // Até 6 sorteios na série: tenta achar um episódio não visto. Com "só
        // não vistos" ligado, desiste da série (null) se só sair repetido; sem
        // ele, aceita um repetido a partir da 3ª tentativa.
        repeat(6) { attempt ->
            val season = (1..seasonCount).random()
            val episodes = runCatching {
                tmdb.getSeason(candidate.tmdbId, season, apiKey = apiKey).episodes
            }.getOrNull().orEmpty()
            if (episodes.isEmpty()) return@repeat
            val ep = episodes.random()
            val watched = runCatching {
                library.isEpisodeWatched(candidate.tmdbId, season, ep.episodeNumber)
            }.getOrDefault(false)
            if (!watched) {
                return RandomState.Ready(
                    item = candidate.toMovieItem(), season = season,
                    episode = ep.episodeNumber, episodeTotal = episodes.size,
                    episodeName = ep.name,
                )
            }
            if (!unwatchedOnly && attempt >= 2) {
                return RandomState.Ready(
                    item = candidate.toMovieItem(), season = season,
                    episode = ep.episodeNumber, episodeTotal = episodes.size,
                    episodeName = ep.name,
                )
            }
        }
        return null
    }

    /** Fallback sem TMDB: episódio salvo da série, ou T1 E1 (total desconhecido = 0). */
    private fun fallbackPick(candidate: RandomSeriesCandidate): RandomState.Ready {
        val saved = parseSeasonEpisode(candidate.lastEpisode)
        val (season, episode) = saved ?: (1 to 1)
        return RandomState.Ready(
            item = candidate.toMovieItem(), season = season,
            episode = episode, episodeTotal = 0,
        )
    }
}
