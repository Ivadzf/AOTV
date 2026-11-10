package com.aotv.app.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.collections.CollectionFolder
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.CollectionSourceResolver
import com.aotv.app.data.collections.CollectionSourceResolver.SearchHit
import com.aotv.app.data.collections.CollectionsJson
import com.aotv.app.data.collections.CollectionsRepository
import com.aotv.app.data.collections.MediaCollection
import com.aotv.app.data.collections.TmdbCollectionRules
import com.aotv.app.data.collections.TmdbSourceType
import com.aotv.app.data.remote.AddonCatalogOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor de coleção igual ao do Nuvio. Mexe num rascunho e só grava no Salvar, então sair sem
 * salvar não estraga a coleção da Início. [Ui.folder] é a pasta aberta (null mostra a coleção).
 */
@HiltViewModel
class CollectionEditorViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: CollectionsRepository,
    private val resolver: CollectionSourceResolver,
) : ViewModel() {

    /** Tipo de busca do seletor de fontes. */
    enum class SearchKind(val label: String, val type: TmdbSourceType) {
        COMPANY("Estúdio", TmdbSourceType.COMPANY),
        COLLECTION("Franquia", TmdbSourceType.COLLECTION),
        PERSON("Ator ou atriz", TmdbSourceType.PERSON),
        DIRECTOR("Direção", TmdbSourceType.DIRECTOR),
    }

    data class Ui(
        val draft: MediaCollection,
        val isNew: Boolean,
        val folder: Int? = null,
        val dirty: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
        val catalogs: List<AddonCatalogOption>? = null,
        val catalogsLoading: Boolean = false,
        val search: List<SearchHit> = emptyList(),
        val searching: Boolean = false,
        val found: SearchHit? = null,
        val finding: Boolean = false,
        val findError: String? = null,
        val genres: Map<Boolean, List<SearchHit>> = emptyMap(),
        val providers: List<SearchHit> = emptyList(),
        val providersRegion: String = "",
        val keywords: List<SearchHit> = emptyList(),
    ) {
        val currentFolder: CollectionFolder? get() = folder?.let { draft.folders.getOrNull(it) }
    }

    private val original: MediaCollection? = saved.get<String>("collectionId")?.takeIf { it != "new" }?.let { repo.get(it) }
    private val _ui = MutableStateFlow(
        Ui(draft = original ?: MediaCollection(id = repo.newId(), title = ""), isNew = original == null),
    )
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private fun edit(f: (MediaCollection) -> MediaCollection) = _ui.update { it.copy(draft = f(it.draft), dirty = true, error = null) }

    // ── Coleção ─────────────────────────────────────────────────────────────

    fun setTitle(v: String) = edit { it.copy(title = v.take(80)) }
    fun setBackdrop(v: String) = edit { it.copy(backdropImageUrl = v.trim().ifBlank { null }) }
    fun setPin(v: Boolean) = edit { it.copy(pinToTop = v) }
    fun setViewMode(v: String) = edit { it.copy(viewMode = v) }
    fun setShowAll(v: Boolean) = edit { it.copy(showAllTab = v) }

    fun addFolder() {
        edit { it.copy(folders = it.folders + CollectionFolder(id = repo.newId(), title = "")) }
        _ui.update { it.copy(folder = it.draft.folders.lastIndex) }
    }

    fun openFolder(i: Int) = _ui.update { it.copy(folder = i, search = emptyList(), found = null, findError = null) }

    /** Volta para a coleção. Pasta nova que ficou sem nome e sem fonte sai sozinha. */
    fun closeFolder() {
        val i = _ui.value.folder ?: return
        val f = _ui.value.draft.folders.getOrNull(i)
        _ui.update { it.copy(folder = null) }
        if (f != null && f.title.isBlank() && f.sources.isEmpty()) edit { c -> c.copy(folders = c.folders.filterIndexed { j, _ -> j != i }) }
    }

    fun removeFolder(i: Int) = edit { c -> c.copy(folders = c.folders.filterIndexed { j, _ -> j != i }) }

    fun moveFolder(i: Int, delta: Int) = edit { c -> c.copy(folders = swap(c.folders, i, i + delta)) }

    /** Grava. Mesmas regras do importar (título da coleção, das pastas e fontes completas). */
    fun save() {
        val d = _ui.value.draft.let { c -> c.copy(title = c.title.trim(), folders = c.folders.map { it.copy(title = it.title.trim()) }) }
        val err = when {
            d.title.isBlank() -> "Dê um nome para a coleção"
            else -> CollectionsJson.validate(listOf(d))
        }
        if (err != null) { _ui.update { it.copy(error = err) }; return }
        repo.upsert(d)
        _ui.update { it.copy(draft = d, saved = true, dirty = false) }
    }

    // ── Pasta aberta ────────────────────────────────────────────────────────

    fun updateFolder(f: (CollectionFolder) -> CollectionFolder) {
        val i = _ui.value.folder ?: return
        edit { c -> c.copy(folders = c.folders.mapIndexed { j, x -> if (j == i) f(x) else x }) }
    }

    fun addSource(s: CollectionSource) = updateFolder { f ->
        if (f.sources.any { it.key == s.key }) f
        else f.copy(sources = f.sources + s, title = f.title.ifBlank { s.title.orEmpty() })
    }

    fun updateSource(i: Int, s: CollectionSource) = updateFolder { f -> f.copy(sources = f.sources.mapIndexed { j, x -> if (j == i) s else x }) }
    fun removeSource(i: Int) = updateFolder { f -> f.copy(sources = f.sources.filterIndexed { j, _ -> j != i }) }
    fun moveSource(i: Int, delta: Int) = updateFolder { f -> f.copy(sources = swap(f.sources, i, i + delta)) }

    // ── Dados do seletor de fontes ──────────────────────────────────────────

    fun loadCatalogs(force: Boolean = false) {
        if (_ui.value.catalogsLoading || (!force && _ui.value.catalogs != null)) return
        _ui.update { it.copy(catalogsLoading = true) }
        viewModelScope.launch {
            val list = runCatching { resolver.catalogOptions() }.getOrDefault(emptyList())
            _ui.update { it.copy(catalogs = list, catalogsLoading = false) }
        }
    }

    private var searchJob: Job? = null

    /** Busca no TMDB com uma pausa curta, para não chamar a cada letra. */
    fun search(kind: SearchKind, q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { _ui.update { it.copy(search = emptyList(), searching = false) }; return }
        _ui.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            delay(350)
            val hits = when (kind) {
                SearchKind.COMPANY -> resolver.searchCompanies(q)
                SearchKind.COLLECTION -> resolver.searchCollections(q)
                SearchKind.PERSON, SearchKind.DIRECTOR -> resolver.searchPeople(q)
            }
            _ui.update { it.copy(search = hits, searching = false) }
        }
    }

    /** Confere uma fonte colada por id ou link do TMDB e mostra o que achou antes de adicionar. */
    fun find(type: TmdbSourceType, input: String) {
        val id = TmdbCollectionRules.parseTmdbId(input)
        if (id == null) { _ui.update { it.copy(found = null, findError = "Cole o número ou o link do TMDB") }; return }
        _ui.update { it.copy(finding = true, found = null, findError = null) }
        viewModelScope.launch {
            val hit = resolver.describe(type, id)
            _ui.update { it.copy(finding = false, found = hit, findError = if (hit == null) "O TMDB não achou nada com esse número" else null) }
        }
    }

    fun clearFound() = _ui.update { it.copy(found = null, findError = null) }

    fun loadGenres(tv: Boolean) {
        if (_ui.value.genres.containsKey(tv)) return
        viewModelScope.launch {
            val list = resolver.genres(tv)
            _ui.update { it.copy(genres = it.genres + (tv to list)) }
        }
    }

    fun loadProviders(region: String, tv: Boolean) {
        val r = region.trim().uppercase().take(2)
        if (r.length != 2 || r == _ui.value.providersRegion) return
        _ui.update { it.copy(providersRegion = r) }
        viewModelScope.launch {
            val list = resolver.providers(r, tv)
            _ui.update { it.copy(providers = list) }
        }
    }

    private var keywordJob: Job? = null

    fun searchKeywords(q: String) {
        keywordJob?.cancel()
        if (q.isBlank()) { _ui.update { it.copy(keywords = emptyList()) }; return }
        keywordJob = viewModelScope.launch {
            delay(350)
            val hits = resolver.searchKeywords(q)
            _ui.update { it.copy(keywords = hits) }
        }
    }

    fun presets() = resolver.presets()

    private fun <T> swap(list: List<T>, i: Int, j: Int): List<T> {
        if (i !in list.indices || j !in list.indices) return list
        return list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
}
