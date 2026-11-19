package com.aotv.app.ui.movies

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.local.HomeDisplayPrefs
import com.aotv.app.data.local.HeroStyle
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.Badge
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.GlassCircleButton
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.MediaPosterCard
import com.aotv.app.ui.common.ScreenTopHeader
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.aotv.app.data.sports.SportsGame
import kotlinx.coroutines.delay
import com.aotv.app.ui.theme.*
import com.aotv.app.data.local.SystemRow
import com.aotv.app.ui.lote24.FolderHeaderRow
import com.aotv.app.ui.lote24.HeroAutoRotate
import com.aotv.app.ui.lote24.HomeFolder24
import com.aotv.app.ui.lote24.MkChannelRow
import com.aotv.app.ui.lote24.MkChannelSource
import com.aotv.app.ui.lote24.PeakCinemaRow
import com.aotv.app.ui.lote24.heroAutoRotateEffective
import com.aotv.app.ui.lote24.pickMkChannelTargets

@Composable
fun MoviesHomeScreen(
    onItemClick: (MovieItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLibraryClick: () -> Unit = {},
    onDiscoverClick: () -> Unit = {},
    onCustomize: () -> Unit = {},
    onFolderClick: (collectionId: String, folderId: String) -> Unit = { _, _ -> },
    /** Modo aleatório (Lote 2): episódio sorteado abre DIRETO no player. */
    onPlayEpisode: (MovieItem, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    /** Ao vivo (Lote 8): jogo da fileira "Próximos" abre o player do canal. Sem ele, o jogo só grava. */
    onPlayLive: (String) -> Unit = {},
    /** On Demand do Xtream (acréscimo v1.26.0): pôster da fileira abre o detalhe no On Demand. */
    onVodClick: (com.aotv.app.data.live.VodItem, Boolean) -> Unit = { _, _ -> },
    /** "Ver tudo" da fileira On Demand: abre a tela On Demand na aba certa. */
    onOpenOnDemand: (Boolean) -> Unit = {},
    viewModel: MoviesHomeViewModel = hiltViewModel(),
    randomVm: RandomEpisodeViewModel = hiltViewModel(),
    /** Minha Lista p/ o MK Channel (item 5, Lote 24): a fonte watchlist. */
    minhaListaVm: MinhaListaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // "Ver Tudo" → grid daquela query; null = home (hero + fileiras).
    // Fonte da verdade é o ViewModel (sobrevive ao ir pro detalhe e voltar). Antes isso era um
    // remember local, que morria na navegação e jogava o usuário de volta pra home inicial.
    val gridMode = state.gridQuery
    val scope = rememberCoroutineScope()
    // Trailer aberto pela fileira "Últimos Trailers". Guarda TAMBÉM o card que está tocando,
    // pra saber qual é o próximo da fileira e qual título abrir no "Ver título".
    var trailerExo by remember { mutableStateOf<com.aotv.app.data.remote.YoutubeTrailer.TrailerStream?>(null) }
    var trailerCard by remember { mutableStateOf<MoviesHomeViewModel.TrailerCard?>(null) }
    var trailerLoading by remember { mutableStateOf(false) }

    // Abre um card da fileira no player de trailer.
    fun playTrailer(card: MoviesHomeViewModel.TrailerCard) {
        trailerCard = card
        trailerLoading = true
        scope.launch {
            trailerExo = viewModel.trailerStream(card.videoKey)
            trailerLoading = false
            // Sem stream (extração falhou) → não deixa o card preso "tocando".
            if (trailerExo == null) trailerCard = null
        }
    }

    // Callbacks estáveis: lambda nova a cada composição tornava as fileiras
    // não-puláveis (a Início inteira recompunha a cada emissão do UiState).
    val seeMore = remember(viewModel) { { q: MoviesHomeViewModel.CatalogQuery -> viewModel.openCatalog(q) } }
    val playTrailerFn = remember { { card: MoviesHomeViewModel.TrailerCard -> playTrailer(card) } }

    // Coleções do perfil (p/ a grade "Ver Tudo" de pastas, Lote 19).
    val collections by viewModel.collectionsFlow.collectAsStateWithLifecycle()
    // Data de hoje p/ "Ocultar conteúdo não lançado" nas grades (o mesmo
    // filtro das fileiras, via [isUnreleased]).
    val today = remember(state.hideUnreleased) { todayIso() }
    // Gravação de jogo (Lote 8): o botão "Gravar" do card — da fileira e da
    // grade "Ver Tudo" de Próximos — abre o diálogo. O estado vive aqui (e
    // não na HomeSectionsTab) porque a grade é irmã da fileira, não filha.
    var recordGame by remember { mutableStateOf<SportsGame?>(null) }
    recordGame?.let { game ->
        RecordProgramDialog(
            channelId = game.channelId, channelName = game.channelName, channelUrl = game.channelUrl,
            channelHeaders = game.channelHeaders, programTitle = game.title,
            startMs = game.startMs, stopMs = game.stopMs,
            onDismiss = { recordGame = null },
            onScheduled = { recordGame = null },
        )
    }

    // ── Lote 25 (v1.35.0), item 4 — Tour de boas-vindas ───────────────────
    // Primeira abertura após a v1.35.0 (pref global) ou pedido (paleta /
    // Configurações → Aparência). Fechar marca a pref como vista.
    val tourContext = LocalContext.current
    val tourPrefs = remember(tourContext) { com.aotv.app.data.local.PlatformPrefs(tourContext) }
    var showTour by remember { mutableStateOf(false) }
    val tourRequested by OnboardingTourRequest.show.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!tourPrefs.onboardingTourSeenV1) showTour = true }
    LaunchedEffect(tourRequested) { if (tourRequested) showTour = true }
    if (showTour) {
        OnboardingTourDialog(onDone = {
            showTour = false
            OnboardingTourRequest.consume()
            tourPrefs.onboardingTourSeenV1 = true
        })
    }

    // SEM bottom bar aqui — a barra é a única do MoviesNavigation (Início, Descobrir, Minha Lista, Downloads, Busca e Config).
    Box(Modifier.fillMaxSize().background(Background)) {
        when {
            // Esqueleto no formato da Início (destaque + fileiras) no lugar do spinner.
            state.isLoading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                val config = androidx.compose.ui.platform.LocalConfiguration.current
                com.aotv.app.ui.common.SkeletonBox(Modifier.fillMaxWidth().height(com.aotv.app.ui.common.homeHeroHeight(config.screenWidthDp, config.screenHeightDp, config.fontScale).dp), radius = 0.dp)
                repeat(3) { com.aotv.app.ui.common.SkeletonRail() }
            }
            gridMode != null -> {
                // BACK do sistema fecha o grid em vez de sair da home.
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                CatalogGridView(state, viewModel, gridMode.title, onItemClick, onBack = { viewModel.closeCatalog() })
            }
            // Grades "Ver Tudo" de fileira que não é TMDB (Lote 19, v1.29.0):
            // addon, TV de addon, lista local, trailers, coleção e próximos.
            // O BACK fecha a grade em vez de sair da home, como no grid TMDB.
            state.addonGridKey != null -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                AddonGridView(state, viewModel, state.addonGridTitle, today, onItemClick, onBack = { viewModel.closeCatalog() })
            }
            state.tvGridKey != null -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                TvGridView(state, viewModel, state.tvGridTitle, onPlayLive, onBack = { viewModel.closeCatalog() })
            }
            state.localGrid != null -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                LocalGridView(state, viewModel, state.localGrid, today, onItemClick, onBack = { viewModel.closeCatalog() })
            }
            state.trailersGrid -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                TrailersGridView(state, viewModel, playTrailerFn, onBack = { viewModel.closeCatalog() })
            }
            state.collectionGridId != null -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                CollectionGridView(state, collections, state.collectionGridId, onFolderClick, onBack = { viewModel.closeCatalog() })
            }
            state.upcomingGrid -> {
                androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
                UpcomingGridView(
                    state = state,
                    onEpisodeClick = { entry -> onItemClick(upcomingEpisodeItem(entry)) },
                    onPlayGame = { game -> onPlayLive(game.channelId) },
                    onRecordGame = { game -> recordGame = game },
                    onBack = { viewModel.closeCatalog() },
                )
            }
            else -> HomeSectionsTab(
                state = state, viewModel = viewModel, onItemClick = onItemClick,
                onSearchClick = onSearchClick, onSettingsClick = onSettingsClick,
                onDiscoverClick = onDiscoverClick,
                onSeeMore = seeMore,
                onPlayTrailer = playTrailerFn,
                onCustomize = onCustomize,
                onFolderClick = onFolderClick,
                onPlayEpisode = onPlayEpisode,
                onPlayLive = onPlayLive,
                onRecordGame = { game -> recordGame = game },
                onVodClick = onVodClick,
                onOpenOnDemand = onOpenOnDemand,
                randomVm = randomVm,
                minhaListaVm = minhaListaVm,
            )
        }
        // Estado vazio do sorteio (Lote 2): diálogo com o motivo, sem travar a
        // tela. Fica aqui (fora do LazyColumn, que não aceita @Composable direto).
        val randomState by randomVm.state.collectAsStateWithLifecycle()
        (randomState as? RandomEpisodeViewModel.RandomState.Empty)?.let { empty ->
            AlertDialog(
                onDismissRequest = { randomVm.consume() },
                title = { Text("Nada para sortear") },
                text = { Text(empty.message) },
                confirmButton = {
                    TextButton(onClick = { randomVm.consume() }) { Text("Entendi") }
                },
            )
        }
        // Overlay do trailer (ExoPlayer) por cima de tudo, num Dialog em tela cheia.
        trailerExo?.let { st ->
            val card = trailerCard
            val fila = state.latestTrailers
            val idx = fila.indexOfFirst { it.tmdbId == card?.tmdbId && it.videoKey == card.videoKey }
            val proximo = if (idx >= 0 && idx < fila.lastIndex) fila[idx + 1] else null
            com.aotv.app.ui.common.TrailerExoOverlay(
                videoUrl = st.videoUrl,
                audioUrl = st.audioUrl,
                onClose = { trailerExo = null; trailerCard = null },
                title = card?.title.orEmpty(),
                onOpenDetail = card?.let { c ->
                    {
                        trailerExo = null; trailerCard = null
                        onItemClick(
                            MovieItem(
                                tmdbId = c.tmdbId, embedId = c.tmdbId.toString(), title = c.title,
                                overview = "", posterUrl = c.posterUrl, backdropUrl = c.backdropUrl,
                                year = "", rating = 0.0, type = c.type,
                            )
                        )
                    }
                },
                onNext = proximo?.let { n -> { trailerExo = null; playTrailer(n) } },
            )
        }
        if (trailerLoading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Accent, strokeWidth = 3.dp) }
        }
    }
}

// ── Segmented tab (pill com feedback de press) ──────────────────────────────
@Composable
private fun SegmentTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "segScale",
    )
    Box(
        modifier
            .scale(scale)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected)
                    Brush.linearGradient(listOf(Accent, AccentDark))
                else
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
            )
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = (-0.2).sp,
            color = if (selected) Color.White else TextSecondary,
        )
    }
}

