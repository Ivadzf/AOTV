// ─── data/remote/AniListService.kt (v1.17.0) ────────────────────────────────
// AniList: tracking de animes via GraphQL manual (OkHttp + Gson, sem Apollo).
// Adaptação do arquivo da outra IA: mesma estrutura, com prefs tipadas
// (padrão Trakt/Simkl). O client ID é DO APARELHO (prefs.anilistClientId);
// o token e o toggle são POR PERFIL. Login por código: o deep link
// aotv://anilist/callback (Manifest) é de outra frente — aqui fica a troca
// do código por token via POST ([exchangeCodeForToken]) e o [saveToken] que
// o callback usa. Erro HTTP ou corpo que não é JSON NUNCA conta como
// conexão concluída: o token só é salvo com resposta 2xx e token não vazio.

package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════════════════════
// Modelos
// ══════════════════════════════════════════════════════════════════════════════

data class AniListMedia(
    val id: Int,
    val type: String?, // "ANIME" | "MANGA"
    val episodes: Int?,
    val duration: Int?,
    val title: AniListTitle
)

data class AniListTitle(
    val romaji: String?,
    val english: String?,
    val native: String?
) {
    fun best(): String = english ?: romaji ?: native ?: ""
}

data class AniListListEntry(
    val id: Int,
    val status: String, // "CURRENT" | "COMPLETED" | ...
    val score: Double,
    val progress: Int
)

// ══════════════════════════════════════════════════════════════════════════════
// Service
// ══════════════════════════════════════════════════════════════════════════════

