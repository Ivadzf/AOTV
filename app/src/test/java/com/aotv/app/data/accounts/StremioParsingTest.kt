package com.aotv.app.data.accounts

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioParsingTest {

    @Test
    fun `addon local e addon so de legenda ficam de fora`() {
        val torrentio = JSONObject("""{"transportUrl":"https://torrentio.strem.fun/manifest.json","manifest":{"name":"Torrentio","resources":["stream"]}}""")
        val cinemeta = JSONObject("""{"transportUrl":"https://v3-cinemeta.strem.io/manifest.json","manifest":{"name":"Cinemeta","resources":["catalog","meta",{"name":"stream"}]}}""")
        val local = JSONObject("""{"transportUrl":"http://127.0.0.1:11470/local-addon/manifest.json","manifest":{"name":"Local Files","resources":["catalog","stream"]}}""")
        val subs = JSONObject("""{"transportUrl":"https://opensubtitles-v3.strem.io/manifest.json","manifest":{"name":"OpenSubtitles","resources":["subtitles"]}}""")
        assertTrue(StremioAccountService.remoteAddon(torrentio)!!.canUse)
        assertTrue(StremioAccountService.remoteAddon(cinemeta)!!.canUse)
        assertFalse(StremioAccountService.remoteAddon(local)!!.canUse)
        assertFalse(StremioAccountService.remoteAddon(subs)!!.canUse)
        assertEquals("Torrentio", StremioAccountService.remoteAddon(torrentio)!!.name)
    }

    @Test
    fun `biblioteca separa lista e progresso e le o episodio do video_id`() {
        val arr = JSONArray(
            """[
            {"_id":"tt0111161","type":"movie","name":"Um Sonho de Liberdade","poster":"p1","removed":false,"temp":false,
             "_ctime":"2024-01-02T10:00:00.000Z","state":{"timeOffset":0,"duration":0}},
            {"_id":"tt0903747","type":"series","name":"Breaking Bad","poster":"p2","removed":false,"temp":true,
             "state":{"timeOffset":1200000,"duration":2800000,"video_id":"tt0903747:2:5","lastWatched":"2025-03-01T20:00:00.000Z"}},
            {"_id":"tt1","type":"movie","name":"Removido","removed":true,"temp":false,"state":{"timeOffset":5000}},
            {"_id":"yt_id:abc","type":"channel","name":"Canal"}
            ]"""
        )
        val (list, prog) = StremioAccountService.parseLibrary(arr)
        assertEquals(listOf("tt0111161"), list.map { it.id })
        assertEquals(1, prog.size)
        val p = prog[0]
        assertEquals(2, p.season)
        assertEquals(5, p.episode)
        assertEquals(1_200_000L, p.positionMs)
        assertTrue(p.series)
        assertTrue(p.watchedAt > 0)
    }
}
