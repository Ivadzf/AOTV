package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.TmdbGenreDto
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MovieItem
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
 * Página "Descobrir": o usuário monta a própria busca (tipo, gêneros, ordenação, classificação
 * indicativa, nota mínima e ano) e rola uma lista infinita do resultado.
 *
 * Qualquer mudança de filtro reinicia na página 1 com um pequeno atraso, senão bater várias
 * chips seguidas dispararia uma requisição por toque.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
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
    ) {
        /** Quantos filtros o usuário mexeu (fora tipo e ordenação). Vira o contador do botão limpar. */
        val activeFilterCount: Int
            get() = selectedGenres.size + (if (cert.isNotBlank()) 1 else 0) +
                (if (minRating != null) 1 else 0) + (if (year != null) 1 else 0)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var reloadJob: Job? = null
    private var queryRevision = 0

    init {
        loadGenres()
        reload(immediate = true)
    }

    // ── Ações de filtro ───────────────────────────────────────────────────────

    fun setKind(kind: Kind) {
        if (_uiState.value.kind == kind) return
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
        _uiState.update { it.copy(selectedGenres = emptySet(), cert = "", minRating = null, year = null) }
        reload()
    }

    // ── Carregamento ──────────────────────────────────────────────────────────

    private fun loadGenres() {
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
        _uiState.update { it.copy(loading = true, loadingMore = false, items = emptyList(), totalResults = 0, page = 1) }
        reloadJob = viewModelScope.launch {
            if (!immediate) delay(280) // agrupa toques seguidos em chips
            val s = _uiState.value
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

    /** Próxima página (scroll infinito). Ignora quando já está no fim ou carregando. */
    fun loadMore() {
        val s = _uiState.value
        if (s.loading || s.loadingMore || s.page >= s.totalPages) return
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
    }
}
