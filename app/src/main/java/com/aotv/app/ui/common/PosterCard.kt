package com.aotv.app.ui.common

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.aotv.app.data.local.DayRanks
import com.aotv.app.data.local.PosterInfo
import com.aotv.app.data.local.SourceQuality
import com.aotv.app.data.local.dayString
import com.aotv.app.data.local.fresh
import com.aotv.app.data.local.posterKey
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Gold
import com.aotv.app.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Ranking, resumo das fontes e cor dos pôsteres. Nulo em prévia, e aí o cartão sai sem eles. */
val LocalPosterInfo = staticCompositionLocalOf<PosterInfo?> { null }

/**
 * Ranking de hoje e resumo das fontes, lidos uma vez na raiz do app. Antes cada pôster abria dois coletores
 * (um por fluxo), e uma tela cheia tem dezenas de pôsteres.
 */
val LocalPosterRanks = staticCompositionLocalOf<State<DayRanks?>?> { null }
val LocalPosterQuality = staticCompositionLocalOf<State<Map<String, SourceQuality>>?> { null }

// ── Regras da etiqueta e da faixa (iguais ao webOS) ─────────────────────────

internal val GENRE_SHORT = mapOf(
    28 to "Ação", 12 to "Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime", 99 to "Documentário",
    18 to "Drama", 10751 to "Família", 14 to "Fantasia", 36 to "História", 27 to "Terror", 10402 to "Música",
    9648 to "Mistério", 10749 to "Romance", 878 to "Ficção", 10770 to "Telefilme", 53 to "Suspense",
    10752 to "Guerra", 37 to "Faroeste", 10759 to "Ação", 10762 to "Infantil", 10763 to "Notícias",
    10764 to "Reality", 10765 to "Ficção", 10766 to "Novela", 10767 to "Talk show", 10768 to "Guerra",
)

internal val LANG_TAG = mapOf(
    "es" to "Espanhol", "fr" to "Francês", "de" to "Alemão", "it" to "Italiano", "ja" to "Japonês",
    "ko" to "Coreano", "zh" to "Chinês", "cn" to "Chinês", "hi" to "Indiano", "ta" to "Indiano",
    "te" to "Indiano", "ml" to "Indiano", "kn" to "Indiano", "th" to "Tailandês", "tr" to "Turco",
    "da" to "Dinamarquês", "sv" to "Sueco", "no" to "Norueguês", "nb" to "Norueguês", "fi" to "Finlandês",
    "pl" to "Polonês", "ru" to "Russo", "nl" to "Holandês", "ar" to "Árabe", "he" to "Hebraico",
    "id" to "Indonésio", "tl" to "Filipino", "uk" to "Ucraniano", "fa" to "Persa", "is" to "Islandês",
)

internal data class PosterTag(val text: String, val top: Boolean = false)

private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** Etiqueta do topo. Ranking de hoje, estreia, idioma original ou nota alta, nessa ordem. */
internal fun posterTag(item: MovieItem, rank: Int, today: String, monthAgo: String): PosterTag? {
    if (rank > 0) return PosterTag("#$rank Hoje", top = rank <= 3)
    val d = item.releaseDate.takeIf { DATE.matches(it) }
    if (d != null && d > today) return PosterTag("Em breve")
    if (d != null && d > monthAgo) return PosterTag(if (item.type == MediaType.SERIES) "Série nova" else "Novo")
    val lang = if (item.originalLanguage == "pt") {
        if ("PT" in item.originCountry) "Português" else "Nacional"
    } else LANG_TAG[item.originalLanguage]
    if (lang != null) return PosterTag(lang)
    if (item.rating >= 8 && item.voteCount >= 500) return PosterTag("Imperdível")
    return null
}

/** Ano e gênero curto da faixa de baixo. Sem nenhum dos dois fica Filme ou Série. */
internal fun stripParts(item: MovieItem): List<String> {
    val genre = item.genreIds.firstNotNullOfOrNull { GENRE_SHORT[it] }
    return listOfNotNull(item.year.ifBlank { null }, genre)
        .ifEmpty { listOf(if (item.type == MediaType.SERIES) "Série" else "Filme") }
}

internal fun ratingText(r: Double) = String.format(Locale.US, "%.1f", r).replace('.', ',')

/** Rótulo da resolução. Abaixo de 720p não vira selo. */
internal fun qualityLabel(q: Int) = when {
    q >= 2160 -> "4K"
    q >= 1440 -> "2K"
    q >= 1080 -> "1080p"
    q >= 720 -> "720p"
    else -> null
}

/**
 * Matiz dominante (média pesada pela saturação), de 0 a 359. -1 é pôster sem cor ou com cores
 * que se anulam (azul com laranja), que ficam no cinza claro.
 */
