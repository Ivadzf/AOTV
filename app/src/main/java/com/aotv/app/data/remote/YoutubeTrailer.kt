package com.aotv.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extrai o stream do trailer do YouTube (NewPipeExtractor) p/ tocar no nosso ExoPlayer.
 * A extração IGNORA o bloqueio de embed (o vídeo toca; só não roda em iframes) — resolve
 * o erro 152 dos trailers oficiais de estúdio.
 */
@Singleton
class YoutubeTrailer @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloader = object : Downloader() {
        override fun execute(request: Request): Response {
            val b = okhttp3.Request.Builder().url(request.url())
            val headers = request.headers()
            headers.forEach { (k, vs) -> vs.forEach { b.addHeader(k, it) } }
            // User-Agent + idioma OBRIGATÓRIOS: sem UA o YouTube devolve consent/captcha e a
            // extração falha (trailer cai no embed → erro 152). Cookie de consentimento pula o wall.
            if (headers.keys.none { it.equals("User-Agent", true) }) b.header("User-Agent", USER_AGENT)
            if (headers.keys.none { it.equals("Accept-Language", true) }) b.header("Accept-Language", "pt-BR, pt;q=0.9, en;q=0.8")
            if (headers.keys.none { it.equals("Cookie", true) }) b.header("Cookie", "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgLC_pwY")
            val data = request.dataToSend()
            when (request.httpMethod()) {
                "POST" -> b.post((data ?: ByteArray(0)).toRequestBody())
                "GET" -> b.get()
                else -> b.method(request.httpMethod(), data?.toRequestBody())
            }
            client.newCall(b.build()).execute().use { r ->
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string() ?: "", r.request.url.toString())
            }
        }
    }

    @Volatile private var inited = false
    private fun ensureInit() {
        if (!inited) synchronized(this) {
            if (!inited) {
                NewPipe.init(
                    downloader,
                    org.schabi.newpipe.extractor.localization.Localization("pt", "BR"),
                    org.schabi.newpipe.extractor.localization.ContentCountry("BR"),
                )
                inited = true
            }
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0"
    }

    /** Stream do trailer: URL de vídeo (+ áudio se DASH). null se falhar. */
    data class TrailerStream(val videoUrl: String, val audioUrl: String?)

    // Altura da resolução: só os dígitos ANTES do 'p' ("1080p60" → 1080, não 108060 — senão
    // o range 1..1080 descarta streams de alto fps e o trailer cai no muxado 360p).
    private fun res(r: String?): Int =
        runCatching { (r ?: "").substringBefore('p').filter(Char::isDigit).toInt() }.getOrDefault(0)

    // Instâncias Piped (extração server-side; devolve HLS pronto e IGNORA bloqueio de embed).
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.reallyaweso.me",
    )

    /**
     * Teto de resolução conforme a BANDA já medida no aparelho (estimativa que o ExoPlayer
     * guarda entre sessões). Rede fraca abre em 480/720 e sobe sozinha, rede boa vai até 4K.
     * Antes era 1080p fixo pra todo mundo, aí o trailer demorava pra começar no 4G ruim e
     * nunca passava de Full HD no Wi-Fi bom.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun adaptiveCap(): Int {
        val bps = runCatching {
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate
        }.getOrDefault(0L)
        return when {
            bps <= 0L          -> 1080 // ainda sem medição, começa em HD
            bps >= 15_000_000L -> 2160
            bps >= 8_000_000L  -> 1440
            bps >= 4_000_000L  -> 1080
            bps >= 1_500_000L  -> 720
            else               -> 480
        }
    }

    /** Trailer: NewPipe (googlevideo direto) e cai pro Piped. null se tudo falhar. */
    suspend fun stream(videoKey: String): TrailerStream? = withContext(Dispatchers.IO) {
        if (videoKey.isBlank()) return@withContext null
        val cap = adaptiveCap()
        newPipe(videoKey, cap) ?: piped(videoKey, cap)
    }

    private fun piped(videoKey: String, cap: Int): TrailerStream? {
        for (base in pipedInstances) {
            val r = runCatching {
                val req = okhttp3.Request.Builder().url("$base/streams/$videoKey")
                    .header("User-Agent", USER_AGENT).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string() ?: return@use null)
                    if (json.optString("error").isNotBlank()) return@use null
                    val vs = json.optJSONArray("videoStreams")
                    // 1) DASH HD: melhor vídeo-only (mp4, cap 1080p) + melhor áudio (mesclados).
                    if (vs != null) {
                        var vUrl: String? = null; var vQ = -1
                        for (i in 0 until vs.length()) {
                            val o = vs.getJSONObject(i)
                            if (!o.optBoolean("videoOnly", false)) continue
                            if (!o.optString("mimeType").contains("mp4", true)) continue
                            val q = res(o.optString("quality"))
                            if (q in 1..cap && q > vQ) { vQ = q; vUrl = o.optString("url") }
                        }
                        val aud = json.optJSONArray("audioStreams")
                        var aUrl: String? = null; var aBr = -1
                        if (aud != null) for (i in 0 until aud.length()) {
                            val o = aud.getJSONObject(i)
                            if (!o.optString("mimeType").contains("mp4", true) && !o.optString("mimeType").contains("m4a", true)) continue
                            val br = o.optInt("bitrate", 0)
                            if (br > aBr) { aBr = br; aUrl = o.optString("url") }
                        }
                        if (vUrl != null && aUrl != null) return@use TrailerStream(vUrl, aUrl)
                        // 2) Progressivo muxado (videoOnly=false) se não houver DASH.
                        var bestUrl: String? = null; var bestQ = -1
                        for (i in 0 until vs.length()) {
                            val o = vs.getJSONObject(i)
                            if (o.optBoolean("videoOnly", true)) continue
                            val q = res(o.optString("quality"))
                            if (q in 1..cap && q > bestQ) { bestQ = q; bestUrl = o.optString("url") }
                        }
                        if (bestUrl != null) return@use TrailerStream(bestUrl, null)
                    }
                    // 3) HLS como último recurso.
                    val hls = json.optString("hls").takeIf { it.isNotBlank() && it != "null" }
                    if (hls != null) return@use TrailerStream(hls, null)
                    null
                }
            }.getOrNull()
            if (r != null) return r
        }
        return null
    }

    /**
     * O googlevideo entrega o primeiro minuto de qualquer link, mas sem PO token devolve 403
     * do minuto 1 em diante (cliente iOS e Android hoje). O trailer começava e travava no meio.
     * Pede os últimos bytes do arquivo antes de tocar. Link sem clen passa sem teste.
     */
    private fun servesToEnd(url: String): Boolean {
        val clen = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return true
        val req = okhttp3.Request.Builder().url(url).header("Range", "bytes=${maxOf(0, clen - 1024)}-${clen - 1}")
            .header("User-Agent", USER_AGENT).build()
        return runCatching { client.newCall(req).execute().use { it.code == 206 || it.code == 200 } }.getOrDefault(false)
    }

    private fun newPipe(videoKey: String, cap: Int): TrailerStream? {
        return runCatching {
            ensureInit()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoKey")
            // 1) DASH HD: melhor vídeo-only (cap 1080p) + melhor áudio → mesclados no ExoPlayer.
            //    Dá 720p/1080p (o progressivo muxado do YouTube trava em 360p).
            val vid = info.videoOnlyStreams.filter { !it.content.isNullOrBlank() && res(it.resolution) in 1..cap }
                .maxByOrNull { res(it.resolution) }
            val aud = preferredAudio(
                info.audioStreams.filter { !it.content.isNullOrBlank() },
                trackId = { it.audioTrackId },
                original = { it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL },
                bitrate = { it.averageBitrate },
            )
            if (vid != null && aud != null && servesToEnd(vid.content) && servesToEnd(aud.content)) {
                return@runCatching TrailerStream(vid.content, aud.content)
            }
            // 2) Fallback: progressivo muxado (vídeo+áudio, ~360p) se DASH faltar ou vier cortado.
            val muxed = info.videoStreams.filter { !it.content.isNullOrBlank() }.maxByOrNull { res(it.resolution) }
            if (muxed != null && servesToEnd(muxed.content)) return@runCatching TrailerStream(muxed.content, null)
            null
        }.onFailure { android.util.Log.w("YoutubeTrailer", "extração falhou p/ $videoKey: ${it.message}", it) }
            .getOrNull()
    }
}

/**
 * Áudio do trailer. Português quando existe, senão a faixa original, e só depois qualquer uma.
 * Antes ia o maior bitrate da lista, que em trailer com várias dublagens caía no alemão.
 */
internal fun <T> preferredAudio(
    list: List<T>,
    trackId: (T) -> String?,
    original: (T) -> Boolean,
    bitrate: (T) -> Int,
): T? {
    val pt = list.filter { trackId(it)?.startsWith("pt", ignoreCase = true) == true }
    val orig = list.filter { original(it) || trackId(it) == null }
    return pt.ifEmpty { orig.ifEmpty { list } }.maxByOrNull(bitrate)
}
