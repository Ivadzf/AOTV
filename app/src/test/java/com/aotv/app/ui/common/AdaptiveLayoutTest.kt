package com.aotv.app.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test fun gradeSempreTemUmaColunaMesmoEmJanelaPequena() {
        listOf(240, 320, 393, 600, 1280).forEach { width ->
            assertTrue(posterColumns(width, 2f) >= 1)
        }
    }

    @Test fun fonteGrandeRecebeMaisEspacoPorCartao() {
        listOf(320, 393, 600, 1280).forEach { width ->
            assertTrue(posterColumns(width, 2f) <= posterColumns(width, 1f))
        }
    }

    @Test fun destaqueReservaAlturaParaTextoAmpliadoNasDuasOrientacoes() {
        listOf(320 to 568, 568 to 320, 393 to 873, 873 to 393, 1280 to 800).forEach { (w, h) ->
            assertTrue(homeHeroHeight(w, h, 2f) > homeHeroHeight(w, h, 1f))
            assertTrue(homeHeroHeight(w, h, 2f) >= if (w > h) 460f else 520f)
        }
    }

    @Test fun destaqueDeitadoReservaEspacoParaNavegacaoComFontePadrao() {
        listOf(568 to 320, 873 to 393).forEach { (w, h) ->
            assertTrue(homeHeroHeight(w, h, 1f) <= h - 88)
        }
    }
}
