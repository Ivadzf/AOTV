package com.aotv.app.data.mediaservers

import android.content.Context
import com.aotv.app.data.remote.AppDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servidores de mídia do usuário (Lote 10, v1.20.0): Jellyfin, Emby e Plex.
 * As contas vivem em SharedPreferences própria (`aotv_mediaservers`) e NUNCA
 * entram no backup — mesma regra do debrid (ver AotvBackup). Nada aqui inventa
 * dado: sem pôster ou sinopse no servidor, a tela mostra o título e pronto.
 */
@Singleton
class MediaServerService @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("aotv_mediaservers", Context.MODE_PRIVATE)
    private val http = MediaHttp(
        AppDns.mediaClient.newBuilder()
            .callTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    )
    private val jf = JellyfinApi(http)
    private val plex = PlexApi(http)

    /** Id do aparelho p/ o Jellyfin/Emby (estável; o servidor o usa p/ a sessão). */
    private val deviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }

    private val _accounts = MutableStateFlow(readAccounts())
    val accounts: StateFlow<List<MediaServerAccount>> = _accounts.asStateFlow()

    fun account(id: String): MediaServerAccount? = _accounts.value.firstOrNull { it.id == id }

    // ── Conexão ───────────────────────────────────────────────────────────────

    /** Endereço limpo (sem barra no fim). null quando não é http(s) válido. */
    fun cleanBase(raw: String): String? {
        val b = raw.trim().trimEnd('/')
        if (b.isBlank()) return null
        val lower = b.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        if (b.contains(' ') || b.substringAfter("://").isBlank()) return null
        return b
    }

    /** Jellyfin/Emby: entra com usuário e senha e guarda o token do servidor. */
    suspend fun connectJellyfin(kind: MediaServerKind, baseRaw: String, username: String, password: String): Result<MediaServerAccount> =
        withContext(Dispatchers.IO) {
            val base = cleanBase(baseRaw)
                ?: return@withContext Result.failure(IllegalArgumentException("Endereço inválido — use http:// ou https://"))
            if (username.isBlank()) return@withContext Result.failure(IllegalArgumentException("Informe o usuário"))
            val auth = jf.authenticate(base, username.trim(), password, deviceId).getOrElse { e ->
                return@withContext Result.failure(e)
            }
            val (serverName, _) = jf.info(base, auth.token, deviceId).getOrNull() ?: ("" to "")
            val acc = MediaServerAccount(
                id = UUID.randomUUID().toString(), kind = kind,
                label = serverName.ifBlank { base }, baseUrl = base,
                username = auth.userName.ifBlank { username.trim() },
                token = auth.token, userId = auth.userId,
            )
            save(acc)
            Result.success(acc)
        }

    /** Plex: confere o token no plex.tv e lista os servidores do usuário. */
    suspend fun discoverPlex(token: String): Result<List<DiscoveredPlexServer>> = withContext(Dispatchers.IO) {
        val t = token.trim()
        if (t.length < 8) return@withContext Result.failure(IllegalArgumentException("Token curto demais"))
        plex.me(t).getOrElse { e -> return@withContext Result.failure(e) }
        plex.resources(t)
    }

    /** Plex: conecta num servidor descoberto pelo plex.tv. */
    suspend fun connectPlexDiscovered(plexToken: String, server: DiscoveredPlexServer): Result<MediaServerAccount> =
        withContext(Dispatchers.IO) {
            val me = plex.me(plexToken.trim()).getOrElse { e -> return@withContext Result.failure(e) }
            val token = server.token.ifBlank { plexToken.trim() }
            val name = plex.serverName(server.uri, token).getOrNull() ?: server.name
            val acc = MediaServerAccount(
                id = UUID.randomUUID().toString(), kind = MediaServerKind.PLEX,
                label = name, baseUrl = server.uri,
                username = me.second, token = token, userId = me.first,
            )
            save(acc)
            Result.success(acc)
        }

    /** Plex: conecta digitando o endereço do servidor + o token do plex.tv. */
    suspend fun connectPlexManual(plexToken: String, baseRaw: String): Result<MediaServerAccount> =
        withContext(Dispatchers.IO) {
            val base = cleanBase(baseRaw)
                ?: return@withContext Result.failure(IllegalArgumentException("Endereço inválido — use http:// ou https://"))
            val t = plexToken.trim()
            if (t.length < 8) return@withContext Result.failure(IllegalArgumentException("Token curto demais"))
            val me = plex.me(t).getOrElse { e -> return@withContext Result.failure(e) }
            val name = plex.serverName(base, t).getOrElse { e -> return@withContext Result.failure(e) }
            val acc = MediaServerAccount(
                id = UUID.randomUUID().toString(), kind = MediaServerKind.PLEX,
                label = name, baseUrl = base,
                username = me.second, token = t, userId = me.first,
            )
            save(acc)
            Result.success(acc)
        }

    /** Testa a conexão e devolve o nome/versão do servidor. Falha com o erro real. */
    suspend fun testConnection(acc: MediaServerAccount): Result<String> = withContext(Dispatchers.IO) {
        when (acc.kind) {
            MediaServerKind.JELLYFIN, MediaServerKind.EMBY -> {
                val info = jf.info(acc.baseUrl, acc.token, deviceId).getOrElse { e -> return@withContext Result.failure(e) }
                val name = info.first.ifBlank { acc.label }
                val version = info.second
                Result.success(if (version.isBlank()) name else "$name ($version)")
            }
            MediaServerKind.PLEX -> {
                val name = plex.serverName(acc.baseUrl, acc.token).getOrElse { e -> return@withContext Result.failure(e) }
                Result.success(name)
            }
        }
    }

    fun remove(id: String) {
        writeAccounts(_accounts.value.filterNot { it.id == id })
    }

    private fun save(acc: MediaServerAccount) {
        val list = _accounts.value.filterNot { it.kind == acc.kind && it.baseUrl == acc.baseUrl } + acc
        writeAccounts(list)
    }

    // ── Bibliotecas ───────────────────────────────────────────────────────────

    suspend fun libraries(acc: MediaServerAccount): Result<List<MediaLibrary>> = withContext(Dispatchers.IO) {
        when (acc.kind) {
            MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                jf.libraries(acc.baseUrl, acc.token, deviceId, acc.userId, acc.id)
            MediaServerKind.PLEX -> plex.libraries(acc.baseUrl, acc.token, acc.id)
        }
    }

    /** Filmes e séries de uma biblioteca. */
    suspend fun libraryItems(acc: MediaServerAccount, library: MediaLibrary): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            when (acc.kind) {
                MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                    jf.items(acc.baseUrl, acc.token, deviceId, acc.userId, acc.id, library.id)
                MediaServerKind.PLEX -> plex.sectionItems(acc.baseUrl, acc.token, acc.id, library.id)
            }
        }

    /** Temporadas de uma série. */
    suspend fun seasons(acc: MediaServerAccount, series: MediaItem): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            when (acc.kind) {
                MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                    jf.seasons(acc.baseUrl, acc.token, deviceId, acc.userId, acc.id, series.id)
                MediaServerKind.PLEX -> plex.children(acc.baseUrl, acc.token, acc.id, series.id)
            }
        }

    /** Episódios de uma temporada (Jellyfin/Emby) ou de uma série (Plex devolve as temporadas em [seasons]). */
    suspend fun episodes(acc: MediaServerAccount, series: MediaItem, season: MediaItem): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            when (acc.kind) {
                MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                    jf.episodes(acc.baseUrl, acc.token, deviceId, acc.userId, acc.id, series.id, season.id)
                MediaServerKind.PLEX -> plex.children(acc.baseUrl, acc.token, acc.id, season.id)
            }
        }

    suspend fun search(acc: MediaServerAccount, query: String): Result<List<MediaItem>> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext Result.success(emptyList())
            when (acc.kind) {
                MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                    jf.search(acc.baseUrl, acc.token, deviceId, acc.userId, acc.id, q)
                MediaServerKind.PLEX -> plex.search(acc.baseUrl, acc.token, acc.id, q)
            }
        }

    /** URL direta do vídeo no servidor, p/ tocar no player existente. */
    suspend fun streamUrl(acc: MediaServerAccount, item: MediaItem): String = withContext(Dispatchers.IO) {
        when (acc.kind) {
            MediaServerKind.JELLYFIN, MediaServerKind.EMBY -> jf.streamUrl(acc.baseUrl, acc.token, item.id)
            MediaServerKind.PLEX -> {
                val direct = plex.streamUrl(acc.baseUrl, acc.token, item.id)
                if (direct.isNotBlank()) direct else plex.transcodeUrl(acc.baseUrl, acc.token, item.id)
            }
        }
    }

    /** Marca como visto (ou tira o visto). false quando o servidor recusou. */
    suspend fun setPlayed(acc: MediaServerAccount, item: MediaItem, played: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            when (acc.kind) {
                MediaServerKind.JELLYFIN, MediaServerKind.EMBY ->
                    jf.setPlayed(acc.baseUrl, acc.token, deviceId, acc.userId, item.id, played)
                MediaServerKind.PLEX -> plex.setPlayed(acc.baseUrl, acc.token, item.id, played)
            }
        }

    // ── Persistência ──────────────────────────────────────────────────────────

    private fun readAccounts(): List<MediaServerAccount> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("accounts", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = MediaServerKind.of(o.optString("k")) ?: return@mapNotNull null
            MediaServerAccount(
                o.optString("id"), k, o.optString("label"), o.optString("base"),
                o.optString("user"), o.optString("token"), o.optString("uid"),
            ).takeIf { it.id.isNotBlank() && it.token.isNotBlank() && it.baseUrl.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun writeAccounts(list: List<MediaServerAccount>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(
                org.json.JSONObject().put("id", it.id).put("k", it.kind.id).put("label", it.label)
                    .put("base", it.baseUrl).put("user", it.username).put("token", it.token).put("uid", it.userId)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
        _accounts.value = list
    }
}