// ── Catálogo completo (Filmes/Séries + categorias + sort + scroll infinito) ──
@Composable
private fun CatalogTab(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onItemClick: (MovieItem) -> Unit,
) {
    val isMovie = state.catalogType == MoviesHomeViewModel.CatalogType.MOVIE
    val genres = if (isMovie) state.movieGenres else state.tvGenres

    val gridState = rememberLazyGridState()

    // Scroll infinito: dispara loadMore quando o último item visível chega perto do fim
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreCatalog()
    }

    // Opções de ordenação dependentes do tipo (Lançamentos varia entre movie/tv)
    val sortOptions = remember(isMovie) {
        listOf(
            "Populares" to MoviesHomeViewModel.SORT_POPULAR,
            "Melhor nota" to MoviesHomeViewModel.SORT_TOP_RATED,
            "Lançamentos" to if (isMovie)
                MoviesHomeViewModel.SORT_RELEASE_MOVIE
            else
                MoviesHomeViewModel.SORT_RELEASE_TV,
        )
    }

    Column(Modifier.fillMaxSize()) {
        // TMDB key hint
        if (!state.hasTmdbKey) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentSoft)
                    .border(1.dp, Accent.copy(alpha = .35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "Configure sua chave TMDB em Configurações para explorar o catálogo.",
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Toggle Filmes / Séries (segmented control reaproveitado)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BackgroundCard)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentTab(
                label = "Filmes",
                selected = isMovie,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setCatalogType(MoviesHomeViewModel.CatalogType.MOVIE) },
            )
            SegmentTab(
                label = "Séries",
                selected = !isMovie,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setCatalogType(MoviesHomeViewModel.CatalogType.SERIES) },
            )
        }

        // Chips de ordenação
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sortOptions.forEach { (label, value) ->
                FilterChipPill(
                    label = label,
                    selected = state.sortBy == value,
                    onClick = { viewModel.setSort(value) },
                )
            }
        }

        // Chips de categoria: "Todos" + gêneros
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipPill(
                label = "Todos",
                selected = state.selectedGenreId == null,
                onClick = { viewModel.selectGenre(null) },
            )
            genres.forEach { genre ->
                FilterChipPill(
                    label = genre.name,
                    selected = state.selectedGenreId == genre.id,
                    onClick = { viewModel.selectGenre(genre.id) },
                )
            }
        }

        // Estado: loading inicial
        if (state.isLoadingCatalog && state.catalogItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            return@Column
        }

        // Estado: vazio
        if (state.catalogItems.isEmpty()) {
            AotvEmptyState(
                icon = Icons.Default.MovieFilter,
                title = "Nada por aqui ainda",
                message = if (!state.hasTmdbKey)
                    "Configure sua chave TMDB e volte para explorar o catálogo."
                else
                    "Tente outra categoria ou ordenação.",
            )
            return@Column
        }

        // Grid com scroll infinito
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.catalogItems, key = { rowKey(it) }) { item ->
                val click = remember(item) { { onItemClick(item) } }
                MediaCard(item = item, onClick = click)
            }
            // Rodapé com indicador de carregamento da próxima página
            if (state.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

// ── Media card (poster) ────────────────────────────────────────────────────
/**
 * Key estável da fileira/grid: tipo + id do TMDB; item de addon sem id usa o
 * embedId — tmdbId = 0 colidia (vários itens com a mesma key 0, reciclagem errada).
 */
internal fun rowKey(item: MovieItem) =
    com.aotv.app.data.local.posterKey(item).ifBlank { "embed:${item.embedId.ifBlank { item.title }}" }

/**
 * On Demand → MovieItem p/ reaproveitar a fileira padrão. tmdbId 0: o toque
 * não abre o detalhe TMDB — a fileira mapeia de volta p/ o VodItem pelo
 * embedId ("vod:<id>") e abre o detalhe no On Demand. Só o que o painel
 * devolve (capa, sinopse, ano, nota); sem TMDB de preenchimento.
 */
private fun com.aotv.app.data.live.VodItem.toMovieItem(isSeries: Boolean): MovieItem = MovieItem(
    tmdbId = 0,
    embedId = "vod:$id",
    title = title,
    overview = plot,
    posterUrl = cover,
    backdropUrl = "",
    year = year,
    rating = rating.toDoubleOrNull() ?: 0.0,
    type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
)

@Composable
fun MediaCard(
    item: MovieItem,
    onClick: () -> Unit,
    showBadges: Boolean = true,
    showQualityBadges: Boolean = true,
    cornerRadius: Dp = 14.dp,
) {
    com.aotv.app.ui.common.PosterCard(
        item = item, onClick = onClick, showBadges = showBadges, showQualityBadges = showQualityBadges,
        cornerRadius = cornerRadius,
    )
}

// ── Aba "Início": catálogo estilo Netflix (fileiras por gênero, cresce ao rolar) ──
@Composable
private fun HomeSectionsTab(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onItemClick: (MovieItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDiscoverClick: () -> Unit = {},
    onSeeMore: (MoviesHomeViewModel.CatalogQuery) -> Unit,
    onPlayTrailer: (MoviesHomeViewModel.TrailerCard) -> Unit,
    onCustomize: () -> Unit,
    onFolderClick: (String, String) -> Unit,
    onPlayEpisode: (MovieItem, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    /** Ao vivo (Lote 8): jogo da fileira "Próximos" abre o player do canal. */
    onPlayLive: (String) -> Unit = {},
    /** Gravação de jogo (Lote 8): o estado do diálogo vive na MoviesHomeScreen (a grade "Ver Tudo" de Próximos usa o mesmo). */
    onRecordGame: (SportsGame) -> Unit = {},
    /** On Demand do Xtream (acréscimo v1.26.0): pôster da fileira abre o detalhe no On Demand. */
    onVodClick: (com.aotv.app.data.live.VodItem, Boolean) -> Unit = { _, _ -> },
    /** "Ver tudo" da fileira On Demand: abre a tela On Demand na aba certa. */
    onOpenOnDemand: (Boolean) -> Unit = {},
    randomVm: RandomEpisodeViewModel,
    /** Minha Lista p/ o MK Channel (item 5, Lote 24): a fonte watchlist. */
    minhaListaVm: MinhaListaViewModel,
) {
    val listState = rememberLazyListState()
    // Remover do Continuar assistindo (segurar o card, v1.13.0): confirma antes.
    var pendingRemove by remember { mutableStateOf<MovieItem?>(null) }
    pendingRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remover de Continuar assistindo?") },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFromContinue(item.tmdbId, item.type.name); pendingRemove = null }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancelar") } },
        )
    }
    // Painel de opções do título (Lote 14, v1.24.0): segurar o card do
    // Continuar abre Ir para detalhes / Marcar como assistido / Remover.
    // O "Remover" do painel cai na confirmação acima; "Marcar" some com
    // canal ao vivo (embedId "live|...") — ao vivo não tem assistido.
    // Lote 23: o painel local (ContinueTitleOptionsDialog, no fim do
    // arquivo) soma "Não hoje" e "Abandonar série"/"Desfazer abandono" —
    // o TitleOptionsDialog original é de outra frente e não pode ganhar
    // parâmetros sem mexer no arquivo dele.
    val droppedSeries by viewModel.droppedSeries.collectAsStateWithLifecycle()
    var pendingOptions by remember { mutableStateOf<MovieItem?>(null) }
    pendingOptions?.let { item ->
        ContinueTitleOptionsDialog(
            item = item,
            isSeriesDropped = item.type == MediaType.SERIES && item.tmdbId in droppedSeries,
            onDismiss = { pendingOptions = null },
            onGoToDetails = { onItemClick(item) },
            onMarkWatched = { viewModel.markContinueWatched(item) },
            onNotToday = { viewModel.markContinueNotToday(item.tmdbId) },
            onDropSeries = { viewModel.dropSeries(item.tmdbId) },
            onUndropSeries = { viewModel.undropSeries(item.tmdbId) },
            onRemove = { pendingRemove = item },
        )
    }
    // "Não hoje" (Lote 13, v1.23.0): segurar o card de episódio novo dispensa
    // o episódio até amanhã (mesmo padrão do remover acima).
    var pendingNotToday by remember { mutableStateOf<MovieItem?>(null) }
    pendingNotToday?.let { item ->
        val ep = state.newEpisodes.firstOrNull { it.item.tmdbId == item.tmdbId }
        AlertDialog(
            onDismissRequest = { pendingNotToday = null },
            title = { Text("Não ver hoje?") },
            text = { Text(if (ep != null) "${item.title} — ${ep.sub} some até amanhã." else "${item.title} some até amanhã.") },
            confirmButton = {
                TextButton(onClick = { viewModel.markNewEpisodeNotToday(item.tmdbId); pendingNotToday = null }) { Text("Não hoje") }
            },
            dismissButton = { TextButton(onClick = { pendingNotToday = null }) { Text("Cancelar") } },
        )
    }
    val collections by viewModel.collectionsFlow.collectAsStateWithLifecycle()
    // Blur/vidro fosco (Lote 4): mesma pref `pref_panel_blur` do detalhe.
    val panelBlurOn by hiltViewModel<DetailPrefsViewModel>().panelBlur.collectAsStateWithLifecycle()
    // Modo aleatório (Lote 2): o sorteio abre DIRETO no player quando sai;
    // sem série elegível, mostra o estado vazio em diálogo (não trava).
    val randomState by randomVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(randomState) {
        val ready = randomState as? RandomEpisodeViewModel.RandomState.Ready ?: return@LaunchedEffect
        onPlayEpisode(ready.item, ready.season, ready.episode, ready.episodeTotal)
        randomVm.consume()
    }
    // Gravação de jogo (Lote 8): o diálogo vive na MoviesHomeScreen — a
    // fileira e a grade "Ver Tudo" de Próximos chamam [onRecordGame].
    // Gravações e lembretes (Lote 8; app fechado no Lote 13, v1.23.0): o
    // in-app dispara o que venceu e avisa o que começa em 1 minuto, e o
    // WorkManager agenda o MESMO p/ quando o app estiver fechado (o agendar
    // é idempotente — agendar 2x não duplica nada). Com o app fechado, o
    // worker dispara a gravação e posta o lembrete; o vigia de fim de
    // jogo/placar roda a cada 15 min quando os alertas estão ligados.
    val context = LocalContext.current
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(state.upcomingEpisodes, state.upcomingGames) {
        com.aotv.app.data.recordings.RecordingService.checkDue(context)
        com.aotv.app.data.recordings.RecordingService.checkTeamGames(context, state.upcomingGames)
        com.aotv.app.data.recordings.RecordingReminders.checkDue(
            context, state.upcomingEpisodes, state.upcomingGames,
            state.calendarHideSpoilers, state.sportsGameAlerts,
        )
        com.aotv.app.data.recordings.WorkScheduler.scheduleReminders(
            context, state.upcomingEpisodes, state.upcomingGames, state.sportsGameAlerts,
        )
        com.aotv.app.data.recordings.WorkScheduler.scheduleAllRecordings(context)
        com.aotv.app.data.recordings.WorkScheduler.syncGameScoreWatch(context, state.sportsGameAlerts)
        com.aotv.app.data.recordings.WorkScheduler.noteLiveGames(context, state.upcomingGames)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            com.aotv.app.data.recordings.RecordingService.checkDue(context)
            com.aotv.app.data.recordings.RecordingService.checkTeamGames(context, currentState.upcomingGames)
            com.aotv.app.data.recordings.RecordingReminders.checkDue(
                context, currentState.upcomingEpisodes, currentState.upcomingGames,
                currentState.calendarHideSpoilers, currentState.sportsGameAlerts,
            )
            com.aotv.app.data.recordings.WorkScheduler.scheduleReminders(
                context, currentState.upcomingEpisodes, currentState.upcomingGames, currentState.sportsGameAlerts,
            )
            com.aotv.app.data.recordings.WorkScheduler.scheduleAllRecordings(context)
            com.aotv.app.data.recordings.WorkScheduler.syncGameScoreWatch(context, currentState.sportsGameAlerts)
            com.aotv.app.data.recordings.WorkScheduler.noteLiveGames(context, currentState.upcomingGames)
        }
    }
    // On Demand do Xtream (acréscimo v1.26.0): 1x por sessão; sem fonte
    // Xtream, sem VOD ou com falha, as fileiras somem em silêncio.
    // Lote 21 (v1.31.0): revalida as recomendações ao voltar p/ a Início
    // (o 👍/👎 marcado no detalhe muda as sementes — a chave de
    // buildRecommendations inclui os sinais de gosto, então sem mudança
    // não há nova chamada de rede).
    LaunchedEffect(Unit) { viewModel.loadVodRows(); viewModel.refreshRecommendations() }
    // Listas derivadas estáveis: map/associate novos a cada composição tornavam
    // a fileira não-pulável (o LazyColumn recompunha a fileira inteira a cada
    // emissão do UiState, mesmo sem nada mudar nela).
    val newEpItems = remember(state.newEpisodes) { state.newEpisodes.map { it.item } }
    val newEpBadges = remember(state.newEpisodes) { state.newEpisodes.associate { it.item.tmdbId to it.badge } }
    val newEpSubs = remember(state.newEpisodes) { state.newEpisodes.associate { it.item.tmdbId to it.sub } }
    // Up Next do Trakt (Lote 19 item 4): mesmas listas derivadas estáveis
    // de Novos episódios (o LazyColumn não recompõe a fileira à toa).
    val upNextItems = remember(state.traktUpNext) { state.traktUpNext.map { it.item } }
    val upNextBadges = remember(state.traktUpNext) { state.traktUpNext.associate { it.item.tmdbId to it.badge } }
    val upNextSubs = remember(state.traktUpNext) { state.traktUpNext.associate { it.item.tmdbId to it.sub } }

    // Destaque vem da fileira escolhida no Personalizar Início (ou do automático).
    val heroSource = heroCandidates(state)
    LaunchedEffect(heroSource.map { it.tmdbId }) {
        heroSource.take(10).forEach {
            val series = it.type == MediaType.SERIES
            if (it.backdropUrl.isBlank()) viewModel.ensureHeroBackdrop(it.tmdbId, series)
            viewModel.ensureHeroLogo(it.tmdbId, series)
        }
    }
    // Itens do herói estáveis: o copy() novo a cada composição recompunha o
    // pager inteiro a cada emissão do UiState (o remember só refaz quando a
    // fonte ou os backdrops mudam de verdade).
    val heroItems = remember(heroSource, state.heroBackdrops) {
        heroSource.mapNotNull { item ->
            val bd = item.backdropUrl.ifBlank { state.heroBackdrops[item.tmdbId].orEmpty() }
            if (bd.isBlank()) null else item.copy(backdropUrl = bd)
        }.take(8)
    }

    val display = state.display
    // Ocultar conteúdo não lançado (Bloco 3, Lote 15): vale p/ as fileiras do
    // catálogo e de addon. Continuar, Novos episódios e Próximos são do
    // progresso/calendário — não filtram.
    val today = remember(state.hideUnreleased) { todayIso() }
    fun vis(list: List<MovieItem>): List<MovieItem> =
        if (state.hideUnreleased) list.filterNot { isUnreleased(it, today) } else list
    val recommended = vis(state.recommended)
    val trendingMovies = vis(state.trendingMovies)
    val trendingTv = vis(state.trendingTv)
    val nowPlaying = vis(state.nowPlaying)
    val releases = vis(state.releases2026)
    // ── Lote 24 (v1.34.0), Frente A ──────────────────────────────────────
    // Herói auto-rotativo (item 2): pref hero_autorotate_v1 ("auto" | "on" |
    // "off") — "auto" usa o default do aparelho (ligado no celular,
    // desligado em TV fraca; ver heroAutoRotateEffective). Prévia ao focar
    // pela pref hero_preview_on_focus_v1.
    val homeContext = LocalContext.current
    val homeIsTv = com.aotv.app.ui.common.LocalIsTv.current
    val heroMode by viewModel.lote24Settings.autoRotateFlow.collectAsStateWithLifecycle()
    val heroPreviewOnFocus by viewModel.lote24Settings.previewOnFocusFlow.collectAsStateWithLifecycle()
    val heroAutoRotateOn = heroAutoRotateEffective(heroMode, homeContext, homeIsTv)
    // Lote 24 item 9 (Frente B): com o modo sensorial ligado o herói não
    // rotaciona sozinho — o AND abaixo desliga a rotação sem mexer no
    // default do item 2 (o trailer continua valendo).
    val heroSensoryOn = remember(homeContext) {
        loadSensoryFriendlyState(com.aotv.app.data.local.PlatformPrefs(homeContext)).enabled
    }
    // MK Channel (item 5): fontes a partir do Continuar + Minha Lista.
    val watchlistEntities by minhaListaVm.watchlist.collectAsStateWithLifecycle()
    val watchlistItems = remember(watchlistEntities) {
        watchlistEntities.map { entity ->
            MovieItem(
                tmdbId = entity.tmdbId, embedId = "", title = entity.title,
                overview = "", posterUrl = entity.posterUrl, backdropUrl = "",
                year = entity.year, rating = 0.0,
                type = runCatching { MediaType.valueOf(entity.type) }.getOrDefault(MediaType.MOVIE),
            )
        }
    }
    val mkSourceId by viewModel.lote24Settings.mkChannelSourceFlow.collectAsStateWithLifecycle()
    val mkSource = MkChannelSource.fromId(mkSourceId) ?: MkChannelSource.UP_NEXT
    // Peak Cinema (item 6): ligado e humor pela pref (a piscina local vem abaixo).
    val peakEnabled by viewModel.lote24Settings.peakCinemaEnabledFlow.collectAsStateWithLifecycle()
    val peakMood by viewModel.lote24Settings.peakCinemaMoodFlow.collectAsStateWithLifecycle()
    // Pastas da Home (item 3): fileira -> pasta que a agrupa (se houver).
    val homeFolders by viewModel.lote24Settings.foldersFlow.collectAsStateWithLifecycle()
    val folderOfRow: Map<String, HomeFolder24> = remember(homeFolders) {
        homeFolders.flatMap { folder -> folder.rowKeys.map { key -> key to folder } }.toMap()
    }
    var collapsedFolderIds by remember { mutableStateOf(emptySet<String>()) }
    val mkTargets = remember(state.continueWatching, state.continueEpisode, watchlistItems) {
        pickMkChannelTargets(state.continueWatching, state.continueEpisode, watchlistItems)
    }
    // Peak Cinema (item 6): piscina local = em alta + recomendados.
    val peakPool = remember(trendingMovies, trendingTv, recommended) {
        trendingMovies + trendingTv + recommended
    }
    // Fundo dinâmico (opt-in, desligado por padrão): gradiente com a matiz do
    // pôster do destaque. Sem matiz ainda, o fundo sólido aparece no lugar.
    val heroHue = if (display.dynamicBackground && heroItems.isNotEmpty()) {
        com.aotv.app.ui.common.rememberPosterHue(heroItems.first())
    } else {
        null
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .then(
                if (display.dynamicBackground) Modifier.background(com.aotv.app.ui.common.dynamicHomeBackground(heroHue))
                else Modifier.background(Background),
            ),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeSectionSpacing(display.density)),
    ) {
        // ── Lote 25 (v1.35.0), itens 1–2 ────────────────────────────────
        // Entrada discreta no topo: pill de busca (abre a busca) + assistente + paleta.
        item(key = "lote25_entry") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .clickable(onClick = onSearchClick)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
                    Text(
                        "Buscar filmes ou séries...",
                        color = TextTertiary,
                        fontSize = 15.sp,
                        fontFamily = ManropeFontFamily,
                    )
                }
                GlassCircleButton(
                    icon = Icons.Default.AutoAwesome,
                    onClick = { AssistantRequest.open() },
                    size = 44.dp,
                    iconTint = TextSecondary,
                    contentDescription = "Assistente",
                )
                GlassCircleButton(
                    icon = Icons.Default.Terminal,
                    onClick = { CommandPaletteRequest.open() },
                    size = 44.dp,
                    iconTint = TextSecondary,
                    contentDescription = "Comandos",
                )
            }
        }
        // Sem chave TMDB as fileiras do sistema voltam vazias em silêncio — avisa no topo.
        if (!state.hasTmdbKey) {
            item(key = "no_tmdb_key") {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentSoft)
                        .border(1.dp, Accent.copy(alpha = .35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Configure sua chave TMDB em Configurações → TMDB para ver o catálogo e buscar.",
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
        // (Lote 4, Correção B): Descobrir e Surpreenda-me NÃO ficam mais aqui,
        // acima do herói — foram p/ o fim da Início (depois das fileiras).
        if (heroItems.isNotEmpty()) {
            item(key = "hero") {
                // Trailer só com a Início no topo. Rolou, parou o trailer.
                val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 300 } }
                HeroCarousel(
                    heroItems, state.heroLogos, onItemClick,
                    // Lote 25 item 8 (Frente C): com o modo economia
                    // ativo na sessão, a prévia do herói/trailer fica
                    // desligada (ver BatteryGovernorSession).
                    trailerOn = state.heroTrailer && atTop && !BatteryGovernorSession.economyActive,
                    trailerFor = viewModel::heroTrailerStream,
                    heroStyle = display.heroStyle,
                    autoRotate = heroAutoRotateOn && heroAutoRotateAllowed(heroSensoryOn),
                    autoRotateIntervalMs = HeroAutoRotate.INTERVAL_MS,
                    previewOnFocus = heroPreviewOnFocus,
                )
            }
        }
        // ── Lote 24 (v1.34.0), Frente A: MK Channel (item 5) + Peak Cinema
        // (item 6) — logo depois do herói, antes das fileiras do layout.
        if (mkTargets.values.any { it != null }) {
            item(key = "mk_channel") {
                MkChannelRow(
                    targets = mkTargets,
                    selectedSource = mkSource,
                    onSelectSource = { source -> viewModel.lote24Settings.setMkChannelSource(source.id) },
                    onPlay = { target ->
                        if (target.season > 0 && target.episode > 0) {
                            onPlayEpisode(target.item, target.season, target.episode, target.episodeTotal)
                        } else {
                            onItemClick(target.item)
                        }
                    },
                    onOpenItem = onItemClick,
                )
            }
        }
        if (peakEnabled && peakPool.isNotEmpty()) {
            item(key = "peak_cinema") {
                PeakCinemaRow(
                    items = peakPool,
                    selectedMoodId = peakMood,
                    onSelectMood = { viewModel.lote24Settings.setPeakCinemaMood(it) },
                    onItemClick = onItemClick,
                )
            }
        }
        // Pastas da Home (item 3): cabeçalhos emitidos nesta composição
        // (local, não remember — a cada recomposição a lista recomeça).
        val emittedFolderHeaders = mutableSetOf<String>()
        state.layout.forEach { row ->
            // Pastas da Home (item 3): a fileira agrupada ganha o cabeçalho
            // da pasta na primeira vez que aparece; com a pasta recolhida,
            // as fileiras dela são puladas (o cabeçalho continua visível).
            val folder = folderOfRow[row.key]
            if (folder != null) {
                if (emittedFolderHeaders.add(folder.id)) {
                    item(key = "folder:${folder.id}") {
                        FolderHeaderRow(
                            folder = folder,
                            rowCount = folder.rowKeys.size,
                            collapsed = folder.id in collapsedFolderIds,
                            onToggle = {
                                collapsedFolderIds = if (folder.id in collapsedFolderIds) {
                                    collapsedFolderIds - folder.id
                                } else {
                                    collapsedFolderIds + folder.id
                                }
                            },
                        )
                    }
                }
                if (folder.id in collapsedFolderIds) return@forEach
            }
            val custom = row.title
            when (row.system) {
                SystemRow.CONTINUE -> if (state.continueWatching.isNotEmpty()) item(key = row.key) {
                    ContinueRow(row.displayTitle, state, onItemClick, display, frosted = panelBlurOn, onOptions = { pendingOptions = it }, onSeeMore = { viewModel.openLocalGrid(MoviesHomeViewModel.LOCAL_CONTINUE) })
                }
                SystemRow.NEW_EPISODES -> if (state.newEpisodes.isNotEmpty()) item(key = row.key) {
                    SectionRow(
                        row.displayTitle, newEpItems, onItemClick,
                        display = display,
                        badges = newEpBadges,
                        subs = newEpSubs,
                        // Segurar o card dispensa o episódio até amanhã ("Não hoje", Lote 13).
                        onLongClick = { pendingNotToday = it },
                        // "Ver tudo" (Lote 19, v1.29.0): grade com todos os episódios novos.
                        onSeeMore = { viewModel.openLocalGrid(MoviesHomeViewModel.LOCAL_NEW_EPISODES) },
                    )
                }
                // Up Next do Trakt (Lote 19 item 4): o toque abre o detalhe
                // da série, como em Novos episódios. "Ver tudo" (item 5): a
                // grade local mostra todos (no máximo 10, o teto do getUpNext).
                SystemRow.TRAKT_UP_NEXT -> if (state.traktUpNext.isNotEmpty()) item(key = row.key) {
                    SectionRow(
                        row.displayTitle, upNextItems, onItemClick,
                        display = display,
                        badges = upNextBadges,
                        subs = upNextSubs,
                        onSeeMore = { viewModel.openLocalGrid(MoviesHomeViewModel.LOCAL_UP_NEXT) },
                    )
                }
                // On Demand do Xtream (acréscimo v1.26.0): o toque NÃO abre o
                // detalhe TMDB (tmdbId 0) — mapeia de volta p/ o VodItem pelo
                // embedId e abre o detalhe no On Demand. "Ver tudo" abre a
                // tela On Demand na aba certa.
                SystemRow.VOD_MOVIES -> if (state.vodMovies.isNotEmpty()) item(key = row.key) {
                    val items = remember(state.vodMovies) { state.vodMovies.map { it.toMovieItem(false) } }
                    SectionRow(
                        row.displayTitle, items,
                        onItemClick = { mi -> state.vodMovies.firstOrNull { "vod:${it.id}" == mi.embedId }?.let { onVodClick(it, false) } },
                        display = display,
                        onSeeMore = { onOpenOnDemand(false) },
                    )
                }
                SystemRow.VOD_SERIES -> if (state.vodSeries.isNotEmpty()) item(key = row.key) {
                    val items = remember(state.vodSeries) { state.vodSeries.map { it.toMovieItem(true) } }
                    SectionRow(
                        row.displayTitle, items,
                        onItemClick = { mi -> state.vodSeries.firstOrNull { "vod:${it.id}" == mi.embedId }?.let { onVodClick(it, true) } },
                        display = display,
                        onSeeMore = { onOpenOnDemand(true) },
                    )
                }
                SystemRow.RECOMMENDED -> if (recommended.isNotEmpty()) item(key = row.key) {
                    SectionRow(custom.ifBlank { "Porque você assistiu ${state.recommendedBecause}" }, recommended, onItemClick, display = display, onSeeMore = { viewModel.openLocalGrid(MoviesHomeViewModel.LOCAL_RECOMMENDED) })
                }
                SystemRow.TRAILERS -> if (state.trailersLoading || state.latestTrailers.isNotEmpty()) item(key = row.key) {
                    LatestTrailersRow(state, viewModel, onPlay = onPlayTrailer, title = row.displayTitle, display = display, onSeeMore = { viewModel.openTrailersGrid() })
                }
                SystemRow.TRENDING_MOVIES -> if (trendingMovies.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.MOVIE, MoviesHomeViewModel.CatalogEndpoint.TRENDING)) } }
                    SectionRow(row.displayTitle, trendingMovies, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.TRENDING_TV -> if (trendingTv.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.SERIES, MoviesHomeViewModel.CatalogEndpoint.TRENDING)) } }
                    SectionRow(row.displayTitle, trendingTv, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.NOW_PLAYING -> if (nowPlaying.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.MOVIE, MoviesHomeViewModel.CatalogEndpoint.NOW_PLAYING)) } }
                    SectionRow(row.displayTitle, nowPlaying, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.RELEASES -> if (releases.isNotEmpty()) item(key = row.key) {
                    val t = custom.ifBlank { "Lançamentos ${state.releasesYear}" }
                    val more = remember(row.key, t, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(t, MoviesHomeViewModel.CatalogType.MOVIE, genreId = null, sort = MoviesHomeViewModel.SORT_RELEASE_MOVIE, year = state.releasesYear)) } }
                    SectionRow(t, releases, onItemClick, display = display, onSeeMore = more)
                }
                // Gêneros é UMA fileira ligada: 1 fileira de chips (filmes + séries),
                // sem expandir em seções. Antes 1 fileira virava até ~70 seções
                // carregadas ao rolar (76 fileiras na tela com só 15 ligadas).
                SystemRow.GENRES -> if (state.movieGenres.isNotEmpty() || state.tvGenres.isNotEmpty()) item(key = row.key) {
                    GenresChipsRow(state.movieGenres, state.tvGenres, display, onSeeMore)
                }
                // Coleção sem pasta não aparece, como no Nuvio.
                null -> if (row.collectionId != null) collections.firstOrNull { it.id == row.collectionId }?.takeIf { it.folders.isNotEmpty() }?.let { col ->
                    item(key = row.key) {
                        // "Ver tudo" (Lote 19, v1.29.0): grade com todas as pastas da coleção.
                        com.aotv.app.ui.collections.CollectionFolderRow(col, row.displayTitle, eyebrow = null, onFolder = { f -> onFolderClick(col.id, f.id) }, onSeeMore = { viewModel.openCollectionGrid(col.id) })
                    }
                } else state.tvAddonRows.firstOrNull { it.ref.key == row.key }?.takeIf { it.loading || it.channels.isNotEmpty() }?.let { tv ->
                    // Lote 16 (v1.26.0): fileira de TV dos addons — tocar no
                    // canal abre o player ao vivo (não o detalhe, como filme/série).
                    item(key = "addon-tv:" + tv.ref.key) {
                        // Carrega ao aparecer na tela, como as fileiras de filme/série.
                        LaunchedEffect(tv.ref.key) {
                            if (tv.channels.isEmpty() && tv.loading) viewModel.loadTvAddonRow(tv.ref.key)
                        }
                        if (tv.channels.isEmpty()) com.aotv.app.ui.common.SkeletonRail()
                        // "Ver tudo" (Lote 19, v1.29.0): grade com todos os canais do catálogo.
                        else TvAddonChannelRow(tv.title, tv.channels, display, onPlayLive, onSeeMore = { viewModel.openTvGrid(tv.ref.key) })
                    }
                } ?: state.addonRows.firstOrNull { it.ref.key == row.key }?.takeIf { it.loading || vis(it.items).isNotEmpty() }?.let { ar ->
                    item(key = "addon:" + ar.ref.key) {
                        // Carrega ao aparecer na tela (a LazyColumn só compõe o
                        // visível): antes todas as fileiras de addon disparavam
                        // juntas na abertura, cada uma com 40 metas + resolve.
                        LaunchedEffect(ar.ref.key) {
                            if (ar.items.isEmpty() && ar.loading) viewModel.loadAddonRow(ar.ref.key)
                        }
                        val shownItems = vis(ar.items)
                        if (ar.items.isEmpty()) com.aotv.app.ui.common.SkeletonRail()
                        // Tudo não lançado e o filtro ligado: a fileira some em vez de ficar vazia.
                        else if (shownItems.isNotEmpty()) SectionRow(
                            addonRowTitle(ar, state.typeSuffix), shownItems, onItemClick,
                            display = display,
                            onNearEnd = if (ar.more) remember(ar.ref.key) { { viewModel.loadAddonRow(ar.ref.key) } } else null,
                            // "Ver tudo" (Lote 19, v1.29.0): grade com todo o catálogo do addon.
                            onSeeMore = { viewModel.openAddonGrid(ar.ref.key) },
                        )
                    }
                }
            }
        }
        // Fileira "Próximos" (Lote 8, v1.18.0): depois das fileiras
        // existentes. Episódios abrem o detalhe da série; jogos abrem o ao
        // vivo (quando a navegação ligar o onPlayLive) ou agendam a gravação.
        if (state.upcomingEpisodes.isNotEmpty() || state.upcomingGames.isNotEmpty()) item(key = "upcoming") {
            UpcomingRow(
                episodes = state.upcomingEpisodes, games = state.upcomingGames,
                now = System.currentTimeMillis(), hideSpoilers = state.calendarHideSpoilers,
                hideScores = state.sportsHideScores, scoreDelaySec = state.sportsScoreDelaySec,
                sportsShow = state.sportsShow, display = display,
                onEpisodeClick = { entry ->
                    onItemClick(upcomingEpisodeItem(entry))
                },
                onPlayGame = { game -> onPlayLive(game.channelId) },
                onRecordGame = onRecordGame,
                // "Ver tudo" (Lote 19, v1.29.0): grade com todos os episódios e jogos.
                onSeeMore = { viewModel.openUpcomingGrid() },
            )
        }
        // ── Fim da Início (Lote 4, Correção B) ──────────────────────────────
        // Descobrir e Surpreenda-me estavam acima do herói e incomodavam:
        // agora ficam aqui, depois de todas as fileiras. Mesma navegação de
        // antes (Descobrir abre o catálogo; Surpreenda-me sorteia e abre
        // direto no player). O Surpreenda-me continua também na Biblioteca.
        item(key = "discover_entry") {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCard)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onDiscoverClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Default.Explore, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Descobrir", color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Explorar o catálogo por gênero e novidades", color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Icon(Icons.Default.ChevronRight, "Abrir Descobrir", tint = TextTertiary, modifier = Modifier.size(22.dp))
            }
        }
        // Modo aleatório (Lote 2): entrada visível mesmo sem nada no Continuar
        // assistindo. Tocar sorteia e abre direto no player.
        item(key = "random_episode") {
            RandomEpisodeEntry(
                rolling = randomState is RandomEpisodeViewModel.RandomState.Rolling,
                onRoll = { randomVm.roll() },
            )
        }
        // Personalizar Início (Bloco 3, Lote 15): entrada visível no fim da
        // Início, no mesmo formato dos cartões Descobrir e Surpreenda-me.
        item(key = "customize_home") {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCard)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCustomize)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Default.Settings, null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Personalizar Início", color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text("Fileiras, ordem, nomes e destaque", color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Icon(Icons.Default.ChevronRight, "Abrir Personalizar Início", tint = TextTertiary, modifier = Modifier.size(22.dp))
            }
        }
        if (heroItems.isEmpty() && state.layout.isEmpty()) item(key = "empty") {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.MovieFilter, "Sua Início está vazia",
                "Ligue o catálogo AOTV ou as fileiras de um addon em Personalizar Início.",
                action = { com.aotv.app.ui.common.AotvButton("Personalizar Início", onCustomize) },
            )
        }
    }
}

