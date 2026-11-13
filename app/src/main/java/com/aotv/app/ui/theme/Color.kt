package com.aotv.app.ui.theme

import androidx.compose.ui.graphics.Color

// Identidade visual do AOTV (aprovada pelo Davi, 2026-11-11): vermelho escuro +
// turquesa, sobre fundo escuro. O azul do JOTV saiu da paleta.
//
// Estes nomes são getters sobre a paleta ativa ([currentPalette]): trocar o tema
// em Configurações → Aparência recompõe as telas sem mexer em nenhuma delas.
// Fora de composição a leitura devolve só a cor atual, sem assinar nada — por
// isso aqui NÃO é CompositionLocal: funções comuns como qualityColor() também
// leem estas cores. Os valores de cada paleta vivem em [AotvPalette].
// Camadas de fundo
val Background: Color get() = currentPalette.background
val BackgroundElevated: Color get() = currentPalette.backgroundElevated
val BackgroundCard: Color get() = currentPalette.backgroundCard
val BackgroundCard2: Color get() = currentPalette.backgroundCard2
val Surface2: Color get() = currentPalette.surface2

// Destaque turquesa (#00C2B8) e vermelho escuro (#9E1B1B, destaque secundário).
val Accent: Color get() = currentPalette.accent
val AccentFill: Color get() = currentPalette.accentFill
val AccentDark: Color get() = currentPalette.accentDark
val AccentSoft: Color get() = currentPalette.accentSoft
val AccentSecondary: Color get() = currentPalette.accentSecondary

// Estado
val LiveRed: Color get() = currentPalette.liveRed // apagar/erro e selos: vermelho claro, legível sobre o fundo escuro
val Danger: Color get() = currentPalette.danger // apagar/erro: vermelho claro, legível sobre o fundo escuro
val DangerText: Color get() = currentPalette.dangerText
val Gold: Color get() = currentPalette.gold
val Ok: Color get() = currentPalette.ok
val GreenMatch: Color get() = Ok

// Texto. O terciário é 999999 e não 808080 para passar de 4.5 para 1 também sobre o cartão.
val TextPrimary: Color get() = currentPalette.textPrimary
val TextSecondary: Color get() = currentPalette.textSecondary
val TextTertiary: Color get() = currentPalette.textTertiary
val TextOnLight: Color get() = currentPalette.textOnLight // texto em fundo branco (foco e botão principal)

// Bordas
val Border: Color get() = currentPalette.border
val BorderSubtle: Color get() = currentPalette.borderSubtle // branco 6 por cento, divisória do resumo

// Vidro e sobreposições
val GlassBg: Color get() = currentPalette.glassBg
val OverlayScrim: Color get() = currentPalette.overlayScrim
val HintChipBackground: Color get() = currentPalette.hintChipBackground
val HintChipText: Color get() = currentPalette.hintChipText
val PanelBackground: Color get() = currentPalette.panelBackground // painel lateral, rgba(16,16,16,.97)

// Foco e superfícies claras. FocusSurface é a superfície branca de foco e do
// botão primário nos temas escuros (escura no Branco); OnFocusSurface é o texto
// sobre ela. FocusRing é o anel de foco sobre o fundo do app. OnAccent é o texto
// sobre o destaque (selos e botões com fundo Accent). FillSubtle é o preenchimento
// sutil de chip; FillTint é a base dos preenchimentos translúcidos (branco nos
// temas escuros, preto no Branco) e SearchFocused é o campo de busca com foco.
val FocusBackground: Color get() = currentPalette.focusBackground
val FocusSurface: Color get() = currentPalette.focusSurface
val OnFocusSurface: Color get() = currentPalette.onFocusSurface
val FocusRing: Color get() = currentPalette.focusRing
val OnAccent: Color get() = currentPalette.onAccent
val FillSubtle: Color get() = currentPalette.fillSubtle
val FillTint: Color get() = currentPalette.fillTint
val SearchFocused: Color get() = currentPalette.searchFocused

// Nomes antigos que as telas ainda usam
val BackgroundDark: Color get() = Background
val SurfaceDark: Color get() = BackgroundCard
val SurfaceVariantDark: Color get() = BackgroundCard2
val CardDark: Color get() = BackgroundCard
val Primary: Color get() = Accent
val PrimaryVariant: Color get() = AccentDark
val PrimaryContainer: Color get() = AccentSoft
val BorderAccent: Color get() = Accent.copy(alpha = 0.35f)
val Surface: Color get() = BackgroundCard
val SurfaceVariant: Color get() = BackgroundCard2
val ErrorCol: Color get() = Danger
val Success: Color get() = Ok
val TextMuted: Color get() = TextTertiary
val AccentFocus: Color get() = Accent
