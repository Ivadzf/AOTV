package com.aotv.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prévias na busca — miniaturas na seek bar (v1.45.0, item 5 — ideia do
 * Stremio Origin / Nuvio-Echo, reimplementada).
 *
 * Segurar/esfregar na barra (±10 s, gesto ou botão) mostra a miniatura
 * daquele momento, sem pausar. Dois caminhos, NENHUM com ffmpeg (decisão
 * do Davi: sem ffmpeg por enquanto):
 *
 * - ExoPlayer: [MediaMetadataRetriever.getFrameAtTime] sobre a URL do
 *   stream. Funciona em arquivo progressivo/local; em HLS/DASH o
 *   retriever NÃO extrai quadro e devolve null — nesse caso não há
 *   miniatura (limitação documentada, não erro). Mesmo padrão do
 *   `RecordingThumbs` (que já usa o retriever no app).
 * - mpv: `screenshot-to-file` do quadro atual, via [screenshotToFile] do
 *   [com.aotv.app.ui.movies.MpvPlayer]. O mpv só fotografa o quadro que
 *   está na tela — então a prévia do mpv mostra o quadro DO MOMENTO PÓS-
 *   BUSCA (o seek já aterrissou quando a foto sai, ~300 ms depois do
 *   gesto), não uma prévia "antes de buscar". Sem o quadro ainda, sem
 *   miniatura.
 *
 * Cache em memória por balde de 10 s (a mesma granularidade do gesto):
 * buscar p/ frente e p/ trás no mesmo trecho não extrai de novo. No
 * máximo [MAX_CACHED] miniaturas; o resto cai por uso.
 */
@Singleton
class SeekPreviewService @Inject constructor() {

    private val cache = ConcurrentHashMap<Long, CachedPreview>()

    private class CachedPreview(val at: Long, val bitmap: Bitmap)

    /**
     * Miniatura do [positionMs] p/ um stream progressivo/local (ExoPlayer).
     * HLS/DASH devolve null. Nunca lança exceção. [url] pode ser http(s)
     * ou caminho local.
     */
    fun thumbnailForUrl(url: String, positionMs: Long): Bitmap? {
        if (url.isBlank() || positionMs < 0) return null
        val bucket = positionMs / BUCKET_MS
        cache[bucket]?.let { return it.bitmap }
        val frame = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    retriever.setDataSource(url, emptyMap<String, String>())
                } else {
                    retriever.setDataSource(url)
                }
                retriever.getFrameAtTime(
                    positionMs * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull() ?: return null
        val scaled = scaleDown(frame)
        if (scaled != frame) frame.recycle()
        put(bucket, scaled)
        return scaled
    }

    /**
     * Miniatura a partir de um screenshot que o mpv já escreveu em [file]
     * (o player chama `screenshot-to-file` e passa o arquivo aqui). Arquivo
     * ausente ou inválido = null. Nunca lança exceção.
     */
    fun thumbnailForScreenshotFile(file: File, positionMs: Long): Bitmap? {
        if (!file.exists() || file.length() <= 0) return null
        val bucket = positionMs / BUCKET_MS
        cache[bucket]?.let { return it.bitmap }
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val scaled = scaleDown(decoded)
        if (scaled != decoded) decoded.recycle()
        put(bucket, scaled)
        return scaled
    }

    /** Limpa o cache (troca de título ou de perfil). */
    fun clear() {
        cache.values.forEach { runCatching { it.bitmap.recycle() } }
        cache.clear()
    }

    private fun put(bucket: Long, bitmap: Bitmap) {
        if (cache.size >= MAX_CACHED) {
            cache.keys.minOrNull()?.let { cache.remove(it) }
        }
        cache[bucket] = CachedPreview(System.currentTimeMillis(), bitmap)
    }

    private fun scaleDown(frame: Bitmap): Bitmap {
        if (frame.width <= PREVIEW_WIDTH) return frame
        val height = (PREVIEW_WIDTH.toFloat() * frame.height / frame.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(frame, PREVIEW_WIDTH, height, true)
    }

    companion object {
        /** Granularidade do cache: um balde por 10 s (o passo do gesto). */
        const val BUCKET_MS = 10_000L

        /** Largura da miniatura na HUD. */
        const val PREVIEW_WIDTH = 320

        /** Teto do cache em memória (miniaturas de ~320 px). */
        const val MAX_CACHED = 12
    }
}
