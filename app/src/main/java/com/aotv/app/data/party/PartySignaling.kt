package com.aotv.app.data.party

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Sinalização da Watch Party (Lote 19, v1.29.0) — como dois aparelhos
 * trocam a oferta/resposta SDP e os candidatos ICE do WebRTC sem nenhum
 * servidor de terceiro.
 *
 * ── ESTUDO (a decisão do Lote 19, registrada aqui) ─────────────────────
 * O MKCinema sinaliza por Supabase Realtime (backend fechado de terceiro
 * com chave embutida): proibido pelas regras duras do AOTV. Os caminhos
 * sem backend são dois, e os dois existem neste arquivo:
 *
 * 1. REDE LOCAL (o caso da sala de estar): o anfitrião liga um servidor
 *    HTTP efêmero no próprio aparelho ([PartyLanServer], NanoHTTPD, o
 *    mesmo padrão do backup por Wi-Fi da v1.13.0) e os convidados na
 *    mesma rede Wi-Fi trocam SDP/ICE pela caixa de mensagens dele
 *    (POST /party/signal + GET /party/signal). A senha da caixa é o
 *    próprio código da sessão (6 caracteres). Sem internet, sem conta,
 *    sem servidor fora de casa. É o caminho principal.
 * 2. TROCA MANUAL (amigos longe, sem servidor nenhum): anfitrião e
 *    convidado copiam e colam a oferta/resposta SDP ([ManualSdp] — um
 *    blob Base64 com SDP + candidatos ICE já reunidos, sem trickle).
 *    Funciona através da internet (o P2P depois é direto), mas é
 *    manual: cada par precisa de 1 troca. Por isso, no modo manual, a
 *    mídia só existe entre anfitrião e cada convidado (o chat e o estado
 *    chegam a todos via retransmissão do anfitrião).
 *
 * O que NÃO existe (honesto): servidor de sinalização hospedado, conta
 * na nuvem e relay TURN embutido. Sem TURN configurado pelo usuário, o
 * P2P que falha por NAT simétrico falha de verdade (ver [PartyRuntime]).
 * O STUN usado p/ descobrir o IP público é público e sem credencial
 * (`stun:stun.l.google.com:19302` — só descoberta de NAT, não é backend).
 */

// ─── Envelope de sinalização ──────────────────────────────────────────────

/** Uma mensagem na caixa do anfitrião (SDP ou ICE). `to` = peerId ou "*" (todos). */
data class SignalEnvelope(
    val seq: Long = 0L,
    val from: String,
    val to: String,
    /** "offer" | "answer" | "ice" | "hello". */
    val kind: String,
    /** SDP (offer/answer), JSON do candidato (ice) ou JSON do HELLO. */
    val payload: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("seq", seq)
        .put("from", from)
        .put("to", to)
        .put("kind", kind)
        .put("payload", payload)

    companion object {
        fun fromJson(o: JSONObject): SignalEnvelope? = runCatching {
            SignalEnvelope(
                seq = o.optLong("seq", 0L),
                from = o.optString("from"),
                to = o.optString("to"),
                kind = o.optString("kind"),
                payload = o.optString("payload"),
            ).takeIf { it.from.isNotBlank() && it.kind.isNotBlank() }
        }.getOrNull()
    }
}

// ─── Servidor LAN do anfitrião ────────────────────────────────────────────

/**
 * Servidor de sinalização na rede local (anfitrião).
 *
 * - Escuta em `0.0.0.0` (precisa alcançar a LAN) numa porta efêmera.
 * - GET /party/info?code= → {ok, code, title, hostName, hostPeerId, peers}.
 * - POST /party/signal (JSON {code, from, to, kind, payload}) → guarda na
 *   caixa e devolve {ok, seq}. Corpo > 64 KB → 413.
 * - GET /party/signal?code=&since=&peer= → mensagens com seq > since
 *   endereçadas a `peer` ou a "*" (todos).
 * - Código errado → 403. A caixa é só memória (some ao parar o servidor).
 *
 * Vive SÓ enquanto a sala está aberta: o runtime para o servidor ao sair.
 */
