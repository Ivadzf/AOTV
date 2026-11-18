package com.aotv.app.data.remote

import fi.iki.elonen.NanoHTTPD
import com.aotv.app.data.backup.BackupWifiServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Controle remoto por LAN (Lote 22, v1.32.0, item 1).
 *
 * O aparelho expõe um servidor HTTP leve (NanoHTTPD, mesmo desenho do
 * BackupWifiServer da v1.13.0) na rede local, com PIN de 4 dígitos gerado
 * na hora. O outro aparelho mostra um QR (só a URL da página — o PIN é
 * digitado à parte, como no backup) e controla este aparelho:
 *
 * - GET / → página simples (status, log, tela, botões).
 * - GET /ping?pin= → {"ok":true}, p/ o outro aparelho conferir o PIN.
 * - GET /log?pin= → últimas linhas do log ao vivo (só método + caminho +
 *   status; o PIN e os corpos nunca vão p/ o log).
 * - GET /screen?pin= → rota atual + título + estado de reprodução (se o
 *   player estiver aberto) + a lista de botões da tela (nome + descrição).
 * - POST /press?pin= {name} → pressiona um botão pelo nome.
 * - POST /clear-cache?pin= → limpa o cache de imagens e os temporários.
 * - POST /keys?pin= {keys:{tipo:valor}, links:[...]} → colar chaves de
 *   contas e links de addons no celular e enviar tudo p/ a TV de uma vez,
 *   sem digitar com o controle. Só chaves que o app já guarda nas prefs
 *   (TMDB, Trakt, Simkl, MDBList, TVDB, SubDL, debrid); nunca senhas.
 * - POST /addon?pin= {url} → a TV instala o addon sem digitar a URL no
 *   controle (resolve pela mesma rota de "instalar por URL" dos addons
 *   Kodi — ZIP direto instala na hora, índice pede escolha na tela).
 * - GET /backup?pin= → o backup JSON deste aparelho (p/ o celular baixar
 *   pelo QR, como no backup por Wi-Fi).
 * - GET /handoff?pin= → o que está tocando agora (título, posição,
 *   duração, fonte) — "Take it with you": ao sair da sala, o celular
 *   busca aqui e continua o filme na mesma fala/posição; ao voltar, a TV
 *   busca o handoff do celular e retoma de onde o celular parou.
 * - POST /handoff?pin= {handoff} → recebe um handoff (o outro aparelho
 *   manda o que está tocando p/ este retomar).
 *
 * Tráfego só na LAN (HTTP local, sem sair da rede; o manifest já permite
 * cleartext por causa do proxy Kodi pré-existente — o servidor escuta em
 * 0.0.0.0 e só aceita conexões da rede local). O servidor vive SÓ
 * enquanto a página "Controle remoto e TV" está aberta (mesmo precedente
 * do BackupWifiServer): a tela para ele ao sair.
 */

/** O que está tocando agora, p/ o handoff entre aparelhos ("Take it with you"). */
data class PlaybackHandoff(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastEpisode: String,
    val sourceUrl: String,
    val watchedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tmdbId", tmdbId)
        .put("type", type)
        .put("title", title)
        .put("posterUrl", posterUrl)
        .put("positionMs", positionMs)
        .put("durationMs", durationMs)
        .put("lastEpisode", lastEpisode)
        .put("sourceUrl", sourceUrl)
        .put("watchedAt", watchedAt)

    companion object {
        fun fromJson(json: JSONObject): PlaybackHandoff? = runCatching {
            PlaybackHandoff(
                tmdbId = json.optInt("tmdbId", 0),
                type = json.optString("type", "MOVIE"),
                title = json.optString("title", ""),
                posterUrl = json.optString("posterUrl", ""),
                positionMs = json.optLong("positionMs", 0),
                durationMs = json.optLong("durationMs", 0),
                lastEpisode = json.optString("lastEpisode", ""),
                sourceUrl = json.optString("sourceUrl", ""),
                watchedAt = json.optLong("watchedAt", System.currentTimeMillis()),
            ).takeIf { it.tmdbId > 0 }
        }.getOrNull()
    }
}

/**
 * Estado de reprodução atual, p/ o /screen e o /handoff. O player atualiza
 * aqui toda vez que salva o progresso (o poll de 10 s e a saída — ver
 * PlayerProgressViewModel.save). null = nada tocando.
 */
object RemotePlaybackState {
    @Volatile var current: PlaybackHandoff? = null
        private set

    fun update(handoff: PlaybackHandoff?) {
        current = handoff
    }
}

/** Um botão da tela atual, pressionável por nome pelo controle remoto. */
data class RemoteAction(
    val name: String,
    val description: String,
    /** Devolve a mensagem de resultado. Roda na thread do servidor: quem
     * registra e precisa da main (navegação, player) posta p/ ela dentro
     * da lambda e devolve a mensagem na hora. */
    val run: () -> String,
)

