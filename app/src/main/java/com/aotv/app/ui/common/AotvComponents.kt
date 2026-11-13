package com.aotv.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aotv.app.R
import com.aotv.app.ui.theme.*

// Componentes visuais do celular no padrão do webOS (webos/src/css/app.css).
// Toda tela pega estilo daqui. Mudou aqui, muda em todas.

// ── MOVIMENTO ───────────────────────────────────────────────────────────────

/** true quando o sistema está com "Remover animações" ligado. */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Tween curto do app. Com animação reduzida vira instantâneo. */
fun <T> aotvTween(ms: Int, reduced: Boolean): TweenSpec<T> = tween(if (reduced) 0 else ms, easing = FastOutSlowInEasing)

/** Escala de foco e de toque. Foco 1.06 (D-pad), toque 0.97, 160 ms como no webOS. */
@Composable
private fun pressFocusScale(pressed: Boolean, focused: Boolean, focusScale: Float = 1.06f): State<Float> {
    val reduced = rememberReducedMotion()
    val target = if (reduced) 1f else if (focused) focusScale else if (pressed) 0.97f else 1f
    return animateFloatAsState(target, aotvTween(160, reduced), label = "pf")
}

/** Escala na camada de renderização: a animação roda na thread de desenho sem recompor o componente. */
private fun Modifier.layerScale(s: State<Float>): Modifier = graphicsLayer {
    scaleX = s.value
    scaleY = s.value
}

/** Ícone girando, usado no "Salvando…". Parado com animação reduzida. */
@Composable
fun SpinningIcon(icon: ImageVector = Icons.Default.Sync, tint: Color, size: Dp = 18.dp) {
    val reduced = rememberReducedMotion()
    val angle = if (reduced) 0f else rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spinA",
    ).value
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size).rotate(angle))
}

// ── PRESS FEEDBACK ──────────────────────────────────────────────────────────

/** Encolhe para [pressedScale] enquanto pressionado. Use junto de um clickable com o mesmo [interactionSource]. */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = this.then(
    Modifier.composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val reduced = rememberReducedMotion()
        val scale by animateFloatAsState(if (pressed && !reduced) pressedScale else 1f, aotvTween(140, reduced), label = "pressScale")
        Modifier.scale(scale)
    }
)

@Composable
fun PressableScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src, pressedScale)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        content = content,
    )
}

// ── LAYOUT LARGO ────────────────────────────────────────────────────────────

/** Tela larga (tablet ou celular deitado). Aí o resumo vai para a direita. */
@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** Margem lateral das telas. Maior em tela larga. */
@Composable
fun pagePadding(): Dp = if (isWideLayout()) 28.dp else 16.dp

/**
 * Conteúdo à esquerda e resumo fixo à direita em tela larga. Em tela estreita o resumo
 * desce para baixo do conteúdo, dentro da mesma rolagem ([content] recebe o resumo para
 * colocar no fim da lista).
 */
@Composable
fun ContentWithSummary(
    summary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    summaryWidth: Dp = 340.dp,
    content: @Composable (summaryBelow: (@Composable () -> Unit)?) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp && maxHeight >= 480.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content(null) }
                // O resumo rola dentro da própria coluna.
                Box(
                    Modifier.width(summaryWidth).fillMaxHeight()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(top = 16.dp, end = 24.dp, bottom = 16.dp)
                ) { summary() }
            }
        } else {
            Box(Modifier.fillMaxSize()) { content(summary) }
        }
    }
}

// ── TEXTO ───────────────────────────────────────────────────────────────────

/** Rótulo pequeno em caixa alta e azul acima do título (.eyebrow do webOS). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Accent) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, fontFamily = Inter,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Título de página com subtítulo opcional (.page-title e .page-sub). */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, sub: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = pagePadding()).padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, fontFamily = Inter)
            if (!sub.isNullOrBlank()) Text(sub, color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), fontFamily = Inter)
        }
        trailing?.invoke()
    }
}

// ── ETIQUETAS ───────────────────────────────────────────────────────────────

