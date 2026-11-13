package com.aotv.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.PosterDecorations
import com.aotv.app.ui.common.rememberPosterVisual
import com.aotv.app.ui.common.SpinningIcon
import com.aotv.app.ui.common.rememberReducedMotion
import com.aotv.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation

// ── MEDIDAS ─────────────────────────────────────────────────────────────────
// O webOS mede tudo em rem, com 1rem = 1/120 da largura da tela (16px em 1920). Aqui igual,
// assim a TV fica com as mesmas proporções do webOS em qualquer densidade.

/** dp de 1rem do webOS nesta tela. */
@Composable
@ReadOnlyComposable
fun remDp(): Float = LocalConfiguration.current.let { tvRem(it.screenWidthDp, it.screenHeightDp) }

internal fun tvRem(widthDp: Int, heightDp: Int): Float =
    minOf(widthDp / 120f, heightDp / 67.5f).coerceAtLeast(1f)

/** Medida em rem do webOS. */
val Number.r: Dp
    @Composable @ReadOnlyComposable get() = (toFloat() * remDp()).dp

/** Tamanho de fonte em rem do webOS, sem a escala de fonte do sistema (igual ao CSS). */
val Number.rs: TextUnit
    @Composable @ReadOnlyComposable get() = (toFloat() * remDp() / LocalDensity.current.fontScale).sp

/** Margem lateral das telas na mesma proporção do webOS. */
val tvPad: Dp @Composable @ReadOnlyComposable get() = 3.4.r

// ── ROLAGEM DO FOCO (scrollIntoView do focus.js) ────────────────────────────

/**
 * Rolagem vertical do webOS. O que recebe foco fica a [inset] das bordas (data-inset). Se já
 * está dentro, não rola. Se é maior que a área, o topo fica visível (o rótulo da fileira).
 */
@OptIn(ExperimentalFoundationApi::class)
private class ColumnSpec(private val inset: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = offset - inset
        return when {
            top < 0 -> top
            offset + size > containerSize - inset -> minOf(offset + size - (containerSize - inset), top)
            else -> 0f
        }
    }
}

/**
 * Rolagem da fileira do webOS. Saindo pela esquerda alinha na margem [pad], saindo pela direita
 * deixa aparecer 60% do próximo cartão.
 */
@OptIn(ExperimentalFoundationApi::class)
private class RowSpec(private val pad: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val peek = size * 0.6f
        return when {
            offset < pad -> offset - pad
            offset + size + peek > containerSize -> offset + size + peek - containerSize
            else -> 0f
        }
    }
}

/** Área de rolagem vertical com a regra do webOS. Fileiras dentro usam [TvRow]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvScrollArea(inset: Dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { inset.toPx() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(px) { ColumnSpec(px) }, content = content)
}

/**
 * Seção que entra inteira na tela quando algo dentro dela ganha foco (a .row do webOS). Assim a
 * fileira aparece com o rótulo, e não colada na borda.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvRow(): Modifier = composed {
    val req = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this.bringIntoViewRequester(req).onFocusChanged { if (it.hasFocus) scope.launch { req.bringIntoView() } }
}

/** Foco aqui rola a área até o topo (data-scroll-top do webOS), para mostrar o destaque inteiro. */
fun Modifier.tvScrollTop(toTop: suspend () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    this.onFocusChanged { if (it.hasFocus) scope.launch { runCatching { toTop() } } }
}

/**
 * Fileira horizontal (.rail-track). Rola como o webOS e volta ao último cartão focado quando o
 * foco entra de novo nela (track._last).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TvRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    gap: Dp = 1.25.r,
    vertical: Dp = 1.r,
    preserveColumnOnVertical: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val pad = tvPad
    val px = with(LocalDensity.current) { pad.toPx() }
    val rowFocus = if (preserveColumnOnVertical) {
        val requester = remember { FocusRequester() }
        Modifier.focusRequester(requester).focusProperties {
            onExit = { requester.saveFocusedChild() }
            onEnter = {
                // Cima e baixo acompanham a coluna atual, sem recuperar um cartão antigo da fileira.
                if (requestedFocusDirection != FocusDirection.Up && requestedFocusDirection != FocusDirection.Down && requester.restoreFocusedChild()) {
                    cancelFocusChange()
                }
            }
        }
    } else Modifier.focusRestorer()
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(px) { RowSpec(px) }) {
        LazyRow(
            modifier.fillMaxWidth().then(rowFocus),
            state = state,
            contentPadding = PaddingValues(start = pad, end = pad, top = vertical, bottom = vertical * 1.4f),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

// ── TEXTOS ──────────────────────────────────────────────────────────────────

@Composable
fun TvEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(bottom = 0.4.r), color = Accent, fontSize = 0.85.rs,
        fontWeight = FontWeight.Bold, letterSpacing = 0.1.rs, fontFamily = DisplayFont, maxLines = 1, lineHeight = 1.2.rs,
    )
}

/** Cabeçalho da fileira (.rail-head). */
@Composable
fun TvRailHead(title: String, eyebrow: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tvPad).padding(bottom = 0.2.r),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) TvEyebrow(eyebrow)
            Text(
                title, color = TextPrimary, fontSize = 1.65.rs, fontWeight = FontWeight.Bold, fontFamily = DisplayFont,
                lineHeight = 2.rs, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.04).rs,
            )
        }
        trailing?.invoke()
    }
}

