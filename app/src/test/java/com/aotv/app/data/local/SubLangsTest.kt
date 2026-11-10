package com.aotv.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubLangsTest {
    @Test
    fun reconheceCodigosDeFontesDiferentes() {
        assertEquals("pob", SubLangs.of("pt-BR")?.key)
        assertEquals("pob", SubLangs.of("pob")?.key)
        assertEquals("por", SubLangs.of("pt")?.key)
        assertEquals("por", SubLangs.of("pt-AO")?.key)
        assertEquals("fre", SubLangs.of("fra")?.key)
        assertEquals("spl", SubLangs.of("es_419")?.key)
        assertEquals("chi", SubLangs.of("zh-Hant-TW")?.key)
        assertNull(SubLangs.of("und"))
        assertNull(SubLangs.of(""))
        assertEquals("Inglês", SubLangs.label("eng"))
        assertEquals("XHO", SubLangs.label("xho"))
        assertEquals("Legenda", SubLangs.label(null))
    }

    @Test
    fun ordenaPeloIdiomaEscolhido() {
        val langs = listOf("eng", "jpn", "por", "pob", "xho")
        assertEquals(listOf("pob", "por", "eng", "jpn", "xho"), langs.sortedBy { SubLangs.rank(it, "pob") })
        assertEquals(listOf("eng", "pob", "por", "jpn", "xho"), langs.sortedBy { SubLangs.rank(it, "eng") })
    }

    @Test
    fun escolheFaixaDoVideoAntesDaExternaENaoLigaIdiomaErrado() {
        // Mesmo idioma nos dois lados, a faixa do vídeo ganha
        assertEquals(SubLangs.Pick(true, 1), SubLangs.choose(listOf("eng", "pt-BR"), listOf("pob"), "pob"))
        // Idioma exato só na externa ganha do vizinho no vídeo
        assertEquals(SubLangs.Pick(false, 1), SubLangs.choose(listOf("por"), listOf("eng", "pob"), "pob"))
        // Sem o exato, o vizinho serve
        assertEquals(SubLangs.Pick(true, 0), SubLangs.choose(listOf("por"), listOf("eng"), "pob"))
        // Faixa sem idioma marcado só quando nada melhor existe
        assertEquals(SubLangs.Pick(true, 1), SubLangs.choose(listOf("eng", "und"), listOf("fre"), "pob"))
        // Nada no idioma escolhido, nada liga
        assertNull(SubLangs.choose(listOf("eng"), listOf("fre"), "pob"))
        assertEquals(SubLangs.Pick(false, 0), SubLangs.choose(emptyList(), listOf("en"), "eng"))
    }
}
