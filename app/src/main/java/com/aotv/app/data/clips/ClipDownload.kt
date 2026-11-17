package com.aotv.app.data.clips

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.aotv.app.data.local.MobileDataUsage
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.dao.DownloadDao
import com.aotv.app.data.local.entity.DownloadEntity
import com.aotv.app.data.remote.AppDns
import com.aotv.app.data.torrent.TorrentMagnet
import com.aotv.app.ui.movies.STREAM_HEADERS
import com.aotv.app.ui.movies.splitKodiStreamHeaders
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext

/**
 * Baixar trechos específicos (clipes) — Lote 18 item 10 (v1.28.0).
 *
 * DESENHO HONESTO (o que dá e o que não dá, sem inventar):
 * - HLS (.m3u8) com segmentos MPEG-TS: DÁ. A playlist lista cada segmento com
 *   duração; o clipe baixa só os segmentos que cobrem o intervalo escolhido e
 *   salva tudo num único arquivo .ts, na ordem. O início/fim exato do arquivo
 *   cai na borda do segmento (em geral alguns segundos de diferença do ponto
 *   pedido) — o arquivo cobre o trecho inteiro pedido, nunca menos.
 * - HLS fragmentado (fMP4, `#EXT-X-MAP`, segmentos .m4s) e HLS cifrado
 *   (AES-128, `#EXT-X-KEY`): NÃO DÁ sem remontar/descriptografar — concatenar
 *   os bytes crus geraria um arquivo que não toca. O botão avisa o motivo.
 * - MP4 direto: NÃO DÁ sem ffmpeg no app. Um corte por faixa de bytes
 *   produziria um arquivo sem o `moov` (ou começando no meio de um GOP),
 *   que não toca. O botão avisa e o download do conteúdo inteiro (Lote 5)
 *   continua sendo o caminho.
 * - Torrent/magnet (incluindo o proxy local 127.0.0.1): NÃO DÁ — as peças do
 *   torrent não mapeiam para tempo. O botão avisa o motivo.
 * - Ao vivo (sem duração): não há trecho para baixar; o chip nem aparece.
 *
 * O clipe salvo é um [DownloadEntity] comum (sem mudança de esquema do Room):
 * o título já vem com o intervalo ("Filme (12:34–15:20)"), o id carrega o
 * marcador ":clip:" e o `downloadId` é 0 (não passa pelo DownloadManager).
 * Na tela de Downloads, o clipe entra pelo slot
 * [com.aotv.app.ui.movies.DownloadsViewModel.clipEntries] (o ViewModel
 * separa as entidades com ":clip:" da lista principal e as mapeia para
 * [com.aotv.app.ui.movies.ClipDownloadEntry]) — a tela renderiza o clipe
 * como ClipDownloadRow nas abas Completos/Restos, com o intervalo no
 * subtítulo ("Trecho · 12:34–15:20 — pronto p/ assistir"), e o toque toca
 * o .ts local no player. Sem o filtro, o clipe apareceria duas vezes.
 * O "Só Wi-Fi" é respeitado: com ele ligado e sem Wi-Fi, o clipe nem começa
 * ([ClipResult.BlockedWifi]) — não cria entidade "queued", porque o
 * [com.aotv.app.data.repository.DownloadRepository.resumeQueued] retomaria
 * a fila como download .mp4 inteiro. A pasta SAF escolhida é respeitada
 * (o arquivo é movido para lá na conclusão, como o Lote 5 faz).
 */

/** Um segmento HLS com seu início (ms desde o começo do conteúdo). */
data class ClipSegment(val url: String, val startMs: Long, val durationMs: Long)

/** Playlist de mídia HLS já lida (sem o master — ver [ClipDownload.masterVariantUrl]). */
data class HlsMediaPlaylist(
    val segments: List<ClipSegment>,
    /** Tem `#EXT-X-MAP` (fMP4): os segmentos não são .ts concatenável. */
    val hasInitMap: Boolean,
    /** Tem `#EXT-X-KEY` com cifra (AES-128 / SAMPLE-AES): não remontável em claro. */
    val encrypted: Boolean,
)

object ClipDownload {