/** Selo do destaque. Em alta do AOTV usa o selo de sempre, outra fonte mostra o nome da fileira. */
internal fun heroLabel(s: MoviesHomeViewModel.UiState): String {
    val chosen = s.layout.firstOrNull { it.key == s.heroSource && s.heroSource.isNotBlank() }
    if (chosen != null) return if (chosen.system == SystemRow.RECOMMENDED && chosen.title.isBlank()) "Para você" else chosen.displayTitle
    val heroCatalogs = s.heroCatalogs
    if (heroCatalogs != null && heroCatalogs.isNotEmpty()) {
        return s.layout.firstOrNull { it.key in heroCatalogs }?.displayTitle ?: ""
    }
    if (s.catalogOn && (s.trendingMovies.isNotEmpty() || s.trendingTv.isNotEmpty())) return ""
    return s.addonRows.firstOrNull { it.items.isNotEmpty() }?.title
        ?: s.layout.firstOrNull { it.system == SystemRow.CONTINUE }?.displayTitle ?: ""
}

/** Data de hoje (yyyy-MM-dd), p/ "Ocultar conteúdo não lançado". */
internal fun todayIso(): String {
    val c = java.util.Calendar.getInstance()
    return "%04d-%02d-%02d".format(
        java.util.Locale.US,
        c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

/**
 * Conteúdo não lançado (Bloco 3, Lote 15): data de lançamento depois de hoje.
 * Sem data (item de addon sem releaseDate, item do progresso), fica — nunca
 * some por falta de dado. Só o ano, sem dia, compara só o ano.
 */
internal fun isUnreleased(item: MovieItem, today: String = todayIso()): Boolean {
    val d = item.releaseDate.ifBlank { item.year }
    if (d.isBlank()) return false
    return if (d.length >= 10) d.take(10) > today else (d.toIntOrNull() ?: return false) > today.take(4).toInt()
}

/**
 * Título da fileira de addon na Início. Com o sufixo de tipo ligado (Bloco 3,
 * Lote 15), o nome ganha " · Filme" ou " · Série".
 */
internal fun addonRowTitle(row: MoviesHomeViewModel.AddonRow, typeSuffix: Boolean): String {
    if (!typeSuffix) return row.title
    return row.title + if (row.ref.type == "series") " · Série" else " · Filme"
}

/** Episódio de "Próximos" → MovieItem do detalhe (o mesmo que o toque no card da fileira abre). */
private fun upcomingEpisodeItem(entry: CalendarEntry): MovieItem = MovieItem(
    tmdbId = entry.tmdbId, embedId = entry.tmdbId.toString(), title = entry.title,
    overview = "", posterUrl = entry.posterUrl, backdropUrl = "",
    year = "", rating = 0.0, type = MediaType.SERIES,
)

/**
 * Título da grade "Ver Tudo" de lista local (Lote 19, v1.29.0): o nome que o
 * perfil deu à fileira no Personalizar Início, ou o padrão da fileira.
 */
private fun localGridTitle(state: MoviesHomeViewModel.UiState, kind: String?): String {
    val key = when (kind) {
        MoviesHomeViewModel.LOCAL_CONTINUE -> SystemRow.CONTINUE.key
        MoviesHomeViewModel.LOCAL_NEW_EPISODES -> SystemRow.NEW_EPISODES.key
        MoviesHomeViewModel.LOCAL_UP_NEXT -> SystemRow.TRAKT_UP_NEXT.key
        else -> SystemRow.RECOMMENDED.key
    }
    return state.layout.firstOrNull { it.key == key }?.displayTitle
        ?: when (kind) {
            MoviesHomeViewModel.LOCAL_CONTINUE -> "Continuar assistindo"
            MoviesHomeViewModel.LOCAL_NEW_EPISODES -> "Novos episódios"
            MoviesHomeViewModel.LOCAL_UP_NEXT -> "Up Next — Trakt"
            else -> "Porque você assistiu ${state.recommendedBecause}"
        }
}

/**
 * De onde vêm os títulos do destaque. Fileira escolhida no Personalizar Início, ou no
 * automático os catálogos escolhidos lá, senão o que está em alta no AOTV, senão a
 * primeira fileira de addon, senão o progresso. Destaque desligado (ou sem catálogo
 * escolhido) = lista vazia — a busca lê esta mesma função e some com os Destaques.
 */
internal fun heroCandidates(s: MoviesHomeViewModel.UiState): List<MovieItem> {
    // Destaque desligado no Personalizar Início: nem a Início nem a busca mostram o carrossel.
    if (!s.heroOn) return emptyList()
    fun itemsOf(key: String): List<MovieItem> = when (SystemRow.of(key)) {
        SystemRow.CONTINUE -> s.continueWatching
        SystemRow.RECOMMENDED -> s.recommended
        SystemRow.TRENDING_MOVIES -> s.trendingMovies
        SystemRow.TRENDING_TV -> s.trendingTv
        SystemRow.NOW_PLAYING -> s.nowPlaying
        SystemRow.RELEASES -> s.releases2026
        else -> s.addonRows.firstOrNull { it.ref.key == key }?.items.orEmpty()
    }
    val heroCatalogs = s.heroCatalogs
    val chosen = s.heroSource.takeIf { k -> k.isNotBlank() && s.layout.any { it.key == k } }
    val raw = when {
        chosen != null -> itemsOf(chosen)
        // Catálogos do destaque escolhidos: o automático junta só os escolhidos,
        // na ordem das fileiras. Lista vazia = nenhum catálogo no destaque.
        heroCatalogs != null -> s.layout.filter { it.key in heroCatalogs }.flatMap { itemsOf(it.key) }
        s.catalogOn && (s.trendingMovies.isNotEmpty() || s.trendingTv.isNotEmpty()) -> s.trendingMovies + s.trendingTv
        else -> s.addonRows.firstOrNull { it.items.isNotEmpty() }?.items ?: s.continueWatching
    }.take(12)
    return if (s.hideUnreleased) raw.filterNot { isUnreleased(it) } else raw
}

@Composable
private fun HeroTitle(title: String, compact: Boolean = false) {
    Text(
        title, color = TextPrimary, fontSize = if (compact) 28.sp else 34.sp, fontWeight = FontWeight.ExtraBold,
        fontFamily = SoraFontFamily, maxLines = 2, lineHeight = if (compact) 32.sp else 37.sp, letterSpacing = (-0.7).sp,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Entrada do Modo aleatório (Lote 2, v1.10.0): cartão "Surpreenda-me" no fim
 * da Início (Lote 4: estava acima do herói) e o mesmo na Biblioteca. Tocar
 * sorteia um episódio das séries do Continuar assistindo + Minha Lista e
 * abre direto no player.
 */
@Composable
fun RandomEpisodeEntry(
    rolling: Boolean,
    onRoll: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !rolling, onClick = onRoll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Default.Shuffle, null, tint = Accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text("Surpreenda-me", color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold)
            Text(
                if (rolling) "Sorteando um episódio…" else "Sortear um episódio das suas séries",
                color = TextTertiary, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        if (rolling) CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Icon(Icons.Default.ChevronRight, "Sortear episódio", tint = TextTertiary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ContinueRow(
    title: String,
    state: MoviesHomeViewModel.UiState,
    onItemClick: (MovieItem) -> Unit,
    display: HomeDisplayPrefs = HomeDisplayPrefs(),
    /** Vidro fosco nos cartões (Lote 4): toggle `pref_panel_blur`. */
    frosted: Boolean = false,
    /** Segurar o card abre o painel de opções do título (Lote 14, v1.24.0). null = sem painel. */
    onOptions: ((MovieItem) -> Unit)? = null,
    /** "Ver tudo" (Lote 19, v1.29.0): abre a grade com tudo que está no Continuar. null = sem link. */
    onSeeMore: (() -> Unit)? = null,
) {
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title,
            action = onSeeMore?.let { seeMore -> { com.aotv.app.ui.common.SeeAllLink("Ver tudo", seeMore) } },
            underline = display.sectionUnderline, compact = display.compact,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeRowSpacing(display.density))) {
            items(state.continueWatching, key = { rowKey(it) }) { item ->
                val click = remember(item) { { onItemClick(item) } }
                com.aotv.app.ui.common.ContinueCard(item, click,
                    artwork = state.continueArtwork[com.aotv.app.data.local.posterKey(item)],
                    progress = state.continueProgress[item.tmdbId], remaining = state.continueRemaining[item.tmdbId],
                    episode = state.continueEpisode[item.tmdbId].orEmpty(), compact = display.compact, frosted = frosted,
                    // Segurar abre o painel de opções (Lote 14, v1.24.0); sem onOptions, o long-press não faz nada.
                    onLongClick = onOptions?.let { options -> { options(item) } })
            }
        }
    }
}

/**
 * Destaque da Início no padrão do webOS. Em pé a arte ocupa a largura toda e o texto fica
 * embaixo à esquerda. Deitado a arte fica nos 80% da direita com degradê vindo da esquerda.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeroCarousel(
    items: List<MovieItem>,
    logos: Map<Int, String>,
    onItemClick: (MovieItem) -> Unit,
    trailerOn: Boolean = false,
    trailerFor: suspend (MovieItem) -> com.aotv.app.data.remote.YoutubeTrailer.TrailerStream? = { null },
    /** Estilo do herói (Fase B): tela cheia (o atual) ou cartão com cantos arredondados. */
    heroStyle: HeroStyle = HeroStyle.FULL_BLEED,
    /** Lote 24 (v1.34.0), item 2: rotação automática ligada. Desligado, o herói não avança sozinho. */
    autoRotate: Boolean = true,
    /** Lote 24, item 2: intervalo da rotação automática (padrão 8 s). */
    autoRotateIntervalMs: Long = HeroAutoRotate.INTERVAL_MS,
    /** Lote 24, item 2: prévia de cena ao focar (TV/D-pad) ou toque longo (celular). */
    previewOnFocus: Boolean = true,
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items.size })
    // Trailer mudo depois de 5 s parado no mesmo destaque. Trocar de página, rolar a Início ou
    // sair dela (o composable some) para o trailer.
    var trailer by remember { mutableStateOf<Pair<Int, com.aotv.app.data.remote.YoutubeTrailer.TrailerStream>?>(null) }
    // Lote 24, item 2: prévia de cena — o item em foco (ou de toque longo) mostra o trailer
    // mudo dele; a rotação pausa enquanto a prévia está aberta.
    var previewItem by remember { mutableStateOf<MovieItem?>(null) }
    // Lote 24, item 2: arrastar o pager pausa a rotação (o usuário está interagindo).
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val resumed by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val active = trailerOn && resumed.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val scope = rememberCoroutineScope()
    fun advance(from: Int) {
        if (items.size > 1) scope.launch { runCatching { pagerState.animateScrollToPage((from + 1) % items.size) } }
    }
    // Um efeito só cuida do avanço e do trailer, assim um não corta o outro. Com trailer ligado,
    // espera 5 s, toca e só avança quando o trailer acaba. Sem trailer, avança a cada 6 s.
    // Herói fora do topo (rolou) ou app em 2º plano: PARA — antes avançava a cada
    // 6 s mesmo fora da tela, animando o pager e buscando trailer sem ninguém ver.
    // Lote 24, item 2: o intervalo virou 8 s (autoRotateIntervalMs); sem
    // autoRotate, o trailer ainda toca mas o herói não avança sozinho; com o
    // usuário arrastando ou a prévia aberta, o ciclo pausa.
    LaunchedEffect(pagerState.settledPage, active, items.size, autoRotate, dragged, previewItem) {
        // Prévia de outra página ficou para trás (o pager andou): fecha.
        if (previewItem != null && previewItem != items.getOrNull(pagerState.settledPage)) previewItem = null
        if (previewItem != null || dragged) return@LaunchedEffect
        trailer = null
        if (items.isEmpty() || !active) return@LaunchedEffect
        val page = pagerState.settledPage
        kotlinx.coroutines.delay(if (autoRotate) autoRotateIntervalMs else HeroAutoRotate.TRAILER_DELAY_MS)
        trailerFor(items[page])?.let { trailer = page to it; return@LaunchedEffect }
        if (autoRotate) advance(page)
    }
    // Lote 24, item 2: prévia de cena do item em foco — busca o trailer mudo
    // dele (o mesmo caminho do trailer do herói) e toca por cima do backdrop.
    LaunchedEffect(previewItem) {
        val preview = previewItem ?: return@LaunchedEffect
        val page = pagerState.settledPage
        if (preview != items.getOrNull(page)) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        trailerFor(preview)?.let { trailer = page to it }
    }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = config.screenWidthDp >= 600 && config.screenWidthDp > config.screenHeightDp
    val compactHero = config.screenWidthDp > config.screenHeightDp && config.screenHeightDp < 500
    val fallbackHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (if (compactHero) 32.sp else 37.sp).toDp() * 2 }
    val logoHeight = maxOf(if (compactHero) 64.dp else if (wide) 84.dp else 100.dp, fallbackHeight)
    val pad = com.aotv.app.ui.common.pagePadding()
    val heroHeight = com.aotv.app.ui.common.homeHeroHeight(config.screenWidthDp, config.screenHeightDp, config.fontScale).dp
    // Cartão: o herói vira um card com margem lateral e cantos arredondados, no lugar
    // de ocupar a largura toda. Tela cheia mantém o comportamento de sempre.
    val heroBox = if (heroStyle == HeroStyle.CARD) {
        Modifier.fillMaxWidth().padding(horizontal = pad).padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp)).height(heroHeight)
    } else {
        Modifier.fillMaxWidth().height(heroHeight)
    }
    Box(heroBox) {
        androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            // Lote 24, item 2: focar o item (TV/D-pad) abre a prévia de cena;
            // perder o foco fecha. O toque longo (celular) abre pela arte, abaixo.
            Box(
                Modifier.fillMaxSize()
                    .onFocusChanged { focusState ->
                        if (!previewOnFocus) return@onFocusChanged
                        if (focusState.isFocused) previewItem = item
                        else if (previewItem == item) previewItem = null
                    },
            ) {
                // Arte. Box com o tamanho exato da área, a imagem e o trailer recortam no centro.
                Box(
                    (if (wide) Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.8f)
                    else Modifier.fillMaxSize())
                        .pointerInput(item.tmdbId, previewOnFocus) {
                            detectTapGestures(onLongPress = { if (previewOnFocus) previewItem = item })
                        },
                ) {
                    // Tamanho explícito em px de exibição (chave de cache estável).
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val pxW = with(density) { (config.screenWidthDp.dp * (if (wide) 0.8f else 1f)).roundToPx() }
                    val pxH = with(density) { heroHeight.roundToPx() }
                    val heroContext = androidx.compose.ui.platform.LocalContext.current
                    val heroRequest = remember(item.backdropUrl, item.posterUrl, pxW, pxH) {
                        coil.request.ImageRequest.Builder(heroContext)
                            .data(item.backdropUrl.ifBlank { item.posterUrl }).size(pxW, pxH).build()
                    }
                    coil.compose.AsyncImage(
                        model = heroRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = androidx.compose.ui.BiasAlignment(0f, -0.6f),
                    )
                    trailer?.takeIf { it.first == page }?.let { (_, st) ->
                        com.aotv.app.ui.common.MutedTrailer(
                            st.videoUrl, st.audioUrl,
                            // Lote 24, item 2: fim da prévia só fecha a prévia;
                            // fim do trailer do ciclo avança (só com autoRotate).
                            onEnd = {
                                trailer = null
                                if (previewItem != null) previewItem = null
                                else if (autoRotate) advance(page)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Degradês do webOS. Lateral só deitado (.scrim-x), de baixo sempre (.scrim-y).
                if (wide) Box(Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        // A arte começa em 20% da largura. Até 22% o fundo é sólido e esconde a borda dela.
                        0f to Background, 0.22f to Background,
                        0.5f to Background.copy(alpha = 0.6f), 0.86f to Color.Transparent,
                    )
                ))
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Background.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        if (wide) 0.38f to Color.Transparent else 0.45f to Color.Transparent,
                        if (wide) 0.75f to Background.copy(alpha = 0.6f) else 0.72f to Background.copy(alpha = 0.78f),
                        1f to Background,
                    )
                ))
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .then(if (wide) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth())
                        .padding(start = pad, end = pad, bottom = if (compactHero) 16.dp else 22.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHero) 8.dp else 10.dp),
                ) {
                    // Logo do título. Texto entra quando não há logo.
                    val logo = logos[item.tmdbId].orEmpty()
                    if (logo.isNotBlank()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = logo,
                            contentDescription = item.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            modifier = Modifier.fillMaxWidth(if (wide) 0.8f else 0.72f).heightIn(max = logoHeight),
                            error = { HeroTitle(item.title, compactHero) },
                            loading = { HeroTitle(item.title, compactHero) },
                        )
                    } else {
                        HeroTitle(item.title, compactHero)
                    }
                    // Metadados (.meta-row). Nota em dourado.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (item.rating > 0) Text(
                            "★ ${"%.1f".format(item.rating)}", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        if (item.year.isNotBlank()) Text(item.year, color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (item.type == MediaType.SERIES) "Série" else "Filme", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (item.overview.isNotBlank()) Text(
                        item.overview, color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                        maxLines = if (compactHero) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // "Ver Detalhes" abre o detalhe (é o que o onItemClick faz); sem ícone de play.
                        com.aotv.app.ui.common.AotvPrimaryButton(text = "Ver Detalhes", onClick = { onItemClick(item) }, icon = null)
                        // Indicador da página atual.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(items.size) { i ->
                                val on = i == pagerState.currentPage
                                Box(
                                    Modifier.height(5.dp).width(if (on) 18.dp else 5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (on) Accent else TextTertiary.copy(alpha = 0.6f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fileira Gêneros: 1 fileira de chips (filmes + séries), cada chip abre a
 * grade daquele gênero. Sem expandir em seções: a expansão era o que fazia
 * 1 fileira ligada virar até ~70 fileiras na tela. Recebe só as listas de
 * gêneros (não o UiState inteiro) para o Compose poder pular a fileira.
 */
@Composable
private fun GenresChipsRow(
    movieGenres: List<com.aotv.app.data.remote.dto.TmdbGenreDto>,
    tvGenres: List<com.aotv.app.data.remote.dto.TmdbGenreDto>,
    display: HomeDisplayPrefs = HomeDisplayPrefs(),
    onSeeMore: (MoviesHomeViewModel.CatalogQuery) -> Unit,
) {
    // Chips estáveis: a lista só refaz quando os gêneros chegam/mudam.
    val chips = remember(movieGenres, tvGenres) {
        movieGenres.map { g ->
            g.name to MoviesHomeViewModel.CatalogQuery(g.name, MoviesHomeViewModel.CatalogType.MOVIE, genreId = g.id, sort = MoviesHomeViewModel.SORT_POPULAR)
        } + tvGenres.map { g ->
            "${g.name} · Séries" to MoviesHomeViewModel.CatalogQuery("${g.name} · Séries", MoviesHomeViewModel.CatalogType.SERIES, genreId = g.id, sort = MoviesHomeViewModel.SORT_POPULAR)
        }
    }
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title = "Gêneros",
            underline = display.sectionUnderline,
            compact = display.compact,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { (label, query) ->
                FilterChipPill(label, false, { onSeeMore(query) })
            }
        }
    }
}

/** Fileira de canais de TV dos addons (Lote 16 item 10): tocar no canal abre
 * o player ao vivo — não o detalhe, como filme/série. Logo do canal (ou as
 * 2 primeiras letras) + nome, no mesmo desenho da lista do player ao vivo. */
@Composable
private fun TvAddonChannelRow(
    title: String,
    channels: List<com.aotv.app.data.live.LiveChannel>,
    display: HomeDisplayPrefs,
    onPlayLive: (String) -> Unit,
    /** "Ver tudo" (Lote 19, v1.29.0): abre a grade com todos os canais do catálogo. null = sem link. */
    onSeeMore: (() -> Unit)? = null,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title = title,
            action = onSeeMore?.let { seeMore -> { com.aotv.app.ui.common.SeeAllLink("Ver tudo", seeMore) } },
            underline = display.sectionUnderline,
            compact = display.compact,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels, key = { it.id }) { c ->
                TvChannelCard(c, onPlayLive)
            }
        }
    }
}

