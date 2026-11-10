package com.aotv.app.data.debrid

import com.aotv.app.data.torrent.TorrentFile
import com.aotv.app.data.torrent.TorrentFiles
import com.aotv.app.data.torrent.TorrentSpec
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** HTTP dos serviços de debrid. Tudo bloqueante, quem chama já está no Dispatchers.IO. */
/** Aberta só para os testes trocarem a rede por respostas prontas. */
internal open class DebridHttp(private val client: OkHttpClient?) {
    data class Res(val code: Int, val body: String) {
        val ok get() = code in 200..299
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
    }

    private fun call(b: Request.Builder, bearer: String?): Res {
        b.header("User-Agent", "AOTV")
        if (!bearer.isNullOrBlank()) b.header("Authorization", "Bearer $bearer")
        return runCatching {
            client!!.newCall(b.build()).execute().use { Res(it.code, it.body?.string().orEmpty()) }
        }.getOrElse { Res(-1, it.message.orEmpty()) }
    }

    open fun get(url: String, bearer: String? = null) = call(Request.Builder().url(url), bearer)

    open fun form(url: String, fields: List<Pair<String, String>>, bearer: String? = null): Res {
        val fb = FormBody.Builder()
        fields.forEach { (k, v) -> fb.add(k, v) }
        return call(Request.Builder().url(url).post(fb.build()), bearer)
    }

    open fun json(url: String, body: String, bearer: String? = null): Res =
        call(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())), bearer)

    open fun multipart(url: String, fields: List<Pair<String, String>>, bearer: String? = null): Res {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        return call(Request.Builder().url(url).post(mb.build()), bearer)
    }

    open fun delete(url: String, bearer: String? = null) = call(Request.Builder().url(url).delete(), bearer)
}

