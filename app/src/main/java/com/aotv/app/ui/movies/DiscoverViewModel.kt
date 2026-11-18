package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AddonCatalogExtra
import com.aotv.app.data.remote.AddonDiscoverCatalog
import com.aotv.app.data.remote.AddonMetaResolver
import com.aotv.app.data.remote.DiscoverCatalogMatcher
import com.aotv.app.data.remote.MDBListService
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.TmdbGenreDto
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Página "Descobrir": o usuário monta a própria busca (tipo, gêneros, ordenação, classificação
 * indicativa, nota mínima e ano) e rola uma lista infinita do resultado.
 *
 * Lote 14 (v1.24.0) — catálogos dos addons, como o Nuvio faz: com addon ligado (e perfil
 * não infantil), o topo ganha o seletor de catálogo ("TMDB · Filtros avançados" ou
 * "Filmes/Séries dos seus addons"). Trocar o catálogo troca os filtros do TMDB pelos que o
 * addon declara (gênero, ano, idioma, país, nota, ordem…); filtro obrigatório abre na 1ª
 * opção, sem "Todos". Filmes e Séries mantêm o MESMO addon ao trocar de aba (o catálogo
 * do mesmo addon no outro tipo, quando existe). Perfil infantil não vê catálogo de addon.
 *
 * Conversor addon→TMDB: cada título do catálogo precisa de par no TMDB (id `tmdb:`/`tt...`
 * via [AddonMetaResolver], ou título+ano via [DiscoverCatalogMatcher]). Título sem par NÃO
 * entra no resultado — nunca se mostra um título como se fosse do TMDB sem ser. Quando
 * nenhum título da página tem par, o aviso honesto aparece (comum em catálogo de anime).
 *
 * Sem addon ligado (ou perfil infantil), o comportamento é o de hoje: só TMDB, e o
 * seletor de catálogo nem aparece.
 *
 * Qualquer mudança de filtro reinicia na página 1 com um pequeno atraso, senão bater várias
 * chips seguidas dispararia uma requisição por toque.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
    private val stremio: StremioAddonService,
    private val addonMetas: AddonMetaResolver,
    private val matcher: DiscoverCatalogMatcher,
    private val mdbList: MDBListService,
) : ViewModel() {

    enum class Kind { MOVIE, SERIES }

    data class UiState(
        val kind: Kind = Kind.MOVIE,
        val genres: List<TmdbGenreDto> = emptyList(),
        val selectedGenres: Set<Int> = emptySet(),
        val sort: String = SORT_POPULAR,
        /** Classificação indicativa BR ("" = qualquer). Só vale pra filme. */
        val cert: String = "",
        /** Nota mínima (null = qualquer). */
        val minRating: Float? = null,
        /** Ano de lançamento (null = qualquer). */
        val year: Int? = null,
        val items: List<MovieItem> = emptyList(),
        val page: Int = 1,
        val totalPages: Int = 1,
        val totalResults: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        // ── Catálogos dos addons (Lote 14, v1.24.0) ──────────────────────────
        /** Catálogos de filme e série de todos os addons ligados. Vazio = só TMDB. */
        val catalogs: List<AddonDiscoverCatalog> = emptyList(),
        val catalogLoading: Boolean = false,
        /** null = TMDB; não null = catálogo de addon selecionado. */
        val selectedCatalog: AddonDiscoverCatalog? = null,
        /** Valores dos filtros que o catálogo declara ("" = "Todos", só p/ opcional). */
        val selectedExtras: Map<String, String> = emptyMap(),
        /** O addon não respondeu (null do catálogo). Diferente de catálogo vazio. */
        val connectionError: Boolean = false,
        /** O addon respondeu, mas o catálogo veio vazio na 1ª página. */
        val emptyCatalog: Boolean = false,
        /** A página trouxe títulos, mas NENHUM tem par no TMDB (comum em anime). */
        val noTmdbMatch: Boolean = false,
        /** Cursor do catálogo de addon (skip) e se há mais página. */
        val addonSkip: Int = 0,
        val addonHasMore: Boolean = false,
        /** Perfil infantil: nunca vê catálogo de addon, só TMDB. */
        val isKidsProfile: Boolean = false,
    ) {
        /** Quantos filtros o usuário mexeu (fora tipo e ordenação). Vira o contador do botão limpar. */
        val activeFilterCount: Int
            get() = if (selectedCatalog != null) {
                val extras = selectedCatalog.extras
                selectedExtras.count { (name, value) ->
                    val extra = extras.firstOrNull { it.name == name }
                    extra != null && (!extra.isRequired || value != extra.options.firstOrNull())
                }
            } else {
                selectedGenres.size + (if (cert.isNotBlank()) 1 else 0) +
                    (if (minRating != null) 1 else 0) + (if (year != null) 1 else 0)
            }

        /** Filtros que o catálogo selecionado declara (vazio no modo TMDB). */
        val addonExtras: List<AddonCatalogExtra>
            get() = selectedCatalog?.extras.orEmpty()

        /** Rótulo do topo: "TMDB · Filtros avançados" ou "Addon — Catálogo". */
        val catalogLabel: String
            get() = selectedCatalog?.label ?: "TMDB · Filtros avançados"

        /** Catálogos do tipo atual, p/ o seletor. */
        fun catalogsForKind(kind: Kind): List<AddonDiscoverCatalog> =
            catalogs.filter { (it.type == "series") == (kind == Kind.SERIES) }
    }

    private val _uiState = MutableStateFlow(UiState(isKidsProfile = prefs.isKids))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var reloadJob: Job? = null
    private var queryRevision = 0

    init {
        loadGenres()
        reload(immediate = true)
        loadCatalogs()
    }

    // ── Catálogos dos addons ────────────────────────────────────────────────

    /** Lê os catálogos de filme/série de todos os addons ligados, em paralelo. */
    private fun loadCatalogs() {
        if (prefs.isKids) return // perfil infantil: só TMDB, sem nem listar catálogo.
        _uiState.update { it.copy(catalogLoading = true) }
        viewModelScope.launch {
            val found = coroutineScope {
                prefs.addons.value.filter { it.enabled }.map { e ->
                    async { stremio.discoverCatalogs(e.url) }
                }.awaitAll().flatten()
            } + mdbListCatalogs()
            _uiState.update { it.copy(catalogs = found, catalogLoading = false) }
            // Restaura o catálogo escolhido na sessão anterior, se ele ainda existir.
            val saved = prefs.discoverCatalog
            if (saved.isNotBlank()) {
                val match = found.firstOrNull { it.key == saved }
                if (match != null) selectCatalog(match)
            }
        }
    }

    /**
     * Listas do usuário no MDBList como pseudo-catálogos (Lote 21, v1.31.0):
     * aparecem no seletor do Descobrir como "MDBList — Nome da lista", uma por
     * tipo (filme/série; lista mista aparece nas duas abas). Sem chave
     * configurada, não há lista — o Descobrir segue só com os addons.
     */
    private suspend fun mdbListCatalogs(): List<AddonDiscoverCatalog> {
        if (!mdbList.isConnected) return emptyList()
        return mdbList.getUserLists().getOrDefault(emptyList()).flatMap { list ->
            val types = when (list.mediatype.lowercase()) {
                "movie" -> listOf("movie")
                "series", "show", "tv" -> listOf("series")
                else -> listOf("movie", "series")
            }
            types.map { type ->
                AddonDiscoverCatalog(
                    addonId = MDBLIST_ADDON_ID,
                    addonName = "MDBList",
                    type = type,
                    id = "list:${list.id}",
                    name = list.name,
                )
            }
        }
    }

    /**
     * Troca o catálogo. null volta p/ o TMDB. Escolher um catálogo de série estando em
     * Filmes (ou o inverso) troca a aba junto — o catálogo manda no tipo.
     */
    fun selectCatalog(catalog: AddonDiscoverCatalog?) {
        val cur = _uiState.value
        if (cur.selectedCatalog == catalog) return
        prefs.discoverCatalog = catalog?.key.orEmpty()
        val kind = if (catalog == null) cur.kind
            else if (catalog.type == "series") Kind.SERIES else Kind.MOVIE
        _uiState.update {
            it.copy(
                selectedCatalog = catalog,
                kind = kind,
                genres = if (catalog == null) it.genres else emptyList(),
                selectedExtras = requiredDefaults(catalog),
            )
        }
        if (catalog == null) loadGenres()
        reload()
    }

    /** Filtro obrigatório abre na 1ª opção, sem "Todos". Opcional começa vazio (= Todos). */
    private fun requiredDefaults(catalog: AddonDiscoverCatalog?): Map<String, String> =
        catalog?.extras.orEmpty()
            .filter { it.isRequired }
            .mapNotNull { e -> e.options.firstOrNull()?.let { e.name to it } }
            .toMap().orEmpty()

    /** Valor de um filtro do catálogo. "" num obrigatório é ignorado (não há "Todos" nele). */
    fun setExtra(name: String, value: String) {
        val catalog = _uiState.value.selectedCatalog ?: return
        val extra = catalog.extras.firstOrNull { it.name == name } ?: return
        if (value.isBlank() && extra.isRequired) return
        _uiState.update {
            val next = it.selectedExtras.toMutableMap()
            if (value.isBlank()) next.remove(name) else next[name] = value
            it.copy(selectedExtras = next)
        }
        reload()
    }

    // ── Ações de filtro ───────────────────────────────────────────────────────

    fun setKind(kind: Kind) {
        if (_uiState.value.kind == kind) return
        val catalog = _uiState.value.selectedCatalog
        if (catalog != null) {
            // Mesmo addon ao trocar de aba: o catálogo do mesmo addon no outro tipo,
            // preferindo o de mesmo id. Sem catálogo desse addon no outro tipo, volta
            // p/ o TMDB em vez de mostrar o catálogo do tipo errado.
            val sameAddon = _uiState.value.catalogs.filter {
                it.addonId == catalog.addonId && (it.type == "series") == (kind == Kind.SERIES)
            }
            val next = sameAddon.firstOrNull { it.id == catalog.id } ?: sameAddon.firstOrNull()
            _uiState.update {
                it.copy(
                    kind = kind, selectedCatalog = next,
                    selectedExtras = requiredDefaults(next),
                    genres = emptyList(), selectedGenres = emptySet(), cert = "",
                    sort = if (it.sort == SORT_RELEASE_MOVIE || it.sort == SORT_RELEASE_TV)
                        (if (kind == Kind.MOVIE) SORT_RELEASE_MOVIE else SORT_RELEASE_TV) else it.sort,
                )
            }
            prefs.discoverCatalog = next?.key.orEmpty()
            if (next == null) loadGenres()
            reload()
            return
        }
        // Gêneros de filme e de série têm ids diferentes, então a seleção não sobrevive à troca.
        _uiState.update {
            it.copy(
                kind = kind, genres = emptyList(), selectedGenres = emptySet(), cert = "",
                sort = if (it.sort == SORT_RELEASE_MOVIE || it.sort == SORT_RELEASE_TV)
                    (if (kind == Kind.MOVIE) SORT_RELEASE_MOVIE else SORT_RELEASE_TV) else it.sort,
            )
        }
        loadGenres()
        reload()
    }

    fun toggleGenre(id: Int) {
        _uiState.update {
            val sel = it.selectedGenres
            it.copy(selectedGenres = if (id in sel) sel - id else sel + id)
        }
        reload()
    }

    fun setGenres(ids: Set<Int>) {
        if (_uiState.value.selectedGenres == ids) return
        _uiState.update { it.copy(selectedGenres = ids) }
        reload()
    }

    fun setSort(sort: String) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        reload()
    }

    /** "" limpa. Só tem efeito em filme (o TMDB não classifica série). */
    fun setCert(cert: String) {
        _uiState.update { it.copy(cert = if (it.cert == cert) "" else cert) }
        reload()
    }

    fun setMinRating(rating: Float?) {
        _uiState.update { it.copy(minRating = if (it.minRating == rating) null else rating) }
        reload()
    }

    fun setYear(year: Int?) {
        _uiState.update { it.copy(year = if (it.year == year) null else year) }
        reload()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedGenres = emptySet(), cert = "", minRating = null, year = null,
                selectedExtras = requiredDefaults(it.selectedCatalog),
            )
        }
        reload()
    }

    // ── Carregamento ──────────────────────────────────────────────────────────

    private fun loadGenres() {
        if (_uiState.value.selectedCatalog != null) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        val kind = _uiState.value.kind
        viewModelScope.launch {
            val list = runCatching {
                if (kind == Kind.MOVIE) tmdb.getMovieGenres(apiKey = apiKey).genres
                else tmdb.getTvGenres(apiKey = apiKey).genres
            }.getOrDefault(emptyList())
            _uiState.update { if (it.kind == kind) it.copy(genres = list) else it }
        }
    }

    private fun reload(immediate: Boolean = false) {
        val revision = ++queryRevision
        reloadJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true, loadingMore = false, items = emptyList(), totalResults = 0,
                page = 1, addonSkip = 0, addonHasMore = false,
                connectionError = false, emptyCatalog = false, noTmdbMatch = false,
            )
        }
        reloadJob = viewModelScope.launch {
            if (!immediate) delay(280) // agrupa toques seguidos em chips
            val s = _uiState.value
            if (s.selectedCatalog != null) {
                val res = fetchAddon(s, skip = 0)
                if (revision != queryRevision) return@launch
                _uiState.update {
                    when (res) {
                        null -> it.copy(loading = false)
                        else -> it.copy(
                            loading = false, items = res.items,
                            addonSkip = res.nextSkip, addonHasMore = res.hasMore,
                            connectionError = res.status == AddonStatus.CONNECTION_ERROR,
                            emptyCatalog = res.status == AddonStatus.EMPTY,
                            noTmdbMatch = res.status == AddonStatus.NO_TMDB_MATCH,
                        )
                    }
                }
            } else {
                val resp = fetch(s, page = 1)
                if (revision != queryRevision) return@launch
                _uiState.update {
                    if (resp == null) it.copy(loading = false)
                    else it.copy(
                        loading = false, items = resp.items, page = 1,
                        totalPages = resp.totalPages, totalResults = resp.totalResults,
                    )
                }
            }
        }
    }

    /** Próxima página (scroll infinito). Ignora quando já está no fim ou carregando. */
    fun loadMore() {
        val s = _uiState.value
        if (s.loading || s.loadingMore) return
        if (s.selectedCatalog != null) {
            if (!s.addonHasMore) return
            val revision = queryRevision
            val skip = s.addonSkip
            viewModelScope.launch {
                _uiState.update { it.copy(loadingMore = true) }
                val res = fetchAddon(s, skip = skip)
                if (revision != queryRevision) return@launch
                _uiState.update { cur ->
                    if (res == null) cur.copy(loadingMore = false)
                    else {
                        val seen = cur.items.mapTo(HashSet()) { it.tmdbId }
                        cur.copy(
                            loadingMore = false,
                            items = cur.items + res.items.filter { seen.add(it.tmdbId) },
                            addonSkip = res.nextSkip,
                            addonHasMore = res.hasMore,
                        )
                    }
                }
            }
            return
        }
        if (s.page >= s.totalPages) return
        val next = s.page + 1
        val revision = queryRevision
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            val resp = fetch(s, page = next)
            if (revision != queryRevision) return@launch
            _uiState.update { cur ->
                if (resp == null) cur.copy(loadingMore = false)
                else {
                    // Dedupe: o TMDB às vezes repete título entre páginas quando a ordenação empata.
                    val seen = cur.items.mapTo(HashSet()) { it.tmdbId }
                    cur.copy(
                        loadingMore = false,
                        items = cur.items + resp.items.filter { seen.add(it.tmdbId) },
                        page = next,
                        totalPages = resp.totalPages,
                    )
                }
            }
        }
    }

    private class Page(val items: List<MovieItem>, val totalPages: Int, val totalResults: Int)

    private enum class AddonStatus { OK, CONNECTION_ERROR, EMPTY, NO_TMDB_MATCH }

    private class AddonPage(
        val items: List<MovieItem>,
        val nextSkip: Int,
        val hasMore: Boolean,
        val status: AddonStatus,
    )

    /**
     * Uma página do catálogo de addon, já casada com o TMDB. null = a busca foi
     * cancelada por outra (o revision mudou no meio); erro de rede vira status, não null.
     */
    private suspend fun fetchAddon(s: UiState, skip: Int): AddonPage? {
        val catalog = s.selectedCatalog ?: return null
        // Listas do MDBList (Lote 21, v1.31.0): pseudo-catálogo, não addon Stremio.
        if (catalog.addonId == MDBLIST_ADDON_ID) return fetchMdbList(s, skip)
        val series = s.kind == Kind.SERIES
        val metas = stremio.catalogMetasWithExtras(
            catalog.base, catalog.type, catalog.id, skip, s.selectedExtras,
        ) ?: return AddonPage(emptyList(), skip, false, AddonStatus.CONNECTION_ERROR)
        if (metas.isEmpty()) return AddonPage(emptyList(), skip, false, AddonStatus.EMPTY)
        val page = metas.take(ADDON_PAGE)
        val resolved = addonMetas.resolveDiscover(page, series)
        val matched = resolved.matched.toMutableList()
        // Sem par por id, tenta por título+ano (só os primeiros, p/ não martelar o TMDB:
        // uma página de catálogo de anime pode ter 40 títulos sem id nenhum).
        if (resolved.unmatched.isNotEmpty()) {
            val seen = matched.mapTo(HashSet()) { it.tmdbId }
            resolved.unmatched.take(MAX_TITLE_MATCH_ATTEMPTS).forEach { meta ->
                val hit = runCatching { matcher.matchByTitle(meta, series) }.getOrNull()
                if (hit != null && seen.add(hit.tmdbId)) matched.add(hit)
            }
        }
        val status = if (matched.isEmpty()) AddonStatus.NO_TMDB_MATCH else AddonStatus.OK
        return AddonPage(matched, skip + page.size, metas.size >= ADDON_PAGE, status)
    }

    /**
     * Uma página de uma lista do MDBList (Lote 21, v1.31.0): os itens já têm
     * tmdbid — resolve cada um por id no TMDB (pôster, sinopse, nota), sem
     * casamento por título. Item sem tmdbid não entra (nunca se mostra um
     * título como se fosse do TMDB sem ser). Página de 40, com offset p/ o
     * "carregar mais" do Descobrir.
     */
    private suspend fun fetchMdbList(s: UiState, skip: Int): AddonPage {
        val catalog = s.selectedCatalog ?: return AddonPage(emptyList(), skip, false, AddonStatus.CONNECTION_ERROR)
        val listId = catalog.id.removePrefix("list:").toIntOrNull()
            ?: return AddonPage(emptyList(), skip, false, AddonStatus.EMPTY)
        val series = s.kind == Kind.SERIES
        val items = mdbList.getListItems(listId, ADDON_PAGE, skip).getOrNull()
            ?: return AddonPage(emptyList(), skip, false, AddonStatus.CONNECTION_ERROR)
        if (items.isEmpty()) return AddonPage(emptyList(), skip, false, AddonStatus.EMPTY)
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return AddonPage(emptyList(), skip, false, AddonStatus.EMPTY)
        // Resolve em paralelo (40 chamadas sequenciais demorariam segundos
        // demais numa conexão lenta); a ordem da lista é preservada.
        val matched = coroutineScope {
            items.mapNotNull { item ->
                val id = item.tmdbid ?: return@mapNotNull null
                async {
                    runCatching {
                        if (series) tmdb.getSeriesDetail(id, apiKey = apiKey).toMovieItem()
                        else tmdb.getMovieDetail(id, apiKey = apiKey).toMovieItem()
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }.filter { it.posterUrl.isNotBlank() && it.title.isNotBlank() }
        val status = if (matched.isEmpty()) AddonStatus.NO_TMDB_MATCH else AddonStatus.OK
        return AddonPage(matched, skip + items.size, items.size >= ADDON_PAGE, status)
    }

    private suspend fun fetch(s: UiState, page: Int): Page? {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return null
        val genres = s.selectedGenres.takeIf { it.isNotEmpty() }?.joinToString(",")
        return runCatching {
            if (s.kind == Kind.MOVIE) {
                val r = tmdb.discoverMoviesFiltered(
                    withGenres = genres,
                    sort = s.sort,
                    page = page,
                    voteGte = s.minRating,
                    year = s.year,
                    certCountry = if (s.cert.isNotBlank()) "BR" else null,
                    certification = s.cert.takeIf { it.isNotBlank() },
                    apiKey = apiKey,
                )
                Page(
                    r.results.filterNot { it.adult }.map { it.toMovieItem() }
                        .filter { it.posterUrl.isNotBlank() && it.title.isNotBlank() },
                    r.totalPages, r.totalResults,
                )
            } else {
                val r = tmdb.discoverTvFiltered(
                    withGenres = genres,
                    // discover/tv não conhece title.asc, o campo lá é name.
                    sort = if (s.sort == SORT_TITLE) "name.asc" else s.sort,
                    page = page,
                    voteGte = s.minRating,
                    year = s.year,
                    apiKey = apiKey,
                )
                Page(
                    r.results.filterNot { it.adult }.map { it.toMovieItem() }
                        .filter { it.posterUrl.isNotBlank() && it.title.isNotBlank() },
                    r.totalPages, r.totalResults,
                )
            }
        }.getOrNull()
    }

    companion object {
        const val SORT_POPULAR = "popularity.desc"
        const val SORT_TOP_RATED = "vote_average.desc"
        const val SORT_RELEASE_MOVIE = "primary_release_date.desc"
        const val SORT_RELEASE_TV = "first_air_date.desc"
        const val SORT_TITLE = "title.asc"

        /** Classificação indicativa brasileira, na ordem oficial. */
        val CERTS = listOf("L", "10", "12", "14", "16", "18")

        /** Itens lidos por página de catálogo de addon (igual à Início e às coleções). */
        const val ADDON_PAGE = 40

        /** Id do pseudo-addon das listas do MDBList no Descobrir (Lote 21, v1.31.0). */
        const val MDBLIST_ADDON_ID = "mdblist"

        /** Títulos sem id por página que tentam o casamento por título+ano (o resto fica sem par). */
        const val MAX_TITLE_MATCH_ATTEMPTS = 8
    }
}
