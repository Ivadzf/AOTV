package com.aotv.app.ui.live

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.data.live.LiveSourceType
import com.aotv.app.data.live.LiveTvRepository
import com.aotv.app.data.live.VodCategory
import com.aotv.app.data.live.VodEpisode
import com.aotv.app.data.live.VodItem
import com.aotv.app.data.live.VodSeriesDetail
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
class LiveTvViewModel @Inject constructor(
    private val repo: LiveTvRepository,
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
) : ViewModel() {

    data class View(
        val hasSources: Boolean = false,
        val rows: List<LiveRow> = emptyList(),
        /** Grupos com a contagem, na ordem em que aparecem na lista. */
        val groups: List<Pair<String, Int>> = emptyList(),
        /** Canais por grupo (a lista INTEIRA, sem filtro nem busca), p/ os cartões do hub da Live TV (Lote 9, v1.19.0). Um canal em 2 grupos aparece nos 2, igual à contagem de [groups]. */
        val groupedRows: Map<String, List<LiveRow>> = emptyMap(),
        val favoriteCount: Int = 0,
        val recentCount: Int = 0,
        val total: Int = 0,
        val filter: String = ALL,
        val query: String = "",
        val loading: Boolean = false,
        val epgLoading: Boolean = false,
        val error: String? = null,
        val now: Long = System.currentTimeMillis(),
        /** Guia completo por canal (a grade usa a lista inteira, não só agora/a seguir). */
        val epg: Map<String, List<LiveProgram>> = emptyMap(),
        // Lote 8 (v1.18.0): guia configurável e exibição de canais (por perfil).
        val guideDaysAhead: Int = 3,
        val guideHoursBack: Int = 1,
        val showLogos: Boolean = true,
        val showNames: Boolean = true,
        val showNumbers: Boolean = false,
        val cleanGroupNames: Boolean = false,
    )

    private val filter = MutableStateFlow(ALL)
    private val query = MutableStateFlow("")
    private val clock = MutableStateFlow(System.currentTimeMillis())

    val view: StateFlow<View> = combine(repo.state, filter, query, clock) { st, f, q, now ->
        val favs = st.favorites
        val byId = st.channels.associateBy { it.id }
        val clean = prefs.liveCleanGroupNames
        val base = when {
            f == FAV -> st.channels.filter { it.id in favs }
            f == RECENT -> st.recents.mapNotNull { byId[it] }
            f.startsWith("g:") -> f.removePrefix("g:").let { g -> st.channels.filter { g in groupNames(it, clean) } }
            else -> st.channels
        }
        val needle = q.trim().lowercase()
        val list = if (needle.isEmpty()) base else base.filter { it.name.lowercase().contains(needle) || it.group.lowercase().contains(needle) }
        // Grupos na ordem em que aparecem na lista, com a contagem (uma passada só).
        val counts = LinkedHashMap<String, Int>()
        st.channels.forEach { c -> groupNames(c, clean).forEach { g -> counts[g] = (counts[g] ?: 0) + 1 } }
        val groups = counts.toList()
        // Lote 9 (v1.19.0): canais por grupo p/ os cartões do hub — a lista
        // inteira, sem o filtro nem a busca (o hub só aparece sem filtro e
        // sem busca; com filtro/busca a tela segue com a grade plana).
        val allRows = st.channels.map { c ->
            val (n, nx) = nowNext(st.epg[c.id], now)
            LiveRow(c, n, nx, c.id in favs)
        }
        val groupedRows = LinkedHashMap<String, MutableList<LiveRow>>()
        allRows.forEach { row ->
            groupNames(row.channel, clean).forEach { g ->
                groupedRows.getOrPut(g) { mutableListOf() }.add(row)
            }
        }
        View(
            hasSources = st.hasSources,
            rows = list.map { c ->
                val (n, nx) = nowNext(st.epg[c.id], now)
                LiveRow(c, n, nx, c.id in favs)
            },
            groups = groups,
            groupedRows = groupedRows,
            favoriteCount = st.channels.count { it.id in favs },
            recentCount = st.recents.count { it in byId },
            total = st.channels.size,
            filter = f, query = q, loading = st.loading, epgLoading = st.epgLoading, error = st.error, now = now,
            epg = st.epg,
            guideDaysAhead = prefs.liveGuideDaysAhead,
            guideHoursBack = prefs.liveGuideHoursBack,
            showLogos = prefs.liveShowLogos,
            showNames = prefs.liveShowNames,
            showNumbers = prefs.liveShowNumbers,
            cleanGroupNames = clean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    init {
        viewModelScope.launch { repo.refreshIfStale() }
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

        /** Tira prefixo de país tipo "BR | " do nome do grupo, SÓ p/ exibir (os dados não mudam). */
        fun cleanGroupName(name: String): String {
            val t = name.trim()
            val m = Regex("^[A-Za-z]{2,3}\\s*[|:\\-]\\s*").find(t) ?: return t
            return t.substring(m.value.length).trim().ifBlank { t }
        }

        /** Nomes de grupo p/ exibir (com a limpeza de prefixo quando ligada). */
        fun groupNames(c: LiveChannel, clean: Boolean): List<String> =
            groupsOf(c).map { if (clean) cleanGroupName(it) else it }

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
class LivePlayerViewModel @Inject constructor(
    private val repo: LiveTvRepository,
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
    private val library: com.aotv.app.data.repository.LibraryRepository,
) : ViewModel() {
    val state = repo.state
    fun channel(id: String) = repo.channel(id)
    fun watched(id: String) = repo.markWatched(id)
    fun toggleFavorite(id: String) = repo.toggleFavorite(id)
    fun nowNext(id: String, now: Long) = repo.nowNext(id, now)

    /** URL de catch-up de um programa ([startSec]/[stopSec] em epoch segundos, como na rota). */
    fun getCatchupUrl(channel: LiveChannel, startSec: Long, stopSec: Long): String? =
        repo.catchupUrl(channel, LiveProgram(startSec * 1000, stopSec * 1000, ""))

    /** Canal reserva ligado neste perfil. */
    val backupEnabled: Boolean get() = prefs.liveBackupChannel

    /**
     * Outro canal com o MESMO nome de outra fonte (Lote 8, v1.18.0). O player
     * tenta este UMA vez quando o canal falha; se ele também falhar, mostra
     * o erro real. Nunca entra em loop.
     */
    fun backupChannel(channel: LiveChannel): LiveChannel? =
        state.value.channels.firstOrNull {
            it.id != channel.id && it.sourceId != channel.sourceId &&
                it.name.equals(channel.name, ignoreCase = true)
        }

    /**
     * Ao vivo no Continuar assistindo (Lote 8, v1.18.0): grava no MESMO
     * histórico dos filmes e séries (tipo "LIVE", tmdbId estável do canal),
     * com o programa atual no selo e os horários no addon — o toque no card
     * volta pelo embedId "live|<canal>|<inicioSec>:<fimSec>" (ver
     * MoviesNavigation). Respeita a pref e o modo privado (no privado, nada
     * é gravado).
     */
    fun recordLiveContinue(channel: LiveChannel, program: LiveProgram?) {
        if (!prefs.liveInContinue || prefs.privateMode) return
        viewModelScope.launch {
            runCatching {
                library.saveProgress(
                    tmdbId = liveTmdbId(channel.id),
                    type = LIVE_TYPE,
                    title = channel.name,
                    posterUrl = channel.logo,
                    positionMs = 0L,
                    durationMs = 0L,
                    lastEpisode = program?.title.orEmpty(),
                    lastSourceUrl = channel.id,
                    lastAddon = if (program == null) "" else "${program.start / 1000}:${program.stop / 1000}",
                )
            }
        }
    }

    /** Última faixa de áudio lembrada deste canal (idioma ou rótulo), ou null. */
    fun savedAudio(channelId: String): String? =
        if (!prefs.liveRememberAudio) null else prefs.liveAudioPicks[channelId]?.lang

    /** Guarda a faixa de áudio escolhida neste canal (só quando a pref está ligada). */
    fun saveAudio(channelId: String, lang: String) {
        if (!prefs.liveRememberAudio || lang.isBlank()) return
        prefs.liveAudioPicks = prefs.liveAudioPicks +
            (channelId to com.aotv.app.data.local.LiveAudioPick(lang, System.currentTimeMillis()))
    }

    companion object {
        /** Tipo do histórico p/ canais ao vivo (não é MOVIE nem SERIES). */
        const val LIVE_TYPE = "LIVE"

        /** tmdbId estável (positivo) de um canal, p/ a chave do histórico. */
        fun liveTmdbId(channelId: String): Int {
            val h = ("live:" + channelId).hashCode()
            return if (h == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(h)
        }

        /** "live|<canal>|<inicioSec>:<fimSec>" do card do Continuar, ou null. */
        fun parseLiveEmbed(embedId: String): Triple<String, Long, Long>? {
            val parts = embedId.split("|")
            if (parts.size != 3 || parts[0] != "live") return null
            val times = parts[2].split(":")
            if (times.size != 2) return null
            val start = times[0].toLongOrNull() ?: return null
            val stop = times[1].toLongOrNull() ?: return null
            return Triple(parts[1], start, stop)
        }
    }
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
            val s = repo.addM3u(name, url, epg).getOrElse { e ->
                _ui.update { it.copy(busy = null, error = e.message) }; return@launch
            }
            repo.refresh(true)
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update {
                if (n == 0) it.copy(busy = null, error = "A lista não trouxe canais. Confira o endereço.")
                else it.copy(busy = null, message = "$n canais de ${s.name}")
            }
        }
    }

    /** Importa uma lista M3U de um arquivo (SAF); o repo guarda a URI e relê no refresh. */
    fun addM3uFromFile(context: Context, uri: Uri, name: String, epg: String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "m3u-file", error = null, message = null) }
        viewModelScope.launch {
            val s = repo.addM3uFromFile(context, uri, name, epg).getOrElse { e ->
                _ui.update { it.copy(busy = null, error = e.message) }; return@launch
            }
            repo.refresh(true)
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update {
                if (n == 0) it.copy(busy = null, error = "A lista não trouxe canais. Confira o arquivo.")
                else it.copy(busy = null, message = "$n canais de ${s.name}")
            }
        }
    }

    /** Edita uma fonte M3U existente (o repo revalida e recarrega os canais se o endereço mudou). */
    fun updateM3u(id: String, name: String, url: String, epg: String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "m3u", error = null, message = null) }
        viewModelScope.launch {
            val s = repo.updateM3u(id, name, url, epg).getOrElse { e ->
                _ui.update { it.copy(busy = null, error = e.message) }; return@launch
            }
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update { it.copy(busy = null, message = "$n canais de ${s.name}") }
        }
    }

    /** Edita uma conta Xtream existente (o repo revalida e recarrega os canais). */
    fun updateXtream(id: String, name: String, server: String, user: String, pass: String) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = "xtream", error = null, message = null) }
        viewModelScope.launch {
            val s = repo.updateXtream(id, name, server, user, pass).getOrElse { e ->
                _ui.update { it.copy(busy = null, error = e.message) }; return@launch
            }
            val n = repo.state.value.channels.count { it.sourceId == s.id }
            _ui.update { it.copy(busy = null, message = "$n canais de ${s.name}") }
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

/** On Demand Xtream (Lote 9, v1.19.0): filmes e séries da conta já configurada. */
@HiltViewModel
class OnDemandViewModel @Inject constructor(
    private val repo: LiveTvRepository,
) : ViewModel() {

    /** Detalhe aberto (filme ou série); [loading] enquanto o detalhe carrega. */
    data class Detail(val movie: VodItem? = null, val series: VodSeriesDetail? = null, val loading: Boolean = false)

    private data class Data(
        val vodCats: List<VodCategory> = emptyList(),
        val vodItems: List<VodItem> = emptyList(),
        val seriesCats: List<VodCategory> = emptyList(),
        val seriesItems: List<VodItem> = emptyList(),
        val loaded: Set<String> = emptySet(),
        val loading: Boolean = false,
        val error: String? = null,
    )

    data class View(
        val hasXtream: Boolean = false,
        val sourceName: String = "",
        val tab: String = MOVIES,
        /** Categorias com item, na ordem da API. */
        val categories: List<VodCategory> = emptyList(),
        /** Contagem por id de categoria. */
        val categoryCounts: Map<String, Int> = emptyMap(),
        /** Id da categoria filtrada; "" = todas. */
        val category: String = "",
        val items: List<VodItem> = emptyList(),
        /** Itens da aba, sem filtro nem busca (p/ o estado vazio honesto). */
        val total: Int = 0,
        val query: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val detail: Detail = Detail(),
    )

    private val tab = MutableStateFlow(MOVIES)
    private val category = MutableStateFlow("")
    private val query = MutableStateFlow("")
    private val detail = MutableStateFlow(Detail())
    private val data = MutableStateFlow(Data())

    // combine de 6 flows SÓ compila na forma vararg (build #235): os valores
    // vêm do array, na ordem, com cast.
    val view: StateFlow<View> = combine(repo.state, tab, category, query, detail, data) { arr ->
        val st = arr[0] as LiveTvRepository.State
        val t = arr[1] as String
        val cat = arr[2] as String
        val q = arr[3] as String
        val det = arr[4] as Detail
        val d = arr[5] as Data
        val source = st.sources.firstOrNull { it.enabled && it.type == LiveSourceType.XTREAM }
        val cats = if (t == SERIES) d.seriesCats else d.vodCats
        val all = if (t == SERIES) d.seriesItems else d.vodItems
        val counts = LinkedHashMap<String, Int>()
        all.forEach { counts[it.categoryId] = (counts[it.categoryId] ?: 0) + 1 }
        val needle = q.trim().lowercase()
        val list = all.filter {
            (cat.isBlank() || it.categoryId == cat) &&
                (needle.isEmpty() || it.title.lowercase().contains(needle))
        }
        View(
            hasXtream = source != null,
            sourceName = source?.name.orEmpty(),
            tab = t,
            categories = cats.filter { (counts[it.id] ?: 0) > 0 },
            categoryCounts = counts,
            category = cat,
            items = list,
            total = all.size,
            query = q,
            loading = d.loading,
            error = d.error,
            detail = det,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    init { load() }

    fun setTab(t: String) {
        if (tab.value == t) return
        tab.value = t
        category.value = ""
        load()
    }

    fun setCategory(c: String) { category.value = c }
    fun setQuery(q: String) { query.value = q }
    fun refresh() { load(force = true) }

    private fun load(force: Boolean = false) {
        val source = repo.xtreamSources().firstOrNull() ?: return
        val t = tab.value
        if (!force && t in data.value.loaded) return
        data.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val res = repo.fetchOnDemand(source, t == SERIES)
            if (res == null) {
                data.update {
                    it.copy(
                        loading = false,
                        error = "Não deu para carregar o On Demand de ${source.name}. Confira a conexão e tente de novo.",
                    )
                }
                return@launch
            }
            val (cats, items) = res
            data.update {
                val next = it.copy(loading = false, error = null, loaded = it.loaded + t)
                if (t == SERIES) next.copy(seriesCats = cats, seriesItems = items)
                else next.copy(vodCats = cats, vodItems = items)
            }
        }
    }

    /** Abre o detalhe do filme (enriquece com get_vod_info se a lista veio rala). */
    fun openMovie(item: VodItem) {
        detail.value = Detail(movie = item, loading = true)
        viewModelScope.launch {
            val source = repo.xtreamSources().firstOrNull()
            val full = if (source == null) item else repo.fetchVodInfo(source, item)
            if (detail.value.movie?.id == item.id) detail.value = Detail(movie = full)
        }
    }

    /** Abre o detalhe da série (get_series_info: info + episódios por temporada). */
    fun openSeries(item: VodItem) {
        detail.value = Detail(loading = true)
        viewModelScope.launch {
            val source = repo.xtreamSources().firstOrNull()
            val det = source?.let { repo.fetchSeriesDetail(it, item.id) }
            if (detail.value.loading) detail.value = Detail(series = det ?: VodSeriesDetail(item))
        }
    }

    fun closeDetail() { detail.value = Detail() }

    /** URL do filme no padrão Xtream (mesmo do ao vivo, com container_extension). */
    fun streamUrl(item: VodItem): String? =
        repo.xtreamSources().firstOrNull()?.let { repo.vodStreamUrl(it, item) }

    /** URL do episódio no padrão Xtream (/series/...). */
    fun episodeUrl(episode: VodEpisode): String? =
        repo.xtreamSources().firstOrNull()?.let { repo.episodeStreamUrl(it, episode) }

    companion object {
        const val MOVIES = "movies"
        const val SERIES = "series"
    }
}
