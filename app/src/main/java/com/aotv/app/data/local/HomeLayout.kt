package com.aotv.app.data.local

import com.aotv.app.data.remote.AddonCatalog

/**
 * Fileiras próprias do app. [catalog] marca as que vêm do catálogo AOTV (TMDB) e somem quando
 * o catálogo AOTV está desligado. As outras são do progresso de quem assiste.
 */
enum class SystemRow(val key: String, val label: String, val catalog: Boolean) {
    CONTINUE("sys:continue", "Continuar assistindo", false),
    NEW_EPISODES("sys:new_episodes", "Novos episódios", false),
    RECOMMENDED("sys:recommended", "Porque você assistiu", true),
    TRAILERS("sys:trailers", "Últimos trailers", true),
    TRENDING_MOVIES("sys:trending_movies", "Tops do Momento Filmes", true),
    TRENDING_TV("sys:trending_tv", "Tops do Momento Séries de TV", true),
    NOW_PLAYING("sys:now_playing", "Filmes nos cinemas", true),
    RELEASES("sys:releases", "Lançamentos do ano", true),
    GENRES("sys:genres", "Gêneros", true);

    companion object {
        fun of(key: String): SystemRow? = entries.firstOrNull { it.key == key }
        /** Antes das fileiras de addon, igual à Início de antes da personalização. */
        val HEAD = listOf(CONTINUE, NEW_EPISODES)
        val TAIL = listOf(RECOMMENDED, TRAILERS, TRENDING_MOVIES, TRENDING_TV, NOW_PLAYING, RELEASES, GENRES)
    }
}

/**
 * Uma fileira da Início. [ref] só existe nas fileiras de addon. [title] vazio usa o nome original.
 * [collection] é o título da coleção (chave "col:<id>"), preenchido a cada normalize e não salvo.
 */
data class HomeRowPref(
    val key: String,
    val enabled: Boolean = true,
    val title: String = "",
    val ref: HomeCatalogRef? = null,
    val collection: String = "",
) {
    val system: SystemRow? get() = SystemRow.of(key)
    val collectionId: String? get() = if (key.startsWith(HomeLayout.COL_PREFIX)) key.removePrefix(HomeLayout.COL_PREFIX) else null
    val defaultTitle: String get() = system?.label ?: ref?.name ?: collection
    val displayTitle: String get() = title.ifBlank { defaultTitle }
}

/** O que o app guarda do manifest de um addon para montar a Início sem ir na rede. [id] é o id do manifest. */
data class CachedAddon(val name: String, val catalogs: List<AddonCatalog>, val id: String = "")

object HomeLayout {

    /** Chave das fileiras de coleção ("col:<id da coleção>"). */
    const val COL_PREFIX = "col:"

    /** Layout de quem nunca personalizou. [legacy] são as fileiras de addon marcadas no formato antigo. */
    fun default(legacy: List<HomeCatalogRef>): List<HomeRowPref> =
        SystemRow.HEAD.map { HomeRowPref(it.key) } +
            legacy.map { HomeRowPref(it.key, true, "", it) } +
            SystemRow.TAIL.map { HomeRowPref(it.key) }

    /**
     * Junta o que o perfil salvou com o que existe agora.
     * Fileira de addon removido sai. Fileira de addon desligado fica guardada (só não aparece).
     * Catálogo que o perfil ainda não viu entra no fim, ligado só quando o addon foi instalado
     * depois da Início personalizável ([autoOnBases]). Assim quem já usava o app não ganha uma
     * enxurrada de fileiras, e addon novo aparece sozinho como no Stremio.
     */
    fun normalize(
        saved: List<HomeRowPref>,
        installedBases: Set<String>,
        available: List<HomeCatalogRef>,
        autoOnBases: Set<String>,
        collections: List<com.aotv.app.data.collections.MediaCollection> = emptyList(),
    ): List<HomeRowPref> {
        val avail = available.associateBy { it.key }
        val cols = collections.associateBy { COL_PREFIX + it.id }
        val out = ArrayList<HomeRowPref>()
        val seen = HashSet<String>()
        for (r in saved) {
            if (r.key in seen) continue
            if (r.system != null) { out += r; seen += r.key; continue }
            // Coleção apagada sai. O título acompanha a coleção, o nome dado pela pessoa fica.
            if (r.collectionId != null) { cols[r.key]?.let { out += r.copy(collection = it.title); seen += r.key }; continue }
            val ref = r.ref ?: continue
            if (ref.base !in installedBases) continue
            // Nome do catálogo e do addon acompanham o manifest. O nome dado pela pessoa fica.
            out += avail[r.key]?.let { r.copy(ref = it) } ?: r
            seen += r.key
        }
        SystemRow.entries.filter { it.key !in seen }.forEach { out += HomeRowPref(it.key); seen += it.key }
        available.filter { seen.add(it.key) }.forEach { out += HomeRowPref(it.key, it.base in autoOnBases, "", it) }
        // Coleção nova entra ligada, no fim (a fixada sobe logo abaixo).
        collections.filter { seen.add(COL_PREFIX + it.id) }.forEach { out += HomeRowPref(COL_PREFIX + it.id, true, "", null, it.title) }
        val pinned = collections.filter { it.pinToTop }.mapTo(HashSet()) { COL_PREFIX + it.id }
        val ordered = genresLast(out)
        // Fixada no topo vem antes de tudo, como no Nuvio.
        return if (pinned.isEmpty()) ordered else ordered.filter { it.key in pinned } + ordered.filter { it.key !in pinned }
    }