/** Etiqueta curta (.tag do webOS). Cor clara sobre fundo da mesma cor, com borda. */
@Composable
fun Badge(label: String, color: Color = Accent, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = .72f))
            .background(color.copy(alpha = .16f))
            .border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        fontFamily = Inter, letterSpacing = 0.5.sp, maxLines = 1,
    )
}

/** Faixa de qualidade da fonte, igual às caixas .q-gold, .q-accent, .q-mid e .q-low. */
enum class QualityTier { GOLD, ACCENT, MID, LOW }

fun qualityTier(rank: Int): QualityTier = when {
    rank >= 2160 -> QualityTier.GOLD
    rank >= 1080 -> QualityTier.ACCENT
    rank >= 720 -> QualityTier.MID
    else -> QualityTier.LOW
}

/** Caixa de qualidade do cartão de fonte (4K, 1080p, 720p). */
@Composable
fun QualityBox(label: String, tier: QualityTier, modifier: Modifier = Modifier, width: Dp = 56.dp, height: Dp = 42.dp) {
    val c = when (tier) {
        QualityTier.GOLD -> Gold
        QualityTier.ACCENT -> Accent
        QualityTier.MID -> TextSecondary
        QualityTier.LOW -> TextTertiary
    }
    val bgA = when (tier) { QualityTier.GOLD, QualityTier.ACCENT -> .12f; QualityTier.MID -> .07f; QualityTier.LOW -> .05f }
    val brA = when (tier) { QualityTier.GOLD, QualityTier.ACCENT -> .35f; QualityTier.MID -> .18f; QualityTier.LOW -> .12f }
    val tone = if (tier == QualityTier.MID || tier == QualityTier.LOW) Color.White else c
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = bgA))
            .border(1.dp, tone.copy(alpha = brA), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter, maxLines = 1)
    }
}

// ── PROGRESSO ───────────────────────────────────────────────────────────────

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, fillColor: Color = Accent) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Color.Black.copy(alpha = .55f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(fillColor))
    }
}

// ── VIDRO ───────────────────────────────────────────────────────────────────

@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(GlassBg).border(1.dp, BorderSubtle, RoundedCornerShape(radius)), content = content)
}

/** Botão redondo só de ícone. Alvo mínimo de 48dp, [contentDescription] obrigatório na prática. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconTint: Color = TextPrimary,
    contentDescription: String? = null,
    busy: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val s = pressFocusScale(pressed, focused)
    Box(
        modifier
            .layerScale(s)
            .onFocusChanged { focused = it.isFocused }
            .size(size.coerceAtLeast(48.dp))
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Black.copy(alpha = .45f))
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = .14f), CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = !busy, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) SpinningIcon(tint = if (focused) TextOnLight else iconTint, size = 22.dp)
        else Icon(icon, contentDescription = null, tint = if (focused) TextOnLight else iconTint, modifier = Modifier.size(22.dp))
    }
}

// ── TÍTULOS DE SEÇÃO ────────────────────────────────────────────────────────

@Composable
fun AotvSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    SectionHeader(
        title,
        action = if (action != null && onAction != null) ({ SeeAllLink(action, onAction) }) else null,
    )
}

/** Link "Ver tudo" à direita do título da fileira. Alvo de 48dp de altura. */
@Composable
fun SeeAllLink(label: String = "Ver tudo", onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Cabeçalho de fileira do webOS. Rótulo pequeno azul em cima e título forte. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    action: (@Composable () -> Unit)? = null,
    /** Barra na cor do tema abaixo do título (Fase B, ligada por padrão na Início). */
    underline: Boolean = false,
    /** Título um pouco menor e respiro menor (Fase B, densidade compacta da Início). */
    compact: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = pagePadding(), end = pagePadding() - 6.dp)
            .padding(top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            if (eyebrow != null) Eyebrow(eyebrow, Modifier.padding(bottom = 3.dp))
            Text(
                title, color = TextPrimary, fontSize = if (compact) 16.sp else 17.sp,
                lineHeight = if (compact) 21.sp else 22.sp, fontWeight = FontWeight.Bold,
                fontFamily = Inter, letterSpacing = (-0.2).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (underline) Box(
                Modifier.padding(top = 5.dp).width(28.dp).height(3.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Accent),
            )
        }
        if (action != null) Box(contentAlignment = Alignment.Center) { action() }
    }
}

