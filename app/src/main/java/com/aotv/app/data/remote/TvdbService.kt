// ─── data/remote/TvdbService.kt (v1.17.0) ───────────────────────────────────
// TheTVDB (API v4): busca de séries e ordem de episódios (official/dvd/
// absolute). Adaptação do arquivo da outra IA: mesma estrutura, com prefs
// tipadas (padrão Trakt/Simkl). A chave de API e o token cacheado são DO
// APARELHO (prefs.tvdbApiKey/tvdbAuthToken/tvdbTokenExpiry); a ordem e o
// toggle são POR PERFIL. A aplicação da ordem no navegador de episódios
// (EpisodeBrowser) ficou como follow-up: o navegador é função pura sobre a
// lista do TMDB, sem id da série nem acesso a serviço — plugar o mapa TVDB
// ali exige a integração na tela que carrega as temporadas, não aqui.
// A seção da TVDB na tela de Trackers é de outra frente.

package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════════════════════
// Modelos
// ══════════════════════════════════════════════════════════════════════════════

data class TvdbLoginResponse(val data: TvdbAuthToken?)
data class TvdbAuthToken(val token: String)

data class TvdbSearchResponse(val data: List<TvdbSearchResult>?)
data class TvdbSearchResult(
    @SerializedName("tvdb_id") val tvdbId: String?,
    val id: String?,
    val name: String?,
    val type: String?,
    @SerializedName("extended_title") val extTitle: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("first_air_time") val firstAirTime: String?
)

data class TvdbSeriesExtResponse(val data: TvdbSeriesExt?)
data class TvdbSeriesExt(
    val id: Int,
    val name: String,
    val seasons: List<TvdbSeasonSummary>?
)

data class TvdbSeasonSummary(
    val id: Int,
    val number: Int,
    val type: TvdbSeasonTypeRef
)

data class TvdbSeasonTypeRef(
    val id: Int,
    val name: String,
    val type: String // "official" | "dvd" | "absolute" | "alternate"
)

data class TvdbSeasonExtResponse(val data: TvdbSeasonExt?)
data class TvdbSeasonExt(val episodes: List<TvdbEpisode>?)

data class TvdbEpisode(
    val id: Int,
    val number: Int,
    @SerializedName("seasonNumber") val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    val aired: String?,
    val runtime: Int?,
    val image: String?,
    @SerializedName("absoluteNumber") val absoluteNumber: Int?
)

/** Mapeamento de um episódio TVDB para a posição no TMDB (mesma série). */
data class TvdbEpisodeMap(
    val tvdbEpisodeId: Int,
    val tvdbSeason: Int,
    val tvdbEpisode: Int,
    val absoluteNumber: Int?,
    // tmdbSeason/tmdbEpisode só diferem quando ordering != "official"
    val tmdbSeason: Int,
    val tmdbEpisode: Int
)

// ══════════════════════════════════════════════════════════════════════════════
// Interface Retrofit
// ══════════════════════════════════════════════════════════════════════════════

private interface TvdbApi {
    @POST("login")
    suspend fun login(@Body req: Map<String, String>): TvdbLoginResponse

    @GET("search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("type") type: String = "series"
    ): TvdbSearchResponse

    @GET("series/{id}/extended")
    suspend fun getSeriesExtended(
        @Header("Authorization") auth: String,
        @Path("id") id: Int
    ): TvdbSeriesExtResponse

    @GET("seasons/{id}/extended")
    suspend fun getSeasonExtended(
        @Header("Authorization") auth: String,
        @Path("id") id: Int
    ): TvdbSeasonExtResponse
}

// ══════════════════════════════════════════════════════════════════════════════
// Service
// ══════════════════════════════════════════════════════════════════════════════

