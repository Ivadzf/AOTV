package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

// ─── Modelos (respostas do Trakt) ─────────────────────────────────────────────
// Campos vindos do servidor são anuláveis com default: o Gson preenche o que
// falta com null/0 em vez do default do Kotlin, então o código que lê trata
// null como ausente (orEmpty/orElse) em vez de confiar no default.

data class TraktDeviceCode(
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("user_code") val userCode: String? = null,
    @SerializedName("verification_url") val verificationUrl: String? = null,
    @SerializedName("expires_in") val expiresIn: Int = 0,
    @SerializedName("interval") val interval: Int = 0,
)

data class TraktToken(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("token_type") val tokenType: String? = null,
)

data class TraktDeviceTokenRequest(
    @SerializedName("code") val code: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String? = null,
    @SerializedName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code",
)

data class TraktIds(
    @SerializedName("trakt") val trakt: Int? = null,
    @SerializedName("tmdb") val tmdb: Int? = null,
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("tvdb") val tvdb: Int? = null,
)

data class TraktScrobblePayload(
    @SerializedName("movie") val movie: TraktMovieRef? = null,
    @SerializedName("show") val show: TraktShowRef? = null,
    @SerializedName("episode") val episode: TraktEpisodeRef? = null,
    @SerializedName("progress") val progress: Double,
)

data class TraktMovieRef(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds,
)

data class TraktShowRef(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds,
)

data class TraktEpisodeRef(
    @SerializedName("season") val season: Int,
    @SerializedName("number") val number: Int,
)

data class TraktHistoryEntry(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("watched_at") val watchedAt: String? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("movie") val movie: TraktHistoryMovie? = null,
    @SerializedName("show") val show: TraktHistoryShow? = null,
    @SerializedName("episode") val episode: TraktHistoryEpisode? = null,
)

