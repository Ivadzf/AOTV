package com.aotv.app.ui.theme

import androidx.compose.ui.graphics.Color

// Identidade visual do AOTV (aprovada pelo Davi, 2026-11-11): vermelho escuro +
// turquesa, sobre fundo escuro. O azul do JOTV saiu da paleta.
// Camadas de fundo
val Background         = Color(0xFF0D0D0D)
val BackgroundElevated = Color(0xFF191919)
val BackgroundCard     = Color(0xFF222222)
val BackgroundCard2    = Color(0xFF1A1A1A)
val Surface2           = Color(0xFF2B2B2B)

// Destaque turquesa (#00C2B8) e vermelho escuro (#9E1B1B, destaque secundário).
val Accent       = Color(0xFF00C2B8)
val AccentFill   = Color(0xFF00A69B)
val AccentDark   = Color(0xFF00796B)
val AccentSoft   = Color(0xFF0E2A28)
val AccentSecondary = Color(0xFF9E1B1B)

// Estado
val LiveRed    = Color(0xFFE5484D) // apagar/erro e selos: vermelho claro, legível sobre o fundo escuro
val Danger     = Color(0xFFE5484D) // apagar/erro: vermelho claro, legível sobre o fundo escuro
val DangerText = Color(0xFFFF8C8F)
val Gold       = Color(0xFFF4CC75)
val Ok         = Color(0xFF3DDC84)
val GreenMatch = Ok

// Texto. O terciário é 999999 e não 808080 para passar de 4.5 para 1 também sobre o cartão.
val TextPrimary   = Color(0xFFF5F7F8)
val TextSecondary = Color(0xFFC3C5C9)
val TextTertiary  = Color(0xFFA0A3AA)
val TextOnLight   = Color(0xFF0A0A0A) // texto em fundo branco (foco e botão principal)

// Bordas
val Border       = Color(0xFF373737)
val BorderSubtle = Color(0x0FFFFFFF) // branco 6 por cento, divisória do resumo

// Vidro e sobreposições
val GlassBg            = Color(0xB80D0D0D)
val OverlayScrim       = Color(0x99000000)
val HintChipBackground = Color(0x88000000)
val HintChipText       = Color(0xAAFFFFFF)
val PanelBackground    = Color(0xF7101010) // painel lateral, rgba(16,16,16,.97)

// Nomes antigos que as telas ainda usam
val BackgroundDark     = Background
val SurfaceDark        = BackgroundCard
val SurfaceVariantDark = BackgroundCard2
val CardDark           = BackgroundCard
val Primary            = Accent
val PrimaryVariant     = AccentDark
val PrimaryContainer   = AccentSoft
val BorderAccent       = Color(0x5900C2B8)
val Surface            = BackgroundCard
val SurfaceVariant     = BackgroundCard2
val ErrorCol           = Danger
val Success            = Ok
val TextMuted          = TextTertiary
val FocusRing          = TextPrimary
val FocusBackground    = Color(0xFF303030)
val AccentFocus        = Accent