// ── TOPO DA TELA ────────────────────────────────────────────────────────────

@Composable
fun ScreenTopHeader(
    title: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = pagePadding())
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.aotv_logo),
                contentDescription = "AOTV",
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Fit,
            )
            Text(
                title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = Inter, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onAction != null && actionIcon != null) {
            GlassCircleButton(icon = actionIcon, onClick = onAction, contentDescription = actionDescription ?: title)
        }
    }
}

// ── CHIP ────────────────────────────────────────────────────────────────────

/**
 * Chip pílula do webOS. Normal fundo 2D2D2D e texto cinza, selecionado azul com texto
 * branco, foco D-pad branco. Altura visual 40dp e área de toque de 48dp.
 */
@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val s = pressFocusScale(pressed, focused)
    val bg = if (focused) FocusSurface else if (selected) AccentFill else Surface2
    val fg = if (focused) OnFocusSurface else if (selected) Color.White else TextSecondary
    Box(
        modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = src, indication = null, enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.layerScale(s).heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (busy) SpinningIcon(tint = fg, size = 16.dp)
            else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter, maxLines = 1)
        }
    }
}

// ── PÔSTER ──────────────────────────────────────────────────────────────────

/**
 * Pôster do webOS. Cantos de 14dp, fundo de cartão enquanto carrega, foco D-pad com anel
 * branco e escala 1.06, toque encolhe para 0.97. Sem título por cima quando [cleanPoster].
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaPosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    aspectRatio: Float = 2f / 3f,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    cleanPoster: Boolean = false,
    /** Espaço embaixo da imagem (faixa do pôster). A imagem termina onde ela começa. */
    imageBottom: Dp = 0.dp,
    /** Avisa quando a imagem carregou (a cor do pôster sai de uma leitura separada de 8×12 px). */
    onImage: ((android.graphics.drawable.Drawable) -> Unit)? = null,
    /** Avisa quando a imagem NÃO carregou (a matiz cai para a leitura 8×12 do disco). */
    onImageError: (() -> Unit)? = null,
    /** Desenhado por cima da arte (etiqueta, faixa e selos do pôster). */
    decor: (@Composable BoxScope.() -> Unit)? = null,
    /** Raio dos cantos, em dp (Fase B: configurável na página Layout, padrão 14dp). */
    cornerRadius: Dp = 14.dp,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val s = pressFocusScale(pressed, focused)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .layerScale(s)
            .aspectRatio(aspectRatio)
            .shadow(if (focused) 18.dp else 0.dp, shape)
            .clip(shape)
            .background(Color.Black)
            .then(if (focused) Modifier.border(3.dp, FocusRing, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick, role = Role.Button)
                else
                    Modifier.clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            ),
    ) {
        // Sem imagem fica só a forma preta (sem texto, como o Nuvio).
        // Tamanho explícito em px de exibição: a chave do cache de memória fica
        // estável e o Coil não decodifica acima do que a tela mostra. A cor do
        // pôster sai do bitmap exibido (ver rememberPosterVisual).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = imageBottom)) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.takeIf { it > 0 && it != Constraints.Infinity }
                ?: with(density) { 108.dp.roundToPx() }
            val hPx = constraints.maxHeight.takeIf { it > 0 && it != Constraints.Infinity }
                ?: (wPx * 3 / 2)
            val context = LocalContext.current
            val request = remember(imageUrl, wPx, hPx) {
                ImageRequest.Builder(context).data(imageUrl).size(wPx, hPx).build()
            }
            AsyncImage(
                model = request, contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = onImage?.let { f -> { s: coil.compose.AsyncImagePainter.State.Success -> f(s.result.drawable) } },
                onError = onImageError?.let { f -> { _: coil.compose.AsyncImagePainter.State.Error -> f() } },
            )
        }
        if (!cleanPoster) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colorStops = arrayOf(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)))
                )
            )
        }
        if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { badge() }
        if (!cleanPoster) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 12.dp else 10.dp)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(
                    subtitle, color = TextSecondary, fontSize = 12.sp, fontFamily = Inter, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // Barra de progresso colada embaixo da imagem, como a .pbar do webOS.
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(bottom = imageBottom).fillMaxWidth().height(4.dp)
                    .background(Color.Black.copy(alpha = .55f)),
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
            }
        }
        decor?.invoke(this)
    }
}

