package com.aotv.app.ui.movies

import androidx.compose.ui.focus.focusProperties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.GlassCircleButton
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.MediaPosterCard
import com.aotv.app.ui.common.pressScale
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaSearchViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<MovieItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _recents = MutableStateFlow(prefs.recentSearches)
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<List<MovieItem>> = _results.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Buscas anteriores, mostradas quando o campo está vazio. */
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    /** Apaga o histórico inteiro. */
    fun clearRecents() {
        prefs.recentSearches = emptyList()
        _recents.value = emptyList()
    }

    /** Tira uma consulta do histórico (X no chip). */
    fun removeRecent(q: String) {
        prefs.recentSearches = prefs.recentSearches.filterNot { it.equals(q, ignoreCase = true) }
        _recents.value = prefs.recentSearches
    }

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _results.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(400)
            val apiKey = prefs.tmdbApiKey
            if (apiKey.isBlank()) return@launch
            _isLoading.value = true
            val results = runCatching {
                tmdb.search(q, apiKey = apiKey).results
                    .filterNot { it.adult }
                    // Busca multi devolve PESSOAS também — viravam cards vazios no grid.
                    .filter { it.mediaType == null || it.mediaType == "movie" || it.mediaType == "tv" }
                    .map { it.toMovieItem() }
                    // Sem pôster ou sem título = card quebrado. Fora.
                    .filter { it.posterUrl.isNotBlank() && it.title.isNotBlank() }
            }.getOrDefault(emptyList())
            _results.value = results
            _isLoading.value = false
            // Só grava no histórico o que realmente achou algo (evita salvar termo digitado pela metade).
            if (results.isNotEmpty()) {
                prefs.pushRecentSearch(q)
                _recents.value = prefs.recentSearches
            }
        }
    }
}

@Composable
fun MediaSearchScreen(
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
    viewModel: MediaSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val tv = com.aotv.app.ui.common.LocalIsTv.current

    // TV. Teclado do controle à esquerda e resultados à direita, como o search.js do webOS.
    if (tv) {
        val firstKey = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { firstKey.requestFocus() } }
        Row(Modifier.fillMaxSize().background(Background).padding(top = 28.dp)) {
            Column(Modifier.width(400.dp).padding(start = 28.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SearchPill(value = query, onValueChange = viewModel::onQueryChange, readOnly = true)
                com.aotv.app.ui.common.TvKeyboard(
                    onKey = { viewModel.onQueryChange(com.aotv.app.ui.common.typeKey(query, it)) },
                    onBackspace = { viewModel.onQueryChange(query.dropLast(1)) },
                    onClear = { viewModel.onQueryChange("") },
                    first = firstKey,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SearchBody(query, results, isLoading, recents, viewModel, onItemClick)
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .topInsetsPadding(),
    ) {
        // ── Cabeçalho: voltar + pill de busca premium ─────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                size = 44.dp,
                iconTint = TextSecondary,
                contentDescription = "Voltar",
            )
            SearchPill(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
            )
        }
        SearchBody(query, results, isLoading, recents, viewModel, onItemClick)
    }
}

/** Resultados, buscas recentes e estados da busca. Igual no celular e na TV. */
@Composable
private fun ColumnScope.SearchBody(
    query: String,
    results: List<MovieItem>,
    isLoading: Boolean,
    recents: List<String>,
    viewModel: MediaSearchViewModel,
    onItemClick: (MovieItem) -> Unit,
) {
        // Barra fina de progresso: mantém o grid na tela enquanto rebusca (antes o spinner
        // de tela cheia apagava os resultados a cada tecla digitada).
        if (isLoading && results.isNotEmpty()) {
            LinearProgressIndicator(
                color = Accent,
                trackColor = BackgroundCard2,
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }

        when {
            // Spinner cheio só na PRIMEIRA busca (ainda sem nada pra mostrar).
            isLoading && results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                }
            }

            query.isBlank() -> {
                RecentSearches(
                    recents = recents,
                    onPick = viewModel::onQueryChange,
                    onRemove = viewModel::removeRecent,
                    onClearAll = viewModel::clearRecents,
                )
            }

            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AotvEmptyState(
                        icon = Icons.Outlined.LocalMovies,
                        title = "Sem resultados",
                        message = "Não encontramos nada para \"$query\". Tente outro termo.",
                    )
                }
            }

            else -> {
                Column(Modifier.fillMaxSize()) {
                    // Eyebrow + contagem de resultados
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 6.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "RESULTADOS",
                            color = Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ManropeFontFamily,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            results.size.toString(),
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ManropeFontFamily,
                            letterSpacing = 0.8.sp,
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 2.dp,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(results, key = { it.tmdbId }) { item ->
                            // Busca mistura filme e série, então a etiqueta diz qual é (igual ao webOS).
                            com.aotv.app.ui.common.PosterCard(item = item, onClick = { onItemClick(item) }, typeBadge = true)
                        }
                    }
                }
            }
        }
}

// ── Helpers visuais privados desta tela ──────────────────────────────────────

private fun posterSubtitle(item: MovieItem): String? {
    val tipo = if (item.type == MediaType.SERIES) "Série" else "Filme"
    return if (item.year.isNotBlank()) "$tipo · ${item.year}" else tipo
}

/**
 * Pill de busca no padrão premium: cápsula RoundedCornerShape(999) com fundo de
 * gradiente sutil, border Border que vira Accent ao focar, ícone de lupa,
 * campo de texto custom e botão de limpar quando há texto.
 */
@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Só mostra o texto (TV, digitado pelo teclado na tela). Fica fora do caminho do foco. */
    readOnly: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val borderColor = if (focused) Accent else Border

    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF2B3644) else Surface2)
            .border(if (focused) 2.dp else 0.dp, if (focused) borderColor else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) Accent else TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "Buscar filmes ou séries...",
                    color = TextTertiary,
                    fontSize = 15.sp,
                    fontFamily = ManropeFontFamily,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = src,
                cursorBrush = SolidColor(Accent),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth()
                    .then(if (readOnly) Modifier.focusProperties { canFocus = false } else Modifier),
            )
        }
        if (value.isNotEmpty() && !readOnly) {
            val clearSrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .pressScale(clearSrc)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
                    .clickable(
                        interactionSource = clearSrc,
                        indication = null,
                        onClick = { onValueChange("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Limpar",
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Badge de nota (estilo TV): pill translúcido dourado com estrela implícita,
 * texto Gold ExtraBold. Posicionado no topo-direita do poster.
 */
@Composable
private fun RatingBadge(rating: Double) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "★",
            color = Gold,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            String.format("%.1f", rating),
            color = Gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = ManropeFontFamily,
            letterSpacing = 0.3.sp,
        )
    }
}

// ── ESTADO SEM CONSULTA: buscas recentes ──────────────────────────────────────

/**
 * O que aparece quando o campo está vazio. Com histórico, mostra as últimas buscas em
 * chips (tocar repete a busca, X remove). Sem histórico, cai no AotvEmptyState centrado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AotvEmptyState(
                icon = Icons.Default.Search,
                title = "Busque filmes e séries",
                message = "Digite o nome de um título para descobrir onde assistir.",
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    "BUSCAS RECENTES",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = 1.2.sp,
                )
            }
            Text(
                "Limpar",
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recents.forEach { term -> RecentChip(term, onPick = { onPick(term) }, onRemove = { onRemove(term) }) }
        }
    }
}

/** Chip de busca anterior: texto repete a consulta, X tira do histórico. */
@Composable
private fun RecentChip(term: String, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Surface2)
            .clickable(onClick = onPick)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            term,
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remover $term do histórico",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