internal fun hueOf(argb: IntArray): Int {
    var x = 0.0
    var y = 0.0
    var w = 0.0
    for (p in argb) {
        val r = (p shr 16 and 0xFF) / 255.0
        val g = (p shr 8 and 0xFF) / 255.0
        val b = (p and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val c = max - minOf(r, g, b)
        if (c < 0.1) continue
        val sector = when (max) {
            r -> (g - b) / c
            g -> (b - r) / c + 2
            else -> (r - g) / c + 4
        }
        val a = sector * PI / 3
        val wt = c * max
        x += cos(a) * wt
        y += sin(a) * wt
        w += wt
    }
    if (argb.isEmpty() || w / argb.size < 0.05 || sqrt(x * x + y * y) < w * 0.4) return -1
    val hue = (atan2(y, x) * 180 / PI).roundToInt()
    return if (hue < 0) hue + 360 else hue
}

/** Cor pastel da etiqueta e da faixa. Texto escuro por cima passa de 9:1 de contraste. */
internal fun tintColor(hue: Int?): Color =
    if (hue == null || hue < 0) Color.hsl(220f, 0.09f, 0.84f) else Color.hsl(hue.toFloat(), 0.58f, 0.80f)

/** Matiz do pôster reduzido a 8 por 12. null quando o bitmap não deixa ler. */
private fun posterHue(bmp: Bitmap): Int? = runCatching {
    val small = Bitmap.createScaledBitmap(bmp, 8, 12, true)
    val px = IntArray(96)
    small.getPixels(px, 0, 8, 0, 0, 8, 12)
    if (small !== bmp) small.recycle()
    hueOf(px)
}.getOrNull()

// ── Cartão ──────────────────────────────────────────────────────────────────

private val INK = Color(0xFF121316)
private val TOP_INK = Color(0xFF062019)
private val CHIP = RoundedCornerShape(5.dp)
private val TAG_SHAPE = RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp)

internal val DolbyIcon: ImageVector by lazy {
    ImageVector.Builder("dolby", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M1 5h3.5a7 7 0 0 1 0 14H1zM23 5h-3.5a7 7 0 0 0 0 14H23z"), fill = SolidColor(Color.White))
        .build()
}

/**
 * Pôster do catálogo igual ao do webOS. Etiqueta no topo, faixa de ano, gênero e nota na cor
 * tirada do pôster e, no canto, o que as fontes dos addons têm (4K, Dolby, HDR, DUB). [badge]
 * troca a etiqueta por um selo da tela e tira os selos de qualidade, [typeBadge] mostra Filme ou
 * Série e [showTag] falso tira a etiqueta (franquia, que tem o número no canto). Título embaixo
 * quando [caption] não é nulo.
 */
@Composable
fun PosterCard(
    item: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = item.title,
    sub: String? = null,
    highlight: Boolean = false,
    badge: String? = null,
    typeBadge: Boolean = false,
    showTag: Boolean = true,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    /** Falso esconde a etiqueta do topo e os selos de qualidade (Fase B, modo compacto). */
    showBadges: Boolean = true,
    /** Raio dos cantos do pôster (Fase B, configurável na página Layout). */
    cornerRadius: Dp = 14.dp,
) {
    val visual = rememberPosterVisual(item, badge, typeBadge, showTag, showBadges = showBadges)
    val unit = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f).dp
    Column(modifier) {
        MediaPosterCard(
            title = item.title,
            imageUrl = item.posterUrl.ifBlank { item.backdropUrl }.ifBlank { null },
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            cleanPoster = true,
            progress = progress,
            imageBottom = unit * 22,
            onImage = visual.onImage,
            cornerRadius = cornerRadius,
        ) {
            PosterDecorations(item, visual, unit = unit, imageBottom = unit * 22)
        }
        if (caption != null) PosterCaption(caption, sub, highlight)
    }
}

/**
 * Matiz dominante do pôster (0–359, null sem cor ou sem leitura). A cor sai de uma
 * leitura de 8 por 12 pixels, vinda do cache em disco, que nunca é desenhada.
 * O cartão parado na tela usa a mesma leitura em [rememberPosterVisual].
 */