/** Fileira inteira (.rail). Cabeçalho mais a fileira de cartões, e entra inteira no foco. */
@Composable
fun TvRail(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    preserveColumnOnVertical: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().tvRow().padding(top = 1.3.r, bottom = 0.2.r)) {
        TvRailHead(title, eyebrow, trailing)
        TvRow(state = state, preserveColumnOnVertical = preserveColumnOnVertical, content = content)
    }
}

@Composable
fun TvCountPill(text: String) {
    Text(
        text, color = TextTertiary, fontSize = 0.95.rs, fontFamily = Inter, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(FillTint.copy(alpha = 0.05f)).padding(horizontal = 0.9.r, vertical = 0.35.r),
    )
}

// ── FOCO ────────────────────────────────────────────────────────────────────

/** Escala animada do foco, 160 ms como no webOS. Parada com "remover animações". */
@Composable
fun tvFocusScale(focused: Boolean, to: Float): Float {
    val reduced = rememberReducedMotion()
    return animateFloatAsState(if (focused && !reduced) to else 1f, tween(if (reduced) 0 else 160), label = "tvf").value
}

/** Clique de TV sem o anel genérico (cada cartão desenha o próprio foco). Segurar OK = [onLongClick]. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, enabled: Boolean = true): Modifier = composed {
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
    )
}

// ── PÔSTER (.poster-card) ───────────────────────────────────────────────────

@Composable
fun TvPoster(
    item: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 12.5.r,
    subtitle: String? = null,
    badge: String? = null,
    order: Int = 0,
    current: Boolean = false,
    progress: Float? = null,
    typeBadge: String? = null,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    qualityNeighbors: List<MovieItem> = emptyList(),
) {
    val title = item.title
    var focused by remember { mutableStateOf(false) }
    val visual = rememberPosterVisual(item, badge, typeBadge != null, showTag = order <= 0, requestQuality = false)
    val posterInfo = com.aotv.app.ui.common.LocalPosterInfo.current
    if (focused && badge == null && posterInfo != null) LaunchedEffect(item.tmdbId, item.type, qualityNeighbors) {
        delay(700)
        val nearby = listOf(item) + qualityNeighbors.take(7)
        nearby.forEach(posterInfo::want)
        try { awaitCancellation() } finally { nearby.forEach(posterInfo::unwant) }
    }
    val context = LocalContext.current
    // Só desliga o hardware bitmap quando a matiz ainda precisa ser lida do
    // drawable exibido (onImage agora existe sempre, para marcar loaded).
    val readColor = visual.huePending
    val image = remember(context, item.posterUrl, item.backdropUrl, readColor) {
        ImageRequest.Builder(context).data(item.posterUrl.ifBlank { item.backdropUrl })
            .apply { if (readColor) allowHardware(false) }.build()
    }
    val s = tvFocusScale(focused, 1.06f)
    val shape = RoundedCornerShape(0.9.r)
    Column(modifier.then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(s)
                .shadow(if (focused) 1.2.r else 0.dp, shape)
                .clip(shape)
                .background(BackgroundCard2)
                .then(if (focused) Modifier.border(0.22.r, FocusRing, shape) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
                .tvClick(onClick, onLongClick),
        ) {
            Text(
                title, color = TextTertiary, fontSize = 1.05.rs, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(1.r),
            )
            AsyncImage(
                image, title, Modifier.fillMaxSize().padding(bottom = 2.09.r), contentScale = ContentScale.Crop,
                onSuccess = { visual.onImage?.invoke(it.result.drawable) },
            )
            PosterDecorations(item, visual, unit = 0.095.r, textUnit = 0.095.rs, imageBottom = 2.09.r)
            if (order > 0) Box(
                Modifier.align(Alignment.TopStart).padding(0.6.r).size(2.r).clip(CircleShape)
                    .background(if (current) Accent else Color.Black.copy(alpha = .72f)),
                contentAlignment = Alignment.Center,
            ) { Text("$order", color = if (current) OnAccent else Color.White, fontSize = 0.9.rs, fontWeight = FontWeight.ExtraBold) }
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).padding(bottom = 2.09.r).fillMaxWidth().height(0.32.r).background(Color.Black.copy(alpha = .55f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
            }
        }
        Text(
            title, color = if (current) Accent else if (focused) TextPrimary else TextSecondary, fontSize = 1.rs,
            fontWeight = FontWeight.SemiBold, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 0.8.r),
        )
        if (!subtitle.isNullOrBlank()) Text(
            subtitle, color = TextTertiary, fontSize = 0.85.rs, fontFamily = Inter, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.2.r),
        )
    }
}

// ── CARTÃO LARGO (.wide-card) ───────────────────────────────────────────────

/** O nome fica visível enquanto a arte do título carrega ou quando a imagem falha. */
@Composable
fun TvTitleArtwork(title: String, logoUrl: String, modifier: Modifier = Modifier, hero: Boolean = false) {
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val fontSize = if (hero) 4.rs else 3.6.rs
    val lineHeight = if (hero) 4.12.rs else 3.8.rs
    if (logoUrl.isBlank() || failed) Text(
        title, color = TextPrimary, fontSize = fontSize, lineHeight = lineHeight,
        fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, maxLines = 2,
        overflow = TextOverflow.Ellipsis, modifier = modifier,
    ) else Box(modifier.height(9.r).widthIn(max = 34.r), contentAlignment = Alignment.BottomStart) {
        if (!loaded) Text(title, color = TextPrimary, fontSize = fontSize, lineHeight = lineHeight,
            fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        AsyncImage(logoUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
            onSuccess = { loaded = true }, onError = { failed = true })
    }
}

class TvBadge(val text: String, val color: Color = Color.Black.copy(alpha = .72f))

@Composable
fun TvWideCard(
    image: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badges: List<TvBadge> = emptyList(),
    progress: Float? = null,
    playIcon: Boolean = false,
    width: Dp = 22.r,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val s = tvFocusScale(focused, 1.05f)
    val shape = RoundedCornerShape(0.9.r)
    Column(modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).scale(s)
                .shadow(if (focused) 1.2.r else 0.dp, shape).clip(shape).background(BackgroundCard2)
                .then(if (focused) Modifier.border(0.22.r, FocusRing, shape) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
                .tvClick(onClick, onLongClick),
        ) {
            AsyncImage(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (playIcon) Box(
                Modifier.align(Alignment.Center).size(3.8.r).clip(CircleShape).background(Color.Black.copy(alpha = .55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(2.r)) }
            badges.forEachIndexed { i, b ->
                Text(
                    b.text, color = Color.White, fontSize = 0.8.rs, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(if (i == 0) Alignment.TopStart else Alignment.TopEnd).padding(0.6.r)
                        .clip(RoundedCornerShape(0.4.r)).background(b.color).padding(horizontal = 0.55.r, vertical = 0.22.r),
                )
            }
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.32.r).background(Color.Black.copy(alpha = .55f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
            }
        }
        Text(
            title, color = if (focused) TextPrimary else TextSecondary, fontSize = 1.05.rs, fontWeight = FontWeight.SemiBold,
            fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.8.r),
        )
        if (!subtitle.isNullOrBlank()) Text(
            subtitle, color = TextTertiary, fontSize = 0.85.rs, fontFamily = Inter, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.2.r),
        )
    }
}

