package com.aotv.app.data.plugins

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Ponte do require('cheerio') para o jsoup. O lado JS (PluginPrelude) implementa
 * a API do Cheerio ($, find, text, attr, each, map, first, eq, filter, html,
 * parent, children, length, índices numéricos) sobre primitivas numeradas
 * aqui: cada seleção/elemento vira um "handle" Int, e o JS só guarda números.
 *
 * Subconjunto dimensionado pelo uso real dos 55 providers do repo do Davi
 * (ver PluginPrelude): load, $, find, text, attr, each, map (+get), first, eq,
 * filter, html, parent, children, length. Nada de get() com índice, .data(),
 * .val() ou manipulação de DOM — nenhum provider usa.
 *
 * Uma instância por execução de plugin; morre junto com o contexto QuickJS.
 */
class PluginCheerio {

    private var nextHandle = 1
    private val handles = HashMap<Int, Any>()

    private fun store(value: Any): Int {
        val h = nextHandle++
        handles[h] = value
        return h
    }

    /** load(html) -> handle do Document. */
    fun load(html: String): Int = store(Jsoup.parse(html))

    /** $(selector) dentro de um handle (Document, Element ou Elements). */
    fun select(handle: Int, selector: String): Int {
        val target = handles[handle] ?: return store(Elements())
        val found: Elements = when (target) {
            is Document -> target.select(selector)
            is Element -> target.select(selector)
            is Elements -> target.select(selector)
            else -> Elements()
        }
        return store(found)
    }

    /** Texto: concatena (cheerio) / jsoup junta com espaço no caso de coleção. */
    fun text(handle: Int): String = when (val t = handles[handle]) {
        is Document -> t.body()?.text().orEmpty()
        is Element -> t.text()
        is Elements -> t.text()
        else -> ""
    }

    /** attr(name) -> JSON {"present":bool,"value":string}. */
    fun attr(handle: Int, name: String): String {
        val el: Element? = when (val t = handles[handle]) {
            is Element -> t
            is Elements -> t.firstOrNull()
            is Document -> t.body()
            else -> null
        }
        val present = el?.hasAttr(name) == true
        val value = if (present) el!!.attr(name) else ""
        return """{"present":$present,"value":${org.json.JSONObject.quote(value)}}"""
    }

    /** html() interno do primeiro elemento (ou do body, no caso do Document). */
    fun html(handle: Int): String = when (val t = handles[handle]) {
        is Document -> t.body()?.html().orEmpty()
        is Element -> t.html()
        is Elements -> t.firstOrNull()?.html().orEmpty()
        else -> ""
    }

    /** Quantidade de elementos (1 para Document/Element isolado). */
    fun count(handle: Int): Int = when (val t = handles[handle]) {
        is Elements -> t.size
        is Document, is Element -> 1
        else -> 0
    }

    /** Elemento i da coleção (índice negativo = a partir do fim). 0 = fora do alcance. */
    fun element(handle: Int, index: Int): Int {
        val els: Elements = when (val t = handles[handle]) {
            is Elements -> t
            is Element -> Elements(t)
            is Document -> t.body()?.let { Elements(it) } ?: Elements()
            else -> Elements()
        }
        if (els.isEmpty()) return 0
        val i = if (index < 0) els.size + index else index
        if (i < 0 || i >= els.size) return 0
        return store(els[i])
    }

    /** Filhos (de todos os elementos, na ordem). */
    fun children(handle: Int): Int {
        val out = Elements()
        when (val t = handles[handle]) {
            is Document -> t.body()?.children()?.let { out.addAll(it) }
            is Element -> out.addAll(t.children())
            is Elements -> t.forEach { out.addAll(it.children()) }
        }
        return store(out)
    }

    /** Pai do primeiro elemento. 0 = sem pai. */
    fun parent(handle: Int): Int {
        val el: Element? = when (val t = handles[handle]) {
            is Element -> t
            is Elements -> t.firstOrNull()
            is Document -> t.body()
            else -> null
        }
        val p = el?.parent() ?: return 0
        return store(p)
    }

    /** filter(seletor): mantém só os elementos que CASAM com o seletor. */
    fun filter(handle: Int, selector: String): Int {
        val els: Elements = when (val t = handles[handle]) {
            is Elements -> t
            is Element -> Elements(t)
            is Document -> Elements(t.body() ?: return store(Elements()))
            else -> Elements()
        }
        val out = Elements()
        for (el in els) {
            if (runCatching { el.`is`(selector) }.getOrDefault(false)) out.add(el)
        }
        return store(out)
    }

    /** Junta elementos de vários handles numa coleção só (filter com função no JS). */
    fun combine(handlesJson: String): Int {
        val arr = runCatching { org.json.JSONArray(handlesJson) }.getOrNull() ?: return store(Elements())
        val out = Elements()
        for (i in 0 until arr.length()) {
            val h = arr.optInt(i, 0)
            when (val t = handles[h]) {
                is Element -> out.add(t)
                is Elements -> out.addAll(t)
                else -> Unit
            }
        }
        return store(out)
    }

    /** $(fragmento HTML): parseia o fragmento e devolve os elementos raiz. */
    fun parseFragment(html: String): Int {
        val children = runCatching { Jsoup.parseBodyFragment(html).body()?.children() }.getOrNull()
        return store(children ?: Elements())
    }
}