/** Registro dos botões da tela atual. Global e thread-safe (o servidor lê
 * de threads próprias e as telas escrevem da main). */
object RemoteActionRegistry {
    private val actions = LinkedHashMap<String, RemoteAction>()

    @Synchronized fun register(action: RemoteAction) {
        actions[action.name] = action
    }

    @Synchronized fun unregister(name: String) {
        actions.remove(name)
    }

    @Synchronized fun snapshot(): List<RemoteAction> = actions.values.toList()

    @Synchronized fun press(name: String): String? = actions[name]?.run?.invoke()
}

/** Navegação do app, oferecida p/ o controle remoto. MoviesNavigation
 * preenche a rota atual; a navegação em si vai pelo [RemoteActionRegistry]
 * (ações "voltar"/"inicio"/"buscar" registradas lá). */
object RemoteNav {
    @Volatile var currentRoute: String = ""
}

/** Log em anel (máx. 100 linhas) p/ o /log ao vivo. */
object RemoteLog {
    private const val MAX_LINES = 100
    private val lines = ArrayDeque<String>()

    @Synchronized fun add(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized fun tail(): List<String> = lines.toList()

    @Synchronized fun clear() {
        lines.clear()
    }
}

/** Chaves/links recebidos pelo /keys (colar no celular, enviar p/ a TV). */
data class RemoteKeysPayload(
    /** tipo -> valor. Tipos aceitos: tmdb, trakt_client_id, simkl, mdblist,
     * tvdb, subdl, debrid_alldebrid. Qualquer outro tipo é ignorado. */
    val keys: Map<String, String>,
    /** Links (manifest de addon ou URL de instalação). */
    val links: List<String>,
)

/** O que a tela dona do servidor precisa responder. Tudo com default, p/
 * quem usa só precisar do que for implementar de verdade. */
interface RemoteControlHost {
    /** Rota + título atuais p/ o /screen (JSON com "route" e "title"). */
    fun currentScreen(): JSONObject = JSONObject()
        .put("route", RemoteNav.currentRoute)
        .put("title", "")

    /** Pressiona um botão por nome (usa o [RemoteActionRegistry]). */
    fun pressAction(name: String): String =
        RemoteActionRegistry.press(name) ?: "Botão \"$name\" não existe nesta tela."

    /** Limpa o cache de imagens e os temporários. Devolve a mensagem. */
    fun clearCache(): String = "Nada para limpar."

    /** Chaves/links recebidos. Devolve a mensagem de resultado. */
    fun onKeysReceived(payload: RemoteKeysPayload): String = "Nada para aplicar."

    /** URL de addon recebida. Devolve a mensagem (a instalação roda em
     * 2º plano e o resultado vai p/ o log). */
    fun onAddonReceived(url: String): String = "Addon não suportado aqui."

    /** JSON do backup deste aparelho (p/ o /backup). */
    fun backupJson(): String = ""

    /** Handoff recebido (o outro aparelho manda o que está tocando). */
    fun onHandoffReceived(handoff: PlaybackHandoff): String = "Handoff não suportado aqui."

