// ─── data/remote/TvMazeService.kt (Lote 24 item 12, v1.34.0) ─────────────
// TVmaze (api.tvmaze.com): grátis, sem chave nem login. O objeto do
// episódio já traz `rating.average` (0–10).
//
// Resolução da série: prefere o lookup por IMDb id (o mais confiável — não
// erra a série); sem IMDb id, busca por título e usa o 1º resultado. Quando
// o IMDb id EXISTE mas o TVmaze não o conhece (404), NÃO cai p/ a busca por
// título: casar por título quando o id disse "não conheço" arriscaria
// trazer a nota da série errada (remakes, homônimos).
//
// Cobertura menor — muito episódio sem nota (rating.average null): sem
// nota = fora da média, nunca zero. Cache em memória igual ao do OMDb
// (LRU 512 p/ notas, 128 p/ ids de série).

package com.aotv.app.data.remote

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

data class TvMazeShow(
    val id: Int,
    val name: String? = null,
)

data class TvMazeSearchResult(
    val show: TvMazeShow? = null,
)

data class TvMazeRating(
    val average: Double? = null,
)

/** Episódio do TVmaze. Só `rating.average` é lido. */
data class TvMazeEpisode(
    val id: Int = 0,
    val name: String? = null,
    val season: Int = 0,
    val number: Int = 0,
    val rating: TvMazeRating? = null,
)

private interface TvMazeApi {
    @GET("lookup/shows")
    suspend fun lookupByImdb(@Query("imdb") imdb: String): TvMazeShow

    @GET("search/shows")
    suspend fun searchShows(@Query("q") query: String): List<TvMazeSearchResult>

    @GET("shows/{id}/episodebynumber")
    suspend fun episodeByNumber(
        @Path("id") showId: Int,
        @Query("season") season: Int,
        @Query("number") number: Int,
    ): TvMazeEpisode
}

@Singleton
class TvMazeService @Inject constructor(
    client: OkHttpClient,
) {
    companion object {
        private const val BASE = "https://api.tvmaze.com/"
        private const val MAX_RATING_CACHE_ENTRIES = 512
        private const val MAX_SHOW_CACHE_ENTRIES = 128
    }

    private val api: TvMazeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .client(client.newBuilder().cache(null).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TvMazeApi::class.java)
    }

    private val ratingCache = object : LinkedHashMap<String, Double?>(MAX_RATING_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double?>): Boolean =
            size > MAX_RATING_CACHE_ENTRIES
    }

    private val showIdCache = object : LinkedHashMap<String, Int?>(MAX_SHOW_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int?>): Boolean =
            size > MAX_SHOW_CACHE_ENTRIES
    }

    /**
     * Nota do episódio no TVmaze (0–10). null = série não encontrada,
     * episódio sem nota ou erro de rede/HTTP. [originalTitle] é o título
     * original da série (fallback da busca por título, como no OMDb).
     * Nunca lança (só o CancellationException da coroutine passa).
     */
    suspend fun getEpisodeRating(
        season: Int,
        episode: Int,
        title: String,
        imdbId: String?,
        originalTitle: String = "",
    ): Double? {
        if (season <= 0 || episode <= 0) return null
        val showId = resolveShowId(title, imdbId, originalTitle) ?: return null
        val cacheKey = "$showId|$season|$episode"
        synchronized(ratingCache) {
            if (ratingCache.containsKey(cacheKey)) return ratingCache[cacheKey]
        }
        val rating: Double?
        try {
            rating = api.episodeByNumber(showId, season, episode).rating?.average
                ?.takeIf { it in 0.0..10.0 }
                ?.takeIf { it > 0 }
        } catch (e: HttpException) {
            currentCoroutineContext().ensureActive()
            // 404 = episódio não existe no TVmaze: resposta definitiva (entra
            // no cache como sem nota). Outro HTTP = erro transitório, sem cache.
            if (e.code() != 404) return null
            rating = null
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return null
        }
        synchronized(ratingCache) { ratingCache[cacheKey] = rating }
        return rating
    }

    /** Id da série no TVmaze (null = não encontrada). Resultado cacheado. */
    private suspend fun resolveShowId(title: String, imdbId: String?, originalTitle: String = ""): Int? {
        val cleanImdb = imdbId?.trim().orEmpty()
        if (cleanImdb.isNotBlank()) {
            val key = "imdb:$cleanImdb"
            synchronized(showIdCache) {
                if (showIdCache.containsKey(key)) return showIdCache[key]
            }
            val id = try {
                api.lookupByImdb(cleanImdb).id
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
            synchronized(showIdCache) { showIdCache[key] = id }
            // IMDb conhecido mas sem par no TVmaze: volta null sem tentar por
            // título (ver o comentário do arquivo).
            return id
        }
        searchShowId(title)?.let { return it }
        val original = originalTitle.trim()
        if (original.isNotBlank() && !original.equals(title.trim(), ignoreCase = true)) {
            searchShowId(original)?.let { return it }
        }
        return null
    }

    /** 1º resultado da busca por título (null = nada ou erro). Cacheado por título. */
    private suspend fun searchShowId(query: String): Int? {
        val cleanTitle = query.trim()
        if (cleanTitle.isBlank()) return null
        val key = "title:${cleanTitle.lowercase()}"
        synchronized(showIdCache) {
            if (showIdCache.containsKey(key)) return showIdCache[key]
        }
        val id = try {
            api.searchShows(cleanTitle).firstOrNull()?.show?.id
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
        synchronized(showIdCache) { showIdCache[key] = id }
        return id
    }
}
