package com.aotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentMagnetTest {

    private val hash = "C301B175CAE6AE4870076BD478680B6E3FC13743"

    @Test fun magnetVaiEVoltaComAsDicas() {
        val spec = TorrentSpec(hash.lowercase(), listOf("udp://b:1/announce", "udp://a:1/announce"), 3, "A Origem 1080p", "[sS]0*1", listOf("https://seed/x"))
        val back = TorrentMagnet.parse(TorrentMagnet.build(spec))!!
        assertEquals(hash.lowercase(), back.infoHash)
        assertEquals(listOf("udp://a:1/announce", "udp://b:1/announce"), back.trackers)
        assertEquals(3, back.fileIdx)
        assertEquals("A Origem 1080p", back.fileName)
        assertEquals("[sS]0*1", back.mustInclude)
        assertEquals(listOf("https://seed/x"), back.webSeeds)
    }

    @Test fun mesmaFonteGeraMesmaUrlComTrackersEmOutraOrdem() {
        val a = TorrentMagnet.fromStremio(hash, listOf("tracker:udp://b:1", "tracker:udp://a:1", "dht:$hash"), null, null, null, null, null)!!
        val b = TorrentMagnet.fromStremio(hash, listOf("tracker:udp://a:1", "tracker:udp://b:1"), null, null, null, null, null)!!
        assertEquals(TorrentMagnet.build(a), TorrentMagnet.build(b))
        assertEquals(listOf("udp://b:1", "udp://a:1"), a.trackers)
    }

    @Test fun serieSemDicaGanhaPadraoDoEpisodio() {
        val s = TorrentMagnet.fromStremio(hash, null, null, null, null, 2, 3)!!
        val r = Regex(s.mustInclude!!)
        assertTrue(r.containsMatchIn("Breaking.Bad.S02E03.720p.mkv"))
        assertTrue(r.containsMatchIn("Breaking Bad 2x03.avi"))
        assertFalse(r.containsMatchIn("Breaking.Bad.S02E13.mkv"))
        assertFalse(r.containsMatchIn("Breaking.Bad.S12E03.mkv"))
        assertNull(TorrentMagnet.fromStremio(hash, null, 5, null, null, 2, 3)!!.mustInclude)
        assertNull(TorrentMagnet.fromStremio("nao-e-hex", null, null, null, null, null, null))
    }

    @Test fun pacoteDeTemporadaTocaOEpisodioPedidoENaoOMaior() {
        val files = listOf(
            TorrentFile(0, "BB.S02/BB.S02E01.mkv", 900),
            TorrentFile(1, "BB.S02/BB.S02E03.mkv", 700),
            TorrentFile(2, "BB.S02/BB.S02E10.mkv", 950),
            TorrentFile(3, "BB.S02/Sample/BB.S02E03-sample.mkv", 10),
            TorrentFile(4, "BB.S02/info.nfo", 2),
        )
        val ep = TorrentSpec(hash, emptyList(), mustInclude = TorrentMagnet.episodePattern(2, 3))
        assertEquals(1, TorrentFiles.pick(files, ep)!!.index)
        assertEquals(4, TorrentFiles.pick(files, ep.copy(fileIdx = 4))!!.index)
        assertEquals(2, TorrentFiles.pick(files, TorrentSpec(hash, emptyList()))!!.index)
    }

    @Test fun dicaDeNomeEscolheArquivoParecido() {
        val files = listOf(TorrentFile(0, "Sintel/Sintel.mp4", 100), TorrentFile(1, "Sintel/extras.mp4", 900))
        assertEquals(0, TorrentFiles.pick(files, TorrentSpec(hash, emptyList(), fileName = "Sintel"))!!.index)
        assertNotNull(TorrentFiles.pick(listOf(TorrentFile(0, "leia.txt", 5)), TorrentSpec(hash, emptyList())))
    }

    @Test fun pecaContaOInicioDoArquivoDentroDoTorrent() {
        // Arquivo começa no byte 1000 de um torrent com peças de 512, então o byte 0 dele está na peça 1.
        assertEquals(1, TorrentFiles.pieceAt(1000, 0, 512))
        assertEquals(1, TorrentFiles.pieceAt(1000, 23, 512))
        assertEquals(2, TorrentFiles.pieceAt(1000, 24, 512))
        assertEquals(0, TorrentFiles.pieceAt(0, 511, 512))
    }

    @Test fun seedersDoRotulo() {
        assertEquals(15, TorrentMagnet.seeders("🔵 54/100 · 👤 15 · 💾 2.2 GB"))
        assertEquals(0, TorrentMagnet.seeders("FrostStream 1080p"))
    }
}