    /** O que a fonte atual permite em matéria de trecho. */
    sealed class ClipEligibility {
        /** HLS com playlist .m3u8 — o único formato com trecho. */
        object Hls : ClipEligibility()
        /** Não permite trecho; [reason] é o texto mostrado ao usuário, sem inventar. */
        data class Unsupported(val reason: String) : ClipEligibility()
    }

    /** "12:34" ou "1:02:03" (mesmo formato de [com.aotv.app.ui.movies.formatTime]). */
    fun formatClipTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Intervalo válido: dentro da duração, início antes do fim, pelo menos 1 s. */
    fun clipRangeValid(startMs: Long, endMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && startMs >= 0 && endMs > startMs && endMs <= durationMs &&
            endMs - startMs >= 1_000

    /** Nome exibido do clipe: "Filme (12:34–15:20)" — é o título guardado na entidade. */
    fun clipDisplayTitle(title: String, startMs: Long, endMs: Long): String =
        "$title (${formatClipTime(startMs)}–${formatClipTime(endMs)})"

    /** Nome do arquivo no disco (só [A-Za-z0-9._-], sem ":" — seguro p/ SAF/MTP). */
    fun clipFileName(title: String, startMs: Long, endMs: Long): String {
        val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50).trim('_')
            .ifBlank { "trecho" }
        return "${safe}_(${clipTimeForFile(startMs)}-${clipTimeForFile(endMs)}).ts"
    }

    private fun clipTimeForFile(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "${h}h${m}m${sec}s" else "${m}m${sec}s"
    }

    /** Id da entidade do clipe — o ":clip:" distingue do download inteiro do mesmo título. */
    fun clipId(tmdbId: Int, type: String, lastEpisode: String, startMs: Long, endMs: Long): String =
        "$tmdbId:$type:$lastEpisode:clip:$startMs-$endMs"

    fun isClipId(id: String): Boolean = ":clip:" in id

    /** Classifica a fonte atual: só HLS permite trecho; o resto devolve o motivo. */
    fun classifyClipSource(rawUrl: String): ClipEligibility {
        val (url, _) = splitKodiStreamHeaders(rawUrl)
        val clean = url.trim()
        if (clean.isBlank()) return ClipEligibility.Unsupported("Sem fonte ativa para baixar o trecho.")
        if (TorrentMagnet.isMagnet(clean) || clean.contains("127.0.0.1")) {
            return ClipEligibility.Unsupported(
                "Torrent não permite baixar trecho: as peças do torrent não mapeiam para tempo — baixe o conteúdo inteiro.",
            )
        }
        if (clean.startsWith("file://") || clean.startsWith("content://")) {
            return ClipEligibility.Unsupported(
                "O arquivo já está no aparelho — o trecho precisaria de corte, que o app não faz sem ffmpeg.",
            )
        }
        val base = clean.substringBefore('?').substringBefore('#')
        if (base.endsWith(".m3u8", ignoreCase = true)) return ClipEligibility.Hls
        if (base.endsWith(".mp4", ignoreCase = true)) {
            return ClipEligibility.Unsupported(
                "MP4 direto não permite baixar trecho: sem ffmpeg não dá para cortar o arquivo num ponto exato — baixe o conteúdo inteiro.",
            )
        }
        return ClipEligibility.Unsupported("Esta fonte não permite baixar trecho (só HLS).")
    }

