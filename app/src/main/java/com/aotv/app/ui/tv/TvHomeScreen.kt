package com.aotv.app.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.remote.YoutubeTrailer
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.data.local.SystemRow
import com.aotv.app.data.local.posterKey
import com.aotv.app.data.collections.MediaCollection
import com.aotv.app.ui.movies.heroCandidates
import com.aotv.app.ui.movies.MoviesHomeViewModel
import com.aotv.app.ui.movies.MoviesHomeViewModel.CatalogEndpoint
import com.aotv.app.ui.movies.MoviesHomeViewModel.CatalogQuery
import com.aotv.app.ui.movies.MoviesHomeViewModel.CatalogType
import com.aotv.app.ui.common.rememberReducedMotion
import com.aotv.app.ui.navigation.TvMenuClosed
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Arte em tela cheia com informações sobre o degradê e fileiras na parte inferior.
 * O destaque acompanha o pôster focado e pausa quando o menu cobre a tela.
 */
@Composable
fun TvHomeScreen(
    onItemClick: (MovieItem) -> Unit,
    viewModel: MoviesHomeViewModel = hiltViewModel(),
    navigationOpen: Boolean = false,
    onFolderClick: (String, String) -> Unit = { _, _ -> },
) {
    TvText { TvHomeScreenBody(onItemClick, viewModel, navigationOpen, onFolderClick) }
}

