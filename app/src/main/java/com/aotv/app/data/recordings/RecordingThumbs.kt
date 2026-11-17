package com.aotv.app.data.recordings

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Miniaturas das gravações (Lote 20, v1.30.0 — pendência do Lote 12).
 *
 * Um frame do .ts (5 s) em JPEG de 320 px, guardado no cache do app
 * (`cacheDir/recording-thumbs/<id>.jpg`). Sem frame (arquivo vazio, .ts
 * que o retriever não lê), volta null e a lista mostra o placeholder —
 * nunca trava a tela. O thumb da concluída é gerado 1x; enquanto grava,
 * gera de novo quando o arquivo cresce (o thumb velho é refeito se o
 * arquivo mudou há mais de 60 s).
 */
object RecordingThumbs {

    /** Arquivo do thumb no cache (cria a pasta). */
    fun thumbnailFile(context: Context, recordingId: String): File =
        File(File(context.cacheDir, "recording-thumbs").apply { mkdirs() }, "$recordingId.jpg")

    /**
     * Garante o thumb da gravação (gera quando falta ou está velho).
     * null = sem thumb (sem arquivo ainda, ou o retriever não leu o .ts).
     */
    fun ensureThumbnail(context: Context, rec: ScheduledRecording, folderUri: String): File? {
        val out = thumbnailFile(context, rec.id)
        val source = resolveSource(context, rec, folderUri) ?: return null
        if (out.exists() && out.length() > 0) {
            // Concluída: o arquivo não muda mais — o thumb vale.
            // Gravando: refaz quando o arquivo cresceu há mais de 60 s.
            val fresh = rec.status == ScheduledRecording.STATUS_DONE ||
                out.lastModified() > System.currentTimeMillis() - 60_000L
            if (fresh) return out
        }
        return runCatching { extract(context, source, out) }.getOrNull()
    }

    /** Origem do vídeo: File local ou Uri SAF (null sem arquivo). */
    private fun resolveSource(context: Context, rec: ScheduledRecording, folderUri: String): Any? {
        if (rec.fileName.isBlank()) return null
        if (rec.movedToDestination) {
            if (folderUri.isBlank()) return null
            return runCatching {
                var dir = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: return null
                val store = RecordingStore(context)
                val folder = store.folderNameFor(rec)
                if (folder.isNotBlank()) dir = dir.findFile(folder) ?: return null
                dir.findFile(rec.fileName)?.uri
            }.getOrNull()
        }
        return RecordingService.localFileFor(context, rec)
    }

    private fun extract(context: Context, source: Any, out: File): File? {
        val retriever = MediaMetadataRetriever()
        try {
            when (source) {
                is File -> retriever.setDataSource(source.absolutePath)
                is Uri -> retriever.setDataSource(context, source)
                else -> return null
            }
            val frame = retriever.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val width = 320
            val height = (width.toFloat() * frame.height / frame.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
            FileOutputStream(out).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            }
            if (scaled != frame) frame.recycle()
            return out.takeIf { it.exists() && it.length() > 0 }
        } finally {
            runCatching { retriever.release() }
        }
    }
}