// ── ELENCO (.cast-card) ─────────────────────────────────────────────────────

@Composable
fun TvCastCard(name: String, role: String, photo: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val s = tvFocusScale(focused, 1.07f)
    Column(
        Modifier.width(9.6.r).onFocusChanged { focused = it.hasFocus }.tvClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(8.6.r).scale(s).clip(CircleShape).background(Surface2)
                .then(if (focused) Modifier.border(0.22.r, FocusRing, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.isNotBlank()) AsyncImage(photo, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, tint = TextTertiary, modifier = Modifier.size(3.r))
        }
        Text(name, color = TextPrimary, fontSize = 1.rs, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.75.r))
        if (role.isNotBlank()) Text(role, color = TextTertiary, fontSize = 0.85.rs, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── BOTÕES E CHIPS (.btn e .chip) ───────────────────────────────────────────

/**
 * Botão pílula do webOS. [primary] branco, [on] azul suave. Com [busy] o ícone gira e o
 * clique fica travado. Desligado ainda segura o foco, para o controle não pular sozinho.
 */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    on: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val s = tvFocusScale(focused, 1.05f)
    val bg = when {
        !enabled -> Surface2
        primary -> FocusSurface
        on -> AccentSoft
        else -> Surface2
    }
    val fg = when {
        !enabled -> TextTertiary
        primary -> OnFocusSurface
        else -> TextPrimary
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .scale(s)
            .then(if (focused) Modifier.border(0.22.r, FocusRing, shape).padding(0.2.r) else Modifier.padding(0.2.r))
            .height(3.6.r)
            .clip(shape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .tvClick({ if (enabled && !busy) onClick() })
            .padding(horizontal = 1.7.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            SpinningIcon(tint = fg, size = 1.5.r)
            Spacer(Modifier.width(0.6.r))
        } else if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(1.5.r))
            Spacer(Modifier.width(0.6.r))
        }
        Text(label, color = fg, fontSize = 1.1.rs, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 1)
    }
}