    /** Handoff atual deste aparelho (p/ o /handoff). */
    fun currentHandoff(): PlaybackHandoff? = RemotePlaybackState.current
}

class RemoteControlServer(
    private val host: RemoteControlHost,
) {
    /** PIN de 4 dígitos, gerado na hora. Mostrado na tela junto do QR. */
    val pin: String = "%04d".format(Random.nextInt(0, 10000))

    private val server = object : NanoHTTPD("0.0.0.0", 0) {
        override fun serve(s: IHTTPSession): Response {
            return try {
                val resp = route(s)
                RemoteLog.add("${s.method} ${s.uri} -> ${resp.status.requestStatus}")
                resp
            } catch (e: Exception) {
                RemoteLog.add("${s.method} ${s.uri} -> erro: ${e.message}")
                text(Response.Status.INTERNAL_ERROR, "Erro no servidor")
            }
        }

        private fun route(s: IHTTPSession): Response {
            val files = HashMap<String, String>()
            if (s.method == Method.POST) {
                runCatching { s.parseBody(files) }.onFailure {
                    return text(Response.Status.BAD_REQUEST, "Não foi possível ler o envio.")
                }
            }
            val path = s.uri.trimEnd('/')
            if (path.isEmpty() || path == "/") return htmlPage()
            if (!pinOk(s)) return text(Response.Status.FORBIDDEN, "PIN inválido.")
            return when (path) {
                "/ping" -> json(JSONObject().put("ok", true).toString())
                "/log" -> json(JSONObject().put("lines", JSONArray(RemoteLog.tail())).toString())
                "/screen" -> {
                    val screen = host.currentScreen()
                    screen.put("actions", JSONArray(RemoteActionRegistry.snapshot().map { action ->
                        JSONObject().put("name", action.name).put("description", action.description)
                    }))
                    RemotePlaybackState.current?.let { screen.put("playback", it.toJson()) }
                    json(screen.toString())
                }
                "/press" -> {
                    val name = jsonField(bodyParam(s, files, "name"), "name")
                    if (name.isNullOrBlank()) return text(Response.Status.BAD_REQUEST, "Falta o nome do botão.")
                    json(JSONObject().put("ok", true).put("message", host.pressAction(name)).toString())
                }
                "/clear-cache" -> json(JSONObject().put("ok", true).put("message", host.clearCache()).toString())
                "/keys" -> {
                    val body = bodyParam(s, files, "json") ?: return text(Response.Status.BAD_REQUEST, "Falta o JSON.")
                    val payload = parseKeys(body)
                        ?: return text(Response.Status.BAD_REQUEST, "JSON inválido.")
                    json(JSONObject().put("ok", true).put("message", host.onKeysReceived(payload)).toString())
                }
                "/addon" -> {
                    val url = jsonField(bodyParam(s, files, "url"), "url")
                    if (url.isNullOrBlank()) return text(Response.Status.BAD_REQUEST, "Falta a URL do addon.")
                    json(JSONObject().put("ok", true).put("message", host.onAddonReceived(url)).toString())
                }
                "/backup" -> {
                    val backup = host.backupJson()
                    if (backup.isBlank()) return text(Response.Status.INTERNAL_ERROR, "Não foi possível montar o backup.")
                    newFixedLengthResponse(Response.Status.OK, "application/json", backup).apply {
                        addHeader("Content-Disposition", "attachment; filename=\"aotv-backup.json\"")
                    }
                }
                "/handoff" -> {
                    if (s.method == Method.GET) {
                        val current = host.currentHandoff()
                            ?: return text(Response.Status.NOT_FOUND, "Nada tocando aqui agora.")
                        json(current.toJson().toString())
                    } else {
                        val body = bodyParam(s, files, "json")
                            ?: return text(Response.Status.BAD_REQUEST, "Falta o JSON do handoff.")
                        val handoff = runCatching { PlaybackHandoff.fromJson(JSONObject(body)) }.getOrNull()
                            ?: return text(Response.Status.BAD_REQUEST, "Handoff inválido.")
                        json(JSONObject().put("ok", true).put("message", host.onHandoffReceived(handoff)).toString())
                    }
                }
                else -> text(Response.Status.NOT_FOUND, "Não encontrado.")
            }
        }

        /** PIN na query, no header X-AOTV-PIN ou no campo do formulário. */
        private fun pinOk(s: IHTTPSession): Boolean {
            val pin = s.parameters["pin"]?.firstOrNull()
                ?: s.headers["x-aotv-pin"]
                ?: return false
            return pin == this@RemoteControlServer.pin
        }

        /** Corpo do POST: campo de formulário, campo de texto ou JSON cru. */
        private fun bodyParam(s: IHTTPSession, files: Map<String, String>, key: String): String? {
            s.parameters[key]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            files["postData"]?.takeIf { it.isNotBlank() }?.let { return it }
            return null
        }

        /**
         * Campo de formulário ou campo dentro de um JSON cru: o cliente
         * manda JSON (`{"name":"voltar"}`), e o `postData` do NanoHTTPD é
         * o JSON inteiro — sem desempacotar, o botão procurado seria o
         * próprio texto do JSON. Texto simples volta como está.
         */
        private fun jsonField(raw: String?, field: String): String? {
            if (raw.isNullOrBlank()) return null
            val fromJson = runCatching { JSONObject(raw).optString(field) }
                .getOrNull()?.takeIf { it.isNotBlank() }
            return fromJson ?: raw
        }

        private fun parseKeys(body: String): RemoteKeysPayload? = runCatching {
            val json = JSONObject(body)
            val keys = mutableMapOf<String, String>()
            json.optJSONObject("keys")?.let { obj ->
                obj.keys().forEach { k ->
                    obj.optString(k).takeIf { it.isNotBlank() }?.let { keys[k] = it }
                }
            }
            val links = mutableListOf<String>()
            json.optJSONArray("links")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { links.add(it) }
                }
            }
            RemoteKeysPayload(keys = keys, links = links)
        }.getOrNull()

        private fun text(status: Response.Status, msg: String) =
            newFixedLengthResponse(status, MIME_PLAINTEXT, msg)

        private fun json(body: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)

