package com.aotv.app.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

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

        /** Todas as paletas, na ordem do seletor. */
        val all: List<AotvPalette> = listOf(Default, White, Crimson, Ocean, Violet, Emerald, Amber, Pink)

        /** Paleta pelo id salvo; id desconhecido (ou vazio) volta ao Padrão. */
        fun fromId(id: String): AotvPalette = all.firstOrNull { it.id == id } ?: Default
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