/**
 * Cartão de canal de TV de addon (Lote 19, v1.29.0): extraído da fileira p/
 * ser o MESMO cartão na fileira e na grade "Ver tudo" — tocar abre o player
 * ao vivo nos dois lugares. Na fileira tem 110dp de largura; na grade ocupa
 * a célula toda.
 */
@Composable
private fun TvChannelCard(
    channel: com.aotv.app.data.live.LiveChannel,
    onPlayLive: (String) -> Unit,
    modifier: Modifier = Modifier.width(110.dp),
) {
    val click = remember(channel.id) { { onPlayLive(channel.id) } }
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .05f))
            .clickable { click() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 64.dp, height = 36.dp), contentAlignment = Alignment.Center) {
            if (channel.logo.isNotBlank()) coil.compose.AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.ExtraBold)
        }
        Text(channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionRow(
    title: String,
    items: List<MovieItem>,
    onItemClick: (MovieItem) -> Unit,
    display: HomeDisplayPrefs = HomeDisplayPrefs(),
    progressMap: Map<Int, Float>? = null,   // Continuar assistindo: fração assistida por tmdbId
    remainingMap: Map<Int, Int>? = null,    // Continuar assistindo: minutos restantes por tmdbId
    onNearEnd: (() -> Unit)? = null,        // paginação da fileira (catálogo de addon)
    badges: Map<Int, String>? = null,       // selo no canto do pôster (Novos episódios)
    subs: Map<Int, String>? = null,         // linha embaixo do pôster
    /** Segurar o card (Novos episódios: "Não hoje", Lote 13). null = sem ação. */
    onLongClick: ((MovieItem) -> Unit)? = null,
    // Último de propósito. A lambda final das chamadas ("Ver Tudo") cai aqui. Antes caía no
    // onNearEnd e a grade abria sozinha quando a fileira era curta.
    onSeeMore: (() -> Unit)? = null,
) {
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    if (onNearEnd != null) {
        val nearEnd by remember {
            derivedStateOf {
                val li = rowState.layoutInfo
                li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
            }
        }
        LaunchedEffect(nearEnd, items.size) { if (nearEnd) onNearEnd() }
    }
    val pad = com.aotv.app.ui.common.pagePadding()
    val cardWidth = com.aotv.app.ui.common.posterWidthFor(display.posterSize)
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title = title,
            action = onSeeMore?.let { f -> { com.aotv.app.ui.common.SeeAllLink("Ver tudo", f) } },
            underline = display.sectionUnderline,
            compact = display.compact,
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeRowSpacing(display.density)),
        ) {
            items(items, key = { rowKey(it) }) { item ->
                // Clique estável por item: lambda nova a cada composição tornava
                // o card não-pulável mesmo com MovieItem estável e key certa.
                val click = remember(item) { { onItemClick(item) } }
                val remain = remainingMap?.get(item.tmdbId)
                com.aotv.app.ui.common.PosterCard(
                    item = item,
                    onClick = click,
                    modifier = Modifier.width(cardWidth),
                    caption = if (remain != null) "$remain min restantes" else item.title,
                    sub = subs?.get(item.tmdbId),
                    badge = badges?.get(item.tmdbId),
                    progress = progressMap?.get(item.tmdbId),
                    showBadges = display.showPosterBadges,
                    showQualityBadges = display.showQualityBadges,
                    cornerRadius = display.posterRadiusDp.dp,
                    onLongClick = onLongClick?.let { f -> { f(item) } },
                )
            }
        }
    }
}

