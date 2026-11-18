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
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
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
    // Lote 21 (v1.31.0) — CAUSA RAIZ do "continua não funcionando": a API
    // real devolve `ratings` como ARRAY de {"source","value"} — o modelo
    // antigo (objeto com um campo por fonte) nunca casava e o Gson deixava
    // `ratings` nulo, então o detalhe e o teste viam "sem notas" mesmo com
    // a chave válida. Provas: Kometa (modules/mdblist.py:207-208 —
    // `ratings_list = data.get("ratings", [])`, itera a lista, cada item
    // tem `source` e `value`) e o doc do cinemachain (lookup por TMDB id
    // devolve title/year/score/score_average/ratings[...]). Fontes que
    // aparecem no array: imdb, metacritic, metacriticuser, trakt, tomatoes,
    // tomatoesaudience (ou "popcorn"), tmdb, letterboxd, myanimelist, mdb.
    val ratings: List<MdbRatingEntry>?,
    @SerializedName("imdbid") val imdbId: String? = null,
    @SerializedName("tmdbid") val tmdbId: Int? = null,
    // Lote 19 (v1.29.0): a API devolve {"response":"False","error":"..."}
    // quando recusa a busca (chave inválida, título não achado — estilo
    // OMDb). Sem estes campos, o erro virava ratings nulo → mapa vazio e
    // a seção sumia em silêncio no detalhe.
    @SerializedName("response") val response: String? = null,
    @SerializedName("error") val error: String? = null
)

