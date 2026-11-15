package com.aotv.app.data.mediaservers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/** HTTP dos servidores de mídia. Tudo bloqueante, quem chama já está no Dispatchers.IO. */
internal open class MediaHttp(private val client: OkHttpClient?) {
    data class Res(val code: Int, val body: String) {
        val ok get() = code in 200..299
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
        fun jsonArray(): JSONArray? = runCatching { JSONArray(body) }.getOrNull()
        /** Erro real curto p/ mostrar na tela (código + começo da resposta do servidor). */
        fun errorText(fallback: String): String {
            if (code == -1) return body.ifBlank { fallback }
            val snippet = body.trim().take(120).replace(Regex("\\s+"), " ")
            return if (snippet.isBlank()) "$fallback ($code)" else "$fallback ($code): $snippet"
        }
    }

    private fun call(b: Request.Builder, headers: Map<String, String> = emptyMap()): Res {
        b.header("User-Agent", "AOTV")
        // Padrão JSON; o Plex pede XML (Accept passado por quem chama vence).
        if ("Accept" !in headers) b.header("Accept", "application/json")
        headers.forEach { (k, v) -> b.header(k, v) }
        return runCatching {
            client!!.newCall(b.build()).execute().use { Res(it.code, it.body?.string().orEmpty()) }
        }.getOrElse { Res(-1, it.message.orEmpty()) }
    }

    open fun get(url: String, headers: Map<String, String> = emptyMap()): Res {
        val b = Request.Builder().url(url)
        return call(b, headers)
    }

    open fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): Res {
        val b = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
        return call(b, headers)
    }

    open fun post(url: String, headers: Map<String, String> = emptyMap()): Res {
        val b = Request.Builder().url(url).post(ByteArray(0).toRequestBody(null))
        return call(b, headers)
    }

    open fun delete(url: String, headers: Map<String, String> = emptyMap()): Res {
        val b = Request.Builder().url(url).delete()
        return call(b, headers)
    }
}

internal fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

/** Elementos XML (nome → atributos) de uma resposta do Plex. */
internal fun xmlElements(body: String, names: Set<String>): List<Map<String, String>> {
    val out = ArrayList<Map<String, String>>()
    runCatching {
        val p: XmlPullParser = android.util.Xml.newPullParser()
        p.setInput(body.byteInputStream(Charsets.UTF_8), "UTF-8")
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name in names) {
                val attrs = HashMap<String, String>()
                for (i in 0 until p.attributeCount) attrs[p.getAttributeName(i)] = p.getAttributeValue(i)
                out += attrs
            }
            ev = p.next()
        }
    }
    return out
}

// ── Jellyfin / Emby (mesma API) ───────────────────────────────────────────────

/** Login Jellyfin/Emby: token, id e nome do usuário no servidor. */
internal data class JellyfinAuth(val token: String, val userId: String, val userName: String)

internal class JellyfinApi(private val http: MediaHttp) {
    companion object {
        const val VERSION = "1.20.0"
    }

    private fun authHeader(deviceId: String) =
        "MediaBrowser Client=\"AOTV\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"$VERSION\""

    private fun tokenHeaders(token: String, deviceId: String) = mapOf(
        "X-MediaBrowser-Token" to token,
        "X-Emby-Authorization" to authHeader(deviceId),
    )

    fun authenticate(base: String, username: String, password: String, deviceId: String): Result<JellyfinAuth> {
        val body = JSONObject().put("Username", username).put("Pw", password).toString()
        val r = http.postJson(
            "$base/Users/AuthenticateByName", body,
            mapOf("X-Emby-Authorization" to authHeader(deviceId)),
        )
        val o = r.json()
        if (!r.ok || o == null) return Result.failure(IllegalStateException(r.errorText("Usuário ou senha inválidos")))
        val token = o.optString("AccessToken").ifBlank {
            return Result.failure(IllegalStateException("O servidor não devolveu o token"))
        }
        val user = o.optJSONObject("User")
            ?: return Result.failure(IllegalStateException("O servidor não devolveu o usuário"))
        return Result.success(JellyfinAuth(token, user.optString("Id"), user.optString("Name")))
    }

    /** Nome e versão do servidor (p/ testar a conexão e batizar a conta). */
    fun info(base: String, token: String, deviceId: String): Result<Pair<String, String>> {
        val r = http.get("$base/System/Info", tokenHeaders(token, deviceId))
        val o = r.json()
        if (!r.ok || o == null) return Result.failure(IllegalStateException(r.errorText("O servidor não respondeu")))
        return Result.success(o.optString("ServerName") to o.optString("Version"))
    }

