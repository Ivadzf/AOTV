// ─── data/remote/MDBListService.kt (v1.17.0) ─────────────────────────────────
// MDBList: notas por fonte e listas dinâmicas. Read-only: o serviço NUNCA
// envia nada (só GET). Adaptação do arquivo da outra IA: mesma estrutura,
// com prefs tipadas (padrão Trakt/Simkl). A chave de API é DO APARELHO
// (prefs.mdbListApiKey); ligado e fontes são POR PERFIL.

package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
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
    val ratings: MdbRatings?,
    // Lote 19 (v1.29.0): a API devolve {"response":"False","error":"..."}
    // quando recusa a busca (chave inválida, título não achado — estilo
    // OMDb). Sem estes campos, o erro virava ratings nulo → mapa vazio e
    // a seção sumia em silêncio no detalhe.
    @SerializedName("response") val response: String? = null,
    @SerializedName("error") val error: String? = null
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

// ── Lote 19 (v1.29.0): busca com fallback de título ────────────────────────
// [ratings] vazio + [matchedTitle] null = nenhum título tentado casou na
// API; [matchedTitle] não nulo + [ratings] vazio = o título existe, mas
// nenhuma fonte habilitada tem nota p/ ele. [apiError] é o motivo cru da
// API quando ela recusou a busca (ver [MDBListService.translateApiError]).
data class MdbRatingLookup(
    val ratings: Map<String, Double?>,
    /** Título devolvido pela API quando houve notas (pode ser o original). */
    val foundTitle: String?,
    /** Título que a API reconheceu, mesmo sem nota nas fontes habilitadas. */
    val matchedTitle: String?,
    /** Títulos tentados, em ordem (localizado, depois o original). */
    val attemptedTitles: List<String>,
    /** Motivo cru da API quando ela recusou a busca; null = busca aceita. */
    val apiError: String?
)

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
    ): MdbRatingResult?
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
        // Título de teste p/ validar a chave (filme conhecido, sempre no
        // MDBList). Só o TÍTULO é fixo — a chave testada vem do usuário.
        private const val TEST_TITLE = "Dune"
        private const val TEST_YEAR = 2021
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
        // Lote 9 (v1.19.0): a API devolve UM objeto, não uma lista (o tipo
        // List<> quebrava com "Expected BEGIN_ARRAY but was BEGIN_OBJECT").
        // Título não encontrado → ratings nulo → mapa vazio e a seção some.
        val result = api.searchRatings(title, year, apiKey, type)
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

    // ── Lote 19 (v1.29.0): motivo do erro + fallback de título ─────────────

    /** Mapa fonte → nota (só as fontes habilitadas no perfil) p/ um resultado da API. */
    private fun ratingsMap(result: MdbRatingResult?): Map<String, Double?> {
        val ratings = result?.ratings ?: return emptyMap()
        val enabled = getEnabledSources()
        return buildMap {
            if ("imdb" in enabled) ratings.imdb?.value?.let { put("IMDb", it) }
            if ("tmdb" in enabled) ratings.tmdb?.value?.let { put("TMDB", it) }
            if ("trakt" in enabled) ratings.trakt?.value?.let { put("Trakt", it) }
            if ("letterboxd" in enabled) ratings.letterboxd?.value?.let { put("Letterboxd", it) }
            if ("tomatoes" in enabled) ratings.rottenTomatoes?.value?.let { put("RT", it) }
            if ("metacritic" in enabled) ratings.metacritic?.value?.let { put("Metacritic", it) }
            if ("rogerebert" in enabled) ratings.rogerEbert?.value?.let { put("RogerEbert", it) }
        }
    }

    /**
     * Mensagem legível p/ um erro da chamada ao MDBList. Chave inválida ou
     * limite atingido devolve HTTP 503 com o motivo no corpo
     * ({"response":"False","error":"Invalid API key or Rate Limiter Reached!"}
     * — conferido ao vivo em 2026-11-17); o Retrofit transforma o 503 em
     * HttpException e o motivo se perderia se só se lesse e.message
     * ("HTTP 503 Service Unavailable"). Sem rede, IOException.
     */
    fun apiErrorMessage(error: Throwable): String {
        val http = error as? HttpException
        if (http != null) {
            val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
            val apiError = body?.let { extractApiError(it) }
            if (!apiError.isNullOrBlank()) return translateApiError(apiError)
            return "MDBList respondeu HTTP ${http.code()}"
        }
        if (error is IOException) return "Sem conexão com o MDBList — confira a internet e tente de novo."
        return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    /** Extrai o campo "error" de um corpo {"response":"False","error":"..."}. */
    private fun extractApiError(body: String): String? =
        Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)

    /**
     * Traduz os erros conhecidos da API p/ PT-BR; texto desconhecido volta
     * cru (a API responde em inglês). "Invalid API key" cobre chave inválida
     * E limite atingido — a API usa a mesma frase p/ os dois (ao vivo, 503).
     */
    fun translateApiError(raw: String?): String {
        val text = raw?.trim().orEmpty()
        return when {
            text.isBlank() -> "O MDBList recusou a busca sem explicar o motivo."
            "Invalid API key" in text -> "Chave inválida ou limite de uso do MDBList atingido — confira a chave em Configurações → Trackers."
            "not found" in text.lowercase() -> "Título não encontrado no MDBList."
            else -> text
        }
    }

    /**
     * Busca notas tentando o título localizado e, se a API não reconhecer
     * ele, o título original (Lote 19, v1.29.0 — "continua não funcionando"):
     * o TMDB devolve o título em PT-BR ("Duna") e a busca `?s=` do MDBList
     * nem sempre casa com ele; o originalTitle já chegava ao ViewModel e
     * não era usado aqui. Título achado mas sem nota nas fontes habilitadas
     * NÃO tenta o outro título — o problema não é o nome. Nunca inventa
     * nota: sem notas, o mapa volta vazio e o motivo vai em
     * [MdbRatingLookup.apiError] ou é dedutível de
     * [MdbRatingLookup.matchedTitle]/[MdbRatingLookup.attemptedTitles].
     */
    suspend fun getRatingsWithFallback(
        title: String,
        originalTitle: String,
        year: Int?,
        isMovie: Boolean
    ): Result<MdbRatingLookup> {
        return try {
            require(isConnected) { "Chave MDBList não configurada" }
            val type = if (isMovie) "movie" else "show"
            val candidates = listOf(title, originalTitle)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            val attempted = mutableListOf<String>()
            var lastApiError: String? = null
            var found: MdbRatingLookup? = null
            var matched: String? = null
            for (candidate in candidates) {
                attempted.add(candidate)
                val result = api.searchRatings(candidate, year, apiKey, type)
                if (result?.response == "False") {
                    lastApiError = result.error?.takeIf { it.isNotBlank() }
                    continue
                }
                if (result?.ratings != null) {
                    matched = result.title ?: candidate
                    val map = ratingsMap(result)
                    if (map.isNotEmpty()) {
                        found = MdbRatingLookup(map, result.title ?: candidate, matched, attempted.toList(), null)
                    }
                    break
                }
                // ratings nulo = a API não reconheceu este título → tenta o próximo.
            }
            Result.success(found ?: MdbRatingLookup(emptyMap(), null, matched, attempted.toList(), lastApiError))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(apiErrorMessage(e)))
        }
    }

    /**
     * Valida uma chave (a digitada, ainda não salva, ou a salva) com uma
     * busca conhecida — "Dune" (2021) sempre existe no MDBList. Chave
     * inválida devolve HTTP 503 + "Invalid API key or Rate Limiter Reached!"
     * (ao vivo, 2026-11-17). Nunca embute chave: a chave testada vem do
     * parâmetro, digitada pelo usuário.
     */
    suspend fun testApiKey(key: String): Result<String> {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalStateException("Cole uma chave antes de testar."))
        }
        return try {
            val result = api.searchRatings(TEST_TITLE, TEST_YEAR, trimmed, "movie")
            if (result?.response == "False") {
                Result.failure(IllegalStateException(translateApiError(result.error)))
            } else if (result?.ratings == null) {
                Result.failure(IllegalStateException("O MDBList respondeu, mas sem notas para o título de teste — tente de novo."))
            } else {
                Result.success("Chave válida — o MDBList respondeu com as notas de “Dune (2021)”.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(apiErrorMessage(e)))
        }
    }
}
