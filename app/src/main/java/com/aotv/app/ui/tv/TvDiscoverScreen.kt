package com.aotv.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.movies.DiscoverViewModel
import com.aotv.app.ui.theme.*
import java.util.Calendar

private enum class DiscoverFilter(val title: String) {
    KIND("Conteúdo"), CATALOG("Catálogo"), SORT("Ordenar por"), GENRES("Gêneros"),
    YEAR("Ano"), CERT("Classificação"), RATING("Nota mínima"),
}

private data class FilterChoice(val id: String, val label: String)

@Composable
fun TvDiscoverScreen(
    onItemClick: (MovieItem) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<DiscoverFilter?>(null) }
    var restoreFocus by remember { mutableStateOf<DiscoverFilter?>(null) }
    var pendingField by remember { mutableStateOf<DiscoverFilter?>(null) }
    // Filtro de catálogo de addon aberto no momento (nome do extra), null = nenhum.
    var extraPicker by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreExtra by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val requesters = remember { DiscoverFilter.entries.associateWith { FocusRequester() } }
    val extraRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val grid = rememberLazyGridState()
    val movie = state.kind == DiscoverViewModel.Kind.MOVIE
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val sorts = listOf(
        FilterChoice(DiscoverViewModel.SORT_POPULAR, "Populares"),
        FilterChoice(DiscoverViewModel.SORT_TOP_RATED, "Melhor nota"),
        FilterChoice(if (movie) DiscoverViewModel.SORT_RELEASE_MOVIE else DiscoverViewModel.SORT_RELEASE_TV, "Lançamentos"),
        FilterChoice(DiscoverViewModel.SORT_TITLE, "Título A-Z"),
    )
    val genres = state.genres.filter { it.id in state.selectedGenres }.map { it.name }
    val genreValue = when (genres.size) {
        0 -> "Todos"
        1 -> genres.first()
        else -> "${genres.first()} +${genres.size - 1}"
    }
    val nearEnd by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 16
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) viewModel.loadMore() }
    LaunchedEffect(state.loading) { if (state.loading) grid.scrollToItem(0) }
    LaunchedEffect(restoreFocus, picker) {
        val target = restoreFocus
        if (target != null && picker == null) {
            withFrameNanos { }
            runCatching { requesters.getValue(target).requestFocus() }
            restoreFocus = null
        }
    }
    LaunchedEffect(restoreExtra, extraPicker) {
        val target = restoreExtra
        if (target != null && extraPicker == null) {
            withFrameNanos { }
            runCatching { extraRequesters[target]?.requestFocus() }
            restoreExtra = null
        }
    }

    TvText {
        Column(Modifier.fillMaxSize().background(Background)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tvPad).padding(top = 2.r, bottom = 1.5.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    TvEyebrow("Explore o catálogo")
                    Text("Descobrir", color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                        fontSize = 2.8.rs, lineHeight = 3.2.rs, letterSpacing = (-0.08).rs)
                }
                Text(
                    if (state.loading) "Atualizando…" else if (state.totalResults > 0) "%,d títulos".format(state.totalResults).replace(',', '.') else state.catalogLabel,
                    color = TextSecondary, fontSize = 1.rs, modifier = Modifier.padding(end = 1.4.r),
                )
                TvButton(if (clearing && state.loading) "Atualizando…" else "Limpar filtros",
                    { clearing = true; pendingField = null; viewModel.clearFilters() }, icon = Icons.Default.Refresh,
                    enabled = state.activeFilterCount > 0 && !state.loading, busy = clearing && state.loading)
            }
            // Catálogo de addon: os filtros do TMDB somem e entram os que o addon declara
            // (linha abaixo). O seletor de catálogo só existe com catálogo disponível.
            val fields = buildList {
                add(DiscoverFilter.KIND)
                if (state.catalogs.isNotEmpty() && !state.isKidsProfile) add(DiscoverFilter.CATALOG)
                if (state.selectedCatalog == null) {
                    add(DiscoverFilter.SORT)
                    add(DiscoverFilter.GENRES)
                    add(DiscoverFilter.YEAR)
                    add(DiscoverFilter.CERT)
                    add(DiscoverFilter.RATING)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tvPad),
                horizontalArrangement = Arrangement.spacedBy(0.8.r),
            ) {
                fields.forEach { field ->
                    val value = when (field) {
                        DiscoverFilter.KIND -> if (movie) "Filmes" else "Séries"
                        DiscoverFilter.CATALOG -> state.catalogLabel
                        DiscoverFilter.SORT -> sorts.firstOrNull { it.id == state.sort }?.label ?: "Populares"
                        DiscoverFilter.GENRES -> genreValue
                        DiscoverFilter.YEAR -> state.year?.toString() ?: "Todos"
                        DiscoverFilter.CERT -> if (!movie) "Para filmes" else when (state.cert) { "" -> "Todas"; "L" -> "Livre"; else -> "${state.cert} anos" }
                        DiscoverFilter.RATING -> state.minRating?.let { "${it.toInt()} ou mais" } ?: "Todas"
                    }
                    val selected = when (field) {
                        DiscoverFilter.KIND -> !movie
                        DiscoverFilter.CATALOG -> state.selectedCatalog != null
                        DiscoverFilter.SORT -> state.sort != DiscoverViewModel.SORT_POPULAR
                        DiscoverFilter.GENRES -> state.selectedGenres.isNotEmpty()
                        DiscoverFilter.YEAR -> state.year != null
                        DiscoverFilter.CERT -> state.cert.isNotBlank()
                        DiscoverFilter.RATING -> state.minRating != null
                    }
                    FilterSelector(
                        field.title, value, selected,
                        busy = state.loading && pendingField == field,
                        enabled = (field != DiscoverFilter.CERT || movie) && (field != DiscoverFilter.GENRES || state.genres.isNotEmpty()),
                        modifier = Modifier.weight(if (field == DiscoverFilter.SORT || field == DiscoverFilter.GENRES || field == DiscoverFilter.CATALOG) 1.2f else 1f)
                            .focusRequester(requesters.getValue(field)),
                        onClick = { picker = field },
                    )
                }
            }
            // Filtros do catálogo de addon (Lote 14): um seletor por extra declarado.
            // Obrigatório não tem "Todos"; opcional tem.
            if (state.selectedCatalog != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = tvPad).padding(top = 0.8.r),
                    horizontalArrangement = Arrangement.spacedBy(0.8.r),
                ) {
                    state.addonExtras.forEach { extra ->
                        val current = state.selectedExtras[extra.name].orEmpty()
                        val shown = current.ifBlank { if (extra.isRequired) extra.options.firstOrNull().orEmpty() else "Todos" }
                        val isDefault = extra.isRequired && current == extra.options.firstOrNull() ||
                            !extra.isRequired && current.isBlank()
                        FilterSelector(
                            extra.name, shown, !isDefault,
                            busy = false, enabled = extra.options.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                                .focusRequester(extraRequesters.getOrPut(extra.name) { FocusRequester() }),
                            onClick = { extraPicker = extra.name },
                        )
                    }
                }
            }
            val active = if (state.selectedCatalog != null) {
                // Só o que mudou do padrão (obrigatório na 1ª opção não conta, como no contador).
                state.selectedExtras.filter { (name, value) ->
                    val extra = state.addonExtras.firstOrNull { it.name == name }
                    extra != null && (!extra.isRequired || value != extra.options.firstOrNull())
                }.values.toList()
            } else genres + listOfNotNull(
                state.year?.toString(), state.cert.takeIf { it.isNotBlank() }?.let { if (it == "L") "Livre" else "$it anos" },
                state.minRating?.let { "Nota ${it.toInt()} ou mais" },
            )
            Text(
                active.joinToString("  ·  ").ifBlank { "Escolha um filtro para encontrar o que você quer assistir" },
                color = if (active.isEmpty()) TextTertiary else Accent, fontSize = 0.95.rs, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tvPad).padding(top = 1.r, bottom = 0.8.r),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> TvSpinner("Atualizando catálogo", Modifier.align(Alignment.Center))
                    state.connectionError -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(3.5.r))
                        Text("Catálogo vazio", color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                            fontSize = 1.7.rs, modifier = Modifier.padding(top = 1.r))
                        Text("Confira a conexão ou escolha outro catálogo", color = TextSecondary, fontSize = 1.1.rs,
                            modifier = Modifier.padding(top = 0.5.r))
                    }
                    state.emptyCatalog -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(3.5.r))
                        Text("Catálogo vazio", color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                            fontSize = 1.7.rs, modifier = Modifier.padding(top = 1.r))
                        Text("Esse catálogo não trouxe títulos com o filtro escolhido. Tente outro filtro ou outro catálogo.", color = TextSecondary, fontSize = 1.1.rs,
                            modifier = Modifier.padding(top = 0.5.r))
                    }
                    state.noTmdbMatch -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(3.5.r))
                        Text("Sem par no TMDB", color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                            fontSize = 1.7.rs, modifier = Modifier.padding(top = 1.r))
                        Text("Os títulos desse catálogo não existem no TMDB, o que é comum em catálogo de anime", color = TextSecondary, fontSize = 1.1.rs,
                            modifier = Modifier.padding(top = 0.5.r))
                    }
                    state.items.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(3.5.r))
                        Text("Nada com esses filtros", color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                            fontSize = 1.7.rs, modifier = Modifier.padding(top = 1.r))
                        Text("Tente outro gênero, ano ou classificação", color = TextSecondary, fontSize = 1.1.rs,
                            modifier = Modifier.padding(top = 0.5.r))
                    }
                    else -> TvScrollArea(1.r) {
                        LazyVerticalGrid(
                            GridCells.Fixed(8), state = grid,
                            contentPadding = PaddingValues(start = tvPad, end = tvPad, top = 0.7.r, bottom = 3.r),
                            horizontalArrangement = Arrangement.spacedBy(1.r), verticalArrangement = Arrangement.spacedBy(1.5.r),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.items, key = { "${it.type}:${it.tmdbId}" }) { item ->
                                TvPoster(item, { onItemClick(item) }, width = Dp.Unspecified)
                            }
                        }
                    }
                }
            }
        }
        picker?.let { field ->
            val options = when (field) {
                DiscoverFilter.KIND -> listOf(FilterChoice("movie", "Filmes"), FilterChoice("series", "Séries"))
                DiscoverFilter.CATALOG -> listOf(FilterChoice("", "TMDB · Filtros avançados")) +
                    state.catalogs.map { FilterChoice(it.key, it.label) }
                DiscoverFilter.SORT -> sorts
                DiscoverFilter.GENRES -> state.genres.map { FilterChoice(it.id.toString(), it.name) }
                DiscoverFilter.YEAR -> listOf(FilterChoice("", "Todos os anos")) + (year downTo 1990).map { FilterChoice(it.toString(), it.toString()) }
                DiscoverFilter.CERT -> listOf(FilterChoice("", "Todas as idades")) + DiscoverViewModel.CERTS.map { FilterChoice(it, if (it == "L") "Livre" else "$it anos") }
                DiscoverFilter.RATING -> listOf(FilterChoice("", "Todas as notas")) + (6..9).map { FilterChoice(it.toString(), "$it ou mais") }
            }
            val selected = when (field) {
                DiscoverFilter.KIND -> setOf(if (movie) "movie" else "series")
                DiscoverFilter.CATALOG -> setOf(state.selectedCatalog?.key.orEmpty())
                DiscoverFilter.SORT -> setOf(state.sort)
                DiscoverFilter.GENRES -> state.selectedGenres.map { it.toString() }.toSet()
                DiscoverFilter.YEAR -> setOf(state.year?.toString().orEmpty())
                DiscoverFilter.CERT -> setOf(state.cert)
                DiscoverFilter.RATING -> setOf(state.minRating?.toInt()?.toString().orEmpty())
            }
            fun close() { picker = null; restoreFocus = field }
            FilterPicker(field.title, options, selected, multi = field == DiscoverFilter.GENRES,
                columns = if (field == DiscoverFilter.GENRES) 3 else if (field == DiscoverFilter.YEAR) 4 else 2,
                onDismiss = ::close) { values ->
                if (values == selected) { close(); return@FilterPicker }
                pendingField = field
                clearing = false
                val value = values.firstOrNull().orEmpty()
                when (field) {
                    DiscoverFilter.KIND -> viewModel.setKind(if (value == "movie") DiscoverViewModel.Kind.MOVIE else DiscoverViewModel.Kind.SERIES)
                    DiscoverFilter.CATALOG -> viewModel.selectCatalog(state.catalogs.firstOrNull { it.key == value })
                    DiscoverFilter.SORT -> viewModel.setSort(value)
                    DiscoverFilter.GENRES -> viewModel.setGenres(values.mapNotNull { it.toIntOrNull() }.toSet())
                    DiscoverFilter.YEAR -> if (value.toIntOrNull() != state.year) viewModel.setYear(value.toIntOrNull())
                    DiscoverFilter.CERT -> if (value != state.cert) viewModel.setCert(value)
                    DiscoverFilter.RATING -> if (value.toFloatOrNull() != state.minRating) viewModel.setMinRating(value.toFloatOrNull())
                }
                close()
            }
        }
        // Picker de um filtro do catálogo de addon (obrigatório sem "Todos").
        extraPicker?.let { name ->
            val extra = state.addonExtras.firstOrNull { it.name == name }
            if (extra != null) {
                val current = state.selectedExtras[name].orEmpty()
                val options = (if (extra.isRequired) emptyList() else listOf(FilterChoice("", "Todos"))) +
                    extra.options.map { FilterChoice(it, it) }
                val selected = setOf(current)
                fun close() { extraPicker = null; restoreExtra = name }
                FilterPicker(name, options, selected, multi = false, columns = 2, onDismiss = ::close) { values ->
                    val value = values.firstOrNull().orEmpty()
                    if (value != current) viewModel.setExtra(name, value)
                    close()
                }
            }
        }
    }
}