// ── Grid de "Ver Tudo" (scroll infinito) com header de voltar ──
@Composable
private fun CatalogGridView(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    title: String,
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val display = state.display
    val loadMore by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(loadMore) { if (loadMore) viewModel.loadMoreGrid() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.aotv.app.ui.common.GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, contentDescription = "Voltar")
            Text(
                title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.4).sp,
            )
        }
        when {
            state.gridLoading && state.gridItems.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            state.gridItems.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Essa lista está vazia agora.")
                }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidthFor(display.posterSize)),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (display.compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeGridSpacing(display.density)),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.gridItems, key = { rowKey(it) }) { item ->
                    val click = remember(item) { { onItemClick(item) } }
                    MediaCard(
                        item = item, onClick = click,
                        showBadges = display.showPosterBadges, showQualityBadges = display.showQualityBadges,
                        cornerRadius = display.posterRadiusDp.dp,
                    )
                }
                if (state.gridLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Grades "Ver Tudo" de fileira que não é TMDB (Lote 19, v1.29.0) ─────────
// Mesmo desenho do CatalogGridView (voltar + título + grade); cada grade
// mostra TODOS os itens da fileira e o toque abre o MESMO destino que o
// toque no card da fileira.

/** Cabeçalho das grades "Ver Tudo": voltar + título (o mesmo do CatalogGridView). */
@Composable
private fun SeeAllGridHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.aotv.app.ui.common.GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, contentDescription = "Voltar")
        Text(
            title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily,
            maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.4).sp,
        )
    }
}

