package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Troca o id do Stremio (tt ou tmdb:) pelo do TMDB, para o cartão abrir o detalhe normal. Usado
 * pelas fileiras de addon da Início e pelas pastas das coleções. Item sem pôster fica de fora;
 * item com id IMDb mas sem par no TMDB (sem chave, por exemplo) fica com o id IMDb e o detalhe
 * resolve o TMDB ao abrir.
 */
@Singleton
class AddonMetaResolver @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) {
    private val imdbToTmdb = java.util.concurrent.ConcurrentHashMap<String, Int>()

    // Compilados uma vez: antes cada item compilava os dois (40 por página de
    // fileira de addon, na Main) ao aparecer a fileira.
    private val tmdbIdRegex = Regex("""^tmdb:(?:movie:|series:)?(\d+)""")
    private val imdbIdRegex = Regex("""^tt\d+""")

    suspend fun resolve(metas: List<AddonMeta>, series: Boolean): List<MovieItem> = coroutineScope {
        val apiKey = prefs.tmdbApiKey
        metas.map { m ->
            async { resolveOne(m, series, apiKey) }
        }.awaitAll().filterNotNull()
    }

    /**
     * Resultado do casamento catálogo de addon → TMDB, p/ o Descobrir.
     * [matched] só traz título COM par no TMDB (id > 0); [unmatched] é tudo o que ficou
     * sem par — o Descobrir tenta o casamento por título+ano nesses e, se nada casar,
     * mostra o aviso honesto em vez de exibir o título como se fosse do TMDB.
     */
    data class DiscoverResolveResult(val matched: List<MovieItem>, val unmatched: List<AddonMeta>)

    /** Como [resolve], mas separa quem tem par no TMDB de quem não tem. */
    suspend fun resolveDiscover(metas: List<AddonMeta>, series: Boolean): DiscoverResolveResult = coroutineScope {
        val apiKey = prefs.tmdbApiKey
        val pairs = metas.map { m -> async { m to resolveOne(m, series, apiKey) } }.awaitAll()
        val matched = pairs.mapNotNull { (_, item) -> item?.takeIf { it.tmdbId > 0 } }
        val unmatched = pairs.filter { (_, item) -> item == null || item.tmdbId == 0 }.map { (m, _) -> m }
        DiscoverResolveResult(matched, unmatched)
    }

    private suspend fun resolveOne(m: AddonMeta, series: Boolean, apiKey: String): MovieItem? {
        if (m.poster.isBlank()) return null
        val tm = tmdbIdRegex.find(m.id)?.groupValues?.get(1)?.toIntOrNull()
        val imdb = imdbIdRegex.find(m.id)?.value
        val id = tm ?: imdb?.let { tt ->
            imdbToTmdb[tt] ?: runCatching {
                val r = tmdb.findByImdb(tt, apiKey = apiKey)
                val hit = if (series) r.tvResults.firstOrNull() ?: r.movieResults.firstOrNull()
                else r.movieResults.firstOrNull() ?: r.tvResults.firstOrNull()
                hit?.id
            }.getOrNull()?.also { imdbToTmdb[tt] = it }
        }
        // Sem par no TMDB (sem chave, por exemplo) o item fica com o id IMDb: o
        // detalhe resolve o TMDB ao abrir, quando houver chave. Só sai quem não tem
        // nem id tmdb: nem id IMDb.
        if (id == null && imdb == null) return null
        return MovieItem(
            tmdbId = id ?: 0, embedId = imdb ?: id.toString(), title = m.name, overview = m.description,
            posterUrl = m.poster, backdropUrl = m.background, year = m.year, rating = m.rating,
            type = if (series) MediaType.SERIES else MediaType.MOVIE,
        )
    }
}