class PartyLanServer(
    /** Código da sessão (também é a senha da caixa). */
    private val code: String,
    /** Título do filme/série da sala (p/ o convidado conferir). */
    private val title: String,
    /** Id TMDB do título da sala (0 = sem id). */
    private val tmdbId: Int = 0,
    /** Nome do anfitrião. */
    private val hostName: String,
    /** Id do aparelho do anfitrião. */
    private val hostPeerId: String,
) {
    companion object {
        /** Corpo máximo de uma mensagem de sinalização (SDP + ICE cabe folgado). */
        const val MAX_SIGNAL_BYTES = 64 * 1024

        /** Quantas mensagens a caixa guarda (as antigas são descartadas). */
        const val MAX_MAILBOX = 500

        /** IP da LAN (Wi-Fi/Ethernet). null sem rede local. */
        fun lanIpAddress(): String? {
            val ifs = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            val up = ifs.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            fun firstIp(list: List<NetworkInterface>): String? = list
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .firstOrNull()?.hostAddress
            return firstIp(up.filter { it.name.startsWith("wlan") || it.name.startsWith("eth") })
                ?: firstIp(up)
        }
    }

    private val mailbox = ArrayDeque<SignalEnvelope>()
    private var nextSeq = 1L
    private val peers = LinkedHashMap<String, String>()

    private val server = object : NanoHTTPD("0.0.0.0", 0) {
        override fun serve(s: IHTTPSession): Response {
            return try {
                route(s)
            } catch (e: Exception) {
                text(Response.Status.INTERNAL_ERROR, "Erro no servidor")
            }
        }

        private fun route(s: IHTTPSession): Response {
            val path = s.uri.trimEnd('/')
            if (path.isEmpty() || path == "/") return text(Response.Status.OK, "AOTV Watch Party")
            val queryCode = s.parameters["code"]?.firstOrNull()
                ?: s.headers["x-aotv-code"]
                ?: ""
            if (queryCode != code) return text(Response.Status.FORBIDDEN, "Código inválido.")
            return when (path) {
                "/party/info" -> json(infoJson().toString())
                "/party/signal" -> when (s.method) {
                    Method.POST -> postSignal(s)
                    Method.GET -> pollSignal(s)
                    else -> text(Response.Status.METHOD_NOT_ALLOWED, "Método não permitido.")
                }
                else -> text(Response.Status.NOT_FOUND, "Não encontrado.")
            }
        }

        private fun postSignal(s: IHTTPSession): Response {
            val length = s.headers["content-length"]?.toLongOrNull() ?: 0L
            if (length > MAX_SIGNAL_BYTES) return text(Response.Status.PAYLOAD_TOO_LARGE, "Mensagem grande demais.")
            val files = HashMap<String, String>()
            runCatching { s.parseBody(files) }.onFailure { return text(Response.Status.BAD_REQUEST, "Não foi possível ler o envio.") }
            val body = files["postData"].orEmpty()
            if (body.isBlank()) return text(Response.Status.BAD_REQUEST, "Corpo vazio.")
            val env = runCatching { SignalEnvelope.fromJson(JSONObject(body)) }.getOrNull()
                ?: return text(Response.Status.BAD_REQUEST, "Mensagem inválida.")
            val seq = synchronized(mailbox) {
                val stored = env.copy(seq = nextSeq++)
                mailbox.addLast(stored)
                while (mailbox.size > MAX_MAILBOX) mailbox.removeFirst()
                if (env.kind == "hello") peers[env.from] = env.payload
                stored.seq
            }
            return json(JSONObject().put("ok", true).put("seq", seq).toString())
        }

        private fun pollSignal(s: IHTTPSession): Response {
            val since = s.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
            val peer = s.parameters["peer"]?.firstOrNull().orEmpty()
            val messages = synchronized(mailbox) {
                mailbox.filter { it.seq > since && (it.to == peer || it.to == "*") }
            }
            val arr = JSONArray()
            messages.forEach { arr.put(it.toJson()) }
            return json(JSONObject().put("ok", true).put("messages", arr).toString())
        }

        private fun text(status: Response.Status, msg: String) =
            newFixedLengthResponse(status, MIME_PLAINTEXT, msg)

        private fun json(body: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)
    }

    /** Liga o servidor. Devolve a URL base p/ mostrar na tela (null sem IP de LAN). */
    fun start(): String? {
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        return url()
    }

    fun stop() = runCatching { server.stop() }

    /** Porta efêmera que o servidor abriu. 0 antes de ligar. */
    val port: Int get() = server.listeningPort

    /** URL base, ex. http://192.168.1.10:12345 (o que vai no QR e no convite). */
    fun url(): String? = lanIpAddress()?.let { "http://$it:${server.listeningPort}" }

    private fun infoJson(): JSONObject {
        val peerArr = JSONArray()
        synchronized(mailbox) {
            peers.forEach { (id, name) ->
                peerArr.put(JSONObject().put("peerId", id).put("name", name))
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("code", code)
            .put("title", title)
            .put("tmdbId", tmdbId)
            .put("hostName", hostName)
            .put("hostPeerId", hostPeerId)
            .put("peers", peerArr)
    }
}

// ─── Cliente LAN do convidado ─────────────────────────────────────────────

/**
 * Cliente da caixa de sinalização (convidado — e o próprio anfitrião, que
 * também lê a caixa p/ receber ofertas). Cliente próprio, sem DoH: IP de
 * LAN não passa pelo DNS (mesmo padrão do backup por Wi-Fi).
 */
object PartyLanClient {
    private val lanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Info da sala (valida o código e traz título/anfitrião). */
    suspend fun fetchInfo(baseUrl: String, code: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/party/info?code=$code".toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Endereço inválido.")
            val req = Request.Builder().url(url).get().build()
            lanClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException(
                    if (resp.code == 403) "Código inválido." else "O anfitrião respondeu ${resp.code}.",
                )
                JSONObject(text)
            }
        }
    }

    /** Envia uma mensagem p/ a caixa. Devolve o nº de sequência. */
    suspend fun postSignal(baseUrl: String, code: String, env: SignalEnvelope): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/party/signal?code=$code".toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Endereço inválido.")
            val body = env.toJson().put("code", code).toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            lanClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException(
                    if (resp.code == 403) "Código inválido." else "O anfitrião respondeu ${resp.code}.",
                )
                JSONObject(text).optLong("seq", 0L)
            }
        }
    }

    /** Lê as mensagens novas da caixa (seq > since, p/ este peer ou p/ todos). */
    suspend fun pollSignals(baseUrl: String, code: String, peerId: String, since: Long): Result<List<SignalEnvelope>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl/party/signal?code=$code&since=$since&peer=$peerId".toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Endereço inválido.")
                val req = Request.Builder().url(url).get().build()
                lanClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException(
                        if (resp.code == 403) "Código inválido." else "O anfitrião respondeu ${resp.code}.",
                    )
                    val arr = JSONObject(text).optJSONArray("messages") ?: JSONArray()
                    val out = ArrayList<SignalEnvelope>(arr.length())
                    for (i in 0 until arr.length()) {
                        SignalEnvelope.fromJson(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
                    }
                    out
                }
            }
        }
}

