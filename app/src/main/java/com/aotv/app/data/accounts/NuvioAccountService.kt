package com.aotv.app.data.accounts

import android.content.Context
import com.aotv.app.data.local.PROFILE_COLORS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Sessão da conta Nuvio. [server] é o endereço do servidor e [key] a chave pública dele. */
data class NuvioSession(
    val server: String,
    val key: String,
    val access: String,
    val refresh: String,
    val expiresAt: Long,
    val email: String,
)

/** Perfil da conta Nuvio. [index] é o número que as consultas usam (1 é o principal). [avatarUrl] é a foto do perfil quando o servidor a devolve (vazio = sem foto — o app mantém o avatar local). [avatarId] é o id do avatar no catálogo do Nuvio (ex. "avatar_lalo") — NÃO é o id do catálogo local de presets do app; a importação resolve esse id para URL e grava a URL em [avatarUrl]. [avatarColorHex] é a cor do perfil no Nuvio ("#RRGGBB"), mapeada para o índice mais próximo em [PROFILE_COLORS] na importação. [kids] é o marcador infantil do perfil, quando o servidor o devolve. [usesPrimaryPlugins] espelha [usesPrimaryAddons] para os repositórios de plugins: ligado, o perfil usa os plugins do principal. [pluginRepos] são os repositórios que o payload do perfil trouxe (fallback quando a tabela de plugins vem vazia). */
data class NuvioProfile(val index: Int, val name: String, val usesPrimaryAddons: Boolean, val avatarUrl: String = "", val kids: Boolean = false, val usesPrimaryPlugins: Boolean = false, val pluginRepos: List<String> = emptyList(), val avatarId: String = "", val avatarColorHex: String = "")

/**
 * Literal "null" gravado como texto vira vazio. O servidor já devolveu a
 * string "null" no lugar de foto/cor ausente e ela ficou persistida nos
 * perfis; sem isso a reimportação preservaria o "null" (não é branco).
 */
fun sanitizeAvatarValue(raw: String): String {
    val v = raw.trim()
    return if (v.equals("null", ignoreCase = true)) "" else v
}

/** URL pública da imagem de um avatar de catálogo pelo caminho guardado no servidor. "" quando não há caminho. */
fun avatarCatalogPublicUrl(storagePath: String): String {
    val p = sanitizeAvatarValue(storagePath).trimStart('/')
    return if (p.isBlank()) "" else "https://api.nuvio.tv/storage/v1/object/public/avatars/$p"
}

/**
 * Índice em [PROFILE_COLORS] mais próximo do hex ("#RRGGBB" ou "RRGGBB",
 * aceita o formato curto "#RGB"). Distância euclidiana em RGB. -1 quando o
 * hex está em branco ou é inválido — o chamador mantém a cor atual.
 */
