package com.aotv.app.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.dao.DownloadDao
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.ui.movies.STREAM_HEADERS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baixa filmes/episódios de fontes .mp4 diretas via DownloadManager do sistema
 * (gerencia notificação, rede e retomada). Arquivo vai p/ a pasta privada do app
 * (sem permissão de storage). O player toca o arquivo local depois.
 *
 * Lote 5 (v1.14.0) — downloads e dados móveis:
 * - "Só Wi-Fi" ([PlatformPrefs.downloadsWifiOnly]): ligado, download novo em
 *   dados móveis NÃO vai p/ o DownloadManager — fica com status "queued" na
 *   fila e só começa quando o Wi-Fi volta (ou o toggle desliga). O
 *   DownloadManager também recebe setAllowedOverMetered(false), então o
 *   próprio sistema pausa o download se a rede virar móvel no meio.
 *   DESLIGADO (padrão), tudo funciona exatamente como antes.
 * - Pasta de download (SAF): o DownloadManager NÃO aceita destino
 *   content://, então o download sempre cai na pasta do app e, na conclusão,
 *   o arquivo é movido p/ a pasta escolhida ([PlatformPrefs.downloadFolderUri]).
 *   Sem pasta escolhida, ou se a mudança falhar (sem permissão, pasta sumiu),
 *   o arquivo fica na pasta do app — nunca se perde um download por isso.
 * - Dados móveis: cada consulta ao DownloadManager soma ao contador o delta
 *   de COLUMN_BYTES_DOWNLOADED_SO_FAR observado enquanto a rede é móvel (ver
 *   [countMobileBytes]). O streaming do player, torrents e API/pôsteres NÃO
 *   entram na conta — não são mensuráveis daqui (ver [MobileDataUsage]).
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val prefs: PlatformPrefs,
) {
    private val dm by lazy { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    /** Bytes já observados por downloadId do DownloadManager (p/ contar só o delta). */
    private val lastSeenBytes = mutableMapOf<Long, Long>()

    /** Escopo do repo p/ retomar a fila quando o Wi-Fi volta (sem dono de tela). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Wi-Fi voltou: tira da fila os downloads "queued" sem esperar a tela
        // de Downloads abrir. Só observa; o DownloadManager continua mandando.
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (prefs.downloadsWifiOnly && MobileDataUsage.isWifiActive(context)) {
                        scope.launch { runCatching { resumeQueued() } }
                    }
                }
            })
        }
    }

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
        if (existingStatus == "done" || existingStatus == "downloading" || existingStatus == "queued") return true // já baixado, baixando ou na fila
        // "Só Wi-Fi" ligado e sem Wi-Fi: entra na fila, sem tocar no DownloadManager.
        if (prefs.downloadsWifiOnly && !MobileDataUsage.isWifiActive(context)) {
            dao.upsert(
                DownloadEntity(
                    id = id, tmdbId = tmdbId, type = type, title = title, posterUrl = posterUrl,
                    sourceUrl = sourceUrl, lastEpisode = lastEpisode, downloadId = 0,
                    status = "queued", createdAt = System.currentTimeMillis(),
                ),
            )
            return true
        }
        return startDownload(tmdbId, type, title, posterUrl, sourceUrl, lastEpisode, id)
    }

    /** Começa o download no DownloadManager (ou retoma um "queued"). */
    private suspend fun startDownload(
        tmdbId: Int, type: String, title: String, posterUrl: String,
        sourceUrl: String, lastEpisode: String, id: String,
        createdAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val safeName = (title + if (lastEpisode.isNotBlank()) "_$lastEpisode" else "")
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val req = DownloadManager.Request(Uri.parse(sourceUrl))
            .setTitle(title)
            .setDescription(if (lastEpisode.isNotBlank()) lastEpisode else "Filme")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "$safeName.mp4")
            // "Só Wi-Fi" ligado: o próprio sistema não roda em rede medida nem em
            // roaming (a fila "queued" já barra a entrada, isto é o cinto de segurança).
            .setAllowedOverMetered(!prefs.downloadsWifiOnly)
            .setAllowedOverRoaming(!prefs.downloadsWifiOnly)
        // Headers que a fonte exige (Referer/User-Agent) — sem isso, fonte que os cobra falha o download.
        STREAM_HEADERS.forEach { (k, v) -> req.addRequestHeader(k, v) }
        val downloadId = runCatching { dm.enqueue(req) }.getOrElse { return false }
        dao.upsert(
            DownloadEntity(
                id = id, tmdbId = tmdbId, type = type, title = title, posterUrl = posterUrl,
                sourceUrl = sourceUrl, lastEpisode = lastEpisode, downloadId = downloadId,
                status = "downloading", createdAt = createdAt,
            ),
        )
        return true
    }

    /**
     * Tira da fila os downloads "queued" (Wi-Fi voltou ou o toggle desligou).
     * Devolve quantos começaram. Sem Wi-Fi e com "Só Wi-Fi" ligado, não faz nada.
     */
    suspend fun resumeQueued(): Int {
        if (prefs.downloadsWifiOnly && !MobileDataUsage.isWifiActive(context)) return 0
        var resumed = 0
        dao.getQueued().forEach { d ->
            if (startDownload(d.tmdbId, d.type, d.title, d.posterUrl, d.sourceUrl, d.lastEpisode, d.id, d.createdAt)) resumed++
        }
        return resumed
    }

    /** Consulta o DownloadManager e atualiza status/localUri dos downloads em andamento. */
    suspend fun refreshStatuses() {
        // Wi-Fi voltou (ou o toggle desligou) desde a última visita: retoma a fila antes de olhar o resto.
        runCatching { resumeQueued() }
        dao.getDownloading().forEach { d ->
            val q = DownloadManager.Query().setFilterById(d.downloadId)
            runCatching {
                dm.query(q)?.use { c ->
                    if (c.moveToFirst()) {
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                countMobileBytes(d.downloadId, soFar, isFinal = true)
                                val uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: ""
                                dao.updateStatus(d.id, "done", uri)
                                // Pasta SAF escolhida: move o arquivo p/ lá (falha = fica na pasta do app).
                                runCatching { moveToDownloadFolder(d, uri) }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                lastSeenBytes.remove(d.downloadId)
                                dao.updateStatus(d.id, "failed", "")
                            }
                            else -> countMobileBytes(d.downloadId, soFar, isFinal = false) // running/pending — segue
                        }
                    }
                }
            }
        }
    }

    /**
     * Soma ao contador de dados móveis o delta de bytes baixados DESDE A
     * ÚLTIMA OBSERVAÇÃO deste download, só quando a rede ativa é móvel.
     * O valor em bytes vem do DownloadManager (medido, não estimado).
     * - Download nunca observado e concluído: conta o total, atribuído à rede
     *   de agora (o app estava fechado durante o download; o total é real, só
     *   a atribuição à rede é pelo momento da observação).
     * - Download nunca observado e ainda baixando: conta 0 — só contamos
     *   daqui p/ frente, sem inventar o passado.
     * - [progress] NÃO conta: ele roda no mesmo loop da tela de Downloads que
     *   [refreshStatuses]; contar nos dois somaria dobrado.
     */
    private fun countMobileBytes(downloadId: Long, soFar: Long, isFinal: Boolean) {
        val prev = lastSeenBytes[downloadId]
        val delta = when {
            prev == null && isFinal -> soFar.coerceAtLeast(0L)
            prev == null -> 0L
            else -> (soFar - prev).coerceAtLeast(0L)
        }
        if (isFinal) lastSeenBytes.remove(downloadId) else lastSeenBytes[downloadId] = soFar
        if (delta > 0 && MobileDataUsage.isCellularActive(context)) prefs.recordMobileBytes(delta)
    }

    /**
     * Move o arquivo concluído p/ a pasta escolhida pelo usuário (SAF).
     * O DownloadManager não aceita destino content://, então a mudança é
     * sempre pós-download: copia p/ a pasta (DocumentsContract.createDocument)
     * e só apaga o original depois da cópia completa. Sem pasta escolhida,
     * sem permissão persistida ou com qualquer falha, o arquivo fica na pasta
     * do app e o download continua "done" — nunca se perde um download por
     * causa da pasta. O player toca content:// (DefaultDataSource delega).
     */
    private suspend fun moveToDownloadFolder(d: DownloadEntity, localUri: String) {
        val treeUriStr = prefs.downloadFolderUri
        if (treeUriStr.isBlank() || localUri.isBlank() || localUri.startsWith("content://")) return
        withContext(Dispatchers.IO) {
            val srcPath = Uri.parse(localUri).path ?: return@withContext
            val srcFile = java.io.File(srcPath)
            if (!srcFile.exists() || !srcFile.isFile) return@withContext
            val treeUri = Uri.parse(treeUriStr)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri),
            )
            val destDoc = DocumentsContract.createDocument(
                context.contentResolver, rootDoc, "video/mp4", srcFile.name,
            ) ?: return@withContext
            context.contentResolver.openInputStream(Uri.fromFile(srcFile))?.use { input ->
                context.contentResolver.openOutputStream(destDoc)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext
            // Só apaga o original depois da cópia completa.
            if (srcFile.delete()) dao.updateStatus(d.id, "done", destDoc.toString())
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
        // removem o arquivo em getExternalFilesDir via dm.remove). Arquivo já movido
        // p/ a pasta SAF é content:// — apaga pelo ContentResolver, não por File.
        runCatching {
            val uriStr = entity.localUri
            if (uriStr.isNotBlank()) {
                if (uriStr.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(uriStr), null, null)
                } else {
                    Uri.parse(uriStr).path?.let { java.io.File(it).delete() }
                }
            }
        }
        dao.delete(entity)
    }
}
