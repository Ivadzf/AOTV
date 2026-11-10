package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.entity.MovieHistoryEntity
import com.aotv.app.data.local.entity.WatchlistEntity
import com.aotv.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da tela "Minha Lista" do modo Filmes & Séries.
 *
 * Expõe os dois Flows da BIBLIOTECA usados pela tela:
 *  - [watchlist]: itens salvos (B — Minha Lista)
 *  - [recentlyPlayed]: reproduções recentes (C — Continuar assistindo)
 */
@HiltViewModel
class MinhaListaViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val continueArt: com.aotv.app.data.repository.ContinueArtworkRepository,
) : ViewModel() {

    val watchlist: StateFlow<List<WatchlistEntity>> =
        library.watchlist().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val recentlyPlayed: StateFlow<List<MovieHistoryEntity>> =
        library.recentlyPlayed().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentArtwork = recentlyPlayed.mapLatest { list ->
        coroutineScope {
            list.take(20).map { h -> async {
                val type = runCatching { com.aotv.app.domain.model.MediaType.valueOf(h.type) }
                    .getOrDefault(com.aotv.app.domain.model.MediaType.MOVIE)
                val item = com.aotv.app.domain.model.MovieItem(h.tmdbId, "", h.title, "", h.posterUrl, "", "", 0.0, type = type)
                com.aotv.app.data.local.posterKey(item) to continueArt.resolve(item, h.lastEpisode)
            } }.awaitAll().toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Remove um item de "Continuar assistindo". */
    fun removeFromContinue(tmdbId: Int, type: String) {
        viewModelScope.launch { library.removePlayback(tmdbId, type) }
    }

    /** Tira um título salvo da Minha Lista (segurar no card do grid). */
    fun removeFromWatchlist(tmdbId: Int, type: String) {
        viewModelScope.launch { library.removeFromList(tmdbId, type) }
        trakt.watchlistChanged(tmdbId, type, added = false)
    }
}
