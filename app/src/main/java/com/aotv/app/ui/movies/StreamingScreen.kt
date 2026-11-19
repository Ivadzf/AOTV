package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.STREAMING_PROVIDERS
import com.aotv.app.data.remote.StreamingProvider
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Lote 25 (v1.35.0) — Frente D, item 10: tela Streaming ───────────────────
// Títulos por provedor via TMDB discover (watch_region=BR,
// with_watch_providers=<id>, with_watch_monetization_types=flatrate).
// A lista de provedores é fixa ([STREAMING_PROVIDERS], ids do TMDB) p/ a
// tela abrir sem rede; sem chave TMDB ou sem resposta, estado vazio honesto.

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    data class UiState(
        val providers: List<StreamingProvider> = STREAMING_PROVIDERS,
        val selectedId: Int = STREAMING_PROVIDERS.first().id,
        val isMovie: Boolean = true,
        val items: List<MovieItem> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val page: Int = 1,
        val endReached: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(1)
    }

    fun selectProvider(id: Int) {
        if (id == _uiState.value.selectedId) return
        _uiState.update { it.copy(selectedId = id) }
        load(1)
    }

    fun selectType(isMovie: Boolean) {
        if (isMovie == _uiState.value.isMovie) return
        _uiState.update { it.copy(isMovie = isMovie) }
        load(1)
    }

    fun loadMore() {
        val st = _uiState.value
        if (st.loading || st.endReached || st.error != null) return
        load(st.page + 1)
    }

    fun retry() = load(1)

    private fun load(page: Int) {
        loadJob?.cancel()
        val st = _uiState.value
        if (page == 1) _uiState.update { it.copy(loading = true, error = null, items = emptyList(), page = 1, endReached = false) }
        else _uiState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val key = prefs.tmdbApiKey
            if (key.isBlank()) {
                _uiState.update { it.copy(loading = false, error = "Sem chave TMDB configurada.") }
                return@launch
            }
            val result = runCatching {
                tmdb.discoverRaw(
                    type = if (st.isMovie) "movie" else "tv",
                    query = mapOf(
                        "watch_region" to "BR",
                        "with_watch_providers" to st.selectedId.toString(),
                        "with_watch_monetization_types" to "flatrate",
                        "sort_by" to "popularity.desc",
                        "page" to page.toString(),
                    ),
                    apiKey = key,
                )
            }.getOrNull()
            if (result == null) {
                _uiState.update { it.copy(loading = false, error = "O TMDB não respondeu. Verifique a conexão e toque em Tentar novamente.") }
                return@launch
            }
            val items = result.results.filterNot { it.adult }.map { it.toMovieItem() }
                .filter { it.posterUrl.isNotBlank() && it.title.isNotBlank() }
            _uiState.update {
                it.copy(
                    loading = false,
                    items = if (page == 1) items else it.items + items,
                    page = page,
                    endReached = page >= result.totalPages || items.isEmpty(),
                )
            }
        }
    }
}

/** Tela "Streaming": escolhe o provedor e o tipo, rola os títulos por popularidade. */
@Composable
fun StreamingScreen(
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
    viewModel: StreamingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    androidx.compose.runtime.LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }
    val selectedName = state.providers.firstOrNull { it.id == state.selectedId }?.name.orEmpty()

    Column(Modifier.fillMaxSize().background(Background)) {
        StreamingTopBar(onBack = onBack, title = "Streaming", sub = "Títulos por provedor · Brasil")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = com.aotv.app.ui.common.pagePadding(),
                end = com.aotv.app.ui.common.pagePadding(),
                bottom = 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.providers, key = { provider -> provider.id }) { provider ->
                            FilterChipPill(provider.name, provider.id == state.selectedId, onClick = { viewModel.selectProvider(provider.id) })
                        }
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipPill("Filmes", state.isMovie, onClick = { viewModel.selectType(true) })
                        FilterChipPill("Séries", !state.isMovie, onClick = { viewModel.selectType(false) })
                    }
                    if (!state.loading && state.error == null && state.items.isNotEmpty()) {
                        Text(
                            "$selectedName · ${if (state.isMovie) "filmes" else "séries"} · ${state.items.size} títulos",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            when {
                state.loading && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    }
                }
                state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    AotvEmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "Não deu para listar",
                        message = state.error,
                        action = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                                Text("Tentar novamente", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }
                state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    AotvEmptyState(
                        icon = Icons.Default.Tv,
                        title = "Nada por aqui",
                        message = "O TMDB não listou títulos de $selectedName no Brasil.",
                    )
                }
                else -> gridItems(state.items, key = { item -> "${item.type}:${item.tmdbId}" }) { item ->
                    com.aotv.app.ui.common.PosterCard(item = item, onClick = { onItemClick(item) })
                }
            }
            if (state.loading && state.items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StreamingTopBar(onBack: () -> Unit, title: String, sub: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().topInsetsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!com.aotv.app.ui.common.LocalIsTv.current) com.aotv.app.ui.common.GlassCircleButton(
            icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            size = 44.dp,
            iconTint = TextSecondary,
            contentDescription = "Voltar",
        )
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = TextSecondary, fontSize = 13.sp)
        }
    }
}
