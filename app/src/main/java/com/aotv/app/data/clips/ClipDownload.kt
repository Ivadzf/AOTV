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
 * DESENHO HONESTO (o que dá e o que não dá, sem inventar) — Lote mpv 2
 * (v1.37.0) amplia o HLS; o resto continua como era:
 * - HLS (.m3u8) com segmentos MPEG-TS: DÁ. A playlist lista cada segmento com
 *   duração; o clipe baixa só os segmentos que cobrem o intervalo escolhido e
 *   salva tudo num único arquivo .ts, na ordem. O início/fim exato do arquivo
 *   cai na borda do segmento (em geral alguns segundos de diferença do ponto
 *   pedido) — o arquivo cobre o trecho inteiro pedido, nunca menos.
 * - HLS cifrado (AES-128, `#EXT-X-KEY`): DÁ (v1.37.0). A chave é buscada na
 *   URI do tag e cada segmento é descriptografado com javax.crypto
 *   (AES/CBC, IV do tag ou nº do segmento) antes de concatenar no .ts.
 *   A chave é cacheada por download (uma busca por URI distinta). Só
 *   AES-128 com KEYFORMAT "identity" (ou sem KEYFORMAT); SAMPLE-AES e
 *   outro KEYFORMAT continuam sem suporte — o botão avisa o motivo.
 * - HLS fragmentado (fMP4, `#EXT-X-MAP`, segmentos .m4s): DÁ (v1.37.0). O
 *   init segment do #EXT-X-MAP é baixado e prefixado aos .m4s do trecho,
 *   num único .mp4 — em geral toca no ExoPlayer/VLC. Limite conhecido:
 *   playlists com `#EXT-X-BYTERANGE` no MAP, ou com `tfdt`/moofs que o
 *   player não aceite em sequência, podem não tocar — o arquivo é
 *   remontado, não re-muxado.
 * - MP4 direto: NÃO DÁ sem ferramenta de corte. O "FFmpeg no app" é o
 *   libmpv (.so do player, via AAR): não existe binário ffmpeg p/ chamar
 *   nem ponte JNI p/ corte de arquivo — um corte por faixa de bytes
 *   produziria um arquivo sem o `moov` (ou começando no meio de um GOP),
 *   que não toca. Cortar MP4 em Kotlin puro exigiria remontar o `moov`
 *   (stbl/stts/stss/stsz/stco/co64/stsc, sync samples, edit lists) — fora
 *   do escopo deste lote. O botão avisa e o download do conteúdo inteiro
 *   (Lote 5) continua sendo o caminho.
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
 * o .ts/.mp4 local no player. Sem o filtro, o clipe apareceria duas vezes.
 * O "Só Wi-Fi" é respeitado: com ele ligado e sem Wi-Fi, o clipe nem começa
 * ([ClipResult.BlockedWifi]) — não cria entidade "queued", porque o
 * [com.aotv.app.data.repository.DownloadRepository.resumeQueued] retomaria
 * a fila como download .mp4 inteiro. A pasta SAF escolhida é respeitada
 * (o arquivo é movido para lá na conclusão, como o Lote 5 faz).
 */

/** Um segmento HLS com seu início (ms desde o começo do conteúdo). */
data class ClipSegment(
    val url: String,
    val startMs: Long,
    val durationMs: Long,
    /** URI da chave AES-128 deste segmento, já resolvida (null = claro). */
    val keyUri: String? = null,
    /** IV em hex (32 dígitos) — explícito do #EXT-X-KEY ou nº do segmento. */
    val keyIvHex: String? = null,
)