@Composable
internal fun rememberPosterHue(item: MovieItem): Int? {
    val info = LocalPosterInfo.current
    val key = posterKey(item)
    var hue by remember(key) { mutableStateOf(if (key.isEmpty()) null else info?.tint(key)) }
    // Só pôster do TMDB e só enquanto não tem cor salva.
    val readHue = remember(key) { hue == null && info != null && key.isNotEmpty() && item.posterUrl.startsWith("https://image.tmdb.org/") }
    val context = LocalContext.current
    // Sem pôster exibido aqui para esperar (o destaque mostra o backdrop): a leitura
    // de 8 por 12 pixels baixa sozinha e grava no cache, para o cartão reaproveitar.
    if (readHue) LaunchedEffect(key) {
        val small = context.imageLoader.execute(
            ImageRequest.Builder(context).data(item.posterUrl).size(8, 12).precision(Precision.INEXACT).scale(Scale.FILL)
                .allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).build(),
        )
        val bmp = (small.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val h = withContext(Dispatchers.Default) { posterHue(bmp) } ?: return@LaunchedEffect
        hue = h
        info?.putTint(key, h)
    }
    return hue
}

internal data class PosterVisual(
    val tint: Color,
    val tag: PosterTag?,
    val quality: SourceQuality?,
    val onImage: ((Drawable) -> Unit)?,
)

@Composable
internal fun rememberPosterVisual(
    item: MovieItem,
    badge: String? = null,
    typeBadge: Boolean = false,
    showTag: Boolean = true,
    requestQuality: Boolean = true,
    /** Falso esconde a etiqueta automática do topo e os selos de qualidade (Fase B, compacto). */
    showBadges: Boolean = true,
): PosterVisual {
    val info = LocalPosterInfo.current
    val key = posterKey(item)
    val ranks = LocalPosterRanks.current?.value
    val qualityMap = LocalPosterQuality.current
    // O cartão só recompõe quando o resumo dele muda, e não a cada resumo novo de qualquer título.
    val quality by remember(key, qualityMap, badge, showBadges) {
        derivedStateOf { qualityMap?.value?.get(key)?.takeIf { showBadges && badge == null && it.fresh(System.currentTimeMillis()) } }
    }

    var hue by remember(key) { mutableStateOf(if (key.isEmpty()) null else info?.tint(key)) }
    // Só pôster do TMDB e só enquanto não tem cor salva.
    val readHue = remember(key) { hue == null && info != null && key.isNotEmpty() && item.posterUrl.startsWith("https://image.tmdb.org/") }
    var loaded by remember(key) { mutableStateOf(false) }
    val context = LocalContext.current
    // O pôster que aparece na tela pode ser bitmap de hardware (sem subir textura na hora de desenhar). A cor sai de uma
    // leitura de 8 por 12 pixels, vinda do cache em disco, que nunca é desenhada.
    // A Início usa a mesma leitura sozinha em rememberPosterHue (fundo dinâmico); aqui ela
    // espera o pôster exibido carregar, para a leitura sair do cache e não baixar de novo.
    if (readHue) LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        val small = context.imageLoader.execute(
            ImageRequest.Builder(context).data(item.posterUrl).size(8, 12).precision(Precision.INEXACT).scale(Scale.FILL)
                .allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED).build(),
        )
        val bmp = (small.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val h = withContext(Dispatchers.Default) { posterHue(bmp) } ?: return@LaunchedEffect
        hue = h
        info?.putTint(key, h)
    }
    // Cartão parado na tela por 0,7 s pede a leitura das fontes. Saiu antes da vez, sai da fila.
    // Sem selos (compacto) nem pede: a leitura não aparece em lugar nenhum.
    if (showBadges && requestQuality && info != null && badge == null && key.isNotEmpty()) LaunchedEffect(key) {
        delay(700)
        info.want(item)
        try { awaitCancellation() } finally { info.unwant(item) }
    }

    val days = remember { dayString() to dayString(30) }
    val tag = when {
        !showTag -> null
        badge != null -> PosterTag(badge)
        typeBadge -> PosterTag(if (item.type == MediaType.SERIES) "Série" else "Filme")
        !showBadges -> null
        else -> posterTag(item, info?.rankOf(ranks, item) ?: 0, days.first, days.second)
    }
    // A cor da faixa entra na hora. Animada, recompunha o cartão inteiro a cada quadro por 300 ms.
    val tint = tintColor(hue)
    return PosterVisual(tint, tag, quality, if (readHue) ({ _ -> loaded = true }) else null)
}

@Composable
internal fun BoxScope.PosterDecorations(
    item: MovieItem,
    visual: PosterVisual,
    unit: Dp = 1.dp,
    textUnit: TextUnit = 1.sp,
    imageBottom: Dp = 0.dp,
) {
    visual.tag?.let { TagChip(it, visual.tint, Modifier.align(Alignment.TopCenter), unit, textUnit) }
    visual.quality?.let { QualityChips(it, Modifier.align(Alignment.TopEnd), unit, textUnit) }
    if (item.rating > 0) RatingBadge(item.rating, Modifier.align(Alignment.BottomStart).padding(start = unit * 6, bottom = imageBottom + unit * 6), unit, textUnit)
    Strip(item, visual.tint, Modifier.align(Alignment.BottomCenter), unit, textUnit)
}