    /** Resolve uma URI relativa contra a URL da playlist (absoluta passa intacta). */
    fun resolveUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return runCatching { java.net.URL(java.net.URL(baseUrl), relative).toString() }
            .getOrDefault(relative)
    }

    /**
     * URL da variante de maior BANDWIDTH de um master playlist, ou null se não
     * for um master (aí a própria playlist já é a de mídia). Empate de
     * BANDWIDTH fica com a primeira variante.
     */
    fun masterVariantUrl(playlistText: String, baseUrl: String): String? {
        val lines = playlistText.lines()
        var bestBandwidth = -1L
        var bestUri: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(line)
                    ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) j++
                val uri = lines.getOrNull(j)?.trim()
                if (uri != null && bw > bestBandwidth) {
                    bestBandwidth = bw
                    bestUri = uri
                }
                i = j
            } else {
                i++
            }
        }
        return bestUri?.let { resolveUrl(baseUrl, it) }
    }

    /** Lê uma playlist de mídia HLS: segmentos com início/duração + flags de fMP4/cifra. */
    fun parseHlsSegments(playlistText: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<ClipSegment>()
        var hasInitMap = false
        var encrypted = false
        var cursorMs = 0L
        var pendingMs: Long? = null
        for (raw in playlistText.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MAP:") -> hasInitMap = true
                line.startsWith("#EXT-X-KEY:") -> {
                    // METHOD=NONE é claro; qualquer outro método cifra os segmentos.
                    if (!line.contains("METHOD=NONE")) encrypted = true
                }
                line.startsWith("#EXTINF:") -> {
                    pendingMs = line.substringAfter("#EXTINF:")
                        .substringBefore(",").toDoubleOrNull()
                        ?.let { (it * 1000).toLong() }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    // Segmento sem #EXTINF não tem duração — sem tempo, não entra no clipe.
                    val durMs = pendingMs
                    if (durMs != null && durMs > 0) {
                        segments.add(ClipSegment(resolveUrl(baseUrl, line), cursorMs, durMs))
                        cursorMs += durMs
                    }
                    pendingMs = null
                }
            }
        }
        return HlsMediaPlaylist(segments, hasInitMap, encrypted)
    }

    /** Segmentos que se sobrepõem ao intervalo [startMs, endMs) — bordas no segmento inteiro. */
    fun selectClipSegments(
        segments: List<ClipSegment>,
        startMs: Long,
        endMs: Long,
    ): List<ClipSegment> =
        segments.filter { it.startMs < endMs && it.startMs + it.durationMs > startMs }
}

/** Resultado do download do trecho (o `when` que lê isto cobre os 4 ramos). */
sealed class ClipResult {
    data class Done(val displayTitle: String, val fileName: String, val segments: Int, val bytes: Long) : ClipResult()
    data class Failed(val message: String) : ClipResult()
    data class Unsupported(val reason: String) : ClipResult()
    object BlockedWifi : ClipResult()
}

/** Acesso ao DAO fora de um componente Hilt (o PlayerSession é criado à mão). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClipDownloadEntryPoint {
    fun downloadDao(): DownloadDao
}

/**
 * Baixa o trecho HLS e registra o clipe como [DownloadEntity] (status
 * "downloading" → "done"/"failed"). Roda tudo em [Dispatchers.IO]; o
 * cancelamento da coroutine apaga o parcial e marca "failed".
 */
class ClipDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val dao: DownloadDao by lazy {
        EntryPointAccessors.fromApplication(appContext, ClipDownloadEntryPoint::class.java).downloadDao()
    }
    private val prefs: PlatformPrefs by lazy { PlatformPrefs(appContext) }
    private val client: OkHttpClient by lazy { AppDns.mediaClient }

    suspend fun downloadClip(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        lastEpisode: String,
        streamUrl: String,
        startMs: Long,
        endMs: Long,
        onProgress: ((downloaded: Int, total: Int) -> Unit)? = null,
    ): ClipResult = withContext(Dispatchers.IO) {
        when (val eligibility = ClipDownload.classifyClipSource(streamUrl)) {
            ClipDownload.ClipEligibility.Hls -> { /* segue */ }
            is ClipDownload.ClipEligibility.Unsupported ->
                return@withContext ClipResult.Unsupported(eligibility.reason)
        }
        // "Só Wi-Fi" (Lote 5): sem Wi-Fi o clipe nem começa — sem entidade
        // "queued" (ver o KDoc do arquivo: a fila retomaria como .mp4 inteiro).
        if (prefs.downloadsWifiOnly && !MobileDataUsage.isWifiActive(appContext)) {
            return@withContext ClipResult.BlockedWifi
        }
        // A duração real já foi validada no diálogo; aqui só a sanidade do
        // intervalo (início antes do fim, pelo menos 1 s).
        if (startMs < 0 || endMs <= startMs || endMs - startMs < 1_000) {
            return@withContext ClipResult.Failed("Intervalo inválido para o trecho.")
        }
        val (cleanUrl, extraHeaders) = splitKodiStreamHeaders(streamUrl)
        val headers = STREAM_HEADERS + extraHeaders
        val id = ClipDownload.clipId(tmdbId, type, lastEpisode, startMs, endMs)
        val displayTitle = ClipDownload.clipDisplayTitle(title, startMs, endMs)
        val fileName = ClipDownload.clipFileName(title, startMs, endMs)
        if (dao.get(id)?.status == "downloading") {
            return@withContext ClipResult.Failed("Esse trecho já está baixando.")
        }
        dao.upsert(
            DownloadEntity(
                id = id, tmdbId = tmdbId, type = type, title = displayTitle,
                posterUrl = posterUrl, sourceUrl = streamUrl, lastEpisode = lastEpisode,
                downloadId = 0, status = "downloading", createdAt = System.currentTimeMillis(),
            ),
        )
        val moviesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: appContext.filesDir
        moviesDir.mkdirs()
        val tmpFile = File(moviesDir, "$fileName.part")
        val finalFile = File(moviesDir, fileName)
        try {
            // Master playlist → variante de maior BANDWIDTH (até 3 saltos).
            var playlistUrl = cleanUrl
            var playlistText = fetchText(playlistUrl, headers)
            repeat(3) {
                val variant = ClipDownload.masterVariantUrl(playlistText, playlistUrl) ?: return@repeat
                playlistUrl = variant
                playlistText = fetchText(playlistUrl, headers)
            }
            val playlist = ClipDownload.parseHlsSegments(playlistText, playlistUrl)
            if (playlist.hasInitMap) {
                throw IOException(
                    "HLS fragmentado (fMP4): os segmentos precisam de remontagem, que o app não faz.",
                )
            }
            if (playlist.encrypted) {
                throw IOException(
                    "HLS cifrado (AES-128): os segmentos não remontam sem descriptografar.",
                )
            }
            val wanted = ClipDownload.selectClipSegments(playlist.segments, startMs, endMs)
            if (wanted.isEmpty()) {
                throw IOException("Nenhum segmento cobre esse intervalo.")
            }
            var bytes = 0L
            tmpFile.outputStream().buffered().use { out ->
                wanted.forEachIndexed { index, segment ->
                    currentCoroutineContext().ensureActive()
                    bytes += fetchSegmentTo(segment.url, headers, out)
                    onProgress?.invoke(index + 1, wanted.size)
                }
            }
            if (!tmpFile.renameTo(finalFile)) {
                tmpFile.copyTo(finalFile, overwrite = true)
                tmpFile.delete()
            }
            // Pasta SAF escolhida (Lote 5): move na conclusão; se falhar, o
            // arquivo fica na pasta do app — nunca se perde um clipe por isso.
            val localUri = moveToDownloadFolder(fileName, finalFile)
                ?: "file://${finalFile.absolutePath}"
            dao.updateStatus(id, "done", localUri)
            ClipResult.Done(displayTitle, fileName, wanted.size, bytes)
        } catch (e: CancellationException) {
            runCatching { tmpFile.delete() }
            runCatching { dao.updateStatus(id, "failed", "") }
            throw e
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            runCatching { finalFile.delete() }
            runCatching { dao.updateStatus(id, "failed", "") }
            ClipResult.Failed(e.message?.take(200) ?: "Falha no download do trecho.")
        }
    }

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} na playlist")
            return resp.body?.string() ?: throw IOException("Resposta sem corpo na playlist")
        }
    }

    private fun fetchSegmentTo(url: String, headers: Map<String, String>, out: OutputStream): Long {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} no segmento")
            resp.body?.byteStream()?.use { input -> return input.copyTo(out) }
                ?: throw IOException("Resposta sem corpo no segmento")
        }
    }

    /**
     * Move o clipe concluído p/ a pasta escolhida (SAF) — mesmo padrão do
     * [com.aotv.app.data.repository.DownloadRepository.moveToDownloadFolder]:
     * cria o documento, copia, e só apaga o original depois da cópia
     * completa. Sem pasta escolhida, devolve null (fica na pasta do app).
     */
    private fun moveToDownloadFolder(fileName: String, srcFile: File): String? {
        val treeUriStr = prefs.downloadFolderUri
        if (treeUriStr.isBlank()) return null
        return runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri),
            )
            val destDoc = DocumentsContract.createDocument(
                appContext.contentResolver, rootDoc, "video/mp2t", fileName,
            ) ?: return@runCatching null
            appContext.contentResolver.openInputStream(Uri.fromFile(srcFile))?.use { input ->
                appContext.contentResolver.openOutputStream(destDoc)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            if (srcFile.delete()) destDoc.toString() else null
        }.getOrNull()
    }
}
