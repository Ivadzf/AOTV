package com.aotv.app.data.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

/** Motor real baixando o Sintel (Blender, Creative Commons). Usa rede, roda só no Windows com TORRENT_IT=1. */
class TorrentStreamerIT {

    @Test fun sintelServeOsBytesCertosDoArquivoDeVideo() {
        assumeTrue(System.getenv("TORRENT_IT") == "1" && System.getProperty("os.name").lowercase().contains("windows"))
        val dll = Files.createTempFile("libtorrent4j", ".dll").toFile().apply { deleteOnExit() }
        javaClass.classLoader!!.getResourceAsStream("lib/x86_64/libtorrent4j.dll")!!.use { i -> dll.outputStream().use { i.copyTo(it) } }
        System.setProperty("libtorrent4j.jni.path", dll.absolutePath)

        val root = Files.createTempDirectory("torrent-it").toFile()
        val engine = TorrentStreamer(root)
        val spec = TorrentSpec(
            "08ada5a7a6183aae1e09d831df6748d566095a10",
            listOf(
                "udp://tracker.opentrackr.org:1337/announce", "udp://explodie.org:6969/announce",
                "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce",
                "udp://exodus.desync.com:6969/announce", "udp://open.demonii.com:1337/announce",
            ),
            fileName = "Sintel",
            webSeeds = listOf("https://webtorrent.io/torrents/"),
        )
        val url = engine.playUrl(TorrentMagnet.build(spec))
        try {
            // Começo do Sintel.mp4, que no torrent vem depois das legendas (arquivo desalinhado com as peças).
            val (head, total) = get(url, "bytes=0-65535")
            assertEquals(65536, head.size)
            assertEquals("ftyp", String(head, 4, 4, Charsets.US_ASCII))
            println("Sintel.mp4 com $total bytes, começo ok")

            // Um trecho do meio e o fim batem com o que ficou no disco.
            for (start in listOf(total / 2, total - 70_000)) {
                val (part, _) = get(url, "bytes=$start-${start + 49_999}")
                val disk = File(root, spec.infoHash).walkTopDown().first { it.name == "Sintel.mp4" }
                val expected = ByteArray(part.size).also { RandomAccessFile(disk, "r").use { f -> f.seek(start); f.readFully(it) } }
                assertArrayEquals(expected, part)
                println("trecho em $start ok")
            }
        } finally {
            engine.stopAll()
            root.deleteRecursively()
        }
    }

    private fun get(url: String, range: String): Pair<ByteArray, Long> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Range", range)
        c.connectTimeout = 5_000
        c.readTimeout = 180_000
        assertEquals(206, c.responseCode)
        val total = c.getHeaderField("Content-Range").substringAfterLast('/').toLong()
        return c.inputStream.use { it.readBytes() } to total
    }
}
