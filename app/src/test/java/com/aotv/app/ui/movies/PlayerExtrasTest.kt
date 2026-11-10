package com.aotv.app.ui.movies

import com.aotv.app.data.local.ExtAudioPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PlayerExtrasTest {

    @Test
    fun `rotulo do atraso do som`() {
        assertEquals("Sincronia original", audioDelayLabel(0.0))
        assertEquals("Som atrasado 1,35 s", audioDelayLabel(1.35))
        assertEquals("Som adiantado 2 s", audioDelayLabel(-2.0))
        assertEquals("Som atrasado 0,05 s", audioDelayLabel(0.05))
        assertEquals("Som atrasado 0,5 s", audioDelayLabel(0.5))
        assertEquals("+1,35 s", syncValue(1.35))
        assertEquals("−0,05 s", syncValue(-0.05))
        assertEquals("0,00 s", syncValue(0.0))
        assertEquals("Legenda adiantada 0,5 s", subDelayLabel(-0.5))
    }

    @Test
    fun `passos somados nao acumulam erro`() {
        var d = 0.0
        repeat(7) { d = addDelay(d, 0.05) }
        assertEquals(0.35, d, 0.0)
        assertEquals(1.0, syncStep(0.05, 20), 0.0)
        assertEquals(-0.25, syncStep(-0.05, 6), 0.0)
        assertEquals(5.0, syncStep(1.0, 7), 0.0)
        assertEquals(-10.0, syncStep(-10.0, 30), 0.0)
    }

    @Test
    fun `assinatura ignora o link e separa espelhos`() {
        val a = MoviePlayerSource("Clube da Luta · Servidor Neon · 1080p · Dublado", "https://x/1?token=a", "FrostStream")
        val b = a.copy(url = "https://x/2?token=b")
        val c = a.copy(label = "Clube da Luta · Servidor Zeus · 1080p · Dublado")
        assertEquals(sourceSig(a, "Clube da Luta"), sourceSig(b, "Clube da Luta"))
        assertTrue(sourceSig(a, "Clube da Luta") != sourceSig(c, "Clube da Luta"))
    }

    @Test
    fun `correcao de deriva`() {
        assertNull(driftSpeed(450, 1f))
        assertNull(driftSpeed(-301, 1f))
        assertEquals(1f, driftSpeed(10, 1f))
        val ahead = driftSpeed(100, 1f)!!
        val behind = driftSpeed(-100, 1f)!!
        assertTrue(ahead < 1f && ahead >= 0.96f)
        assertTrue(behind > 1f && behind <= 1.04f)
        assertEquals(1.5f, driftSpeed(0, 1.5f))
    }

    @Test
    fun `guarda no maximo 60 titulos, os mais recentes`() {
        val all = (1..65).associate { "MOVIE:$it" to ExtAudioPick("s", 0.0, it.toLong()) }
        val kept = keepNewest(all)
        assertEquals(60, kept.size)
        assertTrue("MOVIE:65" in kept && "MOVIE:5" !in kept && "MOVIE:6" in kept)
    }

    @Test
    fun `relogio conta a velocidade`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = 21 * 3_600_000L + 4 * 60_000L
        assertEquals("21h04  ·  termina às 22h04", clockLine(now, 0, 3_600_000L, 1f, utc))
        assertEquals("21h04  ·  termina às 21h34", clockLine(now, 0, 3_600_000L, 2f, utc))
        assertEquals("21h04", clockLine(now, 0, 0, 1f, utc))
    }

    @Test
    fun `legenda srt com atraso`() {
        val cues = parseSubs("1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>Oi</i>\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nTudo bem?\r\n")
        assertEquals(2, cues.size)
        assertEquals("Oi", cueAt(cues, 1_500, 0))
        assertEquals("", cueAt(cues, 2_700, 0))
        assertEquals("Oi", cueAt(cues, 2_700, 500))
        assertEquals("Tudo bem?", cueAt(cues, 2_600, -500))
        assertEquals(1, parseSubs("WEBVTT\n\n00:01.000 --> 00:02.000\nVTT").size)
    }
}
