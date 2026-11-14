package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simkl (v1.13.0, Frente B — retentativa 1x da Fase D, só Simkl).
 *
 * Fluxo de código de dispositivo (PIN) da API AUTH V1 do Simkl, conferido na
 * documentação oficial (https://api.simkl.org/api-reference/pin,
 * /api-reference/simkl/get-pin e /api-reference/simkl/check-pin, lidas em
 * 2026-11-14):
 *
 *  - Pedir o PIN é GET (não POST): `GET /oauth/pin?client_id=…&app-name=aotv
 *    &app-version=1.13.0` → `{ result: "OK", user_code, verification_uri
 *    (ou verification_url), expires_in: 900, interval: 5 }`. O `user_code`
 *    tem 5 caracteres e o `device_code` é um placeholder literal, ignorado.
 *  - Polling é GET: `GET /oauth/pin/{user_code}?client_id=…&app-name=…
 *    &app-version=…` → `{ result: "OK", access_token }` quando o usuário
 *    autoriza, ou `{ result: "KO", message: "Authorization pending" }`
 *    enquanto espera. Um `user_code` desconhecido devolve um corpo de init
 *    novo (com `user_code`) — tratado como código expirado.
 *  - Toda chamada leva `client_id` (query) + `app-name` + `app-version` +
 *    `User-Agent: aotv/1.13.0`. Sem `client_secret` e sem redirect URI.
 *  - O token do PIN é de longa duração (o Simkl não emite refresh token
 *    nesse fluxo): guardamos a validade como 5 anos a partir da emissão
 *    (157680000 s, valor documentado pela comunidade a partir da API);
 *    o token vale até o usuário revogar o app no site.
 *
 * AUTH V2 EXISTE (https://api.simkl.org/api-reference/auth): o fluxo de
 * dispositivo V2 é `POST /oauth2/device` + PKCE, com access token de 7 dias
 * e refresh de 180 dias. O V1 do PIN continua documentado e atualizado
 * (2026-11-14) e é o fluxo da foto de referência (simkl.com/pin + código
 * de 5 caracteres), por isso o V1 foi implementado aqui. Se o Simkl
 * aposentar o V1, migrar p/ o V2 (device + PKCE + refresh) numa versão
 * futura — os pontos de chamada (scrobble/watchlist) não mudam.
 *
 * Sessão POR PERFIL (prefs.simklSession / prefs.simklClientId, já
 * existiam — reutilizadas, nenhuma pref nova). Client ID só via campo
 * configurável na tela, vazio por padrão; NUNCA embutido no código.
 * Sem client ID configurado, nada do Simkl é chamado. Sem token, idem.
 * Independente do Trakt: não lê nem depende da sessão do Trakt.
 *
 * Validação (regra da v1.0.19): erro HTTP ou corpo que não é JSON
 * parseável (ex.: página HTML) NUNCA conta como conexão concluída —
 * [requireJson] rejeita antes de qualquer parse e [call] só aceita 2xx.
 */
@Singleton
class SimklService @Inject constructor(
    client: OkHttpClient,
    private val prefs: PlatformPrefs,
) {
    private val http = client.newBuilder().cache(null).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Client ID configurado pelo usuário neste perfil ("" = Simkl desligado). */
    val clientId: String get() = prefs.simklClientId

    /** Simkl usável neste perfil: tem client ID. O token é verificado à parte. */
    val configured: Boolean get() = clientId.isNotBlank()

    private data class Session(val access: String, val expiresAt: Long, val user: String)

    private fun session(): Session? = runCatching {
        val o = JSONObject(prefs.simklSession)
        Session(o.getString("a"), o.optLong("e"), o.optString("u"))
    }.getOrNull()?.takeIf { it.access.isNotBlank() }

    private fun saveSession(s: Session?) {
        prefs.simklSession = s?.let {
            JSONObject().put("a", it.access).put("e", it.expiresAt).put("u", it.user).toString()
        } ?: ""
    }

    /** Nome da conta conectada no perfil ativo ("" quando o Simkl não deu o nome). null sem login. */
    fun user(): String? = session()?.user

    /** Expiração do token guardado (ms desde epoch), 0 = sem token. */
    fun expiresAt(): Long = session()?.expiresAt ?: 0L

    /** Conectado neste perfil (tem token; a validade longa é guardada na sessão). */
    fun isConnected(): Boolean = session() != null

    fun logout() = saveSession(null)

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private class Resp(val code: Int, val body: String)

    /** Corpo tem que ser JSON (objeto ou array). Página HTML / erro em texto não passa. */
    private fun requireJson(body: String): JSONObject? {
        val t = body.trim()
        if (!t.startsWith("{")) return null
        return runCatching { JSONObject(t) }.getOrNull()
    }

    private fun url(path: String, extra: Map<String, String> = emptyMap()): String {
        val b = (API + path).toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("app-name", APP_NAME)
            .addQueryParameter("app-version", APP_VERSION)
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private suspend fun raw(method: String, url: String, body: JSONObject?, token: String?): Resp =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", "$APP_NAME/$APP_VERSION")
            if (token != null) rb.header("Authorization", "Bearer $token")
            rb.method(method, body?.toString()?.toRequestBody(JSON))
            http.newCall(rb.build()).execute().use { Resp(it.code, it.body?.string().orEmpty()) }
        }

    /** Chamada autenticada. 401 desconecta. Só aceita 2xx com JSON parseável. */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val s = session() ?: error("simkl sem sessão")
        val r = raw(method, url(path), body, s.access)
        if (r.code == 401) { saveSession(null); error("simkl 401") }
        if (r.code !in 200..299) error("simkl ${r.code}")
        return requireJson(r.body) ?: error("simkl resposta inválida")
    }

    // ── Login por PIN ────────────────────────────────────────────────────────

    data class DeviceCode(
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Int,
        val expiresAt: Long,
    ) {
        /** URL que o usuário abre no celular (com o código já embutido, como na foto). */
        val pinUrl: String get() = "$verificationUrl?user_code=$userCode"
    }

    /**
     * Pede o PIN. null quando sem client ID ou quando o Simkl não respondeu
     * com um JSON válido (erro HTTP, HTML ou sem `user_code`).
     */
    suspend fun startLogin(): DeviceCode? {
        if (!configured) return null
        val r = runCatching { raw("GET", url("/oauth/pin"), null, null) }.getOrNull() ?: return null
        if (r.code != 200) return null
        val d = requireJson(r.body) ?: return null
        val code = d.optString("user_code").trim()
        if (code.isBlank()) return null
        // `verification_uri` é o nome atual; `verification_url` é o alias antigo.
        val base = d.optString("verification_uri").ifBlank { d.optString("verification_url") }
            .trim().trimEnd('/').ifBlank { PIN_SITE }
        return DeviceCode(
            code, base,
            maxOf(5, d.optInt("interval", 5)),
            System.currentTimeMillis() + d.optLong("expires_in", 900) * 1000,
        )
    }

    /**
     * Espera a pessoa confirmar no outro aparelho. Devolve o nome da conta,
     * ou null se expirar, se o código for desconhecido ou se for negado.
     * Cancelar a coroutine cancela a espera.
     */
    suspend fun waitLogin(c: DeviceCode): String? {
        var every = c.intervalSec * 1000L
        while (System.currentTimeMillis() < c.expiresAt) {
            delay(every)
            val r = runCatching {
                raw("GET", url("/oauth/pin/${c.userCode}"), null, null)
            }.getOrNull() ?: continue
            if (r.code == 429) { every += 1000; continue }
            if (r.code !in 200..299) return null
            val d = requireJson(r.body) ?: return null
            // Código desconhecido/apagado: o Simkl devolve um init novo.
            if (d.has("user_code")) return null
            when (d.optString("result")) {
                "OK" -> {
                    val token = d.optString("access_token")
                    if (token.isBlank()) return null
                    // Token de longa duração, sem refresh: validade guardada
                    // como 5 anos (157680000 s); vale até revogarem no site.
                    saveSession(Session(token, System.currentTimeMillis() + TOKEN_VALID_MS, ""))
                    val name = runCatching {
                        call("GET", "/users/settings").optJSONObject("user")
                            ?.let { it.optString("name").ifBlank { it.optString("login").ifBlank { it.optString("username") } } }
                    }.getOrNull().orEmpty()
                    session()?.let { saveSession(it.copy(user = name)) }
                    return name
                }
                "KO" -> Unit // ainda esperando a pessoa
                else -> return null
            }
        }
        return null
    }

    // ── Scrobble e sincronia ─────────────────────────────────────────────────

    private fun mediaBody(tmdbId: Int, type: String, season: Int, episode: Int): JSONObject {
        val ids = JSONObject().put("ids", JSONObject().put("tmdb", tmdbId))
        val body = JSONObject()
        if (type == "SERIES" && season > 0 && episode > 0) {
            body.put("show", ids).put("episode", JSONObject().put("season", season).put("number", episode))
        } else body.put("movie", ids)
        return body
    }

    /**
     * Scrobble acoplado ao do Trakt (mesmas ações start/pause/stop).
     * [action] é start, pause ou stop. Não faz nada sem client ID, sem
     * token ou sem tmdbId. Roda em background; falha de rede não derruba
     * o player. Ao parar com ≥90%, marca como assistido em /sync/history
     * (o /scrobble/stop do Simkl já marca a partir de 80%, então o
     * /sync/history aqui é o registro explícito pedido p/ o AOTV).
     */
    fun scrobble(action: String, tmdbId: Int, type: String, season: Int, episode: Int, progress: Double) {
        if (!configured || session() == null || tmdbId <= 0) return
        if (action !in listOf("start", "pause", "stop")) return
        val p = progress.coerceIn(0.0, 100.0)
        scope.launch {
            runCatching {
                call("POST", "/scrobble/$action", mediaBody(tmdbId, type, season, episode).put("progress", p))
                if (action == "stop" && p >= 90.0) {
                    call("POST", "/sync/history", mediaBody(tmdbId, type, season, episode))
                }
            }
        }
    }

    /**
     * Minha Lista → Simkl. Adicionar envia `POST /sync/add-to-list` com
     * status `plantowatch` (filmes não aceitam `watching`/`hold` no Simkl).
     * REMOVER NÃO ENVIA NADA ao Simkl de propósito: a única operação de
     * remoção do Simkl (`POST /sync/history/remove`) apagaria o histórico
     * assistido — tirar da Minha Lista nunca pode apagar o que foi visto.
     * Sem client ID/token, no-op.
     */
    fun watchlistChanged(tmdbId: Int, type: String, added: Boolean) {
        if (!configured || session() == null || tmdbId <= 0) return
        if (!added) return // ver o comentário acima: remoção não toca no Simkl
        val one = JSONObject().put("ids", JSONObject().put("tmdb", tmdbId)).put("status", "plantowatch")
        val body = JSONObject().put(if (type == "MOVIE") "movies" else "shows", org.json.JSONArray().put(one))
        scope.launch { runCatching { call("POST", "/sync/add-to-list", body) } }
    }

    companion object {
        const val API = "https://api.simkl.com"

        /** Página do site onde o usuário digita o PIN (sem o código). */
        const val PIN_SITE = "https://simkl.com/pin"

        /**
         * Página EXATA de cadastro de apps do Simkl (onde o Davi cria o app
         * p/ obter o client ID): o formulário de novo app. A lista dos apps
         * já criados fica em https://simkl.com/settings/developer/ (sem o
         * /new/). No formulário, escolher o tipo "TV, devices & command
         * line" (só client ID, sem client_secret e sem redirect URI — é o
         * tipo certo p/ o fluxo de PIN) e copiar a "API Key" (= client_id).
         * Fonte: doc oficial (api.simkl.org/api-reference/auth, link
         * "Developer settings") + guias de setup da comunidade.
         */
        const val DEVELOPER_URL = "https://simkl.com/settings/developer/new/"
        const val DEVELOPER_LIST_URL = "https://simkl.com/settings/developer/"

        const val APP_NAME = "aotv"
        const val APP_VERSION = "1.13.0"

        /** Validade guardada do token do PIN: 5 anos (157680000 s). */
        const val TOKEN_VALID_MS = 157_680_000L * 1000L

        val JSON = "application/json".toMediaType()
    }
}
