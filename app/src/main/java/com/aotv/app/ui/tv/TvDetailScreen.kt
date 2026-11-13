package com.aotv.app.ui.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.remote.YoutubeTrailer
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.movies.DetailPrefsViewModel
import com.aotv.app.ui.movies.MovieDetailViewModel
import com.aotv.app.ui.movies.MoviePlayerSource
import com.aotv.app.ui.movies.SourceList
import com.aotv.app.ui.movies.rememberDetailPrimary
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun brDate(iso: String): String =
    iso.split("-").takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" } ?: ""

/**
 * Detalhe da TV no desenho do webOS (screens/detail.js). Destaque quase da altura da tela com a
 * arte à direita, logo, metadados, datas, sinopse, botões em pílula e temporadas. Embaixo os
 * episódios (série) ou as fontes em grade de cartões (filme), elenco, coleção e relacionados.
 * A regra do botão principal é a mesma do celular ([rememberDetailPrimary]).
 */
@Composable
fun TvDetailScreen(
    item: MovieItem,
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit,
    onRelatedClick: (MovieItem) -> Unit,
    // v1.4, Fase 4 — tocar um addon Kodi na folha de fontes abre o navegador dele.
    // v1.5 — busca automática: leva (addonId, título da série ou do filme).
    onOpenKodiAddon: (String, String) -> Unit = { _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel(),
    // Só a transição do pôster (Fase B) faz sentido na TV; o resto fica no celular.
    detailPrefs: DetailPrefsViewModel = hiltViewModel(),
) {
    TvText { TvDetailScreenBody(item, onPlayEpisode, onPlayDirect, onRelatedClick, onOpenKodiAddon, viewModel, detailPrefs) }
}

@Composable
private fun TvDetailScreenBody(
    item: MovieItem,
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit,
    onRelatedClick: (MovieItem) -> Unit,
    onOpenKodiAddon: (String, String) -> Unit = { _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel(),
    detailPrefs: DetailPrefsViewModel = hiltViewModel(),
) {
    LaunchedEffect(item.tmdbId) { viewModel.load(item) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Item que chegou só com o id IMDb e teve o TMDB resolvido ao abrir.
    val detailItem = if (state.resolvedTmdbId > 0) item.copy(tmdbId = state.resolvedTmdbId) else item
    val primary = rememberDetailPrimary(detailItem, state, viewModel, onPlayEpisode, onPlayDirect)
    val isSeries = item.type == MediaType.SERIES
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    // O catálogo às vezes traz o texto que o detalhe em português não tem, então vale o primeiro preenchido.
    fun pick(vararg v: String?) = v.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    val title = pick(state.item?.title, state.series?.title, item.title)
    val overview = pick(state.item?.overview, state.series?.overview, item.overview)
    val backdrop = pick(state.item?.backdropUrl, state.series?.backdropUrl, item.backdropUrl)
    val year = state.item?.year ?: state.series?.firstAirYear ?: item.year
    val rating = state.item?.rating ?: state.series?.rating ?: item.rating
    val genres = (state.item?.genres ?: state.series?.genres).orEmpty().ifEmpty { item.genres }
    val cert = state.item?.certification ?: state.series?.certification ?: ""
    val seasons = primary.numberOfSeasons
    val runtime = state.runtimeMin.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h %02dmin".format(it % 60) else "$it min" }.orEmpty()

    // Foco inicial no botão principal, que segura o foco mesmo travado em "Carregando".
    val playFocus = remember { FocusRequester() }
    var playGotFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (playGotFocus) return@LaunchedEffect
            runCatching { playFocus.requestFocus() }
            delay(150)
        }
    }
    val sourcesFocus = remember { FocusRequester() }

    // Transição do pôster (Fase B): o destaque cresce e aparece ao abrir.
    val posterTransition by detailPrefs.posterTransition.collectAsStateWithLifecycle()
    var detailEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { detailEntered = true }
    val introScale by animateFloatAsState(
        targetValue = if (posterTransition && !detailEntered) 0.94f else 1f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "tv_detail_intro_scale",
    )
    val introAlpha by animateFloatAsState(
        targetValue = if (posterTransition && !detailEntered) 0f else 1f,
        animationSpec = tween(350),
        label = "tv_detail_intro_alpha",
    )

    var trailer by remember { mutableStateOf<YoutubeTrailer.TrailerStream?>(null) }
    var trailerFrame by remember { mutableStateOf(false) }
    var trailerBusy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroH = maxOf(maxHeight * 0.92f, 44.r)
            TvScrollArea(inset = 2.5.r) {
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 6.r)) {
                    item(key = "hero") {
                        Box(
                            Modifier.fillMaxWidth().height(heroH)
                                .graphicsLayer {
                                    scaleX = introScale
                                    scaleY = introScale
                                    alpha = introAlpha
                                }
                                .tvScrollTop { list.animateScrollToItem(0) },
                        ) {
                            if (backdrop.isNotBlank()) AsyncImage(
                                backdrop, null,
                                Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.82f),
                                contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.6f),
                            )
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                                0f to Background, 0.2f to Background, 0.5f to Background.copy(alpha = .6f), 0.86f to Color.Transparent,
                            )))
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.62f).background(
                                Brush.verticalGradient(listOf(Color.Transparent, Background))
                            ))
                            Column(Modifier.align(Alignment.BottomStart).padding(start = tvPad, bottom = 2.2.r).fillMaxWidth(0.62f)) {
                                TvTitleArtwork(title, state.logoUrl, Modifier.padding(bottom = 1.3.r))
                                TvMetaRow(
                                    rating, cert, year,
                                    extra = listOf(if (isSeries) (if (seasons == 1) "1 temporada" else "$seasons temporadas") else runtime),
                                    genres = genres,
                                )
                                val rel = brDate(if (isSeries) state.series?.firstAirDate.orEmpty() else state.item?.releaseDate.orEmpty())
                                val next = brDate(state.series?.nextEpisodeDate.orEmpty())
                                if (rel.isNotBlank() || next.isNotBlank()) Row(Modifier.padding(top = 0.9.r), horizontalArrangement = Arrangement.spacedBy(1.6.r)) {
                                    if (rel.isNotBlank()) Text("Lançamento · $rel", color = TextTertiary, fontSize = 1.rs)
                                    if (next.isNotBlank()) Text(
                                        "Novo episódio · $next" + (state.series?.nextEpisodeSE?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""),
                                        color = Accent, fontSize = 1.rs,
                                    )
                                }
                                Text(
                                    overview.ifBlank { if (state.isLoading) "" else "Sem sinopse disponível." },
                                    color = TextSecondary, fontSize = 1.2.rs, lineHeight = 1.9.rs, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.r).widthIn(max = 56.r),
                                )
                                // Botões (.detail-actions)
                                Row(Modifier.padding(top = 1.4.r).offset(x = (-0.2).r), horizontalArrangement = Arrangement.spacedBy(0.8.r)) {
                                    TvButton(
                                        if (primary.ready) primary.label else "Carregando",
                                        primary.play,
                                        Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) playGotFocus = true },
                                        icon = if (primary.ready && !primary.enabled) Icons.Default.Block else Icons.Default.PlayArrow,
                                        primary = primary.enabled || !primary.ready,
                                        enabled = primary.enabled || !primary.ready,
                                        busy = !primary.ready,
                                    )
                                    TvButton(
                                        if (state.isSaved) "Na Lista" else "Minha Lista", { viewModel.toggleSave() },
                                        icon = if (state.isSaved) Icons.Default.Check else Icons.Default.Add, on = state.isSaved,
                                    )
                                    if (state.trailerKey.isNotBlank()) TvButton(
                                        if (trailerBusy) "Carregando…" else "Trailer",
                                        {
                                            if (!trailerBusy) scope.launch {
                                                trailerBusy = true
                                                val st = viewModel.trailerStream()
                                                trailerBusy = false
                                                if (st != null) trailer = st else trailerFrame = true
                                            }
                                        },
                                        icon = Icons.Default.Theaters, busy = trailerBusy,
                                    )
                                    if (!isSeries && state.stremioStreams.isNotEmpty()) TvButton("Fontes", {
                                        scope.launch {
                                            list.animateScrollToItem(1)
                                            runCatching { sourcesFocus.requestFocus() }
                                        }
                                    }, icon = Icons.Default.SwapHoriz)
                                    primary.nextSeries?.let { ns ->
                                        TvButton("Próximo seriado", { onRelatedClick(ns) }, icon = Icons.Default.SkipNext)
                                    }
                                }
                                // Temporadas (.detail-seasons)
                                if (isSeries && seasons > 1) Row(
                                    Modifier.padding(top = 0.4.r).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(0.75.r),
                                ) {
                                    (1..seasons).forEach { n ->
                                        TvChip("Temporada $n", state.selectedSeason == n, { viewModel.loadSeason(detailItem.tmdbId, n) })
                                    }
                                }
                            }
                        }
                    }

                    if (isSeries) item(key = "eps") {
                        TvEpisodes(state, detailItem, onPlayEpisode, onPlayDirect, viewModel::selectEpisode)
                    } else item(key = "sources") {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = tvPad, end = tvPad, top = 1.6.r, bottom = 0.8.r),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    TvEyebrow("Disponível em")
                                    Text("Fontes", color = TextPrimary, fontSize = 1.6.rs, fontWeight = FontWeight.Bold)
                                }
                                TvCountPill(
                                    if (state.stremioLoading) "Buscando"
                                    else state.stremioStreams.size.let { if (it == 1) "1 encontrada" else "$it encontradas" }
                                )
                            }
                            Column(
                                Modifier.padding(horizontal = tvPad).focusRequester(sourcesFocus).focusGroup(),
                                verticalArrangement = Arrangement.spacedBy(1.r),
                            ) {
                                SourceList(
                                    streams = state.stremioStreams,
                                    facts = state.movieFacts(),
                                    mediaTitle = state.item?.title.orEmpty(),
                                    onPlay = { s ->
                                        viewModel.recordPlayback()
                                        onPlayDirect(s.url, 0, "", primary.playerSources, 0, 0, 0)
                                    },
                                    onDownload = { null },
                                    cols = 2,
                                    television = true,
                                    kodiAddons = state.kodiVideoAddons,
                                    onOpenKodiAddon = { addonId -> onOpenKodiAddon(addonId, title) },
                                    // v1.9.0 (Lote 1) — botão Recarregar na TV: igual ao do celular.
                                    onReloadSources = { viewModel.reloadMovieSources() },
                                    loading = state.stremioLoading,
                                )
                            }
                            when {
                                state.stremioLoading -> Text("Buscando em addons Stremio", color = TextTertiary, fontSize = 1.rs, modifier = Modifier.padding(horizontal = tvPad, vertical = 1.r))
                                state.stremioStreams.isEmpty() && !state.sourcesLoading -> TvHint("Nenhuma fonte encontrada para este título.")
                            }
                        }
                    }

                    if (state.cast.isNotEmpty()) item(key = "cast") {
                        TvRail("Elenco") {
                            items(state.cast, key = { it.id }) { c ->
                                TvCastCard(c.name, c.character, c.photoUrl) { viewModel.openPerson(c.id, c.name) }
                            }
                        }
                    }
                    if (state.collectionItems.size > 1) item(key = "col") {
                        TvRail(state.collectionName, eyebrow = "Coleção") {
                            itemsIndexed(state.collectionItems, key = { _, m -> m.tmdbId }) { i, m ->
                                val cur = m.tmdbId == state.item?.tmdbId
                                TvPoster(
                                    m, { if (!cur) onRelatedClick(m) }, order = i + 1, current = cur,
                                    subtitle = if (cur) "Assistindo" else null,
                                )
                            }
                        }
                    }
                    if (state.related.isNotEmpty()) item(key = "rel") {
                        TvRail("Relacionados") {
                            items(state.related, key = { it.tmdbId }) { m ->
                                TvPoster(m, { onRelatedClick(m) })
                            }
                        }
                    }
                }
            }
        }

        // Fontes do episódio no painel lateral (segurar OK no episódio).
        state.selectedEpisode?.let { (season, episode) ->
            val ep = state.seasons[season]?.firstOrNull { it.episodeNumber == episode }
            TvSourcePanel(
                title = "T$season E$episode · Fontes",
                sub = ep?.name.orEmpty(),
                onDismiss = { viewModel.clearSelectedEpisode() },
            ) {
                SourceList(
                    streams = state.episodeStremioStreams,
                    facts = state.episodeFacts(),
                    mediaTitle = "",
                    onPlay = { s ->
                        viewModel.clearSelectedEpisode()
                        onPlayDirect(
                            s.url, 0, "T$season:E$episode",
                            state.episodeStremioStreams.map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) },
                            season, episode, state.seasons[season]?.size ?: 0,
                        )
                    },
                    onDownload = { null },
                    cols = 1,
                    television = true,
                    kodiAddons = state.kodiVideoAddons,
                    onOpenKodiAddon = { addonId ->
                        viewModel.clearSelectedEpisode()
                        onOpenKodiAddon(addonId, title)
                    },
                    // v1.9.0 (Lote 1) — botão Recarregar no painel de fontes do episódio na TV.
                    onReloadSources = { viewModel.reloadEpisodeSources() },
                    loading = state.episodeStremioLoading,
                )
                when {
                    state.episodeStremioLoading -> TvSpinner("Buscando fontes")
                    state.episodeStremioStreams.isEmpty() -> Text("Nenhum addon tem este episódio.", color = TextTertiary, fontSize = 1.rs)
                }
            }
        }

        state.personName?.let { person ->
            TvPersonOverlay(person, state.personCredits, state.personLoading, { viewModel.closePerson() }) { m ->
                viewModel.closePerson(); onRelatedClick(m)
            }
        }

        trailer?.let { st -> com.aotv.app.ui.common.TrailerExoOverlay(st.videoUrl, st.audioUrl, onClose = { trailer = null }, title = title) }
        if (trailerFrame && state.trailerKey.isNotBlank()) {
            com.aotv.app.ui.common.TrailerYoutubeOverlay(state.trailerKeys.ifEmpty { listOf(state.trailerKey) }) { trailerFrame = false }
        }
    }
}

