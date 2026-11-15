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
import kotlinx.coroutines.flow.combine
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
    private val simkl: com.aotv.app.data.remote.SimklService,
    private val continueArt: com.aotv.app.data.repository.ContinueArtworkRepository,
    private val seriesDetailCache: com.aotv.app.data.remote.SeriesDetailCache,
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
    /** Séries acompanhadas nos trackers, p/ o auto-add do calendário (Lote 13, v1.23.0). */
    private val trackerShows: com.aotv.app.data.remote.TrackerShowsCache,
) : ViewModel() {

    val watchlist: StateFlow<List<WatchlistEntity>> =
        library.watchlist().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // O oculto por perfil (v1.13.0) some da fileira: um backup antigo não traz o título de volta.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentlyPlayed: StateFlow<List<MovieHistoryEntity>> =
        kotlinx.coroutines.flow.combine(library.recentlyPlayed(), prefs.hiddenContinueFlow) { list, hidden ->
            list.filterNot { prefs.continueKey(it.tmdbId, it.type) in hidden }
        }.stateIn(
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

    /** Remove um item de "Continuar assistindo" e guarda o oculto por perfil (v1.13.0). */
    fun removeFromContinue(tmdbId: Int, type: String) {
        viewModelScope.launch {
            // v1.21.0: resolve o tipo ARMAZENADO na linha do histórico (o tipo
            // do card pode divergir do da linha — ver LibraryRepository).
            library.removePlaybackAnyType(tmdbId, type)
        }
    }

    // ── Ainda não assistidos (Lote 11, v1.21.0) ───────────────────────────
    // Títulos da Minha Lista que o perfil ainda NÃO assistiu: sem entrada no
    // histórico de reproduções E, p/ séries, sem nenhum episódio marcado
    // como visto. Usa o histórico CRU (library.recentlyPlayed), não o
    // filtrado por `hiddenContinue`: tirar um título do Continuar não faz
    // dele "não assistido" — ele já foi assistido. A série que só foi
    // marcada como vista (sem dar play) também não entra aqui.

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notWatchedYet: StateFlow<List<WatchlistEntity>> =
        combine(
            library.watchlist(),
            library.recentlyPlayed(),
            library.watchedSeriesIds(),
        ) { saved, played, watchedSeries ->
            val playedKeys = played.map { it.tmdbId to it.type }.toSet()
            saved.filterNot { w ->
                (w.tmdbId to w.type) in playedKeys ||
                    (w.type == SERIES_TYPE && w.tmdbId in watchedSeries)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Apaga todo o histórico de reproduções do perfil (aba Histórico, com confirmação na tela). */
    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }
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
            // Auto-add (Lote 13, v1.23.0): séries do Trakt/Simkl entram no
            // conjunto sozinhas, sem estar na Minha Lista (o cache é
            // compartilhado com a Início — sem busca nova na maioria das vezes).
            val tracker = if (prefs.calendarAutoAddTrackers) trackerShows.get(force) else emptyList()
            val series = (recentNow.filter { it.type == SERIES_TYPE }.map { Triple(it.tmdbId, it.title, it.posterUrl) } +
                watchlistNow.filter { it.type == SERIES_TYPE }.map { Triple(it.tmdbId, it.title, it.posterUrl) } +
                tracker)
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
        // Simkl: remoção é no-op de propósito (nunca apaga o histórico assistido).
        simkl.watchlistChanged(tmdbId, type, added = false)
    }
}