/** Título e linha de baixo de um pôster, fora da imagem (.pc-title e .pc-sub). */
@Composable
fun PosterCaption(title: String, sub: String? = null, highlight: Boolean = false) {
    Text(
        title, color = if (highlight) Accent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
    )
    if (!sub.isNullOrBlank()) Text(
        sub, color = TextTertiary, fontSize = 11.sp, fontFamily = Inter, maxLines = 2,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
    )
}

// ── ESTADO VAZIO ────────────────────────────────────────────────────────────

@Composable
fun AotvEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
        Text(title, color = TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, textAlign = TextAlign.Center)
        Text(
            message, color = TextTertiary, fontSize = 15.sp, fontFamily = Inter, lineHeight = 22.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp), maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        action?.invoke()
    }
}

// ── BOTÕES ──────────────────────────────────────────────────────────────────

/** Tom do botão pílula. PRIMARY branco, SECONDARY cinza, ON azul suave (ligado), DANGER vermelho. */
enum class ButtonTone { PRIMARY, SECONDARY, ON, DANGER }

/**
 * Botão pílula do webOS (.btn). 52dp de altura. Com [busy] o ícone gira, o texto vira
 * [busyLabel] e o clique fica travado até a resposta chegar.
 */
@Composable
fun AotvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.SECONDARY,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String = "Salvando…",
    compact: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    // Na TV o botão desligado ("Carregando fontes…") segura o foco do controle e mostra o anel,
    // para o foco não pular sozinho para o vizinho. O clique continua travado.
    val tv = LocalIsTv.current
    val showFocus = focused && (enabled || tv)
    val s = pressFocusScale(pressed && enabled, showFocus, 1.05f)
    val bg = when {
        !enabled -> Surface2
        tone == ButtonTone.PRIMARY -> FocusSurface
        tone == ButtonTone.ON -> AccentSoft
        tone == ButtonTone.DANGER -> Danger.copy(alpha = .9f)
        else -> Surface2
    }
    val fg = when {
        !enabled -> TextTertiary
        tone == ButtonTone.PRIMARY -> OnFocusSurface
        tone == ButtonTone.DANGER -> Color.White
        else -> TextPrimary
    }
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .layerScale(s)
            .heightIn(min = if (compact) 48.dp else 52.dp)
            .clip(shape)
            .background(bg)
            .then(if (showFocus) Modifier.border(3.dp, if (tone == ButtonTone.PRIMARY && enabled) Accent else FocusRing, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (tv && !(enabled && !busy)) Modifier.semantics { disabled() } else Modifier)
            .clickable(
                interactionSource = src, indication = null, enabled = tv || (enabled && !busy), role = Role.Button,
                onClick = { if (enabled && !busy) onClick() },
            )
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            SpinningIcon(tint = fg, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (busy) busyLabel else text, color = fg, fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = if (tv) 1 else 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botão principal branco (Assistir). Mesmo desenho do [AotvButton] PRIMARY. */
@Composable
fun AotvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.PlayArrow,
    enabled: Boolean = true,
) = AotvButton(text, onClick, modifier, icon, ButtonTone.PRIMARY, enabled)

// ── ESQUELETO ───────────────────────────────────────────────────────────────

/** Brilho que corre da direita para a esquerda (.skel do webOS). Parado com animação reduzida. */
@Composable
fun skeletonBrush(): Brush {
    val reduced = rememberReducedMotion()
    val x = if (reduced) 0.5f else rememberInfiniteTransition(label = "sk").animateFloat(
        1f, 0f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)), label = "skX",
    ).value
    val w = 1200f
    val start = x * w * 2 - w
    return Brush.linearGradient(
        listOf(BackgroundCard2, Surface2, BackgroundCard2),
        start = Offset(start, 0f), end = Offset(start + w, 0f),
    )
}

@Composable
fun SkeletonBox(modifier: Modifier, radius: Dp = 14.dp, brush: Brush = skeletonBrush()) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(brush))
}