@Composable
private fun TvHomeScreenBody(
    onItemClick: (MovieItem) -> Unit,
    viewModel: MoviesHomeViewModel = hiltViewModel(),
    navigationOpen: Boolean = false,
    onFolderClick: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collections by viewModel.collectionsFlow.collectAsStateWithLifecycle()
    val grid = state.gridQuery
    if (grid != null) {
        androidx.activity.compose.BackHandler { viewModel.closeCatalog() }
        Box(Modifier.fillMaxSize().padding(start = TvMenuClosed)) {
            TvCatalogGrid(state, viewModel, grid.title, onItemClick)
        }
        return
    }

    val scope = rememberCoroutineScope()
    val candidates = heroCandidates(state)
    LaunchedEffect(candidates.map { posterKey(it) }) {
        candidates.take(8).forEach { if (it.backdropUrl.isBlank()) viewModel.ensureHeroBackdrop(it.tmdbId, it.type == MediaType.SERIES) }
    }
    val heroItems = candidates.mapNotNull { item ->
        val backdrop = item.backdropUrl.ifBlank { state.heroBackdrops[item.tmdbId].orEmpty() }
        if (backdrop.isBlank()) null else item.copy(backdropUrl = backdrop)
    }.take(8)
    var page by remember { mutableIntStateOf(0) }
    // O título selecionado continua no destaque ao voltar para os botões.
    var focusedItem by remember { mutableStateOf<MovieItem?>(null) }
    val carouselItem = heroItems.getOrNull(page.coerceAtMost((heroItems.size - 1).coerceAtLeast(0)))
    val hero = focusedItem ?: carouselItem

    // Trailer aberto pelo botão do destaque ou pela fileira de trailers.
    var trailer by remember { mutableStateOf<YoutubeTrailer.TrailerStream?>(null) }
    var trailerCard by remember { mutableStateOf<MoviesHomeViewModel.TrailerCard?>(null) }
    var trailerTitle by remember { mutableStateOf("") }
    var trailerBusy by remember { mutableStateOf(false) }
    fun playCard(card: MoviesHomeViewModel.TrailerCard) {
        if (trailerBusy) return
        trailerBusy = true
        trailerCard = card
        scope.launch {
            trailer = viewModel.trailerStream(card.videoKey)
            trailerTitle = card.title
            trailerBusy = false
            if (trailer == null) trailerCard = null
        }
    }

    // Carrossel avança a cada 8 s com o foco no destaque (ou antes de qualquer foco).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycle.lifecycle.currentStateFlow.collectAsState()
    val homeActive = !navigationOpen && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(page, focusedItem, heroItems.size, trailer, homeActive) {
        if (!homeActive || focusedItem != null || heroItems.size < 2 || trailer != null) return@LaunchedEffect
        delay(8000)
        page = (page + 1) % heroItems.size
    }
    // Logo, arte e faixa etária chegam depois de uma pausa no foco, como no webOS.
    LaunchedEffect(hero?.tmdbId) {
        val h = hero ?: return@LaunchedEffect
        delay(380)
        val series = h.type == MediaType.SERIES
        viewModel.ensureHeroLogo(h.tmdbId, series)
        viewModel.ensureHeroCert(h.tmdbId, series)
        if (h.backdropUrl.isBlank()) viewModel.ensureHeroBackdrop(h.tmdbId, series)
    }

    val rails = rememberLazyListState()
    val loadMore by remember {
        derivedStateOf {
            val li = rails.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 2
        }
    }
    val genresOn = state.layout.any { it.system == SystemRow.GENRES }
    LaunchedEffect(loadMore, genresOn) { if (loadMore && genresOn) viewModel.loadMoreSections() }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (state.isLoading) {
            TvSpinner("Carregando catálogo", Modifier.align(Alignment.Center))
            return@Box
        }
        // Sem rede ou sem chave TMDB: nenhuma fileira carrega e a tela ficava preta, sem nada
        // focável pro D-pad. Mesmo aviso da versão celular (AotvEmptyState), em layout de TV.
        val nothingToShow = heroItems.isEmpty() && state.continueWatching.isEmpty() && state.newEpisodes.isEmpty() &&
            state.addonRows.isEmpty() && state.recommended.isEmpty() && state.nowPlaying.isEmpty() &&
            state.releases2026.isEmpty() && state.homeSections.isEmpty() && collections.none { c -> c.folders.isNotEmpty() && state.layout.any { it.collectionId == c.id } }
        if (nothingToShow) {
            TvHomeFeedEmpty(hasTmdbKey = state.hasTmdbKey, onRetry = viewModel::load)
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroH = maxHeight * 0.62f
            TvHeroBanner(
                item = hero,
                state = state,
                height = maxHeight,
                pageCount = if (focusedItem == null) heroItems.size else 0,
                page = page,
                trailerOn = homeActive && state.heroTrailer && trailer == null,
                trailerFor = viewModel::heroTrailerStream,
                onTrailerEnd = { if (focusedItem == null && heroItems.size > 1) page = (page + 1) % heroItems.size },
                trailerBusy = trailerBusy && trailerCard == null,
                onWatch = { hero?.let(onItemClick) },
                onTrailer = {
                    val h = hero ?: return@TvHeroBanner
                    if (trailerBusy) return@TvHeroBanner
                    trailerBusy = true
                    scope.launch {
                        trailer = viewModel.heroTrailerStream(h)
                        trailerTitle = h.title
                        trailerBusy = false
                    }
                },
                modifier = Modifier
                    .tvScrollTop { rails.animateScrollToItem(0) },
            )
            TvScrollArea(inset = 1.25.r) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(start = TvMenuClosed, top = heroH),
                    state = rails,
                    contentPadding = PaddingValues(bottom = 4.r),
                ) {
                    homeRails(state, viewModel, onItemClick, onFocus = { focusedItem = it }, onTrailer = ::playCard,
                        collections = collections, onFolderClick = onFolderClick)
                }
            }
        }
        trailer?.let { st ->
            val card = trailerCard
            val fila = state.latestTrailers
            val idx = fila.indexOfFirst { it.tmdbId == card?.tmdbId && it.videoKey == card.videoKey }
            val next = if (card != null && idx >= 0 && idx < fila.lastIndex) fila[idx + 1] else null
            com.aotv.app.ui.common.TrailerExoOverlay(
                videoUrl = st.videoUrl,
                audioUrl = st.audioUrl,
                onClose = { trailer = null; trailerCard = null },
                title = trailerTitle,
                onOpenDetail = card?.let { c ->
                    {
                        trailer = null; trailerCard = null
                        onItemClick(
                            MovieItem(
                                tmdbId = c.tmdbId, embedId = c.tmdbId.toString(), title = c.title, overview = "",
                                posterUrl = c.posterUrl, backdropUrl = c.backdropUrl, year = "", rating = 0.0, type = c.type,
                            )
                        )
                    }
                },
                onNext = next?.let { n -> { trailer = null; playCard(n) } },
            )
        }
    }
}

