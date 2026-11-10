package com.aotv.app.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.CollectionSourceResolver
import com.aotv.app.data.collections.CollectionsRepository
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tela da pasta. Uma aba por fonte e, quando a coleção pede, a aba Tudo, que intercala os itens
 * de todas as fontes sem repetir (igual ao Nuvio). No modo fileiras cada fonte vira uma fileira.
 */
@HiltViewModel
class FolderViewModel @Inject constructor(
    saved: SavedStateHandle,
    repo: CollectionsRepository,
    private val resolver: CollectionSourceResolver,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    data class Tab(
        val key: String,
        val label: String,
        val source: CollectionSource?,
        val items: List<MovieItem> = emptyList(),
        val next: Int? = null,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
    ) {
        val isAll: Boolean get() = source == null
        val canLoadMore: Boolean get() = next != null && !loading && !loadingMore
    }

    data class UiState(
        val found: Boolean = true,
        val title: String = "",
        val collectionTitle: String = "",
        val backdrop: String? = null,
        val logo: String? = null,
        val tabbed: Boolean = true,
        val tabs: List<Tab> = emptyList(),
        val selected: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val col = repo.get(saved.get<String>("collectionId").orEmpty())
        val folder = col?.folders?.firstOrNull { it.id == saved.get<String>("folderId") }
        if (col == null || folder == null) {
            _state.value = UiState(found = false)
        } else {
            val catalogs = prefs.addonMetaFlow.value.values
            fun catalogName(s: CollectionSource) = catalogs.firstOrNull { it.id == s.addonId }?.catalogs
                ?.firstOrNull { it.id == s.catalogId && it.type == s.type }?.name
            val sourceTabs = folder.sources.map { s -> Tab(s.key, sourceLabel(s, ::catalogName), s) }.distinctBy { it.key }
            val all = if (col.showAllTab && col.tabbed && sourceTabs.size > 1) listOf(Tab("all", "Tudo", null)) else emptyList()
            _state.value = UiState(
                title = folder.title,
                collectionTitle = col.title,
                backdrop = folder.heroBackdropUrl ?: col.backdropImageUrl ?: folder.coverImageUrl,
                logo = folder.titleLogoUrl,
                tabbed = col.tabbed,
                tabs = all + sourceTabs,
            )
            // Tudo junta as fontes, então todas carregam a primeira página logo de cara.
            sourceTabs.forEach { load(it.key, more = false) }
            rebuildAll()
        }
    }

    fun select(i: Int) = _state.update { it.copy(selected = i.coerceIn(0, (it.tabs.size - 1).coerceAtLeast(0))) }

    /** Próxima página da aba. Na aba Tudo, de cada fonte que ainda tem mais. */
    fun loadMore(tabKey: String) {
        val tab = _state.value.tabs.firstOrNull { it.key == tabKey } ?: return
        if (tab.isAll) _state.value.tabs.filter { !it.isAll && it.canLoadMore }.forEach { load(it.key, more = true) }
        else if (tab.canLoadMore) load(tabKey, more = true)
    }

    private fun load(key: String, more: Boolean) {
        val tab = _state.value.tabs.firstOrNull { it.key == key } ?: return
        val source = tab.source ?: return
        if (more) update(key) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = resolver.load(source, if (more) tab.next else null)
            update(key) { t ->
                t.copy(
                    items = (t.items + page.items).distinctBy { "${it.type}:${it.tmdbId}" },
                    next = page.next,
                    loading = false,
                    loadingMore = false,
                    error = page.error.takeIf { t.items.isEmpty() && page.items.isEmpty() },
                )
            }
            rebuildAll()
        }
    }

    private fun update(key: String, f: (Tab) -> Tab) =
        _state.update { st -> st.copy(tabs = st.tabs.map { if (it.key == key) f(it) else it }) }

    /** Intercala as fontes (uma de cada por vez), sem repetir título. */
    private fun rebuildAll() = _state.update { st ->
        val sources = st.tabs.filter { !it.isAll }
        if (st.tabs.none { it.isAll }) return@update st
        val merged = ArrayList<MovieItem>()
        val seen = HashSet<String>()
        val its = sources.map { it.items.iterator() }
        var any = true
        while (any) {
            any = false
            its.forEach { i -> if (i.hasNext()) { any = true; val m = i.next(); if (seen.add("${m.type}:${m.tmdbId}")) merged += m } }
        }
        st.copy(tabs = st.tabs.map { t ->
            if (!t.isAll) t else t.copy(
                items = merged,
                next = if (sources.any { it.next != null }) 1 else null,
                loading = sources.any { it.loading },
                loadingMore = sources.any { it.loadingMore },
                error = sources.firstOrNull { it.error != null }?.error.takeIf { merged.isEmpty() && sources.none { it.loading } },
            )
        })
    }
}
