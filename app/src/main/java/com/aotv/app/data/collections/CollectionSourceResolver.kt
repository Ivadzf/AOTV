package com.aotv.app.data.collections

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AddonCatalogOption
import com.aotv.app.data.remote.AddonLinks
import com.aotv.app.data.remote.AddonMetaResolver
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carrega o conteúdo das fontes das pastas e serve o editor (catálogos dos addons, atalhos,
 * buscas e filtros do TMDB). As regras de cada fonte seguem o Nuvio, para a mesma coleção
 * mostrar a mesma coisa nos dois apps.
 */
@Singleton
class CollectionSourceResolver @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
    private val stremio: StremioAddonService,
    private val addonMetas: AddonMetaResolver,
) {
    /** Uma página. [next] é o cursor da próxima (página do TMDB ou skip do addon), null quando acabou. */
    data class Page(val items: List<MovieItem>, val next: Int?, val error: String? = null)

    /** Atalho do editor (estúdio ou canal conhecido), os mesmos do Nuvio. */
    data class Preset(val label: String, val source: CollectionSource)

    /** Resultado de busca do editor. [image] é logo, pôster ou foto. */
    data class SearchHit(val id: Int, val name: String, val image: String?, val sub: String = "")

    suspend fun load(source: CollectionSource, cursor: Int? = null): Page = withContext(Dispatchers.IO) {
        try {
            when {
                source.isTrakt -> Page(emptyList(), null, "Lista do Trakt precisa da conta do Trakt, que esta versão ainda não tem")
                source.isAddon -> loadAddon(source, cursor ?: 0)
                else -> loadTmdb(source, cursor ?: 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Page(emptyList(), null, "Não deu para carregar agora")
        }
    }

    private suspend fun loadAddon(s: CollectionSource, skip: Int): Page {
        val base = addonBase(s.addonId.orEmpty())
            ?: return Page(emptyList(), null, "Instale e ligue o addon ${s.addonId} para ver esta fonte")
        val metas = stremio.catalogMetas(base, s.type.orEmpty(), s.catalogId.orEmpty(), skip, s.genre)
            ?: return Page(emptyList(), null, "O addon não respondeu")
        val page = metas.take(ADDON_PAGE)
        return Page(addonMetas.resolve(page, s.type == "series"), if (metas.size >= ADDON_PAGE) skip + page.size else null)
    }

    /** Base do addon instalado e ligado com esse id de manifest (é o que o JSON do Nuvio guarda). */
    private suspend fun addonBase(addonId: String): String? {
        if (addonId.isBlank()) return null
        val installed = prefs.addons.value.filter { it.enabled }
        installed.firstOrNull { it.id == addonId }?.let { return AddonLinks.baseOf(it.url) }
        val meta = prefs.addonMetaFlow.value
        installed.firstOrNull { meta[AddonLinks.baseOf(it.url)]?.id == addonId }?.let { return AddonLinks.baseOf(it.url) }
        // Cache antigo, sem o id. Ler o manifest grava o id para a próxima vez.
        for (e in installed) {
            val base = AddonLinks.baseOf(e.url)
            if (prefs.addonMetaFlow.value[base]?.id.isNullOrBlank() && stremio.getManifest(e.url)?.id == addonId) return base
        }
        return null
    }

    private suspend fun loadTmdb(s: CollectionSource, page: Int): Page {
        val key = prefs.tmdbApiKey
        val type = s.tmdbType ?: return Page(emptyList(), null, "Fonte do TMDB desconhecida")
        val id = s.tmdbId
        if (type != TmdbSourceType.DISCOVER && id == null) return Page(emptyList(), null, "Fonte do TMDB sem id")
        return when (type) {
            TmdbSourceType.LIST -> {
                val r = tmdb.getList(id!!, page, apiKey = key)
                Page(sorted(r.items.filterNot { it.adult }.map { it.toMovieItem() }.withPoster(), s.sortBy), if (r.page < r.totalPages && r.items.isNotEmpty()) page + 1 else null)
            }
            TmdbSourceType.COLLECTION -> {
                val r = tmdb.getCollection(id!!, apiKey = key)
                Page(sorted(r.parts.filterNot { it.adult }.map { it.toMovieItem().copy(type = MediaType.MOVIE) }.withPoster(), s.sortBy), null)
            }
            TmdbSourceType.PERSON, TmdbSourceType.DIRECTOR -> {
                val r = tmdb.getPersonCredits(id!!, apiKey = key)
                val credits = if (type == TmdbSourceType.DIRECTOR) r.crew.filter { it.job.equals("Director", true) } else r.cast
                // Com tipo escolhido, fica só filme ou só série (o Nuvio troca o tipo, aqui filtra).
                val want = s.mediaType?.trim()?.lowercase()
                val items = credits.filterNot { it.adult }
                    .filter { c -> want.isNullOrBlank() || ((want == "tv" || want == "series") == (c.mediaType == "tv")) }
                    .map { it.toMovieItem() }
                    .distinctBy { "${it.type}:${it.tmdbId}" }
                Page(sorted(items.withPoster(), s.sortBy), null)
            }
            TmdbSourceType.COMPANY, TmdbSourceType.NETWORK, TmdbSourceType.DISCOVER -> {
                val r = tmdb.discoverRaw(if (s.tv) "tv" else "movie", TmdbCollectionRules.discoverQuery(s, page), apiKey = key)
                val items = r.results.filterNot { it.adult }
                    .map { it.toMovieItem().copy(type = if (s.tv) MediaType.SERIES else MediaType.MOVIE) }
                Page(items.withPoster(), if (r.page < r.totalPages && r.results.isNotEmpty()) page + 1 else null)
            }
        }
    }

    private fun List<MovieItem>.withPoster() = filter { it.posterUrl.isNotBlank() }

    /** Ordem das fontes que vêm inteiras (lista, franquia e pessoa), como o sortedFor do Nuvio. */
    private fun sorted(items: List<MovieItem>, sortBy: String?): List<MovieItem> = when (sortBy) {
        TmdbCollectionRules.SORT_RATING -> items.sortedWith(compareByDescending<MovieItem> { it.rating }.thenByDescending { it.releaseDate })
        TmdbCollectionRules.SORT_RELEASE, TmdbCollectionRules.SORT_FIRST_AIR -> items.sortedByDescending { it.releaseDate.ifBlank { it.year } }
        else -> items
    }

    // ── Editor ──────────────────────────────────────────────────────────────

    /** Catálogos de filme e série de todos os addons ligados. */
    suspend fun catalogOptions(): List<AddonCatalogOption> = coroutineScope {
        prefs.addons.value.filter { it.enabled }.map { e -> async { stremio.collectionCatalogs(e.url) } }.awaitAll().flatten()
    }

    fun presets(): List<Preset> = listOf(
        company("Marvel Studios", 420), company("Walt Disney Pictures", 2), company("Pixar", 3),
        company("Lucasfilm", 1), company("Warner Bros.", 174),
        network("Netflix", 213), network("HBO", 49), network("Disney+", 2739),
        network("Prime Video", 1024), network("Hulu", 453), network("Apple TV+", 2552),
    )

    private fun company(label: String, id: Int) = Preset(label, CollectionSource(
        provider = "tmdb", tmdbSourceType = TmdbSourceType.COMPANY.name, title = label, tmdbId = id, mediaType = "MOVIE", sortBy = TmdbCollectionRules.SORT_POPULAR,
    ))

    private fun network(label: String, id: Int) = Preset(label, CollectionSource(
        provider = "tmdb", tmdbSourceType = TmdbSourceType.NETWORK.name, title = label, tmdbId = id, mediaType = "TV", sortBy = TmdbCollectionRules.SORT_POPULAR,
    ))

    suspend fun searchCompanies(q: String): List<SearchHit> = safe {
        tmdb.searchCompanies(q, apiKey = prefs.tmdbApiKey).results.map { SearchHit(it.id, it.name, img(it.logoPath), it.originCountry.orEmpty()) }
    }

    suspend fun searchCollections(q: String): List<SearchHit> = safe {
        tmdb.searchCollections(q, apiKey = prefs.tmdbApiKey).results.map { SearchHit(it.id, it.name, img(it.posterPath)) }
    }

    suspend fun searchPeople(q: String): List<SearchHit> = safe {
        tmdb.searchPeople(q, apiKey = prefs.tmdbApiKey).results.map { p ->
            SearchHit(p.id, p.name, img(p.profilePath), when (p.department) { "Acting" -> "Atuação"; "Directing" -> "Direção"; null -> ""; else -> p.department })
        }
    }

    suspend fun searchKeywords(q: String): List<SearchHit> = safe {
        tmdb.searchKeywords(q, apiKey = prefs.tmdbApiKey).results.map { SearchHit(it.id, it.name, null) }
    }

    suspend fun genres(tv: Boolean): List<SearchHit> = safe {
        (if (tv) tmdb.getTvGenres(apiKey = prefs.tmdbApiKey) else tmdb.getMovieGenres(apiKey = prefs.tmdbApiKey)).genres.map { SearchHit(it.id, it.name, null) }
    }

    suspend fun providers(region: String, tv: Boolean): List<SearchHit> = safe {
        tmdb.getWatchProviders(if (tv) "tv" else "movie", region, apiKey = prefs.tmdbApiKey).results
            .sortedBy { it.priority }.map { SearchHit(it.id, it.name, img(it.logoPath)) }
    }

    /** Nome e imagem de uma fonte colada por id ou link. Null quando o TMDB não acha. */
    suspend fun describe(type: TmdbSourceType, id: Int): SearchHit? = withContext(Dispatchers.IO) {
        val key = prefs.tmdbApiKey
        runCatching {
            when (type) {
                TmdbSourceType.LIST -> tmdb.getList(id, apiKey = key).let { SearchHit(id, it.name.orEmpty(), img(it.posterPath)) }
                TmdbSourceType.COLLECTION -> tmdb.getCollection(id, apiKey = key).let { SearchHit(id, it.name, img(it.posterPath)) }
                TmdbSourceType.COMPANY -> tmdb.getCompany(id, key).let { SearchHit(id, it.name, img(it.logoPath)) }
                TmdbSourceType.NETWORK -> tmdb.getNetwork(id, key).let { SearchHit(id, it.name, img(it.logoPath)) }
                TmdbSourceType.PERSON, TmdbSourceType.DIRECTOR -> tmdb.getPerson(id, apiKey = key).let { SearchHit(id, it.name, img(it.profilePath)) }
                TmdbSourceType.DISCOVER -> null
            }
        }.getOrNull()?.takeIf { it.name.isNotBlank() }
    }

    private suspend fun safe(block: suspend () -> List<SearchHit>): List<SearchHit> = withContext(Dispatchers.IO) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }

    private fun img(path: String?) = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w342$it" }

    private companion object {
        /** Itens lidos por página de catálogo de addon (igual à Início). */
        const val ADDON_PAGE = 40
    }
}