@Singleton
class TvdbService @Inject constructor(
    private val prefs: PlatformPrefs,
    client: OkHttpClient
) {
    companion object {
        private const val BASE = "https://api4.thetvdb.com/v4/"
        const val PREF_API_KEY = "tvdb_api_key" // global
        const val PREF_AUTH_TOKEN = "tvdb_auth_token" // global (cacheado)
        const val PREF_TOKEN_EXPIRY = "tvdb_token_expiry" // global (Long ms)
        const val PREF_ORDERING = "tvdb_episode_ordering" // por perfil: official|dvd|absolute
        const val PREF_ENABLED = "tvdb_ordering_enabled" // por perfil
        val ORDERINGS = setOf("official", "dvd", "absolute")
    }

    private val api: TvdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .client(client.newBuilder().cache(null).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TvdbApi::class.java)
    }

    val apiKey: String get() = prefs.tvdbApiKey
    val isConnected: Boolean get() = apiKey.isNotBlank()
    val isEnabled: Boolean get() = prefs.tvdbOrderingEnabled && isConnected
    fun getOrdering(): String = prefs.tvdbOrdering

    fun setApiKey(key: String) {
        prefs.tvdbApiKey = key
        prefs.tvdbAuthToken = "" // invalida token anterior
        prefs.tvdbTokenExpiry = 0L
    }

    fun setEnabled(on: Boolean) {
        prefs.tvdbOrderingEnabled = on
    }

    /** Só aceita "official", "dvd" ou "absolute"; resto volta p/ "official". */
    fun setOrdering(o: String) {
        prefs.tvdbOrdering = o.takeIf { it in ORDERINGS } ?: "official"
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private suspend fun auth(): String {
        val cached = prefs.tvdbAuthToken
        val expiry = prefs.tvdbTokenExpiry
        if (cached.isNotBlank() && System.currentTimeMillis() < expiry) return "Bearer $cached"
        require(apiKey.isNotBlank()) { "Chave TVDB não configurada" }
        val token = api.login(mapOf("apikey" to apiKey)).data?.token
            ?: throw Exception("Login TVDB falhou: token ausente")
        prefs.tvdbAuthToken = token
        prefs.tvdbTokenExpiry = System.currentTimeMillis() + 23 * 3_600_000L
        return "Bearer $token"
    }

    // ── Busca ─────────────────────────────────────────────────────────────────

    suspend fun searchSeries(title: String): Result<List<TvdbSearchResult>> = runCatching {
        require(isConnected) { "TVDB não configurado" }
        api.search(auth(), title).data ?: emptyList()
    }

    // ── Ordem de episódios ────────────────────────────────────────────────────

    /**
     * Retorna episódios de uma série TVDB na ordem selecionada.
     * [tvdbId] é o ID inteiro da série no TVDB.
     */
    suspend fun getOrderedEpisodes(tvdbId: Int): Result<List<TvdbEpisode>> = runCatching {
        require(isConnected) { "TVDB não configurado" }
        val a = auth()
        val ordering = getOrdering() // "official" | "dvd" | "absolute"
        val series = api.getSeriesExtended(a, tvdbId).data
            ?: throw Exception("Série $tvdbId não encontrada no TVDB")
        val seasons = series.seasons ?: emptyList()
        val episodes = mutableListOf<TvdbEpisode>()
        seasons
            .filter { it.type.type.equals(ordering, ignoreCase = true) }
            .sortedBy { it.number }
            .forEach { season ->
                val eps = api.getSeasonExtended(a, season.id).data?.episodes ?: emptyList()
                episodes.addAll(eps)
            }
        episodes.sortedWith(compareBy({ it.seasonNumber }, { it.number }))
    }

    /**
     * Constrói mapa de correspondência entre ordenação TVDB e posição TMDB.
     * Para ordering == "official" o mapa é trivial (mesma posição).
     * Para "dvd" ou "absolute", os números podem divergir do TMDB.
     */
    suspend fun buildEpisodeMap(tvdbId: Int): Result<List<TvdbEpisodeMap>> = runCatching {
        val episodes = getOrderedEpisodes(tvdbId).getOrThrow()
        // Monta a ordem "official" para servir de referência TMDB
        val officialEps = if (getOrdering() == "official") episodes else {
            require(isConnected) { "TVDB não configurado" }
            val a = auth()
            val series = api.getSeriesExtended(a, tvdbId).data
                ?: throw Exception("Série $tvdbId não encontrada no TVDB")
            val officialSeasons = (series.seasons ?: emptyList())
                .filter { it.type.type.equals("official", ignoreCase = true) }
                .sortedBy { it.number }
            buildList {
                officialSeasons.forEach { s ->
                    addAll(api.getSeasonExtended(a, s.id).data?.episodes ?: emptyList())
                }
            }.sortedWith(compareBy({ it.seasonNumber }, { it.number }))
        }
        // Mapa tvdbEpisodeId → posição official (= posição TMDB)
        val officialById = officialEps.associateBy { it.id }
        episodes.map { ep ->
            val ref = officialById[ep.id]
            TvdbEpisodeMap(
                tvdbEpisodeId = ep.id,
                tvdbSeason = ep.seasonNumber,
                tvdbEpisode = ep.number,
                absoluteNumber = ep.absoluteNumber,
                tmdbSeason = ref?.seasonNumber ?: ep.seasonNumber,
                tmdbEpisode = ref?.number ?: ep.number
            )
        }
    }
}