/** Episódios da temporada em cartões largos (.episode-card), com VISTO e VENDO. */
@Composable
private fun TvEpisodes(
    state: MovieDetailViewModel.UiState,
    item: MovieItem,
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    val season = state.selectedSeason
    val eps = state.seasons[season]
    if (eps == null) { TvSpinner("Carregando temporada $season"); return }
    if (eps.isEmpty()) { TvHint("Sem episódios nesta temporada."); return }
    val row = rememberLazyListState()
    // Abre a fileira no episódio que a pessoa está vendo.
    LaunchedEffect(season, state.watchingEpisode, eps.size) {
        val i = eps.indexOfFirst { "${it.seasonNumber}:${it.episodeNumber}" == state.watchingEpisode }
        if (i > 0) row.scrollToItem(i)
    }
    Column(Modifier.fillMaxWidth()) {
        TvRail("Episódios", eyebrow = "Temporada $season", state = row) {
            items(eps, key = { "${it.seasonNumber}:${it.episodeNumber}" }) { ep ->
                val k = "${ep.seasonNumber}:${ep.episodeNumber}"
                val watching = state.watchingEpisode == k
                val watched = k in state.watchedEpisodes
                val meta = listOf(brDate(ep.airDate), if (ep.runtimeMin > 0) "${ep.runtimeMin} min" else "").filter { it.isNotBlank() }.joinToString(" · ")
                TvWideCard(
                    image = ep.stillUrl.ifBlank { item.backdropUrl },
                    title = "${ep.episodeNumber}. ${ep.name.ifBlank { "Episódio ${ep.episodeNumber}" }}",
                    subtitle = meta,
                    badges = listOfNotNull(
                        TvBadge("E${ep.episodeNumber}"),
                        when {
                            watching -> TvBadge("VENDO", LiveRed)
                            watched -> TvBadge("VISTO", Accent)
                            else -> null
                        },
                    ),
                    progress = if (watching && state.resumeDurationMs > 0) state.resumePositionMs.toFloat() / state.resumeDurationMs else null,
                    onClick = {
                        val total = state.seasons[ep.seasonNumber]?.size ?: 0
                        if (watching && state.resumeSourceUrl.isNotBlank() && state.resumePositionMs > 10_000) {
                            onPlayDirect(state.resumeSourceUrl, state.resumePositionMs, state.resumeLastEpisode,
                                listOf(MoviePlayerSource("Fonte salva", state.resumeSourceUrl)), ep.seasonNumber, ep.episodeNumber, total)
                        } else onPlayEpisode(item.tmdbId, ep.seasonNumber, ep.episodeNumber, total)
                    },
                    onLongClick = { onSelect(ep.seasonNumber, ep.episodeNumber) },
                )
            }
        }
        Text(
            "OK assiste  ·  Segure OK para escolher a fonte", color = TextTertiary, fontSize = 0.95.rs,
            modifier = Modifier.padding(horizontal = tvPad).offset(y = (-0.4).r),
        )
    }
}

