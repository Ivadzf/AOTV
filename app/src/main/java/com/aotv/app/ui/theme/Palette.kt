package com.aotv.app.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Cor editável do tema personalizado (Lote 3, v1.11.0). Só 6 cores são
 * editáveis — o resto da paleta é DERIVADO delas (ver [derivedCustomPalette]):
 * escolher 6 cores já dá um tema inteiro sem expor ~35 campos ao usuário.
 * Guardado como ARGB Int (0xAARRGGBB) p/ serializar fácil em JSON no
 * [com.aotv.app.data.local.PlatformPrefs].
 *
 * Chaves (estáveis, guardadas no JSON): "accent", "accentSecondary",
 * "background", "backgroundElevated", "backgroundCard", "textPrimary".
 */
data class CustomThemeColors(
    val accent: Int = 0xFF00C2B8.toInt(),
    val accentSecondary: Int = 0xFF9E1B1B.toInt(),
    val background: Int = 0xFF0D0D0D.toInt(),
    val backgroundElevated: Int = 0xFF191919.toInt(),
    val backgroundCard: Int = 0xFF222222.toInt(),
    val textPrimary: Int = 0xFFF5F7F8.toInt(),
) {
    /** Valor da chave; chave desconhecida devolve o destaque. */
    fun color(key: String): Int = when (key) {
        "accent" -> accent
        "accentSecondary" -> accentSecondary
        "background" -> background
        "backgroundElevated" -> backgroundElevated
        "backgroundCard" -> backgroundCard
        "textPrimary" -> textPrimary
        else -> accent
    }

    /** Cópia com uma cor trocada (chave desconhecida = sem mudança). */
    fun withColor(key: String, argb: Int): CustomThemeColors = when (key) {
        "accent" -> copy(accent = argb)
        "accentSecondary" -> copy(accentSecondary = argb)
        "background" -> copy(background = argb)
        "backgroundElevated" -> copy(backgroundElevated = argb)
        "backgroundCard" -> copy(backgroundCard = argb)
        "textPrimary" -> copy(textPrimary = argb)
        else -> this
    }

    /** JSON `{"accent":"#00C2B8",...}` (sempre opaco: o alfa não é editável). */
    fun toJson(): String {
        val o = org.json.JSONObject()
        CUSTOM_THEME_KEYS.forEach { o.put(it, formatHexColor(color(it))) }
        return o.toString()
    }

    companion object {
        /** As 6 chaves, na ordem do editor. */
        val CUSTOM_THEME_KEYS = listOf(
            "accent", "accentSecondary", "background",
            "backgroundElevated", "backgroundCard", "textPrimary",
        )

        /** Rótulo de cada chave no editor. */
        fun label(key: String): String = when (key) {
            "accent" -> "Destaque"
            "accentSecondary" -> "Destaque secundário"
            "background" -> "Fundo"
            "backgroundElevated" -> "Fundo elevado"
            "backgroundCard" -> "Cartões"
            "textPrimary" -> "Texto principal"
            else -> key
        }

        /** Lê o JSON guardado; qualquer erro (ou null) volta ao padrão. */
        fun fromJson(raw: String?): CustomThemeColors = runCatching {
            val o = org.json.JSONObject(raw ?: return CustomThemeColors())
            var out = CustomThemeColors()
            CUSTOM_THEME_KEYS.forEach { key ->
                parseHexColor(o.optString(key, ""))?.let { out = out.withColor(key, it) }
            }
            out
        }.getOrDefault(CustomThemeColors())
    }
}

/** Cores do tema Padrão nas 6 chaves editáveis (o personalizado nasce igual a ele). */
val DEFAULT_CUSTOM_COLORS = CustomThemeColors()

/** "#RRGGBB" a partir de um ARGB Int (alfa ignorado: as cores editáveis são opacas). */
fun formatHexColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * "#RRGGBB" (com ou sem "#", maiúsculo ou minúsculo) → ARGB opaco.
 * Null quando não são 6 dígitos hexadecimais — o editor só aplica quando válido.
 */
fun parseHexColor(hex: String): Int? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF000000.toInt() or clean.toInt(16))
}