    fun libraries(base: String, token: String, deviceId: String, userId: String, serverId: String): Result<List<MediaLibrary>> {
        val r = http.get("$base/Users/$userId/Views", tokenHeaders(token, deviceId))
        val arr = r.json()?.optJSONArray("Items")
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("Não deu para listar as bibliotecas")))
        val out = ArrayList<MediaLibrary>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("Id")
            if (id.isBlank()) continue
            val type = when (o.optString("CollectionType")) {
                "movies" -> "movie"
                "tvshows" -> "series"
                else -> "mixed"
            }
            out += MediaLibrary(id, serverId, o.optString("Name").ifBlank { "Biblioteca" }, type)
        }
        return Result.success(out)
    }

    fun items(base: String, token: String, deviceId: String, userId: String, serverId: String, libraryId: String): Result<List<MediaItem>> {
        val url = "$base/Users/$userId/Items?ParentId=$libraryId&Recursive=true" +
            "&IncludeItemTypes=Movie,Series&Fields=Overview,ProductionYear,RunTimeTicks,UserData" +
            "&SortBy=SortName&Limit=500"
        val r = http.get(url, tokenHeaders(token, deviceId))
        val arr = r.json()?.optJSONArray("Items")
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("Não deu para listar os itens")))
        return Result.success(parseItems(base, token, serverId, libraryId, arr))
    }

    fun seasons(base: String, token: String, deviceId: String, userId: String, serverId: String, seriesId: String): Result<List<MediaItem>> {
        val url = "$base/Shows/$seriesId/Seasons?UserId=$userId&Fields=Overview,ProductionYear,UserData"
        val r = http.get(url, tokenHeaders(token, deviceId))
        val arr = r.json()?.optJSONArray("Items")
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("Não deu para listar as temporadas")))
        return Result.success(parseItems(base, token, serverId, "", arr))
    }

    fun episodes(base: String, token: String, deviceId: String, userId: String, serverId: String, seriesId: String, seasonId: String): Result<List<MediaItem>> {
        val url = "$base/Shows/$seriesId/Episodes?SeasonId=$seasonId&UserId=$userId" +
            "&Fields=Overview,ProductionYear,RunTimeTicks,UserData&SortBy=IndexNumber&Limit=500"
        val r = http.get(url, tokenHeaders(token, deviceId))
        val arr = r.json()?.optJSONArray("Items")
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("Não deu para listar os episódios")))
        return Result.success(parseItems(base, token, serverId, "", arr))
    }

    fun search(base: String, token: String, deviceId: String, userId: String, serverId: String, query: String): Result<List<MediaItem>> {
        val url = "$base/Users/$userId/Items?SearchTerm=${enc(query)}&Recursive=true" +
            "&IncludeItemTypes=Movie,Series,Episode&Fields=Overview,ProductionYear,RunTimeTicks,UserData&Limit=50"
        val r = http.get(url, tokenHeaders(token, deviceId))
        val arr = r.json()?.optJSONArray("Items")
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("A busca falhou")))
        return Result.success(parseItems(base, token, serverId, "", arr))
    }

    fun setPlayed(base: String, token: String, deviceId: String, userId: String, itemId: String, played: Boolean): Boolean {
        val url = "$base/Users/$userId/PlayedItems/$itemId"
        val h = tokenHeaders(token, deviceId)
        val r = if (played) http.post(url, h) else http.delete(url, h)
        return r.ok
    }

    fun streamUrl(base: String, token: String, itemId: String): String =
        "$base/Videos/$itemId/stream.mp4?api_key=$token&Static=true"

    fun imageUrl(base: String, token: String, itemId: String, kind: String): String =
        "$base/Items/$itemId/Images/$kind?api_key=$token&maxWidth=600"

    /** Converte o array `Items` do Jellyfin/Emby. Só usa o que o servidor devolveu. */
    fun parseItems(base: String, token: String, serverId: String, libraryId: String, arr: JSONArray): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("Id")
            if (id.isBlank()) continue
            val type = when (o.optString("Type")) {
                "Movie" -> "movie"
                "Series" -> "series"
                "Season" -> "season"
                "Episode" -> "episode"
                else -> continue
            }
            val userData = o.optJSONObject("UserData")
            val hasPrimary = o.optJSONObject("ImageTags")?.has("Primary") == true
            val hasBackdrop = (o.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0
            out += MediaItem(
                id = id, serverId = serverId, libraryId = libraryId,
                title = o.optString("Name").ifBlank { "Sem título" }, type = type,
                year = o.optInt("ProductionYear", 0),
                overview = o.optString("Overview"),
                posterUrl = if (hasPrimary) imageUrl(base, token, id, "Primary") else "",
                backdropUrl = if (hasBackdrop) imageUrl(base, token, id, "Backdrop") else "",
                runtimeMin = (o.optLong("RunTimeTicks", 0L) / 600_000_000L).toInt(),
                seasonNumber = o.optInt("ParentIndexNumber", 0),
                episodeNumber = o.optInt("IndexNumber", 0),
                seriesId = o.optString("SeriesId"),
                seriesTitle = o.optString("SeriesName"),
                played = userData?.optBoolean("Played", false) == true,
            )
        }
        return out
    }
}

