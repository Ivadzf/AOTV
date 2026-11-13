package com.aotv.app.ui.movies

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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
import com.aotv.app.ui.common.FrostedScrim
import com.aotv.app.ui.common.blurBehind
import com.aotv.app.ui.common.rememberPanelBlurEffect
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
    // v1.4, Fase 4 — tocar um addon Kodi na folha de fontes abre o
    // navegador dele (a navegação passa pela MoviesNavigation).
    // v1.5 — busca automática: leva (addonId, título) — filme: título do
    // filme; série: título da SÉRIE (decisão do Davi). O navegador busca
    // o título no Vulgata; outros addons abrem na raiz.
    onOpenKodiAddon: (String, String) -> Unit = { _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel(),
    // Preferências da página de detalhes (Fase B): fundo, transição, ícones, abas, episódios.
    detailPrefs: DetailPrefsViewModel = hiltViewModel(),
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

    // Ken Burns scale animation — parado com a folha de fontes aberta: a
    // animação infinita recompõe o hero a cada quadro por baixo da folha.
    val sheetOpen = state.selectedEpisode != null
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

    // Preferências da página de detalhes (Fase B). Mudam na hora, sem reabrir.
    val cinematicBg by detailPrefs.cinematic.collectAsStateWithLifecycle()
    val posterTransition by detailPrefs.posterTransition.collectAsStateWithLifecycle()
    val iconActions by detailPrefs.iconActions.collectAsStateWithLifecycle()
    val detailTabsOn by detailPrefs.tabs.collectAsStateWithLifecycle()
    val episodeStyleId by detailPrefs.episodeStyle.collectAsStateWithLifecycle()
    val episodeStyle = EpisodeCardStyle.fromId(episodeStyleId)
    // Notas dos episódios (Lote 2 item 5, por perfil; padrão: mostrar).
    val showEpisodeRatings by detailPrefs.episodeRatings.collectAsStateWithLifecycle()

    // Blur nos painéis (Lote 3 item 4, v1.11.0): com uma folha aberta (fontes
    // ou filmografia), o conteúdo da página atrás dela é desfocado. O efeito
    // é criado 1x; abaixo do Android 12 ele é null e o fallback é o scrim
    // fosco ([FrostedScrim], Lote 4) — o toggle continua valendo lá.
    val panelBlurOn by detailPrefs.panelBlur.collectAsStateWithLifecycle()
    val panelBlurEffect = rememberPanelBlurEffect()
    val blurBehindSheets = panelBlurOn && panelBlurEffect != null &&
        (sheetOpen || state.personName != null)
    // Título em idioma original (Lote 3 item 6, por perfil; padrão: desligado).
    val showOriginalTitle by detailPrefs.originalTitle.collectAsStateWithLifecycle()

    // Navegador de episódios (Lote 2 item 3): busca + ordenação da TEMPORADA
    // selecionada, em memória (sem recarregar a série). O estado vive aqui,
    // na tela — não é pref: trocar de série recomeça a busca do zero.
    var episodeQuery by rememberSaveable { mutableStateOf("") }
    var episodeSortId by rememberSaveable { mutableStateOf(EpisodeSort.NUMBER.id) }

    // Transição do pôster (Fase B): escala 0.94→1 + fade na primeira composição.
    var detailEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { detailEntered = true }
    val introScale by animateFloatAsState(
        targetValue = if (posterTransition && !detailEntered) 0.94f else 1f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "detail_intro_scale",
    )
    val introAlpha by animateFloatAsState(
        targetValue = if (posterTransition && !detailEntered) 0f else 1f,
        animationSpec = tween(350),
        label = "detail_intro_alpha",
    )

    // Layout de abas (Fase B, opt-in): agrupa as seções em abas; desligado, a rolagem única atual.
    val detailTabList = buildList {
        if (isSeries) add("episodios" to "Episódios") else {
            if (overview.isNotBlank()) add("sobre" to "Sobre")
            add("fontes" to "Fontes")
        }
        if (state.cast.isNotEmpty()) add("elenco" to "Elenco")
        if (state.collectionItems.size > 1 || state.related.isNotEmpty()) add("mais" to "Mais como isto")
    }
    var detailTab by rememberSaveable { mutableStateOf("") }
    val activeDetailTab = detailTabList.firstOrNull { it.first == detailTab }?.first
        ?: detailTabList.firstOrNull()?.first.orEmpty()

    // Na TV o botão aceita foco mesmo travado ("Carregando fontes…"), então basta a tela carregar.
    LaunchedEffect(state.isLoading, primary.enabled) {
        if (state.isLoading) return@LaunchedEffect
        repeat(12) {
            if (primaryGotFocus) return@LaunchedEffect
            runCatching { primaryFocus.requestFocus() }
            kotlinx.coroutines.delay(150)
        }
    }

    // Abrir o trailer (extrai p/ o ExoPlayer; WebView = fallback). Usado pelo
    // botão em vidro (layout atual) e pelo ícone (ações em ícones, Fase B).
    fun openTrailer() {
        if (!trailerLoading) detailScope.launch {
            trailerLoading = true
            val s = viewModel.trailerStream()
            trailerLoading = false
            if (s != null) trailerExo = s else trailerOpen = true
        }
    }

    // Lambda de download p/ SourceRow: só retorna ação se a URL for .mp4 baixável.
    fun dl(url: String): (() -> Unit)? =
        if (viewModel.isDownloadable(url)) ({ viewModel.downloadSource(url) }) else null

    Box(
        Modifier
            .fillMaxSize()
            .background(if (cinematicBg) Color.Black else Background),
    ) {
        // O blur é aplicado a ESTE Box (fundo + lista): a folha, irmã dele no
        // Box externo, fica nítida por cima. Com o blur desligado (ou sem
        // folha aberta, ou abaixo do Android 12) o Modifier não muda nada.
        Box(
            Modifier
                .fillMaxSize()
                .blurBehind(blurBehindSheets, panelBlurEffect),
        ) {
        // Fundo cinematográfico (Fase B, opt-in): arte desfocada em tela cheia
        // atrás da página. O LazyColumn não tem fundo próprio, então a arte aparece.
        if (cinematicBg && backdropUrl.isNotBlank()) {
            CinematicBackdrop(backdropUrl, sheetOpen = sheetOpen)
        }
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
                        .clipToBounds()
                        // Transição do pôster (Fase B): cresce e aparece ao abrir.
                        .graphicsLayer {
                            scaleX = introScale
                            scaleY = introScale
                            alpha = introAlpha
                        },
                ) {
                    // Ken Burns backdrop image. No modo cinematográfico a arte já está
                    // desfocada atrás da página inteira, então aqui não repete.
                    if (!cinematicBg && backdropUrl.isNotBlank()) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                // Folha aberta: escala fixa — o valor animado não é
                                // lido, então a recomposição por quadro para.
                                .scale(if (sheetOpen) 1.08f else scale),
                        )
                    } else if (backdropUrl.isBlank()) {
                        // Sem arte: o gradiente azul escuro de antes. Com arte no modo
                        // cinematográfico não entra nada aqui (a arte já cobre a tela).
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

                    // Bottom fade vertical to Background. Fora no modo cinematográfico:
                    // o conteúdo abaixo precisa continuar vendo a arte desfocada.
                    if (!cinematicBg) Box(
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
                        // Título em idioma original (Lote 3 item 6): linha secundária
                        // abaixo do título traduzido (ou do logo, quando há). Só quando a
                        // pref está ligada, o TMDB trouxe o original E ele é diferente do
                        // traduzido. Nos cards NÃO aparece — não cabe sem ruído; só no detalhe.
                        val originalTitle = state.originalTitle.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
                        if (showOriginalTitle && originalTitle != null) {
                            Text(
                                originalTitle,
                                fontSize = 15.sp,
                                color = TextTertiary,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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
                        // Ações em vidro (layout atual). Com ações em ícones (Fase B) elas
                        // descem p/ uma fileira de ícones com rótulo, abaixo do Reproduzir.
                        if (!iconActions) {
                            // Trailer (YouTube) — extrai e toca no NOSSO ExoPlayer; WebView = fallback.
                            if (state.trailerKey.isNotBlank()) {
                                GlassCircleButton(
                                    icon = Icons.Default.Theaters,
                                    onClick = { openTrailer() },
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
                    }

                    // Ações em ícones (Fase B): as mesmas ações de cima, em ícones com
                    // rótulo pequeno — nada novo, nada removido.
                    if (iconActions) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            if (state.trailerKey.isNotBlank()) {
                                DetailActionIcon(
                                    icon = Icons.Default.Theaters,
                                    label = "Trailer",
                                    busy = trailerLoading,
                                    onClick = { openTrailer() },
                                )
                            }
                            DetailActionIcon(
                                icon = if (state.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label = if (state.isSaved) "Salvo" else "Minha Lista",
                                tint = if (state.isSaved) Accent else TextPrimary,
                                onClick = { viewModel.toggleSave() },
                            )
                        }
                    }

                    // Erro de carregamento (TMDB falhou / load travou): nunca tela preta
                    // para sempre — mostra o erro e o retry.
                    val loadError = state.error
                    if (loadError != null && !state.isLoading) {
                        Spacer(Modifier.height(14.dp))
                        ErrorRetryCard(message = loadError, onRetry = { viewModel.retry() })
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

                    // Título da seção de episódios (séries) — sem aba "Sobre" (sinopse já está acima).
                    // Com abas (Fase B) o título some: a aba "Episódios" já diz tudo.
                    if (isSeries && !detailTabsOn) {
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

            // ── Abas do detalhe (Fase B, opt-in) ─────────────────────────────
            // Fixa no topo ao rolar; com uma aba só, nem aparece.
            if (detailTabsOn && detailTabList.size > 1) {
                stickyHeader(key = "detail-tabs") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (cinematicBg) Background.copy(alpha = 0.85f) else Background)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = pad, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detailTabList.forEach { (id, label) ->
                            com.aotv.app.ui.common.FilterChipPill(label, activeDetailTab == id, onClick = { detailTab = id })
                        }
                    }
                }
            }

            // ── Seção "Sobre" (sinopse + fontes) — SÓ filme (série tem sinopse acima) ──
            if (!isSeries && (!detailTabsOn || activeDetailTab == "sobre")) {
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
                            // Orçamento e receita (Lote 3 item 7): só filmes, só quando o
                            // TMDB trouxe valor > 0. Séries não têm esses campos no TMDB —
                            // budget/revenue ficam 0 e nada aparece. Sem dado, não mostra nada.
                            if (state.budget > 0 || state.revenue > 0) {
                                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (state.budget > 0) Text(
                                        "Orçamento · ${formatMoneyUsdBr(state.budget)}",
                                        fontSize = 14.sp, color = TextSecondary, fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (state.revenue > 0) Text(
                                        "Receita · ${formatMoneyUsdBr(state.revenue)}",
                                        fontSize = 14.sp, color = TextSecondary, fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fontes do FILME (séries têm fontes por episódio na aba Episódios)
            if (!isSeries && (!detailTabsOn || activeDetailTab == "fontes")) {
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
                            kodiAddons = state.kodiVideoAddons,
                            onOpenKodiAddon = { addonId -> onOpenKodiAddon(addonId, title) },
                            // v1.8.1 — botão Recarregar da lista: re-busca as fontes do filme.
                            onReloadSources = { viewModel.reloadMovieSources() },
                            loading = state.stremioLoading,
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

            // ── Episódios tab (series only) ─────────────────────────────────
            if (isSeries && (!detailTabsOn || activeDetailTab == "episodios")) {
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

                // Lista exibida: temporada selecionada, filtrada pela busca e
                // ordenada pelo modo escolhido — calculada 1x e usada pelo
                // mapa, pelo contador e pelos cards (mesma ordem nos três).
                val seasonEpisodes = state.seasons[state.selectedSeason] ?: emptyList()
                val episodes = sortEpisodes(filterEpisodes(seasonEpisodes, episodeQuery), EpisodeSort.fromId(episodeSortId))

                // ── Navegador de episódios (Lote 2 item 3) ─────────────────
                // Busca + ordenação + Rating Map, SOBRE os cards da Fase B.
                // Escopo: a temporada do chip selecionado (as temporadas
                // carregam sob demanda; buscar em todas exigiria baixar todas).
                // Tudo filtra em memória — trocar a busca não recarrega a série.
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = pad)
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = episodeQuery,
                            onValueChange = { episodeQuery = it },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                            trailingIcon = {
                                if (episodeQuery.isNotEmpty()) IconButton(onClick = { episodeQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Limpar busca", tint = TextTertiary)
                                }
                            },
                            placeholder = { Text("Buscar episódio…", color = TextTertiary, fontSize = 15.sp) },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Color.Transparent, cursorColor = Accent,
                                focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Ordenar:",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ManropeFontFamily,
                            )
                            EpisodeSort.entries.forEach { sort ->
                                com.aotv.app.ui.common.FilterChipPill(
                                    sort.label,
                                    episodeSortId == sort.id,
                                    onClick = { episodeSortId = sort.id },
                                )
                            }
                        }
                        // Rating Map (Lote 2 item 4): uma célula por episódio da
                        // lista exibida, pintada pela nota. Some quando as notas
                        // são desligadas (item 5) — o mapa É a nota, em cor.
                        if (showEpisodeRatings && episodes.isNotEmpty()) {
                            RatingMap(
                                episodes = episodes,
                                onEpisodeClick = { ep -> viewModel.selectEpisode(ep.seasonNumber, ep.episodeNumber) },
                            )
                        }
                        if (episodeQuery.isNotBlank()) {
                            Text(
                                "${episodes.size} de ${seasonEpisodes.size} episódios",
                                color = TextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ManropeFontFamily,
                            )
                        }
                    }
                }

                // Episode rows
                items(episodes, key = { it.episodeNumber }) { ep ->
                    EpisodeRowCard(
                        episode = ep,
                        // (D) visto (≥90%) vs vendo (em andamento)
                        watched = state.watchedEpisodes.contains("${ep.seasonNumber}:${ep.episodeNumber}"),
                        watching = state.watchingEpisode == "${ep.seasonNumber}:${ep.episodeNumber}",
                        style = episodeStyle,
                        // (Lote 2 item 5) notas desligadas somem da meta do card.
                        showRating = showEpisodeRatings,
                        modifier = Modifier.padding(horizontal = pad, vertical = 5.dp),
                        onPlay = {
                            onPlayEpisode(detailItem.tmdbId, ep.seasonNumber, ep.episodeNumber, state.seasons[ep.seasonNumber]?.size ?: 0)
                        },
                        // (A) tocar o card abre o painel de fontes/servidores do episódio
                        onSelect = { viewModel.selectEpisode(ep.seasonNumber, ep.episodeNumber) },
                    )
                }

                // Busca sem resultado: avisa e oferece limpar (diferente de
                // temporada vazia, que é falha de carregamento — ver abaixo).
                if (seasonEpisodes.isNotEmpty() && episodes.isEmpty() && episodeQuery.isNotBlank()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Nenhum episódio corresponde à busca.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontFamily = ManropeFontFamily,
                            )
                            Text(
                                "Limpar busca",
                                color = Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ManropeFontFamily,
                                modifier = Modifier.clickable { episodeQuery = "" }.padding(8.dp),
                            )
                        }
                    }
                }

                // Temporada sem episódios carregados e sem erro: avisa e oferece retry
                // em vez de deixar a lista vazia sem explicação.
                if (seasonEpisodes.isEmpty() && !state.isLoading && state.error == null) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Nenhum episódio carregado nesta temporada.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontFamily = ManropeFontFamily,
                            )
                            Text(
                                "Tentar novamente",
                                color = Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ManropeFontFamily,
                                modifier = Modifier.clickable { viewModel.retry() }.padding(8.dp),
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(28.dp)) }
            }

            // ── Elenco (TMDB credits) ──────────────────────────────────────────
            if (state.cast.isNotEmpty() && (!detailTabsOn || activeDetailTab == "elenco")) {
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
            if (state.collectionItems.size > 1 && (!detailTabsOn || activeDetailTab == "mais")) {
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
            if (state.related.isNotEmpty() && (!detailTabsOn || activeDetailTab == "mais")) {
                item {
                    com.aotv.app.ui.common.SectionHeader("Relacionados", Modifier.padding(top = 18.dp), eyebrow = "Você também pode gostar")
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = pad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.related, key = { rowKey(it) }) { rel ->
                            val click = remember(rel) { { onRelatedClick(rel) } }
                            com.aotv.app.ui.common.PosterCard(item = rel, onClick = click, modifier = Modifier.width(com.aotv.app.ui.common.posterWidth()))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
        // Fallback < Android 12 (Lote 4): sem RenderEffect, o scrim fosco
        // escurece o conteúdo atrás da folha quando o toggle está ligado.
        // Com efeito real ele não aparece (nunca os dois juntos).
        FrostedScrim(panelBlurOn && (sheetOpen || state.personName != null), panelBlurEffect)
        } // fecha o Box do blur atrás das folhas

        // ── (A) Painel de FONTES/SERVIDORES do episódio selecionado ────────────
        // Reaproveita o visual do bloco "Fontes" do filme (SourceRow), como bottom sheet.
        state.selectedEpisode?.let { (season, episode) ->
            EpisodeServersSheet(
                season = season,
                episode = episode,
                // Lote 3 item 4: com o blur ligado, o card fica translúcido p/
                // o conteúdo desfocado aparecer através dele (vidro fosco).
                blurred = blurBehindSheets,
                stremioStreams = state.episodeStremioStreams,
                facts = state.episodeFacts(),
                debridTag = state.debridTag,
                stremioLoading = state.episodeStremioLoading,
                pluginDone = state.episodePluginDone,
                pluginTotal = state.episodePluginTotal,
                onDismiss = { viewModel.clearSelectedEpisode() },
                // v1.8.1 — botão Recarregar da folha: re-busca as fontes do episódio.
                onReloadSources = { viewModel.reloadEpisodeSources() },
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
                kodiAddons = state.kodiVideoAddons,
                onOpenKodiAddon = { addonId ->
                    viewModel.clearSelectedEpisode()
                    onOpenKodiAddon(addonId, title)
                },
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
                // Lote 3 item 4: vidro fosco, como na folha de fontes.
                blurred = blurBehindSheets,
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
    // Lote 3 item 4: com o blur ligado, o card fica translúcido (vidro fosco)
    // e o conteúdo da página atrás aparece desfocado; desligado, opaco como antes.
    blurred: Boolean = false,
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
                .background(if (blurred) Background.copy(alpha = 0.86f) else Background)
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
    // v1.8.1 — botão Recarregar: re-busca as fontes do episódio (ViewModel).
    // null = sem botão (só aparece quando há de onde recarregar).
    onReloadSources: (() -> Unit)? = null,
    onPlayStremio: (com.aotv.app.data.remote.StremioStream) -> Unit,
    isDownloadable: (String) -> Boolean = { false },
    onDownloadStremio: (com.aotv.app.data.remote.StremioStream) -> Unit = {},
    // v1.4, Fase 4 — grupo "Addons Kodi" na folha (addons de vídeo
    // habilitados; tocar abre o navegador do addon).
    kodiAddons: List<com.aotv.app.data.kodi.KodiAddonStore.KodiAddon> = emptyList(),
    onOpenKodiAddon: (String) -> Unit = {},
    // Fase C — prefs da folha (busca, emblemas, logo, fixadas).
    streamPrefs: StreamPrefsViewModel = hiltViewModel(),
    // Lote 3 item 4: com o blur ligado, o card fica translúcido (vidro fosco)
    // e o conteúdo da página atrás aparece desfocado; desligado, opaco como antes.
    blurred: Boolean = false,
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
                .background(if (blurred) BackgroundCard.copy(alpha = 0.86f) else BackgroundCard)
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
            // Fase C: busca, emblemas, logo e fixadas vêm das prefs de streams.
            val searchOn by streamPrefs.search.collectAsStateWithLifecycle()
            val pinned by streamPrefs.pinned.collectAsStateWithLifecycle()
            val cardPrefs = rememberStreamCardPrefs(streamPrefs)
            val sourceData = rememberSourceListData(
                stremioStreams, facts, pinned,
                // Recarregar fica ocupado enquanto addons ou plugins ainda respondem.
                loading = stremioLoading || (pluginTotal > 0 && pluginDone < pluginTotal),
                onReloadSources = onReloadSources,
            )
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
                item(key = "filters") { SourceListFilters(sourceData, showSearch = searchOn) }
                sourceData.sections.forEach { (addon, gs) ->
                    item(key = "section-$addon") {
                        SourceSectionHeader(addon, gs.size, pinned = addon in pinned,
                            onTogglePin = { streamPrefs.togglePin(addon) })
                    }
                    gs.chunked(cols.coerceAtLeast(1)).forEach { row ->
                        // Key = url do lead do 1º grupo da fileira (estável por grupo).
                        item(key = "row-${row.first().lead.url}") {
                            SourceCardRow(
                                row, sourceData, "", onPlayStremio,
                                { s -> if (isDownloadable(s.url)) ({ onDownloadStremio(s) }) else null },
                                cols, debridTag, cardPrefs,
                            )
                        }
                    }
                }
                if (sourceData.filtered.size > sourceData.limit) item(key = "more") {
                    com.aotv.app.ui.common.AotvButton("Mostrar mais (${sourceData.filtered.size - sourceData.limit})", { sourceData.onMore() })
                }
                // v1.4, Fase 4 — grupo "Addons Kodi": os addons de vídeo
                // habilitados entram como fontes navegáveis (o Kodi não
                // busca por título aqui — tocar abre o catálogo do addon).
                if (kodiAddons.isNotEmpty()) {
                    item(key = "kodi-header") { SourceSectionHeader("Addons Kodi", kodiAddons.size) }
                    kodiAddons.forEach { addon ->
                        item(key = "kodi-${addon.id}") {
                            SourceRow(
                                label = com.aotv.app.data.kodi.KodiPluginRunner.cleanKodiText(
                                    addon.name.ifBlank { addon.id },
                                ),
                                quality = "v${addon.version.ifBlank { "?" }}",
                                tag = "KODI",
                                tagColor = Accent,
                                onClick = { onOpenKodiAddon(addon.id) },
                            )
                        }
                    }
                }
                if (sourceData.filtered.isEmpty() && stremioStreams.isNotEmpty()) item(key = "empty-list") {
                    Text(
                        if (sourceData.query.isNotBlank()) "Nenhuma fonte bate com essa busca."
                        else if (sourceData.wrongCount > 0 && !sourceData.showWrong) "As fontes encontradas parecem ser de outro filme. Use Mostrar todas para conferir."
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
    /** Busca por texto livre (Fase C): só da sessão, não persiste. */
    val query: String,
    val onQueryChange: (String) -> Unit,
    val limit: Int,
    val onMore: () -> Unit,
    /** true enquanto as fontes ainda estão chegando (o Recarregar gira e trava). */
    val loading: Boolean,
    /** Recarrega as fontes e reseta o "Mostrar mais". null = sem botão Recarregar. */
    val onReload: (() -> Unit)?,
    val fact: (com.aotv.app.data.remote.StremioStream) -> com.aotv.app.data.remote.SourceFacts,
    val playable: (ReleaseGroup) -> com.aotv.app.data.remote.StremioStream,
    val addonOf: (ReleaseGroup) -> String,
)

/** Normaliza p/ busca: minúsculo e sem acento ("Dublado" casa com "dublado"). */
private fun normBusca(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()

/** Palavras de qualidade que o rank do stream cobre ("4k" casa com 2160p e vice-versa). */
private fun qualityWords(rank: Int): String = when {
    rank >= 2160 -> "4k 2160p uhd"
    rank >= 1440 -> "1440p 2k"
    rank >= 1080 -> "1080p fullhd fhd"
    rank >= 720 -> "720p hd"
    rank >= 480 -> "480p sd"
    else -> ""
}

/**
 * A busca casa com addon, qualidade, idioma, codec, servidor/grupo e título do arquivo:
 * o rótulo cru do addon já traz quase tudo isso, e o resto (qualidade normalizada, idioma,
 * nome do arquivo) entra no texto. Toda palavra digitada precisa aparecer em algum lugar
 * do grupo.
 */
private fun groupMatchesQuery(
    group: ReleaseGroup,
    words: List<String>,
    fact: (com.aotv.app.data.remote.StremioStream) -> com.aotv.app.data.remote.SourceFacts,
): Boolean {
    val hay = normBusca(buildString {
        group.members.forEach { s ->
            append(' '); append(s.addon)
            append(' '); append(s.label)
            append(' '); append(s.fileName)
            append(' '); append(qualityWords(fact(s).rank))
            append(' '); append(when (fact(s).lang) { "dub" -> "dublado dub"; "leg" -> "legendado leg"; else -> "" })
        }
    })
    return words.all { it in hay }
}

@Composable
private fun rememberSourceListData(
    streams: List<com.aotv.app.data.remote.StremioStream>,
    facts: List<com.aotv.app.data.remote.SourceFacts>,
    /** Addons fixados, na ordem escolhida (Fase C): as seções deles vão p/ o topo. */
    pinned: List<String> = emptyList(),
    /** true enquanto as fontes ainda estão chegando (botão Recarregar ocupado). */
    loading: Boolean = false,
    /** Re-busca as fontes (ViewModel). null = sem botão Recarregar. */
    onReloadSources: (() -> Unit)? = null,
): SourceListData {
    var filter by rememberSaveable { mutableStateOf("all") }
    var showWrong by rememberSaveable { mutableStateOf(false) }
    // Filtro por fonte (""): "Todas" ou o nome do addon/provider.
    var sourceFilter by rememberSaveable { mutableStateOf("") }
    // Busca por texto livre (Fase C): só da sessão, não persiste.
    var query by rememberSaveable { mutableStateOf("") }
    // O limite reseta quando qualquer filtro muda (comportamento antigo).
    var limit by rememberSaveable(filter, showWrong, sourceFilter, query) { mutableStateOf(24) }
    val groups = remember(streams) { groupReleases(streams) }
    // Tudo abaixo é derivado com remember: antes, cada emissão do UiState
    // (um probe, um addon) re-derivava as listas e criava listas novas, o
    // que inutilizava até o remember(streams) do groupReleases.
    val byUrl = remember(facts) { facts.associateBy { it.url } }
    fun fact(s: com.aotv.app.data.remote.StremioStream) = byUrl[s.url]
        ?: com.aotv.app.data.remote.SourceFacts(s.url, s.addon, s.releaseText.ifBlank { s.label }, s.qualityRank)
    val wrongCount = remember(groups, facts) { groups.count { g -> g.members.all { fact(it).wrongTitle } } }
    val visible = remember(groups, facts, showWrong) { groups.filter { g -> showWrong || g.members.any { !fact(it).wrongTitle } } }
    val counts = remember(visible, facts) { SRC_FILTERS.associate { (k, _, fn) -> k to visible.count { g -> g.members.any { fn(fact(it)) } } } }
    val predicate = remember(filter) { SRC_FILTERS.firstOrNull { it.first == filter }?.third ?: { true } }
    val langFiltered = remember(visible, facts, predicate) { visible.filter { g -> g.members.any { predicate(fact(it)) } } }
    // Fonte que vai tocar em cada versão (a viva, fora as de outro filme). O grupo pode ter o
    // mesmo arquivo em mais de um addon — a seção é o addon dessa fonte.
    fun playable(group: ReleaseGroup) =
        group.members.firstOrNull { !fact(it).dead && !fact(it).wrongTitle } ?: group.lead
    fun addonOf(group: ReleaseGroup) =
        fact(playable(group)).addon.ifBlank { playable(group).addon }.ifBlank { "Outras fontes" }
    // Ordem das fixadas (Fase C): índice na lista de fixadas; quem não está fixado vai depois.
    val pinIndex = remember(pinned) { pinned.withIndex().associate { it.value to it.index } }
    // Fontes presentes (dentro do filtro de idioma), ordenadas pelo melhor stream.
    // Fixadas primeiro, na ordem escolhida; o resto segue por rank como antes.
    val sources = remember(langFiltered, facts, pinIndex) {
        langFiltered.groupBy(::addonOf).entries
            .sortedWith(compareBy({ pinIndex[it.key] ?: Int.MAX_VALUE }, { -it.value.maxOf { g -> fact(playable(g)).rank } }))
            .map { it.key to it.value.size }
    }
    val filtered = remember(langFiltered, facts, sourceFilter, query, pinIndex) {
        val bySource = if (sourceFilter.isEmpty()) langFiltered
        else langFiltered.filter { addonOf(it) == sourceFilter }
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map(::normBusca)
        val byQuery = if (words.isEmpty()) bySource else bySource.filter { groupMatchesQuery(it, words, ::fact) }
        // Fixadas primeiro (ordenação estável: o resto mantém a ordem de chegada, e as
        // fixadas sobrevivem ao corte do "Mostrar mais").
        byQuery.sortedWith(compareBy { pinIndex[addonOf(it)] ?: Int.MAX_VALUE })
    }
    // Seções por reprodutor (addon), ordenadas pelo melhor stream de cada uma (maior rank).
    // Fixadas primeiro, na ordem escolhida. O limite ("Mostrar mais") corta antes de
    // agrupar, então ele continua valendo no total.
    val shown = remember(filtered, limit) { filtered.take(limit) }
    val sections = remember(shown, facts, pinIndex) {
        shown.groupBy(::addonOf).entries.sortedWith(
            compareBy({ pinIndex[it.key] ?: Int.MAX_VALUE }, { -it.value.maxOf { g -> fact(playable(g)).rank } }),
        )
    }
    val best = remember(facts) { facts.getOrNull(com.aotv.app.data.remote.bestIndex(facts))?.url }
    return SourceListData(
        groups = groups, streamCount = streams.size, wrongCount = wrongCount,
        visible = visible, counts = counts, sources = sources,
        filtered = filtered, shown = shown, sections = sections, best = best,
        filter = filter, onFilterChange = { filter = it },
        showWrong = showWrong, onShowWrongChange = { showWrong = it },
        sourceFilter = sourceFilter, onSourceFilterChange = { sourceFilter = it },
        query = query, onQueryChange = { query = it },
        limit = limit, onMore = { limit += 24 },
        loading = loading,
        // Recarregar reseta o "Mostrar mais" (volta a 24) e MANTÉM busca, filtro
        // por addon, filtro de idioma e fixadas (nenhum deles é tocado aqui).
        onReload = onReloadSources?.let { src -> { limit = 24; src() } },
        fact = { s -> fact(s) }, playable = { g -> playable(g) }, addonOf = { g -> addonOf(g) },
    )
}

/** Cabeçalho de uma seção por fonte ("VêTela Brasil · 5 versões"). */
@Composable
private fun SourceSectionHeader(
    addon: String,
    count: Int,
    /** A seção está fixada (pin preenchido). */
    pinned: Boolean = false,
    /** null = sem botão de pin (grupo "Addons Kodi"). Na TV o botão não aparece. */
    onTogglePin: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(addon, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = SoraFontFamily)
        Text("$count ${if (count == 1) "versão" else "versões"}", color = TextTertiary, fontSize = 12.5.sp, fontFamily = ManropeFontFamily)
        // Pin (Fase C): só no celular — na TV mantém o comportamento atual, sem o botão.
        if (onTogglePin != null && !com.aotv.app.ui.common.LocalIsTv.current) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    if (pinned) "Desfixar fonte" else "Fixar fonte",
                    tint = if (pinned) Accent else TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
    cardPrefs: StreamCardPrefs = StreamCardPrefs(),
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { group ->
            val source = data.playable(group)
            val f = data.fact(source)
            // parseSourceLabel + ~10 regexes por linha: memorizado por URL
            // (antes rodava em toda linha visível, a cada recomposição).
            var info = remember(source.url, mediaTitle, f.probe?.height) {
                releaseInfo(source, mediaTitle, group.members.map { it.addon }.distinct())
            }
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
                        suggested = group.members.any { it.url == data.best },
                        logoUrl = if (cardPrefs.showLogo) cardPrefs.logoOf(info.addon) else "",
                        cardPrefs = cardPrefs)
                }
            }
        }
        repeat(cols.coerceAtLeast(1) - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Fileiras de filtros: busca por texto (Fase C), por fonte (novo) e por idioma (existente). */
@Composable
private fun SourceListFilters(data: SourceListData, showSearch: Boolean = false) {
    // Busca por texto livre (Fase C) + botão Recarregar (v1.8.1), lado a lado e
    // visíveis sem rolar, acima dos chips. O Recarregar é um GlassCircleButton
    // (foco D-pad na TV; gira e trava enquanto carrega) e não mexe em busca,
    // filtros nem fixadas — só re-busca as fontes e reseta o "Mostrar mais".
    if (showSearch || data.onReload != null) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSearch) {
                val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                com.aotv.app.ui.common.TvTextFieldGate(Modifier.weight(1f)) { gate ->
                    OutlinedTextField(
                        value = data.query,
                        onValueChange = data.onQueryChange,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary) },
                        trailingIcon = {
                            if (data.query.isNotEmpty()) IconButton(onClick = { data.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, "Limpar busca", tint = TextTertiary)
                            }
                        },
                        placeholder = { Text("Buscar nas fontes…", color = TextTertiary, fontSize = 15.sp) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Color.Transparent, cursorColor = Accent,
                            focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = gate,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            data.onReload?.let { reload ->
                GlassCircleButton(
                    icon = Icons.Default.Refresh,
                    onClick = reload,
                    size = 38.dp,
                    iconTint = TextSecondary,
                    contentDescription = "Recarregar fontes",
                    busy = data.loading,
                )
            }
        }
    }
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
    // v1.4, Fase 4 — grupo "Addons Kodi" (addons de vídeo habilitados;
    // tocar abre o navegador do addon).
    kodiAddons: List<com.aotv.app.data.kodi.KodiAddonStore.KodiAddon> = emptyList(),
    onOpenKodiAddon: (String) -> Unit = {},
    // Fase C — prefs da lista (busca, emblemas, logo, fixadas).
    streamPrefs: StreamPrefsViewModel = hiltViewModel(),
    // v1.8.1 — botão Recarregar: re-busca as fontes (ViewModel). null = sem botão.
    onReloadSources: (() -> Unit)? = null,
    /** true enquanto as fontes ainda estão chegando (botão Recarregar ocupado). */
    loading: Boolean = false,
) {
    if (television) {
        com.aotv.app.ui.tv.TvScaled { SourceList(streams, facts, mediaTitle, onPlay, onDownload, cols, debridTag, television = false, kodiAddons = kodiAddons, onOpenKodiAddon = onOpenKodiAddon, streamPrefs = streamPrefs, onReloadSources = onReloadSources, loading = loading) }
        return
    }
    val searchOn by streamPrefs.search.collectAsStateWithLifecycle()
    val pinned by streamPrefs.pinned.collectAsStateWithLifecycle()
    val cardPrefs = rememberStreamCardPrefs(streamPrefs)
    val data = rememberSourceListData(streams, facts, pinned, loading = loading, onReloadSources = onReloadSources)
    if (data.groups.isNotEmpty()) Text("${data.groups.size} versões em ${data.streamCount} fontes", color = TextTertiary, fontSize = 13.sp)
    SourceListFilters(data, showSearch = searchOn)
    data.sections.forEach { (addon, gs) ->
        SourceSectionHeader(addon, gs.size, pinned = addon in pinned,
            onTogglePin = { streamPrefs.togglePin(addon) })
        gs.chunked(cols.coerceAtLeast(1)).forEach { row ->
            SourceCardRow(row, data, mediaTitle, onPlay, onDownload, cols, debridTag, cardPrefs)
        }
    }
    // v1.4, Fase 4 — grupo "Addons Kodi": os addons de vídeo habilitados
    // entram como fontes navegáveis (o Kodi não busca por título aqui —
    // tocar abre o catálogo do addon).
    if (kodiAddons.isNotEmpty()) {
        SourceSectionHeader("Addons Kodi", kodiAddons.size)
        kodiAddons.forEach { addon ->
            SourceRow(
                label = com.aotv.app.data.kodi.KodiPluginRunner.cleanKodiText(
                    addon.name.ifBlank { addon.id },
                ),
                quality = "v${addon.version.ifBlank { "?" }}",
                tag = "KODI",
                tagColor = Accent,
                onClick = { onOpenKodiAddon(addon.id) },
            )
        }
    }
    if (data.filtered.size > data.limit) com.aotv.app.ui.common.AotvButton("Mostrar mais (${data.filtered.size - data.limit})", { data.onMore() })
    if (data.filtered.isEmpty() && streams.isNotEmpty()) Text(
        if (data.query.isNotBlank()) "Nenhuma fonte bate com essa busca."
        else if (data.wrongCount > 0 && !data.showWrong) "As fontes encontradas parecem ser de outro filme. Use Mostrar todas para conferir."
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
    style: EpisodeCardStyle = EpisodeCardStyle.HORIZONTAL,
    // (Lote 2 item 5) notas desligadas: a "★ nota" some da meta do card
    // (nos dois estilos). A nota da série no hero NÃO é afetada — só a do episódio.
    showRating: Boolean = true,
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
    // Meta do episódio: data de lançamento · duração · nota (minimalista).
    // A nota só entra quando as notas estão ligadas (item 5) — e só quando
    // existe (rating 0 = sem votos no TMDB, não "nota zero").
    val epMeta = remember(episode.airDate, episode.runtimeMin, episode.rating, showRating) {
        buildList {
            fmtIsoDateMobile(episode.airDate).takeIf { it.isNotBlank() }?.let { add(it) }
            if (episode.runtimeMin > 0) add("${episode.runtimeMin} min")
            if (showRating && episode.rating > 0) add("★ %.1f".format(episode.rating))
        }
    }
    // Empilhado com foco em texto (Fase B): sem arte, título + sinopse + duração.
    if (style == EpisodeCardStyle.STACKED) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BackgroundCard)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                // (A) tocar o card abre o painel de fontes/servidores do episódio
                .clickable(interactionSource = rowSrc, indication = androidx.compose.foundation.LocalIndication.current, onClick = onSelect)
                .padding(12.dp)
                .alpha(cardAlpha),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${episode.episodeNumber}. ${episode.name}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    lineHeight = 19.sp,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = SoraFontFamily,
                    modifier = Modifier.weight(1f),
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
            if (epMeta.isNotEmpty()) {
                Text(
                    epMeta.joinToString("  ·  "),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ManropeFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    episode.overview,
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 17.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        watched -> "Visto"
                        watching -> "Continuando"
                        else -> "Episódio ${episode.episodeNumber}"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ManropeFontFamily,
                )
                SmallPlayBtn("PLAY", Accent, onPlay)
            }
        }
        return
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
        val fg = if (focused) OnAccent else accentColor
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

// ── Fundo cinematográfico (Fase B) ────────────────────────────────────────────
// Arte em tela cheia atrás da página, desfocada (RenderEffect, API 31+) e
// escurecida p/ o texto continuar legível. Em API mais baixa fica só a arte
// escurecida — sem blur, sem biblioteca nova. A escala 1.18 cobre as bordas
// que o blur deixaria transparentes.
@Composable
private fun CinematicBackdrop(backdropUrl: String, sheetOpen: Boolean = false) {
    // RenderEffect criado 1× (antes era criado dentro do graphicsLayer a cada
    // recomposição). Com a folha aberta o blur nem é aplicado.
    val blur = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else {
            null
        }
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.18f)
                .then(
                    if (blur != null && !sheetOpen) {
                        Modifier.graphicsLayer { renderEffect = blur }
                    } else Modifier
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Background.copy(alpha = 0.55f),
                            Background.copy(alpha = 0.68f),
                        ),
                    ),
                ),
        )
    }
}

// ── Ação em ícone com rótulo (Fase B) ─────────────────────────────────────────
@Composable
private fun DetailActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = TextPrimary,
    busy: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressScale(src)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Surface2)
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Accent)
            else Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, fontFamily = ManropeFontFamily)
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

/**
 * Cartão de erro do detalhe: mensagem + "Tentar novamente". É o que impede a tela
 * de ficar preta para sempre quando o TMDB falha ou o load trava.
 */
@Composable
private fun ErrorRetryCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard2)
            .border(1.dp, LiveRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            message,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text("Tentar novamente", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = ManropeFontFamily)
        }
    }
}