/** Data ISO do serviço (2026-12-01T00:00:00.000Z) em epoch ms. 0 se não der para ler. */
internal fun isoMs(s: String?): Long {
    if (s.isNullOrBlank()) return 0L
    val clean = s.trim().replace(Regex("\\.\\d+"), "").replace("Z", "+0000").replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
    for (p in listOf("yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")) {
        runCatching {
            val f = java.text.SimpleDateFormat(p, java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            return f.parse(clean)!!.time
        }
    }
    return 0L
}

/** Arquivo do torrent no serviço, com o id que o serviço usa para pedir o link. */
internal data class RemoteFile(val serviceId: String, val file: TorrentFile, val link: String = "")

internal fun pickRemote(files: List<RemoteFile>, spec: TorrentSpec, indexReliable: Boolean, season: Int, episode: Int): RemoteFile? {
    val picked = TorrentFiles.pickDebrid(files.map { it.file }, spec, indexReliable, season, episode) ?: return null
    return files.firstOrNull { it.file === picked }
}

// ── Real-Debrid ─────────────────────────────────────────────────────────────────

internal class RealDebridApi(private val http: DebridHttp) {
    companion object {
        const val BASE = "https://api.real-debrid.com/rest/1.0"
        const val OAUTH = "https://api.real-debrid.com/oauth/v2"
        /** Cliente público que o Real-Debrid libera para apps de código aberto (login por código). */
        const val OPEN_CLIENT = "X245A4XAIBGVM"
        private const val DEVICE_GRANT = "http://oauth.net/grant_type/device/1.0"
        private val WORKING = setOf("queued", "downloading", "compressing", "uploading")
        private val BROKEN = setOf("magnet_error", "error", "virus", "dead")
    }

    /** Nome e fim do premium. null quando o token não vale. */
    fun user(token: String): Pair<String, Long>? {
        val r = http.get("$BASE/user", token)
        val o = r.json() ?: return null
        if (!r.ok) return null
        val left = o.optLong("premium", 0L)
        val until = if (left > 0) System.currentTimeMillis() + left * 1000 else isoMs(o.optString("expiration"))
        return o.optString("username").ifBlank { o.optString("email") } to until
    }

    fun deviceStart(): DeviceCode? {
        val o = http.get("$OAUTH/device/code?client_id=$OPEN_CLIENT&new_credentials=yes").json() ?: return null
        val code = o.optString("device_code").ifBlank { return null }
        return DeviceCode(
            DebridProvider.REAL_DEBRID, code, o.optString("user_code"),
            o.optString("direct_verification_url").ifBlank { o.optString("verification_url").ifBlank { "https://real-debrid.com/device" } },
            o.optInt("interval", 5).coerceIn(2, 15),
            System.currentTimeMillis() + o.optLong("expires_in", 600) * 1000,
            manualUrl = o.optString("verification_url").ifBlank { "https://real-debrid.com/device" },
        )
    }

    fun devicePoll(d: DeviceCode): DevicePoll {
        if (System.currentTimeMillis() > d.expiresAt) return DevicePoll.Expired
        val cred = http.get("$OAUTH/device/credentials?client_id=$OPEN_CLIENT&code=${d.deviceCode}")
        if (!cred.ok) return if (cred.code == 403 || cred.code == 400 || cred.code == -1) DevicePoll.Pending else DevicePoll.Failed("Real-Debrid recusou (${cred.code})")
        val c = cred.json() ?: return DevicePoll.Pending
        val id = c.optString("client_id")
        val secret = c.optString("client_secret")
        if (id.isBlank() || secret.isBlank()) return DevicePoll.Pending
        val t = http.form("$OAUTH/token", listOf("client_id" to id, "client_secret" to secret, "code" to d.deviceCode, "grant_type" to DEVICE_GRANT))
        val o = t.json() ?: return DevicePoll.Failed("Real-Debrid não devolveu o token")
        val token = o.optString("access_token").ifBlank { return DevicePoll.Failed("Real-Debrid não devolveu o token") }
        val (user, until) = user(token) ?: ("" to 0L)
        return DevicePoll.Done(
            DebridAccount(
                DebridProvider.REAL_DEBRID, token, user, until,
                refresh = o.optString("refresh_token"), clientId = id, clientSecret = secret,
                expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            )
        )
    }

    /** Renova o token do login por código. null se a renovação foi recusada. */
    fun refresh(a: DebridAccount): DebridAccount? {
        if (a.refresh.isBlank() || a.clientId.isBlank()) return null
        val o = http.form("$OAUTH/token", listOf("client_id" to a.clientId, "client_secret" to a.clientSecret, "code" to a.refresh, "grant_type" to DEVICE_GRANT)).json() ?: return null
        val token = o.optString("access_token").ifBlank { return null }
        return a.copy(
            token = token, refresh = o.optString("refresh_token").ifBlank { a.refresh },
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
        )
    }

    /**
     * Sem a consulta de cache (o Real-Debrid tirou), o jeito é adicionar e ver se fica pronto na
     * hora. Torrent que começa a baixar não está no cache e sai da conta.
     */
    fun resolve(token: String, magnet: String, spec: TorrentSpec, season: Int, episode: Int): DebridResolve {
        val add = http.form("$BASE/torrents/addMagnet", listOf("magnet" to magnet), token)
        if (add.code == 401) return DebridResolve.Failed("Token do Real-Debrid venceu. Conecte de novo.")
        if (add.code == 403) return DebridResolve.Failed("Conta do Real-Debrid sem premium")
        val id = add.json()?.optString("id").orEmpty()
        if (!add.ok || id.isBlank()) return DebridResolve.Failed("Real-Debrid não aceitou o torrent (${add.code})")
        var keep = false
        try {
            var info = waitInfo(token, id) ?: return DebridResolve.NotCached
            if (info.optString("status") in BROKEN) return DebridResolve.NotCached
            val files = filesOf(info)
            val chosen = pickRemote(files, spec, indexReliable = true, season, episode) ?: return DebridResolve.Failed("Arquivo de vídeo não encontrado no torrent")
            if (info.optString("status") == "waiting_files_selection") {
                http.form("$BASE/torrents/selectFiles/$id", listOf("files" to chosen.serviceId), token)
                info = afterSelect(token, id) ?: return DebridResolve.NotCached
            }
            if (info.optString("status") != "downloaded") return DebridResolve.NotCached
            val link = linkFor(info, chosen) ?: return DebridResolve.NotCached
            val un = http.form("$BASE/unrestrict/link", listOf("link" to link), token)
            val url = un.json()?.optString("download").orEmpty()
            if (!un.ok || url.isBlank()) return DebridResolve.Failed("Real-Debrid não liberou o link (${un.code})")
            keep = true
            return DebridResolve.Ok(url, DebridProvider.REAL_DEBRID)
        } finally {
            // Torrent que não serviu sai da conta da pessoa.
            if (!keep) http.delete("$BASE/torrents/delete/$id", token)
        }
    }

    private fun waitInfo(token: String, id: String): JSONObject? {
        repeat(14) { i ->
            val o = http.get("$BASE/torrents/info/$id", token).json()
            val st = o?.optString("status")
            if (o != null && st != "magnet_conversion" && (o.optJSONArray("files")?.length() ?: 0) > 0) return o
            if (st in BROKEN) return o
            Thread.sleep(if (i < 4) 500 else 900)
        }
        return null
    }

    /** Depois de escolher o arquivo. Pronto na hora é cache. Começou a baixar não é. */
    private fun afterSelect(token: String, id: String): JSONObject? {
        var last: JSONObject? = null
        for (i in 0 until 6) {
            Thread.sleep(if (i == 0) 400 else 800)
            last = http.get("$BASE/torrents/info/$id", token).json() ?: continue
            val st = last.optString("status")
            if (st == "downloaded" || st in BROKEN) return last
            if (st in WORKING && i >= 1) return last
        }
        return last
    }

    private fun filesOf(info: JSONObject): List<RemoteFile> {
        val arr = info.optJSONArray("files") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = f.optInt("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            RemoteFile(id.toString(), TorrentFile(id - 1, f.optString("path").trimStart('/'), f.optLong("bytes")))
        }
    }

    /** Os links vêm na ordem dos arquivos selecionados. */
    private fun linkFor(info: JSONObject, chosen: RemoteFile): String? {
        val links = info.optJSONArray("links") ?: return null
        val selected = info.optJSONArray("files")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optInt("selected") == 1 }.map { it.optInt("id").toString() }
        }.orEmpty()
        val idx = selected.indexOf(chosen.serviceId).takeIf { it >= 0 } ?: if (links.length() == 1) 0 else return null
        return links.optString(idx).takeIf { it.isNotBlank() }
    }
}

