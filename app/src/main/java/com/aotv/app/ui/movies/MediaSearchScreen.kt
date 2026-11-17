package com.aotv.app.ui.movies

import androidx.compose.ui.focus.focusProperties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.data.repository.DownloadRepository
import com.aotv.app.data.torrent.PastedLink
import com.aotv.app.data.torrent.PastedLinkKind
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.FilterChipPill
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
    private val downloads: DownloadRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<MovieItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _recents = MutableStateFlow(prefs.recentSearches)
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<List<MovieItem>> = _results.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Link colado na busca (Lote 18 item 3): magnet ou link direto de
     * vídeo, p/ tocar direto sem navegar até o título. Não-null = a tela
     * mostra o cartão "Link colado" no lugar da busca por título (a
     * busca TMDB nem dispara p/ ele). Null = busca normal.
     */
    private val _pastedLink = MutableStateFlow<PastedLink?>(null)
    val pastedLink: StateFlow<PastedLink?> = _pastedLink.asStateFlow()

    /** Descarta o link colado e limpa o campo (volta p/ a busca normal). */
    fun clearPastedLink() {
        _pastedLink.value = null
        _query.value = ""
        _results.value = emptyList()
    }

    /** Enfileira o download do link colado (só .mp4 direto é baixável). */
    fun queuePastedDownload() {
        val link = _pastedLink.value ?: return
        if (!link.downloadable) return
        viewModelScope.launch { downloads.enqueueDirectLink(link.raw, link.title) }
    }

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

    /** Última busca abortada por falta de chave TMDB. A tela mostra o aviso no lugar do "Sem resultados". */
    private val _noTmdbKey = MutableStateFlow(false)
    val noTmdbKey: StateFlow<Boolean> = _noTmdbKey.asStateFlow()

    fun onQueryChange(q: String) {
        _query.value = q
        // Link colado (Lote 18 item 3): magnet ou vídeo direto vira o
        // cartão "Link colado" — sem busca TMDB e sem gravar no histórico.
        val pasted = PastedLink.parse(q)
        if (pasted != null) {
            searchJob?.cancel()
            _results.value = emptyList()
            _noTmdbKey.value = false
            _isLoading.value = false
            _pastedLink.value = pasted
            return
        }
        _pastedLink.value = null
        if (q.isBlank()) { searchJob?.cancel(); _results.value = emptyList(); _noTmdbKey.value = false; return }
        launchSearch(q, SEARCH_DEBOUNCE_MS)
    }

    /**
     * Busca IMEDIATA com a query atual (botão de busca do teclado): cancela o
     * debounce e busca na hora, sem esperar. É o que faz a busca parecer "ao
     * vivo" igual à do Nuvio — sem isso, na internet lenta, os resultados só
     * apareciam depois de enviar.
     */
    fun searchNow() {
        val q = _query.value
        if (q.isBlank()) return
        // Tecla de busca com link colado: mesmo caminho do ao vivo.
        if (PastedLink.parse(q) != null) { onQueryChange(q); return }
        launchSearch(q, 0)
    }

    private fun launchSearch(q: String, delayMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val apiKey = prefs.tmdbApiKey
            if (apiKey.isBlank()) { _noTmdbKey.value = true; return@launch }
            _noTmdbKey.value = false
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

    companion object {
        /** Pausa após a última tecla antes de buscar (250 ms — ao vivo, sem afogar a rede). */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}

@Composable
fun MediaSearchScreen(
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
    // Lote 18 item 3 — tocar um magnet/link colado direto no player (sem
    // passar pelo detalhe): a navegação entrega o (url, título).
    onPlayUrl: (String, String) -> Unit = { _, _ -> },
    viewModel: MediaSearchViewModel = hiltViewModel(),
    // Lote 15 item 1 — Destaques da busca: a MESMA fonte do destaque da Início
    // (o ViewModel da Início + heroCandidates, só leitura). Segunda instância
    // do ViewModel (a busca é outro destino do nav): carrega as fileiras de
    // novo ao abrir a busca — ver caveat no relatório do lote.
    homeVm: MoviesHomeViewModel = hiltViewModel(),
    // Lote 15 item 1 — Descobrir embutido na busca (mesmo ViewModel da tela Descobrir).
    discoverVm: DiscoverViewModel = hiltViewModel(),
    // Lote 12 (v1.22.0 — Frente 1): query pronta (ex.: título de um
    // programa do guia, "Buscar nos addons") — busca na hora, sem digitar.
    initialQuery: String = "",
) {
    // A query inicial dispara a busca ao vivo do ViewModel (debounce de 250 ms).
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery)
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val tv = com.aotv.app.ui.common.LocalIsTv.current

    // ── Destaques da busca (Lote 15 item 1) ──────────────────────────────
    // O mesmo cálculo da Início (MoviesHomeScreen.kt:561-581): a fileira
    // escolhida no Personalizar Início, ou o automático (em alta). Só leitura.
    val homeState by homeVm.uiState.collectAsStateWithLifecycle()
    val heroSource = heroCandidates(homeState)
    LaunchedEffect(heroSource.map { it.tmdbId }) {
        heroSource.take(10).forEach {
            val series = it.type == MediaType.SERIES
            if (it.backdropUrl.isBlank()) homeVm.ensureHeroBackdrop(it.tmdbId, series)
            homeVm.ensureHeroLogo(it.tmdbId, series)
        }
    }
    val heroItems = remember(heroSource, homeState.heroBackdrops) {
        heroSource.mapNotNull { item ->
            val bd = item.backdropUrl.ifBlank { homeState.heroBackdrops[item.tmdbId].orEmpty() }
            if (bd.isBlank()) null else item.copy(backdropUrl = bd)
        }.take(8)
    }
    val discoverState by discoverVm.uiState.collectAsStateWithLifecycle()

    // TV. Teclado do controle à esquerda e resultados à direita, como o search.js do webOS.
    if (tv) {
        val firstKey = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { firstKey.requestFocus() } }
        Row(Modifier.fillMaxSize().background(Background).padding(top = 28.dp)) {
            Column(Modifier.width(400.dp).padding(start = 28.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SearchPill(value = query, onValueChange = viewModel::onQueryChange, readOnly = true, onSearch = viewModel::searchNow)
                com.aotv.app.ui.common.TvKeyboard(
                    onKey = { viewModel.onQueryChange(com.aotv.app.ui.common.typeKey(query, it)) },
                    onBackspace = { viewModel.onQueryChange(query.dropLast(1)) },
                    onClear = { viewModel.onQueryChange("") },
                    first = firstKey,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SearchBody(query, results, isLoading, recents, viewModel, onItemClick,
                    heroItems, homeState.heroLogos, discoverState, discoverVm, onPlayUrl)
            }
        }
        return
    }

    // Busca por voz (Lote 3, item 1) — só no celular. Na TV não há microfone, então
    // o ramo de TV abaixo nem cria o launcher e a pill não mostra o botão. A
    // transcrição cai em onQueryChange e a busca ao vivo dispara como se digitada.
    // É da sessão: nada é salvo em pref.
    val voiceSearch = rememberVoiceSearch(onResult = viewModel::onQueryChange)

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
                onSearch = viewModel::searchNow,
                // Aparelho sem reconhecimento de voz: sem botão, sem erro.
                onVoiceClick = if (voiceSearch.available) voiceSearch.start else null,
                voiceListening = voiceSearch.listening,
            )
        }
        SearchBody(query, results, isLoading, recents, viewModel, onItemClick,
            heroItems, homeState.heroLogos, discoverState, discoverVm, onPlayUrl)
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
    heroItems: List<MovieItem>,
    heroLogos: Map<Int, String>,
    discoverState: DiscoverViewModel.UiState,
    discoverVm: DiscoverViewModel,
    onPlayUrl: (String, String) -> Unit,
) {
        // Link colado (Lote 18 item 3): o cartão fica acima de tudo — a
        // busca por título nem aparece enquanto houver um link colado.
        val pastedLink by viewModel.pastedLink.collectAsStateWithLifecycle()
        pastedLink?.let { link ->
            PastedLinkCard(
                link = link,
                onPlay = { onPlayUrl(link.raw, link.title) },
                onDownload = viewModel::queuePastedDownload,
                onDismiss = viewModel::clearPastedLink,
            )
        }
        // Barra fina de progresso: mantém o grid na tela enquanto rebusca (antes o spinner
        // de tela cheia apagava os resultados a cada tecla digitada).
        val noTmdbKey by viewModel.noTmdbKey.collectAsStateWithLifecycle()
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
                // Lote 15 item 1 — com o campo vazio, a busca vira a aba do Nuvio:
                // recentes (só com histórico) + Destaques + Descobrir, em vez
                // do estado vazio de antes.
                SearchEmptyState(
                    recents = recents,
                    viewModel = viewModel,
                    noTmdbKey = noTmdbKey,
                    heroItems = heroItems,
                    heroLogos = heroLogos,
                    discoverState = discoverState,
                    discoverVm = discoverVm,
                    onItemClick = onItemClick,
                )
            }

            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (noTmdbKey) {
                        AotvEmptyState(
                            icon = Icons.Outlined.LocalMovies,
                            title = "Sem chave TMDB",
                            message = "Adicione sua chave TMDB em Configurações → TMDB para buscar.",
                        )
                    } else {
                        AotvEmptyState(
                            icon = Icons.Outlined.LocalMovies,
                            title = "Sem resultados",
                            message = "Não encontramos nada para \"$query\". Tente outro termo.",
                        )
                    }
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