@Singleton
class AniListService @Inject constructor(
    private val prefs: PlatformPrefs,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val API_URL = "https://graphql.anilist.co"
        private const val TOKEN_URL = "https://anilist.co/api/v2/oauth/token"
        private const val OAUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
        const val PREF_CLIENT_ID = "anilist_client_id" // global
        const val PREF_ACCESS_TOKEN = "anilist_access_token" // por perfil
        const val PREF_ENABLED = "anilist_enabled" // por perfil
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()

    val clientId: String get() = prefs.anilistClientId
    val accessToken: String get() = prefs.anilistAccessToken
    val isConnected: Boolean get() = accessToken.isNotBlank() && clientId.isNotBlank()
    val isEnabled: Boolean get() = prefs.anilistEnabled && isConnected

    fun setClientId(id: String) {
        prefs.anilistClientId = id
    }

    fun setEnabled(on: Boolean) {
        prefs.anilistEnabled = on
    }

    /**
     * URL para abrir no navegador ou exibir como QR.
     * AniList usa Authorization Code com redirect.
     * redirect_uri deve estar registrado no app AniList do usuário.
     */
    fun buildOAuthUrl(redirectUri: String = "aotv://anilist/callback"): String {
        require(clientId.isNotBlank()) { "Client ID AniList não configurado" }
        val enc = java.net.URLEncoder.encode(redirectUri, "UTF-8")
        return "$OAUTH_URL?client_id=$clientId&redirect_uri=$enc&response_type=code"
    }

    /**
     * Troca o código do callback por access token (POST form no endpoint de
     * token). Sucesso: salva o token e o devolve. Falha: o Result traz a
     * mensagem real do AniList (campo "message"/"error_description" do JSON,
     * ou o código HTTP) e NADA é salvo — sem token, segue desconectado.
     */
    suspend fun exchangeCodeForToken(
        code: String,
        redirectUri: String = "aotv://anilist/callback"
    ): Result<String> = runCatching {
        require(clientId.isNotBlank()) { "Client ID AniList não configurado" }
        require(code.isNotBlank()) { "Código AniList vazio" }
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("redirect_uri", redirectUri)
            .add("code", code)
            .build()
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        val (status, json) = withContext(Dispatchers.IO) {
            okHttpClient.newCall(req).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }
        if (status !in 200..299) throw Exception(errorMessage(json, "AniList $status"))
        val token = runCatching {
            JsonParser.parseString(json).asJsonObject.get("access_token")?.asString.orEmpty()
        }.getOrDefault("")
        if (token.isBlank()) throw Exception("AniList: token ausente na resposta")
        prefs.anilistAccessToken = token
        token
    }

    /** Salva o access token que veio do deep link após OAuth. */
    fun saveToken(token: String) {
        prefs.anilistAccessToken = token
    }

    fun disconnect() {
        prefs.anilistAccessToken = ""
        prefs.anilistEnabled = false
    }

    /** Mensagem real do erro do AniList, quando o corpo é JSON; senão o fallback. */
    private fun errorMessage(json: String, fallback: String): String = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        o.get("message")?.asString
            ?: o.get("error_description")?.asString
            ?: o.get("error")?.asString
            ?: fallback
    }.getOrDefault(fallback)

    // ── GraphQL helper ────────────────────────────────────────────────────────

    private suspend fun graphqlRaw(query: String, variables: Map<String, Any?>): String {
        require(isConnected) { "AniList não conectado" }
        val body = gson.toJson(mapOf("query" to query, "variables" to variables))
            .toRequestBody(JSON)
        val req = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        val (status, json) = withContext(Dispatchers.IO) {
            okHttpClient.newCall(req).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }
        if (status !in 200..299) throw Exception(errorMessage(json, "AniList $status"))
        // Extrai erros antes de retornar
        val root = JsonParser.parseString(json).asJsonObject
        root.getAsJsonArray("errors")?.let { errs ->
            if (errs.size() > 0) {
                val first = errs[0].asJsonObject
                val msg = first.get("message")?.asString ?: "Erro desconhecido"
                val code = first.get("status")?.asInt ?: status
                throw Exception("AniList $code: $msg")
            }
        }
        return json
    }

    // ── Busca ─────────────────────────────────────────────────────────────────

    /** Busca anime pelo título. Retorna null se não encontrar. */
    suspend fun searchAnime(title: String): Result<AniListMedia?> = runCatching {
        val q = """
            query(${'$'}search:String){
              Media(search:${'$'}search,type:ANIME){
                id type episodes duration
                title{romaji english native}
              }
            }
        """.trimIndent()
        val json = graphqlRaw(q, mapOf("search" to title))
        val dataObj = JsonParser.parseString(json)
            .asJsonObject.getAsJsonObject("data")
            ?.getAsJsonObject("Media") ?: return@runCatching null
        gson.fromJson(dataObj, AniListMedia::class.java)
    }

    // ── Tracking ──────────────────────────────────────────────────────────────

    /**
     * Atualiza o progresso de um anime (status CURRENT).
     * [episodesWatched] = número do episódio recém-assistido.
     */
    suspend fun updateProgress(mediaId: Int, episodesWatched: Int): Result<AniListListEntry> =
        runCatching {
            val mutation = """
                mutation(${'$'}mediaId:Int,${'$'}status:MediaListStatus,${'$'}progress:Int){
                  SaveMediaListEntry(mediaId:${'$'}mediaId,status:${'$'}status,progress:${'$'}progress){
                    id status score progress
                  }
                }
            """.trimIndent()
            val json = graphqlRaw(mutation, mapOf(
                "mediaId" to mediaId,
                "status" to "CURRENT",
                "progress" to episodesWatched
            ))
            val entryObj = JsonParser.parseString(json)
                .asJsonObject.getAsJsonObject("data")
                ?.getAsJsonObject("SaveMediaListEntry")
                ?: throw Exception("Entry ausente na resposta AniList")
            gson.fromJson(entryObj, AniListListEntry::class.java)
        }

    /**
     * Marca anime como COMPLETED e envia nota (escala 0–100 → AniList 0–10).
     * [score] em escala 0–10 (double), null = sem nota.
     */
    suspend fun markCompleted(
        mediaId: Int,
        totalEpisodes: Int,
        score: Double? = null
    ): Result<AniListListEntry> = runCatching {
        val vars: MutableMap<String, Any?> = mutableMapOf(
            "mediaId" to mediaId,
            "status" to "COMPLETED",
            "progress" to totalEpisodes
        )
        score?.let { vars["score"] = it }
        val scoreParam = if (score != null) ",\$score:Float" else ""
        val scoreField = if (score != null) ",score:\$score" else ""
        val mutation = """
            mutation(${'$'}mediaId:Int,${'$'}status:MediaListStatus,${'$'}progress:Int$scoreParam){
              SaveMediaListEntry(mediaId:${'$'}mediaId,status:${'$'}status,progress:${'$'}progress$scoreField){
                id status score progress
              }
            }
        """.trimIndent()
        val json = graphqlRaw(mutation, vars)
        val entryObj = JsonParser.parseString(json)
            .asJsonObject.getAsJsonObject("data")
            ?.getAsJsonObject("SaveMediaListEntry")
            ?: throw Exception("Entry ausente na resposta AniList")
        gson.fromJson(entryObj, AniListListEntry::class.java)
    }
}