/** Grade "Ver Tudo" de catálogo de addon: pagina o mesmo catálogo da fileira. */
@Composable
private fun AddonGridView(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    title: String,
    today: String,
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val display = state.display
    // Mesmo filtro da fileira ("Ocultar conteúdo não lançado").
    val shown = remember(state.addonGridItems, state.hideUnreleased, today) {
        if (state.hideUnreleased) state.addonGridItems.filterNot { isUnreleased(it, today) } else state.addonGridItems
    }
    val loadMore by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(loadMore) { if (loadMore) viewModel.loadMoreAddonGrid() }

    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader(title, onBack)
        when {
            state.addonGridLoading && state.addonGridItems.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            shown.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Essa lista está vazia agora.")
                }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidthFor(display.posterSize)),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (display.compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeGridSpacing(display.density)),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { rowKey(it) }) { item ->
                    val click = remember(item) { { onItemClick(item) } }
                    MediaCard(
                        item = item, onClick = click,
                        showBadges = display.showPosterBadges, showQualityBadges = display.showQualityBadges,
                        cornerRadius = display.posterRadiusDp.dp,
                    )
                }
                if (state.addonGridLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Grade "Ver Tudo" de TV de addon: todos os canais do catálogo; o toque abre o ao vivo. */
@Composable
private fun TvGridView(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    title: String,
    onPlayLive: (String) -> Unit,
    onBack: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val loadMore by remember {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(loadMore) { if (loadMore) viewModel.loadMoreTvGrid() }

    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader(title, onBack)
        when {
            state.tvGridLoading && state.tvGridChannels.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            state.tvGridChannels.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Essa lista está vazia agora.")
                }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.tvGridChannels, key = { it.id }) { channel ->
                    TvChannelCard(channel, onPlayLive, modifier = Modifier.fillMaxWidth())
                }
                if (state.tvGridLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grade "Ver Tudo" de lista local finita (Continuar assistindo, Novos
 * episódios, Up Next do Trakt, Recomendados): mostra tudo que a fileira já
 * tem, sem buscar nada; o toque abre o mesmo detalhe. O Continuar mantém o
 * progresso e os minutos restantes no card; Novos episódios e Up Next
 * mantêm selo e legenda.
 */
@Composable
private fun LocalGridView(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    kind: String?,
    today: String,
    onItemClick: (MovieItem) -> Unit,
    onBack: () -> Unit,
) {
    val display = state.display
    val title = localGridTitle(state, kind)
    val items: List<MovieItem>
    val progress: Map<Int, Float>?
    val remaining: Map<Int, Int>?
    val badges: Map<Int, String>?
    val subs: Map<Int, String>?
    val isRecommended = kind != MoviesHomeViewModel.LOCAL_CONTINUE &&
        kind != MoviesHomeViewModel.LOCAL_NEW_EPISODES &&
        kind != MoviesHomeViewModel.LOCAL_UP_NEXT
    var tasteOpen by rememberSaveable { mutableStateOf(false) }
    when (kind) {
        MoviesHomeViewModel.LOCAL_CONTINUE -> {
            items = state.continueWatching
            progress = state.continueProgress
            remaining = state.continueRemaining
            badges = null
            subs = null
        }
        MoviesHomeViewModel.LOCAL_NEW_EPISODES -> {
            items = state.newEpisodes.map { it.item }
            progress = null
            remaining = null
            badges = state.newEpisodes.associate { it.item.tmdbId to it.badge }
            subs = state.newEpisodes.associate { it.item.tmdbId to it.sub }
        }
        MoviesHomeViewModel.LOCAL_UP_NEXT -> {
            items = state.traktUpNext.map { it.item }
            progress = null
            remaining = null
            badges = state.traktUpNext.associate { it.item.tmdbId to it.badge }
            subs = state.traktUpNext.associate { it.item.tmdbId to it.sub }
        }
        else -> {
            // Recomendados: o mesmo filtro de não lançados da fileira.
            items = if (state.hideUnreleased) state.recommended.filterNot { isUnreleased(it, today) } else state.recommended
            progress = null
            remaining = null
            badges = null
            subs = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader(title, onBack)
        // Lote 21 (v1.31.0): na grade de recomendados, o botão que explica
        // o motor ("O que molda suas recomendações") — regras, sementes e
        // os sinais de gosto com remoção.
        if (isRecommended) {
            TextButton(
                onClick = { tasteOpen = true },
                modifier = Modifier.padding(start = com.aotv.app.ui.common.pagePadding()),
            ) {
                Text(
                    "Por que estas recomendações?",
                    color = Accent, fontFamily = ManropeFontFamily, fontSize = 13.sp,
                )
            }
        }
        if (tasteOpen) TasteDialog(state = state, viewModel = viewModel, onDismiss = { tasteOpen = false })
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Essa lista está vazia agora.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidthFor(display.posterSize)),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (display.compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeGridSpacing(display.density)),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { rowKey(it) }) { item ->
                    val click = remember(item) { { onItemClick(item) } }
                    val remain = remaining?.get(item.tmdbId)
                    com.aotv.app.ui.common.PosterCard(
                        item = item,
                        onClick = click,
                        caption = if (remain != null) "$remain min restantes" else item.title,
                        sub = subs?.get(item.tmdbId),
                        badge = badges?.get(item.tmdbId),
                        progress = progress?.get(item.tmdbId),
                        showBadges = display.showPosterBadges,
                        showQualityBadges = display.showQualityBadges,
                        cornerRadius = display.posterRadiusDp.dp,
                    )
                }
            }
        }
    }
}