/**
 * Cartão "Link colado" (Lote 18 item 3): magnet ou link direto de vídeo
 * colado na busca — toca direto no player sem passar pelo detalhe. O
 * "Baixar" só aparece p/ .mp4 direto (o DownloadManager só baixa .mp4);
 * magnet toca via torrent e não é baixável como arquivo.
 */
@Composable
private fun PastedLinkCard(
    link: PastedLink,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    var downloadQueued by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(BackgroundCard2).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Link, null, tint = Accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                link.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary,
                maxLines = 2,
            )
            Text(
                if (link.kind == PastedLinkKind.MAGNET) "Magnet — toca direto via torrent (streaming, sem baixar o arquivo)"
                else "Link direto — toca sem buscar o título",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Text("Tocar agora", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
                if (link.downloadable) {
                    TextButton(
                        onClick = { onDownload(); downloadQueued = true },
                        enabled = !downloadQueued,
                    ) {
                        Text(if (downloadQueued) "Na fila ✓" else "Baixar", color = Accent)
                    }
                }
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, "Descartar", tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── ESTADO SEM CONSULTA: Destaques + Descobrir (Lote 15 item 1) ─────────────

/**
 * O que aparece quando o campo está vazio, no formato da aba de Busca do Nuvio:
 * buscas recentes (só com histórico) + carrossel de Destaques (a mesma fonte do
 * destaque da Início) + Descobrir com filtros compactos e grade de pôsteres
 * (mesmo ViewModel da tela Descobrir, com scroll infinito).
 */
@Composable
private fun SearchEmptyState(
    recents: List<String>,
    viewModel: MediaSearchViewModel,
    noTmdbKey: Boolean,
    heroItems: List<MovieItem>,
    heroLogos: Map<Int, String>,
    discoverState: DiscoverViewModel.UiState,
    discoverVm: DiscoverViewModel,
    onItemClick: (MovieItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Scroll infinito do Descobrir, igual ao da tela Descobrir.
    val nearEnd by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) discoverVm.loadMore() }
    var genrePickerOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (genrePickerOpen) GenrePickerDialog(discoverState, discoverVm) { genrePickerOpen = false }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        containerColor = BackgroundCard,
        title = { Text("Limpar buscas recentes?", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Text("O histórico de buscas será apagado.", color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = { viewModel.clearRecents(); confirmClear = false }) { Text("Limpar", color = DangerText) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = TextSecondary) } },
    )

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchRecentsCompact(
                        recents = recents,
                        onPick = viewModel::onQueryChange,
                        onRemove = viewModel::removeRecent,
                        onClearRequest = { confirmClear = true },
                    )
                }
            }
            if (heroItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchHeroSection(heroItems, heroLogos, onItemClick)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchDiscoverHeader(discoverState, discoverVm, onGenreClick = { genrePickerOpen = true })
            }
            when {
                discoverState.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                    }
                }
                discoverState.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        when {
                            noTmdbKey -> AotvEmptyState(
                                icon = Icons.Outlined.LocalMovies,
                                title = "Sem chave TMDB",
                                message = "Adicione sua chave TMDB em Configurações → TMDB para ver o Descobrir.",
                            )
                            discoverState.connectionError -> AotvEmptyState(
                                icon = Icons.Outlined.LocalMovies,
                                title = "Catálogo vazio",
                                message = "Confira a conexão ou escolha outro catálogo",
                            )
                            discoverState.emptyCatalog -> AotvEmptyState(
                                icon = Icons.Outlined.LocalMovies,
                                title = "Catálogo vazio",
                                message = "Esse catálogo não trouxe títulos com o filtro escolhido. Tente outro filtro ou outro catálogo.",
                            )
                            discoverState.noTmdbMatch -> AotvEmptyState(
                                icon = Icons.Outlined.LocalMovies,
                                title = "Sem par no TMDB",
                                message = "Os títulos desse catálogo não existem no TMDB, o que é comum em catálogo de anime",
                            )
                            else -> AotvEmptyState(
                                icon = Icons.Outlined.LocalMovies,
                                title = "Nada com esses filtros",
                                message = "Tire alguma condição e tente de novo. Gênero somado a nota alta e ano fechado costuma zerar a lista.",
                            )
                        }
                    }
                }
                else -> {
                    items(discoverState.items, key = { it.tmdbId }) { item ->
                        com.aotv.app.ui.common.PosterCard(item = item, onClick = { onItemClick(item) })
                    }
                    if (discoverState.loadingMore) {
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

/** Recentes compactos no topo da busca vazia: título + Limpar + chips (sem o estado vazio). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchRecentsCompact(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearRequest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
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
                    .clickable(onClick = onClearRequest)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recents.forEach { term -> RecentChip(term, onPick = { onPick(term) }, onRemove = { onRemove(term) }) }
        }
    }
}

/** Seção de Destaques: eyebrow + carrossel (a mesma fonte do herói da Início). */
@Composable
private fun SearchHeroSection(
    items: List<MovieItem>,
    logos: Map<Int, String>,
    onItemClick: (MovieItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "DESTAQUES",
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = ManropeFontFamily,
            letterSpacing = 1.2.sp,
        )
        SearchHeroCarousel(items, logos, onItemClick)
    }
}

/** Título do destaque (texto; o logo-art entra quando há). */
@Composable
private fun SearchHeroTitle(title: String) {
    Text(
        title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
        fontFamily = SoraFontFamily, maxLines = 2, lineHeight = 29.sp, letterSpacing = (-0.5).sp,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Carrossel de Destaques, versão compacta do herói da Início (mesma arte, mesmo
 * gradiente, sem trailer e sem os botões — a busca só apresenta o título; o
 * toque abre o detalhe). Avança sozinho a cada 6 s.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchHeroCarousel(
    items: List<MovieItem>,
    logos: Map<Int, String>,
    onItemClick: (MovieItem) -> Unit,
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items.size })
    LaunchedEffect(pagerState.settledPage, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        kotlinx.coroutines.delay(6000)
        runCatching { pagerState.animateScrollToPage((pagerState.settledPage + 1) % items.size) }
    }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    // Mais baixo que o herói da Início: na busca o carrossel é uma seção, não a tela.
    val heroHeight = (com.aotv.app.ui.common.homeHeroHeight(config.screenWidthDp, config.screenHeightDp, config.fontScale) * 0.72f).dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            val item = items[index]
            Box(Modifier.fillMaxSize().clickable { onItemClick(item) }) {
                coil.compose.AsyncImage(
                    model = item.backdropUrl.ifBlank { item.posterUrl },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.72f to Background.copy(alpha = 0.78f),
                            1f to Background,
                        ),
                    ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val logo = logos[item.tmdbId].orEmpty()
                    if (logo.isNotBlank()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = logo,
                            contentDescription = item.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier.fillMaxWidth(0.72f).heightIn(max = 64.dp),
                            error = { SearchHeroTitle(item.title) },
                            loading = { SearchHeroTitle(item.title) },
                        )
                    } else {
                        SearchHeroTitle(item.title)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (item.rating > 0) Text(
                            "★ ${"%.1f".format(item.rating)}", color = Gold, fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily,
                        )
                        if (item.year.isNotBlank()) Text(
                            item.year, color = TextSecondary, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = ManropeFontFamily,
                        )
                        Text(
                            if (item.type == MediaType.SERIES) "Série" else "Filme",
                            color = TextSecondary, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = ManropeFontFamily,
                        )
                    }
                }
            }
        }
        // Bolinhas da página atual.
        if (items.size > 1) Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(if (i == pagerState.currentPage) 16.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

/** Título "Descobrir" + os 3 filtros compactos do Nuvio (tipo / ordem / gênero). */
@Composable
private fun SearchDiscoverHeader(
    state: DiscoverViewModel.UiState,
    vm: DiscoverViewModel,
    onGenreClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Descobrir",
                color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = SoraFontFamily, letterSpacing = (-0.4).sp,
            )
            if (state.totalResults > 0) {
                Text(
                    "%,d títulos".format(state.totalResults).replace(',', '.'),
                    color = TextTertiary, fontSize = 12.sp, fontFamily = ManropeFontFamily,
                )
            }
        }
        // Catálogo de addon selecionado: o rótulo honesto no lugar dos filtros do TMDB.
        if (state.selectedCatalog != null) {
            Text(
                state.catalogLabel,
                color = TextTertiary, fontSize = 13.sp, fontFamily = ManropeFontFamily,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isMovie = state.kind == DiscoverViewModel.Kind.MOVIE
            FilterChipPill(
                if (isMovie) "Filmes" else "Séries", false,
                onClick = { vm.setKind(if (isMovie) DiscoverViewModel.Kind.SERIES else DiscoverViewModel.Kind.MOVIE) },
                icon = Icons.Default.ArrowDropDown,
            )
            if (state.selectedCatalog == null) {
                FilterChipPill(
                    discoverSortLabel(state), false,
                    onClick = { vm.setSort(discoverNextSort(state)) },
                    icon = Icons.Default.ArrowDropDown,
                )
                val genreLabel = when {
                    state.selectedGenres.isEmpty() -> "Todos os Gêneros"
                    state.selectedGenres.size == 1 ->
                        state.genres.firstOrNull { it.id in state.selectedGenres }?.name ?: "1 gênero"
                    else -> "${state.selectedGenres.size} gêneros"
                }
                FilterChipPill(
                    genreLabel, state.selectedGenres.isNotEmpty(),
                    onClick = onGenreClick,
                    icon = Icons.Default.ArrowDropDown,
                )
            }
        }
    }
}

