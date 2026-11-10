package com.aotv.app.data.accounts

import android.content.Context
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

/** Sessão da conta Stremio guardada no aparelho. */
data class StremioSession(val authKey: String, val email: String)

/** Código para entrar pelo site do Stremio (link.stremio.com). */
data class StremioLink(val code: String, val url: String)

/**
 * Conta Stremio. Entra com e-mail e senha ou pelo código do link.stremio.com, lê os addons e a
 * biblioteca da conta e pode mandar a lista de addons do AOTV de volta.
 */
@Singleton
class StremioAccountService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("aotv_accounts", Context.MODE_PRIVATE)
    private val client = com.aotv.app.data.remote.AppDns.mediaClient.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    private val _session = MutableStateFlow(
        prefs.getString("stremio_auth", null)?.takeIf { it.isNotBlank() }?.let { StremioSession(it, prefs.getString("stremio_email", "").orEmpty()) }
    )
    val session: StateFlow<StremioSession?> = _session.asStateFlow()

    suspend fun login(email: String, password: String): Result<StremioSession> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("type", "Login").put("email", email.trim()).put("password", password).put("facebook", false)
        val r = api("login", body).getOrElse { return@withContext Result.failure(it) }
        val key = (r as? JSONObject)?.optString("authKey").orEmpty()
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("O Stremio não devolveu a sessão"))
        val s = StremioSession(key, (r as JSONObject).optJSONObject("user")?.optString("email")?.ifBlank { null } ?: email.trim())
        save(s)
        Result.success(s)
    }

    suspend fun linkStart(): Result<StremioLink> = withContext(Dispatchers.IO) {
        val o = get("https://link.stremio.com/api/v2/create?type=Create")?.optJSONObject("result")
            ?: return@withContext Result.failure(IllegalStateException("O Stremio não deu o código agora"))
        val code = o.optString("code").ifBlank { return@withContext Result.failure(IllegalStateException("O Stremio não deu o código agora")) }
        Result.success(StremioLink(code, o.optString("link").ifBlank { "https://link.stremio.com/$code" }))
    }

    /** Uma consulta. null enquanto a pessoa não confirmou no site. */
    suspend fun linkPoll(link: StremioLink): StremioSession? = withContext(Dispatchers.IO) {
        val key = get("https://link.stremio.com/api/v2/read?type=Read&code=${link.code}")?.optJSONObject("result")?.optString("authKey").orEmpty()
        if (key.isBlank()) return@withContext null
        // O e-mail vem da própria API com a sessão nova.
        val email = api("getUser", JSONObject().put("type", "GetUser").put("authKey", key)).getOrNull()
            ?.let { (it as? JSONObject)?.optString("email") }.orEmpty()
        StremioSession(key, email).also { save(it) }
    }

    /** Addons da conta, na ordem do Stremio. */
    suspend fun addons(): Result<List<RemoteAddon>> = withContext(Dispatchers.IO) {
        val s = _session.value ?: return@withContext Result.failure(IllegalStateException("Entre na conta Stremio"))
        val r = api("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", s.authKey).put("update", true))
            .getOrElse { return@withContext Result.failure(it) }
        val arr = (r as? JSONObject)?.optJSONArray("addons") ?: JSONArray()
        Result.success((0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::remoteAddon) })
    }

    /** Biblioteca da conta (Minha Lista e o que foi assistido). */
    suspend fun library(): Result<Pair<List<RemoteLibraryItem>, List<RemoteProgress>>> = withContext(Dispatchers.IO) {
        val s = _session.value ?: return@withContext Result.failure(IllegalStateException("Entre na conta Stremio"))
        val r = api("datastoreGet", JSONObject().put("authKey", s.authKey).put("collection", "libraryItem").put("ids", JSONArray()).put("all", true))
            .getOrElse { return@withContext Result.failure(it) }
        Result.success(parseLibrary(r as? JSONArray ?: JSONArray()))
    }

    /**
     * Manda os addons do AOTV para a conta, sem tirar os oficiais que o Stremio protege. Os da
     * conta que o AOTV não tem ficam também (nada some do Stremio por causa daqui).
     */
    suspend fun pushAddons(urls: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        val s = _session.value ?: return@withContext Result.failure(IllegalStateException("Entre na conta Stremio"))
        val cur = api("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", s.authKey).put("update", true))
            .getOrElse { return@withContext Result.failure(it) } as? JSONObject
        val existing = cur?.optJSONArray("addons") ?: JSONArray()
        val have = (0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("transportUrl") }.toSet()
        val out = JSONArray()
        (0 until existing.length()).forEach { out.put(existing.get(it)) }
        var added = 0
        for (u in urls) {
            if (u in have) continue
            // O Stremio guarda o manifest junto. Sem ele o addon aparece quebrado no app deles.
            val manifest = get(u) ?: continue
            out.put(JSONObject().put("transportUrl", u).put("transportName", "http").put("manifest", manifest).put("flags", JSONObject()))
            added++
        }
        if (added == 0) return@withContext Result.success(0)
        api("addonCollectionSet", JSONObject().put("type", "AddonCollectionSet").put("authKey", s.authKey).put("addons", out))
            .map { added }
    }

    fun logout() {
        val key = _session.value?.authKey
        prefs.edit().remove("stremio_auth").remove("stremio_email").apply()
        _session.value = null
        // Avisa o Stremio para fechar a sessão. Falha de rede aqui não importa.
        if (key != null) Thread {
            runCatching { call("https://api.strem.io/api/logout", JSONObject().put("type", "Logout").put("authKey", key)) }
        }.start()
    }

    private fun save(s: StremioSession) {
        prefs.edit().putString("stremio_auth", s.authKey).putString("stremio_email", s.email).apply()
        _session.value = s
    }

    /** Chamada da API. Devolve o "result" ou a mensagem de erro do Stremio. */
    private fun api(method: String, body: JSONObject): Result<Any> {
        val o = call("https://api.strem.io/api/$method", body) ?: return Result.failure(IllegalStateException("Sem resposta do Stremio"))
        o.optJSONObject("error")?.let { e ->
            val msg = e.optString("message")
            // Sessão vencida ou encerrada no site. Sai daqui também para pedir login de novo.
            if (e.optInt("code") == 1 && method != "login") { prefs.edit().remove("stremio_auth").apply(); _session.value = null }
            return Result.failure(IllegalStateException(stremioMessage(msg)))
        }
        return o.opt("result")?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Resposta vazia do Stremio"))
    }

    private fun call(url: String, body: JSONObject): JSONObject? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "AOTV")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { JSONObject(it.body?.string().orEmpty()) }
    }.getOrNull()

    private fun get(url: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "AOTV").build()).execute().use {
            if (it.isSuccessful) JSONObject(it.body?.string().orEmpty()) else null
        }
    }.getOrNull()

    companion object {
        private fun stremioMessage(msg: String): String = when {
            msg.contains("password", true) || msg.contains("user not found", true) -> "E-mail ou senha não conferem"
            msg.contains("session", true) -> "A sessão do Stremio acabou. Entre de novo."
            msg.isBlank() -> "O Stremio recusou o pedido"
            else -> msg
        }

        /** Addon da conta. Fica de fora o que só roda dentro do Stremio (localhost) ou sem fonte e catálogo. */
        internal fun remoteAddon(o: JSONObject): RemoteAddon? {
            val url = o.optString("transportUrl").ifBlank { return null }
            val m = o.optJSONObject("manifest")
            val name = m?.optString("name").orEmpty().ifBlank { url }
            val host = Regex("^https?://([^/:]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase().orEmpty()
            val resources = buildSet {
                val r = m?.optJSONArray("resources") ?: JSONArray()
                for (i in 0 until r.length()) {
                    val v = r.opt(i)
                    add(if (v is JSONObject) v.optString("name") else v.toString())
                }
            }
            val local = host == "localhost" || host.startsWith("127.") || host.isBlank()
            return RemoteAddon(url, name, !local && url.startsWith("http") && ("stream" in resources || "catalog" in resources))
        }

        /** Itens da biblioteca e progresso, como o app do Stremio grava. */
        internal fun parseLibrary(arr: JSONArray): Pair<List<RemoteLibraryItem>, List<RemoteProgress>> {
            val list = mutableListOf<RemoteLibraryItem>()
            val prog = mutableListOf<RemoteProgress>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("_id")
                if (id.isBlank()) continue
                val type = o.optString("type")
                if (type != "movie" && type != "series") continue
                val series = type == "series"
                val name = o.optString("name")
                val poster = o.optString("poster")
                if (o.optBoolean("removed") && !o.optBoolean("temp")) continue
                if (!o.optBoolean("removed") && !o.optBoolean("temp")) {
                    list += RemoteLibraryItem(id, series, name, poster, isoMsOrZero(o.optString("_ctime")))
                }
                val st = o.optJSONObject("state") ?: continue
                val pos = st.optLong("timeOffset")
                val dur = st.optLong("duration")
                if (pos <= 0) continue
                val vid = st.optString("video_id")
                val parts = vid.split(':')
                val season = if (series) st.optInt("season").takeIf { it > 0 } ?: parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0 else 0
                val episode = if (series) st.optInt("episode").takeIf { it > 0 } ?: parts.lastOrNull()?.toIntOrNull() ?: 0 else 0
                prog += RemoteProgress(id, series, name, poster, season, episode, pos, dur, isoMsOrZero(st.optString("lastWatched")).takeIf { it > 0 } ?: isoMsOrZero(o.optString("_mtime")))
            }
            return list to prog
        }

        private fun isoMsOrZero(s: String?) = com.aotv.app.data.debrid.isoMs(s)
    }
}