/** Playlist de mídia HLS já lida (sem o master — ver [ClipDownload.masterVariantUrl]). */
data class HlsMediaPlaylist(
    val segments: List<ClipSegment>,
    /** Tem `#EXT-X-MAP` (fMP4): os segmentos não são .ts concatenável. */
    val hasInitMap: Boolean,
    /** Tem `#EXT-X-KEY` com cifra AES-128 (descriptografável — ver [ClipSegment.keyUri]). */
    val encrypted: Boolean,
    /** URI do init segment do #EXT-X-MAP, já resolvida (null = sem MAP ou sem URI utilizável). */
    val initSegmentUrl: String? = null,
    /** Cifra sem suporte (SAMPLE-AES ou KEYFORMAT diferente de "identity"). */
    val unsupportedEncryption: Boolean = false,
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
    fun clipFileName(title: String, startMs: Long, endMs: Long, extension: String = "ts"): String {
        val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50).trim('_')
            .ifBlank { "trecho" }
        return "${safe}_(${clipTimeForFile(startMs)}-${clipTimeForFile(endMs)}).$extension"
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
                "O arquivo já está no aparelho — o trecho precisaria de corte, que o app não faz (o FFmpeg do app é só o player, sem corte de arquivo).",
            )
        }
        val base = clean.substringBefore('?').substringBefore('#')
        if (base.endsWith(".m3u8", ignoreCase = true)) return ClipEligibility.Hls
        if (base.endsWith(".mp4", ignoreCase = true)) {
            return ClipEligibility.Unsupported(
                "MP4 direto não permite baixar trecho: cortar MP4 exige remontar o arquivo e o FFmpeg do app é só o player (libmpv), sem corte de arquivo — baixe o conteúdo inteiro.",
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

    /**
     * Atributos de um tag HLS (`CHAVE=valor,CHAVE="valor com vírgula"`) —
     * sem regex frágil: valor entre aspas pode ter vírgula e `=`.
     */
    fun parseHlsAttributes(tagValue: String): Map<String, String> {
        val attrs = LinkedHashMap<String, String>()
        var i = 0
        val n = tagValue.length
        while (i < n) {
            val eq = tagValue.indexOf('=', i)
            if (eq < 0) break
            val key = tagValue.substring(i, eq).trim()
            var j = eq + 1
            val value = if (j < n && tagValue[j] == '"') {
                val end = tagValue.indexOf('"', j + 1)
                val found = if (end < 0) tagValue.substring(j + 1) else tagValue.substring(j + 1, end)
                j = if (end < 0) n else end + 1
                found
            } else {
                val end = tagValue.indexOf(',', j)
                val found = if (end < 0) tagValue.substring(j) else tagValue.substring(j, end)
                j = if (end < 0) n else end
                found.trim()
            }
            if (key.isNotEmpty()) attrs[key] = value
            i = j
            if (i < n && tagValue[i] == ',') i++
        }
        return attrs
    }

    /** IV padrão do HLS: o nº do segmento como 16 bytes big-endian, em hex (32 dígitos). */
    fun segmentSequenceIvHex(mediaSequence: Long): String = "%032x".format(mediaSequence)

    /** Hex (com ou sem `0x`) p/ bytes; null se o texto não for hex par. */
    fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        if (clean.isEmpty() || clean.length % 2 != 0 ||
            clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
        ) return null
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Descriptografa um segmento AES-128/CBC (javax.crypto, sem nativo nem
     * JNI). [key] tem 16 bytes; [iv] tem 16 bytes. Lança se a chave, o IV
     * ou o preenchimento for inválido — o chamador vira IOException.
     */
    fun decryptAes128Segment(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.IvParameterSpec(iv),
        )
        return cipher.doFinal(ciphertext)
    }

    /**
     * Lê uma playlist de mídia HLS: segmentos com início/duração + flags de
     * fMP4/cifra. Cada segmento carrega a chave AES-128 vigente na sua
     * posição (a chave pode trocar no meio da playlist; METHOD=NONE volta
     * a deixar os segmentos em claro). O IV é o do tag, ou o nº do segmento
     * (`#EXT-X-MEDIA-SEQUENCE` + ordem) quando o tag não traz IV.
     */
    fun parseHlsSegments(playlistText: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<ClipSegment>()
        var hasInitMap = false
        var initSegmentUrl: String? = null
        var encrypted = false
        var unsupportedEncryption = false
        var segmentIndex = 0L
        var cursorMs = 0L
        var pendingMs: Long? = null
        var currentKeyUri: String? = null
        var currentKeyIvHex: String? = null
        for (raw in playlistText.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    segmentIndex = line.substringAfter(":").trim().toLongOrNull() ?: segmentIndex
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    hasInitMap = true
                    if (initSegmentUrl == null) {
                        val mapAttrs = parseHlsAttributes(line.substringAfter("#EXT-X-MAP:"))
                        // BYTERANGE no MAP (init é um pedaço de outro arquivo):
                        // sem suporte — o init fica null e o download aborta.
                        if (!mapAttrs.containsKey("BYTERANGE")) {
                            mapAttrs.get("URI")?.takeIf { it.isNotBlank() }?.let {
                                initSegmentUrl = resolveUrl(baseUrl, it)
                            }
                        }
                    }
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseHlsAttributes(line.substringAfter("#EXT-X-KEY:"))
                    val method = attrs.get("METHOD").orEmpty()
                    when {
                        method == "NONE" -> {
                            currentKeyUri = null
                            currentKeyIvHex = null
                        }
                        method == "AES-128" -> {
                            val format = attrs.get("KEYFORMAT").orEmpty()
                            if (format.isNotBlank() && !format.equals("identity", ignoreCase = true)) {
                                unsupportedEncryption = true
                                currentKeyUri = null
                                currentKeyIvHex = null
                            } else {
                                encrypted = true
                                currentKeyUri = attrs.get("URI")?.takeIf { it.isNotBlank() }
                                    ?.let { resolveUrl(baseUrl, it) }
                                currentKeyIvHex = attrs.get("IV")
                            }
                        }
                        else -> {
                            // SAMPLE-AES e outros métodos: amostra a amostra, sem suporte.
                            unsupportedEncryption = true
                            currentKeyUri = null
                            currentKeyIvHex = null
                        }
                    }
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
                        val ivHex = if (currentKeyUri != null) {
                            currentKeyIvHex ?: segmentSequenceIvHex(segmentIndex)
                        } else {
                            null
                        }
                        segments.add(ClipSegment(resolveUrl(baseUrl, line), cursorMs, durMs, currentKeyUri, ivHex))
                        cursorMs += durMs
                    }
                    pendingMs = null
                    segmentIndex++
                }
            }
        }
        return HlsMediaPlaylist(segments, hasInitMap, encrypted, initSegmentUrl, unsupportedEncryption)
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
        // O nome do arquivo depende da playlist (.ts p/ MPEG-TS, .mp4 p/
        // fMP4) — só existe depois de ler a playlist, dentro do try.
        var tmpFile: File? = null
        var finalFile: File? = null
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
            if (playlist.unsupportedEncryption) {
                throw IOException(
                    "HLS com cifra que o app não descriptografa (SAMPLE-AES ou formato de chave desconhecido).",
                )
            }
            val wanted = ClipDownload.selectClipSegments(playlist.segments, startMs, endMs)
            if (wanted.isEmpty()) {
                throw IOException("Nenhum segmento cobre esse intervalo.")
            }
            val isFmp4 = playlist.hasInitMap
            if (isFmp4 && playlist.initSegmentUrl == null) {
                throw IOException(
                    "HLS fragmentado (fMP4) sem init segment utilizável (#EXT-X-MAP sem URI ou com BYTERANGE).",
                )
            }
            val fileName = ClipDownload.clipFileName(title, startMs, endMs, if (isFmp4) "mp4" else "ts")
            val moviesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: appContext.filesDir
            moviesDir.mkdirs()
            val partFile = File(moviesDir, "$fileName.part")
            val destFile = File(moviesDir, fileName)
            tmpFile = partFile
            finalFile = destFile
            var bytes = 0L
            val keyCache = HashMap<String, ByteArray>()
            partFile.outputStream().buffered().use { out ->
                if (isFmp4) {
                    // Init segment primeiro: sem ele os .m4s não tocam.
                    val initUrl = playlist.initSegmentUrl
                    if (initUrl != null) {
                        bytes += fetchSegmentTo(initUrl, headers, out)
                    }
                }
                wanted.forEachIndexed { index, segment ->
                    currentCoroutineContext().ensureActive()
                    bytes += fetchClipSegmentTo(segment, headers, out, keyCache)
                    onProgress?.invoke(index + 1, wanted.size)
                }
            }
            if (!partFile.renameTo(destFile)) {
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
            }
            // Pasta SAF escolhida (Lote 5): move na conclusão; se falhar, o
            // arquivo fica na pasta do app — nunca se perde um clipe por isso.
            val localUri = moveToDownloadFolder(
                fileName, destFile, if (isFmp4) "video/mp4" else "video/mp2t",
            ) ?: "file://${destFile.absolutePath}"
            dao.updateStatus(id, "done", localUri)
            ClipResult.Done(displayTitle, fileName, wanted.size, bytes)
        } catch (e: CancellationException) {
            runCatching { tmpFile?.delete() }
            runCatching { dao.updateStatus(id, "failed", "") }
            throw e
        } catch (e: Exception) {
            runCatching { tmpFile?.delete() }
            runCatching { finalFile?.delete() }
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
     * Baixa um segmento do clipe: em claro, copia os bytes crus; com
     * AES-128, busca a chave (cacheada por URI neste download), valida
     * chave (16 bytes) e IV (16 bytes) e escreve o segmento
     * descriptografado. Lança IOException com motivo legível em qualquer
     * falha — o chamador marca o clipe como "failed" com esse texto.
     */
    private fun fetchClipSegmentTo(
        segment: ClipSegment,
        headers: Map<String, String>,
        out: OutputStream,
        keyCache: MutableMap<String, ByteArray>,
    ): Long {
        val keyUri = segment.keyUri ?: return fetchSegmentTo(segment.url, headers, out)
        val key = keyCache.getOrPut(keyUri) { fetchKeyBytes(keyUri, headers) }
        if (key.size != 16) {
            throw IOException("Chave AES-128 inválida (não tem 16 bytes).")
        }
        val ivHex = segment.keyIvHex ?: throw IOException("Segmento cifrado sem IV.")
        val iv = ClipDownload.hexToBytes(ivHex)?.takeIf { it.size == 16 }
            ?: throw IOException("IV inválido no segmento cifrado.")
        val req = Request.Builder().url(segment.url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} no segmento")
            val body = resp.body ?: throw IOException("Resposta sem corpo no segmento")
            val cipherBytes = body.bytes()
            val plain = try {
                ClipDownload.decryptAes128Segment(cipherBytes, key, iv)
            } catch (e: Exception) {
                throw IOException("Não deu para descriptografar o segmento (chave ou IV inválido).")
            }
            out.write(plain)
            return plain.size.toLong()
        }
    }

    /** Baixa a chave AES-128 da URI do #EXT-X-KEY (mesmos headers do stream). */
    private fun fetchKeyBytes(url: String, headers: Map<String, String>): ByteArray {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} na chave AES-128")
            return resp.body?.bytes() ?: throw IOException("Resposta sem corpo na chave AES-128")
        }
    }

    /**
     * Move o clipe concluído p/ a pasta escolhida (SAF) — mesmo padrão do
     * [com.aotv.app.data.repository.DownloadRepository.moveToDownloadFolder]:
     * cria o documento, copia, e só apaga o original depois da cópia
     * completa. Sem pasta escolhida, devolve null (fica na pasta do app).
     */
    private fun moveToDownloadFolder(fileName: String, srcFile: File, mimeType: String): String? {
        val treeUriStr = prefs.downloadFolderUri
        if (treeUriStr.isBlank()) return null
        return runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri),
            )
            val destDoc = DocumentsContract.createDocument(
                appContext.contentResolver, rootDoc, mimeType, fileName,
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