@Composable
fun TvChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    var focused by remember { mutableStateOf(false) }
    val s = tvFocusScale(focused, 1.06f)
    val shape = RoundedCornerShape(50)
    Row(
        modifier.scale(s).height(2.7.r).clip(shape)
            .background(if (selected) Accent else Surface2)
            .then(if (focused) Modifier.border(0.2.r, FocusRing, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvClick(onClick)
            .padding(horizontal = 1.2.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Color.White else TextPrimary, modifier = Modifier.size(1.2.r)); Spacer(Modifier.width(0.5.r)) }
        Text(label, color = if (selected) Color.White else if (focused) TextPrimary else TextSecondary, fontSize = 1.rs, fontWeight = FontWeight.SemiBold, fontFamily = Inter, maxLines = 1)
    }
}

// ── METADADOS (.meta-row) ───────────────────────────────────────────────────

/** Faixa etária no padrão brasileiro (.age-l, .age-10 ...). */
@Composable
fun TvAge(cert: String) {
    if (cert.isBlank()) return
    val n = cert.filter { it.isDigit() }.toIntOrNull()
    val bg = when {
        cert.equals("L", true) -> Color(0xFF2E7D32)
        n == null -> Color(0xFF444444)
        n >= 18 -> Color(0xFF111111)
        n >= 16 -> Color(0xFFC62828)
        n >= 14 -> Color(0xFFEF6C00)
        n >= 12 -> Color(0xFFF9A825)
        else -> Color(0xFF1565C0)
    }
    Text(
        cert, color = Color.White, fontSize = 0.92.rs, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(RoundedCornerShape(0.3.r)).background(bg)
            .then(if (n != null && n >= 18) Modifier.border(1.dp, Color(0xFF555555), RoundedCornerShape(0.3.r)) else Modifier)
            .padding(horizontal = 0.5.r, vertical = 0.12.r),
    )
}

@Composable
fun TvMetaRow(rating: Double, cert: String, year: String, extra: List<String> = emptyList(), genres: List<String> = emptyList()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.15.r)) {
        if (rating > 0) Text("★ %.1f".format(rating), color = Gold, fontSize = 1.15.rs, fontWeight = FontWeight.ExtraBold)
        TvAge(cert)
        if (year.isNotBlank()) Text(year, color = TextSecondary, fontSize = 1.15.rs)
        extra.filter { it.isNotBlank() }.forEach { Text(it, color = TextSecondary, fontSize = 1.15.rs) }
        if (genres.isNotEmpty()) Text(
            genres.take(3).joinToString(", "), color = TextSecondary, fontSize = 1.15.rs, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 32.r),
        )
    }
}

/** Carregando no padrão do webOS (.spinner). */
@Composable
fun TvSpinner(label: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(2.4.r), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.CircularProgressIndicator(color = Accent, strokeWidth = 0.28.r, modifier = Modifier.size(2.8.r))
        if (label != null) Text(label, color = TextTertiary, fontSize = 1.rs, modifier = Modifier.padding(top = 1.r))
    }
}

/**
 * Texto da TV com altura de linha proporcional à fonte (o CSS do webOS usa o normal). O tema do
 * celular fixa 24sp de linha e deixava selo e rótulo altos demais na TV.
 */
@Composable
fun TvText(content: @Composable () -> Unit) {
    val base = androidx.compose.material3.LocalTextStyle.current
    CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides base.copy(
            fontFamily = Inter,
            lineHeight = 1.3.em,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
            ),
        ),
        content = content,
    )
}