/** Sem rede ou sem chave TMDB: aviso focável com botão de tentar de novo (em vez de tela preta). */
@Composable
private fun TvHomeFeedEmpty(hasTmdbKey: Boolean, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(start = TvMenuClosed).padding(horizontal = tvPad),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (hasTmdbKey) "Não deu para carregar agora" else "Configure sua chave TMDB",
            color = TextPrimary, fontSize = 1.8.rs, fontWeight = FontWeight.Bold, fontFamily = DisplayFont,
        )
        Text(
            if (hasTmdbKey) "Confira sua conexão e tente de novo." else "Vá em Configurações e cole sua chave TMDB para ver o catálogo.",
            color = TextTertiary, fontSize = 1.1.rs, modifier = Modifier.padding(top = 0.6.r).widthIn(max = 44.r),
        )
        Spacer(Modifier.height(1.4.r))
        TvButton("Tentar de novo", onRetry, Modifier.focusRequester(retryFocus))
    }
}

/** Destaque (.home-hero). Arte com troca suave, trailer mudo depois de 5 s parado, texto e botões. */
@Composable
private fun TvHeroBanner(
    item: MovieItem?,
    state: MoviesHomeViewModel.UiState,
    height: Dp,
    pageCount: Int,
    page: Int,
    trailerOn: Boolean,
    trailerFor: suspend (MovieItem) -> YoutubeTrailer.TrailerStream?,
    onTrailerEnd: () -> Unit,
    trailerBusy: Boolean,
    onWatch: () -> Unit,
    onTrailer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = item?.backdropUrl?.ifBlank { state.heroBackdrops[item.tmdbId].orEmpty() }.orEmpty()
    val reduced = rememberReducedMotion()
    // Trailer mudo depois de 5 s no mesmo título, só com a tela na frente.
    var muted by remember { mutableStateOf<Pair<Int, YoutubeTrailer.TrailerStream>?>(null) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val resumed by owner.lifecycle.currentStateFlow.collectAsState()
    val active = trailerOn && resumed.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(item?.tmdbId, active) {
        muted = null
        val it = item ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        delay(5000)
        trailerFor(it)?.let { st -> muted = it.tmdbId to st }
    }
    Box(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(backdrop, animationSpec = tween(if (reduced) 0 else 280), label = "heroBg") { url ->
                if (url.isNotBlank()) AsyncImage(
                    url, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.5f),
                )
            }
            muted?.takeIf { it.first == item?.tmdbId }?.let { (_, st) ->
                com.aotv.app.ui.common.MutedTrailer(
                    st.videoUrl, st.audioUrl, onEnd = { muted = null; onTrailerEnd() }, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Background.copy(alpha = .93f), 0.28f to Background.copy(alpha = .75f),
            0.6f to Background.copy(alpha = .2f), 0.82f to Color.Transparent,
        )))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.7f).background(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.37f to Background.copy(alpha = .4f),
                0.68f to Background.copy(alpha = .94f), 0.92f to Background,
            )
        ))
        if (item == null) return@Box
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = TvMenuClosed + tvPad, bottom = height * 0.42f).fillMaxWidth(0.53f),
        ) {
            TvTitleArtwork(item.title, state.heroLogos[item.tmdbId].orEmpty(),
                Modifier.padding(bottom = 1.1.r), hero = true)
            TvMetaRow(
                rating = item.rating, cert = item.certification.ifBlank { state.heroCerts[item.tmdbId].orEmpty() },
                year = item.year, extra = listOf(if (item.type == MediaType.SERIES) "Série" else "Filme"), genres = item.genres,
            )
            if (item.overview.isNotBlank()) Text(
                item.overview, color = TextSecondary, fontSize = 1.15.rs, lineHeight = 1.84.rs, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.r).widthIn(max = 40.r),
            )
            Row(Modifier.padding(top = 1.4.r).offset(x = (-0.2).r), verticalAlignment = Alignment.CenterVertically) {
                HeroAction("Assistir", onWatch, primary = true)
                Spacer(Modifier.width(0.8.r))
                HeroAction(if (trailerBusy) "Carregando…" else "Trailer", onTrailer, busy = trailerBusy)
                if (pageCount > 1) {
                    Spacer(Modifier.width(1.6.r))
                    // Indicador do carrossel.
                    Row(horizontalArrangement = Arrangement.spacedBy(0.5.r)) {
                        repeat(pageCount) { i ->
                            val on = i == page
                            Box(
                                Modifier.height(0.4.r).width(if (on) 1.6.r else 0.4.r).clip(RoundedCornerShape(50))
                                    .background(if (on) Accent else TextTertiary.copy(alpha = .6f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAction(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    busy: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = tvFocusScale(focused, 1.025f)
    val shape = RoundedCornerShape(50)
    val foreground = if (primary) TextOnLight else Color.White
    Row(
        Modifier.padding(0.2.r).graphicsLayer { scaleX = scale; scaleY = scale }
            .height(3.5.r)
            .then(if (focused) Modifier.border(0.18.r, Color.White, shape) else Modifier)
            .clip(shape).background(if (primary) Color(0xFFF5F5F5) else Color.White.copy(alpha = .14f))
            .onFocusChanged { focused = it.isFocused }
            .tvClick({ if (!busy) onClick() })
            .padding(horizontal = 1.7.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) com.aotv.app.ui.common.SpinningIcon(tint = foreground, size = 1.5.r)
        else Icon(
            if (primary) Icons.Default.PlayArrow else Icons.Default.Theaters,
            null, tint = foreground, modifier = Modifier.size(1.5.r),
        )
        Spacer(Modifier.width(0.6.r))
        Text(label, color = foreground, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 1.1.rs, maxLines = 1)
    }
}

/** Fileiras da Início, na ordem do webOS. */
private fun androidx.compose.foundation.lazy.LazyListScope.homeRails(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onItemClick: (MovieItem) -> Unit,
    onFocus: (MovieItem) -> Unit,
    onTrailer: (MoviesHomeViewModel.TrailerCard) -> Unit,
    collections: List<MediaCollection>,
    onFolderClick: (String, String) -> Unit,
) {
    state.layout.forEach { row ->
        when (row.system) {
            SystemRow.CONTINUE -> if (state.continueWatching.isNotEmpty()) item(key = row.key) {
                TvRail(row.displayTitle, preserveColumnOnVertical = true) {
                    items(state.continueWatching, key = { posterKey(it) }) { item ->
                        TvContinueCard(
                            item, { onItemClick(item) }, artwork = state.continueArtwork[posterKey(item)],
                            progress = state.continueProgress[item.tmdbId], remaining = state.continueRemaining[item.tmdbId],
                            episode = state.continueEpisode[item.tmdbId].orEmpty(), onFocus = { onFocus(item) },
                        )
                    }
                }
            }
            SystemRow.NEW_EPISODES -> if (state.newEpisodes.isNotEmpty()) item(key = row.key) {
                PosterRail(
                    row.displayTitle, state.newEpisodes.map { it.item }, onItemClick, onFocus,
                    badges = state.newEpisodes.associate { it.item.tmdbId to it.badge },
                    subs = state.newEpisodes.associate { it.item.tmdbId to it.sub },
                )
            }
            SystemRow.RECOMMENDED -> if (state.recommended.isNotEmpty()) item(key = row.key) {
                PosterRail(row.title.ifBlank { "Porque você assistiu ${state.recommendedBecause}" }, state.recommended, onItemClick, onFocus)
            }
            SystemRow.TRENDING_MOVIES -> if (state.trendingMovies.isNotEmpty()) item(key = row.key) {
                PosterRail(row.displayTitle, state.trendingMovies, onItemClick, onFocus,
                    seeAll = { viewModel.openCatalog(CatalogQuery(row.displayTitle, CatalogType.MOVIE, CatalogEndpoint.TRENDING)) })
            }
            SystemRow.TRENDING_TV -> if (state.trendingTv.isNotEmpty()) item(key = row.key) {
                PosterRail(row.displayTitle, state.trendingTv, onItemClick, onFocus,
                    seeAll = { viewModel.openCatalog(CatalogQuery(row.displayTitle, CatalogType.SERIES, CatalogEndpoint.TRENDING)) })
            }
            SystemRow.NOW_PLAYING -> if (state.nowPlaying.isNotEmpty()) item(key = row.key) {
                PosterRail(row.displayTitle, state.nowPlaying, onItemClick, onFocus,
                    seeAll = { viewModel.openCatalog(CatalogQuery(row.displayTitle, CatalogType.MOVIE, CatalogEndpoint.NOW_PLAYING)) })
            }
            SystemRow.RELEASES -> if (state.releases2026.isNotEmpty()) item(key = row.key) {
                val title = row.title.ifBlank { "Lançamentos ${state.releasesYear}" }
                PosterRail(title, state.releases2026, onItemClick, onFocus,
                    seeAll = { viewModel.openCatalog(CatalogQuery(title, CatalogType.MOVIE, genreId = null, sort = MoviesHomeViewModel.SORT_RELEASE_MOVIE, year = state.releasesYear)) })
            }
            SystemRow.TRAILERS -> if (state.trailersLoading || state.latestTrailers.isNotEmpty()) item(key = row.key) {
                TrailersRail(state, viewModel, onFocus, onTrailer, row.displayTitle)
            }
            SystemRow.GENRES -> {
                items(state.homeSections, key = { it.id }) { sec ->
                    PosterRail(sec.title, sec.items, onItemClick, onFocus,
                        onNearEnd = { viewModel.loadMoreSectionItems(sec.id) }, seeAll = { viewModel.openCatalog(sec.query) })
                }
                if (state.isLoadingSections) item(key = "more") { TvSpinner() }
            }
            null -> if (row.collectionId != null) {
                collections.firstOrNull { it.id == row.collectionId }?.takeIf { it.folders.isNotEmpty() }?.let { col ->
                    item(key = row.key) {
                        TvScaled {
                            com.aotv.app.ui.collections.CollectionFolderRow(col, row.displayTitle, eyebrow = null) { f -> onFolderClick(col.id, f.id) }
                        }
                    }
                }
            } else state.addonRows.firstOrNull { it.ref.key == row.key }?.takeIf { it.loading || it.items.isNotEmpty() }?.let { ar ->
                item(key = row.key) {
                    if (ar.items.isEmpty()) TvSkeletonRail()
                    else PosterRail(ar.title, ar.items, onItemClick, onFocus,
                        onNearEnd = if (ar.more) ({ viewModel.loadAddonRow(ar.ref.key) }) else null)
                }
            }
        }
    }
}

@Composable
private fun PosterRail(
    title: String,
    items: List<MovieItem>,
    onItemClick: (MovieItem) -> Unit,
    onFocus: (MovieItem) -> Unit,
    progress: Map<Int, Float>? = null,
    subs: Map<Int, String>? = null,
    badges: Map<Int, String>? = null,
    onNearEnd: (() -> Unit)? = null,
    seeAll: (() -> Unit)? = null,
) {
    val row = rememberLazyListState()
    if (onNearEnd != null) {
        val near by remember {
            derivedStateOf {
                val li = row.layoutInfo
                li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
            }
        }
        LaunchedEffect(near, items.size) { if (near) onNearEnd() }
    }
    TvRail(title, state = row, preserveColumnOnVertical = true) {
        items(items.size, key = { "${items[it].type}:${items[it].tmdbId}" }) { index ->
            val item = items[index]
            TvPoster(
                item = item, onClick = { onItemClick(item) }, width = 12.5.r,
                progress = progress?.get(item.tmdbId), subtitle = subs?.get(item.tmdbId), badge = badges?.get(item.tmdbId),
                onFocus = { onFocus(item) }, qualityNeighbors = items.drop(index + 1).take(7),
            )
        }
        if (seeAll != null) item(key = "seeAll") { SeeAllCard(seeAll) }
    }
}

/** Cartão "Ver tudo" no fim da fileira (.see-all). */
@Composable
private fun SeeAllCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val s = tvFocusScale(focused, 1.06f)
    val shape = RoundedCornerShape(0.9.r)
    Column(
        Modifier.width(12.5.r).aspectRatio(2f / 3f).scale(s).clip(shape).background(BackgroundCard)
            .border(if (focused) 0.22.r else 0.1.r, if (focused) FocusRing else Border, shape)
            .onFocusChanged { focused = it.isFocused }.tvClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextSecondary, modifier = Modifier.size(2.6.r))
        Text("Ver tudo", color = TextSecondary, fontSize = 1.05.rs, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 0.8.r))
    }
}

/** Últimos trailers, com abas em chips e cartões largos. */
@Composable
private fun TrailersRail(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    onFocus: (MovieItem) -> Unit,
    onTrailer: (MoviesHomeViewModel.TrailerCard) -> Unit,
    title: String = "Últimos trailers",
) {
    Column(Modifier.fillMaxWidth().tvRow().padding(top = 1.3.r)) {
        TvRailHead(title)
        TvRow(gap = 0.75.r, vertical = 0.6.r) {
            items(MoviesHomeViewModel.TrailerTab.entries.toList(), key = { it.name }) { tab ->
                TvChip(tab.label, state.trailerTab == tab, { viewModel.selectTrailerTab(tab) })
            }
        }
        when {
            state.trailersLoading -> TvSpinner("Carregando trailers")
            state.latestTrailers.isEmpty() -> Text(
                "Nenhum trailer nessa aba agora.", color = TextTertiary, fontSize = 1.rs,
                modifier = Modifier.padding(horizontal = tvPad, vertical = 1.r),
            )
            else -> TvRow(preserveColumnOnVertical = true) {
                items(state.latestTrailers, key = { "${it.tmdbId}:${it.videoKey}" }) { c ->
                    TvWideCard(
                        image = c.backdropUrl, title = c.title, subtitle = c.videoName, playIcon = true, width = 24.r,
                        onClick = { onTrailer(c) },
                        onFocus = {
                            onFocus(MovieItem(c.tmdbId, c.tmdbId.toString(), c.title, "", c.posterUrl, c.backdropUrl, "", 0.0, type = c.type))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSkeletonRail() {
    val brush = com.aotv.app.ui.common.skeletonBrush()
    Column(Modifier.padding(top = 1.3.r)) {
        Box(Modifier.padding(start = tvPad).size(16.r, 1.8.r).clip(RoundedCornerShape(0.5.r)).background(brush))
        Row(Modifier.padding(start = tvPad, top = 1.r), horizontalArrangement = Arrangement.spacedBy(1.25.r)) {
            repeat(8) { Box(Modifier.size(12.5.r, 18.75.r).clip(RoundedCornerShape(0.9.r)).background(brush)) }
        }
    }
}

/** Grade do "Ver tudo" (screens/catalog.js), 8 por linha e mais páginas conforme desce. */
@Composable
private fun TvCatalogGrid(
    state: MoviesHomeViewModel.UiState,
    viewModel: MoviesHomeViewModel,
    title: String,
    onItemClick: (MovieItem) -> Unit,
) {
    val gs = rememberLazyGridState()
    val near by remember {
        derivedStateOf {
            val li = gs.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 10
        }
    }
    LaunchedEffect(near) { if (near) viewModel.loadMoreGrid() }
    TvScrollArea(inset = 3.r) {
        LazyVerticalGrid(
            GridCells.Fixed(8), Modifier.fillMaxSize().background(Background), state = gs,
            contentPadding = PaddingValues(start = tvPad, end = tvPad, bottom = 5.r),
            horizontalArrangement = Arrangement.spacedBy(1.3.r),
            verticalArrangement = Arrangement.spacedBy(2.r),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "head") {
                Column(Modifier.padding(top = 2.6.r, bottom = 1.r)) {
                    Text(title, color = TextPrimary, fontSize = 2.6.rs, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).rs)
                    Text(
                        if (state.gridLoading) "Carregando" else "${state.gridItems.size} títulos",
                        color = TextTertiary, fontSize = 1.1.rs, modifier = Modifier.padding(top = 0.45.r),
                    )
                }
            }
            if (state.gridLoading) item(span = { GridItemSpan(maxLineSpan) }) { TvSpinner() }
            items(state.gridItems.size, key = { state.gridItems[it].tmdbId }) { i ->
                val it = state.gridItems[i]
                TvPoster(it, { onItemClick(it) }, width = Dp.Unspecified)
            }
            if (state.gridLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { TvSpinner() }
        }
    }
}