/** Esqueleto de fileira de pôsteres enquanto o conteúdo chega. */
@Composable
fun SkeletonRail(modifier: Modifier = Modifier, count: Int = 6, cardWidth: Dp = 128.dp) {
    val brush = skeletonBrush()
    val pad = pagePadding()
    Column(modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.padding(start = pad, bottom = 12.dp).size(width = 160.dp, height = 20.dp), 6.dp, brush)
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(count) { SkeletonBox(Modifier.width(cardWidth).aspectRatio(2f / 3f), 14.dp, brush) }
        }
    }
}

// ── PAINEL LATERAL ──────────────────────────────────────────────────────────

/**
 * Painel que entra pela direita (.side-panel). Em tela estreita ocupa a largura toda.
 * Tocar fora fecha.
 */
@Composable
fun SidePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subColor: Color = TextTertiary,
    titleTrailing: (@Composable () -> Unit)? = null,
    width: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val touch = !LocalIsTv.current
    val shortWindow = touch && LocalConfiguration.current.screenHeightDp < 480
    Box(
        modifier.fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .15f), Color.Black.copy(alpha = .7f))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Sem foco próprio. Quem abriu o painel (o player, na TV) continua dono do D-pad.
            .focusProperties { canFocus = false }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = width).fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(0.dp))
                .background(PanelBackground)
                .focusProperties { canFocus = false }
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(start = 18.dp, end = 18.dp, top = if (shortWindow) 12.dp else 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Inter,
                    lineHeight = if (touch) 28.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = if (touch) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = touch))
                titleTrailing?.invoke()
                if (touch) GlassCircleButton(Icons.Default.Close, onDismiss, size = 48.dp, contentDescription = "Fechar painel")
            }
            if (!sub.isNullOrBlank()) Text(sub, color = subColor, fontSize = 13.sp, fontFamily = Inter)
            content()
        }
    }
}

// ── LINHA DE AJUSTE E RESUMO ────────────────────────────────────────────────

/**
 * Linha de configuração (.set-row). Ícone em caixa, título, linha de baixo e algo à direita.
 * Com [busy] o ícone gira e o toque fica travado. Dentro de um [SettingsGroup], use
 * [grouped] (sem fundo próprio) e [divider] (divisória acima, menos na primeira linha).
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    busy: Boolean = false,
    iconTint: Color = TextPrimary,
    trailing: (@Composable () -> Unit)? = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary) },
    onClick: (() -> Unit)? = null,
    grouped: Boolean = false,
    divider: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth()) {
        if (grouped && divider) Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(BorderSubtle))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (grouped) {
                        // Sem fundo próprio; só o foco acende (anel branco + fundo suave).
                        if (focused) Modifier.clip(RoundedCornerShape(10.dp)).background(AccentSoft)
                            .border(2.dp, FocusRing, RoundedCornerShape(10.dp)) else Modifier
                    } else {
                        Modifier.clip(shape)
                            .background(if (focused) AccentSoft else BackgroundCard2)
                            .then(if (focused) Modifier.border(2.dp, FocusRing, shape) else Modifier)
                    }
                )
                .onFocusChanged { focused = it.isFocused }
                .then(if (onClick != null) Modifier.pressScale(src, 0.98f).clickable(src, null, enabled = !busy, role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                if (busy) SpinningIcon(tint = iconTint, size = 22.dp) else Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val s = if (busy) "Salvando…" else sub
                if (!s.isNullOrBlank()) Text(s, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = Inter, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) Box(Modifier.padding(start = 10.dp)) { trailing() }
        }
    }
}

/** Cartão agrupado das Configurações (estilo Nuvio): várias linhas num só cartão arredondado. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BackgroundCard2)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/** Rótulo de grupo das configurações (.set-section). */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier) {
    Eyebrow(title, modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
}

/** Cartão de resumo com degradê azulado (.set-summary e .store-detail). */
@Composable
fun SummaryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(AccentSoft, BackgroundCard2), start = Offset(0f, 0f), end = Offset(600f, 900f)))
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(20.dp),
        content = content,
    )
}

/** Linha rótulo e valor do resumo (.sum-row). */
@Composable
fun SummaryRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp).semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, fontFamily = Inter)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}