@Composable
private fun FilterSelector(
    label: String, value: String, selected: Boolean, enabled: Boolean, busy: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(0.65.r)
    val scale = tvFocusScale(focused, 1.015f)
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.height(4.7.r)
            .clip(shape).background(if (selected) AccentSoft else BackgroundCard)
            .border(if (focused) 0.18.r else 0.06.r, if (focused) Color.White else if (selected) Accent.copy(alpha = .55f) else Border, shape)
            .focusProperties { canFocus = enabled }.onFocusChanged { focused = it.isFocused }
            .tvClick({ if (!busy) onClick() }, enabled = enabled).padding(horizontal = 1.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = if (selected) Accent else TextTertiary, fontSize = 0.72.rs,
                fontWeight = FontWeight.Bold, fontFamily = DisplayFont, letterSpacing = 0.08.rs, maxLines = 1)
            Text(value, color = if (enabled) TextPrimary else TextTertiary, fontFamily = DisplayFont,
                fontWeight = FontWeight.SemiBold, fontSize = 1.05.rs, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.45.r))
        }
        if (busy) com.aotv.app.ui.common.SpinningIcon(tint = TextSecondary, size = 1.2.r)
        else Icon(Icons.Default.ExpandMore, null, tint = if (enabled) TextSecondary else TextTertiary, modifier = Modifier.padding(start = 0.4.r).size(1.2.r))
    }
}