// ── TorBox ──────────────────────────────────────────────────────────────────────

internal class TorBoxApi(private val http: DebridHttp) {
    companion object { const val BASE = "https://api.torbox.app/v1/api" }

    fun user(token: String): Pair<String, Long>? {
        val r = http.get("$BASE/user/me", token)
        val o = r.json() ?: return null
        if (!r.ok || !o.optBoolean("success", r.ok)) return null
        val d = o.optJSONObject("data") ?: return null
        return d.optString("email") to isoMs(d.optString("premium_expires_at"))
    }

    fun deviceStart(): DeviceCode? {
        val d = http.get("$BASE/user/auth/device/start?app=AOTV").json()?.optJSONObject("data") ?: return null
        val code = d.optString("device_code").ifBlank { return null }
        return DeviceCode(
            DebridProvider.TORBOX, code, d.optString("code"),
            d.optString("verification_url").ifBlank { d.optString("friendly_verification_url") },
            d.optInt("interval", 5).coerceIn(2, 15),
            isoMs(d.optString("expires_at")).takeIf { it > 0 } ?: (System.currentTimeMillis() + 600_000),
            manualUrl = d.optString("friendly_verification_url").ifBlank { d.optString("verification_url") },
        )
    }

    fun devicePoll(d: DeviceCode): DevicePoll {
        if (System.currentTimeMillis() > d.expiresAt) return DevicePoll.Expired
        val r = http.json("$BASE/user/auth/device/token", JSONObject().put("device_code", d.deviceCode).toString())
        val o = r.json()
        val token = o?.optJSONObject("data")?.optString("access_token").orEmpty()
        if (r.ok && token.isNotBlank()) {
            val (user, until) = user(token) ?: ("" to 0L)
            return DevicePoll.Done(DebridAccount(DebridProvider.TORBOX, token, user, until))
        }
        val msg = (o?.optString("detail").orEmpty() + " " + o?.optString("error").orEmpty()).lowercase()
        return when {
            r.code == 410 || "expired" in msg -> DevicePoll.Expired
            r.code in setOf(-1, 400, 404, 409, 425) || listOf("pending", "not authorized", "not been used", "not used", "scan").any { it in msg } -> DevicePoll.Pending
            else -> DevicePoll.Failed("TorBox recusou (${r.code})")
        }
    }

    /** Hashes que o TorBox tem prontos. */
    fun cached(token: String, hashes: List<String>): Set<String> {
        val out = HashSet<String>()
        hashes.chunked(80).forEach { part ->
            val body = JSONObject().put("hashes", JSONArray(part)).toString()
            val d = http.json("$BASE/torrents/checkcached?format=object&list_files=false", body, token).json()?.opt("data")
            when (d) {
                is JSONObject -> d.keys().forEach { out += it.lowercase() }
                is JSONArray -> (0 until d.length()).forEach { i -> d.optJSONObject(i)?.optString("hash")?.let { out += it.lowercase() } }
            }
        }
        return out
    }

