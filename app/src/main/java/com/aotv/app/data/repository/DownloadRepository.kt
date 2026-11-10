package com.aotv.app.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.aotv.app.data.local.dao.DownloadDao
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.ui.movies.STREAM_HEADERS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baixa filmes/episódios de fontes .mp4 diretas via DownloadManager do sistema
 * (gerencia notificação, rede e retomada). Arquivo vai p/ a pasta privada do app
 * (sem permissão de storage). O player toca o arquivo local depois.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
) {
    private val dm by lazy { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    fun all(): Flow<List<DownloadEntity>> = dao.getAll()

    /** Só .mp4 direto é baixável (HLS/embed não). */
    fun isDownloadable(url: String): Boolean =
        url.startsWith("http") && url.substringBefore('?').endsWith(".mp4", ignoreCase = true)

    suspend fun get(id: String): DownloadEntity? = dao.get(id)

    /** Enfileira o download. id = "tmdbId:type:lastEpisode". */
    suspend fun enqueue(
        tmdbId: Int, type: String, title: String, posterUrl: String,
        sourceUrl: String, lastEpisode: String = "",
    ): Boolean {
        if (!isDownloadable(sourceUrl)) return false
        val id = "$tmdbId:$type:$lastEpisode"
        val existingStatus = dao.get(id)?.status
        if (existingStatus == "done" || existingStatus == "downloading") return true // já baixado ou baixando
        val safeName = (title + if (lastEpisode.isNotBlank()) "_$lastEpisode" else "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val req = DownloadManager.Request(Uri.parse(sourceUrl))
            .setTitle(title)
            .setDescription(if (lastEpisode.isNotBlank()) lastEpisode else "Filme")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "$safeName.mp4")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        // Headers que a fonte exige (Referer/User-Agent) — sem isso, fonte que os cobra falha o download.
        STREAM_HEADERS.forEach { (k, v) -> req.addRequestHeader(k, v) }
        val downloadId = runCatching { dm.enqueue(req) }.getOrElse { return false }
        dao.upsert(
            DownloadEntity(
                id = id, tmdbId = tmdbId, type = type, title = title, posterUrl = posterUrl,
                sourceUrl = sourceUrl, lastEpisode = lastEpisode, downloadId = downloadId,
                status = "downloading", createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    /** Consulta o DownloadManager e atualiza status/localUri dos downloads em andamento. */
    suspend fun refreshStatuses() {
        dao.getDownloading().forEach { d ->
            val q = DownloadManager.Query().setFilterById(d.downloadId)
            runCatching {
                dm.query(q)?.use { c ->
                    if (c.moveToFirst()) {
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: ""
                                dao.updateStatus(d.id, "done", uri)
                            }
                            DownloadManager.STATUS_FAILED -> dao.updateStatus(d.id, "failed", "")
                            else -> {} // running/pending — segue
                        }
                    }
                }
            }
        }
    }

    /** Progresso (0..100) por id dos downloads em andamento, consultando o DownloadManager. */
    suspend fun progress(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        dao.getDownloading().forEach { d ->
            runCatching {
                dm.query(DownloadManager.Query().setFilterById(d.downloadId))?.use { c ->
                    if (c.moveToFirst()) {
                        val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        out[d.id] = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 100) else 0
                    }
                }
            }
        }
        return out
    }

    suspend fun delete(entity: DownloadEntity) {
        // Cancela no DownloadManager: para o download em andamento, remove da fila/notificação
        // e apaga o arquivo (parcial ou completo) que ele gerencia.
        runCatching { dm.remove(entity.downloadId) }
        // Garantia: apaga o arquivo local manualmente (algumas versões do Android não
        // removem o arquivo em getExternalFilesDir via dm.remove).
        runCatching {
            entity.localUri.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it).path }
                ?.let { java.io.File(it).delete() }
        }
        dao.delete(entity)
    }
}
