package com.aotv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Mantém os cartões legíveis com a fonte do sistema e a largura da janela. */
@Composable
fun posterWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    val font = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return (136f * font.coerceAtMost(1.65f)).coerceAtMost((width - 32f) * .72f).dp
}

@Composable
fun continueCardWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
    return (width * .82f).coerceIn(240f, 360f).coerceAtMost(width - 32f).dp
}

/** Continuar assistindo no modo compacto: card mais estreito, cabe mais título na tela. */
@Composable
fun continueCardWidth(compact: Boolean): Dp {
    if (!compact) return continueCardWidth()
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
    return (width * .72f).coerceIn(200f, 300f).coerceAtMost(width - 32f).dp
}

internal fun posterColumns(widthDp: Int, fontScale: Float, minimumWidth: Int = 136): Int =
    ((widthDp - 32 + 12) / (minimumWidth * fontScale.coerceIn(1f, 1.65f) + 12)).toInt().coerceIn(1, 8)

internal fun homeHeroHeight(widthDp: Int, heightDp: Int, fontScale: Float): Float {
    val landscape = widthDp > heightDp
    val base = if (landscape) ((heightDp - 88) * .98f).coerceIn(224f, 440f) else (heightDp * .7f).coerceIn(420f, 620f)
    val extra = fontScale.coerceAtLeast(1f) - 1f
    val minimum = if (landscape) 220f else 280f
    return maxOf(base + extra * if (landscape) 130f else 170f, minimum + extra * 240f)
}