/**
 * Grade "Ver Tudo" da fileira "Últimos Trailers": as mesmas abas da fileira
 * (Popular, Streaming, Na TV, Para Alugar, Nos Cinemas) e os cards 16:9 em
 * grade; o toque toca o trailer no overlay, como na fileira.
 */
@Composable
private fun TrailersGridView(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onPlay: (MoviesHomeViewModel.TrailerCard) -> Unit,
    onBack: () -> Unit,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val title = state.layout.firstOrNull { it.key == SystemRow.TRAILERS.key }?.displayTitle ?: "Últimos trailers"
    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader(title, onBack)
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MoviesHomeViewModel.TrailerTab.entries.toList(), key = { it.name }) { tab ->
                com.aotv.app.ui.common.FilterChipPill(
                    label = tab.label,
                    selected = state.trailerTab == tab,
                    onClick = { viewModel.selectTrailerTab(tab) },
                )
            }
        }
        when {
            state.trailersLoading && state.latestTrailers.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }
            state.latestTrailers.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum trailer nessa aba agora.", color = TextTertiary, fontSize = 14.sp)
                }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(264.dp),
                contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.latestTrailers, key = { "${it.tmdbId}:${it.videoKey}" }) { card ->
                    TrailerCardView(card, onClick = { onPlay(card) })
                }
            }
        }
    }
}

