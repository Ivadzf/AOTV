package com.aotv.app.ui.movies

import androidx.compose.ui.focus.focusProperties
import kotlin.math.roundToInt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.domain.model.EpisodeItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.GlassCircleButton
import com.aotv.app.ui.common.AotvPrimaryButton
import com.aotv.app.ui.common.pressScale
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Data ISO (yyyy-MM-dd) → dd/MM/yyyy (minimalista). "" se vazio/invalido. */
private fun fmtIsoDateMobile(iso: String): String {
    if (iso.isBlank()) return ""
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun MovieDetailScreen(
    item: MovieItem,
    // (tmdbId, season, episode, episodeTotal). O player busca as fontes do episódio nos addons.
    onPlayEpisode: (Int, Int, Int, Int) -> Unit,
    onBack: () -> Unit,
    onRelatedClick: (MovieItem) -> Unit = {},
    // (url, startPos, lastEp, sources, season, episode, episodeTotal)
    // season/episode/episodeTotal != 0 habilitam os botões anterior/próximo episódio no player.
    onPlayDirect: (String, Long, String, List<MoviePlayerSource>, Int, Int, Int) -> Unit = { _, _, _, _, _, _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    // Passa o item completo (título/poster/ano/tipo) p/ a ViewModel poder salvar na
    // Minha Lista e registrar reprodução; também abre os coletores reativos da BIBLIOTECA.
    LaunchedEffect(item.tmdbId) { viewModel.load(item) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    val wide = com.aotv.app.ui.common.isWideLayout()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val fontScale = configuration.fontScale.coerceAtLeast(1f)
    // Trailer: 1º tenta extrair p/ o ExoPlayer (toca até com embed bloqueado); senão IFrame.
    var trailerOpen by remember { mutableStateOf(false) }  // IFrame (fallback)
    var trailerExo by remember { mutableStateOf<com.aotv.app.data.remote.YoutubeTrailer.TrailerStream?>(null) }
    var trailerLoading by remember { mutableStateOf(false) }
    val detailScope = rememberCoroutineScope()
    // Foco inicial (TV): botão Assistir/Continuar já vem selecionado no D-pad.
    val primaryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val detailList = androidx.compose.foundation.lazy.rememberLazyListState()
    var primaryGotFocus by remember { mutableStateOf(false) }

    // Item que chegou só com o id IMDb e teve o TMDB resolvido ao abrir: o resto da tela
    // (temporadas, player) usa o id resolvido.
    val detailItem = if (state.resolvedTmdbId > 0) item.copy(tmdbId = state.resolvedTmdbId) else item
    val primary = rememberDetailPrimary(detailItem, state, viewModel, onPlayEpisode, onPlayDirect)
    val playerSources = primary.playerSources
    // Clique numa fonte: toca no ExoPlayer.
    fun playStremio(s: com.aotv.app.data.remote.StremioStream) {
        viewModel.recordPlayback(); onPlayDirect(s.url, 0, "", playerSources, 0, 0, 0)
    }

    val title       = state.item?.title ?: state.series?.title ?: item.title
    val overview    = state.item?.overview ?: state.series?.overview ?: item.overview
    val backdropUrl = state.item?.backdropUrl ?: state.series?.backdropUrl ?: item.backdropUrl
    val year        = state.item?.year ?: state.series?.firstAirYear ?: item.year
    val rating      = state.item?.rating ?: state.series?.rating ?: item.rating
    val genres      = state.item?.genres ?: state.series?.genres ?: item.genres
    val certification = state.item?.certification ?: state.series?.certification ?: ""
    val isSeries    = item.type == MediaType.SERIES
    val matchPct    = (rating * 10).toInt()

    // Seasons info
    val numberOfSeasons = primary.numberOfSeasons
    val durationLabel = if (isSeries) "$numberOfSeasons temp." else ""
    // Data de lançamento (completa) + próximo episódio (séries semanais/em produção).
    val releaseDateStr = fmtIsoDateMobile(if (isSeries) state.series?.firstAirDate.orEmpty() else state.item?.releaseDate.orEmpty())
    val nextEpDateStr = fmtIsoDateMobile(state.series?.nextEpisodeDate.orEmpty())
    // Série terminada (último ep ≥90%) → sugere "Próximo seriado" (1ª série relacionada).
    val nextSeries = primary.nextSeries

    // Ken Burns scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "kenburns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.08f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenburns_scale",
    )

    // Na TV o botão aceita foco mesmo travado ("Carregando fontes…"), então basta a tela carregar.
    LaunchedEffect(state.isLoading, primary.enabled) {
        if (state.isLoading) return@LaunchedEffect
        repeat(12) {
            if (primaryGotFocus) return@LaunchedEffect
            runCatching { primaryFocus.requestFocus() }
            kotlinx.coroutines.delay(150)
        }
    }

    // Lambda de download p/ SourceRow: só retorna ação se a URL for .mp4 baixável.
    fun dl(url: String): (() -> Unit)? =
        if (viewModel.isDownloadable(url)) ({ viewModel.downloadSource(url) }) else null

    Box(
        Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = detailList,
            // Inset inferior = barra de navegação do sistema, p/ a última seção (Fontes /
            // episódios) não ficar escondida atrás da navigation bar no fim da tela.
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            // ── Backdrop hero — Ken-Burns ─────────────────────────────────────
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(((if (wide) (configuration.screenHeightDp * .95f).coerceIn(280f, 400f) else 420f) + (fontScale - 1f) * 180f).dp)
                        // clipToBounds: a imagem com .scale() (Ken Burns) não vaza p/ baixo
                        // do hero (era a "faixa" esverdeada atrás da sinopse).
                        .clipToBounds(),
                ) {
                    // Ken Burns backdrop image
                    if (backdropUrl.isNotBlank()) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(scale),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1A2840), Color(0xFF0B1422)),
                                    ),
                                ),
                        )
                    }

                    // Radial highlight (topo-esquerda) — assinatura visual TV
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                                    center = Offset(180f, 120f),
                                    radius = 620f,
                                ),
                            ),
                    )

                    // Horizontal scrim p/ contraste do texto
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Background.copy(.62f),
                                        0.55f to Color.Transparent,
                                    ),
                                ),
                            ),
                    )

                    // Top scrim p/ legibilidade dos botões
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Background.copy(.55f), Color.Transparent),
                                ),
                            ),
                    )

                    // Bottom fade vertical to Background
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.48f to Background.copy(.45f),
                                        0.99f to Background,
                                    ),
                                ),
                            ),
                    )

                    // Top bar: voltar + lista (glass circles) — abaixo da barra de status
                    // Na TV o Voltar do controle e o botão salvar da fileira de ações bastam.
                    // A barra sai do caminho do foco e o controle começa no Assistir.
                    val tvMode = com.aotv.app.ui.common.LocalIsTv.current
                    Row(
                        Modifier
                            .then(if (tvMode) Modifier.focusProperties { canFocus = false } else Modifier)
                            .fillMaxWidth()
                            .topInsetsPadding()
                            .padding(horizontal = pad, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassCircleButton(
                            icon = Icons.Default.ArrowBack,
                            onClick = onBack,
                            iconTint = TextPrimary,
                            contentDescription = "Voltar",
                        )
                        GlassCircleButton(
                            icon = if (state.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            onClick = { viewModel.toggleSave() },
                            iconTint = if (state.isSaved) Accent else TextPrimary,
                            contentDescription = if (state.isSaved) "Salvo" else "Minha lista",
                        )
                    }

                    // Bottom content: eyebrow + título + meta
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .then(if (wide) Modifier.fillMaxWidth(0.62f) else Modifier)
                            .padding(horizontal = pad, vertical = 16.dp),
                    ) {
                        com.aotv.app.ui.common.Eyebrow(if (isSeries) "Série" else "Filme", Modifier.padding(bottom = 8.dp))
                        // LOGO do título (TMDB PT-BR → metahub) no lugar do texto; texto = fallback.
                        if (state.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = state.logoUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.BottomStart,
                                modifier = Modifier
                                    .widthIn(max = 360.dp)
                                    .heightIn(min = 60.dp, max = 110.dp),
                            )
                        } else {
                            Text(
                                title,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 34.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 37.sp,
                                letterSpacing = (-0.5).sp,
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        // Meta row: classificação · ★rating · match% · year · duration
                        FlowRow(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (certification.isNotBlank()) {
                                val isAdult = certification.filter { it.isDigit() }.toIntOrNull()?.let { it >= 18 } == true ||
                                    certification.uppercase() in setOf("NC-17", "R", "18", "A")
                                val certColor = if (isAdult) LiveRed else GreenMatch
                                Text(
                                    if (certification.equals("L", true) || certification.isBlank()) "L" else certification,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = certColor,
                                    fontFamily = ManropeFontFamily,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, certColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                                MetaDot(Modifier.align(Alignment.CenterVertically))
                            }
                            if (rating > 0) {
                                Text(
                                    "★ %.1f".format(rating),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Gold,
                                    fontFamily = ManropeFontFamily,
                                )
                                MetaDot(Modifier.align(Alignment.CenterVertically))
                            }
                            if (matchPct > 0) {
                                Text(
                                    "${matchPct}% relevante",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = GreenMatch,
                                    fontFamily = ManropeFontFamily,
                                )
                                MetaDot(Modifier.align(Alignment.CenterVertically))
                            }
                            if (year.isNotBlank()) {
                                Text(
                                    year,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextSecondary,
                                    fontFamily = ManropeFontFamily,
                                )
                            }
                            if (durationLabel.isNotBlank()) {
                                MetaDot(Modifier.align(Alignment.CenterVertically))
                                Text(
                                    durationLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextSecondary,
                                    fontFamily = ManropeFontFamily,
                                )
                            }
                        }
                        // Data de lançamento (minimalista) + próximo episódio (séries semanais).
                        if (releaseDateStr.isNotBlank() || nextEpDateStr.isNotBlank()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                if (releaseDateStr.isNotBlank()) Text("Lançamento · $releaseDateStr", fontSize = 13.sp, color = TextTertiary, fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold)
                                if (nextEpDateStr.isNotBlank()) Text("Novo episódio · $nextEpDateStr", fontSize = 13.sp, color = Accent, fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Content below backdrop ──────────────────────────────────────
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = pad)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // Sinopse curta ACIMA do botão Assistir (séries; filmes também têm SINOPSE abaixo).
                    if (isSeries && overview.isNotBlank()) {
                        Text(
                            overview,
                            fontSize = 15.sp,
                            color = TextSecondary,
                            lineHeight = 23.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    // Primary action row: Assistir (CTA branco) + glass actions
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AotvPrimaryButton(
                            text = primary.label,
                            onClick = primary.play,
                            icon = if (primary.enabled) Icons.Default.PlayArrow else null,
                            enabled = primary.enabled,
                            modifier = Modifier.fillMaxWidth(if (fontScale > 1.3f || configuration.screenWidthDp < 420) 1f else .62f).focusRequester(primaryFocus)
                                .onFocusChanged { if (it.isFocused) primaryGotFocus = true },
                        )
                        // Trailer (YouTube) — extrai e toca no NOSSO ExoPlayer; WebView = fallback.
                        if (state.trailerKey.isNotBlank()) {
                            GlassCircleButton(
                                icon = Icons.Default.Theaters,
                                onClick = {
                                    if (!trailerLoading) detailScope.launch {
                                        trailerLoading = true
                                        val s = viewModel.trailerStream()
                                        trailerLoading = false
                                        if (s != null) trailerExo = s else trailerOpen = true
                                    }
                                },
                                size = 52.dp,
                                iconTint = TextPrimary,
                                contentDescription = if (trailerLoading) "Carregando trailer" else "Trailer",
                                busy = trailerLoading,
                            )
                        }
                        // Botão "Transmitir" removido daqui: era sem ação (o Cast real fica no player).
                        // (B) Botão Minha Lista — reflete estado salvo (preenchido + Accent) /
                        // não salvo (contorno). Liga ao toggleSave da ViewModel.
                        SaveListButton(
                            saved = state.isSaved,
                            onClick = { viewModel.toggleSave() },
                        )
                    }

                    // Série terminada → "Próximo seriado" (abre a 1ª série relacionada).
                    if (nextSeries != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(BackgroundCard2)
                                .clickable { onRelatedClick(nextSeries) }
                                .padding(horizontal = pad, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.SkipNext, null, tint = Accent, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Próximo seriado", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = SoraFontFamily)
                                Text(nextSeries.title, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Genre chips (pill, padrão TV)
                    if (genres.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            genres.forEach { genre ->
                                Text(
                                    genre,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Surface2)
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }

                    // Título da seção de episódios (séries) — sem aba "Sobre" (sinopse já está acima)
                    if (isSeries) {
                        com.aotv.app.ui.common.Eyebrow("Temporadas", Modifier.padding(bottom = 3.dp))
                        Text(
                            "Episódios",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary,
                        )
                    }
                }
            }

            // ── Seção "Sobre" (sinopse + fontes) — SÓ filme (série tem sinopse acima) ──
            if (!isSeries) {
                // Synopsis
                item {
                    if (overview.isNotBlank()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = pad)
                                .padding(top = 22.dp),
                        ) {
                            com.aotv.app.ui.common.Eyebrow("Sinopse", Modifier.padding(bottom = 8.dp))
                            Text(
                                overview,
                                fontSize = 15.sp,
                                color = TextSecondary,
                                lineHeight = 24.sp,
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }
                }

                // Fontes do FILME (séries têm fontes por episódio na aba Episódios)
                if (!isSeries) {
                // Fontes disponíveis — section header
                item {
                    val sourceCount = state.stremioStreams.size

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = pad)
                            .padding(top = 24.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            com.aotv.app.ui.common.Eyebrow("Disponível em", Modifier.padding(bottom = 3.dp))
                            Text("Fontes", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                        }
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                "$sourceCount encontrada${if (sourceCount != 1) "s" else ""}",
                                fontSize = 13.sp,
                                color = TextTertiary,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ManropeFontFamily,
                            )
                        }
                    }
                }

                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = pad),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SourceList(
                            streams = state.stremioStreams,
                            facts = state.movieFacts(),
                            debridTag = state.debridTag,
                            mediaTitle = state.item?.title.orEmpty(),
                            onPlay = { playStremio(it) },
                            onDownload = { dl(it.url) },
                        )
                        if (state.stremioLoading) {
                            Text(
                                "Buscando em addons Stremio…",
                                color = TextTertiary, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // Spacer before bottom
                item { Spacer(Modifier.height(28.dp)) }
                }
            }

            // ── Episódios tab (series only) ─────────────────────────────────
            if (isSeries) {
                // Season chips (FilterChipPill pattern)
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = pad)
                            .padding(top = 18.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (1..maxOf(1, numberOfSeasons)).forEach { season ->
                            val selected = state.selectedSeason == season
                            SeasonChip(
                                label = "Temporada $season",
                                selected = selected,
                                onClick = { viewModel.loadSeason(detailItem.tmdbId, season) },
                            )
                        }
                    }
                }

                // Episode rows
                val episodes = state.seasons[state.selectedSeason] ?: emptyList()
                items(episodes, key = { it.episodeNumber }) { ep ->
                    EpisodeRowCard(
                        episode = ep,
                        // (D) visto (≥90%) vs vendo (em andamento)
                        watched = state.watchedEpisodes.contains("${ep.seasonNumber}:${ep.episodeNumber}"),
                        watching = state.watchingEpisode == "${ep.seasonNumber}:${ep.episodeNumber}",
                        modifier = Modifier.padding(horizontal = pad, vertical = 5.dp),
                        onPlay = {
                            onPlayEpisode(detailItem.tmdbId, ep.seasonNumber, ep.episodeNumber, state.seasons[ep.seasonNumber]?.size ?: 0)
                        },
                        // (A) tocar o card abre o painel de fontes/servidores do episódio
                        onSelect = { viewModel.selectEpisode(ep.seasonNumber, ep.episodeNumber) },
                    )
                }

                item { Spacer(Modifier.height(28.dp)) }
            }

            // ── Elenco (TMDB credits) ──────────────────────────────────────────
            if (state.cast.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SectionHeader("Elenco", Modifier.padding(top = 14.dp), eyebrow = "Quem está no título")
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.cast, key = { it.id }) { c ->
                            CastCard(c, onClick = { viewModel.openPerson(c.id, c.name) })
                        }
                    }
                }
            }

            // ── Coleção/franquia em ordem cronológica ──────────────────────────
            if (state.collectionItems.size > 1) {
                item {
                    Column {
                        com.aotv.app.ui.common.Eyebrow("Coleção", Modifier.padding(start = pad, top = 22.dp, bottom = 3.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 10.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                state.collectionName,
                                color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                "${state.collectionItems.size} filmes",
                                color = TextTertiary, fontSize = 12.sp, fontFamily = ManropeFontFamily,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = pad),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(state.collectionItems, key = { _, m -> m.tmdbId }) { idx, movie ->
                                CollectionPoster(
                                    order = idx + 1,
                                    movie = movie,
                                    isCurrent = movie.tmdbId == state.item?.tmdbId,
                                    onClick = { onRelatedClick(movie) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Relacionados (TMDB recommendations) ────────────────────────────
            if (state.related.isNotEmpty()) {
                item {
                    com.aotv.app.ui.common.SectionHeader("Relacionados", Modifier.padding(top = 18.dp), eyebrow = "Você também pode gostar")
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.related, key = { it.tmdbId }) { rel ->
                            com.aotv.app.ui.common.PosterCard(item = rel, onClick = { onRelatedClick(rel) }, modifier = Modifier.width(com.aotv.app.ui.common.posterWidth()))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }

        // ── (A) Painel de FONTES/SERVIDORES do episódio selecionado ────────────
        // Reaproveita o visual do bloco "Fontes" do filme (SourceRow), como bottom sheet.
        state.selectedEpisode?.let { (season, episode) ->
            EpisodeServersSheet(
                season = season,
                episode = episode,
                stremioStreams = state.episodeStremioStreams,
                facts = state.episodeFacts(),
                debridTag = state.debridTag,
                stremioLoading = state.episodeStremioLoading,
                pluginDone = state.episodePluginDone,
                pluginTotal = state.episodePluginTotal,
                onDismiss = { viewModel.clearSelectedEpisode() },
                onPlayStremio = { s ->
                    // Não marca "visto" ao clicar — só ao passar de 90% no player.
                    viewModel.clearSelectedEpisode()
                    onPlayDirect(
                        s.url, 0, "T$season:E$episode",
                        state.episodeStremioStreams.map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) },
                        season, episode, state.seasons[season]?.size ?: 0,
                    )
                },
                isDownloadable = { viewModel.isDownloadable(it) },
                onDownloadStremio = { s -> viewModel.downloadSource(s.url, "T$season:E$episode") },
            )
        }

        // ── Trailer no NOSSO ExoPlayer (stream extraído — toca até com embed bloqueado) ──
        trailerExo?.let { s ->
            com.aotv.app.ui.common.TrailerExoOverlay(s.videoUrl, s.audioUrl, onClose = { trailerExo = null }, title = title)
        }
        // Fallback: IFrame oficial do YouTube (se a extração falhar).
        if (trailerOpen && state.trailerKey.isNotBlank()) {
            com.aotv.app.ui.common.TrailerYoutubeOverlay(state.trailerKeys.ifEmpty { listOf(state.trailerKey) }) { trailerOpen = false }
        }

        // ── Filmografia do ATOR (clicou no elenco) ──────────────────────────────
        state.personName?.let { person ->
            PersonFilmographySheet(
                name = person,
                items = state.personCredits,
                loading = state.personLoading,
                onDismiss = { viewModel.closePerson() },
                onItemClick = { rel -> viewModel.closePerson(); onRelatedClick(rel) },
            )
        }

    }
}

// ── Filmografia do ator (grade de títulos em que atuou) ───────────────────────
@Composable
private fun PersonFilmographySheet(
    name: String,
    items: List<MovieItem>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onItemClick: (MovieItem) -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Background)
                .border(1.dp, Border, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("FILMOGRAFIA", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily, letterSpacing = 1.2.sp)
                    Text(name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircleButton(icon = Icons.Default.Close, onClick = onDismiss, size = 38.dp, iconTint = TextSecondary, contentDescription = "Fechar")
            }
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nada encontrado", color = TextTertiary, fontSize = 14.sp)
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(com.aotv.app.ui.common.posterWidth()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.tmdbId }) { m ->
                        com.aotv.app.ui.common.PosterCard(item = m, onClick = { onItemClick(m) }, typeBadge = true)
                    }
                }
            }
        }
    }
}

