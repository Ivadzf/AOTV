package com.aotv.app.ui.movies

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.aotv.app.ui.common.AotvEmptyState
import com.aotv.app.ui.common.MediaPosterCard
import com.aotv.app.ui.common.ScreenTopHeader
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.rememberCoroutineScope
import com.aotv.app.ui.theme.*
import com.aotv.app.data.local.SystemRow

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
    viewModel: MoviesHomeViewModel = hiltViewModel(),
    randomVm: RandomEpisodeViewModel = hiltViewModel(),
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
            else -> HomeSectionsTab(
                state = state, viewModel = viewModel, onItemClick = onItemClick,
                onSearchClick = onSearchClick, onSettingsClick = onSettingsClick,
                onDiscoverClick = onDiscoverClick,
                onSeeMore = seeMore,
                onPlayTrailer = playTrailerFn,
                onCustomize = onCustomize,
                onFolderClick = onFolderClick,
                onPlayEpisode = onPlayEpisode,
                randomVm = randomVm,
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

@Composable
fun MediaCard(
    item: MovieItem,
    onClick: () -> Unit,
    showBadges: Boolean = true,
    cornerRadius: Dp = 14.dp,
) {
    com.aotv.app.ui.common.PosterCard(
        item = item, onClick = onClick, showBadges = showBadges, cornerRadius = cornerRadius,
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
    randomVm: RandomEpisodeViewModel,
) {
    val listState = rememberLazyListState()
    val collections by viewModel.collectionsFlow.collectAsStateWithLifecycle()
    // Modo aleatório (Lote 2): o sorteio abre DIRETO no player quando sai;
    // sem série elegível, mostra o estado vazio em diálogo (não trava).
    val randomState by randomVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(randomState) {
        val ready = randomState as? RandomEpisodeViewModel.RandomState.Ready ?: return@LaunchedEffect
        onPlayEpisode(ready.item, ready.season, ready.episode, ready.episodeTotal)
        randomVm.consume()
    }
    // Listas derivadas estáveis: map/associate novos a cada composição tornavam
    // a fileira não-pulável (o LazyColumn recompunha a fileira inteira a cada
    // emissão do UiState, mesmo sem nada mudar nela).
    val newEpItems = remember(state.newEpisodes) { state.newEpisodes.map { it.item } }
    val newEpBadges = remember(state.newEpisodes) { state.newEpisodes.associate { it.item.tmdbId to it.badge } }
    val newEpSubs = remember(state.newEpisodes) { state.newEpisodes.associate { it.item.tmdbId to it.sub } }

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
        // Descobrir saiu da barra (Fase A, pílula de 4 abas): a entrada fica aqui,
        // no topo da Início.
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
        // Modo aleatório (Lote 2): entrada visível no topo da Início, mesmo sem
        // nada no Continuar assistindo. Tocar sorteia e abre direto no player.
        item(key = "random_episode") {
            RandomEpisodeEntry(
                rolling = randomState is RandomEpisodeViewModel.RandomState.Rolling,
                onRoll = { randomVm.roll() },
            )
        }
        if (heroItems.isNotEmpty()) {
            item(key = "hero") {
                // Trailer só com a Início no topo. Rolou, parou o trailer.
                val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 300 } }
                HeroCarousel(
                    heroItems, state.heroLogos, onItemClick,
                    trailerOn = state.heroTrailer && atTop,
                    trailerFor = viewModel::heroTrailerStream,
                    heroStyle = display.heroStyle,
                )
            }
        }
        // Cada fileira na ordem e com o nome que o perfil escolheu. Sem item, a fileira some.
        state.layout.forEach { row ->
            val custom = row.title
            when (row.system) {
                SystemRow.CONTINUE -> if (state.continueWatching.isNotEmpty()) item(key = row.key) {
                    ContinueRow(row.displayTitle, state, onItemClick, display)
                }
                SystemRow.NEW_EPISODES -> if (state.newEpisodes.isNotEmpty()) item(key = row.key) {
                    SectionRow(
                        row.displayTitle, newEpItems, onItemClick,
                        display = display,
                        badges = newEpBadges,
                        subs = newEpSubs,
                    )
                }
                SystemRow.RECOMMENDED -> if (state.recommended.isNotEmpty()) item(key = row.key) {
                    SectionRow(custom.ifBlank { "Porque você assistiu ${state.recommendedBecause}" }, state.recommended, onItemClick, display = display)
                }
                SystemRow.TRAILERS -> if (state.trailersLoading || state.latestTrailers.isNotEmpty()) item(key = row.key) {
                    LatestTrailersRow(state, viewModel, onPlay = onPlayTrailer, title = row.displayTitle, display = display)
                }
                SystemRow.TRENDING_MOVIES -> if (state.trendingMovies.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.MOVIE, MoviesHomeViewModel.CatalogEndpoint.TRENDING)) } }
                    SectionRow(row.displayTitle, state.trendingMovies, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.TRENDING_TV -> if (state.trendingTv.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.SERIES, MoviesHomeViewModel.CatalogEndpoint.TRENDING)) } }
                    SectionRow(row.displayTitle, state.trendingTv, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.NOW_PLAYING -> if (state.nowPlaying.isNotEmpty()) item(key = row.key) {
                    val more = remember(row.key, row.displayTitle, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(row.displayTitle, MoviesHomeViewModel.CatalogType.MOVIE, MoviesHomeViewModel.CatalogEndpoint.NOW_PLAYING)) } }
                    SectionRow(row.displayTitle, state.nowPlaying, onItemClick, display = display, onSeeMore = more)
                }
                SystemRow.RELEASES -> if (state.releases2026.isNotEmpty()) item(key = row.key) {
                    val t = custom.ifBlank { "Lançamentos ${state.releasesYear}" }
                    val more = remember(row.key, t, onSeeMore) { { onSeeMore(MoviesHomeViewModel.CatalogQuery(t, MoviesHomeViewModel.CatalogType.MOVIE, genreId = null, sort = MoviesHomeViewModel.SORT_RELEASE_MOVIE, year = state.releasesYear)) } }
                    SectionRow(t, state.releases2026, onItemClick, display = display, onSeeMore = more)
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
                        com.aotv.app.ui.collections.CollectionFolderRow(col, row.displayTitle, eyebrow = null) { f -> onFolderClick(col.id, f.id) }
                    }
                } else state.addonRows.firstOrNull { it.ref.key == row.key }?.takeIf { it.loading || it.items.isNotEmpty() }?.let { ar ->
                    item(key = "addon:" + ar.ref.key) {
                        // Carrega ao aparecer na tela (a LazyColumn só compõe o
                        // visível): antes todas as fileiras de addon disparavam
                        // juntas na abertura, cada uma com 40 metas + resolve.
                        LaunchedEffect(ar.ref.key) {
                            if (ar.items.isEmpty() && ar.loading) viewModel.loadAddonRow(ar.ref.key)
                        }
                        if (ar.items.isEmpty()) com.aotv.app.ui.common.SkeletonRail()
                        else SectionRow(
                            ar.title, ar.items, onItemClick,
                            display = display,
                            onNearEnd = if (ar.more) remember(ar.ref.key) { { viewModel.loadAddonRow(ar.ref.key) } } else null,
                        )
                    }
                }
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
    if (s.catalogOn && (s.trendingMovies.isNotEmpty() || s.trendingTv.isNotEmpty())) return ""
    return s.addonRows.firstOrNull { it.items.isNotEmpty() }?.title
        ?: s.layout.firstOrNull { it.system == SystemRow.CONTINUE }?.displayTitle ?: ""
}

