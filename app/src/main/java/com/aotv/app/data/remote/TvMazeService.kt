// ─── data/remote/TvMazeService.kt (Lote 24 item 12, v1.34.0) ─────────────
// TVmaze (api.tvmaze.com): grátis, sem chave nem login. O objeto do
// episódio já traz `rating.average` (0–10).
//
// Resolução da série: prefere o lookup por IMDb id (o mais confiável — não
// erra a série); sem IMDb id, busca por título e usa o 1º resultado. Quando
// o IMDb id EXISTE mas o TVmaze não o conhece (404), CAI p/ a busca por
// título (Lote mpv 2, v1.37.0 — reversão da decisão do Lote 24): o link de
// IMDb falta em muitas entradas do TVmaze, então o 404 calava a fonte p/
// séries que ele conhece pelo título — era a causa raiz do "TVmaze não
// aparece nas notas" (o OMDb, que sempre tenta o título original quando o
// exibido falha, aparecia). A busca usa o 1º resultado (o mais relevante);
// o risco de homônimo/remake continua existindo, mas é menor que a fonte
// nunca aparecer. Título em PT-BR quase nunca casa — verificado ao vivo:
// "O Mentalista" não retorna nada, "The Mentalist" retorna a série —
// por isso o título original é tentado quando o exibido falha. Erro de
// rede NÃO cai p/ a busca nem entra no cache: a próxima abertura tenta
// de novo (um null cacheado por falha passageira sumiria com a fonte na
// sessão inteira).
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
        var rating: Double?
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

    /**
     * Testa a conexão com o TVmaze (Lote mpv 2, v1.37.0): busca conhecida
     * ("Breaking Bad") — a API é grátis e SEM chave, sem login. Sucesso =
     * a API respondeu com pelo menos um show. Nunca lança (só o
     * CancellationException da coroutine passa).
     */
    suspend fun testConnection(): Result<String> {
        return try {
            val found = api.searchShows("Breaking Bad")
            if (found.any { it.show != null }) {
                Result.success("TVmaze conectado — a API respondeu à busca de teste.")
            } else {
                Result.failure(IllegalStateException("O TVmaze respondeu, mas sem nenhum show na busca de teste."))
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(IllegalStateException("Não foi possível falar com o TVmaze. Confira a conexão e tente de novo."))
        }
    }

    /** Resultado do lookup por IMDb: achado, "não conhece" (404) ou erro transitório. */
    private sealed class ImdbLookup {
        data class Found(val showId: Int) : ImdbLookup()
        object NotFound : ImdbLookup()
        object TransientError : ImdbLookup()
    }

    /** Lookup por IMDb id, distinguindo 404 (definitivo) de erro transitório. */
    private suspend fun lookupByImdb(cleanImdb: String): ImdbLookup {
        return try {
            ImdbLookup.Found(api.lookupByImdb(cleanImdb).id)
        } catch (e: HttpException) {
            currentCoroutineContext().ensureActive()
            if (e.code() == 404) ImdbLookup.NotFound else ImdbLookup.TransientError
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            ImdbLookup.TransientError
        }
    }

    /** Id da série no TVmaze (null = não encontrada). Resultado cacheado. */
    private suspend fun resolveShowId(title: String, imdbId: String?, originalTitle: String = ""): Int? {
        val cleanImdb = imdbId?.trim().orEmpty()
        if (cleanImdb.isNotBlank()) {
            val key = "imdb:$cleanImdb"
            synchronized(showIdCache) {
                if (showIdCache.containsKey(key)) return showIdCache[key]
            }
            when (val lookup = lookupByImdb(cleanImdb)) {
                is ImdbLookup.Found -> {
                    synchronized(showIdCache) { showIdCache[key] = lookup.showId }
                    return lookup.showId
                }
                ImdbLookup.NotFound -> {
                    // 404: o TVmaze não conhece esse IMDb id — mas pode
                    // conhecer a série pelo título (o link de IMDb falta em
                    // muitas entradas). Cai p/ a busca por título abaixo,
                    // como se não houvesse IMDb id (ver o comentário do
                    // arquivo). O 404 em si não entra no cache.
                }
                ImdbLookup.TransientError -> return null
            }
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
            // Erro de transporte (rede/HTTP): NÃO cacheia — um null cacheado
            // aqui faria a fonte sumir na sessão inteira por uma falha
            // passageira (o OMDb já faz assim: erro de rede não entra).
            return null
        }
        synchronized(showIdCache) { showIdCache[key] = id }
        return id
    }
}
