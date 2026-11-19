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
import kotlinx.coroutines.flow.asStateFlow
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

    // ── Ordenação da Minha Lista (Lote 23, v1.33.0) ───────────────────────
    // "recent" (mais recentes primeiro, como antes), "name" (título A–Z) ou
    // "unwatched" (não assistidos primeiro — mesma regra de [notWatchedYet] —
    // depois os recentes). A pref não tem Flow, então a ordem vive aqui: a
    // tela lê [myListOrder] e troca por [setMyListOrder].
    private val _myListOrder = MutableStateFlow(prefs.myListOrder)
    val myListOrder: StateFlow<String> = _myListOrder.asStateFlow()

    fun setMyListOrder(order: String) {
        val clean = order.takeIf { it in setOf("recent", "name", "unwatched") } ?: "recent"
        prefs.myListOrder = clean
        _myListOrder.value = clean
    }

    // Forma vararg do combine (a de 4 flows tipada não infere nesta versão
    // do projeto — ver o combine de 6 flows do MoviesHomeViewModel).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val orderedWatchlist: StateFlow<List<WatchlistEntity>> =
        kotlinx.coroutines.flow.combine(
            library.watchlist(), _myListOrder, library.recentlyPlayed(), library.watchedSeriesIds(),
        ) { args ->
            val saved = args[0] as List<WatchlistEntity>
            val order = args[1] as String
            val played = args[2] as List<MovieHistoryEntity>
            val watchedSeries = args[3] as Set<Int>
            val playedKeys = played.map { it.tmdbId to it.type }.toSet()
            val isWatched = { w: WatchlistEntity ->
                (w.tmdbId to w.type) in playedKeys ||
                    (w.type == SERIES_TYPE && w.tmdbId in watchedSeries)
            }
            when (order) {
                "name" -> saved.sortedBy { it.title.lowercase() }
                "unwatched" -> saved.sortedWith(compareBy({ isWatched(it) }, { -it.addedAt }))
                else -> saved.sortedByDescending { it.addedAt }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ── Selo "Assistido" da Minha Lista (Lote 23) ─────────────────────────
    // Filme com >= 90% assistido no histórico; série com o último episódio
    // exibido marcado como visto (o app não guarda o total de episódios sem
    // baixar cada temporada — estar em dia com o último exibido é o sinal
    // local de série terminada; marcar só o último sem ver os anteriores
    // também marcaria). Só entram títulos que estão na Minha Lista.
    private val _watchedTitleIds = MutableStateFlow<Set<Int>>(emptySet())
    val watchedTitleIds: StateFlow<Set<Int>> = _watchedTitleIds.asStateFlow()

    init {
        viewModelScope.launch {
            combine(library.watchlist(), library.recentlyPlayed()) { saved, played ->
                saved to played
            }.collect { (saved, played) -> refreshWatchedTitleIds(saved, played) }
        }
    }

    private suspend fun refreshWatchedTitleIds(saved: List<WatchlistEntity>, played: List<MovieHistoryEntity>) {
        val savedIds = saved.map { it.tmdbId }.toSet()
        val out = played
            .filter { it.tmdbId in savedIds && it.type != SERIES_TYPE && it.durationMs > 0 && it.positionMs >= it.durationMs * 0.9 }
            .map { it.tmdbId }
            .toMutableSet()
        val series = saved.filter { it.type == SERIES_TYPE }
        if (series.isNotEmpty()) {
            val finished = coroutineScope {
                series.map { w ->
                    async {
                        val last = seriesDetailCache.get(w.tmdbId)?.lastEpisodeToAir ?: return@async null
                        if (library.isEpisodeWatched(w.tmdbId, last.season, last.episode)) w.tmdbId else null
                    }
                }.awaitAll().filterNotNull()
            }
            out += finished
        }
        _watchedTitleIds.value = out
    }

    // ── Abandon Series (Lote 23) ───────────────────────────────────────────
    /** Séries abandonadas no perfil (p/ a tela filtrar o Continuar e o calendário). */
    val droppedSeries: StateFlow<Set<Int>> = prefs.droppedSeriesFlow

    /** "Não hoje" de hoje p/ este título (o rail do Continuar esconde até amanhã). */
    fun isNotToday(tmdbId: Int): Boolean = prefs.isNotToday(tmdbId)

    /** Abandonar série: some do Continuar e do calendário; avisa os trackers. */
    fun dropSeries(tmdbId: Int) {
        if (tmdbId <= 0) return
        prefs.dropSeries(tmdbId)
        simkl.seriesDropped(tmdbId)
        trakt.seriesDropped(tmdbId)
        loadCalendar(force = true)
    }

    /** Desfazer o abandono de uma série. */
    fun undropSeries(tmdbId: Int) {
        if (tmdbId <= 0) return
        prefs.undropSeries(tmdbId)
        loadCalendar(force = true)
    }

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

    /**
     * "Marcar como assistido" no painel de opções do título (Lote 14,
     * v1.24.0 — TV e Minha Lista): mesma regra do
     * [MoviesHomeViewModel.markContinueWatched] (ver
     * [LibraryRepository.markContinueWatched]). Modo privado não marca nada.
     */
    fun markContinueWatched(item: com.aotv.app.domain.model.MovieItem) {
        if (prefs.privateMode) return
        viewModelScope.launch { library.markContinueWatched(item.tmdbId) }
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
                .distinctBy { it.first }
                // Série abandonada (Lote 23) some do calendário.
                .filterNot { prefs.isSeriesDropped(it.first) }
                .take(CALENDAR_MAX_SERIES)
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