/**
 * De onde vêm os títulos do destaque. Fileira escolhida no Personalizar Início, ou no
 * automático o que está em alta no AOTV, senão a primeira fileira de addon, senão o progresso.
 */
internal fun heroCandidates(s: MoviesHomeViewModel.UiState): List<MovieItem> {
    fun itemsOf(key: String): List<MovieItem> = when (SystemRow.of(key)) {
        SystemRow.CONTINUE -> s.continueWatching
        SystemRow.RECOMMENDED -> s.recommended
        SystemRow.TRENDING_MOVIES -> s.trendingMovies
        SystemRow.TRENDING_TV -> s.trendingTv
        SystemRow.NOW_PLAYING -> s.nowPlaying
        SystemRow.RELEASES -> s.releases2026
        else -> s.addonRows.firstOrNull { it.ref.key == key }?.items.orEmpty()
    }
    val chosen = s.heroSource.takeIf { k -> k.isNotBlank() && s.layout.any { it.key == k } }
    return when {
        chosen != null -> itemsOf(chosen)
        s.catalogOn && (s.trendingMovies.isNotEmpty() || s.trendingTv.isNotEmpty()) -> s.trendingMovies + s.trendingTv
        else -> s.addonRows.firstOrNull { it.items.isNotEmpty() }?.items ?: s.continueWatching
    }.take(12)
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
 * Entrada do Modo aleatório (Lote 2, v1.10.0): cartão "Surpreenda-me" no topo
 * da Início (e o mesmo na Biblioteca). Tocar sorteia um episódio das séries do
 * Continuar assistindo + Minha Lista e abre direto no player.
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
) {
    Column {
        com.aotv.app.ui.common.SectionHeader(
            title, underline = display.sectionUnderline, compact = display.compact,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = com.aotv.app.ui.common.pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(com.aotv.app.ui.common.homeRowSpacing(display.density))) {
            items(state.continueWatching, key = { rowKey(it) }) { item ->
                val click = remember(item) { { onItemClick(item) } }
                com.aotv.app.ui.common.ContinueCard(item, click,
                    artwork = state.continueArtwork[com.aotv.app.data.local.posterKey(item)],
                    progress = state.continueProgress[item.tmdbId], remaining = state.continueRemaining[item.tmdbId],
                    episode = state.continueEpisode[item.tmdbId].orEmpty(), compact = display.compact)
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
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items.size })
    // Trailer mudo depois de 5 s parado no mesmo destaque. Trocar de página, rolar a Início ou
    // sair dela (o composable some) para o trailer.
    var trailer by remember { mutableStateOf<Pair<Int, com.aotv.app.data.remote.YoutubeTrailer.TrailerStream>?>(null) }
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
    LaunchedEffect(pagerState.settledPage, active, items.size) {
        trailer = null
        if (items.isEmpty() || !active) return@LaunchedEffect
        val page = pagerState.settledPage
        kotlinx.coroutines.delay(5000)
        trailerFor(items[page])?.let { trailer = page to it; return@LaunchedEffect }
        advance(page)
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
            Box(Modifier.fillMaxSize()) {
                // Arte. Box com o tamanho exato da área, a imagem e o trailer recortam no centro.
                Box(
                    if (wide) Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.8f)
                    else Modifier.fillMaxSize()
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
                            st.videoUrl, st.audioUrl, onEnd = { trailer = null; advance(page) },
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
                    cornerRadius = display.posterRadiusDp.dp,
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
                        showBadges = display.showPosterBadges, cornerRadius = display.posterRadiusDp.dp,
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
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val rowSpace = com.aotv.app.ui.common.homeRowSpacing(display.density)
    Column(verticalArrangement = Arrangement.spacedBy(if (display.compact) 4.dp else 6.dp)) {
        com.aotv.app.ui.common.SectionHeader(
            title, underline = display.sectionUnderline, compact = display.compact,
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
