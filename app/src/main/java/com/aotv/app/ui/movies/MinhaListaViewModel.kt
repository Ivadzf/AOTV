package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.entity.MovieHistoryEntity
import com.aotv.app.data.local.entity.WatchlistEntity
import com.aotv.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
 *
 * E o Calendário (Lote 2, item 6 — v1.10.0): as datas dos próximos episódios
 * das séries acompanhadas (ver [LibraryCalendar.kt] p/ o conjunto e as regras).
 */
@HiltViewModel
class MinhaListaViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val continueArt: com.aotv.app.data.repository.ContinueArtworkRepository,
    private val seriesDetailCache: com.aotv.app.data.remote.SeriesDetailCache,
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

    // ── Calendário da Biblioteca (Lote 2, item 6) ───────────────────────────
    // Conjunto: séries da Minha Lista + séries do Continuar assistindo, sem
    // duplicar, com teto de CALENDAR_MAX_SERIES (o mesmo conjunto da fileira
    // "Novos episódios" da Início). Só recarrega quando o conjunto muda (ou
    // no "Atualizar" da seção): abrir a Biblioteca 2x seguidas não refaz
    // nada. Os detalhes vêm do SeriesDetailCache (TTL 6h, compartilhado com
    // a Início), então a série que a Início já buscou não é buscada de novo.

    private val _calendar = MutableStateFlow(CalendarUiState())
    val calendar: StateFlow<CalendarUiState> = _calendar

    private var lastCalendarKey = ""

    /** Carrega o calendário. [force] = ignora o conjunto anterior (botão Atualizar). */
    fun loadCalendar(force: Boolean = false) {
        viewModelScope.launch {
            val watchlistNow = library.watchlist().first()
            val recentNow = library.recentlyPlayed().first()
            val series = (recentNow.filter { it.type == SERIES_TYPE }.map { Triple(it.tmdbId, it.title, it.posterUrl) } +
                watchlistNow.filter { it.type == SERIES_TYPE }.map { Triple(it.tmdbId, it.title, it.posterUrl) })
                .distinctBy { it.first }.take(CALENDAR_MAX_SERIES)
            val key = series.joinToString(",") { it.first.toString() }
            if (key == lastCalendarKey && !force) return@launch
            lastCalendarKey = key
            if (series.isEmpty()) {
                _calendar.value = CalendarUiState(loading = false, hasTrackedSeries = false)
                return@launch
            }
            _calendar.value = CalendarUiState(loading = true, hasTrackedSeries = true)
            val now = System.currentTimeMillis()
            val entries = coroutineScope {
                series.map { (id, title, poster) ->
                    async {
                        val d = seriesDetailCache.get(id) ?: return@async emptyList<CalendarEntry>()
                        val last = d.lastEpisodeToAir
                        val seen = last != null && library.isEpisodeWatched(id, last.season, last.episode)
                        calendarEntriesOf(id, title.ifBlank { d.name }, poster, d, seen, now)
                    }
                }.awaitAll().flatten()
            }
            _calendar.value = CalendarUiState(loading = false, entries = sortCalendarEntries(entries), hasTrackedSeries = true)
        }
    }

    companion object {
        private const val SERIES_TYPE = "SERIES"
    }

    /** Tira um título salvo da Minha Lista (segurar no card do grid). */
    fun removeFromWatchlist(tmdbId: Int, type: String) {
        viewModelScope.launch { library.removeFromList(tmdbId, type) }
        trakt.watchlistChanged(tmdbId, type, added = false)
    }
}