/** Mistura [a] com [b]: t=0 devolve [a], t=1 devolve [b]. */
fun mixColors(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/**
 * Texto (escuro 0xFF0A0A0A ou branco) com mais contraste sobre [background].
 * Razão WCAG: escuro = (L+0,05)/0,05, branco = 1,05/(L+0,05); o escuro vence
 * quando L > ~0,18. Usado p/ o onAccent do tema personalizado.
 */
fun bestOnColor(background: Color): Color {
    val lum = background.luminance()
    val contrastDark = (lum + 0.05f) / 0.05f
    val contrastWhite = 1.05f / (lum + 0.05f)
    return if (contrastDark >= contrastWhite) Color(0xFF0A0A0A) else Color(0xFFFFFFFF)
}

/**
 * Paleta completa a partir das 6 cores editáveis (Lote 3, v1.11.0).
 * REGRAS DE DERIVAÇÃO (documentadas p/ quem for mexer aqui):
 * - accentFill = destaque escurecido 12%; accentDark = escurecido 40%.
 * - accentSoft = fundo misturado com 15% de destaque (o "brilho" suave do tema).
 * - backgroundCard2 = fundo clareado 4%; surface2 = fundo elevado clareado 10%.
 * - textSecondary = texto misturado 22% p/ o fundo; textTertiary = 38% p/ o fundo.
 * - border = fundo clareado 16% (tema escuro) / escurecido 16% (tema claro);
 *   borderSubtle = texto principal com 6% de alfa.
 * - glassBg = fundo com alfa 0xB8; panelBackground = fundo com alfa 0xF7;
 *   overlayScrim = preto 0x99; hintChipBackground = preto 0x88; hintChipText = branco 0xAA.
 * - focusBackground = fundo clareado 14%; searchFocused = fundo com 18% de destaque.
 * - onAccent = o que tem MAIS contraste sobre o destaque (texto escuro
 *   0xFF0A0A0A ou branco), pela razão de contraste WCAG: escuro vence quando
 *   a luminância (linearizada) do destaque passa de ~0,18. Com as cores do
 *   Padrão dá texto escuro sobre o turquesa, igual ao tema Padrão.
 * - isLight = luminância do fundo > 0,55. Tema claro usa os valores de foco do
 *   tema Branco (superfície escura, anel escuro, preenchimento preto); tema
 *   escuro usa os do Padrão. O usuário escolhe o texto principal, então um
 *   fundo claro com texto claro fica legível só se ele trocar o texto também.
 * - liveRed/danger/dangerText/gold/ok NÃO são derivados: são as cores de estado
 *   do Padrão, fixas em todo tema personalizado (não editáveis).
 */
fun derivedCustomPalette(colors: CustomThemeColors): AotvPalette {
    val accent = Color(colors.accent)
    val accentSecondary = Color(colors.accentSecondary)
    val background = Color(colors.background)
    val backgroundElevated = Color(colors.backgroundElevated)
    val backgroundCard = Color(colors.backgroundCard)
    val textPrimary = Color(colors.textPrimary)
    val isLight = background.luminance() > 0.55f
    val white = Color.White
    val black = Color.Black
    val lightenTarget = if (isLight) black else white
    return AotvPalette(
        id = "custom",
        name = "Personalizado",
        isLight = isLight,
        background = background,
        backgroundElevated = backgroundElevated,
        backgroundCard = backgroundCard,
        backgroundCard2 = mixColors(background, lightenTarget, 0.04f),
        surface2 = mixColors(backgroundElevated, lightenTarget, 0.10f),
        accent = accent,
        accentFill = mixColors(accent, black, 0.12f),
        accentDark = mixColors(accent, black, 0.40f),
        accentSoft = mixColors(background, accent, 0.15f),
        accentSecondary = accentSecondary,
        liveRed = Color(0xFFE5484D),
        danger = Color(0xFFE5484D),
        dangerText = Color(0xFFFF8C8F),
        gold = Color(0xFFF4CC75),
        ok = Color(0xFF3DDC84),
        textPrimary = textPrimary,
        textSecondary = mixColors(textPrimary, background, 0.22f),
        textTertiary = mixColors(textPrimary, background, 0.38f),
        textOnLight = Color(0xFF0A0A0A),
        border = mixColors(background, lightenTarget, 0.16f),
        borderSubtle = textPrimary.copy(alpha = 0.06f),
        glassBg = background.copy(alpha = 0xB8 / 255f),
        overlayScrim = Color(0x99000000),
        hintChipBackground = Color(0x88000000),
        hintChipText = Color(0xAAFFFFFF),
        panelBackground = background.copy(alpha = 0xF7 / 255f),
        focusBackground = mixColors(background, lightenTarget, 0.14f),
        focusSurface = if (isLight) Color(0xFF161616) else Color(0xFFFFFFFF),
        onFocusSurface = if (isLight) Color(0xFFFFFFFF) else Color(0xFF0A0A0A),
        focusRing = if (isLight) Color(0xFF1A1A1A) else Color(0xFFFFFFFF),
        onAccent = bestOnColor(accent),
        fillSubtle = if (isLight) Color(0x14000000) else Color(0x14FFFFFF),
        fillTint = if (isLight) Color(0xFF000000) else Color(0xFFFFFFFF),
        searchFocused = mixColors(background, accent, 0.18f),
    )
}

/**
 * Paleta de cores do AOTV (Fase A, Frente 3). Uma paleta cobre tudo que antes
 * eram os vals soltos de [Color.kt]: fundos, destaque, textos, bordas, estados
 * e vidro. As telas continuam lendo os nomes de sempre — eles viraram getters
 * sobre a paleta ativa (ver [Color.kt]) — então trocar de tema não mexe em
 * nenhuma tela.
 *
 * [isLight] verdadeiro usa [androidx.compose.material3.lightColorScheme] no
 * [AotvTheme]; falso usa darkColorScheme. [textOnLight] é texto escuro sobre
 * superfícies que são claras em TODOS os temas (botão branco do player, chip
 * sobre o vídeo); não confundir com [onFocusSurface], que é o texto sobre a
 * superfície de foco/primário (branca nos temas escuros, escura no Branco).
 */
data class AotvPalette(
    val id: String,
    val name: String,
    val isLight: Boolean,
    // Fundos
    val background: Color,
    val backgroundElevated: Color,
    val backgroundCard: Color,
    val backgroundCard2: Color,
    val surface2: Color,
    // Destaque
    val accent: Color,
    val accentFill: Color,
    val accentDark: Color,
    val accentSoft: Color,
    val accentSecondary: Color,
    // Estado
    val liveRed: Color,
    val danger: Color,
    val dangerText: Color,
    val gold: Color,
    val ok: Color,
    // Texto
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnLight: Color,
    // Bordas
    val border: Color,
    val borderSubtle: Color,
    // Vidro e sobreposições
    val glassBg: Color,
    val overlayScrim: Color,
    val hintChipBackground: Color,
    val hintChipText: Color,
    val panelBackground: Color,
    // Foco e superfícies claras
    val focusBackground: Color,
    val focusSurface: Color,
    val onFocusSurface: Color,
    val focusRing: Color,
    val onAccent: Color,
    val fillSubtle: Color,
    val fillTint: Color,
    val searchFocused: Color,
) {
    companion object {
        /** Padrão: as cores atuais do AOTV (vermelho escuro + turquesa, fundo escuro). */
        val Default = AotvPalette(
            id = "default",
            name = "Padrão",
            isLight = false,
            background = Color(0xFF0D0D0D),
            backgroundElevated = Color(0xFF191919),
            backgroundCard = Color(0xFF222222),
            backgroundCard2 = Color(0xFF1A1A1A),
            surface2 = Color(0xFF2B2B2B),
            accent = Color(0xFF00C2B8),
            accentFill = Color(0xFF00A69B),
            accentDark = Color(0xFF00796B),
            accentSoft = Color(0xFF0E2A28),
            accentSecondary = Color(0xFF9E1B1B),
            liveRed = Color(0xFFE5484D),
            danger = Color(0xFFE5484D),
            dangerText = Color(0xFFFF8C8F),
            gold = Color(0xFFF4CC75),
            ok = Color(0xFF3DDC84),
            textPrimary = Color(0xFFF5F7F8),
            textSecondary = Color(0xFFC3C5C9),
            textTertiary = Color(0xFFA0A3AA),
            textOnLight = Color(0xFF0A0A0A),
            border = Color(0xFF373737),
            borderSubtle = Color(0x0FFFFFFF),
            glassBg = Color(0xB80D0D0D),
            overlayScrim = Color(0x99000000),
            hintChipBackground = Color(0x88000000),
            hintChipText = Color(0xAAFFFFFF),
            panelBackground = Color(0xF7101010),
            focusBackground = Color(0xFF303030),
            focusSurface = Color(0xFFFFFFFF),
            onFocusSurface = Color(0xFF0A0A0A),
            focusRing = Color(0xFFFFFFFF),
            onAccent = Color(0xFF0A0A0A),
            fillSubtle = Color(0x14FFFFFF),
            fillTint = Color(0xFFFFFFFF),
            searchFocused = Color(0xFF2B3644),
        )

        /** Branco: tema claro. Fundos claros, texto escuro, turquesa mais escuro. */
        val White = Default.copy(
            id = "white",
            name = "Branco",
            isLight = true,
            background = Color(0xFFF7F7F5),
            backgroundElevated = Color(0xFFFFFFFF),
            backgroundCard = Color(0xFFFFFFFF),
            backgroundCard2 = Color(0xFFF0F0EC),
            surface2 = Color(0xFFE7E7E1),
            accent = Color(0xFF00897B),
            accentFill = Color(0xFF00796B),
            accentDark = Color(0xFF00695C),
            accentSoft = Color(0xFFD9F0ED),
            liveRed = Color(0xFFC62828),
            danger = Color(0xFFC62828),
            dangerText = Color(0xFFB71C1C),
            gold = Color(0xFF8A6D00),
            ok = Color(0xFF1B7A43),
            textPrimary = Color(0xFF1A1C1E),
            textSecondary = Color(0xFF4A4D52),
            textTertiary = Color(0xFF6E7278),
            border = Color(0xFFD8D8D2),
            borderSubtle = Color(0x14000000),
            glassBg = Color(0xB8FFFFFF),
            panelBackground = Color(0xF7FFFFFF),
            focusBackground = Color(0xFFE0E0DA),
            focusSurface = Color(0xFF161616),
            onFocusSurface = Color(0xFFFFFFFF),
            focusRing = Color(0xFF1A1A1A),
            fillSubtle = Color(0x14000000),
            fillTint = Color(0xFF000000),
            searchFocused = Color(0xFFDCE4EC),
        )

        /** Carmesim: destaque vermelho vivo; secundário vira turquesa. */
        val Crimson = Default.copy(
            id = "crimson",
            name = "Carmesim",
            accent = Color(0xFFE53935),
            accentFill = Color(0xFFD32F2F),
            accentDark = Color(0xFFB71C1C),
            accentSoft = Color(0xFF2E1214),
            accentSecondary = Color(0xFF00C2B8),
            onAccent = Color(0xFFFFFFFF),
        )

        /** Oceano: destaque azul. */
        val Ocean = Default.copy(
            id = "ocean",
            name = "Oceano",
            accent = Color(0xFF1E88E5),
            accentFill = Color(0xFF1976D2),
            accentDark = Color(0xFF0D47A1),
            accentSoft = Color(0xFF10233A),
            onAccent = Color(0xFFFFFFFF),
        )

        /** Violeta: destaque roxo; secundário vira turquesa. */
        val Violet = Default.copy(
            id = "violet",
            name = "Violeta",
            accent = Color(0xFF7E57C2),
            accentFill = Color(0xFF673AB7),
            accentDark = Color(0xFF4527A0),
            accentSoft = Color(0xFF1E1533),
            accentSecondary = Color(0xFF00C2B8),
            onAccent = Color(0xFFFFFFFF),
        )

        /** Esmeralda: destaque verde. */
        val Emerald = Default.copy(
            id = "emerald",
            name = "Esmeralda",
            accent = Color(0xFF2E9E6B),
            accentFill = Color(0xFF1B7A4B),
            accentDark = Color(0xFF145C38),
            accentSoft = Color(0xFF0F241C),
        )

        /** Âmbar: destaque âmbar/laranja; secundário vira azul. */
        val Amber = Default.copy(
            id = "amber",
            name = "Âmbar",
            accent = Color(0xFFF59E0B),
            accentFill = Color(0xFFE8890C),
            accentDark = Color(0xFFB96A00),
            accentSoft = Color(0xFF2B1E08),
            accentSecondary = Color(0xFF1976D2),
        )

        /** Rosa: destaque rosa; secundário vira violeta. */
        val Pink = Default.copy(
            id = "pink",
            name = "Rosa",
            accent = Color(0xFFEC407A),
            accentFill = Color(0xFFD81B60),
            accentDark = Color(0xFFAD1457),
            accentSoft = Color(0xFF2E1220),
            accentSecondary = Color(0xFF7E57C2),
        )

        /** Tema personalizado (Lote 3, v1.11.0): derivado das cores salvas; aqui, com os padrões. */
        val Custom: AotvPalette = derivedCustomPalette(DEFAULT_CUSTOM_COLORS)

        /** Todas as paletas, na ordem do seletor. */
        val all: List<AotvPalette> = listOf(Default, White, Crimson, Ocean, Violet, Emerald, Amber, Pink, Custom)

        /**
         * Paleta pelo id salvo; id desconhecido (ou vazio) volta ao Padrão.
         * [custom] são as 6 cores editáveis salvas — só vale p/ id "custom";
         * null usa os padrões (o seletor mostra o Personalizado com elas).
         */
        fun fromId(id: String, custom: CustomThemeColors? = null): AotvPalette =
            if (id == "custom") derivedCustomPalette(custom ?: DEFAULT_CUSTOM_COLORS)
            else all.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Paleta ativa no momento. É um State: quem lê [currentPalette] (ou os vals de
 * [Color.kt]) dentro de uma composição recompõe quando a paleta troca.
 * Só o [AotvTheme] publica aqui, via [setCurrentPalette], a partir do id salvo
 * no [com.aotv.app.data.local.PlatformPrefs]; fora de composição a leitura
 * devolve só o valor atual, sem assinar nada.
 */
internal val currentPaletteState = mutableStateOf(AotvPalette.Default)

/** Paleta ativa no momento. */
val currentPalette: AotvPalette get() = currentPaletteState.value

/** Publica a paleta ativa. Chamado pelo [AotvTheme] (via MainActivity). */
fun setCurrentPalette(palette: AotvPalette) {
    currentPaletteState.value = palette
}
