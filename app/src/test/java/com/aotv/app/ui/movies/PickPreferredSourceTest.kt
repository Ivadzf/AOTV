package com.aotv.app.ui.movies

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Escolha da fonte ao trocar de episódio ou temporada.
 *
 * O que quebrou na prática: o player casava a fonte pelo RÓTULO, e o rótulo muda a cada
 * episódio (tamanho do arquivo, servidor sorteado). Nunca batia, caía no índice 0, e a fonte
 * escolhida ou fixada não acompanhava o episódio seguinte.
 */
class PickPreferredSourceTest {

    private fun src(addon: String, label: String) =
        MoviePlayerSource(label = label, url = "https://x/$addon/$label", addon = addon)

    // Lista do episódio NOVO: rótulos diferentes do episódio anterior, de propósito.
    private val novoEpisodio = listOf(
        src("UnioFlix", "UnioFlix 1080p · Dublado · 1.9GB"),   // 0
        src("FrostStream", "FrostStream 720p · Dublado"),      // 1
        src("FrostStream", "FrostStream 4K · Dublado · 7.2GB"),// 2
        src("BestCine", "BestCine 1080p · Legendado"),         // 3
    )

    @Test
    fun `mesmo addon e mesma qualidade, mesmo com rotulo diferente`() {
        // Vinha assistindo FrostStream 4K no episódio anterior (rótulo "FrostStream 4K · 6.8GB").
        assertEquals(2, pickPreferredSource(novoEpisodio, "FrostStream", 2160))
    }

    @Test
    fun `addon sem a qualidade exata cai na melhor dele, nao em outro addon`() {
        assertEquals(2, pickPreferredSource(novoEpisodio, "FrostStream", 1440))
    }

    @Test
    fun `addon sumiu neste episodio, mantem ao menos a qualidade`() {
        assertEquals(0, pickPreferredSource(novoEpisodio, "FenixFlix", 1080))
    }

    @Test
    fun `sem preferencia usa o primeiro, que ja vem na prioridade dos addons`() {
        assertEquals(0, pickPreferredSource(novoEpisodio, "", 0))
    }

    @Test
    fun `lista vazia nao estoura`() {
        assertEquals(0, pickPreferredSource(emptyList(), "FrostStream", 2160))
    }

    @Test
    fun `faixa de qualidade sai do rotulo`() {
        assertEquals(2160, sourceQualityRank("FrostStream 4K · Dublado"))
        assertEquals(2160, sourceQualityRank("UHD 2160p"))
        assertEquals(1080, sourceQualityRank("BestCine 1080p"))
        assertEquals(720, sourceQualityRank("Fonte HD"))
        assertEquals(0, sourceQualityRank("Servidor Athena"))
    }
}
