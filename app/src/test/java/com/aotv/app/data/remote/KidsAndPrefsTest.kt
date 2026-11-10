package com.aotv.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsAndPrefsTest {

    @Test
    fun kidsOkPedeAnimacaoOuFamiliaSemTerror() {
        assertTrue(kidsOk(listOf(16, 35)))
        assertTrue(kidsOk(listOf(10762)))
        assertFalse(kidsOk(listOf(28, 35)))      // ação sem nada infantil
        assertFalse(kidsOk(listOf(16, 27)))      // animação de terror
        assertFalse(kidsOk(emptyList()))
    }

    @Test
    fun bloqueioPorClassificacaoFalhaFechado() {
        // BR 12 libera, BR 14 bloqueia.
        assertFalse(kidsBlocked(listOf(16), "12"))
        assertFalse(kidsBlocked(listOf(16), "L"))
        assertTrue(kidsBlocked(listOf(16), "14"))
        // Adulto bloqueia mesmo com gênero e classificação livres.
        assertTrue(kidsBlocked(listOf(16, 10751), "L", adult = true))
        // Só a dos EUA (o certBR cai nela quando não há a BR).
        assertFalse(kidsBlocked(listOf(16), "G"))
        assertFalse(kidsBlocked(listOf(16), "PG"))
        assertTrue(kidsBlocked(listOf(16), "PG-13"))
        assertTrue(kidsBlocked(listOf(16), "R"))
        assertFalse(kidsBlocked(listOf(10762), "TV-Y7"))
        assertFalse(kidsBlocked(listOf(10762), "TV-PG"))
        assertTrue(kidsBlocked(listOf(10762), "TV-14"))
        assertTrue(kidsBlocked(listOf(10762), "TV-MA"))
        // Sem classificação só passa animação infantil ou de família.
        assertTrue(kidsBlocked(listOf(16), ""))
        assertTrue(kidsBlocked(listOf(10751), ""))
        assertFalse(kidsBlocked(listOf(16, 10751), ""))
        assertFalse(kidsBlocked(listOf(16, 10762), ""))
        // Gênero fora do infantil bloqueia sempre.
        assertTrue(kidsBlocked(listOf(18), "L"))
    }

    @Test
    fun filtroJsonTiraTituloAdultoEPessoaMasMantemVideoEElenco() {
        val body = """{"results":[
            {"id":1,"genre_ids":[16,10751]},
            {"id":2,"genre_ids":[28]},
            {"id":3,"media_type":"person"},
            {"id":4,"genre_ids":[16],"adult":true}],
          "videos":{"results":[{"key":"abc","site":"YouTube"}]},
          "credits":{"cast":[{"id":9,"name":"Ator"}]},
          "recommendations":{"results":[{"id":5,"genre_ids":[27,16]},{"id":6,"genre_ids":[10762]}]}}"""
        val out = filterKidsJson(body)
        val ids = Regex("\"id\":(\\d+)").findAll(out).map { it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(1, 9, 6), ids)
        assertTrue(out.contains("\"key\":\"abc\""))
    }

    private data class S(val label: String, val q: Int)
    private fun sort(list: List<S>, audio: String, max: Int) = sortByPrefs(list, audio, max, { it.label }, { it.q })

    @Test
    fun ordenaPorIdiomaELimiteSemPerderOrdemDosAddons() {
        val a = S("A Legendado", 1080); val b = S("B Dublado", 720); val c = S("C", 2160); val d = S("D Dublado", 1080)
        val list = listOf(a, b, c, d)
        assertEquals(list, sort(list, "any", 0))
        // Dublado primeiro na ordem original, depois sem idioma, depois legendado.
        assertEquals(listOf(b, d, c, a), sort(list, "dub", 0))
        // Limite 1080 joga o 4K pro fim e ordena o resto pela maior qualidade.
        assertEquals(listOf(a, d, b, c), sort(list, "any", 1080))
        assertEquals(listOf(d, b, c, a), sort(list, "dub", 1080))
    }

    @Test
    fun bestIndexRespeitaQualidadeMaxima() {
        val l = listOf(
            SourceFacts("u1", "A", "x", 2160, ProbeResult(true, 300, 0.0, 2160)),
            SourceFacts("u2", "A", "x", 1080, ProbeResult(true, 300, 0.0, 1080)),
        )
        assertEquals(0, bestIndex(l))
        assertEquals(1, bestIndex(l, maxQuality = 1080))
    }
}
