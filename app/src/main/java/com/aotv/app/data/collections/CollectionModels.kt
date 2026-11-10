package com.aotv.app.data.collections

import org.json.JSONArray
import org.json.JSONObject

/*
 * Coleções no formato do Nuvio, para o JSON de lá entrar e sair igual (o que sai daqui também
 * entra no Nuvio). Coleção é uma fileira da Início cheia de pastas. Cada pasta junta fontes,
 * que podem ser catálogo de addon, fonte do TMDB ou lista do Trakt. Lista do Trakt fica guardada
 * e volta no exportar, mas não carrega nesta build, que não tem client id do Trakt.
 */

/** Tipos de fonte do TMDB, com os nomes que o Nuvio grava. */
enum class TmdbSourceType {
    LIST, COLLECTION, COMPANY, NETWORK, DISCOVER, PERSON, DIRECTOR;

    companion object {
        fun of(v: String?): TmdbSourceType? = entries.firstOrNull { it.name.equals(v?.trim(), true) }
    }
}

/** Filtros da descoberta do TMDB. Cada campo vira um parâmetro do discover. */
data class TmdbFilters(
    val withGenres: String? = null,
    val withoutGenres: String? = null,
    val releaseDateGte: String? = null,
    val releaseDateLte: String? = null,
    val voteAverageGte: Double? = null,
    val voteAverageLte: Double? = null,
    val voteCountGte: Int? = null,
    val withOriginalLanguage: String? = null,
    val withOriginCountry: String? = null,
    val withKeywords: String? = null,
    val withoutKeywords: String? = null,
    val withCompanies: String? = null,
    val withoutCompanies: String? = null,
    val withNetworks: String? = null,
    val year: Int? = null,
    val watchRegion: String? = null,
    val withWatchProviders: String? = null,
    val withoutWatchProviders: String? = null,
) {
    val isEmpty: Boolean get() = this == TmdbFilters()
}

data class CollectionSource(
    val provider: String = "addon",
    val addonId: String? = null,
    val type: String? = null,
    val catalogId: String? = null,
    val genre: String? = null,
    val tmdbSourceType: String? = null,
    val title: String? = null,
    val tmdbId: Int? = null,
    val traktListId: Long? = null,
    val mediaType: String? = null,
    val sortBy: String? = null,
    val sortHow: String? = null,
    val filters: TmdbFilters? = null,
) {
    val isTmdb: Boolean get() = provider.equals("tmdb", true)
    val isTrakt: Boolean get() = provider.equals("trakt", true)
    val isAddon: Boolean get() = !isTmdb && !isTrakt
    val tmdbType: TmdbSourceType? get() = TmdbSourceType.of(tmdbSourceType)

    /** Série quando a fonte pede ("tv" ou "series"). Canal de TV é sempre série. */
    val tv: Boolean get() = tmdbType == TmdbSourceType.NETWORK || mediaType?.trim()?.lowercase() in setOf("tv", "series")

    /** Chave estável da aba na tela da pasta (a mesma ideia do catalogRouteKey do Nuvio). */
    val key: String
        get() = when {
            isTmdb -> "tmdb_${tmdbSourceType}_${tmdbId}_${mediaType}_${sortBy}_${filters.hashCode()}"
            isTrakt -> "trakt_${traktListId}_${mediaType}_${sortBy}_${sortHow}"
            else -> "addon_${addonId}_${type}_${catalogId}_${genre.orEmpty()}"
        }
}

data class CollectionFolder(
    val id: String,
    val title: String,
    val coverImageUrl: String? = null,
    val focusGifUrl: String? = null,
    val focusGifEnabled: Boolean = true,
    val coverEmoji: String? = null,
    val tileShape: String = "poster",
    val hideTitle: Boolean = false,
    val sources: List<CollectionSource> = emptyList(),
    val heroBackdropUrl: String? = null,
    val heroVideoUrl: String? = null,
    val titleLogoUrl: String? = null,
) {
    /** "poster", "landscape" ou "square". O Nuvio também aceita "wide" como paisagem. */
    val shape: String
        get() = when (tileShape.lowercase()) {
            "landscape", "wide" -> "landscape"
            "square" -> "square"
            else -> "poster"
        }
}

/** Nome evita conflito com kotlin.collections.Collection. */
data class MediaCollection(
    val id: String,
    val title: String,
    val backdropImageUrl: String? = null,
    val pinToTop: Boolean = false,
    /** TABBED_GRID (abas com grade), ROWS (uma fileira por fonte) ou FOLLOW_LAYOUT. */
    val viewMode: String = VIEW_TABS,
    val showAllTab: Boolean = true,
    val folders: List<CollectionFolder> = emptyList(),
) {
    val tabbed: Boolean get() = !viewMode.equals(VIEW_ROWS, true) && !viewMode.equals(VIEW_FOLLOW, true)

    companion object {
        const val VIEW_TABS = "TABBED_GRID"
        const val VIEW_ROWS = "ROWS"
        const val VIEW_FOLLOW = "FOLLOW_LAYOUT"
    }
}

