package com.aotv.app.data.debrid

import com.aotv.app.data.remote.SourceFacts
import com.aotv.app.data.remote.bestIndex
import com.aotv.app.data.torrent.TorrentFile
import com.aotv.app.data.torrent.TorrentFiles
import com.aotv.app.data.torrent.TorrentSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rede de mentira. Cada chamada procura a primeira regra cujo trecho aparece na URL. */
private class FakeHttp(private val rules: MutableList<Pair<String, () -> DebridHttp.Res>>) : DebridHttp(null) {
    val calls = mutableListOf<String>()
    private fun answer(method: String, url: String): DebridHttp.Res {
        calls += "$method $url"
        val i = rules.indexOfFirst { url.contains(it.first) }
        return if (i < 0) DebridHttp.Res(404, "{}") else rules[i].second()
    }
    override fun get(url: String, bearer: String?) = answer("GET", url)
    override fun form(url: String, fields: List<Pair<String, String>>, bearer: String?) = answer("POST", url)
    override fun json(url: String, body: String, bearer: String?) = answer("POST", url)
    override fun multipart(url: String, fields: List<Pair<String, String>>, bearer: String?) = answer("POST", url)
    override fun delete(url: String, bearer: String?) = answer("DELETE", url)
}

private fun res(body: String, code: Int = 200) = DebridHttp.Res(code, body)

class DebridApisTest {
    private val hash = "a".repeat(40)
    private val magnet = "magnet:?xt=urn:btih:$hash"
    private val pack = """[{"id":1,"path":"/Show/Show.S01E01.1080p.mkv","bytes":900,"selected":0},
        {"id":2,"path":"/Show/Show.S01E02.1080p.mkv","bytes":950,"selected":0},
        {"id":3,"path":"/Show/sample.mkv","bytes":10,"selected":0}]"""

    @Test
    fun `real-debrid com cache devolve o link do episodio certo`() {
        var infoCalls = 0
        val http = FakeHttp(mutableListOf(
            "addMagnet" to { res("""{"id":"T1"}""", 201) },
            "torrents/info/T1" to {
                infoCalls++
                if (infoCalls == 1) res("""{"status":"waiting_files_selection","files":$pack,"links":[]}""")
                else res("""{"status":"downloaded","files":${pack.replace("\"id\":2,\"path\":\"/Show/Show.S01E02.1080p.mkv\",\"bytes\":950,\"selected\":0", "\"id\":2,\"path\":\"/Show/Show.S01E02.1080p.mkv\",\"bytes\":950,\"selected\":1")},"links":["https://real-debrid.com/d/E2"]}""")
            },
            "selectFiles/T1" to { res("", 204) },
            "unrestrict/link" to { res("""{"download":"https://cdn.rd/E2.mkv"}""") },
        ))
        val r = RealDebridApi(http).resolve("tok", magnet, TorrentSpec(hash, emptyList()), season = 1, episode = 2)
        assertEquals(DebridResolve.Ok("https://cdn.rd/E2.mkv", DebridProvider.REAL_DEBRID), r)
        assertTrue("não pode apagar torrent que serviu", http.calls.none { it.startsWith("DELETE") })
    }

    @Test
    fun `real-debrid sem cache apaga o torrent e avisa`() {
        var infoCalls = 0
        val http = FakeHttp(mutableListOf(
            "addMagnet" to { res("""{"id":"T2"}""", 201) },
            "torrents/info/T2" to {
                infoCalls++
                if (infoCalls == 1) res("""{"status":"waiting_files_selection","files":$pack}""")
                else res("""{"status":"downloading","progress":1,"files":$pack,"links":[]}""")
            },
            "selectFiles/T2" to { res("", 204) },
            "torrents/delete/T2" to { res("", 204) },
        ))
        val r = RealDebridApi(http).resolve("tok", magnet, TorrentSpec(hash, emptyList()), 1, 1)
        assertEquals(DebridResolve.NotCached, r)
        assertTrue(http.calls.any { it == "DELETE https://api.real-debrid.com/rest/1.0/torrents/delete/T2" })
    }

    @Test
    fun `real-debrid sem premium nao tenta de novo`() {
        val http = FakeHttp(mutableListOf("addMagnet" to { res("""{"error":"permission_denied"}""", 403) }))
        val r = RealDebridApi(http).resolve("tok", magnet, TorrentSpec(hash, emptyList()), 0, 0)
        assertTrue(r is DebridResolve.Failed)
    }