data class TraktHistoryMovie(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

data class TraktHistoryShow(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

data class TraktHistoryEpisode(
    @SerializedName("season") val season: Int = 0,
    @SerializedName("number") val number: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("ids") val ids: TraktIds? = null,
)

data class TraktWatchlistEntry(
    @SerializedName("rank") val rank: Int = 0,
    @SerializedName("listed_at") val listedAt: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("movie") val movie: TraktHistoryMovie? = null,
    @SerializedName("show") val show: TraktHistoryShow? = null,
)

data class TraktRatingPayload(
    @SerializedName("movies") val movies: List<TraktRatedMovie>? = null,
    @SerializedName("shows") val shows: List<TraktRatedShow>? = null,
    @SerializedName("episodes") val episodes: List<TraktRatedEpisode>? = null,
)

data class TraktRatedMovie(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("rating") val rating: Int,
    @SerializedName("ids") val ids: TraktIds,
)

data class TraktRatedShow(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("rating") val rating: Int,
    @SerializedName("ids") val ids: TraktIds,
)

data class TraktRatedEpisode(
    @SerializedName("season") val season: Int,
    @SerializedName("number") val number: Int,
    @SerializedName("rating") val rating: Int,
    @SerializedName("ids") val ids: TraktIds,
)

data class TraktPlaybackItem(
    @SerializedName("progress") val progress: Double = 0.0,
    @SerializedName("type") val type: String? = null,
    @SerializedName("paused_at") val pausedAt: String? = null,
    @SerializedName("movie") val movie: TraktHistoryMovie? = null,
    @SerializedName("show") val show: TraktHistoryShow? = null,
    @SerializedName("episode") val episode: TraktHistoryEpisode? = null,
)

/** Um show visto, com as temporadas e os episódios vistos (GET /sync/watched/shows). */
data class TraktWatchedShow(
    @SerializedName("show") val show: TraktHistoryShow? = null,
    @SerializedName("seasons") val seasons: List<TraktWatchedSeason>? = null,
)

data class TraktWatchedSeason(
    @SerializedName("number") val number: Int = 0,
    @SerializedName("episodes") val episodes: List<TraktWatchedEpisode>? = null,
)

data class TraktWatchedEpisode(
    @SerializedName("number") val number: Int = 0,
)

/** GET /users/settings: só interessa o usuário (nome ou username). */
data class TraktSettingsResponse(
    @SerializedName("user") val user: TraktSettingsUser? = null,
)

data class TraktSettingsUser(
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
)

// ─── Interface Retrofit ───────────────────────────────────────────────────────

private interface TraktApi {
    @POST("oauth/device/code")
    suspend fun requestDeviceCode(@Body body: Map<String, String>): TraktDeviceCode

    @POST("oauth/device/token")
    suspend fun pollDeviceToken(@Body req: TraktDeviceTokenRequest): Response<TraktToken>

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: Map<String, String>): Response<TraktToken>

    @GET("users/settings")
    suspend fun getSettings(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
    ): TraktSettingsResponse

    @POST("scrobble/start")
    suspend fun scrobbleStart(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: TraktScrobblePayload,
    ): Response<Unit>

    @POST("scrobble/pause")
    suspend fun scrobblePause(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: TraktScrobblePayload,
    ): Response<Unit>

    @POST("scrobble/stop")
    suspend fun scrobbleStop(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: TraktScrobblePayload,
    ): Response<Unit>

    @GET("users/me/history/{type}")
    suspend fun getHistory(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Path("type") type: String,
        @Query("limit") limit: Int = 100,
    ): List<TraktHistoryEntry>

    @GET("users/me/watchlist/{type}")
    suspend fun getWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Path("type") type: String,
    ): List<TraktWatchlistEntry>

    @POST("users/me/watchlist")
    suspend fun addToWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: Map<String, Any>,
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "users/me/watchlist", hasBody = true)
    suspend fun removeFromWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: Map<String, Any>,
    ): Response<Unit>

    /** Watchlist de sincronia (a que o sync lê e empurra): filmes e séries juntos. */
    @GET("sync/watchlist")
    suspend fun getSyncWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Query("extended") extended: String = "full",
    ): List<TraktWatchlistEntry>

    @POST("sync/watchlist")
    suspend fun addToSyncWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: Map<String, Any>,
    ): Response<Unit>

    @POST("sync/watchlist/remove")
    suspend fun removeFromSyncWatchlist(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: Map<String, Any>,
    ): Response<Unit>

    @POST("sync/ratings")
    suspend fun addRating(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Body payload: TraktRatingPayload,
    ): Response<Unit>

    /** Ponto onde parou (filmes e episódios juntos, como o sync antigo lia). */
    @GET("sync/playback")
    suspend fun getPlaybackAll(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
    ): List<TraktPlaybackItem>

    @GET("sync/playback/{type}")
    suspend fun getPlayback(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Path("type") type: String,
    ): List<TraktPlaybackItem>

    @DELETE("sync/playback/{id}")
    suspend fun deletePlayback(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
        @Path("id") id: Long,
    ): Response<Unit>

    @GET("sync/watched/shows")
    suspend fun getWatchedShows(
        @Header("Authorization") auth: String,
        @Header("trakt-api-key") key: String,
    ): List<TraktWatchedShow>
}

/**
 * Trakt (v1.17.0 — reescrita sobre Retrofit, a partir do arquivo da outra IA).
 *
 * Junta o histórico do celular e da TV. Cada perfil tem a própria conta.
 * Login por código, a pessoa abre trakt.tv/activate e digita o código mostrado.
 *
 * Sincroniza a Minha Lista nos dois sentidos, o ponto onde parou vira
 * Continuar assistindo, e os episódios vistos do Trakt marcam os daqui. O
 * player avisa quando toca, pausa e fecha. Fica desligado (e escondido)
 * enquanto o client ID estiver vazio; o client secret é opcional (sem ele
 * o refresh de token não roda e o usuário reconecta quando o token expira).
 *
 * Sessão POR PERFIL em prefs tipadas (traktAccessToken / traktRefreshToken /
 * traktExpiresAt / traktUser / traktEnabled); client ID e secret são DO
 * APARELHO (prefs.traktClientId / traktClientSecret). A sessão antiga
 * ("trakt_v1", JSON cru) foi removida — quem tinha login conecta de novo.
 *
 * Validação: erro HTTP ou corpo que não é JSON (ex.: página HTML) NUNCA
 * conta como conexão concluída — o Gson lança na conversão e os fluxos de
 * login devolvem null; [withAuth] só aceita 2xx e limpa a sessão no 401
 * que sobrevive ao refresh.
 */