object CollectionsJson {

    /**
     * Lê a lista de coleções do Nuvio. Aceita a lista pura ou um objeto com "collections".
     * Recusa com mensagem em português o que o Nuvio também recusaria.
     */
    fun parse(text: String): Result<List<MediaCollection>> = runCatching {
        val raw = text.trim().removePrefix("﻿")
        require(raw.isNotEmpty()) { "O JSON está vazio" }
        val arr = when {
            raw.startsWith("[") -> JSONArray(raw)
            raw.startsWith("{") -> JSONObject(raw).optJSONArray("collections") ?: throw IllegalArgumentException("O JSON não traz uma lista de coleções")
            else -> throw IllegalArgumentException("Isso não é um JSON de coleções")
        }
        val list = (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: throw IllegalArgumentException("A coleção ${i + 1} não é um objeto")
            collectionOf(o)
        }
        validate(list)?.let { throw IllegalArgumentException(it) }
        // Mesmo id duas vezes no arquivo não passa na validação. Aqui é só garantia.
        list.distinctBy { it.id }
    }

    /** Mensagem do primeiro problema, ou null quando a lista serve. Mesmas regras do Nuvio. */
    fun validate(list: List<MediaCollection>): String? {
        val ids = HashSet<String>()
        list.forEachIndexed { ci, c ->
            if (c.id.isBlank()) return "A coleção ${ci + 1} está sem id"
            if (!ids.add(c.id)) return "Há duas coleções com o id ${c.id}"
            if (c.title.isBlank()) return "A coleção ${c.id} está sem título"
            val folderIds = HashSet<String>()
            c.folders.forEachIndexed { fi, f ->
                if (f.id.isBlank()) return "A pasta ${fi + 1} de ${c.title} está sem id"
                if (!folderIds.add(f.id)) return "Há duas pastas com o id ${f.id} em ${c.title}"
                if (f.title.isBlank()) return "A pasta ${f.id} de ${c.title} está sem título"
                f.sources.forEachIndexed { si, s ->
                    if (s.isTrakt && (s.traktListId ?: 0L) <= 0L) return "A fonte ${si + 1} de ${f.title} tem lista do Trakt sem id"
                    val badAddon = s.isAddon && (s.addonId.isNullOrBlank() || s.type.isNullOrBlank() || s.catalogId.isNullOrBlank())
                    val badTmdb = s.isTmdb && s.tmdbSourceType.isNullOrBlank()
                    if (badAddon || badTmdb) return "A fonte ${si + 1} de ${f.title} está incompleta"
                }
            }
        }
        return null
    }

    fun encode(list: List<MediaCollection>, pretty: Boolean = false): String {
        val arr = JSONArray()
        list.forEach { arr.put(collectionJson(it)) }
        return if (pretty) arr.toString(2) else arr.toString()
    }

    // ── Leitura ─────────────────────────────────────────────────────────────

    private fun collectionOf(o: JSONObject) = MediaCollection(
        id = str(o, "id").orEmpty(),
        title = str(o, "title").orEmpty(),
        backdropImageUrl = str(o, "backdropImageUrl"),
        pinToTop = o.optBoolean("pinToTop", false),
        viewMode = str(o, "viewMode") ?: MediaCollection.VIEW_TABS,
        showAllTab = o.optBoolean("showAllTab", true),
        folders = objects(o.optJSONArray("folders")).map(::folderOf),
    )

    private fun folderOf(o: JSONObject): CollectionFolder {
        // Arquivo antigo do Nuvio guardava só catalogSources (addon). Vira fonte de addon.
        val sources = objects(o.optJSONArray("sources")).map(::sourceOf).ifEmpty {
            objects(o.optJSONArray("catalogSources")).map { c ->
                CollectionSource(provider = "addon", addonId = str(c, "addonId"), type = str(c, "type"), catalogId = str(c, "catalogId"), genre = genreOf(str(c, "genre")))
            }
        }
        return CollectionFolder(
            id = str(o, "id").orEmpty(),
            title = str(o, "title").orEmpty(),
            coverImageUrl = str(o, "coverImageUrl"),
            focusGifUrl = str(o, "focusGifUrl"),
            focusGifEnabled = o.optBoolean("focusGifEnabled", true),
            coverEmoji = str(o, "coverEmoji"),
            tileShape = str(o, "tileShape") ?: "poster",
            hideTitle = o.optBoolean("hideTitle", false),
            sources = sources,
            heroBackdropUrl = str(o, "heroBackdropUrl"),
            heroVideoUrl = str(o, "heroVideoUrl"),
            titleLogoUrl = str(o, "titleLogoUrl"),
        )
    }

