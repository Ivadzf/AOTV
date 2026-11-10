package com.aotv.app.ui.common

import com.aotv.app.data.local.SourceQuality
import com.aotv.app.data.local.rankMap
import com.aotv.app.data.local.stale
import com.aotv.app.data.local.summarize
import com.aotv.app.data.remote.StremioStream
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras do cartão de pôster, as mesmas do webOS 1.5.12. */
class PosterCardTest {

    private fun item(
        type: MediaType = MediaType.MOVIE, release: String = "2020-01-01", lang: String = "en",
        rating: Double = 6.0, votes: Int = 100, countries: List<String> = emptyList(),
        year: String = "2020", genres: List<Int> = emptyList(),
    ) = MovieItem(
        tmdbId = 1, embedId = "1", title = "T", overview = "", posterUrl = "", backdropUrl = "", year = year,
        rating = rating, type = type, releaseDate = release, genreIds = genres, originalLanguage = lang,
        voteCount = votes, originCountry = countries,
    )

    private val today = "2026-11-09"
    private val monthAgo = "2026-10-10"

    @Test fun etiquetaSegueAOrdemDoWebOS() {
        assertEquals(PosterTag("#2 Hoje", top = true), posterTag(item(release = "2026-12-01"), 2, today, monthAgo))
        assertEquals(PosterTag("#7 Hoje"), posterTag(item(), 7, today, monthAgo))
        assertEquals("Em breve", posterTag(item(release = "2026-12-01"), 0, today, monthAgo)?.text)
        assertEquals("Novo", posterTag(item(release = "2026-11-01"), 0, today, monthAgo)?.text)
        assertEquals("Novo", posterTag(item(release = today), 0, today, monthAgo)?.text)
        assertEquals("Série nova", posterTag(item(MediaType.SERIES, release = "2026-10-20"), 0, today, monthAgo)?.text)
        // Estreia velha cai no idioma original.
        assertEquals("Coreano", posterTag(item(release = "2020-01-01", lang = "ko"), 0, today, monthAgo)?.text)
        assertEquals("Nacional", posterTag(item(lang = "pt", countries = listOf("BR")), 0, today, monthAgo)?.text)
        assertEquals("Português", posterTag(item(lang = "pt", countries = listOf("PT")), 0, today, monthAgo)?.text)
        // Inglês não vira etiqueta. Nota alta com muito voto vira Imperdível.
        assertEquals("Imperdível", posterTag(item(rating = 8.4, votes = 900), 0, today, monthAgo)?.text)
        assertNull(posterTag(item(rating = 8.4, votes = 120), 0, today, monthAgo))
        assertNull(posterTag(item(release = ""), 0, today, monthAgo))
        assertNull(posterTag(item(release = "2026"), 0, today, monthAgo))
    }

    @Test fun faixaTemAnoEGeneroCurto() {
        assertEquals(listOf("2020", "Ficção"), stripParts(item(genres = listOf(9999, 878, 28))))
        assertEquals(listOf("2020"), stripParts(item(genres = listOf(9999))))
        assertEquals(listOf("Série"), stripParts(item(MediaType.SERIES, year = "")))
        assertEquals("7,5", ratingText(7.46))
        assertEquals("10,0", ratingText(10.0))
    }

    @Test fun resolucaoAbaixoDe720NaoViraSelo() {
        assertEquals("4K", qualityLabel(2160))
        assertEquals("2K", qualityLabel(1440))
        assertEquals("1080p", qualityLabel(1080))
        assertEquals("720p", qualityLabel(720))
        assertNull(qualityLabel(480))
        assertNull(qualityLabel(0))
    }

    private fun px(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun corDoPoster() {
        assertEquals(0, hueOf(IntArray(96) { px(220, 30, 30) }))
        assertEquals(240, hueOf(IntArray(96) { px(30, 30, 220) }))
        assertEquals(120, hueOf(IntArray(96) { px(30, 220, 30) }))
        // Cinza não tem cor, e azul com laranja em partes iguais se anulam.
        assertEquals(-1, hueOf(IntArray(96) { px(128, 128, 128) }))
        assertEquals(-1, hueOf(IntArray(96) { if (it % 2 == 0) px(30, 30, 220) else px(220, 125, 30) }))
        assertEquals(-1, hueOf(IntArray(0)))
        // Matiz negativo volta para 0 a 359.
        val magenta = hueOf(IntArray(96) { px(220, 30, 120) })
        assertTrue(magenta in 300..359)
    }

    @Test fun rankingPegaOs25PrimeirosSemRepetir() {
        val r = rankMap(listOf(10, 20, 10, 0, 30) + (100..140).toList())
        assertEquals(1, r[10])
        assertEquals(2, r[20])
        assertEquals(3, r[30])
        assertEquals(25, r.size)
        assertNull(r[0])
    }

    @Test fun resumoDasFontes() {
        val list = listOf(
            StremioStream("a", "u1", qualityRank = 1080, ptAudio = true),
            StremioStream("b", "u2", qualityRank = 2160, dolbyVision = true),
            StremioStream("c", "u3", qualityRank = 720, atmos = true),
        )
        val q = summarize(list, 5L)
        assertEquals(SourceQuality(2160, hdr = true, dolbyVision = true, atmos = true, ptAudio = true, n = 3, at = 5L), q)
        assertEquals(0, summarize(emptyList(), 0L).q)
        // Com fonte vale um dia, sem fonte 6 horas.
        val h = 3_600_000L
        assertTrue((null as SourceQuality?).stale(0L))
        assertFalse(q.copy(at = 0L).stale(23 * h))
        assertTrue(q.copy(at = 0L).stale(25 * h))
        assertTrue(q.copy(at = 0L, n = 0).stale(7 * h))
    }
}
