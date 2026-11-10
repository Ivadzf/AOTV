package com.aotv.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.data.live.LiveTvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Linha da lista de canais com o que está passando agora. */
data class LiveRow(val channel: LiveChannel, val now: LiveProgram?, val next: LiveProgram?, val favorite: Boolean)

@HiltViewModel
class LiveTvViewModel @Inject constructor(private val repo: LiveTvRepository) : ViewModel() {

    data class View(
        val hasSources: Boolean = false,
        val rows: List<LiveRow> = emptyList(),
        /** Grupos com a contagem, na ordem em que aparecem na lista. */
        val groups: List<Pair<String, Int>> = emptyList(),
        val favoriteCount: Int = 0,
        val recentCount: Int = 0,
        val total: Int = 0,
        val filter: String = ALL,
        val query: String = "",
        val loading: Boolean = false,
        val epgLoading: Boolean = false,
        val error: String? = null,
        val now: Long = System.currentTimeMillis(),
    )

    private val filter = MutableStateFlow(ALL)
    private val query = MutableStateFlow("")
    private val clock = MutableStateFlow(System.currentTimeMillis())

    val view: StateFlow<View> = combine(repo.state, filter, query, clock) { st, f, q, now ->
        val favs = st.favorites
        val byId = st.channels.associateBy { it.id }
        val base = when {
            f == FAV -> st.channels.filter { it.id in favs }
            f == RECENT -> st.recents.mapNotNull { byId[it] }
            f.startsWith("g:") -> f.removePrefix("g:").let { g -> st.channels.filter { g in groupsOf(it) } }
            else -> st.channels
        }
        val needle = q.trim().lowercase()
        val list = if (needle.isEmpty()) base else base.filter { it.name.lowercase().contains(needle) || it.group.lowercase().contains(needle) }
        // Grupos na ordem em que aparecem na lista, com a contagem (uma passada só).
        val counts = LinkedHashMap<String, Int>()
        st.channels.forEach { c -> groupsOf(c).forEach { g -> counts[g] = (counts[g] ?: 0) + 1 } }
        val groups = counts.toList()
        View(
            hasSources = st.hasSources,
            rows = list.map { c ->
                val (n, nx) = nowNext(st.epg[c.id], now)
                LiveRow(c, n, nx, c.id in favs)
            },
            groups = groups,
            favoriteCount = st.channels.count { it.id in favs },
            recentCount = st.recents.count { it in byId },
            total = st.channels.size,
            filter = f, query = q, loading = st.loading, epgLoading = st.epgLoading, error = st.error, now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    init {
        viewModelScope.launch { repo.refresh(false) }
        // O "agora" do guia anda sozinho.
        viewModelScope.launch { while (true) { delay(30_000); clock.value = System.currentTimeMillis() } }
    }

    fun reload() { viewModelScope.launch { repo.refresh(true) } }
    fun setFilter(f: String) { filter.value = f }
    fun setQuery(q: String) { query.value = q }
    fun toggleFavorite(id: String) = repo.toggleFavorite(id)

    companion object {
        const val ALL = "all"
        const val FAV = "fav"
        const val RECENT = "recent"
        const val NO_GROUP = "Sem grupo"

        /** Categorias do canal. Listas como a do iptv-org juntam várias com ";" no mesmo grupo. */
        fun groupsOf(c: LiveChannel): List<String> =
            c.group.split(';').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(NO_GROUP) }

        /** Grupo para mostrar ("Filmes · Música"). */
        fun groupLabel(c: LiveChannel): String = c.group.split(';').map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")

        fun nowNext(list: List<LiveProgram>?, now: Long): Pair<LiveProgram?, LiveProgram?> {
            if (list.isNullOrEmpty()) return null to null
            val i = list.indexOfFirst { it.isNow(now) }
            return if (i < 0) null to list.firstOrNull { it.start > now } else list[i] to list.getOrNull(i + 1)
        }
    }
}

/** Só diz se a aba "Ao vivo" aparece na barra. Barato, a navegação observa o tempo todo. */
@HiltViewModel
class LiveBarViewModel @Inject constructor(repo: LiveTvRepository, prefs: com.aotv.app.data.local.PlatformPrefs) : ViewModel() {
    // Lista de IPTV pode ter canal adulto, então o perfil infantil não vê a aba.
    private val kids = prefs.isKids
    val show: StateFlow<Boolean> = repo.state.map { !kids && it.hasSources && it.showInBar }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.state.value.let { !kids && it.hasSources && it.showInBar })
}

@HiltViewModel
class LivePlayerViewModel @Inject constructor(private val repo: LiveTvRepository) : ViewModel() {
    val state = repo.state
    fun channel(id: String) = repo.channel(id)
    fun watched(id: String) = repo.markWatched(id)
    fun toggleFavorite(id: String) = repo.toggleFavorite(id)
    fun nowNext(id: String, now: Long) = repo.nowNext(id, now)
}

@HiltViewModel
class LiveSourcesViewModel @Inject constructor(private val repo: LiveTvRepository) : ViewModel() {
    data class Ui(val busy: String? = null, val message: String? = null, val error: String? = null)

    val state = repo.state
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun addM3u(name: String, url: String, epg: String) {
        if (_ui.value.busy != null) return
        if (!url.trim().startsWith("http", true)) { _ui.update { it.copy(error = "Cole o endereço da lista (http ou https)") }; return }
        _ui.update { it.copy(busy = "m3u", error = null, message = null) }
        viewModelScope.launch {
            val s = repo.addM3u(name, url, epg)
            repo.refresh(true)
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update {
                if (n == 0) it.copy(busy = null, error = "A lista não trouxe canais. Confira o endereço.")
                else it.copy(busy = null, message = "$n canais de ${s.name}")
            }
        }
    }

    fun addXtream(name: String, server: String, user: String, pass: String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "xtream", error = null, message = null) }
        viewModelScope.launch {
            val r = repo.addXtream(name, server, user, pass)
            val s = r.getOrElse { e -> _ui.update { it.copy(busy = null, error = e.message) }; return@launch }
            repo.refresh(true)
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update { it.copy(busy = null, message = "$n canais de ${s.name}") }
        }
    }

    fun refresh() {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "refresh", error = null, message = null) }
        viewModelScope.launch {
            repo.refresh(true)
            _ui.update { it.copy(busy = null, message = "${repo.state.value.channels.size} canais atualizados", error = repo.state.value.error) }
        }
    }

    fun setEnabled(id: String, on: Boolean) = repo.setEnabled(id, on)
    fun remove(id: String) = repo.remove(id)
    fun setShowInBar(on: Boolean) = repo.setShowInBar(on)
    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }
}