    private fun sourceOf(o: JSONObject) = CollectionSource(
        provider = str(o, "provider") ?: "addon",
        addonId = str(o, "addonId"),
        type = str(o, "type"),
        catalogId = str(o, "catalogId"),
        genre = genreOf(str(o, "genre")),
        tmdbSourceType = str(o, "tmdbSourceType"),
        title = str(o, "title"),
        tmdbId = int(o, "tmdbId"),
        traktListId = long(o, "traktListId"),
        mediaType = str(o, "mediaType"),
        sortBy = str(o, "sortBy"),
        sortHow = str(o, "sortHow"),
        filters = o.optJSONObject("filters")?.let(::filtersOf),
    )

    private fun filtersOf(o: JSONObject) = TmdbFilters(
        withGenres = str(o, "withGenres"),
        withoutGenres = str(o, "withoutGenres"),
        releaseDateGte = str(o, "releaseDateGte"),
        releaseDateLte = str(o, "releaseDateLte"),
        voteAverageGte = double(o, "voteAverageGte"),
        voteAverageLte = double(o, "voteAverageLte"),
        voteCountGte = int(o, "voteCountGte"),
        withOriginalLanguage = str(o, "withOriginalLanguage"),
        withOriginCountry = str(o, "withOriginCountry"),
        withKeywords = str(o, "withKeywords"),
        withoutKeywords = str(o, "withoutKeywords"),
        withCompanies = str(o, "withCompanies"),
        withoutCompanies = str(o, "withoutCompanies"),
        withNetworks = str(o, "withNetworks"),
        year = int(o, "year"),
        watchRegion = str(o, "watchRegion"),
        withWatchProviders = str(o, "withWatchProviders"),
        withoutWatchProviders = str(o, "withoutWatchProviders"),
    ).takeUnless { it.isEmpty }

    /** "none" é o "sem gênero" do Nuvio. */
    private fun genreOf(g: String?) = g?.takeIf { !it.equals("none", true) }

    private fun objects(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun str(o: JSONObject, k: String): String? =
        if (!o.has(k) || o.isNull(k)) null else o.opt(k)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun int(o: JSONObject, k: String): Int? = long(o, k)?.toInt()

    private fun long(o: JSONObject, k: String): Long? = when (val v = if (o.isNull(k)) null else o.opt(k)) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    private fun double(o: JSONObject, k: String): Double? = when (val v = if (o.isNull(k)) null else o.opt(k)) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    // ── Escrita ─────────────────────────────────────────────────────────────

    private fun collectionJson(c: MediaCollection) = JSONObject()
        .put("id", c.id).put("title", c.title)
        .putOpt("backdropImageUrl", c.backdropImageUrl)
        .put("pinToTop", c.pinToTop).put("viewMode", c.viewMode).put("showAllTab", c.showAllTab)
        .put("folders", JSONArray().also { a -> c.folders.forEach { a.put(folderJson(it)) } })

    private fun folderJson(f: CollectionFolder) = JSONObject()
        .put("id", f.id).put("title", f.title)
        .putOpt("coverImageUrl", f.coverImageUrl).putOpt("focusGifUrl", f.focusGifUrl)
        .put("focusGifEnabled", f.focusGifEnabled).putOpt("coverEmoji", f.coverEmoji)
        .put("tileShape", f.tileShape).put("hideTitle", f.hideTitle)
        .put("sources", JSONArray().also { a -> f.sources.forEach { a.put(sourceJson(it)) } })
        .putOpt("heroBackdropUrl", f.heroBackdropUrl).putOpt("heroVideoUrl", f.heroVideoUrl)
        .putOpt("titleLogoUrl", f.titleLogoUrl)

    private fun sourceJson(s: CollectionSource) = JSONObject()
        .put("provider", s.provider)
        .putOpt("addonId", s.addonId).putOpt("type", s.type).putOpt("catalogId", s.catalogId).putOpt("genre", s.genre)
        .putOpt("tmdbSourceType", s.tmdbSourceType).putOpt("title", s.title).putOpt("tmdbId", s.tmdbId)
        .putOpt("traktListId", s.traktListId).putOpt("mediaType", s.mediaType)
        .putOpt("sortBy", s.sortBy).putOpt("sortHow", s.sortHow)
        .putOpt("filters", s.filters?.takeUnless { it.isEmpty }?.let(::filtersJson))

    private fun filtersJson(f: TmdbFilters) = JSONObject()
        .putOpt("withGenres", f.withGenres).putOpt("withoutGenres", f.withoutGenres)
        .putOpt("releaseDateGte", f.releaseDateGte).putOpt("releaseDateLte", f.releaseDateLte)
        .putOpt("voteAverageGte", f.voteAverageGte).putOpt("voteAverageLte", f.voteAverageLte)
        .putOpt("voteCountGte", f.voteCountGte)
        .putOpt("withOriginalLanguage", f.withOriginalLanguage).putOpt("withOriginCountry", f.withOriginCountry)
        .putOpt("withKeywords", f.withKeywords).putOpt("withoutKeywords", f.withoutKeywords)
        .putOpt("withCompanies", f.withCompanies).putOpt("withoutCompanies", f.withoutCompanies)
        .putOpt("withNetworks", f.withNetworks).putOpt("year", f.year).putOpt("watchRegion", f.watchRegion)
        .putOpt("withWatchProviders", f.withWatchProviders).putOpt("withoutWatchProviders", f.withoutWatchProviders)
}

/** Regras do TMDB que o Nuvio aplica nas fontes, sem rede (dá para testar). */
object TmdbCollectionRules {
    const val SORT_ORIGINAL = "original"
    const val SORT_POPULAR = "popularity.desc"
    const val SORT_RATING = "vote_average.desc"
    const val SORT_VOTES = "vote_count.desc"
    const val SORT_RELEASE = "primary_release_date.desc"
    const val SORT_FIRST_AIR = "first_air_date.desc"

