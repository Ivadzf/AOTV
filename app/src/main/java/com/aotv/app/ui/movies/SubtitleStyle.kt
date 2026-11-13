package com.aotv.app.ui.movies

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.media3.ui.CaptionStyleCompat
import kotlin.math.roundToInt

// Personalização de legenda (v1.9.0): cor do texto, tamanho, fonte, opacidade do
// fundo e contorno. A computação do estilo mora aqui, em funções puras, e o ponto
// onde o CaptionStyleCompat é construído (MoviePlayerScreen) só chama
// [buildCaptionStyle]. Os padrões preservam o comportamento da v1.8.1: texto
// branco, fundo preto com opacidade 0,78, contorno quando não há fundo, sombra 8
// na legenda externa, `setApplyEmbeddedStyles(false)` no player (não mexer).

/** Cores do texto da legenda (ARGB). Branco é o padrão, igual à v1.8.1. */
internal val SUB_COLORS: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "Branco",
    0xFFFFFF00.toInt() to "Amarelo",
    0xFF00FFFF.toInt() to "Ciano",
    0xFF00FF00.toInt() to "Verde",
)

/** Rótulo da cor, ou "Branco" quando o valor guardado não está na lista. */
internal fun subColorLabel(color: Int): String =
    SUB_COLORS.firstOrNull { it.first == color }?.second ?: "Branco"

/** Próxima cor da lista, ciclando (toque no painel do player). */
internal fun nextSubColor(color: Int): Int {
    val i = SUB_COLORS.indexOfFirst { it.first == color }
    return SUB_COLORS[(i + 1) % SUB_COLORS.size].first
}

/** Fontes da legenda. "default" é o padrão (a fonte do sistema). */
internal val SUB_FONTS: List<Pair<String, String>> = listOf(
    "default" to "Padrão",
    "monospace" to "Monoespaçada",
    "serif" to "Serifada",
    "sans" to "Sem serifa",
)

/** Rótulo da fonte, ou "Padrão" quando a chave guardada não está na lista. */
internal fun subFontLabel(font: String): String =
    SUB_FONTS.firstOrNull { it.first == font }?.second ?: "Padrão"

/** Próxima fonte da lista, ciclando (toque no painel do player). */
internal fun nextSubFont(font: String): String {
    val i = SUB_FONTS.indexOfFirst { it.first == font }
    return SUB_FONTS[(i + 1) % SUB_FONTS.size].first
}

/** Família Compose da fonte (legenda externa desenhada pelo app). */
internal fun subFontFamily(font: String): FontFamily = when (font) {
    "monospace" -> FontFamily.Monospace
    "serif" -> FontFamily.Serif
    "sans" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

/** Typeface da fonte (legenda embutida, via CaptionStyleCompat). null = padrão. */
internal fun subTypeface(font: String): Typeface? = when (font) {
    "monospace" -> Typeface.MONOSPACE
    "serif" -> Typeface.SERIF
    "sans" -> Typeface.SANS_SERIF
    else -> null
}

/**
 * Opacidades do fundo da legenda. 0,78 é o padrão — o mesmo valor da v1.8.1
 * na legenda externa; a embutida usava 0xB3 (0,70) e passa a usar 0,78 também,
 * unificando as duas.
 */
internal val SUB_BG_OPACITIES: List<Pair<Float, String>> = listOf(
    0.5f to "50%",
    0.65f to "65%",
    0.78f to "78%",
    0.9f to "90%",
)

/** Rótulo da opacidade ("78%"); valor fora da lista mostra a porcentagem direta. */
internal fun subBgOpacityLabel(opacity: Float): String =
    SUB_BG_OPACITIES.firstOrNull { it.first == opacity }?.second ?: "${(opacity * 100).roundToInt()}%"

/** Próxima opacidade da lista, ciclando (toque no painel do player). */
internal fun nextSubBgOpacity(opacity: Float): Float {
    val i = SUB_BG_OPACITIES.indexOfFirst { it.first == opacity }
    return SUB_BG_OPACITIES[(i + 1) % SUB_BG_OPACITIES.size].first
}

/**
 * Cor de fundo preta com a opacidade escolhida. Sem fundo, transparente.
 * 0,78 → alfa 199 (0xC7).
 */
internal fun subtitleBgColor(withBg: Boolean, bgOpacity: Float): Int {
    if (!withBg) return android.graphics.Color.TRANSPARENT
    val a = (bgOpacity.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(0, 255)
    return a shl 24
}

/**
 * Tipo de borda da legenda embutida. Com fundo não há borda; sem fundo, o
 * contorno ligado (padrão) dá OUTLINE — o comportamento da v1.8.1 — e desligado
 * tira a borda.
 */
internal fun subtitleEdgeType(withBg: Boolean, outline: Boolean): Int =
    if (withBg) CaptionStyleCompat.EDGE_TYPE_NONE
    else if (outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE
    else CaptionStyleCompat.EDGE_TYPE_NONE

/**
 * Estilo da legenda embutida — o único lugar que constrói o CaptionStyleCompat.
 * Cor do texto escolhida, fundo com a opacidade escolhida, contorno conforme a
 * pref, borda sempre preta, fonte escolhida (null = padrão do sistema).
 */
internal fun buildCaptionStyle(
    color: Int,
    withBg: Boolean,
    bgOpacity: Float,
    outline: Boolean,
    font: String,
): CaptionStyleCompat = CaptionStyleCompat(
    color,
    subtitleBgColor(withBg, bgOpacity),
    android.graphics.Color.TRANSPARENT,
    subtitleEdgeType(withBg, outline),
    android.graphics.Color.BLACK,
    subTypeface(font),
)

/** Cor Compose a partir do ARGB guardado (legenda externa). */
internal fun subtitleTextColor(color: Int): Color = Color(color)
