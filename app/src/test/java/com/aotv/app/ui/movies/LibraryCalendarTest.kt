package com.aotv.app.ui.movies

import com.aotv.app.data.remote.dto.TmdbNextEpisodeDto
import com.aotv.app.data.remote.dto.TmdbSeriesDetailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calendário da Biblioteca (Lote 2, item 6 — v1.10.0): regras puras.
 * "Hoje" nos testes é 13/11/2026 (now = meio-dia UTC desse dia).
 */
class LibraryCalendarTest {
    private val now = airDateMs("2026-11-13")!!
    private val today = now

    private fun detail(next: TmdbNextEpisodeDto? = null, last: TmdbNextEpisodeDto? = null) =
        TmdbSeriesDetailDto(
            id = 1, name = "Série",
            nextEpisodeToAir = next, lastEpisodeToAir = last,
        )

    private fun entries(next: TmdbNextEpisodeDto? = null, last: TmdbNextEpisodeDto? = null, lastSeen: Boolean = false) =
        calendarEntriesOf(1, "Série", "", detail(next, last), lastSeen, now)

    @Test
    fun proximoSemLimiteDeDiasEntra() {
        // Estreia daqui a 3 meses entra (o calendário não tem o teto de 10 dias da fileira).
        val list = entries(next = TmdbNextEpisodeDto("2027-02-10", 3, 1))
        assertEquals(1, list.size)
        assertFalse(list[0].isLastAired)
        assertEquals("T3 E1 · estreia 10/02/2027", list[0].describe())
    }

    @Test
    fun semAirDateNaoEntraENaoInventaData() {
        assertTrue(entries(next = TmdbNextEpisodeDto(null, 2, 5)).isEmpty())
        assertTrue(entries(last = TmdbNextEpisodeDto("", 2, 4)).isEmpty())
        assertTrue(entries().isEmpty())
    }

    @Test
    fun ultimoRecenteNaoVistoEntraComoJaFoiAoAr() {
        val list = entries(last = TmdbNextEpisodeDto("2026-11-10", 2, 4))
        assertEquals(1, list.size)
        assertTrue(list[0].isLastAired)
        assertTrue(list[0].isAired(today))
        assertEquals("T2 E4 · foi ao ar 10/11/2026", list[0].describe())
    }

    @Test
    fun ultimoVistoOuAntigoNaoEntra() {
        assertTrue(entries(last = TmdbNextEpisodeDto("2026-11-10", 2, 4), lastSeen = true).isEmpty())
        // 20 dias atrás passa dos 14 dias do calendário.
        assertTrue(entries(last = TmdbNextEpisodeDto("2026-10-24", 2, 4)).isEmpty())
    }

    @Test
    fun proximoEultimoJuntos() {
        val list = entries(
            next = TmdbNextEpisodeDto("2026-11-20", 2, 5),
            last = TmdbNextEpisodeDto("2026-11-06", 2, 4),
        )
        assertEquals(2, list.size)
        assertEquals(listOf(true, false), sortCalendarEntries(list).map { it.isLastAired })
    }

    @Test
    fun ordemPorData() {
        val a = entries(next = TmdbNextEpisodeDto("2026-12-01", 1, 1))[0].copy(tmdbId = 1, title = "A")
        val b = entries(next = TmdbNextEpisodeDto("2026-11-20", 1, 1))[0].copy(tmdbId = 2, title = "B")
        val c = entries(last = TmdbNextEpisodeDto("2026-11-10", 1, 1))[0].copy(tmdbId = 3, title = "C")
        assertEquals(listOf(3, 2, 1), sortCalendarEntries(listOf(a, b, c)).map { it.tmdbId })
    }

    @Test
    fun rotulosDeData() {
        fun entry(date: String) = entries(next = TmdbNextEpisodeDto(date, 1, 1))[0]
        assertEquals("Hoje", entry("2026-11-13").dateLabel(today))
        assertEquals("Amanhã", entry("2026-11-14").dateLabel(today))
        assertEquals("em 7 dias", entry("2026-11-20").dateLabel(today))
        assertEquals("Ontem", entry("2026-11-12").dateLabel(today))
        assertEquals("já foi ao ar", entry("2026-11-05").dateLabel(today))
        assertFalse(entry("2026-11-13").isAired(today))
        assertTrue(entry("2026-11-12").isAired(today))
    }
}
