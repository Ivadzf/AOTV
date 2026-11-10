package com.aotv.app.ui.movies

import com.aotv.app.data.remote.dto.TmdbNextEpisodeDto
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewEpisodesTest {
    private val item = MovieItem(1, "1", "Série", "", "", "", "", 0.0, type = MediaType.SERIES)
    private val now = airDateMs("2026-11-01")!!
    private fun ep(date: String?, s: Int = 2, e: Int = 5) = TmdbNextEpisodeDto(date, s, e)

    @Test
    fun episodioRecenteNaoVistoGanhaNovo() {
        val n = newEpisodeOf(item, ep("2026-10-20"), null, lastSeen = false, now = now)!!
        assertEquals("NOVO", n.badge)
        assertEquals("T2 E5 saiu 20/10/2026", n.sub)
    }

    @Test
    fun vistoOuAntigoNaoEntra() {
        assertNull(newEpisodeOf(item, ep("2026-10-20"), null, lastSeen = true, now = now))
        assertNull(newEpisodeOf(item, ep("2026-09-01"), null, lastSeen = false, now = now))
    }

    @Test
    fun estreiaEmAte10DiasGanhaData() {
        val n = newEpisodeOf(item, ep("2026-09-01"), ep("2026-11-08", 3, 1), lastSeen = false, now = now)!!
        assertEquals("08/11", n.badge)
        assertEquals("T3 E1 estreia 08/11/2026", n.sub)
        assertNull(newEpisodeOf(item, null, ep("2026-11-20"), lastSeen = false, now = now))
    }

    @Test
    fun ordemNovosPrimeiroDepoisEstreias() {
        val a = newEpisodeOf(item.copy(tmdbId = 1), ep("2026-10-15"), null, false, now)!!
        val b = newEpisodeOf(item.copy(tmdbId = 2), ep("2026-10-30"), null, false, now)!!
        val c = newEpisodeOf(item.copy(tmdbId = 3), null, ep("2026-11-05"), false, now)!!
        val d = newEpisodeOf(item.copy(tmdbId = 4), null, ep("2026-11-03"), false, now)!!
        assertEquals(listOf(2, 1, 4, 3), sortNewEpisodes(listOf(a, b, c, d)).map { it.item.tmdbId })
    }
}
