package com.aotv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aotv.app.data.local.HomeDensity
import com.aotv.app.data.local.PosterSize
import com.aotv.app.ui.theme.Background

/**
 * Largura do card da fileira da Início para o tamanho escolhido (Fase B, frente
 * compacta). Mesma conta do [posterWidth] — só muda a base: Pequeno 108dp (o
 * padrão novo, estilo Nuvio), Médio 136dp (o tamanho antigo) e Grande 168dp.
 */
@Composable
fun posterWidthFor(size: PosterSize): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    val font = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return (size.widthDp * font.coerceAtMost(1.65f)).coerceAtMost((width - 32f) * .72f).dp
}

/** Espaço vertical entre as seções da Início. Compacta aperta, Confortável mantém o antigo. */
fun homeSectionSpacing(density: HomeDensity): Dp = if (density == HomeDensity.COMPACT) 12.dp else 22.dp

/** Espaço entre os cards de uma fileira. */
fun homeRowSpacing(density: HomeDensity): Dp = if (density == HomeDensity.COMPACT) 10.dp else 14.dp

/** Espaço vertical entre as linhas de um grid da Início ("Ver Tudo"). */
fun homeGridSpacing(density: HomeDensity): Dp = if (density == HomeDensity.COMPACT) 10.dp else 16.dp

/**
 * Fundo da Início tingido com a matiz do destaque (Fase B, opt-in). Sem matiz
 * (pôster sem cor ou ainda sem leitura) volta ao fundo sólido de sempre.
 */
fun dynamicHomeBackground(hue: Int?): Brush {
    if (hue == null || hue < 0) return Brush.verticalGradient(listOf(Background, Background))
    val top = Color.hsl(hue.toFloat(), 0.45f, 0.15f)
    val mid = Color.hsl(hue.toFloat(), 0.38f, 0.09f)
    return Brush.verticalGradient(listOf(top, mid, Background))
}
