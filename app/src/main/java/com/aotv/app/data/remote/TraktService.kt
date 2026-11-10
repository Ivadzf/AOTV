package com.aotv.app.data.remote

import com.aotv.app.BuildConfig
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trakt (porte do trakt.js do webOS). Junta o histórico do celular e da TV. Cada perfil tem a
 * própria conta. Login por código, a pessoa abre trakt.tv/activate e digita o código mostrado.
 *
 * Sincroniza a Minha Lista nos dois sentidos, o ponto onde parou vira Continuar assistindo, e os
 * episódios vistos do Trakt marcam os daqui. O player avisa quando toca, pausa e fecha.
 * Fica desligado (e escondido) enquanto TRAKT_CLIENT_ID e TRAKT_CLIENT_SECRET estiverem vazios.
 */
@Singleton
class TraktService @Inject constructor(
    client: OkHttpClient,
    private val prefs: PlatformPrefs,
    private val library: LibraryRepository,
    private val tmdb: TmdbApiService,
) {
    private val http = client.newBuilder().cache(null).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()

    val configured get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    private data class Session(val access: String, val refresh: String, val expiresAt: Long, val user: String)

    private fun session(): Session? = runCatching {
        val o = JSONObject(prefs.traktSession)
        Session(o.getString("a"), o.optString("r"), o.optLong("e"), o.optString("u"))
    }.getOrNull()?.takeIf { it.access.isNotBlank() }

    private fun saveSession(s: Session?) {
        prefs.traktSession = s?.let { JSONObject().put("a", it.access).put("r", it.refresh).put("e", it.expiresAt).put("u", it.user).toString() } ?: ""
    }

    /** Nome da conta conectada no perfil ativo ("" quando o Trakt não deu o nome). null sem login. */
    fun user(): String? = session()?.user

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private class Resp(val code: Int, val body: String)

    private suspend fun raw(method: String, path: String, body: JSONObject?, token: String?): Resp = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(API + path)
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", CLIENT_ID)
        if (token != null) rb.header("Authorization", "Bearer $token")
        rb.method(method, body?.toString()?.toRequestBody(JSON))
        http.newCall(rb.build()).execute().use { Resp(it.code, it.body?.string().orEmpty()) }
    }

    /** Chamada autenticada. 401 desconecta. Devolve o corpo ou lança erro. */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): String {
        refreshIfNeeded()
        val r = raw(method, path, body, session()?.access)
        if (r.code == 401) { saveSession(null); error("trakt 401") }
        if (r.code !in 200..204) error("trakt ${r.code}")
        return r.body
    }

    private suspend fun refreshIfNeeded() {
        val s = session() ?: return
        if (s.refresh.isBlank() || System.currentTimeMillis() < s.expiresAt - DAY_MS) return
        val r = runCatching {
            raw("POST", "/oauth/token", JSONObject()
                .put("refresh_token", s.refresh).put("client_id", CLIENT_ID).put("client_secret", CLIENT_SECRET)
                .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob").put("grant_type", "refresh_token"), null)
        }.getOrNull() ?: return
        if (r.code == 200) saveToken(JSONObject(r.body), s.user)
    }

    private fun saveToken(t: JSONObject, user: String) = saveSession(
        Session(t.getString("access_token"), t.optString("refresh_token"),
            System.currentTimeMillis() + t.optLong("expires_in", 7_776_000L) * 1000, user),
    )

    // ── Login por código ─────────────────────────────────────────────────────

    data class DeviceCode(val userCode: String, val url: String, val deviceCode: String, val intervalSec: Int, val expiresAt: Long)

    /** Pede o código. null quando o Trakt não respondeu. */
    suspend fun startLogin(): DeviceCode? = runCatching {
        val r = raw("POST", "/oauth/device/code", JSONObject().put("client_id", CLIENT_ID), null)
        if (r.code != 200) return null
        val d = JSONObject(r.body)
        DeviceCode(
            d.getString("user_code"), d.optString("verification_url", "https://trakt.tv/activate"), d.getString("device_code"),
            maxOf(5, d.optInt("interval", 5)), System.currentTimeMillis() + d.optLong("expires_in", 600) * 1000,
        )
    }.getOrNull()

    /**
     * Espera a pessoa confirmar no outro aparelho. Devolve o nome da conta, ou null se expirar
     * ou for negado. Cancelar a coroutine cancela a espera.
     */
    suspend fun waitLogin(c: DeviceCode): String? {
        var every = c.intervalSec * 1000L
        while (System.currentTimeMillis() < c.expiresAt) {
            delay(every)
            val r = runCatching {
                raw("POST", "/oauth/device/token", JSONObject().put("code", c.deviceCode)
                    .put("client_id", CLIENT_ID).put("client_secret", CLIENT_SECRET), null)
            }.getOrNull() ?: continue
            when (r.code) {
                200 -> {
                    saveToken(JSONObject(r.body), "")
                    val name = runCatching {
                        JSONObject(call("GET", "/users/settings")).optJSONObject("user")
                            ?.let { it.optString("name").ifBlank { it.optString("username") } }
                    }.getOrNull().orEmpty()
                    session()?.let { saveSession(it.copy(user = name)) }
                    scope.launch { sync() }
                    return name
                }
                400 -> Unit                 // ainda esperando a pessoa
                429 -> every += 1000        // devagar
                else -> return null         // negado ou expirado
            }
        }
        return null
    }

    fun logout() = saveSession(null)

    // ── Sincronia ────────────────────────────────────────────────────────────

    /** Sincronia completa. true quando tudo deu certo. Uma por vez. */
    suspend fun sync(): Boolean {
        if (!configured || session() == null) return false
        // Perfil capturado UMA VEZ aqui. A sincronia demora (chama o TMDB item por item) e, se o
        // usuário trocar de perfil no meio, o resto não pode ir parar no perfil novo.
        val profileId = prefs.profileId.value
        return syncLock.withLock {
            listOf(
                runCatching { pullWatchlist(profileId); pushWatchlist(profileId) },
                runCatching { pullPlayback(profileId) },
                runCatching { pullWatched(profileId) },
            ).all { it.isSuccess }
        }
    }

    /** Sincroniza sem esperar (abertura da Início). */
    fun syncInBackground() { if (configured && session() != null) scope.launch { sync() } }

    private suspend fun pullWatchlist(profileId: String) {
        val have = library.watchlist(profileId).first().mapTo(HashSet()) { "${it.type}:${it.tmdbId}" }
        val list = JSONArray(call("GET", "/sync/watchlist?extended=full"))
        for (i in list.length() - 1 downTo 0) {
            val e = list.getJSONObject(i)
            val m = e.optJSONObject("movie") ?: e.optJSONObject("show") ?: continue
            val id = m.optJSONObject("ids")?.optInt("tmdb") ?: 0
            val type = if (e.has("movie")) MediaType.MOVIE else MediaType.SERIES
            if (id <= 0 || "${type.name}:$id" in have) continue
            library.saveToList(MovieItem(id, id.toString(), m.optString("title"), "", "", "", m.optInt("year").takeIf { it > 0 }?.toString().orEmpty(), 0.0, type = type), profileId)
        }
    }

    private suspend fun pushWatchlist(profileId: String) {
        val movies = JSONArray(); val shows = JSONArray()
        library.watchlist(profileId).first().forEach { w ->
            val o = JSONObject().put("ids", JSONObject().put("tmdb", w.tmdbId))
            if (w.type == MediaType.MOVIE.name) movies.put(o) else shows.put(o)
        }
        if (movies.length() + shows.length() == 0) return
        call("POST", "/sync/watchlist", JSONObject().put("movies", movies).put("shows", shows))
    }

    /** Ponto onde parou. Só entra o que é mais recente que o histórico daqui. */
    private suspend fun pullPlayback(profileId: String) {
        val list = JSONArray(call("GET", "/sync/playback"))
        val key = prefs.tmdbApiKey
        for (i in 0 until minOf(20, list.length())) {
            val p = list.getJSONObject(i)
            val isEp = p.optString("type") == "episode"
            val m = (if (isEp) p.optJSONObject("show") else p.optJSONObject("movie")) ?: continue
            val id = m.optJSONObject("ids")?.optInt("tmdb") ?: 0
            if (id <= 0) continue
            val type = if (isEp) MediaType.SERIES.name else MediaType.MOVIE.name
            val at = runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(p.optString("paused_at").take(19))!!.time
            }.getOrDefault(0L)
            val local = library.getPlayback(id, type, profileId)
            if (local != null && local.lastWatchedAt >= at) continue
            val runtimeMin = runCatching {
                if (isEp) 45 else tmdb.getMovieDetail(id, append = "", apiKey = key).runtime ?: 0
            }.getOrDefault(0)
            val dur = runtimeMin * 60_000L
            if (dur <= 0) continue
            val ep = p.optJSONObject("episode")
            library.saveProgress(
                tmdbId = id, type = type, title = m.optString("title"), posterUrl = "",
                positionMs = (dur * p.optDouble("progress", 0.0) / 100).toLong(), durationMs = dur,
                lastEpisode = if (isEp && ep != null) "T${ep.optInt("season")}:E${ep.optInt("number")}" else "",
                lastSourceUrl = "",
                profileId = profileId,
            )
        }
    }

    private suspend fun pullWatched(profileId: String) {
        val list = JSONArray(call("GET", "/sync/watched/shows"))
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            val id = e.optJSONObject("show")?.optJSONObject("ids")?.optInt("tmdb") ?: 0
            if (id <= 0) continue
            val seasons = e.optJSONArray("seasons") ?: continue
            for (s in 0 until seasons.length()) {
                val so = seasons.getJSONObject(s)
                val eps = so.optJSONArray("episodes") ?: continue
                for (k in 0 until eps.length()) {
                    val n = eps.getJSONObject(k).optInt("number")
                    if (!library.isEpisodeWatched(id, so.optInt("number"), n, profileId)) library.markEpisodeWatched(id, so.optInt("number"), n, profileId)
                }
            }
        }
    }

    // ── Avisos do player e da Minha Lista ─────────────────────────────────────

    /** [action] é start, pause ou stop (stop com 80% ou mais conta como visto no Trakt). */
    fun scrobble(action: String, tmdbId: Int, type: String, season: Int, episode: Int, progress: Double) {
        if (!configured || session() == null || tmdbId <= 0) return
        val body = JSONObject().put("progress", progress.coerceIn(0.0, 100.0))
        val ids = JSONObject().put("ids", JSONObject().put("tmdb", tmdbId))
        if (type == MediaType.SERIES.name && season > 0 && episode > 0) {
            body.put("show", ids).put("episode", JSONObject().put("season", season).put("number", episode))
        } else body.put("movie", ids)
        scope.launch { runCatching { call("POST", "/scrobble/$action", body) } }
    }

    fun watchlistChanged(tmdbId: Int, type: String, added: Boolean) {
        if (!configured || session() == null || tmdbId <= 0) return
        val one = JSONArray().put(JSONObject().put("ids", JSONObject().put("tmdb", tmdbId)))
        val body = JSONObject().put(if (type == MediaType.MOVIE.name) "movies" else "shows", one)
        scope.launch { runCatching { call("POST", if (added) "/sync/watchlist" else "/sync/watchlist/remove", body) } }
    }

    private companion object {
        // App registrado em trakt.tv/oauth/applications. Vêm do local.properties (fora do git).
        val CLIENT_ID: String = BuildConfig.TRAKT_CLIENT_ID
        val CLIENT_SECRET: String = BuildConfig.TRAKT_CLIENT_SECRET
        const val API = "https://api.trakt.tv"
        const val DAY_MS = 86_400_000L
        val JSON = "application/json".toMediaType()
    }
}
