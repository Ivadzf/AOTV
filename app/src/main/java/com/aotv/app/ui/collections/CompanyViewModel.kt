package com.aotv.app.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.collections.CollectionSource
import com.aotv.app.data.collections.CollectionSourceResolver
import com.aotv.app.data.collections.TmdbCollectionRules
import com.aotv.app.data.collections.TmdbSourceType
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Página da produtora (Lote 2 item 9b): abre do detalhe (seção Produção) e
 * mostra os títulos dela em fileiras — Populares, Mais bem avaliados e
 * Recentes, de filmes e de séries. Reutiliza o [CollectionSourceResolver]
 * (fonte COMPANY, como o editor de coleções) — nada de endpoint novo.
 */
@HiltViewModel
class CompanyViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val resolver: CollectionSourceResolver,
) : ViewModel() {

    data class Row(val label: String, val items: List<MovieItem>)

    data class UiState(
        val name: String = "",
        val logoUrl: String? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val rows: List<Row> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState(name = saved.get<String>("name").orEmpty()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val id = saved.get<Int>("companyId") ?: 0
        val name = saved.get<String>("name").orEmpty()
        if (id <= 0) {
            _state.update { it.copy(loading = false, error = "Produtora sem id.") }
        } else {
            viewModelScope.launch {
                val logo = runCatching { resolver.describe(TmdbSourceType.COMPANY, id) }.getOrNull()?.image
                val jobs = listOf(
                    "Filmes populares" to source(id, name, "MOVIE", TmdbCollectionRules.SORT_POPULAR),
                    "Filmes mais bem avaliados" to source(id, name, "MOVIE", TmdbCollectionRules.SORT_RATING),
                    "Filmes recentes" to source(id, name, "MOVIE", TmdbCollectionRules.SORT_RELEASE),
                    "Séries populares" to source(id, name, "TV", TmdbCollectionRules.SORT_POPULAR),
                    "Séries mais bem avaliadas" to source(id, name, "TV", TmdbCollectionRules.SORT_RATING),
                    "Séries recentes" to source(id, name, "TV", TmdbCollectionRules.SORT_FIRST_AIR),
                ).map { (label, src) ->
                    async {
                        val page = runCatching { resolver.load(src) }.getOrNull()
                        label to (page?.items.orEmpty())
                    }
                }
                val rows = jobs.awaitAll()
                    .mapNotNull { (label, items) -> if (items.isEmpty()) null else Row(label, items) }
                _state.update {
                    it.copy(
                        logoUrl = logo,
                        loading = false,
                        rows = rows,
                        error = if (rows.isEmpty()) "Nenhum título desta produtora no TMDB." else null,
                    )
                }
            }
        }
    }

    private fun source(id: Int, name: String, mediaType: String, sortBy: String) = CollectionSource(
        provider = "tmdb",
        tmdbSourceType = TmdbSourceType.COMPANY.name,
        title = name,
        tmdbId = id,
        mediaType = mediaType,
        sortBy = sortBy,
    )
}