/** Selo de nota sobre o pôster (estilo Nuvio): estrela dourada + nota num chip escuro. */
@Composable
private fun RatingBadge(rating: Double, modifier: Modifier, unit: Dp, textUnit: TextUnit) {
    Row(
        modifier.background(Color.Black.copy(alpha = .72f), CHIP).padding(horizontal = unit * 6, vertical = unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.size(unit * 10))
        Spacer(Modifier.width(unit * 3))
        Text(
            ratingText(rating), color = Color.White, fontSize = textUnit * 10.5f, lineHeight = textUnit * 12f,
            fontWeight = FontWeight.ExtraBold, fontFamily = Inter,
        )
    }
}

@Composable
private fun TagChip(tag: PosterTag, tint: Color, modifier: Modifier, unit: Dp, textUnit: TextUnit) {
    // Até 86% da largura, centrada, e o texto longo termina em reticências.
    Box(modifier.fillMaxWidth(0.86f), contentAlignment = Alignment.TopCenter) {
        Text(
            tag.text, color = if (tag.top) TOP_INK else INK, fontSize = textUnit * 10f, lineHeight = textUnit * 12f,
            fontWeight = FontWeight.ExtraBold, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(if (tag.top) Accent else tint, TAG_SHAPE)
                .padding(horizontal = unit * 7, vertical = unit * 2),
        )
    }
}

@Composable
private fun Strip(item: MovieItem, tint: Color, modifier: Modifier, unit: Dp, textUnit: TextUnit) {
    Row(
        modifier.fillMaxWidth().height(unit * 22).background(tint).padding(horizontal = unit * 6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // O ano não encolhe. O gênero encolhe com reticências. A nota saiu para o selo sobre o pôster.
        stripParts(item).forEachIndexed { i, p ->
            if (i > 0) StripDot(unit, textUnit)
            StripText(p, if (i == 0) Modifier else Modifier.weight(1f, fill = false), textUnit)
        }
    }
}

@Composable
private fun StripText(text: String, modifier: Modifier, textUnit: TextUnit) = Text(
    text, color = INK, fontSize = textUnit * 10.5f, lineHeight = textUnit * 12f, fontWeight = FontWeight.Bold, fontFamily = Inter,
    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
)

@Composable
private fun StripDot(unit: Dp, textUnit: TextUnit) = Text(
    "·", color = INK.copy(alpha = .5f), fontSize = textUnit * 10.5f, lineHeight = textUnit * 12f, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(horizontal = unit * 4),
)

@Composable
private fun QualityChips(q: SourceQuality, modifier: Modifier, unit: Dp, textUnit: TextUnit) {
    val res = qualityLabel(q.q) ?: return
    val dolby = q.dolbyVision || q.atmos
    val hdr = q.hdr || q.dolbyVision
    Column(
        modifier.padding(top = unit * 21, end = unit * 5),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(unit * 3),
    ) {
        Text(
            res, color = Color(0xFF0D0D0D), fontSize = textUnit * 10f, lineHeight = textUnit * 12f, fontWeight = FontWeight.ExtraBold,
            fontFamily = Inter,
            modifier = Modifier.background(Color(0xFFF2F2F2), CHIP).padding(horizontal = unit * 5, vertical = unit),
        )
        if (dolby || hdr) Row(
            Modifier.background(Color.Black.copy(alpha = .8f), CHIP).padding(horizontal = unit * 4, vertical = unit * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dolby) Icon(DolbyIcon, "Dolby", tint = Color.White, modifier = Modifier.size(unit * 13))
            if (dolby && hdr) Spacer(Modifier.width(unit * 3))
            if (hdr) Text(
                "HDR", color = Color.White, fontSize = textUnit * 8f, lineHeight = textUnit * 10f, fontWeight = FontWeight.ExtraBold,
                fontFamily = Inter, letterSpacing = textUnit * .4f,
                modifier = Modifier.border(unit, Color.White.copy(alpha = .92f), RoundedCornerShape(unit * 3)).padding(horizontal = unit * 3),
            )
        }
        if (q.ptAudio) Text(
            "DUB", color = Color.White, fontSize = textUnit * 8.5f, lineHeight = textUnit * 10f, fontWeight = FontWeight.ExtraBold,
            fontFamily = Inter, letterSpacing = textUnit * .4f,
            modifier = Modifier.background(Color.Black.copy(alpha = .8f), CHIP).padding(horizontal = unit * 4, vertical = unit * 2),
        )
    }
}