    /** Número solto ou link do TMDB (".../list/123", ".../collection/10-...", "?id=5"). */
    fun parseTmdbId(input: String): Int? {
        val t = input.trim()
        t.toIntOrNull()?.let { return it }
        return Regex("""(?:list|collection|company|network|person)/(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""[?&]id=(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Ordem pedida no discover. Filme e série chamam a data de lançamento de jeitos diferentes. */
    fun discoverSort(sortBy: String?, tv: Boolean): String = when (sortBy) {
        null, "", SORT_ORIGINAL -> SORT_POPULAR
        SORT_FIRST_AIR -> if (tv) SORT_FIRST_AIR else SORT_RELEASE
        SORT_RELEASE -> if (tv) SORT_FIRST_AIR else SORT_RELEASE
        else -> sortBy
    }

    /** Parâmetros do discover para fonte de estúdio, canal ou descoberta (igual ao Nuvio). */
    fun discoverQuery(source: CollectionSource, page: Int): Map<String, String> {
        val type = source.tmdbType
        val tv = source.tv
        val f = source.filters ?: TmdbFilters()
        val out = LinkedHashMap<String, String>()
        fun put(k: String, v: String?) { if (!v.isNullOrBlank()) out[k] = v }
        put("page", page.toString())
        put("sort_by", discoverSort(source.sortBy, tv))
        put("with_companies", source.tmdbId?.toString()?.takeIf { type == TmdbSourceType.COMPANY } ?: f.withCompanies)
        put("without_companies", f.withoutCompanies)
        put("with_networks", source.tmdbId?.toString()?.takeIf { type == TmdbSourceType.NETWORK } ?: f.withNetworks)
        put("with_genres", f.withGenres)
        put("without_genres", f.withoutGenres)
        put("vote_count.gte", f.voteCountGte?.toString())
        put("vote_average.gte", f.voteAverageGte?.toString())
        put("vote_average.lte", f.voteAverageLte?.toString())
        put("with_original_language", f.withOriginalLanguage)
        put("with_origin_country", f.withOriginCountry)
        put("with_keywords", f.withKeywords)
        put("without_keywords", f.withoutKeywords)
        val withProv = f.withWatchProviders?.takeIf { it.isNotBlank() }
        val withoutProv = f.withoutWatchProviders?.takeIf { it.isNotBlank() }
        if (withProv != null || withoutProv != null) {
            put("with_watch_providers", withProv)
            put("without_watch_providers", withoutProv)
            put("watch_region", f.watchRegion?.takeIf { it.isNotBlank() } ?: "US")
            if (withProv != null) put("with_watch_monetization_types", "flatrate|free|ads|rent|buy")
        }
        if (tv) put("first_air_date_year", f.year?.toString()) else put("year", f.year?.toString())
        put(if (tv) "first_air_date.gte" else "primary_release_date.gte", f.releaseDateGte)
        put(if (tv) "first_air_date.lte" else "primary_release_date.lte", f.releaseDateLte)
        return out
    }
}