// ─── Troca manual de SDP (sem servidor nenhum) ────────────────────────────

/** Um candidato ICE dentro do blob manual. */
data class ManualIceCandidate(val mid: String?, val idx: Int, val sdp: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("mid", mid)
        .put("idx", idx)
        .put("sdp", sdp)

    companion object {
        fun fromJson(o: JSONObject): ManualIceCandidate? = runCatching {
            ManualIceCandidate(
                mid = o.optString("mid").takeIf { it.isNotBlank() },
                idx = o.optInt("idx", 0),
                sdp = o.optString("sdp"),
            ).takeIf { it.sdp.isNotBlank() }
        }.getOrNull()
    }
}

/** Oferta ou resposta SDP + candidatos ICE, p/ copiar e colar. */
data class ManualSdpData(
    /** "offer" ou "answer". */
    val kind: String,
    val sdp: String,
    val candidates: List<ManualIceCandidate>,
)

/**
 * Troca manual de SDP: o blob é um JSON Base64 (URL-safe, sem quebra de
 * linha) com o SDP + TODOS os candidatos ICE já reunidos (sem trickle:
 * quem gera espera o gathering completar). null quando o blob é inválido
 * ou grande demais p/ copiar e colar com segurança.
 */
object ManualSdp {
    /** Teto do blob (caracteres). SDP + ICE de rede local cabe folgado; acima disso, o usuário errou ao colar. */
    const val MAX_BLOB_CHARS = 16384

    fun encode(kind: String, sdp: String, candidates: List<ManualIceCandidate>): String? {
        if (kind != "offer" && kind != "answer") return null
        if (sdp.isBlank()) return null
        val ice = JSONArray()
        candidates.forEach { ice.put(it.toJson()) }
        val json = JSONObject()
            .put("v", 1)
            .put("kind", kind)
            .put("sdp", sdp)
            .put("ice", ice)
            .toString()
        val blob = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return blob.takeIf { it.length <= MAX_BLOB_CHARS }
    }

    fun decode(blob: String): ManualSdpData? = runCatching {
        val clean = blob.trim()
        if (clean.isBlank() || clean.length > MAX_BLOB_CHARS) return null
        val json = JSONObject(String(Base64.decode(clean, Base64.URL_SAFE), Charsets.UTF_8))
        if (json.optInt("v", 1) != 1) return null
        val kind = json.optString("kind")
        if (kind != "offer" && kind != "answer") return null
        val sdp = json.optString("sdp").takeIf { it.isNotBlank() } ?: return null
        val iceArr = json.optJSONArray("ice") ?: JSONArray()
        val candidates = ArrayList<ManualIceCandidate>(iceArr.length())
        for (i in 0 until iceArr.length()) {
            ManualIceCandidate.fromJson(iceArr.optJSONObject(i) ?: continue)?.let { candidates.add(it) }
        }
        ManualSdpData(kind = kind, sdp = sdp, candidates = candidates)
    }.getOrNull()
}