/** Uma nota de uma fonte no array `ratings` da API real (Lote 21). */
data class MdbRatingEntry(
    val source: String = "",
    val value: Double? = null,
    val count: Int? = null,
    val votes: Int? = null
)

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

    // Lote 21 (v1.31.0): busca POR ID TMDB — o detalhe já tem o tmdbId, e
    // buscar por título falha quando o título localizado não casa
    // ("Duna" vs "Dune"). Caminho do Kometa: `api/tmdb/movie/{id}/` (e
    // `api/tmdb/show/{id}/` p/ séries). Fallback: `api/?tm={id}&m=...`
    // (forma documentada no doc do cinemachain).
    @GET("api/tmdb/movie/{id}/")
    suspend fun movieByTmdb(
        @Path("id") id: Int,
        @Query("apikey") key: String
    ): MdbRatingResult?

    @GET("api/tmdb/show/{id}/")
    suspend fun showByTmdb(
        @Path("id") id: Int,
        @Query("apikey") key: String
    ): MdbRatingResult?

    @GET("api/")
    suspend fun searchByTmdb(
        @Query("tm") tmdbId: Int,
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
    private val client: OkHttpClient
) {
    companion object {
        private const val BASE = "https://mdblist.com/"
        const val PREF_API_KEY = "mdblist_api_key" // global
        const val PREF_ENABLED = "mdblist_enabled" // por perfil
        const val PREF_RATING_SOURCES = "mdblist_rating_sources" // por perfil, CSV
        // Lote 21 (v1.31.0): as fontes que o array `ratings` da API real
        // traz (ver MdbRatingResult) — as 4 novas entram ligadas por padrão
        // p/ quem ainda não personalizou as fontes (quem já salvou a lista
        // mantém a dele e liga as novas na tela de Trackers).
        val DEFAULT_SOURCES = setOf(
            "imdb", "tmdb", "trakt", "letterboxd", "tomatoes", "metacritic",
            "tomatoesaudience", "metacriticuser", "myanimelist", "mdb"
        )

        /** Fonte crua (minúscula, como vem no array) → nome de exibição. */
        val SOURCE_DISPLAY = mapOf(
            "imdb" to "IMDb",
            "tmdb" to "TMDB",
            "trakt" to "Trakt",
            "letterboxd" to "Letterboxd",
            "tomatoes" to "RT",
            "tomatoesaudience" to "RT Audiência",
            "popcorn" to "RT Audiência",
            "metacritic" to "Metacritic",
            "metacriticuser" to "Metacritic Usuários",
            "myanimelist" to "MyAnimeList",
            "mdb" to "MDBList",
            "rogerebert" to "RogerEbert"
        )

        /**
         * Fonte crua → chave do toggle que a liga/desliga (ver
         * [DEFAULT_SOURCES]). A audiência do RT segue o toggle do RT e o
         * Metacritic de usuários segue o do Metacritic — são a mesma
         * fonte, medida em outro público.
         */
        val SOURCE_GATE = mapOf(
            "tomatoesaudience" to "tomatoes",
            "popcorn" to "tomatoes",
            "metacriticuser" to "metacritic"
        )

        /**
         * Escala da nota crua de cada fonte de exibição (p/ normalizar p/
         * 0–100 no detalhe): imdb 0–10, letterboxd 0–5, rogerebert 0–4;
         * tomatoes/metacritic/trakt/tmdb/mdb 0–100. ATENÇÃO: o tmdb NO
         * ARRAY do MDBList é 0–100 (o Kometa lê como Integer) — NÃO é o
         * vote_average 0–10 do TMDB, então NÃO multiplica por 10.
         */
        fun sourceScale100(display: String): Double = when (display) {
            "IMDb", "MyAnimeList" -> 10.0
            "Letterboxd" -> 20.0
            "RogerEbert" -> 25.0
            else -> 1.0
        }
        // Título de teste p/ validar a chave (filme conhecido, sempre no
        // MDBList). Só o TÍTULO é fixo — a chave testada vem do usuário.
        private const val TEST_TITLE = "Dune"
        private const val TEST_YEAR = 2021
        /** 2º título do teste (Lote 20, v1.30.0): 'Dune' (2021) pode voltar
         * sem o bloco `ratings` mesmo com a chave válida — o teste tenta
         * 'Inception' (2010) antes de falhar. */
        private val TEST_TITLES = listOf(TEST_TITLE to TEST_YEAR, "Inception" to 2010)
        /** Teste POR ID (Lote 21, v1.31.0): determinístico, sem depender de
         * casamento de título — Dune (2021) tmdb 438631, Inception (2010)
         * tmdb 27205. */
        private val TEST_IDS = listOf(438631 to "Dune (2021)", 27205 to "Inception (2010)")
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

    suspend fun getListItems(listId: Int, limit: Int = 500, offset: Int = 0): Result<List<MdbListItem>> = runCatching {
        require(isConnected) { "Chave MDBList não configurada" }
        api.getListItems(listId, apiKey, limit, offset)
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
        // Lote 21 (v1.31.0): a API devolve `ratings` como ARRAY de
        // {"source", "value"} — o modelo antigo (objeto MdbRatings) quebrava
        // com "Expected BEGIN_ARRAY but was BEGIN_OBJECT" e o mapa vinha
        // vazio. Título não encontrado → ratings vazio → mapa vazio e a
        // seção some.
        val result = api.searchRatings(title, year, apiKey, type)
        ratingsMap(result)
    }

    // ── Lote 19 (v1.29.0): motivo do erro + fallback de título ─────────────

    /** Mapa fonte → nota (só as fontes habilitadas no perfil) p/ um resultado da API. */
    private fun ratingsMap(result: MdbRatingResult?): Map<String, Double?> {
        val entries = result?.ratings.orEmpty()
        if (entries.isEmpty()) return emptyMap()
        val enabled = getEnabledSources()
        return buildMap {
            for (entry in entries) {
                val key = entry.source.trim().lowercase()
                val display = SOURCE_DISPLAY[key] ?: continue
                val gate = SOURCE_GATE[key] ?: key
                if (gate !in enabled) continue
                val value = entry.value ?: continue
                putIfAbsent(display, value)
            }
        }
    }

    /**
     * Busca por id TMDB (filme ou série): caminho do Kometa
     * (`api/tmdb/movie|show/{id}/`) e, se ele recusar, a forma
     * `api/?tm={id}`. null = nenhum dos dois reconheceu o id (a API
     * respondeu False ou vazia). Exceção de rede propaga p/ o chamador.
     */
    private suspend fun lookupByTmdbId(tmdbId: Int, key: String, isMovie: Boolean): MdbRatingResult? {
        val byPath = runCatching {
            if (isMovie) api.movieByTmdb(tmdbId, key) else api.showByTmdb(tmdbId, key)
        }.getOrNull()
        if (byPath?.response != "False" && (byPath?.title != null || byPath?.score != null || !byPath?.ratings.isNullOrEmpty())) {
            return byPath
        }
        val byQuery = runCatching {
            api.searchByTmdb(tmdbId, key, if (isMovie) "movie" else "show")
        }.getOrNull()
        if (byQuery?.response != "False" && (byQuery?.title != null || byQuery?.score != null || !byQuery?.ratings.isNullOrEmpty())) {
            return byQuery
        }
        return byPath?.takeIf { it.response != "False" } ?: byQuery
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
     * Busca notas tentando primeiro o ID TMDB (Lote 21, v1.31.0 — o detalhe
     * já tem o tmdbId; buscar por título falha quando o título localizado
     * não casa) e, sem id ou sem resposta por id, o título localizado e
     * depois o original (Lote 19, v1.29.0): o TMDB devolve o título em
     * PT-BR ("Duna") e a busca `?s=` do MDBList nem sempre casa com ele.
     * Título achado mas sem nota nas fontes habilitadas NÃO tenta o outro
     * título — o problema não é o nome. Nunca inventa nota: sem notas, o
     * mapa volta vazio e o motivo vai em [MdbRatingLookup.apiError] ou é
     * dedutível de [MdbRatingLookup.matchedTitle]/[MdbRatingLookup.attemptedTitles].
     */
    suspend fun getRatingsWithFallback(
        title: String,
        originalTitle: String,
        year: Int?,
        isMovie: Boolean,
        tmdbId: Int = 0
    ): Result<MdbRatingLookup> {
        return try {
            require(isConnected) { "Chave MDBList não configurada" }
            val type = if (isMovie) "movie" else "show"
            val attempted = mutableListOf<String>()
            var lastApiError: String? = null
            var found: MdbRatingLookup? = null
            var matched: String? = null
            // (1) Por id TMDB — determinístico, sem casamento de título.
            if (tmdbId > 0) {
                attempted.add("tmdb:$tmdbId")
                val byId = lookupByTmdbId(tmdbId, apiKey, isMovie)
                if (byId?.response == "False") {
                    lastApiError = byId.error?.takeIf { it.isNotBlank() }
                } else if (byId != null && (!byId.ratings.isNullOrEmpty() || byId.title != null || byId.score != null)) {
                    matched = byId.title ?: title
                    val map = ratingsMap(byId)
                    if (map.isNotEmpty()) {
                        found = MdbRatingLookup(map, byId.title ?: title, matched, attempted.toList(), null)
                    }
                }
            }
            // (2) Por título, localizado e depois o original.
            if (found == null) {
                val candidates = listOf(title, originalTitle)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                for (candidate in candidates) {
                    attempted.add(candidate)
                    val result = api.searchRatings(candidate, year, apiKey, type)
                    if (result?.response == "False") {
                        lastApiError = result.error?.takeIf { it.isNotBlank() }
                        continue
                    }
                    if (!result?.ratings.isNullOrEmpty() || result?.title != null) {
                        matched = result?.title ?: candidate
                        val map = ratingsMap(result)
                        if (map.isNotEmpty()) {
                            found = MdbRatingLookup(map, result?.title ?: candidate, matched, attempted.toList(), null)
                        }
                        break
                    }
                    // ratings vazio e sem título = a API não reconheceu este título → tenta o próximo.
                }
            }
            Result.success(found ?: MdbRatingLookup(emptyMap(), null, matched, attempted.toList(), lastApiError))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(apiErrorMessage(e)))
        }
    }

    /**
     * Valida uma chave (a digitada, ainda não salva, ou a salva).
     *
     * Lote 21 (v1.31.0): o teste agora é POR ID TMDB — Dune (2021) tmdb
     * 438631 e Inception (2010) tmdb 27205 — determinístico, sem depender
     * de casamento de título. Chave inválida devolve HTTP 503 + "Invalid
     * API key or Rate Limiter Reached!" (ao vivo, 2026-11-17). A chave é
     * VÁLIDA quando a API devolve o título (ou o score) mesmo sem o bloco
     * `ratings`. Se o teste por id falhar sem provar nada, tenta os títulos
     * (comportamento do Lote 20) antes de desistir. Na falha, a mensagem
     * mostra o JSON cru devolvido pela API (primeiros ~200 caracteres) —
     * nunca mais falha "em silêncio" sem dizer o que a API devolveu. Nunca
     * embute chave: a chave testada vem do parâmetro, digitada pelo usuário.
     */
    suspend fun testApiKey(key: String): Result<String> {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalStateException("Cole uma chave antes de testar."))
        }
        return try {
            var lastNote: String? = null
            var rawJson: String? = null
            // (1) Por id TMDB.
            for ((tmdbId, label) in TEST_IDS) {
                val result = lookupByTmdbId(tmdbId, trimmed, true)
                if (result?.response == "False") {
                    val err = result.error.orEmpty()
                    if ("Invalid API key" in err) {
                        return Result.failure(IllegalStateException(translateApiError(err)))
                    }
                    lastNote = "“$label” (tmdb:$tmdbId): ${translateApiError(err)}"
                    continue
                }
                val hasTitle = !result?.title.isNullOrBlank()
                val hasScore = result?.score != null || result?.scoreAverage != null
                val hasRatings = !result?.ratings.isNullOrEmpty()
                if (hasTitle || hasScore || hasRatings) {
                    val shown = if (hasTitle && result?.title != label.substringBefore(" (")) " (“${result?.title}”)" else ""
                    return Result.success("Chave válida — o MDBList respondeu com “$label”$shown.")
                }
                lastNote = "“$label” (tmdb:$tmdbId): respondeu sem título nem notas"
            }
            // (2) Por título (fallback do Lote 20).
            for ((title, year) in TEST_TITLES) {
                val result = api.searchRatings(title, year, trimmed, "movie")
                if (result?.response == "False") {
                    val err = result.error.orEmpty()
                    if ("Invalid API key" in err) {
                        return Result.failure(IllegalStateException(translateApiError(err)))
                    }
                    lastNote = "“$title ($year)”: ${translateApiError(err)}"
                    continue
                }
                val hasTitle = !result?.title.isNullOrBlank()
                val hasScore = result?.score != null || result?.scoreAverage != null
                if (hasTitle || hasScore || !result?.ratings.isNullOrEmpty()) {
                    val shown = if (hasTitle && result?.title != title) " (“${result?.title}”)" else ""
                    return Result.success("Chave válida — o MDBList respondeu com “$title ($year)”$shown.")
                }
                lastNote = if (result == null) {
                    "“$title ($year)”: resposta vazia"
                } else {
                    "“$title ($year)”: respondeu sem título nem notas"
                }
            }
            // (3) Última resposta crua, p/ a mensagem dizer o que a API devolveu.
            rawJson = fetchRawTestBody(trimmed)
            val rawNote = rawJson?.let { " Resposta da API: ${it.take(200)}" }.orEmpty()
            Result.failure(
                IllegalStateException(
                    "O MDBList respondeu, mas sem título nem notas nos títulos de teste. " +
                        "Última resposta: ${lastNote ?: "vazia"}. A chave pode estar válida — abra um filme e veja se as notas aparecem no detalhe.$rawNote",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(apiErrorMessage(e)))
        }
    }

    /**
     * Corpo cru da resposta do teste por id (só p/ a mensagem de falha —
     * best-effort, nunca quebra o teste). A chave vai na URL, como em
     * qualquer chamada da API; nada é logado nem guardado.
     */
    private fun fetchRawTestBody(key: String): String? = runCatching {
        val url = "${BASE}api/tmdb/movie/438631/?apikey=$key"
        val req = Request.Builder().url(url).header("User-Agent", "AOTV (Android)").build()
        client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()?.trim()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()
}
