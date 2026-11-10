package com.aotv.app.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveParsersTest {

    @Test
    fun `m3u le grupo, logo, guia, cabecalhos e nome com virgula nas aspas`() {
        val text = """
            #EXTM3U url-tvg="http://guia.tv/epg.xml.gz,http://outro.tv/epg.xml"
            #EXTINF:-1 tvg-id="globo.br" tvg-name="Globo" tvg-logo="http://img/globo.png" group-title="Abertos, Brasil",Globo HD
            #EXTVLCOPT:http-user-agent=VLC/3.0
            #EXTVLCOPT:http-referrer=http://site.tv/
            http://stream.tv/globo.m3u8
            #EXTINF:-1 tvg-chno="7",Canal Sem Grupo
            http://stream.tv/sem.ts|User-Agent=Kodi%2F20&Referer=http://ref/
            #EXTINF:-1,Repetido
            http://stream.tv/globo.m3u8
        """.trimIndent()
        val r = M3uParser.parse(text, "s1")
        assertEquals(listOf("http://guia.tv/epg.xml.gz", "http://outro.tv/epg.xml"), r.epgUrls)
        assertEquals(2, r.channels.size)
        val g = r.channels[0]
        assertEquals("Globo HD", g.name)
        assertEquals("Abertos, Brasil", g.group)
        assertEquals("globo.br", g.epgId)
        assertEquals("http://img/globo.png", g.logo)
        assertEquals("VLC/3.0", g.headers["User-Agent"])
        assertEquals("http://site.tv/", g.headers["Referer"])
        val s = r.channels[1]
        assertEquals("http://stream.tv/sem.ts", s.url)
        assertEquals("Kodi/20", s.headers["User-Agent"])
        assertEquals(7, s.number)
        assertTrue("cabeçalho de um canal não vaza para o seguinte", s.headers["Referer"] == "http://ref/")
        // Mesmo canal na próxima leitura tem o mesmo id.
        assertEquals(g.id, M3uParser.parse(text, "s1").channels[0].id)
    }

    @Test
    fun `xtream monta a url do canal e le a validade da conta`() {
        val acc = XtreamParser.account("""{"user_info":{"auth":1,"status":"Active","exp_date":"1767225600","max_connections":"2","allowed_output_formats":["m3u8","ts"]}}""")!!
        assertEquals(1_767_225_600_000L, acc.expiresAt)
        assertEquals(listOf("m3u8", "ts"), acc.formats)
        val src = LiveSource("x1", "Conta", LiveSourceType.XTREAM, server = "http://srv.tv:8080", user = "joao silva", pass = "p@ss")
        val groups = XtreamParser.categories("""[{"category_id":"3","category_name":"Esportes"}]""")
        val ch = XtreamParser.live("""[{"num":1,"name":"Sport 1","stream_id":99,"stream_icon":"http://i/s.png","epg_channel_id":"sport1.br","category_id":"3"}]""", src, groups, "m3u8")
        assertEquals("http://srv.tv:8080/live/joao%20silva/p%40ss/99.m3u8", ch[0].url)
        assertEquals("Esportes", ch[0].group)
        assertEquals("x1:99", ch[0].id)
        assertEquals(null, XtreamParser.account("""{"user_info":{"auth":0}}"""))
    }

    @Test
    fun `servidor xtream aceita endereco sem esquema ou com player_api`() {
        assertEquals("http://srv.tv:8080", LiveTvRepository.normalizeServer("srv.tv:8080"))
        assertEquals("https://srv.tv", LiveTvRepository.normalizeServer("https://srv.tv/player_api.php?username=a"))
        assertEquals(null, LiveTvRepository.normalizeServer("  "))
    }

    @Test
    fun `guia casa por id e por nome e respeita o fuso`() {
        val now = XmltvParser.time("20261108200000 +0000")
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="Globo.br"><display-name>Globo</display-name></channel>
              <channel id="xyz"><display-name>Band HD</display-name></channel>
              <programme channel="Globo.br" start="20261108193000 +0000" stop="20261108210000 +0000"><title>Jornal</title><desc>Notícias &amp; mais</desc></programme>
              <programme channel="Globo.br" start="20261108180000 -0300" stop="20261108190000 -0300"><title>Novela</title></programme>
              <programme channel="xyz" start="20261108200000 +0000" stop="20261108220000 +0000"><title>Futebol</title></programme>
              <programme channel="outro" start="20261108200000 +0000" stop="20261108210000 +0000"><title>Ninguém</title></programme>
              <programme channel="Globo.br" start="20261101000000 +0000" stop="20261101010000 +0000"><title>Velho</title></programme>
            </tv>"""
        val out = XmltvParser.parse(
            xml.byteInputStream(),
            wanted = mapOf("globo.br" to listOf("c1")),
            names = mapOf(XmltvParser.norm("Band") to listOf("c2")),
            now = now,
        )
        val globo = out["c1"]!!
        assertEquals(listOf("Jornal", "Novela"), globo.map { it.title })
        assertEquals("Notícias & mais", globo[0].desc)
        // 18:00 em -0300 é 21:00 UTC.
        assertEquals(XmltvParser.time("20261108210000 +0000"), globo[1].start)
        assertEquals(listOf("Futebol"), out["c2"]!!.map { it.title })
        assertEquals(2, out.size)
    }

    @Test
    fun `nome normalizado ignora qualidade, acento e prefixo de pais`() {
        assertEquals(XmltvParser.norm("Band"), XmltvParser.norm("BR: Band HD"))
        assertEquals(XmltvParser.norm("Récord"), XmltvParser.norm("Record [FHD]"))
    }
}
