package com.aotv.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Telas de lista e formulário que já seguem o desenho do webOS (Configurações com resumo fixo à
 * direita, Addons, Explorar addons, Perfis, Busca e Descobrir) entram na TV na escala do webOS.
 * O celular mede em dp para o dedo. Aqui 1dp vira 1/1600 da largura da tela, a mesma proporção
 * do CSS do webOS (texto de 1.2rem, margem de 3rem), em vez do celular esticado.
 * A rolagem segue a regra do focus.js, com o item focado a 3rem da borda.
 */
@Composable
fun TvScaled(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val config = LocalConfiguration.current
    val scale = minOf(config.screenWidthDp * d.density / 1600f, config.screenHeightDp * d.density / 900f)
    // Margem calculada antes da troca de escala, em rem da tela real.
    val inset = 3.r
    CompositionLocalProvider(LocalDensity provides Density(scale, d.fontScale)) {
        TvScrollArea(inset = with(d) { inset.toPx() }.let { px -> androidx.compose.ui.unit.Dp(px / scale) }) {
            TvText(content)
        }
    }
}
