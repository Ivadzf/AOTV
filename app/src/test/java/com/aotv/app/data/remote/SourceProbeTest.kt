package com.aotv.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class SourceProbeTest {

    // ── Caixas MP4 montadas na mão ──
    private fun box(type: String, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(8 + body.size); d.writeBytes(type); d.write(body)
        return o.toByteArray()
    }

    private fun mvhdV0(scale: Int, dur: Int): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(0); d.writeInt(0); d.writeInt(0) // versão+flags, criação, modificação
        d.writeInt(scale); d.writeInt(dur); d.write(ByteArray(80))
        return box("mvhd", o.toByteArray())
    }

    private fun mvhdV1(scale: Int, dur: Long): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(1 shl 24); d.writeLong(0); d.writeLong(0)
        d.writeInt(scale); d.writeLong(dur); d.write(ByteArray(80))
        return box("mvhd", o.toByteArray())
    }

    /** tkhd versão 0 tem 92 bytes. Largura e altura em 16.16 no fim. */
    private fun tkhd(w: Int, h: Int): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.write(ByteArray(76)); d.writeInt(w shl 16); d.writeInt(h shl 16)
        return box("tkhd", o.toByteArray())
    }

    private fun mp4(vararg parts: ByteArray) =
        box("ftyp", "isom".toByteArray()) + box("moov", parts.fold(ByteArray(0)) { a, b -> a + b })

    @Test fun mvhdVersao0() {
        assertEquals(7080.0, mvhdSeconds(mp4(mvhdV0(1000, 7_080_000))), 0.01)
    }

    @Test fun mvhdVersao1() {
        assertEquals(6540.0, mvhdSeconds(mp4(mvhdV1(90000, 6540L * 90000))), 0.01)
    }

    @Test fun semMvhdDaZero() {
        assertEquals(0.0, mvhdSeconds(ByteArray(200)), 0.0)
        assertEquals(0.0, mvhdSeconds(ByteArray(3)), 0.0)
    }

    @Test fun tkhdPegaAMaiorAltura() {
        // Faixa de áudio tem altura 0, a de vídeo 800.
        assertEquals(800, tkhdHeight(mp4(mvhdV0(1000, 1), tkhd(0, 0), tkhd(1920, 800))))
        assertEquals(0, tkhdHeight(ByteArray(100)))
    }

    @Test fun hls() {
        val fechado = "#EXTM3U\n#EXTINF:10.0,\na.ts\n#EXTINF:5.5,\nb.ts\n#EXT-X-ENDLIST\n"
        assertEquals(15.5, hlsSeconds(fechado), 0.001)
        assertEquals(0.0, hlsSeconds("#EXTM3U\n#EXTINF:10.0,\na.ts\n"), 0.0)
        assertEquals("v/720.m3u8", hlsVariant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv/720.m3u8\n"))
        assertEquals(null, hlsVariant(fechado))
    }

    // ── Duração destoa ──
    @Test fun outroFilme() {
        // Ponto Sem Retorno tem 118 min no TMDB e o Rubikon 109 min. Folga é 7,08 min.
        assertTrue(durationMismatch(118, 109 * 60.0))
        assertFalse(durationMismatch(118, 113 * 60.0))
        // Filme longo usa 6%. 200 min aceita até 12 min de diferença.
        assertFalse(durationMismatch(200, 189 * 60.0))
        assertTrue(durationMismatch(200, 187 * 60.0))
        // Sem duração no TMDB, ou arquivo curto demais pra ser filme, não acusa.
        assertFalse(durationMismatch(0, 50 * 60.0))
        assertFalse(durationMismatch(118, 300.0))
    }

    @Test fun idioma() {
        assertEquals("dub", langOf("FrostStream 1080p · Dublado"))
        assertEquals("dub", langOf("Dual Audio"))
        assertEquals("dub", langOf("PT-BR"))
        assertEquals("leg", langOf("Legendado · Servidor Athena"))
        assertEquals("leg", langOf("[LEG] 720p"))
        assertEquals("", langOf("Servidor Neon 1080p"))
        // "sub" só como palavra, "subway" não conta.
        assertEquals("", langOf("Subway 720p"))
    }

    // ── Escolha ──
    private fun f(url: String, addon: String = "A", label: String = "", rank: Int = 0, probe: ProbeResult? = null, wrong: Boolean = false) =
        SourceFacts(url, addon, label, rank, probe, wrong)
    private fun ok(ms: Long, h: Int = 0) = ProbeResult(true, ms, 0.0, h)

    @Test fun bestPegaMaiorResolucaoRapidaNoIdiomaDaPrimeira() {
        val list = listOf(
            f("a", label = "Dublado", rank = 720, probe = ok(500)),
            f("b", label = "Legendado", rank = 2160, probe = ok(300)),      // outro idioma
            f("c", label = "Dublado", probe = ok(800, 1080)),               // resolução do teste
            f("d", label = "Dublado", rank = 2160, probe = ok(4000)),       // lenta
            f("e", label = "Dublado", rank = 2160, probe = ProbeResult(false, 10, 0.0, 0)), // morta
            f("g", label = "Dublado", rank = 2160, wrong = true),           // outro filme
        )
        assertEquals(2, bestIndex(list))
    }

    @Test fun bestDesempataPelaMaisRapida() {
        val list = listOf(f("a", rank = 1080, probe = ok(900)), f("b", rank = 1080, probe = ok(200)))
        assertEquals(1, bestIndex(list))
    }

    @Test fun bestRespeitaQualidadeMaxima() {
        val list = listOf(f("a", rank = 720, probe = ok(500)), f("b", rank = 2160, probe = ok(300)))
        assertEquals(0, bestIndex(list, maxQuality = 1080))
    }

    @Test fun bestP2pPrefere1080ComMaisSeedersQue4k() {
        val list = listOf(
            f("magnet:?xt=urn:btih:a", label = "🌐 4K · 👤 300", rank = 2160),
            f("magnet:?xt=urn:btih:b", label = "🌐 1080p · 👤 50", rank = 1080),
            f("magnet:?xt=urn:btih:c", label = "🌐 1080p · 👤 900", rank = 1080),
        )
        assertEquals(2, bestIndex(list))
    }

    @Test fun bestTudoRuim() {
        assertEquals(1, bestIndex(listOf(f("a", wrong = true), f("b", probe = ProbeResult(false, 1, 0.0, 0)))))
        assertEquals(0, bestIndex(listOf(f("a", wrong = true))))
        assertEquals(0, bestIndex(emptyList()))
    }

    @Test fun espelho() {
        val list = listOf(
            f("http://neon", addon = "Frost", label = "Dublado", rank = 1080),
            f("http://leg", addon = "Frost", label = "Legendado", rank = 1080),   // outro idioma
            f("http://fenix", addon = "Fenix", label = "Dublado", rank = 1080),   // outro addon
            f("http://4k", addon = "Frost", label = "Dublado", rank = 2160),      // outra resolução
            f("http://carbon", addon = "Frost", label = "Dublado", rank = 0),     // desconhecida conta
            f("http://azure", addon = "Frost", label = "Dublado", rank = 1080),
        )
        assertEquals(4, sameMirror(list, 0, setOf("http://neon")))
        assertEquals(5, sameMirror(list, 0, setOf("http://neon", "http://carbon")))
        assertEquals(-1, sameMirror(list, 0, setOf("http://neon", "http://carbon", "http://azure")))
    }

    @Test fun proximaEvitaOutroFilmeEMorta() {
        val list = listOf(
            f("a", addon = "X"),
            f("b", addon = "X"),                        // addon que falhou
            f("c", addon = "Y", wrong = true),
            f("d", addon = "Y", probe = ProbeResult(false, 1, 0.0, 0)),
            f("e", addon = "Y"),
        )
        assertEquals(4, nextIndex(list, setOf("a"), setOf("X")))
        assertEquals(1, nextIndex(list, setOf("a", "e"), setOf("X")))
        assertEquals(2, nextIndex(list, setOf("a", "b", "d", "e"), emptySet()))
        assertEquals(-1, nextIndex(list, list.map { it.url }.toSet(), emptySet()))
    }

    // ── Selos do pôster (mesmas regras do webOS) ──

    @Test fun resolucaoSoPorPalavraInteira() {
        assertEquals(2160, qualityRankOf("Filme.2024.2160p.WEB-DL"))
        assertEquals(2160, qualityRankOf("FrostStream 4K"))
        assertEquals(1440, qualityRankOf("2K HDR"))
        assertEquals(1080, qualityRankOf("1080p · Dublado"))
        assertEquals(720, qualityRankOf("WEB-DL HD"))
        assertEquals(480, qualityRankOf("DVDRip 480p"))
        // Nome de site, HDR, HDTV, seeds e tamanho não são resolução.
        assertEquals(0, qualityRankOf("4KHDHub"))
        assertEquals(0, qualityRankOf("HDR"))
        assertEquals(0, qualityRankOf("HDTV"))
        assertEquals(0, qualityRankOf("👤 1080"))
        assertEquals(0, qualityRankOf("1080 seeds"))
        assertEquals(0, qualityRankOf("2160 MB"))
    }

    @Test fun hdrDolbyEAtmos() {
        assertTrue(hasHdr("2160p HDR10+ DV"))
        assertTrue(hasHdr("HDR"))
        assertTrue(hasHdr("HDR10Plus"))
        assertFalse(hasHdr("HDRip"))
        assertTrue(hasDolbyVision("2160p.DV.HDR"))
        assertTrue(hasDolbyVision("Dolby Vision"))
        assertFalse(hasDolbyVision("DVDRip"))
        assertTrue(hasAtmos("DDP5.1 Atmos"))
        assertFalse(hasAtmos("Atmosfera"))
    }

    @Test fun audioEmPortugues() {
        assertTrue(ptAudio("Dublado"))
        assertTrue(ptAudio("Dual Áudio · Legendas PT"))
        assertTrue(ptAudio("🇧🇷 🇺🇸"))
        assertTrue(ptAudio("Português"))
        assertTrue(ptAudio("PT-BR 1080p"))
        assertFalse(ptAudio("Legendado PT-BR"))
        assertFalse(ptAudio("🇺🇸 Legendas: 🇧🇷"))
        assertFalse(ptAudio("Inglês 1080p"))
    }
}