        private fun htmlPage() = newFixedLengthResponse(
            Response.Status.OK, MIME_HTML,
            """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Controle remoto do AOTV</title></head>
            <body style="font-family:sans-serif;max-width:560px;margin:32px auto;padding:0 16px">
            <h1>Controle remoto do AOTV</h1>
            <p>Este aparelho está pronto para ser controlado pela rede local. Tudo fica entre os seus aparelhos.</p>
            <p><a href="/screen?pin=">Ver a tela</a> · <a href="/log?pin=">Ver o log</a> · <a href="/backup?pin=">Baixar backup</a> · <a href="/handoff?pin=">O que está tocando</a></p>
            <p>Os links pedem o PIN de 4 dígitos (mostrado na tela do aparelho).</p>
            <h2>Pressionar um botão</h2>
            <form method="post" action="/press">
            <label>PIN <input name="pin" inputmode="numeric" autocomplete="off" required></label><br><br>
            <label>Botão <input name="name" autocomplete="off" required></label>
            <button type="submit">Pressionar</button></form>
            <h2>Limpar o cache</h2>
            <form method="post" action="/clear-cache">
            <label>PIN <input name="pin" inputmode="numeric" autocomplete="off" required></label>
            <button type="submit">Limpar</button></form>
            </body></html>
            """.trimIndent(),
        )
    }

    /** Liga o servidor. Devolve a URL p/ mostrar na tela e no QR (null sem IP de LAN). */
    fun start(): String? {
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        return url()
    }

    fun stop() = runCatching { server.stop() }

    /** Porta efêmera que o servidor abriu. 0 antes de ligar. */
    val port: Int get() = server.listeningPort

    /** URL da página inicial, ex. http://192.168.1.10:12345/ (o que vai no QR — sem o PIN). */
    fun url(): String? = BackupWifiServer.lanIpAddress()?.let { "http://$it:${server.listeningPort}/" }
}

/** Cliente do controle remoto: o celular fala com o servidor da TV (e vice-versa). */
object RemoteControlClient {
    private val lanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun url(host: String, port: Int, path: String, pin: String): String =
        "http://$host:$port$path?pin=$pin"

    private suspend fun get(host: String, port: Int, path: String, pin: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url(host, port, path, pin)).get().build()
                lanClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException(
                        if (resp.code == 403) "PIN inválido." else "O outro aparelho respondeu ${resp.code}.",
                    )
                    text
                }
            }
        }

    private suspend fun postJson(host: String, port: Int, path: String, pin: String, json: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url(host, port, path, pin)).post(body).build()
                lanClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException(
                        if (resp.code == 403) "PIN inválido." else "O outro aparelho respondeu ${resp.code}.",
                    )
                    text
                }
            }
        }

    /** Confere o PIN e se o servidor responde. */
    suspend fun ping(host: String, port: Int, pin: String): Result<Boolean> =
        get(host, port, "/ping", pin).map { runCatching { JSONObject(it).optBoolean("ok") }.getOrDefault(false) }

    /** Tela do outro aparelho (rota + botões + reprodução). */
    suspend fun fetchScreen(host: String, port: Int, pin: String): Result<JSONObject> =
        get(host, port, "/screen", pin).map { JSONObject(it) }

    /** Pressiona um botão pelo nome no outro aparelho. */
    suspend fun press(host: String, port: Int, pin: String, name: String): Result<String> =
        postJson(host, port, "/press", pin, JSONObject().put("name", name).toString())
            .map { runCatching { JSONObject(it).optString("message") }.getOrNull().orEmpty() }

    /** Envia chaves de contas e links p/ o outro aparelho. */
    suspend fun sendKeys(host: String, port: Int, pin: String, payload: RemoteKeysPayload): Result<String> {
        val json = JSONObject()
            .put("keys", JSONObject(payload.keys))
            .put("links", JSONArray(payload.links))
        return postJson(host, port, "/keys", pin, json.toString())
            .map { runCatching { JSONObject(it).optString("message") }.getOrNull().orEmpty() }
    }

    /** Envia a URL de um addon p/ o outro aparelho instalar. */
    suspend fun sendAddon(host: String, port: Int, pin: String, addonUrl: String): Result<String> =
        postJson(host, port, "/addon", pin, JSONObject().put("url", addonUrl).toString())
            .map { runCatching { JSONObject(it).optString("message") }.getOrNull().orEmpty() }

    /** Busca o que está tocando no outro aparelho ("Take it with you"). */
    suspend fun fetchHandoff(host: String, port: Int, pin: String): Result<PlaybackHandoff> =
        get(host, port, "/handoff", pin).map {
            PlaybackHandoff.fromJson(JSONObject(it))
                ?: throw IllegalStateException("O outro aparelho não devolveu um handoff válido.")
        }

    /** Manda o que está tocando aqui p/ o outro aparelho retomar. */
    suspend fun sendHandoff(host: String, port: Int, pin: String, handoff: PlaybackHandoff): Result<String> =
        postJson(host, port, "/handoff", pin, handoff.toJson().toString())
            .map { runCatching { JSONObject(it).optString("message") }.getOrNull().orEmpty() }
}