fun closestProfileColorIndex(hex: String): Int {
    val h = sanitizeAvatarValue(hex).removePrefix("#")
    val expanded = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        else -> return -1
    }
    val rgb = expanded.toLongOrNull(16) ?: return -1
    val r = ((rgb shr 16) and 0xFF).toInt()
    val g = ((rgb shr 8) and 0xFF).toInt()
    val b = (rgb and 0xFF).toInt()
    var best = -1
    var bestDist = Long.MAX_VALUE
    PROFILE_COLORS.forEachIndexed { i, c ->
        val cr = ((c shr 16) and 0xFF).toInt()
        val cg = ((c shr 8) and 0xFF).toInt()
        val cb = (c and 0xFF).toInt()
        val dr = (r - cr).toLong()
        val dg = (g - cg).toLong()
        val db = (b - cb).toLong()
        val d = dr * dr + dg * dg + db * db
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

/** Repositório de plugins do Nuvio. [url] é a URL do repositório como veio do servidor (só trim — plugins são repositórios, não manifests, então não ganha "/manifest.json"). */
data class RemotePlugin(val url: String, val name: String, val enabled: Boolean = true)

/** Código para entrar pelo site do Nuvio (nuvio.tv/link). */
data class NuvioDeviceCode(val deviceCode: String, val userCode: String, val url: String, val intervalSec: Int, val nonce: String, val server: String, val key: String)

/**
 * Conta Nuvio. O servidor oficial publica o próprio endereço e a chave pública em
 * /.well-known/nuvio, então nada fica escondido no app. Só leitura dos dados da pessoa.
 */
@Singleton
class NuvioAccountService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("aotv_accounts", Context.MODE_PRIVATE)
    private val client = com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<NuvioSession?> = _session.asStateFlow()

    /** Endereço e chave pública do servidor. Aceita "api.nuvio.tv" ou a URL de um servidor próprio. */
    suspend fun discover(input: String = OFFICIAL): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val raw = input.trim().ifBlank { OFFICIAL }
        val base = (if ("://" in raw) raw else "https://$raw").trimEnd('/').removeSuffix("/.well-known/nuvio")
        if (!base.startsWith("https://") && !isLocal(base)) return@withContext Result.failure(IllegalArgumentException("Use um endereço https"))
        val o = getJson("$base/.well-known/nuvio") ?: return@withContext Result.failure(IllegalStateException("Esse endereço não é um servidor Nuvio"))
        if (!o.optString("service").equals("nuvio", true)) return@withContext Result.failure(IllegalStateException("Esse endereço não é um servidor Nuvio"))
        val backend = o.optString("backend_url").trimEnd('/').ifBlank { base }
        val key = o.optString("publishable_key")
        if (key.isBlank() || !o.optJSONObject("capabilities")?.optBoolean("email_password_auth", false).let { it == true }) {
            return@withContext Result.failure(IllegalStateException("Esse servidor Nuvio não aceita login por e-mail"))
        }
        Result.success(backend to key)
    }

    suspend fun login(email: String, password: String, server: String = OFFICIAL): Result<NuvioSession> = withContext(Dispatchers.IO) {
        val (backend, key) = discover(server).getOrElse { return@withContext Result.failure(it) }
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val r = post("$backend/auth/v1/token?grant_type=password", body, key, null)
        val o = r.second ?: return@withContext Result.failure(IllegalStateException("Sem resposta do Nuvio"))
        val access = o.optString("access_token")
        if (r.first !in 200..299 || access.isBlank()) {
            val msg = o.optString("error_description").ifBlank { o.optString("msg").ifBlank { o.optString("message") } }
            return@withContext Result.failure(IllegalStateException(if (msg.contains("invalid", true)) "E-mail ou senha não conferem" else msg.ifBlank { "O Nuvio recusou o login" }))
        }
        val s = sessionOf(backend, key, o, email.trim())
        save(s)
        Result.success(s)
    }

    suspend fun deviceStart(server: String = OFFICIAL): Result<NuvioDeviceCode> = withContext(Dispatchers.IO) {
        val (backend, key) = discover(server).getOrElse { return@withContext Result.failure(it) }
        val nonce = java.util.UUID.randomUUID().toString()
        val redirect = if (backend.contains("nuvio.tv", true)) "https://nuvio.tv/link" else "$backend/link"
        val params = JSONObject().put("p_device_nonce", nonce).put("p_redirect_base_url", redirect)
            .put("p_device_name", "AOTV").put("p_device_type", "mobile")
        val r = post("$backend/rest/v1/rpc/start_device_login_session", params, key, null)
        val row = firstRow(r.second, r.third) ?: return@withContext Result.failure(IllegalStateException("O Nuvio não deu o código agora"))
        val code = row.optString("device_code").ifBlank { return@withContext Result.failure(IllegalStateException("O Nuvio não deu o código agora")) }
        Result.success(
            NuvioDeviceCode(
                code, row.optString("user_code"), row.optString("verification_uri_complete").ifBlank { redirect },
                row.optInt("poll_interval_seconds", 3).coerceIn(2, 10), nonce, backend, key,
            )
        )
    }

    /** Uma consulta. null enquanto pendente. Falha quando venceu ou foi recusado. */
    suspend fun devicePoll(d: NuvioDeviceCode): Result<NuvioSession?> = withContext(Dispatchers.IO) {
        val r = post("${d.server}/rest/v1/rpc/poll_tv_login_session", JSONObject().put("p_code", d.deviceCode).put("p_device_nonce", d.nonce), d.key, null)
        val row = firstRow(r.second, r.third) ?: return@withContext Result.success(null)
        when (row.optString("status")) {
            "pending" -> return@withContext Result.success(null)
            "approved" -> Unit
            else -> return@withContext Result.failure(IllegalStateException("O código venceu. Peça outro."))
        }
        val ex = post("${d.server}/functions/v1/tv-logins-exchange", JSONObject().put("code", d.deviceCode).put("device_nonce", d.nonce), d.key, null)
        val o = ex.second ?: return@withContext Result.failure(IllegalStateException("O Nuvio não entregou a sessão"))
        if (o.optString("access_token").isBlank()) return@withContext Result.failure(IllegalStateException("O Nuvio não entregou a sessão"))
        val email = o.optJSONObject("user")?.optString("email")
            ?: getJson("${d.server}/auth/v1/user", d.key, o.optString("access_token"))?.optString("email").orEmpty()
        val s = sessionOf(d.server, d.key, o, email)
        save(s)
        Result.success(s)
    }

    fun logout() {
        prefs.edit().remove("nuvio_session").apply()
        _session.value = null
    }

    suspend fun profiles(): Result<List<NuvioProfile>> = rpcRows("sync_pull_profiles", JSONObject()).map { rows ->
        rows.map {
            NuvioProfile(
                it.optInt("profile_index", 1), it.optString("name").ifBlank { "Perfil" }, it.optBoolean("uses_primary_addons"),
                // Foto do perfil: o servidor nem sempre devolve — tenta os nomes
                // conhecidos e fica vazio quando não há (o app mantém o avatar local).
                // Avatar de catálogo vem como avatar_id (avatar_url null) e é
                // resolvido para URL na importação, não aqui.
                sanitizeAvatarValue(it.optString("avatar_url").ifBlank { it.optString("avatar").ifBlank { it.optString("photo_url").ifBlank { it.optString("image_url") } } }),
                it.optBoolean("kids", it.optBoolean("is_kids", it.optBoolean("is_child", false))),
                it.optBoolean("uses_primary_plugins", it.optBoolean("usesPrimaryPlugins", false)),
                parsePluginRepos(it),
                avatarId = sanitizeAvatarValue(it.optString("avatar_id").ifBlank { it.optString("avatarId") }),
                avatarColorHex = sanitizeAvatarValue(it.optString("avatar_color_hex").ifBlank { it.optString("avatar_color").ifBlank { it.optString("avatarColorHex") } }),
            )
        }.sortedBy { it.index }
    }

    /**
     * Catálogo de avatares do Nuvio (RPC `get_avatar_catalog`, SEM token de
     * acesso: só a publishable key da sessão, como no login por dispositivo).
     * Devolve id do catálogo -> URL pública da imagem. Cada entrada traz o
     * `storage_path`; a URL é `https://api.nuvio.tv/storage/v1/object/public/avatars/<storage_path>`.
     * Falha (sem rede, sem sessão, formato desconhecido) devolve mapa vazio —
     * a importação segue com o avatar local. Chamado 1x por importação; o
     * mapa é montado aqui, não a cada perfil. Os nomes dos campos são lidos
     * com tolerância (`id` ou `avatar_id`; `storage_path` ou `path`; `url`
     * direta quando houver) porque o formato exato do catálogo não foi
     * confirmado além do `storage_path`.
     */
    suspend fun avatarCatalog(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = fresh() ?: return@runCatching emptyMap<String, String>()
            val r = post("${s.server}/rest/v1/rpc/get_avatar_catalog", JSONObject(), s.key, null)
            if (r.first !in 200..299) return@runCatching emptyMap<String, String>()
            val arr = catalogArray(r.third) ?: return@runCatching emptyMap<String, String>()
            val out = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = sanitizeAvatarValue(o.optString("id").ifBlank { o.optString("avatar_id") })
                if (id.isBlank()) continue
                val direct = sanitizeAvatarValue(o.optString("url").ifBlank { o.optString("image_url") })
                val url = if (direct.startsWith("http")) direct else avatarCatalogPublicUrl(o.optString("storage_path").ifBlank { o.optString("path") })
                if (url.isNotBlank()) out[id] = url
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** Array do catálogo: o RPC devolve um array direto; aceita um objeto com o array dentro (`data`/`avatars`/`catalog`/`items`). */
    private fun catalogArray(raw: String): JSONArray? {
        val direct = runCatching { JSONArray(raw) }.getOrNull()
        if (direct != null) return direct
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        for (key in listOf("data", "avatars", "catalog", "items")) {
            val arr = o.optJSONArray(key)
            if (arr != null) return arr
        }
        return null
    }

    /** Repositórios que o payload do perfil trouxe (`pluginRepos`/`plugin_repos`): array de URLs ou de objetos com `url`/`repo_url`/`repo`. */
    private fun parsePluginRepos(o: JSONObject): List<String> {
        val arr = o.optJSONArray("pluginRepos") ?: o.optJSONArray("plugin_repos") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            when (val v = arr.opt(i)) {
                is String -> v.trim().takeIf { it.isNotBlank() }
                is JSONObject -> v.optString("url").ifBlank { v.optString("repo_url").ifBlank { v.optString("repo") } }.trim().takeIf { it.isNotBlank() }
                else -> null
            }
        }.distinct()
    }

    /** Addons do perfil. Perfil que usa os addons do principal lê os do perfil 1, como no Nuvio. */
    suspend fun addons(profile: NuvioProfile): Result<List<RemoteAddon>> = withContext(Dispatchers.IO) {
        val idx = if (profile.index != 1 && profile.usesPrimaryAddons) 1 else profile.index
        val s = fresh() ?: return@withContext Result.failure(IllegalStateException("Entre na conta Nuvio"))
        val r = getRaw("${s.server}/rest/v1/addons?select=*&profile_id=eq.$idx&order=sort_order.asc", s.key, s.access)
        val arr = runCatching { JSONArray(r.second) }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException(errorOf(r.second, r.first)))
        Result.success(
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                if (!o.optBoolean("enabled", true)) return@mapNotNull null
                val raw = o.optString("url").trim().ifBlank { return@mapNotNull null }
                val url = if (raw.endsWith("/manifest.json")) raw else raw.trimEnd('/') + "/manifest.json"
                val host = Regex("^https?://([^/:]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase().orEmpty()
                RemoteAddon(url, o.optString("name").ifBlank { host }, url.startsWith("http") && host.isNotBlank() && host != "localhost" && !host.startsWith("127."))
            }.distinctBy { it.url }
        )
    }

    /**
     * Repositórios de plugins do perfil. Perfil que usa os plugins do principal lê os do perfil 1, como no Nuvio.
     * Sem `order=sort_order` (a coluna não está confirmada na tabela de plugins — sem ela o PostgREST
     * recusaria a consulta; a ordem vem do sort por URL aqui). A URL vai como veio do servidor, só trim:
     * plugins são repositórios, não manifests. Quando a tabela vem vazia, vale o `pluginRepos` do
     * payload do perfil ([NuvioProfile.pluginRepos]) — a tabela continua sendo a fonte principal.
     */
    suspend fun plugins(profile: NuvioProfile): Result<List<RemotePlugin>> = withContext(Dispatchers.IO) {
        val idx = if (profile.index != 1 && profile.usesPrimaryPlugins) 1 else profile.index
        val s = fresh() ?: return@withContext Result.failure(IllegalStateException("Entre na conta Nuvio"))
        val r = getRaw("${s.server}/rest/v1/plugins?select=*&profile_id=eq.$idx", s.key, s.access)
        val arr = runCatching { JSONArray(r.second) }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException(errorOf(r.second, r.first)))
        val rows = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!o.optBoolean("enabled", true)) return@mapNotNull null
            val url = o.optString("url").trim()
            if (url.isBlank()) return@mapNotNull null
            RemotePlugin(url, o.optString("name").ifBlank { url }, true)
        }.distinctBy { it.url.lowercase() }.sortedBy { it.url.lowercase() }
        if (rows.isNotEmpty()) return@withContext Result.success(rows)
        Result.success(profile.pluginRepos.map { RemotePlugin(it, it, true) }.distinctBy { it.url.lowercase() }.sortedBy { it.url.lowercase() })
    }

    suspend fun library(profile: NuvioProfile): Result<List<RemoteLibraryItem>> {
        val out = mutableListOf<RemoteLibraryItem>()
        var offset = 0
        while (offset < 5_000) {
            val rows = rpcRows("sync_pull_library", JSONObject().put("p_profile_id", profile.index).put("p_limit", 500).put("p_offset", offset))
                .getOrElse { return Result.failure(it) }
            rows.forEach { o ->
                val type = o.optString("content_type")
                if (type != "movie" && type != "series") return@forEach
                out += RemoteLibraryItem(o.optString("content_id"), type == "series", o.optString("name"), o.optString("poster"), o.optLong("added_at"))
            }
            if (rows.size < 500) break
            offset += 500
        }
        return Result.success(out)
    }

    suspend fun progress(profile: NuvioProfile): Result<List<RemoteProgress>> =
        rpcRows("sync_pull_watch_progress", JSONObject().put("p_profile_id", profile.index)).map { rows ->
            rows.mapNotNull { o ->
                val type = o.optString("content_type")
                if (type != "movie" && type != "series") return@mapNotNull null
                RemoteProgress(
                    o.optString("content_id"), type == "series", "", "",
                    o.optInt("season", 0), o.optInt("episode", 0),
                    o.optLong("position"), o.optLong("duration"), o.optLong("last_watched"),
                )
            }
        }

    /** Chaves de debrid salvas na conta ("torbox" → chave). */
    suspend fun debridKeys(profile: NuvioProfile): Result<Map<String, String>> =
        rpcRows("sync_pull_provider_credentials", JSONObject().put("p_profile_id", profile.index)).map { rows ->
            rows.mapNotNull { o ->
                val p = o.optString("provider")
                val key = o.optJSONObject("credential_json")?.optString("api_key").orEmpty()
                if (p.startsWith("debrid:") && key.isNotBlank()) p.removePrefix("debrid:") to key else null
            }.toMap()
        }

    /** Coleções do perfil (o mesmo collections_json que o Nuvio sincroniza). Null quando não tem. */
    suspend fun collectionsJson(profile: NuvioProfile): Result<String?> =
        rpcRows("sync_pull_collections", JSONObject().put("p_profile_id", profile.index)).map { rows ->
            rows.firstOrNull()?.opt("collections_json")?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() && it != "[]" }
        }

    /** Perfil principal da conta, para quem importa sem escolher perfil (tela de Coleções). */
    suspend fun mainProfile(): NuvioProfile =
        profiles().getOrNull()?.minByOrNull { it.index } ?: NuvioProfile(1, "Perfil", false)

    // ── HTTP ────────────────────────────────────────────────────────────────────

    /** Sessão válida, renovada quando falta pouco para vencer. null sem login ou sem renovação. */
    private fun fresh(): NuvioSession? {
        val s = _session.value ?: return null
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s
        val r = post("${s.server}/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refresh), s.key, null)
        val o = r.second
        if (r.first !in 200..299 || o == null || o.optString("access_token").isBlank()) {
            if (r.first in 400..499) logout()
            return null
        }
        return sessionOf(s.server, s.key, o, s.email).also { save(it) }
    }

    private suspend fun rpcRows(name: String, params: JSONObject): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        val s = fresh() ?: return@withContext Result.failure(IllegalStateException("Entre na conta Nuvio"))
        var r = post("${s.server}/rest/v1/rpc/$name", params, s.key, s.access)
        if (r.first == 401) {
            // Token recusado antes da hora. Força a renovação uma vez.
            _session.value = s.copy(expiresAt = 0)
            val again = fresh() ?: return@withContext Result.failure(IllegalStateException("A sessão do Nuvio acabou. Entre de novo."))
            r = post("${again.server}/rest/v1/rpc/$name", params, again.key, again.access)
        }
        if (r.first !in 200..299) return@withContext Result.failure(IllegalStateException(errorOf(r.third, r.first)))
        val arr = runCatching { JSONArray(r.third) }.getOrElse { runCatching { JSONArray().put(JSONObject(r.third)) }.getOrDefault(JSONArray()) }
        Result.success((0 until arr.length()).mapNotNull { arr.optJSONObject(it) })
    }

    /** POST com JSON. Devolve código, corpo como objeto (quando é objeto) e o texto cru. */
    private fun post(url: String, body: JSONObject, key: String, bearer: String?): Triple<Int, JSONObject?, String> = runCatching {
        val req = Request.Builder().url(url)
            .header("apikey", key).header("Authorization", "Bearer ${bearer ?: key}").header("User-Agent", "AOTV")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use {
            val text = it.body?.string().orEmpty()
            Triple(it.code, runCatching { JSONObject(text) }.getOrNull(), text)
        }
    }.getOrElse { Triple(-1, null, "") }

    private fun getRaw(url: String, key: String, bearer: String?): Pair<Int, String> = runCatching {
        val req = Request.Builder().url(url).header("apikey", key).header("Authorization", "Bearer ${bearer ?: key}").header("User-Agent", "AOTV").build()
        client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
    }.getOrElse { -1 to "" }

    private fun getJson(url: String, key: String? = null, bearer: String? = null): JSONObject? = runCatching {
        val b = Request.Builder().url(url).header("User-Agent", "AOTV")
        if (key != null) b.header("apikey", key).header("Authorization", "Bearer ${bearer ?: key}")
        client.newCall(b.build()).execute().use { if (it.isSuccessful) JSONObject(it.body?.string().orEmpty()) else null }
    }.getOrNull()

    private fun firstRow(obj: JSONObject?, raw: String): JSONObject? =
        runCatching { JSONArray(raw).optJSONObject(0) }.getOrNull() ?: obj?.takeIf { it.has("device_code") || it.has("status") }

    private fun errorOf(raw: String, code: Int): String {
        val o = runCatching { JSONObject(raw) }.getOrNull()
        val msg = o?.optString("message")?.ifBlank { null } ?: o?.optString("msg")?.ifBlank { null }
        return when {
            code == 401 || code == 403 -> "A sessão do Nuvio acabou. Entre de novo."
            code == -1 -> "Sem conexão com o Nuvio"
            msg != null -> msg
            else -> "O Nuvio recusou o pedido ($code)"
        }
    }

    private fun sessionOf(server: String, key: String, o: JSONObject, email: String) = NuvioSession(
        server, key, o.optString("access_token"), o.optString("refresh_token"),
        System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
        o.optJSONObject("user")?.optString("email")?.ifBlank { null } ?: email,
    )

    private fun save(s: NuvioSession) {
        prefs.edit().putString(
            "nuvio_session",
            JSONObject().put("s", s.server).put("k", s.key).put("a", s.access).put("r", s.refresh).put("x", s.expiresAt).put("e", s.email).toString(),
        ).apply()
        _session.value = s
    }

    private fun readSession(): NuvioSession? = runCatching {
        val o = JSONObject(prefs.getString("nuvio_session", null) ?: return null)
        NuvioSession(o.getString("s"), o.getString("k"), o.getString("a"), o.optString("r"), o.optLong("x"), o.optString("e"))
    }.getOrNull()

    private fun isLocal(url: String) = Regex("^http://(localhost|127\\.|10\\.|192\\.168\\.)").containsMatchIn(url)

    companion object {
        const val OFFICIAL = "https://api.nuvio.tv"
    }
}