    /** Gêneros carrega sem fim, então o que viesse depois dele nunca apareceria. */
    private fun genresLast(list: List<HomeRowPref>): List<HomeRowPref> {
        val g = list.firstOrNull { it.key == SystemRow.GENRES.key } ?: return list
        return list.filter { it.key != SystemRow.GENRES.key } + g
    }

    /**
     * Sobe ([delta] negativo) ou desce a fileira, trocando com a vizinha mais próxima que
     * [visible] mostra na tela. Gêneros não sai do fim.
     */
    fun move(list: List<HomeRowPref>, key: String, delta: Int, visible: (HomeRowPref) -> Boolean = { true }): List<HomeRowPref> {
        val i = list.indexOfFirst { it.key == key }
        if (i < 0 || delta == 0 || list[i].key == SystemRow.GENRES.key) return list
        var j = i + if (delta > 0) 1 else -1
        while (j in list.indices && !visible(list[j])) j += if (delta > 0) 1 else -1
        if (j !in list.indices || list[j].key == SystemRow.GENRES.key) return list
        val m = list.toMutableList()
        val t = m[i]; m[i] = m[j]; m[j] = t
        return m
    }

    /** Pode subir ou descer na lista que a tela mostra. */
    fun canMove(list: List<HomeRowPref>, key: String, delta: Int, visible: (HomeRowPref) -> Boolean = { true }): Boolean =
        move(list, key, delta, visible) !== list

    /**
     * Volta à ordem padrão. Fileiras fixas ligadas, nomes originais. As de addon mantêm o
     * liga e desliga de cada uma e seguem a prioridade dos addons.
     */
    fun reset(current: List<HomeRowPref>): List<HomeRowPref> {
        val addonRows = current.filter { it.system == null }.map { it.copy(title = "") }
        return SystemRow.HEAD.map { HomeRowPref(it.key) } + addonRows + SystemRow.TAIL.map { HomeRowPref(it.key) }
    }

    /** "10 fileiras novas na Início." ou "10 de 40 fileiras na Início. Escolha outras em Fileiras." */
    fun rowsText(on: Int, all: Int): String =
        if (on >= all) (if (all == 1) "1 fileira nova" else "$all fileiras novas") + " na Início."
        else "$on de $all fileiras na Início. Escolha outras em Fileiras."

    /** Mensagem de addon instalado, a mesma na loja, no link e na importação. */
    fun installText(name: String, on: Int, all: Int): String =
        "$name instalado." + if (all > 0) " " + rowsText(on, all) else ""

    fun encode(list: List<HomeRowPref>): String {
        val arr = org.json.JSONArray()
        list.forEach { r ->
            val o = org.json.JSONObject().put("k", r.key).put("e", r.enabled)
            if (r.title.isNotBlank()) o.put("t", r.title)
            r.ref?.let { o.put("b", it.base).put("ty", it.type).put("i", it.id).put("n", it.name).put("a", it.addon) }
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<HomeRowPref>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = o.optString("k")
                val ref = if (o.has("b")) HomeCatalogRef(o.optString("b"), o.optString("ty"), o.optString("i"), o.optString("n"), o.optString("a"))
                    .takeIf { it.base.startsWith("http") && it.id.isNotBlank() } else null
                when {
                    SystemRow.of(key) != null -> HomeRowPref(key, o.optBoolean("e", true), o.optString("t"))
                    key.startsWith(COL_PREFIX) && key.length > COL_PREFIX.length -> HomeRowPref(key, o.optBoolean("e", true), o.optString("t"))
                    ref != null && ref.key == key -> HomeRowPref(key, o.optBoolean("e", true), o.optString("t"), ref)
                    else -> null
                }
            }
        }.getOrNull()
    }

    fun encodeAddons(map: Map<String, CachedAddon>): String {
        val root = org.json.JSONObject()
        map.forEach { (base, a) ->
            val cats = org.json.JSONArray()
            a.catalogs.forEach { c -> cats.put(org.json.JSONObject().put("t", c.type).put("i", c.id).put("n", c.name)) }
            root.put(base, org.json.JSONObject().put("n", a.name).put("c", cats).put("id", a.id))
        }
        return root.toString()
    }

    fun decodeAddons(raw: String?): Map<String, CachedAddon> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(raw)
            root.keys().asSequence().associateWith { base ->
                val o = root.getJSONObject(base)
                val cats = o.optJSONArray("c") ?: org.json.JSONArray()
                CachedAddon(
                    o.optString("n"),
                    (0 until cats.length()).mapNotNull { i ->
                        val c = cats.optJSONObject(i) ?: return@mapNotNull null
                        AddonCatalog(c.optString("t"), c.optString("i"), c.optString("n")).takeIf { it.id.isNotBlank() }
                    },
                    o.optString("id"),
                )
            }
        }.getOrDefault(emptyMap())
    }
}