@Composable
private fun TvHint(text: String) {
    Text(
        text, color = TextTertiary, fontSize = 1.05.rs, lineHeight = 1.6.rs,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = tvPad, vertical = 0.6.r)
            .border(1.dp, Border, RoundedCornerShape(1.r)).background(FillTint.copy(alpha = 0.03f), RoundedCornerShape(1.r)).padding(1.5.r),
    )
}

/**
 * Painel lateral do webOS (.side-panel) numa janela própria, que prende o foco do controle até
 * fechar. Voltar fecha.
 */
@Composable
fun TvSourcePanel(title: String, sub: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .15f), Color.Black.copy(alpha = .7f)))),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier.fillMaxHeight().width(42.r).background(PanelBackground)
                    .border(1.dp, Border, RoundedCornerShape(0))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 2.r, end = 2.r, top = 2.3.r, bottom = 3.r),
                verticalArrangement = Arrangement.spacedBy(1.r),
            ) {
                Text(title, color = TextPrimary, fontSize = 1.6.rs, fontWeight = FontWeight.ExtraBold)
                if (sub.isNotBlank()) Text(sub, color = TextTertiary, fontSize = 1.rs)
                content()
            }
        }
    }
}

/** Filmografia do ator por cima do detalhe (.person-overlay). */
@Composable
private fun TvPersonOverlay(name: String, items: List<MovieItem>, loading: Boolean, onDismiss: () -> Unit, onPick: (MovieItem) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        TvScrollArea(inset = 2.r) {
            LazyVerticalGrid(
                GridCells.Fixed(8), Modifier.fillMaxSize().background(Background.copy(alpha = .985f)),
                contentPadding = PaddingValues(start = tvPad, end = tvPad, bottom = 4.r),
                horizontalArrangement = Arrangement.spacedBy(1.3.r), verticalArrangement = Arrangement.spacedBy(2.r),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 2.6.r, bottom = 1.r)) {
                        TvEyebrow("Filmografia")
                        Text(name, color = TextPrimary, fontSize = 2.6.rs, fontWeight = FontWeight.ExtraBold)
                    }
                }
                when {
                    loading -> item(span = { GridItemSpan(maxLineSpan) }) { TvSpinner() }
                    items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { TvHint("Nada encontrado") }
                    else -> items(items.size, key = { items[it].tmdbId }) { i ->
                        val m = items[i]
                        TvPoster(
                            m, { onPick(m) }, width = Dp.Unspecified,
                            typeBadge = if (m.type == MediaType.SERIES) "Série" else "Filme",
                        )
                    }
                }
            }
        }
    }
}
