package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext

/**
 * Simkl (v1.13.1 — device flow AUTH V2, PKCE + refresh).
 *
 * O fluxo V1 (GET /oauth/pin, código de 5 caracteres) ESTÁ MORTO para
 * cadastros novos: `GET /oauth/pin` responde 400 `unauthorized_client` —
 * "This client_id is an OAuth 2.0 app: use POST /oauth2/device instead of
 * /oauth/pin" (conferido ao vivo em 2026-11-14, com o Client ID completo
 * do usuário). O fluxo abaixo é o V2, conferido na documentação oficial
 * (https://api.simkl.org/api-reference/auth,
 * /api-reference/oauth2-device e /api-reference/oauth2-tokens, lidas em
 * 2026-11-14) e validado ao vivo da VM:
 *
 *  - Pedir o código é POST (parâmetros na query):
 *    `POST /oauth2/device?client_id=…&code_challenge=…&code_challenge_method=S256
 *    &scope=media:read media:write` → 200 `{ device_code, user_code
 *    ("XXXX-YYYY", 8 caracteres), verification_uri (https://simkl.com/pin),
 *    verification_uri_complete (https://simkl.com/pin?user_code=…),
 *    expires_in: 900, interval: 5 }`.
 *  - PKCE (obrigatório nos dois fluxos V2): `code_verifier` de 43
 *    caracteres URL-safe aleatórios (SecureRandom, alfabeto
 *    A-Za-z0-9-._~) e `code_challenge = BASE64URL(SHA-256(verifier))`
 *    sem padding. O verifier vive SÓ em memória durante o fluxo
 *    (nunca em prefs — é de uso único); é limpo ao fim do [waitLogin]
 *    e ao iniciar um novo [startLogin]. O `device_code` também fica só
 *    em memória, pelo mesmo motivo.
 *  - Polling é POST form-urlencoded em `/oauth2/token`:
 *    `grant_type=urn:ietf:params:oauth:grant-type:device_code
 *    &device_code=…&client_id=…&code_verifier=…`, a cada `interval`
 *    segundos até `expires_in`. Erros do polling (corpo JSON com
 *    `error`): 400 `authorization_pending` (continuar no mesmo
 *    intervalo), 400 `slow_down` (somar 5 s ao intervalo e esperar),
 *    400 `expired_token` (código morto, recomeçar), 401
 *    `invalid_client` (client ID desconhecido ou app V1 — parar, retry
 *    não ajuda). NÃO existe `access_denied` documentado no device flow
 *    V2 (quem nega só deixa o código pendente até expirar) — ele é
 *    tratado defensivamente se aparecer. O fim do loop é o prazo
 *    (`expires_in`).
 *  - Sucesso do polling (200): `{ access_token, refresh_token,
 *    expires_in }`. Access token de 7 dias; refresh token de 180 dias,
 *    deslizante e NÃO-rotativo (cada refresh renova a janela de 180
 *    dias; o refresh token continua o mesmo, salvo se a resposta
 *    trouxer um novo). Refresh é POST form em `/oauth2/token` com
 *    `grant_type=refresh_token&refresh_token=…&client_id=…`, proativo
 *    (~1 dia antes de expirar — aqui: margem de 60 s antes de cada
 *    chamada, que cobre o uso real do app) ou no 401. No 401, dá
 *    refresh e tenta a chamada UMA vez; se o refresh falhar, a sessão
 *    é limpa e a chamada dá erro.
 *  - Toda chamada de API leva `client_id` (query) + `app-name` +
 *    `app-version` + `User-Agent: aotv/1.13.1` + header `simkl-api-key`
 *    com o client ID. Sem `client_secret` e sem redirect URI.
 *
 * Sessão POR PERFIL (prefs.simklSession / prefs.simklClientId, já
 * existiam — reutilizadas, nenhuma pref nova): JSON com "a"
 * (access_token), "e" (expiresAt em ms), "u" (nome do usuário) e "r"
 * (refresh_token). Sessão antiga sem "r" continua lendo (refresh null).
 * Client ID só via campo configurável na tela, vazio por padrão; NUNCA
 * embutido no código. Sem client ID configurado, nada do Simkl é
 * chamado. Sem token, idem. Independente do Trakt: não lê nem depende
 * da sessão do Trakt.
 *
 * Validação (regra da v1.0.19): erro HTTP ou corpo que não é JSON
 * parseável (ex.: página HTML) NUNCA conta como conexão concluída —
 * [requireJson] rejeita antes de qualquer parse e [call] só aceita 2xx.
 *
 * Follow-up (não implementado aqui): revogar a sessão no site
 * (`POST /oauth2/revoke`) — hoje o [logout] só limpa a sessão local.
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

    /**
     * PKCE + device_code do fluxo em andamento — SÓ em memória, nunca em
     * prefs. Limpos ao fim do [waitLogin] e ao iniciar um novo [startLogin].
     */
    private var codeVerifier: String? = null
    private var pendingDeviceCode: String? = null

    private data class Session(
        val access: String,
        val expiresAt: Long,
        val user: String,
        val refresh: String?,
    )

    private fun session(): Session? = runCatching {
        val o = JSONObject(prefs.simklSession)
        Session(
            o.getString("a"),
            o.optLong("e"),
            o.optString("u"),
            o.optString("r").takeIf { it.isNotBlank() },
        )
    }.getOrNull()?.takeIf { it.access.isNotBlank() }

    private fun saveSession(s: Session?) {
        prefs.simklSession = s?.let {
            JSONObject().put("a", it.access).put("e", it.expiresAt).put("u", it.user)
                .apply { if (it.refresh != null) put("r", it.refresh) }.toString()
        } ?: ""
    }

    /** Nome da conta conectada no perfil ativo ("" quando o Simkl não deu o nome). null sem login. */
    fun user(): String? = session()?.user

    /** Expiração do token guardado (ms desde epoch), 0 = sem token. */
    fun expiresAt(): Long = session()?.expiresAt ?: 0L

    /** Conectado neste perfil (tem token; a validade de 7 dias é guardada na sessão). */
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

    private suspend fun raw(method: String, url: String, body: RequestBody?, token: String?): Resp =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
                .header("User-Agent", "$APP_NAME/$APP_VERSION")
            // Sem Content-Type fixo aqui: cada corpo declara o seu
            // (JSON via toRequestBody(JSON), form via FormBody).
            if (token != null) {
                rb.header("Authorization", "Bearer $token")
                rb.header("simkl-api-key", clientId)
            }
            rb.method(method, body)
            http.newCall(rb.build()).execute().use { Resp(it.code, it.body?.string().orEmpty()) }
        }

    /**
     * Renova o access token com o refresh token guardado. Sucesso: salva
     * o novo access token (e o novo refresh token, se a resposta trouxer
     * um; senão mantém o antigo) e devolve true. Falha com resposta do
     * servidor (ex.: `invalid_grant`): limpa a sessão e devolve false.
     * Falha de rede (sem resposta): não limpa nada, devolve false.
     */
    private suspend fun refreshSession(): Boolean {
        val s = session() ?: return false
        val refresh = s.refresh ?: return false
        if (!configured) return false
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        val r = try {
            raw("POST", "$API/oauth2/token", form, null)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return false
        }
        if (r.code !in 200..299) {
            saveSession(null)
            return false
        }
        val d = requireJson(r.body) ?: run { saveSession(null); return false }
        val token = d.optString("access_token")
        if (token.isBlank()) {
            saveSession(null)
            return false
        }
        val newRefresh = d.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
        val expiresIn = d.optLong("expires_in", 7L * 24L * 3600L)
        saveSession(
            Session(token, System.currentTimeMillis() + expiresIn * 1000, s.user, newRefresh),
        )
        return true
    }

    /**
     * Chamada autenticada. Antes de cada chamada, se o token estiver
     * expirado (com 60 s de margem) e houver refresh token, dá refresh
     * primeiro. No 401: dá refresh UMA vez e repete a chamada; se o
     * refresh falhar, limpa a sessão e dá erro. Só aceita 2xx com JSON
     * parseável.
     */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        var s = session() ?: error("simkl sem sessão")
        if (s.refresh != null && s.expiresAt <= System.currentTimeMillis() + 60_000L) {
            if (!refreshSession()) error("simkl sessão expirada")
            s = session() ?: error("simkl sem sessão")
        }
        var r = raw(method, url(path), body?.toString()?.toRequestBody(JSON), s.access)
        if (r.code == 401) {
            if (refreshSession()) {
                s = session() ?: error("simkl sem sessão")
                r = raw(method, url(path), body?.toString()?.toRequestBody(JSON), s.access)
            }
            if (r.code == 401) {
                saveSession(null)
                error("simkl 401")
            }
        }
        if (r.code !in 200..299) error("simkl ${r.code}")
        return requireJson(r.body) ?: error("simkl resposta inválida")
    }

    // ── Login por código de dispositivo (V2 + PKCE) ─────────────────────────

    data class DeviceCode(
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Int,
        val expiresAt: Long,
    ) {
        /** URL completa que o usuário abre no celular (código já embutido). */
        val pinUrl: String get() = verificationUrl
    }

    sealed interface LoginResult {
        data class Started(val code: DeviceCode) : LoginResult
        data class Failed(val message: String) : LoginResult
    }

    sealed interface LoginOutcome {
        data class Connected(val name: String) : LoginOutcome
        object Expired : LoginOutcome
        object Denied : LoginOutcome
        data class Failed(val message: String) : LoginOutcome
    }

    private fun newCodeVerifier(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = SecureRandom()
        return CharArray(43) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Pede o código de dispositivo V2. Devolve [LoginResult.Started] com
     * o código p/ o usuário confirmar em simkl.com/pin, ou
     * [LoginResult.Failed] com a mensagem real do erro (nunca genérica
     * quando o Simkl respondeu).
     */
    suspend fun startLogin(): LoginResult {
        if (!configured) return LoginResult.Failed(
            "Configure o Client ID do Simkl em Configurações → Trackers.",
        )
        // Novo fluxo: o verifier e o device_code anteriores não valem mais.
        codeVerifier = null
        pendingDeviceCode = null
        val verifier = newCodeVerifier()
        val deviceUrl = (API + "/oauth2/device").toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("code_challenge", codeChallenge(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("scope", "media:read media:write")
            .build().toString()
        val r = try {
            raw("POST", deviceUrl, FormBody.Builder().build(), null)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return LoginResult.Failed(
                "Sem conexão com o Simkl — confira a internet e tente de novo.",
            )
        }
        if (r.code == 401) return LoginResult.Failed(
            "Client ID não reconhecido pelo Simkl (401)",
        )
        if (r.code == 400) return LoginResult.Failed(
            "Simkl recusou o Client ID (400)",
        )
        if (r.code !in 200..299) return LoginResult.Failed(
            "Simkl respondeu com erro HTTP ${r.code}",
        )
        val d = requireJson(r.body) ?: return LoginResult.Failed(
            "O Simkl respondeu sem o código de dispositivo.",
        )
        val deviceCode = d.optString("device_code").trim()
        val code = d.optString("user_code").trim()
        if (deviceCode.isBlank() || code.isBlank()) return LoginResult.Failed(
            "O Simkl respondeu sem o código de dispositivo.",
        )
        // `verification_uri_complete` já traz o código embutido; cai p/
        // `verification_uri` (sem código) só se o campo completo faltar.
        val complete = d.optString("verification_uri_complete").trim()
        val base = d.optString("verification_uri").trim()
        val verificationUrl = complete.ifBlank { base }.ifBlank { PIN_SITE }
        codeVerifier = verifier
        pendingDeviceCode = deviceCode
        return LoginResult.Started(
            DeviceCode(
                code, verificationUrl,
                maxOf(5, d.optInt("interval", 5)),
                System.currentTimeMillis() + d.optLong("expires_in", 900) * 1000,
            ),
        )
    }

    /**
     * Espera a pessoa confirmar no outro aparelho. Devolve
     * [LoginOutcome.Connected] com o nome da conta, [LoginOutcome.Expired]
     * se o código expirar, [LoginOutcome.Denied] se for negado, ou
     * [LoginOutcome.Failed] com a mensagem real do erro. Cancelar a
     * coroutine cancela a espera (CancellationException propaga — nada
     * aqui a engole).
     */
    suspend fun waitLogin(c: DeviceCode): LoginOutcome {
        val verifier = codeVerifier
        val deviceCode = pendingDeviceCode
        if (verifier.isNullOrBlank() || deviceCode.isNullOrBlank()) {
            return LoginOutcome.Failed(
                "Código de verificação expirado — peça um novo código.",
            )
        }
        try {
            var every = c.intervalSec * 1000L
            while (System.currentTimeMillis() < c.expiresAt) {
                currentCoroutineContext().ensureActive()
                delay(every)
                currentCoroutineContext().ensureActive()
                val form = FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", deviceCode)
                    .add("client_id", clientId)
                    .add("code_verifier", verifier)
                    .build()
                val r = try {
                    raw("POST", "$API/oauth2/token", form, null)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    continue // sem rede: tenta de novo no próximo intervalo
                }
                if (r.code == 200) {
                    val d = requireJson(r.body) ?: return LoginOutcome.Failed(
                        "O Simkl respondeu sem o token de acesso.",
                    )
                    val token = d.optString("access_token")
                    if (token.isBlank()) {
                        return LoginOutcome.Failed(
                            "O Simkl respondeu sem o token de acesso.",
                        )
                    }
                    val refresh = d.optString("refresh_token").takeIf { it.isNotBlank() }
                    val expiresIn = d.optLong("expires_in", 7L * 24L * 3600L)
                    saveSession(
                        Session(
                            token,
                            System.currentTimeMillis() + expiresIn * 1000,
                            "",
                            refresh,
                        ),
                    )
                    val name = runCatching {
                        call("GET", "/users/settings").optJSONObject("user")
                            ?.let { it.optString("name").ifBlank { it.optString("login").ifBlank { it.optString("username") } } }
                    }.getOrNull().orEmpty()
                    currentCoroutineContext().ensureActive()
                    session()?.let { saveSession(it.copy(user = name)) }
                    return LoginOutcome.Connected(name)
                }
                if (r.code == 401) {
                    return LoginOutcome.Failed(
                        "Client ID não reconhecido pelo Simkl (401)",
                    )
                }
                if (r.code == 400) {
                    when (requireJson(r.body)?.optString("error")) {
                        "authorization_pending" -> Unit // ainda esperando a pessoa
                        "slow_down" -> every += 5000L
                        "expired_token" -> return LoginOutcome.Expired
                        "access_denied" -> return LoginOutcome.Denied
                        else -> return LoginOutcome.Failed(
                            "Simkl recusou a autorização (400)",
                        )
                    }
                    continue
                }
                return LoginOutcome.Failed("Simkl respondeu com erro HTTP ${r.code}")
            }
            return LoginOutcome.Expired
        } finally {
            codeVerifier = null
            pendingDeviceCode = null
        }
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
         * tipo certo p/ o fluxo de dispositivo) e copiar a "API Key" (= client_id).
         * Fonte: doc oficial (api.simkl.org/api-reference/auth, link
         * "Developer settings") + guias de setup da comunidade.
         */
        const val DEVELOPER_URL = "https://simkl.com/settings/developer/new/"
        const val DEVELOPER_LIST_URL = "https://simkl.com/settings/developer/"

        const val APP_NAME = "aotv"
        const val APP_VERSION = "1.15.0"

        val JSON = "application/json".toMediaType()
    }
}
