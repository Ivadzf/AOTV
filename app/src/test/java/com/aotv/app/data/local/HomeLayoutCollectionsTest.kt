package com.aotv.app.data.local

import com.aotv.app.data.collections.MediaCollection
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutCollectionsTest {

    private fun keys(list: List<HomeRowPref>) = list.map { it.key }

    @Test
    fun colecaoNovaEntraNoFimEAFixadaSobeParaOTopo() {
        val saved = HomeLayout.default(emptyList())
        val cols = listOf(MediaCollection("a", "Estúdios"), MediaCollection("b", "Streamings", pinToTop = true))
        val out = HomeLayout.normalize(saved, emptySet(), emptyList(), emptySet(), cols)
        assertEquals("col:b", out.first().key)
        assertEquals("Streamings", out.first().displayTitle)
        // Gêneros continua no fim, a coleção não fixada fica antes dele
        assertEquals(SystemRow.GENRES.key, out.last().key)
        assertEquals("col:a", out[out.size - 2].key)
    }

    @Test
    fun colecaoApagadaSaiEONomeDadoPelaPessoaFica() {
        val saved = listOf(HomeRowPref("col:a", true, "Meu nome"), HomeRowPref("col:x", false))
        val out = HomeLayout.normalize(saved, emptySet(), emptyList(), emptySet(), listOf(MediaCollection("a", "Estúdios")))
        val a = out.single { it.collectionId != null }
        assertEquals("a", a.collectionId)
        assertEquals("Meu nome", a.displayTitle)
        assertEquals("Estúdios", a.defaultTitle)
    }

    @Test
    fun chaveDeColecaoVoltaDoArquivo() {
        val list = listOf(HomeRowPref("col:a", false, "X"), HomeRowPref(SystemRow.CONTINUE.key))
        assertEquals(keys(list), keys(HomeLayout.decode(HomeLayout.encode(list))!!))
        assertEquals(false, HomeLayout.decode(HomeLayout.encode(list))!!.first().enabled)
    }
}