@Singleton
class TraktService @Inject constructor(
    client: OkHttpClient,
    private val prefs: PlatformPrefs,
    private val library: LibraryRepository,
    private val tmdb: TmdbApiService,
) {
    private val http = client.newBuilder().cache(null).addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("trakt-api-version", "2")
                .build(),
        )
    }.build()
    private val api: TraktApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TraktApi::class.java)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()

    /** Trakt usável: tem client ID. O secret é opcional. */
    val configured: Boolean get() = clientId.isNotBlank()

    val clientId: String get() = prefs.traktClientId
    fun setClientId(id: String) {
        prefs.traktClientId = id
    }

    val clientSecret: String get() = prefs.traktClientSecret
    fun setClientSecret(s: String) {
        prefs.traktClientSecret = s
    }

    /** Conectado neste perfil (tem access token). */
    val isConnected: Boolean get() = prefs.traktAccessToken.isNotBlank()

    /** Scrobble ligado neste perfil (toggle + login). */
    val isEnabled: Boolean get() = prefs.traktEnabled && isConnected
    fun setEnabled(on: Boolean) {
        prefs.traktEnabled = on
    }

    /** Nome da conta conectada no perfil ativo. null sem login ou sem nome. */
    fun user(): String? = prefs.traktUser.ifBlank { null }

    private fun authHeader() = "Bearer ${prefs.traktAccessToken}"

    private fun saveToken(t: TraktToken) {
        prefs.traktAccessToken = t.accessToken.orEmpty()
        if (!t.refreshToken.isNullOrBlank()) prefs.traktRefreshToken = t.refreshToken
        prefs.traktExpiresAt = System.currentTimeMillis() + (t.expiresIn.takeIf { it > 0 } ?: 7_776_000L) * 1000L
    }

    private fun clearSession() {
        prefs.traktAccessToken = ""
        prefs.traktRefreshToken = ""
        prefs.traktExpiresAt = 0L
        prefs.traktUser = ""
    }

    // ── Refresh de token ─────────────────────────────────────────────────────

    /**
     * Renova o access token com o refresh token guardado. Sucesso: salva o
     * novo access token (e o novo refresh token, se a resposta trouxer um;
     * senão mantém o antigo) e devolve true. Falha com resposta do servidor
     * (ex.: `invalid_grant`): limpa a sessão e devolve false. Falha de rede
     * (sem resposta): não limpa nada, devolve false. Sem refresh token ou
     * sem client secret: nem tenta (o usuário reconecta quando expirar).
     */
    private suspend fun refreshSession(): Boolean {
        val refresh = prefs.traktRefreshToken
        if (refresh.isBlank()) return false
        if (prefs.traktClientSecret.isBlank()) return false
        if (!configured) return false
        val body = mapOf(
            "refresh_token" to refresh,
            "client_id" to clientId,
            "client_secret" to prefs.traktClientSecret,
            "redirect_uri" to "urn:ietf:wg:oauth:2.0:oob",
            "grant_type" to "refresh_token",
        )
        val resp = try {
            api.refreshToken(body)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return false
        }
        if (!resp.isSuccessful) {
            clearSession()
            return false
        }
        val t = resp.body()
        if (t == null || t.accessToken.isNullOrBlank()) {
            clearSession()
            return false
        }
        saveToken(t)
        return true
    }

    /** Renova antes da chamada quando o token expirou (margem de 60 s). */
    private suspend fun refreshIfNeeded() {
        val expiresAt = prefs.traktExpiresAt
        if (expiresAt <= 0L) return
        if (prefs.traktRefreshToken.isBlank()) return
        if (System.currentTimeMillis() < expiresAt - 60_000L) return
        refreshSession()
    }

    /**
     * Chamada autenticada. Antes de cada chamada, se o token estiver
     * expirado (com 60 s de margem) e houver refresh token, dá refresh
     * primeiro. No 401: dá refresh UMA vez e repete a chamada; se o
     * refresh falhar, o erro original propaga (a sessão só é limpa quando
     * o refresh falhou com resposta do servidor, ou quando o retry também
     * dá 401). Funções Retrofit sem Response<T> lançam HttpException no
     * erro HTTP — é o que chega aqui e o que os chamadores capturam.
     */
    private suspend fun <T> withAuth(block: suspend (auth: String, key: String) -> T): T {
        refreshIfNeeded()
        if (prefs.traktAccessToken.isBlank()) error("trakt sem sessão")
        try {
            return block(authHeader(), clientId)
        } catch (e: HttpException) {
            if (e.code() != 401) throw e
            if (!refreshSession()) throw e
            try {
                return block(authHeader(), clientId)
            } catch (retry: HttpException) {
                if (retry.code() == 401) clearSession()
                throw retry
            }
        }
    }

    /** 2xx ou erro com o código real (nunca engole o erro como sucesso). */
    private fun Response<Unit>.checkOk() {
        if (!isSuccessful) error("trakt ${code()}")
    }

    // ── Login por código ─────────────────────────────────────────────────────

    data class DeviceCode(val userCode: String, val url: String, val deviceCode: String, val intervalSec: Int, val expiresAt: Long)

    /** Pede o código. null quando o Trakt não respondeu (ou respondeu sem JSON válido). */
    suspend fun startLogin(): DeviceCode? {
        if (!configured) return null
        val d = try {
            api.requestDeviceCode(mapOf("client_id" to clientId))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return null
        }
        val deviceCode = d.deviceCode.orEmpty()
        val userCode = d.userCode.orEmpty()
        if (deviceCode.isBlank() || userCode.isBlank()) return null
        return DeviceCode(
            userCode,
            d.verificationUrl.orEmpty().ifBlank { "https://trakt.tv/activate" },
            deviceCode,
            maxOf(5, d.interval),
            System.currentTimeMillis() + (d.expiresIn.takeIf { it > 0 } ?: 600) * 1000L,
        )
    }

    /**
     * Espera a pessoa confirmar no outro aparelho. Devolve o nome da conta, ou null se expirar
     * ou for negado. Cancelar a coroutine cancela a espera.
     */
    suspend fun waitLogin(c: DeviceCode): String? {
        var every = c.intervalSec * 1000L
        while (System.currentTimeMillis() < c.expiresAt) {
            currentCoroutineContext().ensureActive()
            delay(every)
            currentCoroutineContext().ensureActive()
            val resp = try {
                api.pollDeviceToken(
                    TraktDeviceTokenRequest(
                        code = c.deviceCode,
                        clientId = clientId,
                        clientSecret = prefs.traktClientSecret.ifBlank { null },
                    ),
                )
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                continue
            }
            when {
                resp.isSuccessful -> {
                    val t = resp.body()
                    if (t == null || t.accessToken.isNullOrBlank()) return null
                    saveToken(t)
                    val settingsUser = runCatching {
                        withAuth { auth, key -> api.getSettings(auth, key) }.user
                    }.getOrNull()
                    val name = settingsUser?.let { u ->
                        u.name?.ifBlank { u.username }.orEmpty()
                    }.orEmpty()
                    prefs.traktUser = name
                    scope.launch { sync() }
                    return name
                }
                resp.code() == 400 -> Unit // ainda esperando a pessoa
                resp.code() == 429 -> every += 1000L // devagar
                else -> return null // negado ou expirado
            }
        }
        return null
    }

    /** Desconecta: limpa tokens, expiração, usuário e o toggle. */
    fun logout() {
        clearSession()
        prefs.traktEnabled = false
    }

    // ── Sincronia ────────────────────────────────────────────────────────────

    /** Sincronia completa. true quando tudo deu certo. Uma por vez. */
    suspend fun sync(): Boolean {
        if (!configured || !isConnected) return false
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
    fun syncInBackground() {
        if (configured && isConnected) scope.launch { sync() }
    }

    private suspend fun pullWatchlist(profileId: String) {
        val have = library.watchlist(profileId).first().mapTo(HashSet()) { "${it.type}:${it.tmdbId}" }
        val list = withAuth { auth, key -> api.getSyncWatchlist(auth, key) }
        for (i in list.size - 1 downTo 0) {
            val e = list[i]
            val m = e.movie ?: e.show ?: continue
            val id = m.ids?.tmdb ?: 0
            val type = if (e.movie != null) MediaType.MOVIE else MediaType.SERIES
            if (id <= 0 || "${type.name}:$id" in have) continue
            library.saveToList(
                MovieItem(
                    id, id.toString(), m.title.orEmpty(), "", "", "",
                    m.year?.takeIf { it > 0 }?.toString().orEmpty(), 0.0, type = type,
                ),
                profileId,
            )
        }
    }

    private suspend fun pushWatchlist(profileId: String) {
        val movies = mutableListOf<Map<String, Any>>()
        val shows = mutableListOf<Map<String, Any>>()
        library.watchlist(profileId).first().forEach { w ->
            val one = mapOf<String, Any>("ids" to mapOf("tmdb" to w.tmdbId))
            if (w.type == MediaType.MOVIE.name) movies += one else shows += one
        }
        if (movies.isEmpty() && shows.isEmpty()) return
        val payload = mapOf<String, Any>("movies" to movies, "shows" to shows)
        withAuth { auth, key -> api.addToSyncWatchlist(auth, key, payload).checkOk() }
    }

    /** Ponto onde parou. Só entra o que é mais recente que o histórico daqui. */
    private suspend fun pullPlayback(profileId: String) {
        val list = withAuth { auth, key -> api.getPlaybackAll(auth, key) }
        val key = prefs.tmdbApiKey
        for (i in 0 until minOf(20, list.size)) {
            val p = list[i]
            val isEp = p.type == "episode"
            val m = (if (isEp) p.show else p.movie) ?: continue
            val id = m.ids?.tmdb ?: 0
            if (id <= 0) continue
            val type = if (isEp) MediaType.SERIES.name else MediaType.MOVIE.name
            val at = runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(p.pausedAt.orEmpty().take(19))!!.time
            }.getOrDefault(0L)
            val local = library.getPlayback(id, type, profileId)
            if (local != null && local.lastWatchedAt >= at) continue
            val runtimeMin = runCatching {
                if (isEp) 45 else tmdb.getMovieDetail(id, append = "", apiKey = key).runtime ?: 0
            }.getOrDefault(0)
            val dur = runtimeMin * 60_000L
            if (dur <= 0) continue
            val ep = p.episode
            library.saveProgress(
                tmdbId = id, type = type, title = m.title.orEmpty(), posterUrl = "",
                positionMs = (dur * p.progress / 100).toLong(), durationMs = dur,
                lastEpisode = if (isEp && ep != null) "T${ep.season}:E${ep.number}" else "",
                lastSourceUrl = "",
                profileId = profileId,
            )
        }
    }

    private suspend fun pullWatched(profileId: String) {
        val list = withAuth { auth, key -> api.getWatchedShows(auth, key) }
        for (e in list) {
            val id = e.show?.ids?.tmdb ?: 0
            if (id <= 0) continue
            for (so in e.seasons.orEmpty()) {
                for (ep in so.episodes.orEmpty()) {
                    if (!library.isEpisodeWatched(id, so.number, ep.number, profileId)) {
                        library.markEpisodeWatched(id, so.number, ep.number, profileId)
                    }
                }
            }
        }
    }

    // ── Histórico, watchlist, avaliação e retomada ───────────────────────────

    suspend fun getMovieHistory(): Result<List<TraktHistoryEntry>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getHistory(auth, key, "movies") }
    }

    suspend fun getShowHistory(): Result<List<TraktHistoryEntry>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getHistory(auth, key, "shows") }
    }

    suspend fun getMovieWatchlist(): Result<List<TraktWatchlistEntry>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getWatchlist(auth, key, "movies") }
    }

    suspend fun getShowWatchlist(): Result<List<TraktWatchlistEntry>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getWatchlist(auth, key, "shows") }
    }

    suspend fun addMovieToWatchlist(tmdbId: Int?, title: String?, year: Int?): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        val item = mapOf<String, Any>(
            "title" to (title.orEmpty()),
            "year" to (year ?: 0),
            "ids" to mapOf("tmdb" to tmdbId),
        )
        withAuth { auth, key -> api.addToWatchlist(auth, key, mapOf("movies" to listOf(item))).checkOk() }
    }

    suspend fun removeMovieFromWatchlist(tmdbId: Int?, title: String?, year: Int?): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        val item = mapOf<String, Any>(
            "title" to (title.orEmpty()),
            "year" to (year ?: 0),
            "ids" to mapOf("tmdb" to tmdbId),
        )
        withAuth { auth, key -> api.removeFromWatchlist(auth, key, mapOf("movies" to listOf(item))).checkOk() }
    }

    suspend fun addShowToWatchlist(tmdbId: Int?, title: String?, year: Int?): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        val item = mapOf<String, Any>(
            "title" to (title.orEmpty()),
            "year" to (year ?: 0),
            "ids" to mapOf("tmdb" to tmdbId),
        )
        withAuth { auth, key -> api.addToWatchlist(auth, key, mapOf("shows" to listOf(item))).checkOk() }
    }

    suspend fun removeShowFromWatchlist(tmdbId: Int?, title: String?, year: Int?): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        val item = mapOf<String, Any>(
            "title" to (title.orEmpty()),
            "year" to (year ?: 0),
            "ids" to mapOf("tmdb" to tmdbId),
        )
        withAuth { auth, key -> api.removeFromWatchlist(auth, key, mapOf("shows" to listOf(item))).checkOk() }
    }

    suspend fun rateMovie(tmdbId: Int?, title: String?, year: Int?, rating: Int): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        withAuth { auth, key ->
            api.addRating(
                auth, key,
                TraktRatingPayload(movies = listOf(TraktRatedMovie(title, year, rating, TraktIds(tmdb = tmdbId)))),
            ).checkOk()
        }
    }

    suspend fun rateEpisode(
        tmdbId: Int?, showTitle: String?, season: Int, episode: Int, rating: Int,
    ): Result<Unit> = runCatching {
        if (!isEnabled) return@runCatching
        withAuth { auth, key ->
            api.addRating(
                auth, key,
                TraktRatingPayload(
                    episodes = listOf(
                        TraktRatedEpisode(season, episode, rating, TraktIds(tmdb = tmdbId)),
                    ),
                ),
            ).checkOk()
        }
    }

    suspend fun getMoviePlayback(): Result<List<TraktPlaybackItem>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getPlayback(auth, key, "movies") }
    }

    suspend fun getEpisodePlayback(): Result<List<TraktPlaybackItem>> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.getPlayback(auth, key, "episodes") }
    }

    suspend fun clearPlayback(id: Long): Result<Unit> = runCatching {
        require(isConnected) { "Trakt não conectado" }
        withAuth { auth, key -> api.deletePlayback(auth, key, id).checkOk() }
    }

    // ── Avisos do player e da Minha Lista ─────────────────────────────────────

    private fun buildScrobblePayload(
        tmdbId: Int, type: String, season: Int, episode: Int, progress: Double,
    ): TraktScrobblePayload {
        val ids = TraktIds(tmdb = tmdbId)
        return if (type == MediaType.SERIES.name && season > 0 && episode > 0) {
            TraktScrobblePayload(
                show = TraktShowRef(ids = ids),
                episode = TraktEpisodeRef(season, episode),
                progress = progress,
            )
        } else {
            TraktScrobblePayload(movie = TraktMovieRef(ids = ids), progress = progress)
        }
    }

    /** [action] é start, pause ou stop (stop com 80% ou mais conta como visto no Trakt). */
    fun scrobble(action: String, tmdbId: Int, type: String, season: Int, episode: Int, progress: Double) {
        if (!configured || !isConnected || tmdbId <= 0) return
        if (!isEnabled) return
        if (action != "start" && action != "pause" && action != "stop") return
        val payload = buildScrobblePayload(tmdbId, type, season, episode, progress.coerceIn(0.0, 100.0))
        scope.launch {
            runCatching {
                withAuth { auth, key ->
                    when (action) {
                        "start" -> api.scrobbleStart(auth, key, payload)
                        "pause" -> api.scrobblePause(auth, key, payload)
                        else -> api.scrobbleStop(auth, key, payload)
                    }.checkOk()
                }
            }
        }
    }

    fun watchlistChanged(tmdbId: Int, type: String, added: Boolean) {
        if (!configured || !isConnected || tmdbId <= 0) return
        val one = mapOf<String, Any>("ids" to mapOf("tmdb" to tmdbId))
        val payload = mapOf<String, Any>(if (type == MediaType.MOVIE.name) "movies" else "shows" to listOf(one))
        scope.launch {
            runCatching {
                withAuth { auth, key ->
                    if (added) {
                        api.addToSyncWatchlist(auth, key, payload).checkOk()
                    } else {
                        api.removeFromSyncWatchlist(auth, key, payload).checkOk()
                    }
                }
            }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.trakt.tv/"
    }
}