/** Rótulo do chip de ordem no Descobrir embutido. */
private fun discoverSortLabel(state: DiscoverViewModel.UiState): String = when (state.sort) {
    DiscoverViewModel.SORT_TOP_RATED -> "Melhor nota"
    DiscoverViewModel.SORT_RELEASE_MOVIE, DiscoverViewModel.SORT_RELEASE_TV -> "Lançamentos"
    DiscoverViewModel.SORT_TITLE -> "Título A-Z"
    else -> "Popular"
}

/** Próxima ordem do chip (Popular → Melhor nota → Lançamentos → Título A-Z). */
private fun discoverNextSort(state: DiscoverViewModel.UiState): String {
    val release = if (state.kind == DiscoverViewModel.Kind.MOVIE) DiscoverViewModel.SORT_RELEASE_MOVIE
        else DiscoverViewModel.SORT_RELEASE_TV
    val order = listOf(
        DiscoverViewModel.SORT_POPULAR, DiscoverViewModel.SORT_TOP_RATED, release, DiscoverViewModel.SORT_TITLE,
    )
    val idx = order.indexOf(state.sort).takeIf { it >= 0 } ?: 0
    return order[(idx + 1) % order.size]
}

/** Diálogo de gêneros do Descobrir embutido (multi-seleção, igual ao painel da tela Descobrir). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenrePickerDialog(
    state: DiscoverViewModel.UiState,
    vm: DiscoverViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Gêneros", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            if (state.genres.isEmpty()) {
                Text("Carregando gêneros…", color = TextSecondary, fontSize = 14.sp, fontFamily = ManropeFontFamily)
            } else {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.genres.forEach { g ->
                        FilterChipPill(g.name, g.id in state.selectedGenres, onClick = { vm.toggleGenre(g.id) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Pronto", color = Accent) }
        },
        dismissButton = if (state.selectedGenres.isNotEmpty()) {
            { TextButton(onClick = { vm.clearFilters() }) { Text("Limpar", color = TextSecondary) } }
        } else null,
    )
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
    /** Busca imediata (botão de busca do teclado). */
    onSearch: (() -> Unit)? = null,
    /** Busca por voz (só celular: null na TV e em aparelho sem reconhecedor). */
    onVoiceClick: (() -> Unit)? = null,
    /** O reconhecedor do sistema está aberto: o microfone acende. */
    voiceListening: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val borderColor = if (focused) Accent else Border

    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) SearchFocused else Surface2)
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
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch?.invoke() },
                ),
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth()
                    .then(if (readOnly) Modifier.focusProperties { canFocus = false } else Modifier),
            )
        }
        // Microfone (Lote 3, item 1): só no celular editável — na TV readOnly=true
        // e onVoiceClick é null, então o botão nunca aparece lá. Ouvindo, o botão
        // acende em Accent; parado, fica igual ao limpar.
        if (onVoiceClick != null && !readOnly) {
            val micSrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .pressScale(micSrc)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (voiceListening) Accent else FillSubtle)
                    .clickable(
                        interactionSource = micSrc,
                        indication = null,
                        onClick = onVoiceClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (voiceListening) "Ouvindo…" else "Buscar por voz",
                    tint = if (voiceListening) OnAccent else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (value.isNotEmpty() && !readOnly) {
            val clearSrc = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .pressScale(clearSrc)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(FillSubtle)
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