/** Grade "Ver Tudo" das pastas de uma coleção: o toque abre a mesma pasta que o card da fileira. */
@Composable
private fun CollectionGridView(
    state: MoviesHomeViewModel.UiState,
    collections: List<com.aotv.app.data.collections.MediaCollection>,
    collectionId: String?,
    onFolderClick: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val collection = collections.firstOrNull { it.id == collectionId }
    val title = state.layout.firstOrNull { it.collectionId == collectionId }?.displayTitle
        ?: collection?.title ?: ""
    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader(title, onBack)
        if (collection == null || collection.folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Essa coleção não tem pastas.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(collection.folders, key = { it.id }) { folder ->
                    com.aotv.app.ui.collections.FolderTile(
                        folder = folder,
                        onClick = { onFolderClick(collection.id, folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Grade "Ver Tudo" da fileira "Próximos": episódios em grade de pôsteres
 * (mesma legenda da fileira — T/E + data, ou "Episódio em breve" com
 * spoiler) e jogos em cartões de largura toda — o MESMO cartão da fileira
 * ([UpcomingGameCard]), com "Assistir" abrindo o ao vivo e "Gravar"
 * agendando a gravação.
 */
@Composable
private fun UpcomingGridView(
    state: MoviesHomeViewModel.UiState,
    onEpisodeClick: (CalendarEntry) -> Unit,
    onPlayGame: (SportsGame) -> Unit,
    onRecordGame: (SportsGame) -> Unit,
    onBack: () -> Unit,
) {
    val display = state.display
    val now = System.currentTimeMillis()
    val today = todayMs()
    val pad = com.aotv.app.ui.common.pagePadding()
    val hasEpisodes = state.upcomingEpisodes.isNotEmpty()
    val hasGames = state.sportsShow && state.upcomingGames.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        SeeAllGridHeader("Próximos", onBack)
        if (!hasEpisodes && !hasGames) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AotvEmptyState(Icons.Default.MovieFilter, "Nada encontrado", "Nada chegando por agora.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(com.aotv.app.ui.common.posterWidthFor(display.posterSize)),
                contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (display.compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeGridSpacing(display.density)),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (hasEpisodes) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Episódios", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            fontFamily = Inter, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(state.upcomingEpisodes, key = { "ep:${it.tmdbId}:${it.season}:${it.episode}" }) { entry ->
                        val item = upcomingEpisodeItem(entry)
                        val click = remember(entry) { { onEpisodeClick(entry) } }
                        val hideLabel = state.calendarHideSpoilers && !entry.isAired(today)
                        val sub = if (hideLabel) "Episódio em breve" else "${entry.seLabel} · ${entry.dateLabel(today)}"
                        com.aotv.app.ui.common.PosterCard(
                            item = item,
                            onClick = click,
                            sub = sub,
                            showBadges = display.showPosterBadges,
                            showQualityBadges = display.showQualityBadges,
                            cornerRadius = display.posterRadiusDp.dp,
                        )
                    }
                }
                if (hasGames) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Jogos", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            fontFamily = Inter, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(state.upcomingGames, key = { "game:${it.channelId}:${it.startMs}" }) { game ->
                        UpcomingGameCard(
                            game = game, now = now, hideScores = state.sportsHideScores,
                            scoreDelaySec = state.sportsScoreDelaySec, onPlayGame = onPlayGame,
                            onRecordGame = onRecordGame, modifier = Modifier.fillMaxWidth().height(96.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── ÚLTIMOS TRAILERS ──────────────────────────────────────────────────────────

/**
 * Fileira "Últimos Trailers" no padrão do site do TMDB. Abas (Popular, Streaming, Na TV,
 * Para Alugar, Nos Cinemas) e cards 16:9 com play no meio, título e nome do vídeo embaixo.
 * Tocar abre o trailer no nosso ExoPlayer via [onPlay].
 */
@Composable
private fun LatestTrailersRow(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onPlay: (MoviesHomeViewModel.TrailerCard) -> Unit,
    title: String = "Últimos trailers",
    display: HomeDisplayPrefs = HomeDisplayPrefs(),
    /** "Ver tudo" (Lote 19, v1.29.0): abre a grade com todos os trailers da aba atual. null = sem link. */
    onSeeMore: (() -> Unit)? = null,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val rowSpace = com.aotv.app.ui.common.homeRowSpacing(display.density)
    Column(verticalArrangement = Arrangement.spacedBy(if (display.compact) 4.dp else 6.dp)) {
        com.aotv.app.ui.common.SectionHeader(
            title,
            action = onSeeMore?.let { seeMore -> { com.aotv.app.ui.common.SeeAllLink("Ver tudo", seeMore) } },
            underline = display.sectionUnderline, compact = display.compact,
        )
        // Abas roláveis (cabem 5 sem espremer em tela pequena).
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MoviesHomeViewModel.TrailerTab.entries.toList(), key = { it.name }) { tab ->
                com.aotv.app.ui.common.FilterChipPill(
                    label = tab.label,
                    selected = state.trailerTab == tab,
                    onClick = { viewModel.selectTrailerTab(tab) },
                )
            }
        }
        when {
            // Espaço reservado do mesmo tamanho do card: a fileira não "pula" quando carrega.
            state.trailersLoading -> {
                val brush = com.aotv.app.ui.common.skeletonBrush()
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(rowSpace),
                    userScrollEnabled = false,
                ) {
                    items(3) {
                        Column(Modifier.width(264.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            com.aotv.app.ui.common.SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 14.dp, brush)
                            com.aotv.app.ui.common.SkeletonBox(Modifier.fillMaxWidth(0.7f).height(13.dp), 4.dp, brush)
                        }
                    }
                }
            }

            state.latestTrailers.isEmpty() -> Text(
                "Nenhum trailer nessa aba agora.",
                color = TextTertiary, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = pad),
            )

            else -> androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(rowSpace),
            ) {
                items(state.latestTrailers, key = { "${it.tmdbId}:${it.videoKey}" }) { card ->
                    TrailerCardView(card, onClick = { onPlay(card) })
                }
            }
        }
    }
}

@Composable
private fun TrailerCardView(card: MoviesHomeViewModel.TrailerCard, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else if (pressed) 0.97f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "trailerScale",
    )
    Column(
        Modifier
            .width(264.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(BackgroundCard2)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
        ) {
            // Tamanho explícito em px de exibição (card 264dp × 16:9).
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pxW = with(density) { 264.dp.roundToPx() }
            val pxH = pxW * 9 / 16
            val trailerContext = androidx.compose.ui.platform.LocalContext.current
            val trailerRequest = remember(card.backdropUrl, pxW, pxH) {
                coil.request.ImageRequest.Builder(trailerContext).data(card.backdropUrl).size(pxW, pxH).build()
            }
            coil.compose.AsyncImage(
                model = trailerRequest,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
            )
            // Botão de play central (48dp, acima do mínimo de toque).
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Assistir trailer de ${card.title}",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            card.title,
            color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = SoraFontFamily, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (card.videoName.isNotBlank()) {
            Text(
                card.videoName,
                color = TextTertiary, fontSize = 12.sp, lineHeight = 15.sp,
                fontFamily = ManropeFontFamily, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Painel de opções do cartão do Continuar assistindo (Lote 23) ──────────
// Mesmo desenho do TitleOptionsDialog (OptionsPanels.kt — outra frente),
// com "Não hoje" e "Abandonar série"/"Desfazer abandono" a mais. Local
// porque o original não pode ganhar parâmetros sem mexer no arquivo dele;
// quando unificarem, apaga-se este e volta-se a usar o original.
@Composable
private fun ContinueTitleOptionsDialog(
    item: MovieItem,
    isSeriesDropped: Boolean,
    onDismiss: () -> Unit,
    onGoToDetails: () -> Unit,
    onMarkWatched: () -> Unit,
    onNotToday: () -> Unit,
    onDropSeries: () -> Unit,
    onUndropSeries: () -> Unit,
    onRemove: () -> Unit,
) {
    val isLive = item.embedId.startsWith("live|")
    val isSeries = item.type == MediaType.SERIES
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = BackgroundCard) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "Opções do título",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (item.title.isNotBlank()) {
                    Text(
                        item.title,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                ContinueOptionsRow(label = "Ir para detalhes", icon = Icons.Default.Info, onClick = { onDismiss(); onGoToDetails() })
                if (!isLive) {
                    ContinueOptionsRow(label = "Marcar como assistido", icon = Icons.Default.Check, onClick = { onDismiss(); onMarkWatched() })
                    ContinueOptionsRow(label = "Não hoje", icon = Icons.Default.Schedule, onClick = { onDismiss(); onNotToday() })
                }
                if (isSeries) {
                    if (isSeriesDropped) {
                        ContinueOptionsRow(label = "Desfazer abandono", icon = Icons.Default.Restore, onClick = { onDismiss(); onUndropSeries() })
                    } else {
                        ContinueOptionsRow(label = "Abandonar série", icon = Icons.Default.Block, destructive = true, onClick = { onDismiss(); onDropSeries() })
                    }
                }
                ContinueOptionsRow(label = "Remover", icon = Icons.Default.Delete, destructive = true, onClick = { onDismiss(); onRemove() })
            }
        }
    }
}

/** Uma linha do painel: ícone + rótulo, largura toda, toca e fecha. */
@Composable
private fun ContinueOptionsRow(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) Danger else TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (destructive) Danger else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ManropeFontFamily,
        )
    }
}