// ── Plex ──────────────────────────────────────────────────────────────────────

internal class PlexApi(private val http: MediaHttp) {
    companion object {
        const val PLEX_TV = "https://plex.tv"
        const val CLIENT_ID = "aotv-android"
    }

    private fun plexHeaders(token: String) = mapOf(
        "X-Plex-Token" to token,
        "X-Plex-Client-Identifier" to CLIENT_ID,
        "X-Plex-Product" to "AOTV",
        "X-Plex-Version" to JellyfinApi.VERSION,
    )

    /** Confere o token no plex.tv. Falha quando não vale. */
    fun me(token: String): Result<Pair<String, String>> {
        val r = http.get("$PLEX_TV/api/v2/user", plexHeaders(token))
        val o = r.json()
        if (!r.ok || o == null) return Result.failure(IllegalStateException(r.errorText("Token do Plex inválido")))
        val id = o.optString("id").ifBlank { o.optString("uuid") }
        if (id.isBlank()) return Result.failure(IllegalStateException("O plex.tv não devolveu o usuário"))
        return Result.success(id to o.optString("username"))
    }

    /** Servidores do usuário no plex.tv (p/ escolher um sem digitar endereço). */
    fun resources(token: String): Result<List<DiscoveredPlexServer>> {
        val r = http.get("$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1", plexHeaders(token))
        val arr = r.jsonArray()
        if (!r.ok || arr == null) return Result.failure(IllegalStateException(r.errorText("Não deu para listar os servidores")))
        val out = ArrayList<DiscoveredPlexServer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.optString("provides").contains("server")) continue
            val conns = o.optJSONArray("connections") ?: continue
            var best: String? = null
            var local: String? = null
            for (j in 0 until conns.length()) {
                val c = conns.optJSONObject(j) ?: continue
                if (c.optBoolean("relay", false)) continue
                val uri = c.optString("uri")
                if (uri.isBlank()) continue
                if (c.optBoolean("local", false)) { if (local == null) local = uri }
                else if (best == null) best = uri
            }
            val uri = best ?: local ?: continue
            out += DiscoveredPlexServer(
                name = o.optString("name").ifBlank { "Servidor Plex" },
                uri = uri,
                machineId = o.optString("clientIdentifier"),
                owned = o.optBoolean("owned", false),
                token = o.optString("accessToken"),
            )
        }
        return Result.success(out)
    }

    /** Nome do servidor (p/ testar a conexão e batizar a conta). */
    fun serverName(base: String, token: String): Result<String> {
        val r = http.get("$base/?X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        if (!r.ok) return Result.failure(IllegalStateException(r.errorText("O servidor não respondeu")))
        val name = xmlElements(r.body, setOf("MediaContainer")).firstOrNull()?.get("friendlyName").orEmpty()
        if (name.isBlank()) return Result.failure(IllegalStateException("O servidor não se identificou"))
        return Result.success(name)
    }

    fun libraries(base: String, token: String, serverId: String): Result<List<MediaLibrary>> {
        val r = http.get("$base/library/sections?X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        if (!r.ok) return Result.failure(IllegalStateException(r.errorText("Não deu para listar as bibliotecas")))
        val out = xmlElements(r.body, setOf("Directory")).mapNotNull { d ->
            val key = d["key"].orEmpty()
            if (key.isBlank()) return@mapNotNull null
            val type = when (d["type"]) {
                "movie" -> "movie"
                "show" -> "series"
                else -> return@mapNotNull null
            }
            MediaLibrary(key, serverId, d["title"].orEmpty().ifBlank { "Biblioteca" }, type)
        }
        return Result.success(out)
    }

    fun sectionItems(base: String, token: String, serverId: String, sectionKey: String): Result<List<MediaItem>> {
        val r = http.get("$base/library/sections/$sectionKey/all?X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        if (!r.ok) return Result.failure(IllegalStateException(r.errorText("Não deu para listar os itens")))
        return Result.success(parseMedia(base, token, serverId, sectionKey, xmlElements(r.body, setOf("Video", "Directory"))))
    }

    fun children(base: String, token: String, serverId: String, ratingKey: String): Result<List<MediaItem>> {
        val r = http.get("$base/library/metadata/$ratingKey/children?X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        if (!r.ok) return Result.failure(IllegalStateException(r.errorText("Não deu para listar os itens")))
        return Result.success(parseMedia(base, token, serverId, "", xmlElements(r.body, setOf("Video", "Directory"))))
    }

    fun search(base: String, token: String, serverId: String, query: String): Result<List<MediaItem>> {
        val r = http.get("$base/library/search?query=${enc(query)}&X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        if (!r.ok) return Result.failure(IllegalStateException(r.errorText("A busca falhou")))
        return Result.success(parseMedia(base, token, serverId, "", xmlElements(r.body, setOf("Video", "Directory"))))
    }

    /**
     * URL direta do vídeo: o arquivo da `Part` do servidor (o token vai na
     * query, como o próprio Plex faz nos apps dele). Vazio quando o servidor
     * não devolveu a parte — aí a tela oferece o HLS ([transcodeUrl]).
     */
    fun streamUrl(base: String, token: String, ratingKey: String): String {
        val r = http.get("$base/library/metadata/$ratingKey?X-Plex-Token=$token", mapOf("Accept" to "application/xml"))
        val part = if (r.ok) xmlElements(r.body, setOf("Part")).firstOrNull() else null
        val partKey = part?.get("key").orEmpty()
        if (partKey.isBlank()) return ""
        return "$base$partKey?X-Plex-Token=$token"
    }

    /** URL HLS (transcode) quando o arquivo direto não toca no aparelho. */
    fun transcodeUrl(base: String, token: String, ratingKey: String): String {
        val direct = streamUrl(base, token, ratingKey)
        if (direct.isBlank()) return ""
        val path = enc(direct.substringBefore('?'))
        return "$base/video/:/transcode/universal/start.m3u8?path=$path&mediaIndex=0&partIndex=0" +
            "&protocol=hls&fastSeek=1&directPlay=0&directStream=1&X-Plex-Token=$token"
    }

    fun setPlayed(base: String, token: String, ratingKey: String, played: Boolean): Boolean {
        val verb = if (played) "scrobble" else "unscrobble"
        val r = http.get("$base/:/$verb?key=$ratingKey&identifier=com.plexapp.plugins.library&X-Plex-Token=$token")
        return r.ok
    }

    /** Converte os elementos Video/Directory do Plex. Só usa o que o servidor devolveu. */
    fun parseMedia(base: String, token: String, serverId: String, libraryId: String, els: List<Map<String, String>>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        for (d in els) {
            val key = d["ratingKey"].orEmpty()
            if (key.isBlank()) continue
            val type = when (d["type"]) {
                "movie" -> "movie"
                "show" -> "series"
                "season" -> "season"
                "episode" -> "episode"
                else -> continue
            }
            val thumb = d["thumb"].orEmpty()
            val art = d["art"].orEmpty()
            val durationMs = d["duration"]?.toLongOrNull() ?: 0L
            out += MediaItem(
                id = key, serverId = serverId, libraryId = libraryId,
                title = d["title"].orEmpty().ifBlank { "Sem título" }, type = type,
                year = d["year"]?.toIntOrNull() ?: 0,
                overview = d["summary"].orEmpty(),
                posterUrl = if (thumb.isNotBlank()) "$base$thumb?X-Plex-Token=$token" else "",
                backdropUrl = if (art.isNotBlank()) "$base$art?X-Plex-Token=$token" else "",
                runtimeMin = (durationMs / 60_000L).toInt(),
                seasonNumber = d["parentIndex"]?.toIntOrNull() ?: 0,
                episodeNumber = d["index"]?.toIntOrNull() ?: 0,
                seriesId = d["grandparentRatingKey"].orEmpty().ifBlank { d["parentRatingKey"].orEmpty() },
                seriesTitle = d["grandparentTitle"].orEmpty(),
                played = (d["viewCount"]?.toIntOrNull() ?: 0) > 0,
            )
        }
        return out
    }
}
