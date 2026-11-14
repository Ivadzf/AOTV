// ─── data/remote/MDBListService.kt (v1.17.0) ─────────────────────────────────
// MDBList: notas por fonte e listas dinâmicas. Read-only: o serviço NUNCA
// envia nada (só GET). Adaptação do arquivo da outra IA: mesma estrutura,
// com prefs tipadas (padrão Trakt/Simkl). A chave de API é DO APARELHO
// (prefs.mdbListApiKey); ligado e fontes são POR PERFIL.

package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════════════════════
// Modelos
// ══════════════════════════════════════════════════════════════════════════════

data class MdbUserList(
    val id: Int,
    val name: String,
    val mediatype: String,
    val items: Int,
    val likes: Int,
    val description: String?
)

data class MdbListItem(
    val id: Int,
    val title: String,
    val year: Int?,
    val score: Double?,
    @SerializedName("score_average") val scoreAverage: Double?,
    val mediatype: String,
    val tmdbid: Int?,
    val imdbid: String?
)

data class MdbRatingResult(
    val title: String?,
    val year: Int?,
    val score: Double?,
    @SerializedName("score_average") val scoreAverage: Double?,
    val ratings: MdbRatings?
)

data class MdbRatings(
    val imdb: MdbSource?,
    val tmdb: MdbSource?,
    val trakt: MdbSource?,
    val letterboxd: MdbSource?,
    @SerializedName("tomatoes") val rottenTomatoes: MdbSource?,
    val metacritic: MdbSource?,
    @SerializedName("rogerebert") val rogerEbert: MdbSource?
)

data class MdbSource(val value: Double?, val count: Int?)

// ══════════════════════════════════════════════════════════════════════════════
// Interface Retrofit
// ══════════════════════════════════════════════════════════════════════════════

private interface MdbApi {
    @GET("api/lists/u/{user}/")
    suspend fun getUserLists(
        @Path("user") user: String,
        @Query("apikey") key: String
    ): List<MdbUserList>

    @GET("api/lists/{id}/items/")
    suspend fun getListItems(
        @Path("id") id: Int,
        @Query("apikey") key: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0
    ): List<MdbListItem>

    @GET("api/")
    suspend fun searchRatings(
        @Query("s") title: String,
        @Query("y") year: Int?,
        @Query("apikey") key: String,
        @Query("m") mediaType: String = "movie"
    ): List<MdbRatingResult>
}

// ══════════════════════════════════════════════════════════════════════════════
// Service
// ══════════════════════════════════════════════════════════════════════════════

@Singleton
class MDBListService @Inject constructor(
    private val prefs: PlatformPrefs,
    client: OkHttpClient
) {
    companion object {
        private const val BASE = "https://mdblist.com/"
        const val PREF_API_KEY = "mdblist_api_key" // global
        const val PREF_ENABLED = "mdblist_enabled" // por perfil
        const val PREF_RATING_SOURCES = "mdblist_rating_sources" // por perfil, CSV
        val DEFAULT_SOURCES = setOf("imdb", "tmdb", "trakt", "letterboxd", "tomatoes", "metacritic")
    }

    private val api: MdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .client(client.newBuilder().cache(null).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MdbApi::class.java)
    }

    val apiKey: String get() = prefs.mdbListApiKey
    val isConnected: Boolean get() = apiKey.isNotBlank()
    val isEnabled: Boolean get() = prefs.mdbListEnabled && isConnected

    fun setApiKey(key: String) {
        prefs.mdbListApiKey = key
    }

    fun setEnabled(on: Boolean) {
        prefs.mdbListEnabled = on
    }

    fun getEnabledSources(): Set<String> {
        val stored = prefs.mdbListRatingSources
        return if (stored.isBlank()) DEFAULT_SOURCES else stored.split(",").toSet()
    }

    fun setEnabledSources(sources: Set<String>) {
        prefs.mdbListRatingSources = sources.joinToString(",")
    }

    // ── Listas dinâmicas ─────────────────────────────────────────────────────

    suspend fun getUserLists(): Result<List<MdbUserList>> = runCatching {
        require(isConnected) { "Chave MDBList não configurada" }
        api.getUserLists("me", apiKey)
    }

    suspend fun getListItems(listId: Int, limit: Int = 500): Result<List<MdbListItem>> = runCatching {
        require(isConnected) { "Chave MDBList não configurada" }
        api.getListItems(listId, apiKey, limit)
    }

    // ── Notas por fonte ──────────────────────────────────────────────────────

    /**
     * Busca notas de todas as fontes ativas para um título.
     * Retorna apenas as fontes presentes em [getEnabledSources()].
     */
    suspend fun getRatings(
        title: String,
        year: Int?,
        isMovie: Boolean
    ): Result<Map<String, Double?>> = runCatching {
        require(isConnected) { "Chave MDBList não configurada" }
        val type = if (isMovie) "movie" else "show"
        val result = api.searchRatings(title, year, apiKey, type).firstOrNull()
        val ratings = result?.ratings ?: return@runCatching emptyMap()
        val enabled = getEnabledSources()
        buildMap {
            if ("imdb" in enabled) ratings.imdb?.value?.let { put("IMDb", it) }
            if ("tmdb" in enabled) ratings.tmdb?.value?.let { put("TMDB", it) }
            if ("trakt" in enabled) ratings.trakt?.value?.let { put("Trakt", it) }
            if ("letterboxd" in enabled) ratings.letterboxd?.value?.let { put("Letterboxd", it) }
            if ("tomatoes" in enabled) ratings.rottenTomatoes?.value?.let { put("RT", it) }
            if ("metacritic" in enabled) ratings.metacritic?.value?.let { put("Metacritic", it) }
            if ("rogerebert" in enabled) ratings.rogerEbert?.value?.let { put("RogerEbert", it) }
        }
    }
}
