package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ─── Lote 25 (v1.35.0) — Frente D, item 11: catálogos de vídeo grátis ────────
// Pseudo-catálogos opcionais no Descobrir ("Vídeos grátis"): CCC
// (media.ccc.de), Blender (video.blender.org) e PeerTube (peer.tube).
// Endpoints testados a partir da VM em 2026-11-19 (todos HTTP 200):
// - api.media.ccc.de/public/events (lista) + /public/events/{guid} (a lista
//   NÃO traz recordings; o detalhe traz recording_url direto em mp4).
// - video.blender.org/api/v1/videos (PeerTube: streamingPlaylists HLS).
// - peer.tube/api/v1/videos (PeerTube: playlist HLS; o thumb é relativo à
//   instância de origem do vídeo, não ao peer.tube).
// FORA, com motivo: TED (a API de busca devolve 405 na VM — sem resposta
// útil, e a análise do Sombra já não tinha confirmado o formato) e YouTube
// via Innertube (o relatório do Sombra só documenta o host, sem endpoint
// exato — não inventar endpoint).
// Falha graciosa: qualquer erro de rede ou JSON vira lista vazia — a tela
// mostra o estado vazio, nunca crash. Nenhuma chave nova.

/** Um vídeo grátis tocável (ou resolvível na hora do toque, caso do CCC). */
data class FreeVideoItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbUrl: String = "",
    val pageUrl: String = "",
    /** Link direto (mp4 ou HLS). Null = resolver na hora do toque ([resolveStream]). */
    val streamUrl: String? = null,
    val durationSec: Int = 0,
    val source: String,
)

enum class FreeCatalog(val id: String, val label: String) {
    CCC("ccc", "CCC"),
    BLENDER("blender", "Blender"),
    PEERTUBE("peertube", "PeerTube"),
}

fun freeCatalogOf(id: String): FreeCatalog =
    FreeCatalog.values().firstOrNull { it.id == id } ?: FreeCatalog.CCC

@Singleton
class FreeCatalogsRepository @Inject constructor(
    client: OkHttpClient,
) {
    private val http = client.newBuilder().cache(null).build()

    private fun get(url: String): JSONObject? = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", STREAM_UA)
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.string()?.let { JSONObject(it) }
        }
    }.getOrNull()

    /** Lista de vídeos do catálogo. Vazia quando a fonte não responde (nunca falha). */
    suspend fun list(catalog: FreeCatalog, count: Int = 20): List<FreeVideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            when (catalog) {
                FreeCatalog.CCC -> listCcc(count)
                FreeCatalog.BLENDER -> listPeerTube("https://video.blender.org", count, "Blender")
                FreeCatalog.PEERTUBE -> listPeerTube("https://peer.tube", count, "PeerTube")
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve o link direto de um item sem [FreeVideoItem.streamUrl] (CCC:
     * busca o detalhe do evento e pega a gravação mp4 de maior qualidade).
     * null = sem gravação tocável.
     */
    suspend fun resolveStream(item: FreeVideoItem): String? = withContext(Dispatchers.IO) {
        if (item.streamUrl != null) return@withContext item.streamUrl
        if (item.source != FreeCatalog.CCC.label) return@withContext null
        runCatching {
            val detail = get("https://api.media.ccc.de/public/events/${item.id}") ?: return@runCatching null
            bestCccRecording(detail.optJSONArray("recordings"))
        }.getOrNull()
    }

    private fun listCcc(count: Int): List<FreeVideoItem> {
        val root = get("https://api.media.ccc.de/public/events?limit=$count") ?: return emptyList()
        val out = mutableListOf<FreeVideoItem>()
        val arr = root.optJSONArray("events") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val guid = e.optString("guid")
            val title = e.optString("title")
            if (guid.isBlank() || title.isBlank()) continue
            val dur = e.optInt("duration", 0).takeIf { it > 0 } ?: e.optInt("length", 0)
            out += FreeVideoItem(
                id = guid,
                title = title,
                subtitle = e.optString("conference_title"),
                thumbUrl = e.optString("thumb_url"),
                pageUrl = e.optString("frontend_link").ifBlank { e.optString("url") },
                streamUrl = null,
                durationSec = dur,
                source = FreeCatalog.CCC.label,
            )
        }
        return out
    }

    private fun listPeerTube(base: String, count: Int, source: String): List<FreeVideoItem> {
        val root = get("$base/api/v1/videos?count=$count&sort=-publishedAt") ?: return emptyList()
        val out = mutableListOf<FreeVideoItem>()
        val arr = root.optJSONArray("data") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            val uuid = v.optString("uuid")
            val name = v.optString("name")
            if (uuid.isBlank() || name.isBlank()) continue
            val origin = v.optString("url").substringBefore("/videos/").ifBlank { base }
            val playlist = v.optJSONArray("streamingPlaylists")?.optJSONObject(0)?.optString("playlistUrl")
            val file = v.optJSONArray("files")?.let { files ->
                (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                    .firstOrNull { (it.optJSONObject("resolution")?.optInt("id") ?: 0) > 0 }
                    ?.optString("fileUrl")
            }
            val stream = playlist?.takeIf { it.isNotBlank() } ?: file?.takeIf { it.isNotBlank() }
            val thumb = v.optString("previewPath").takeIf { it.isNotBlank() }?.let { origin + it }.orEmpty()
            out += FreeVideoItem(
                id = uuid,
                title = name,
                subtitle = v.optJSONObject("channel")?.optString("displayName").orEmpty(),
                thumbUrl = thumb,
                pageUrl = v.optString("url"),
                streamUrl = stream,
                durationSec = v.optInt("duration", 0),
                source = source,
            )
        }
        return out
    }

    /** Gravação mp4 de maior qualidade (high_quality primeiro, depois maior altura). */
    internal fun bestCccRecording(recordings: JSONArray?): String? {
        if (recordings == null) return null
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until recordings.length()) {
            val r = recordings.optJSONObject(i) ?: continue
            if (r.optString("mime_type") != "video/mp4") continue
            val url = r.optString("recording_url")
            if (url.isBlank()) continue
            val score = (if (r.optBoolean("high_quality", false)) 10_000 else 0) + r.optInt("height", 0)
            if (score > bestScore) {
                bestScore = score
                best = r
            }
        }
        return best?.optString("recording_url")
    }
}

/** Duração "12:34" (ou "1:02:03"). Vazio sem duração. */
fun freeVideoDurationLabel(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