@Composable
private fun FilterPicker(
    title: String, options: List<FilterChoice>, selected: Set<String>,
    multi: Boolean, columns: Int,
    onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit,
) {
    var draft by remember(title) { mutableStateOf(selected) }
    val firstIndex = options.indexOfFirst { it.id in selected }.coerceAtLeast(0)
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (firstIndex / columns) * columns)
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(title, options.size) { withFrameNanos { }; runCatching { first.requestFocus() } }
        Column(
            Modifier.width(62.r).heightIn(max = 39.r).clip(RoundedCornerShape(1.r))
                .background(BackgroundElevated).border(0.06.r, Border, RoundedCornerShape(1.r)).padding(1.8.r),
        ) {
            TvEyebrow(if (multi) "Selecione os gêneros que você gosta" else "Refine sua busca")
            Text(title, color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                fontSize = 2.rs, modifier = Modifier.padding(bottom = 1.2.r))
            TvScrollArea(0.5.r) {
                LazyVerticalGrid(
                    GridCells.Fixed(columns), state = grid, modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(0.6.r), verticalArrangement = Arrangement.spacedBy(0.6.r),
                    contentPadding = PaddingValues(0.3.r),
                ) {
                    items(options, key = { it.id }) { option ->
                        var focused by remember { mutableStateOf(false) }
                        val on = option.id in draft
                        val shape = RoundedCornerShape(0.55.r)
                        Row(
                            Modifier.height(4.r).clip(shape).background(if (on) AccentSoft else Surface2)
                                .border(if (focused) 0.16.r else 0.06.r, if (focused) Color.White else if (on) Accent else Color.Transparent, shape)
                                .then(if (options.indexOf(option) == firstIndex) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .tvClick({
                                    if (multi) draft = if (on) draft - option.id else draft + option.id
                                    else onConfirm(setOf(option.id))
                                }).padding(horizontal = 0.9.r),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, color = TextPrimary, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold,
                                fontSize = 1.rs, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (on) Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.padding(start = 0.5.r).size(1.2.r))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 1.2.r), horizontalArrangement = Arrangement.spacedBy(0.6.r, Alignment.End)) {
                TvButton("Cancelar", onDismiss)
                if (multi) {
                    TvButton("Limpar seleção", { draft = emptySet() })
                    TvButton("Aplicar gêneros", { onConfirm(draft) }, primary = true)
                }
            }
        }
    }
}
