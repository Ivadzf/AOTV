package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.GlassCircleButton
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.MediaPosterCard
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/**
 * Página "Descobrir": o usuário monta a busca do jeito dele (tipo, ordenação, gêneros,
 * classificação indicativa, nota e ano) e rola uma lista infinita.
 *
 * Serve celular E TV com o mesmo código: FilterChipPill e MediaPosterCard já reagem a foco
 * de D-pad (borda branca + escala), então não precisa de uma segunda tela só pra TV.
 * [columns] é o único ajuste, a TV cabe mais pôster por linha.
 */
@Composable
fun DiscoverScreen(
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
    columns: Int = 3,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // Filtros começam abertos. Depois de escolher, o usuário fecha e sobra tela pro resultado.
    var filtersOpen by rememberSaveable { mutableStateOf(true) }

    // Scroll infinito: pede a próxima página quando falta pouco pro fim.
    val nearEnd by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - (columns * 2)
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }
    // Filtro novo volta pro topo, senão o usuário fica no meio de uma lista que mudou.
    LaunchedEffect(state.loading) { if (state.loading) runCatching { gridState.scrollToItem(0) } }

    Column(Modifier.fillMaxSize().background(Background)) {
        DiscoverTopBar(
            total = state.totalResults,
            filterCount = state.activeFilterCount,
            filtersOpen = filtersOpen,
            onToggleFilters = { filtersOpen = !filtersOpen },
            onClear = { viewModel.clearFilters() },
            onBack = onBack,
        )

        LazyVerticalGrid(
            // Celular em pé 3 colunas, tela larga cabe mais (pôster de 112dp ou mais).
            columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = com.aotv.app.ui.common.pagePadding(), end = com.aotv.app.ui.common.pagePadding(), bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (filtersOpen) FiltersPanel(state, viewModel)
                else ActiveFiltersBar(state, viewModel)
            }

            when {
                state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    }
                }

                state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    AotvEmptyState(
                        icon = Icons.Outlined.LocalMovies,
                        title = "Nada com esses filtros",
                        message = "Tire alguma condição e tente de novo. Gênero somado a nota alta e ano fechado costuma zerar a lista.",
                    )
                }

                else -> {
                    items(state.items, key = { it.tmdbId }) { item ->
                        DiscoverPoster(item, onClick = { onItemClick(item) })
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Com o painel fechado, mostra em chips o que está filtrando agora. Tocar num chip remove
 * aquele filtro. Sem isso o usuário fecharia o painel e perderia de vista o que aplicou.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActiveFiltersBar(state: DiscoverViewModel.UiState, vm: DiscoverViewModel) {
    if (state.activeFilterCount == 0) { Spacer(Modifier.height(2.dp)); return }
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.selectedGenres.forEach { id ->
            val name = state.genres.firstOrNull { it.id == id }?.name ?: return@forEach
            FilterChipPill(name, true, onClick = { vm.toggleGenre(id) })
        }
        if (state.cert.isNotBlank()) FilterChipPill(state.cert, true, onClick = { vm.setCert(state.cert) })
        state.minRating?.let { r -> FilterChipPill("${r.toInt()}+", true, onClick = { vm.setMinRating(r) }) }
        state.year?.let { y -> FilterChipPill(y.toString(), true, onClick = { vm.setYear(y) }) }
    }
}

/** Pôster com o título ABAIXO da arte. Ano e nota ficam na faixa do próprio pôster. */
@Composable
private fun DiscoverPoster(item: MovieItem, onClick: () -> Unit) {
    com.aotv.app.ui.common.PosterCard(item = item, onClick = onClick)
}

@Composable
private fun DiscoverTopBar(
    total: Int,
    filterCount: Int,
    filtersOpen: Boolean,
    onToggleFilters: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .topInsetsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Na TV o menu lateral e o Voltar do controle fazem esse papel.
        if (!com.aotv.app.ui.common.LocalIsTv.current) GlassCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            size = 44.dp,
            iconTint = TextSecondary,
            contentDescription = "Voltar",
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Descobrir",
                color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = SoraFontFamily, letterSpacing = (-0.4).sp,
            )
            if (total > 0) {
                Text(
                    "%,d títulos".format(total).replace(',', '.'),
                    color = TextTertiary, fontSize = 12.sp, fontFamily = ManropeFontFamily,
                )
            }
        }
        if (filterCount > 0) {
            Text(
                "Limpar",
                color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            )
        }
        // Abre/fecha o painel. O número mostra quantos filtros estão valendo agora.
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (filterCount > 0) AccentSoft else Color(0x0AFFFFFF))
                .border(1.dp, if (filterCount > 0) Accent else Border, RoundedCornerShape(999.dp))
                .clickable(onClick = onToggleFilters)
                .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                if (filterCount > 0) "Filtros $filterCount" else "Filtros",
                color = if (filterCount > 0) TextPrimary else TextSecondary,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ManropeFontFamily,
            )
            Icon(
                if (filtersOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (filterCount > 0) Accent else TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FiltersPanel(state: DiscoverViewModel.UiState, vm: DiscoverViewModel) {
    val isMovie = state.kind == DiscoverViewModel.Kind.MOVIE
    // Painel é uma superfície só, com borda e fundo próprios. Antes as seções ficavam soltas
    // sobre o fundo da tela e não dava pra saber onde o filtro acabava e o resultado começava.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BackgroundCard)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        FilterGroup("Tipo", wrap = true) {
            FilterChipPill("Filmes", isMovie, onClick = { vm.setKind(DiscoverViewModel.Kind.MOVIE) })
            FilterChipPill("Séries", state.kind == DiscoverViewModel.Kind.SERIES,
                onClick = { vm.setKind(DiscoverViewModel.Kind.SERIES) })
        }

        FilterDivider()

        FilterGroup("Ordenar por", wrap = true) {
            FilterChipPill("Populares", state.sort == DiscoverViewModel.SORT_POPULAR,
                onClick = { vm.setSort(DiscoverViewModel.SORT_POPULAR) })
            FilterChipPill("Melhor nota", state.sort == DiscoverViewModel.SORT_TOP_RATED,
                onClick = { vm.setSort(DiscoverViewModel.SORT_TOP_RATED) })
            val releaseSort = if (isMovie) DiscoverViewModel.SORT_RELEASE_MOVIE else DiscoverViewModel.SORT_RELEASE_TV
            FilterChipPill("Lançamentos", state.sort == releaseSort, onClick = { vm.setSort(releaseSort) })
            FilterChipPill("Título A-Z", state.sort == DiscoverViewModel.SORT_TITLE,
                onClick = { vm.setSort(DiscoverViewModel.SORT_TITLE) })
        }

        if (state.genres.isNotEmpty()) {
            FilterDivider()
            // Gênero quebra linha em vez de rolar: a pessoa vê a lista toda de uma vez e
            // escolhe, em vez de descobrir por acaso que tem mais coisa à direita.
            FilterGroup("Gêneros", wrap = true, count = state.selectedGenres.size) {
                state.genres.forEach { g ->
                    FilterChipPill(g.name, g.id in state.selectedGenres, onClick = { vm.toggleGenre(g.id) })
                }
            }
        }

        // O TMDB só classifica filme, então a seção some no modo Séries.
        if (isMovie) {
            FilterDivider()
            FilterGroup("Classificação", wrap = true, count = if (state.cert.isNotBlank()) 1 else 0) {
                DiscoverViewModel.CERTS.forEach { c ->
                    FilterChipPill(c, state.cert == c, onClick = { vm.setCert(c) })
                }
            }
        }

        FilterDivider()
        FilterGroup("Nota mínima", wrap = true, count = if (state.minRating != null) 1 else 0) {
            listOf(6f, 7f, 8f, 9f).forEach { r ->
                FilterChipPill("${r.toInt()}+", state.minRating == r, onClick = { vm.setMinRating(r) })
            }
        }

        FilterDivider()
        // Ano é naturalmente longo e ordenado, então aqui rolar horizontal faz mais sentido
        // que quebrar em cinco linhas de número.
        FilterGroup("Ano", wrap = false, count = if (state.year != null) 1 else 0) {
            (2026 downTo 1990).forEach { y ->
                FilterChipPill(y.toString(), state.year == y, onClick = { vm.setYear(y) })
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun FilterDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = 0.6f)))
}

/**
 * Título da seção + chips. [wrap] true quebra linha (FlowRow), false rola na horizontal.
 * [count] desenha quantos itens dessa seção estão marcados.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    wrap: Boolean,
    count: Int = 0,
    chips: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = 14.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                title.uppercase(),
                color = if (count > 0) Accent else TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ManropeFontFamily,
                letterSpacing = 1.2.sp,
            )
            if (count > 0) {
                Text(
                    count.toString(),
                    color = Color(0xFF06070A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Accent)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        if (wrap) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { chips() }
        } else {
            LazyRow(
                Modifier.padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() } }
            }
        }
    }
}
