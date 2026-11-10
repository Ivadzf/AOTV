package com.aotv.app.ui.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tradução do rótulo cru do addon para o que a lista de fontes mostra.
 *
 * Casos tirados do que apareceu errado na tela: o nome do filme virando "servidor" e o
 * endereço do addon aparecendo no lugar do nome.
 */
class ParseSourceLabelTest {

    @Test
    fun `titulo com ano nao vira servidor`() {
        val info = parseSourceLabel(
            rawLabel = "Tron O Legado (2010) · 1080p · Dublado",
            addonName = "FrostStream",
            qualityRank = 1080,
            mediaTitle = "Tron O Legado",
        )
        assertEquals("FrostStream", info.addon)
        assertEquals("", info.server)
        assertEquals("1080p", info.quality)
        assertEquals("Dublado", info.language)
    }

    @Test
    fun `torrent mostra seeds tamanho e codec e nao a nota como servidor`() {
        val info = parseSourceLabel(
            rawLabel = "🌐 1080p · Brrip · 🔵 65/100 · 👤 942 · ✅ TrueSpec Verified · 🎞️ x264 · 🔊 dual audio · 💾 1.9 GB",
            addonName = "TorrentClaw (PT-BR)",
            qualityRank = 1080,
            mediaTitle = "A Origem",
        )
        assertEquals("Brrip", info.server)
        assertEquals("Dual áudio", info.language)
        assertEquals(listOf("942 seeds", "1.9 GB", "H.264"), info.tags)
        val semOrigem = parseSourceLabel("🌐 1080p · 👤 500 · Sintel com semente web", "Teste", 1080, "Sintel")
        assertEquals("", semOrigem.server)
        assertEquals(listOf("500 seeds"), semOrigem.tags)
    }

    @Test
    fun `titulo com acento diferente tambem sai`() {
        val info = parseSourceLabel(
            rawLabel = "Mistérios e Sobrenatural (2019) · Servidor Nyx · Dublado",
            addonName = "BestCine",
            qualityRank = 2160,
            mediaTitle = "Misterios e Sobrenatural",
        )
        assertEquals("Servidor Nyx", info.server)
        assertEquals("4K", info.quality)
    }

    @Test
    fun `servidor de verdade e preservado`() {
        val info = parseSourceLabel(
            rawLabel = "🎬 Tron · 🌀 Filme · ⚡ Servidor Safira · 🇧🇷 Dublado",
            addonName = "BestCine",
            qualityRank = 1080,
            mediaTitle = "Tron",
        )
        assertEquals("Servidor Safira", info.server)
        assertEquals("Dublado", info.language)
        assertTrue("emoji nao pode sobrar", info.server.all { it.code < 0x2190 })
    }

    @Test
    fun `nome do addon em branco nao deixa a linha sem identidade`() {
        val info = parseSourceLabel("1080p · Dublado", addonName = "", qualityRank = 1080)
        assertEquals("Fonte", info.addon)
    }

    @Test
    fun `tamanho do arquivo vira pill e nao servidor`() {
        val info = parseSourceLabel(
            rawLabel = "FrostStream · 4K · 7.2 GB · Dublado",
            addonName = "FrostStream",
            qualityRank = 2160,
            mediaTitle = "Tron",
        )
        assertTrue("tamanho deveria estar nas pills", info.tags.any { it.contains("GB") })
        assertEquals("4K", info.quality)
    }
}
