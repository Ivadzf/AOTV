package com.aotv.app.ui.movies

import com.aotv.app.data.remote.StremioStream
import org.junit.Assert.*
import org.junit.Test

class ReleaseInfoTest {
    @Test fun mesmoArquivoEmAddonsDiferentesViraUmaVersao() {
        val a = StremioStream("1080p", "https://a.invalid/1", "Addon A", fileName = "Video.1080p.mkv", fileSize = 2_000_000_000)
        val b = a.copy(url = "https://b.invalid/2", addon = "Addon B")
        assertEquals(1, groupReleases(listOf(a, b)).size)
        assertEquals(listOf(a, b), groupReleases(listOf(a, b)).single().members)
        assertEquals(2, groupReleases(listOf(a, b.copy(fileSize = 3_000_000_000))).size)
    }

    @Test fun mesmaQualidadeSemIdentidadeNaoAgrupaArquivosDiferentes() {
        val a = StremioStream("1080p", "https://a.invalid/1", "Addon A")
        assertEquals(2, groupReleases(listOf(a, a.copy(url = "https://b.invalid/2"))).size)
        assertEquals(1, groupReleases(listOf(a, a.copy(addon = "Addon B"))).size)
    }

    @Test fun formatosNoFimDoTextoInteiroContinuamVisiveis() {
        val stream = StremioStream("Vídeo", "https://a.invalid/1", "Addon A",
            releaseText = "Nome muito comprido ".repeat(12) + "2160p WEB-DL HEVC Dolby Vision HDR10+ Atmos 7.1 Dublado 👤 124 5.25 GB",
            fileName = "Video.2160p.mkv")
        val info = releaseInfo(stream, "Vídeo", listOf("Addon A", "Addon B"))
        assertEquals("4K", info.quality)
        assertTrue(info.tags.containsAll(listOf("Dolby Vision", "HDR10+", "Atmos", "7.1")))
        assertEquals("Dublado", info.language)
        assertEquals("5,25 GB", info.size)
        assertEquals(124, info.seeds)
        assertEquals("Video.2160p.mkv", info.fileName)
        assertEquals(listOf("Addon A", "Addon B"), info.providers)
    }

    @Test fun legendaEmPortuguesNaoViraDublagem() {
        val s = StremioStream("Legendado PT-BR", "https://a.invalid/1", releaseText = "1080p Legendado PT-BR")
        assertEquals("Legendado", releaseInfo(s, "").language)
        assertEquals("leg", com.aotv.app.data.remote.langOf(s.releaseText))
    }

    @Test fun seedsETamanhoNaoViraramQualidade() {
        val s = StremioStream("Fonte", "https://a.invalid/1", releaseText = "👤 1080 · 2160 MB · HDRip · 4KHDHub")
        val info = releaseInfo(s, "")
        assertEquals("", info.quality)
        assertTrue(info.tags.isEmpty())
        assertEquals(1080, info.seeds)
    }

    @Test fun velocidadeIncluiSetentaECincoPorCentoEExibeVirgula() {
        assertEquals(listOf(.75f, 1f, 1.25f, 1.5f, 2f), PLAYBACK_SPEEDS)
        assertEquals("1x", playbackSpeedText(1f))
        assertEquals("0,75x", playbackSpeedText(.75f))
        assertEquals("1,25x", playbackSpeedText(1.25f))
        assertEquals("21h00  ·  termina às 22h20", clockLine(21 * 3_600_000L, 0, 3_600_000L, .75f, java.util.TimeZone.getTimeZone("UTC")))
    }

    @Test fun resolucaoDeCinemaUsaALarguraRealDoVideo() {
        assertEquals("1080p", videoResolutionLabel(1920, 800))
        assertEquals("4K", videoResolutionLabel(3840, 1600))
        assertEquals("2K", videoResolutionLabel(2560, 1080))
        assertEquals("720p", videoResolutionLabel(1280, 536))
        assertEquals("360p", videoResolutionLabel(640, 360))
        assertEquals("", videoResolutionLabel(0, 0))
    }
}
