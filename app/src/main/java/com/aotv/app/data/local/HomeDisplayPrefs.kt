package com.aotv.app.data.local

/** Tamanho do pôster nas fileiras da Início (Fase B, frente compacta). */
enum class PosterSize {
    SMALL, MEDIUM, LARGE;

    /** Largura do card em dp, antes do ajuste da fonte do sistema. */
    val widthDp: Float
        get() = when (this) {
            SMALL -> 108f
            MEDIUM -> 136f
            LARGE -> 168f
        }
}

/** Estilo do destaque do topo da Início. */
enum class HeroStyle { FULL_BLEED, CARD }

/** Densidade das fileiras da Início. */
enum class HomeDensity { COMPACT, COMFORTABLE }

/**
 * Exibição da Início, por perfil. O padrão é o compacto: pôster pequeno, fileiras
 * apertadas, sem selos nem etiqueta no pôster, herói em tela cheia, fundo sólido
 * e sublinhado de destaque nas seções. Os selos de QUALIDADE/FONTE (4K, Remux,
 * BluRay, WEB-DL, DV, HDR10+) são outra coisa: decisão do Davi (Lote 11,
 * v1.21.0), vão LIGADOS por padrão e têm toggle próprio ([showQualityBadges]) —
 * a etiqueta do topo continua no [showPosterBadges] da Fase B, desligada por
 * padrão. Os selos de qualidade só aparecem quando o resumo das fontes do
 * título existe (ver PosterInfo): sem fonte lida, sem selo inventado.
 */
data class HomeDisplayPrefs(
    val posterSize: PosterSize = PosterSize.SMALL,
    val posterRadiusDp: Int = 14,
    val density: HomeDensity = HomeDensity.COMPACT,
    val showPosterBadges: Boolean = false,
    val showQualityBadges: Boolean = true,
    val heroStyle: HeroStyle = HeroStyle.FULL_BLEED,
    val dynamicBackground: Boolean = false,
    val sectionUnderline: Boolean = true,
) {
    val compact: Boolean get() = density == HomeDensity.COMPACT
}
