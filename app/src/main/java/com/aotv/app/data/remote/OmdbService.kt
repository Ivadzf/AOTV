// ─── data/remote/OmdbService.kt (Lote 24 item 12, v1.34.0) ───────────────
// OMDb (omdbapi.com): nota do IMDb por episódio (`imdbRating`, 0–10).
//
// A chave é GRATUITA e DO USUÁRIO (criar em omdbapi.com, 1000 req/dia) —
// NUNCA embutida no código, guardada nas prefs do aparelho (ver
// [EpisodeRatingSettings]), nunca em log. Sem chave, a fonte fica
// indisponível e NÃO entra na média. `imdbRating` ausente ou "N/A" = sem
// nota p/ o episódio.
//
// Cache em memória (LRU, 512 entradas, chave = título+temporada+episódio):
// o mesmo episódio não é consultado 2x na mesma sessão — protege a cota
// diária. Só respostas definitivas entram no cache (erro de rede não
// entra: na próxima abertura da temporada ele tenta de novo).

package com.aotv.app.data.remote

import com.aotv.app.data.ratings.EpisodeRatingSettings
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/** Resposta do OMDb p/ um episódio. Só `imdbRating` é lido. */
data class OmdbEpisodeResponse(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Season") val season: String? = null,
    @SerializedName("Episode") val episode: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("Response") val response: String? = null,
    @SerializedName("Error") val error: String? = null,
)

private interface OmdbApi {
    @GET("/")
    suspend fun getEpisode(
        @Query("t") title: String,
        @Query("Season") season: Int,
        @Query("Episode") episode: Int,
        @Query("apikey") apiKey: String,
    ): OmdbEpisodeResponse
}

@Singleton
class OmdbService @Inject constructor(
    client: OkHttpClient,
    private val settings: EpisodeRatingSettings,
) {
    companion object {
        private const val BASE = "https://www.omdbapi.com/"
        private const val MAX_CACHE_ENTRIES = 512
    }

    private val api: OmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .client(client.newBuilder().cache(null).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OmdbApi::class.java)
    }

    private val cache = object : LinkedHashMap<String, Double?>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double?>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /** Tem chave do usuário guardada (sem chave, a fonte não existe). */
    val isConfigured: Boolean get() = settings.omdbKeyFlow.value.isNotBlank()

    /**
     * Nota do IMDb do episódio (0–10). null = sem chave, sem nota ("N/A" ou
     * episódio não encontrado) ou erro de rede/HTTP. [originalTitle] é o
     * título original da série: o OMDb casa melhor pelo título em inglês,
     * então quando o título exibido (PT-BR) não retorna nada, tenta o
     * original antes de desistir (2 chamadas no pior caso, cacheadas).
     * Nunca lança (só o CancellationException da coroutine passa).
     */
    suspend fun getEpisodeRating(title: String, season: Int, episode: Int, originalTitle: String = ""): Double? {
        val key = settings.omdbKeyFlow.value.trim()
        if (key.isBlank() || title.isBlank() || season <= 0 || episode <= 0) return null
        fetchEpisodeRating(title, season, episode, key)?.let { return it }
        val original = originalTitle.trim()
        if (original.isNotBlank() && !original.equals(title.trim(), ignoreCase = true)) {
            fetchEpisodeRating(original, season, episode, key)?.let { return it }
        }
        return null
    }

    private suspend fun fetchEpisodeRating(queryTitle: String, season: Int, episode: Int, key: String): Double? {
        val cacheKey = "${queryTitle.lowercase()}|$season|$episode"
        synchronized(cache) {
            if (cache.containsKey(cacheKey)) return cache[cacheKey]
        }
        val resp = try {
            api.getEpisode(queryTitle, season, episode, key)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return null
        }
        val rating = if (resp.response != "True") {
            null
        } else {
            resp.imdbRating
                ?.takeIf { it != "N/A" }
                ?.toDoubleOrNull()
                ?.takeIf { it in 0.0..10.0 }
                ?.takeIf { it > 0 }
        }
        synchronized(cache) { cache[cacheKey] = rating }
        return rating
    }
}