// ── (A) Bottom sheet de fontes/servidores de um episódio ──────────────────────
@Composable
private fun EpisodeServersSheet(
    season: Int,
    episode: Int,
    stremioStreams: List<com.aotv.app.data.remote.StremioStream>,
    facts: List<com.aotv.app.data.remote.SourceFacts>,
    debridTag: String = "",
    stremioLoading: Boolean,
    // v1.0.7 — progresso dos plugins do Nuvio (quantos já responderam de
    // quantos); o rodapé mostra "Buscando em plugins… X/55" enquanto houver
    // pendente, para não parecer travado.
    pluginDone: Int = 0,
    pluginTotal: Int = 0,
    onDismiss: () -> Unit,
    onPlayStremio: (com.aotv.app.data.remote.StremioStream) -> Unit,
    isDownloadable: (String) -> Boolean = { false },
    onDownloadStremio: (com.aotv.app.data.remote.StremioStream) -> Unit = {},
) {
    // Scrim cobre a tela inteira; toque fora fecha.
    val scrimSrc = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = scrimSrc, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Card inferior — não fecha ao tocar dentro. Altura limitada a 90% da tela e
        // lista rolável: com muitas fontes o card crescia além da tela e o resto ficava
        // inalcançável (sem rolagem, nem o "Mostrar mais" aparecia).
        val cardSrc = remember { MutableInteractionSource() }
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(BackgroundCard)
                .border(1.dp, Border, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(interactionSource = cardSrc, indication = null, onClick = {})
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 18.dp)
                .navigationBarsPadding(),
        ) {
            // Grabber
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSubtle),
            )
            Spacer(Modifier.height(14.dp))
            // Header: eyebrow + título + fechar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        "DISPONÍVEL EM",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ManropeFontFamily,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                    Text(
                        "Episódio $episode · Fontes",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp,
                    )
                }
                GlassCircleButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    size = 38.dp,
                    iconTint = TextSecondary,
                    contentDescription = "Fechar",
                )
            }
            Spacer(Modifier.height(16.dp))

            // Estado da lista (filtros/limite) lembrado aqui, na folha — a função
            // auxiliar LazyListScope.sourceListItems não compilava (o compilador
            // recusou chamar @Composable com receiver LazyListScope de dentro do
            // conteúdo do LazyColumn), então os items ficam inline aqui.
            val sourceData = rememberSourceListData(stremioStreams, facts)
            val cols = if (com.aotv.app.ui.common.isWideLayout()) 2 else 1

            // LazyColumn com keys estáveis: a posição do scroll sobrevive quando
            // fontes chegam e a lista cresce (com Column + verticalScroll, o
            // "Mostrar mais" voltava pro topo e as outras fontes não apareciam).
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Fontes dos addons, tocam no ExoPlayer.
                if (sourceData.groups.isNotEmpty()) item(key = "count") {
                    Text("${sourceData.groups.size} versões em ${sourceData.streamCount} fontes", color = TextTertiary, fontSize = 13.sp)
                }
                item(key = "filters") { SourceListFilters(sourceData) }
                sourceData.sections.forEach { (addon, gs) ->
                    item(key = "section-$addon") { SourceSectionHeader(addon, gs.size) }
                    gs.chunked(cols.coerceAtLeast(1)).forEach { row ->
                        // Key = url do lead do 1º grupo da fileira (estável por grupo).
                        item(key = "row-${row.first().lead.url}") {
                            SourceCardRow(
                                row, sourceData, "", onPlayStremio,
                                { s -> if (isDownloadable(s.url)) ({ onDownloadStremio(s) }) else null },
                                cols, debridTag,
                            )
                        }
                    }
                }
                if (sourceData.filtered.size > sourceData.limit) item(key = "more") {
                    com.aotv.app.ui.common.AotvButton("Mostrar mais (${sourceData.filtered.size - sourceData.limit})", { sourceData.onMore() })
                }
                if (sourceData.filtered.isEmpty() && stremioStreams.isNotEmpty()) item(key = "empty-list") {
                    Text(
                        if (sourceData.wrongCount > 0 && !sourceData.showWrong) "As fontes encontradas parecem ser de outro filme. Use Mostrar todas para conferir."
                        else "Nenhuma versão neste filtro.", color = TextSecondary, fontSize = 13.sp,
                    )
                }
                if (stremioLoading) {
                    item(key = "loading") {
                        Text(
                            "Buscando em addons Stremio…",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                } else if (stremioStreams.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nenhum addon tem este episódio.",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                // v1.0.7 — progresso dos plugins do Nuvio: enquanto houver
                // plugin sem responder, mostra quantos já voltaram, para não
                // parecer que a folha travou (a fila leva minutos).
                if (pluginTotal > 0 && pluginDone < pluginTotal) {
                    item(key = "loading-plugins") {
                        Text(
                            "Buscando em plugins… $pluginDone/$pluginTotal",
                            color = TextTertiary, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Lista de fontes com filtros e o resultado do teste ──────────────────────
private val SRC_FILTERS = listOf<Triple<String, String, (com.aotv.app.data.remote.SourceFacts) -> Boolean>>(
    Triple("all", "Todas") { true },
    Triple("dub", "Dublado") { it.lang == "dub" },
    Triple("leg", "Legendado") { it.lang == "leg" },
    // Qualidade do nome primeiro (igual ao webOS). Filme 1080p em scope tem 800 linhas reais.
    Triple("4k", "4K") { (it.labelRank.takeIf { q -> q > 0 } ?: it.rank) >= 2000 },
    Triple("1080", "1080p") { (it.labelRank.takeIf { q -> q > 0 } ?: it.rank) in 1000..1199 },
    Triple("hdr", "HDR") { com.aotv.app.data.remote.hasHdr(it.label) || com.aotv.app.data.remote.hasDolbyVision(it.label) },
    Triple("instant", "Instantâneo") { it.debrid >= 2 },
)

/**
 * Estado + dados derivados da lista de fontes. Compartilhado entre o SourceList
 * em Column (filme/TV) e a versão em LazyColumn da folha de episódio: o estado
 * (filtros, limite) sobrevive à recriação com rememberSaveable, e a LazyColumn
 * com keys estáveis segura a posição do scroll quando a lista cresce.
 */
private class SourceListData(
    val groups: List<ReleaseGroup>,
    val streamCount: Int,
    val wrongCount: Int,
    val visible: List<ReleaseGroup>,
    val counts: Map<String, Int>,
    /** Fontes presentes (nome, nº de grupos), na ordem das seções. */
    val sources: List<Pair<String, Int>>,
    val filtered: List<ReleaseGroup>,
    val shown: List<ReleaseGroup>,
    val sections: List<Map.Entry<String, List<ReleaseGroup>>>,
    val best: String?,
    val filter: String,
    val onFilterChange: (String) -> Unit,
    val showWrong: Boolean,
    val onShowWrongChange: (Boolean) -> Unit,
    val sourceFilter: String,
    val onSourceFilterChange: (String) -> Unit,
    val limit: Int,
    val onMore: () -> Unit,
    val fact: (com.aotv.app.data.remote.StremioStream) -> com.aotv.app.data.remote.SourceFacts,
    val playable: (ReleaseGroup) -> com.aotv.app.data.remote.StremioStream,
    val addonOf: (ReleaseGroup) -> String,
)

@Composable
private fun rememberSourceListData(
    streams: List<com.aotv.app.data.remote.StremioStream>,
    facts: List<com.aotv.app.data.remote.SourceFacts>,
): SourceListData {
    var filter by rememberSaveable { mutableStateOf("all") }
    var showWrong by rememberSaveable { mutableStateOf(false) }
    // Filtro por fonte (""): "Todas" ou o nome do addon/provider.
    var sourceFilter by rememberSaveable { mutableStateOf("") }
    // O limite reseta quando qualquer filtro muda (comportamento antigo).
    var limit by rememberSaveable(filter, showWrong, sourceFilter) { mutableStateOf(24) }
    val groups = remember(streams) { groupReleases(streams) }
    val byUrl = facts.associateBy { it.url }
    fun fact(s: com.aotv.app.data.remote.StremioStream) = byUrl[s.url]
        ?: com.aotv.app.data.remote.SourceFacts(s.url, s.addon, s.releaseText.ifBlank { s.label }, s.qualityRank)
    val wrongCount = groups.count { g -> g.members.all { fact(it).wrongTitle } }
    val visible = groups.filter { g -> showWrong || g.members.any { !fact(it).wrongTitle } }
    val counts = SRC_FILTERS.associate { (k, _, fn) -> k to visible.count { g -> g.members.any { fn(fact(it)) } } }
    val predicate = SRC_FILTERS.firstOrNull { it.first == filter }?.third ?: { true }
    val langFiltered = visible.filter { g -> g.members.any { predicate(fact(it)) } }
    // Fonte que vai tocar em cada versão (a viva, fora as de outro filme). O grupo pode ter o
    // mesmo arquivo em mais de um addon — a seção é o addon dessa fonte.
    fun playable(group: ReleaseGroup) =
        group.members.firstOrNull { !fact(it).dead && !fact(it).wrongTitle } ?: group.lead
    fun addonOf(group: ReleaseGroup) =
        fact(playable(group)).addon.ifBlank { playable(group).addon }.ifBlank { "Outras fontes" }
    // Fontes presentes (dentro do filtro de idioma), ordenadas pelo melhor stream.
    val sources = langFiltered.groupBy(::addonOf).entries
        .sortedByDescending { (_, gs) -> gs.maxOf { fact(playable(it)).rank } }
        .map { it.key to it.value.size }
    val filtered = if (sourceFilter.isEmpty()) langFiltered
        else langFiltered.filter { addonOf(it) == sourceFilter }
    // Seções por reprodutor (addon), ordenadas pelo melhor stream de cada uma (maior rank).
    // O limite ("Mostrar mais") corta antes de agrupar, então ele continua valendo no total.
    val shown = filtered.take(limit)
    val sections = shown.groupBy(::addonOf).entries.sortedByDescending { (_, gs) ->
        gs.maxOf { fact(playable(it)).rank }
    }
    val best = facts.getOrNull(com.aotv.app.data.remote.bestIndex(facts))?.url
    return SourceListData(
        groups = groups, streamCount = streams.size, wrongCount = wrongCount,
        visible = visible, counts = counts, sources = sources,
        filtered = filtered, shown = shown, sections = sections, best = best,
        filter = filter, onFilterChange = { filter = it },
        showWrong = showWrong, onShowWrongChange = { showWrong = it },
        sourceFilter = sourceFilter, onSourceFilterChange = { sourceFilter = it },
        limit = limit, onMore = { limit += 24 },
        fact = { s -> fact(s) }, playable = { g -> playable(g) }, addonOf = { g -> addonOf(g) },
    )
}

/** Cabeçalho de uma seção por fonte ("VêTela Brasil · 5 versões"). */
@Composable
private fun SourceSectionHeader(addon: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(addon, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily)
        Text("$count ${if (count == 1) "versão" else "versões"}", color = TextTertiary, fontSize = 12.5.sp, fontFamily = ManropeFontFamily)
    }
}

/** Uma fileira de cards (1 ou 2 por linha, conforme cols). */
@Composable
private fun SourceCardRow(
    row: List<ReleaseGroup>,
    data: SourceListData,
    mediaTitle: String,
    onPlay: (com.aotv.app.data.remote.StremioStream) -> Unit,
    onDownload: (com.aotv.app.data.remote.StremioStream) -> (() -> Unit)?,
    cols: Int,
    debridTag: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { group ->
            val source = data.playable(group)
            val f = data.fact(source)
            var info = releaseInfo(source, mediaTitle, group.members.map { it.addon }.distinct())
            if (source.qualityRank == 0 && (f.probe?.height ?: 0) > 0) info = info.copy(quality = com.aotv.app.data.remote.heightLabel(f.probe!!.height))
            val note = when { f.wrongTitle -> "Parece ser outro filme com nome parecido"; f.dead -> "Não respondeu no teste agora"; else -> null }
            val (tag, color) = when {
                f.wrongTitle -> "OUTRO" to LiveRed
                f.debrid >= 2 -> debridTag.ifBlank { "DEBRID" } to Ok
                f.p2p -> "P2P" to TextSecondary
                else -> "" to TextTertiary
            }
            Box(Modifier.weight(1f)) {
                androidx.compose.runtime.key(source.url) {
                    SourceCard(info, tag, color, onClick = { onPlay(source) }, onDownload = onDownload(source), note = note,
                        suggested = group.members.any { it.url == data.best })
                }
            }
        }
        repeat(cols.coerceAtLeast(1) - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Fileiras de filtros: por fonte (novo) e por idioma (existente). */
@Composable
private fun SourceListFilters(data: SourceListData) {
    // Por fonte: um chip por addon/provider presente, com contagem. Só aparece
    // quando há mais de 1 fonte — com uma só, a seção já diz tudo.
    if (data.sources.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SeasonChip("Todas ${data.sources.sumOf { it.second }}", data.sourceFilter.isEmpty()) { data.onSourceFilterChange("") }
        data.sources.forEach { (name, n) ->
            SeasonChip("$name $n", data.sourceFilter == name) { data.onSourceFilterChange(name) }
        }
    }
    if (data.groups.size > 4 || data.wrongCount > 0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.groups.size > 4) SRC_FILTERS.forEach { (key, name, _) ->
            val n = data.counts[key] ?: 0
            if (key == "all" || n > 0) SeasonChip("$name $n", data.filter == key) { data.onFilterChange(key) }
        }
        if (data.wrongCount > 0) SeasonChip(if (data.showWrong) "Ocultar outro filme (${data.wrongCount})" else "Mostrar todas (${data.wrongCount} de outro filme)", data.showWrong) { data.onShowWrongChange(!data.showWrong) }
    }
}

/**
 * Cartões das fontes (filme e episódio). Chips de filtro com contagem só do que está visível,
 * fonte de outro filme escondida atrás do chip "Mostrar todas", resolução real quando o nome
 * não diz e aviso de fonte que não respondeu no teste.
 */
@Composable
internal fun SourceList(
    streams: List<com.aotv.app.data.remote.StremioStream>,
    facts: List<com.aotv.app.data.remote.SourceFacts>,
    mediaTitle: String,
    onPlay: (com.aotv.app.data.remote.StremioStream) -> Unit,
    onDownload: (com.aotv.app.data.remote.StremioStream) -> (() -> Unit)?,
    cols: Int = if (com.aotv.app.ui.common.isWideLayout()) 2 else 1,
    debridTag: String = "",
    television: Boolean = false,
) {
    if (television) {
        com.aotv.app.ui.tv.TvScaled { SourceList(streams, facts, mediaTitle, onPlay, onDownload, cols, debridTag) }
        return
    }
    val data = rememberSourceListData(streams, facts)
    if (data.groups.isNotEmpty()) Text("${data.groups.size} versões em ${data.streamCount} fontes", color = TextTertiary, fontSize = 13.sp)
    SourceListFilters(data)
    data.sections.forEach { (addon, gs) ->
        SourceSectionHeader(addon, gs.size)
        gs.chunked(cols.coerceAtLeast(1)).forEach { row ->
            SourceCardRow(row, data, mediaTitle, onPlay, onDownload, cols, debridTag)
        }
    }
    if (data.filtered.size > data.limit) com.aotv.app.ui.common.AotvButton("Mostrar mais (${data.filtered.size - data.limit})", { data.onMore() })
    if (data.filtered.isEmpty() && streams.isNotEmpty()) Text(
        if (data.wrongCount > 0 && !data.showWrong) "As fontes encontradas parecem ser de outro filme. Use Mostrar todas para conferir."
        else "Nenhuma versão neste filtro.", color = TextSecondary, fontSize = 13.sp,
    )
}

// ── Meta dot separator ──────────────────────────────────────────────────────
@Composable
private fun MetaDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(TextTertiary),
    )
}

// ── Season chip (pill, padrão FilterChipPill) ────────────────────────────────
@Composable
private fun SeasonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    com.aotv.app.ui.common.FilterChipPill(label, selected, onClick)
}

// ── Source row card ─────────────────────────────────────────────────────────
@Composable
private fun SourceRow(
    label: String,
    quality: String,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    // Foco D-pad (TV): borda branca + fundo Accent + elevação para mostrar a fonte selecionada.
    val borderColor = if (focused) Color.White else if (pressed) Accent else Border

    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(src)
            .shadow(if (focused) 16.dp else if (pressed) 10.dp else 0.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) AccentSoft else BackgroundCard)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // Left icon box
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tagColor.copy(.14f))
                .border(1.dp, tagColor.copy(.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = tagColor,
                modifier = Modifier.size(18.dp),
            )
        }
        // Labels
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = TextPrimary,
                fontFamily = ManropeFontFamily,
            )
            Text(
                quality,
                fontSize = 11.5.sp,
                color = TextTertiary,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Tag pill
        Text(
            tag,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tagColor.copy(.18f))
                .border(1.dp, tagColor.copy(.35f), RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            color = tagColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = ManropeFontFamily,
            letterSpacing = 0.5.sp,
        )
        // Botão de download (só fontes .mp4 baixáveis)
        if (onDownload != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Accent.copy(0.18f))
                    .clickable { onDownload() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Download, "Baixar", tint = Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// Mostra qualidade/áudio da fonte Stremio ANTES de tocar (resolução + addon + dub/leg).
private fun stremioQuality(s: com.aotv.app.data.remote.StremioStream): String {
    val res = when {
        s.qualityRank >= 2160 -> "4K"
        s.qualityRank >= 1080 -> "1080p"
        s.qualityRank >= 720 -> "720p"
        s.qualityRank >= 480 -> "480p"
        else -> "HD"
    }
    val audio = when {
        s.label.contains("legenda", true) || Regex("\\bleg\\b", RegexOption.IGNORE_CASE).containsMatchIn(s.label) -> "Legendado"
        s.label.contains("dubl", true) || s.label.contains("portugu", true) || s.label.contains("nacional", true) ||
            Regex("\\bdub\\b", RegexOption.IGNORE_CASE).containsMatchIn(s.label) -> "Dublado"
        else -> ""
    }
    return listOf(res, audio).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { if (com.aotv.app.data.torrent.TorrentMagnet.isMagnet(s.url)) "P2P" else "HTTP" }
}

// ── Cartão de ator (elenco) ───────────────────────────────────────────────────
@Composable
private fun CastCard(member: com.aotv.app.domain.model.CastMember, onClick: () -> Unit = {}) {
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Ver filmes de ${member.name}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (member.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = member.photoUrl,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.Person, null, tint = TextTertiary, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            member.name,
            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (member.character.isNotBlank()) {
            Text(
                member.character,
                color = TextTertiary, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
            )
        }
    }
}

// ── Empty state for unavailable sources ───────────────────────────────────────
@Composable
private fun SourceEmptyState() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundCard2)
                .border(1.dp, Border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Indisponível no momento",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary,
                fontFamily = SoraFontFamily,
            )
            Text(
                "Nenhuma fonte encontrada para este título.",
                fontSize = 11.5.sp,
                color = TextTertiary,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ── Episode row card ────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    watched: Boolean,
    watching: Boolean = false,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
) {
    val rowSrc = remember { MutableInteractionSource() }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val stacked = config.screenHeightDp >= 480 && (config.screenWidthDp < 360 || config.screenWidthDp < 600 && fontScale > 1.3f)
    val watchingColor = Color(0xFFF5A623) // âmbar = "vendo"
    // Visto -> leve dim; vendo -> destaque âmbar sem dim.
    val cardAlpha = if (watched) 0.62f else 1f
    val cardBorder = when {
        watched -> Accent.copy(alpha = 0.35f)
        watching -> watchingColor.copy(alpha = 0.55f)
        else -> Border
    }
    FlowRow(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundCard)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
            // (A) tocar o card abre o painel de fontes/servidores do episódio
            .clickable(interactionSource = rowSrc, indication = androidx.compose.foundation.LocalIndication.current, onClick = onSelect)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Thumbnail 16:9 com overlay + play glass
        Box(
            Modifier
                .then(if (stacked) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.width(120.dp).height(68.dp))
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundCard2)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .alpha(cardAlpha),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.stillUrl.isNotBlank()) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dark overlay p/ legibilidade do play
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
            // Círculo: visto=check(Accent) · vendo=play(âmbar) · não visto=play(glass)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(when {
                        watched -> Accent.copy(alpha = 0.92f)
                        watching -> watchingColor.copy(alpha = 0.92f)
                        else -> GlassBg
                    })
                    .border(1.dp, when {
                        watched -> Accent
                        watching -> watchingColor
                        else -> BorderSubtle
                    }, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (watched) Icons.Default.Check else Icons.Default.PlayArrow,
                    null,
                    tint = if (watched || watching) Color.White else TextPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
            // Episode number badge (canto inferior-esquerda)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "EP ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        // Title + overview
        Column(
            Modifier.then(if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f))
                .align(Alignment.CenterVertically).alpha(cardAlpha),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${episode.episodeNumber}. ${episode.name}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = SoraFontFamily,
                    modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f, fill = false),
                )
                // Badge: VISTO (≥90%) ou VENDO (em andamento)
                if (watched || watching) {
                    val badgeColor = if (watched) Accent else watchingColor
                    Text(
                        if (watched) "VISTO" else "VENDO",
                        color = badgeColor,
                        fontSize = 8.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ManropeFontFamily,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            // Meta do episódio: data de lançamento · duração · nota (minimalista).
            val epMeta = remember(episode.airDate, episode.runtimeMin, episode.rating) {
                buildList {
                    fmtIsoDateMobile(episode.airDate).takeIf { it.isNotBlank() }?.let { add(it) }
                    if (episode.runtimeMin > 0) add("${episode.runtimeMin} min")
                    if (episode.rating > 0) add("★ %.1f".format(episode.rating))
                }
            }
            if (epMeta.isNotEmpty()) {
                Text(
                    epMeta.joinToString("  ·  "),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = if (stacked) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    episode.overview,
                    fontSize = 11.5.sp,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier.padding(top = 3.dp),
                    lineHeight = 15.sp,
                )
            }
        }

        SmallPlayBtn("PLAY", Accent, onPlay, Modifier.align(Alignment.CenterVertically))
    }
}

// ── Small play button (tag pill clicável) ─────────────────────────────────────
@Composable
private fun SmallPlayBtn(label: String, accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.heightIn(min = 48.dp)
            .pressScale(src)
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) accentColor else accentColor.copy(alpha = 0.16f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else accentColor.copy(alpha = 0.40f), RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val fg = if (focused) Color(0xFF06070A) else accentColor
        Icon(
            Icons.Default.PlayArrow,
            null,
            tint = fg,
            modifier = Modifier.size(11.dp),
        )
        Text(
            label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = ManropeFontFamily,
            letterSpacing = 0.4.sp,
        )
    }
}

// ── (B) Botão "Minha Lista" / "Salvo" (glass circular com estado) ─────────────
@Composable
private fun SaveListButton(saved: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(src)
            .size(52.dp)
            .clip(CircleShape)
            .background(if (saved) AccentSoft else Surface2)
            .border(1.dp, if (saved) Accent.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
            // Indicação do tema. Na TV vira o anel de foco do controle.
            .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (saved) "Salvo na Minha Lista" else "Adicionar à Minha Lista",
            tint = if (saved) Accent else TextPrimary,
            modifier = Modifier.size(21.dp),
        )
    }
}