    fun resolve(token: String, magnet: String, spec: TorrentSpec, season: Int, episode: Int): DebridResolve {
        val c = http.multipart("$BASE/torrents/createtorrent", listOf("magnet" to magnet, "add_only_if_cached" to "true", "allow_zip" to "false"), token)
        if (c.code == 401 || c.code == 403) return DebridResolve.Failed("Chave do TorBox não vale mais. Conecte de novo.")
        val co = c.json()
        val id = co?.optJSONObject("data")?.let { it.optString("torrent_id").ifBlank { it.optString("id") } }.orEmpty()
        if (id.isBlank()) {
            val msg = (co?.optString("detail").orEmpty() + " " + co?.optString("error").orEmpty()).lowercase()
            return if (c.code == 409 || "cache" in msg) DebridResolve.NotCached else DebridResolve.Failed("TorBox não aceitou o torrent (${c.code})")
        }
        val list = http.get("$BASE/torrents/mylist?id=$id&bypass_cache=true", token).json()?.opt("data")
        val t = when (list) { is JSONObject -> list; is JSONArray -> list.optJSONObject(0); else -> null }
            ?: return DebridResolve.Failed("TorBox não listou o torrent")
        val arr = t.optJSONArray("files") ?: JSONArray()
        val files = (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val fid = f.optInt("id", i)
            RemoteFile(fid.toString(), TorrentFile(fid, f.optString("name").ifBlank { f.optString("short_name") }, f.optLong("size")))
        }
        val chosen = pickRemote(files, spec, indexReliable = true, season, episode) ?: return DebridResolve.Failed("Arquivo de vídeo não encontrado no torrent")
        val r = http.get("$BASE/torrents/requestdl?token=$token&torrent_id=$id&file_id=${chosen.serviceId}&zip_link=false&redirect=false", token)
        val url = r.json()?.optString("data").orEmpty()
        return if (r.ok && url.startsWith("http")) DebridResolve.Ok(url, DebridProvider.TORBOX)
        else DebridResolve.Failed("TorBox não liberou o link (${r.code})")
    }
}

// ── Premiumize ──────────────────────────────────────────────────────────────────

internal class PremiumizeApi(private val http: DebridHttp) {
    companion object { const val BASE = "https://www.premiumize.me/api" }

    fun user(key: String): Pair<String, Long>? {
        val o = http.get("$BASE/account/info?apikey=${enc(key)}").json() ?: return null
        if (o.optString("status") != "success") return null
        return o.optString("customer_id") to o.optLong("premium_until", 0L) * 1000
    }

    fun cached(key: String, hashes: List<String>): Set<String> {
        val out = HashSet<String>()
        hashes.chunked(80).forEach { part ->
            val o = http.form("$BASE/cache/check", listOf("apikey" to key) + part.map { "items[]" to it }).json() ?: return@forEach
            val res = o.optJSONArray("response") ?: return@forEach
            part.forEachIndexed { i, h -> if (res.optBoolean(i, false)) out += h.lowercase() }
        }
        return out
    }

    fun resolve(key: String, magnet: String, spec: TorrentSpec, season: Int, episode: Int): DebridResolve {
        val r = http.form("$BASE/transfer/directdl", listOf("apikey" to key, "src" to magnet))
        if (r.code == 401 || r.code == 403) return DebridResolve.Failed("Chave do Premiumize não vale mais. Conecte de novo.")
        val o = r.json() ?: return DebridResolve.Failed("Premiumize não respondeu (${r.code})")
        if (o.optString("status") != "success") {
            val msg = (o.optString("message") + " " + o.optString("code")).lowercase()
            return if ("cache" in msg || "not found" in msg) DebridResolve.NotCached else DebridResolve.Failed("Premiumize recusou o torrent")
        }
        val arr = o.optJSONArray("content") ?: JSONArray()
        // A ordem do directdl não é a do torrent, então o fileIdx do addon não serve aqui.
        val files = (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i) ?: return@mapNotNull null
            val link = f.optString("link").ifBlank { f.optString("stream_link") }
            if (link.isBlank()) null else RemoteFile(i.toString(), TorrentFile(i, f.optString("path"), f.optLong("size")), link)
        }
        val chosen = pickRemote(files, spec, indexReliable = false, season, episode) ?: return DebridResolve.NotCached
        return DebridResolve.Ok(chosen.link, DebridProvider.PREMIUMIZE)
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
