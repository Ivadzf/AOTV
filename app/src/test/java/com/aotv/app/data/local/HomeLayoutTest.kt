package com.aotv.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private val a = "https://a.com"
    private val b = "https://b.com"
    private fun ref(base: String, id: String, type: String = "movie") = HomeCatalogRef(base, type, id, "Cat $id", "Addon")

    @Test
    fun `quem ja usava mantem as fileiras marcadas e o resto do addon antigo entra desligado`() {
        val legacy = listOf(ref(a, "top"))
        val saved = HomeLayout.default(legacy)
        val out = HomeLayout.normalize(saved, setOf(a), listOf(ref(a, "top"), ref(a, "novos")), autoOnBases = emptySet())
        assertEquals(SystemRow.CONTINUE.key, out[0].key)
        assertEquals(SystemRow.NEW_EPISODES.key, out[1].key)
        assertEquals(ref(a, "top").key, out[2].key)
        assertTrue(out[2].enabled)
        val novos = out.first { it.key == ref(a, "novos").key }
        assertFalse(novos.enabled)
        assertEquals(SystemRow.GENRES.key, out.last().key)
    }

    @Test
    fun `addon instalado depois entra ligado no fim antes dos generos`() {
        val saved = HomeLayout.default(emptyList())
        val out = HomeLayout.normalize(saved, setOf(b), listOf(ref(b, "x"), ref(b, "y", "series")), autoOnBases = setOf(b))
        val keys = out.map { it.key }
        assertTrue(out.first { it.key == ref(b, "x").key }.enabled)
        assertTrue(out.first { it.key == ref(b, "y", "series").key }.enabled)
        assertEquals(SystemRow.GENRES.key, keys.last())
        assertEquals(keys.indexOf(ref(b, "x").key) + 1, keys.indexOf(ref(b, "y", "series").key))
    }

    @Test
    fun `addon removido sai e addon sem manifest ainda guarda a escolha`() {
        val saved = HomeLayout.default(listOf(ref(a, "top"), ref(b, "z")))
        // b continua instalado mas o manifest dele ainda não foi lido. a foi removido.
        val out = HomeLayout.normalize(saved, setOf(b), emptyList(), autoOnBases = emptySet())
        assertNull(out.firstOrNull { it.key == ref(a, "top").key })
        assertTrue(out.first { it.key == ref(b, "z").key }.enabled)
    }

    @Test
    fun `nome dado pela pessoa fica e nome do catalogo acompanha o manifest`() {
        val saved = listOf(HomeRowPref(ref(a, "top").key, true, "Meus filmes", ref(a, "top")))
        val renamed = HomeCatalogRef(a, "movie", "top", "Top da semana", "Addon A")
        val out = HomeLayout.normalize(saved, setOf(a), listOf(renamed), emptySet())
        val r = out.first { it.key == renamed.key }
        assertEquals("Meus filmes", r.displayTitle)
        assertEquals("Top da semana", r.defaultTitle)
    }

    @Test
    fun `mover troca com a vizinha visivel e generos nao sai do fim`() {
        val l = HomeLayout.normalize(HomeLayout.default(emptyList()), emptySet(), emptyList(), emptySet())
        val first = l[0].key
        val second = l[1].key
        val down = HomeLayout.move(l, first, 1)
        assertEquals(second, down[0].key)
        assertEquals(first, down[1].key)
        assertTrue(HomeLayout.move(l, first, -1) === l)
        assertTrue(HomeLayout.move(l, SystemRow.GENRES.key, -1) === l)
        val beforeGenres = l[l.size - 2].key
        assertTrue(HomeLayout.move(l, beforeGenres, 1) === l)
        // Com filtro, pula as desligadas.
        val off = l.mapIndexed { i, r -> if (i == 1) r.copy(enabled = false) else r }
        val jumped = HomeLayout.move(off, off[0].key, 1) { it.enabled }
        assertEquals(off[0].key, jumped[2].key)
    }

    @Test
    fun `restaurar liga as fixas, tira nomes e mantem as de addon`() {
        val cur = listOf(
            HomeRowPref(SystemRow.GENRES.key, false),
            HomeRowPref(ref(a, "top").key, false, "Outro nome", ref(a, "top")),
            HomeRowPref(SystemRow.CONTINUE.key, false, "Retomar"),
        )
        val out = HomeLayout.reset(cur)
        assertEquals(SystemRow.CONTINUE.key, out[0].key)
        assertTrue(out[0].enabled)
        assertEquals("", out[0].title)
        val addon = out.first { it.ref != null }
        assertFalse(addon.enabled)
        assertEquals("", addon.title)
        assertEquals(SystemRow.GENRES.key, out.last().key)
    }

    @Test
    fun `salvar e ler devolve o mesmo layout`() {
        val l = listOf(
            HomeRowPref(SystemRow.TRAILERS.key, false, "Trailers novos"),
            HomeRowPref(ref(a, "top").key, true, "", ref(a, "top")),
        )
        assertEquals(l, HomeLayout.decode(HomeLayout.encode(l)))
        assertNull(HomeLayout.decode(""))
        val addons = mapOf(a to CachedAddon("A", listOf(com.aotv.app.data.remote.AddonCatalog("movie", "top", "Top"))))
        assertEquals(addons, HomeLayout.decodeAddons(HomeLayout.encodeAddons(addons)))
    }
}