    @Test
    fun `torbox le o cache e resolve pelo id do arquivo`() {
        val http = FakeHttp(mutableListOf(
            "checkcached" to { res("""{"success":true,"data":{"$hash":{"name":"x","size":1}}}""") },
            "createtorrent" to { res("""{"success":true,"data":{"torrent_id":77}}""") },
            "mylist" to { res("""{"success":true,"data":{"id":77,"files":[{"id":0,"name":"Movie/sample.mp4","size":5},{"id":1,"name":"Movie/Movie.2010.mkv","size":800}]}}""") },
            "requestdl" to { res("""{"success":true,"data":"https://tb.cdn/Movie.mkv"}""") },
        ))
        val api = TorBoxApi(http)
        assertEquals(setOf(hash), api.cached("k", listOf(hash, "b".repeat(40))))
        val r = api.resolve("k", magnet, TorrentSpec(hash, emptyList()), 0, 0)
        assertEquals(DebridResolve.Ok("https://tb.cdn/Movie.mkv", DebridProvider.TORBOX), r)
        assertTrue(http.calls.any { it.contains("file_id=1") })
    }

    @Test
    fun `torbox sem cache vira NotCached`() {
        val http = FakeHttp(mutableListOf("createtorrent" to { res("""{"success":false,"detail":"Torrent is not cached"}""", 400) }))
        assertEquals(DebridResolve.NotCached, TorBoxApi(http).resolve("k", magnet, TorrentSpec(hash, emptyList()), 0, 0))
    }

    @Test
    fun `premiumize ignora o fileIdx e acha o episodio pelo nome`() {
        val http = FakeHttp(mutableListOf(
            "cache/check" to { res("""{"status":"success","response":[true,false]}""") },
            "directdl" to {
                res("""{"status":"success","content":[
                    {"path":"Show/Show.S02E05.mkv","size":700,"link":"https://pm/5"},
                    {"path":"Show/Show.S02E04.mkv","size":900,"link":"https://pm/4"}]}""")
            },
        ))
        val api = PremiumizeApi(http)
        assertEquals(setOf(hash), api.cached("k", listOf(hash, "b".repeat(40))))
        // fileIdx 0 apontaria para o E05 se a ordem fosse confiável, e o episódio pedido é o E04.
        val r = api.resolve("k", magnet, TorrentSpec(hash, emptyList(), fileIdx = 0), 2, 4)
        assertEquals(DebridResolve.Ok("https://pm/4", DebridProvider.PREMIUMIZE), r)
    }

    @Test
    fun `escolha do arquivo segue nome exato, padrao e fileIdx`() {
        val files = listOf(
            TorrentFile(0, "Pack/Show.S01E01.mkv", 500),
            TorrentFile(1, "Pack/Show.S01E02.mkv", 400),
            TorrentFile(2, "Pack/extras/featurette.mkv", 900),
        )
        val byName = TorrentSpec(hash, emptyList(), fileName = "show.s01e02.mkv")
        assertEquals(1, TorrentFiles.pickDebrid(files, byName, indexReliable = true)?.index)
        val byIdx = TorrentSpec(hash, emptyList(), fileIdx = 0)
        assertEquals(0, TorrentFiles.pickDebrid(files, byIdx, indexReliable = true)?.index)
        // Sem ordem confiável e sem dica, o episódio da tela decide.
        assertEquals(1, TorrentFiles.pickDebrid(files, byIdx, indexReliable = false, season = 1, episode = 2)?.index)
        assertEquals(2, TorrentFiles.pickDebrid(files, TorrentSpec(hash, emptyList()), indexReliable = false)?.index)
    }

    @Test
    fun `torrent pronto no debrid passa na frente de p2p maior`() {
        val p2p4k = SourceFacts("magnet:?xt=urn:btih:${"c".repeat(40)}", "T", "4K 👤 900", 2160)
        val rd1080 = SourceFacts("magnet:?xt=urn:btih:${"d".repeat(40)}", "T", "1080p 👤 10", 1080, debrid = 2)
        assertEquals(1, bestIndex(listOf(p2p4k, rd1080)))
        assertTrue(!rd1080.p2p)
    }

    @Test
    fun `data iso do servico vira epoch`() {
        assertEquals(1_764_547_200_000L, isoMs("2025-12-01T00:00:00.000Z"))
        assertEquals(1_764_547_200_000L, isoMs("2025-12-01T00:00:00+00:00"))
        assertEquals(0L, isoMs(""))
    }
}
