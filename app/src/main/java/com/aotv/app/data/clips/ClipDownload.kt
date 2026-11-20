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
 * (v1.37.0) amplia o HLS; o resto continua como era. Lote mpv 3 (v1.38.0):
 * o download tenta as variantes do master EM ORDEM (a em reprodução
 * primeiro, quando a altura dela é conhecida — ver [downloadClip]) e, no
 * 403 de segmento, cai p/ a próxima variante antes de falhar (o ALYA
 * protegia a variante de maior bandwidth); e os cookies da resposta da
 * playlist (Set-Cookie) são repassados p/ os segmentos (o OkHttp do app
 * não tem CookieJar).
 * - HLS (.m3u8) com segmentos MPEG-TS: DÁ. A playlist lista cada segmento com
 *   duração; o clipe baixa só os segmentos que cobrem o intervalo escolhido e
 *   salva tudo num único arquivo .ts, na ordem. O início/fim exato do arquivo
 *   cai na borda do segmento (em geral alguns segundos de diferença do ponto
 *   pedido) — o arquivo cobre o trecho inteiro pedido, nunca menos.
 *   Lote VPN (v1.40.0): os timestamps (PTS/DTS/PCR) são re-basificados
 *   por segmento ([TsRebase]) p/ a linha do tempo ficar contínua desde
 *   ~0 — sem isso o player externo lia duração e relógio errados no
 *   trecho concatenado (começava em 1:57, voltava ~30 s a cada ~5 s).
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
 *
 * Lote mpv 4 — Frente 2 (v1.39.0): exportar o trecho e o vídeo inteiro.
 * Exportar o trecho mora em [ClipExport] (share sheet via FileProvider ou
 * cópia p/ a pasta SAF de Downloads — nunca move: o original continua em
 * Downloads). O vídeo inteiro é [ClipDownloader.downloadFull]: HLS baixa
 * TODOS os segmentos (a mesma maquinaria do trecho, com o intervalo
 * (0, Long.MAX_VALUE)); MP4 direto baixa o arquivo inteiro (sem remontar
 * o `moov`, porque não há corte). A URL master + headers vivem na
 * entidade do clipe ([DownloadEntity.sourceUrl], gravada pelo
 * [ClipDownloader.downloadClip] desde a v1.28.0) — sem mudança de esquema
 * do Room e sem codificar URL no id. Clipes antigos sem `sourceUrl` não
 * têm de onde baixar o vídeo inteiro: a opção avisa, nunca crasha.
 */

/** Uma variante de um master playlist HLS (ver [ClipDownload.listMasterVariants]). */
data class HlsVariant(
    val url: String,
    val bandwidth: Long,
    /** Altura da variante (RESOLUTION), 0 = o master não informa. */
    val height: Int,
)

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

    /**
     * Id da entidade do vídeo inteiro (Lote mpv 4 — Frente 2, v1.39.0): o
     * ":full" distingue do download normal do mesmo título (o id normal é
     * "$tmdbId:$type:$lastEpisode", sem sufixo) e do clipe (":clip:").
     * Vai p/ a lista principal de Downloads, não p/ o slot do clipe.
     */
    fun fullId(tmdbId: Int, type: String, lastEpisode: String): String =
        "$tmdbId:$type:$lastEpisode:full"

    fun isFullId(id: String): Boolean = id.endsWith(":full")

    /** Nome exibido do vídeo inteiro: "Filme (completo)" — é o título guardado na entidade. */
    fun fullDisplayTitle(title: String): String = "$title (completo)"

    /** Nome do arquivo do vídeo inteiro no disco (mesma sanitização do clipe). */
    fun fullFileName(title: String, extension: String = "ts"): String {
        val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(50).trim('_')
            .ifBlank { "video" }
        return "${safe}_(completo).$extension"
    }

    /** O que a fonte atual permite em matéria de vídeo inteiro (Lote mpv 4 — Frente 2). */
    sealed class FullEligibility {
        /** HLS com playlist .m3u8 — baixa todos os segmentos e concatena. */
        object Hls : FullEligibility()
        /** MP4 direto — baixa o arquivo inteiro, sem corte e sem remontar o `moov`. */
        object Mp4 : FullEligibility()
        /** Não permite vídeo inteiro; [reason] é o texto mostrado ao usuário, sem inventar. */
        data class Unsupported(val reason: String) : FullEligibility()
    }

    /**
     * Classifica a fonte p/ o vídeo inteiro (Lote mpv 4 — Frente 2): HLS e
     * MP4 direto dão; torrent e arquivo local não (o torrent já tem o
     * download inteiro pelo Lote 5 e o arquivo local já está no aparelho).
     */
    fun classifyFullSource(rawUrl: String): FullEligibility {
        val (url, _) = splitKodiStreamHeaders(rawUrl)
        val clean = url.trim()
        if (clean.isBlank()) return FullEligibility.Unsupported("Sem fonte salva para baixar o vídeo inteiro.")
        if (TorrentMagnet.isMagnet(clean) || clean.contains("127.0.0.1")) {
            return FullEligibility.Unsupported(
                "Torrent não permite baixar o vídeo inteiro por aqui: use o download normal do conteúdo (o torrent já baixa inteiro pelo Lote 5).",
            )
        }
        if (clean.startsWith("file://") || clean.startsWith("content://")) {
            return FullEligibility.Unsupported("O arquivo já está no aparelho — não há o que baixar.")
        }
        val base = clean.substringBefore('?').substringBefore('#')
        if (base.endsWith(".m3u8", ignoreCase = true)) return FullEligibility.Hls
        if (base.endsWith(".mp4", ignoreCase = true)) return FullEligibility.Mp4
        return FullEligibility.Unsupported("Esta fonte não permite baixar o vídeo inteiro (só HLS e MP4 direto).")
    }

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
    fun masterVariantUrl(playlistText: String, baseUrl: String): String? =
        listMasterVariants(playlistText, baseUrl).maxByOrNull { it.bandwidth }?.url

    /**
     * Todas as variantes de um master playlist (Lote mpv 3, v1.38.0), com
     * BANDWIDTH e altura (RESOLUTION). Lista vazia = não é um master (a
     * própria playlist já é a de mídia). A ordem é a do master.
     */
    fun listMasterVariants(playlistText: String, baseUrl: String): List<HlsVariant> {
        val out = ArrayList<HlsVariant>()
        val lines = playlistText.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseHlsAttributes(line.substringAfter("#EXT-X-STREAM-INF:"))
                val bw = attrs.get("BANDWIDTH")?.toLongOrNull() ?: -1L
                val height = attrs.get("RESOLUTION")?.substringAfter('x', "")?.toIntOrNull() ?: 0
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) j++
                val uri = lines.getOrNull(j)?.trim()
                if (uri != null) out += HlsVariant(resolveUrl(baseUrl, uri), bw, height)
                i = j
            } else {
                i++
            }
        }
        return out
    }

    /**
     * Ordem das variantes p/ o download do trecho (Lote mpv 3, v1.38.0):
     * com a altura em reprodução conhecida, a variante de altura mais
     * próxima dela vem primeiro (é a que o player está tocando — no ALYA
     * a de maior bandwidth é protegida e devolve 403 nos segmentos); sem
     * ela, vale o maior BANDWIDTH primeiro (comportamento de antes).
     * Variantes sem RESOLUTION vão p/ o fim (só com o BANDWIDTH de desempate).
     */
    fun orderVariantsForPlayback(variants: List<HlsVariant>, playingHeight: Int?): List<HlsVariant> {
        if (playingHeight == null || playingHeight <= 0) {
            return variants.sortedByDescending { it.bandwidth }
        }
        val known = variants.filter { it.height > 0 }
            .sortedWith(compareBy({ kotlin.math.abs(it.height - playingHeight) }, { -it.bandwidth }))
        val unknown = variants.filter { it.height <= 0 }.sortedByDescending { it.bandwidth }
        return known + unknown
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
        /**
         * Altura do vídeo em reprodução (Lote mpv 3, v1.38.0): com ela, o
         * clipe tenta primeiro a variante de altura mais próxima (a que
         * está tocando); sem ela, vale o maior BANDWIDTH (como antes).
         * null = desconhecida.
         */
        playingHeight: Int? = null,
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
            // Master playlist → variantes. A playlist carrega, mas os
            // segmentos podem dar 403 numa variante protegida (BESTCINE/
            // ALYA na v1.37.0): por isso o download tenta as variantes em
            // ordem (a em reprodução primeiro, quando conhecida), não só a
            // de maior BANDWIDTH — e repassa os cookies da playlist p/ os
            // segmentos (o OkHttp do app não tem CookieJar).
            val (masterText, masterCookies) = fetchText(cleanUrl, headers)
            val variants = ClipDownload.listMasterVariants(masterText, cleanUrl)
            // Candidatos: (URL da playlist de mídia, texto já buscado).
            // Sem master, a própria playlist já é a de mídia (sem refetch).
            val candidates: List<Pair<String, String?>> = if (variants.isEmpty()) {
                listOf(cleanUrl to masterText)
            } else {
                ClipDownload.orderVariantsForPlayback(variants, playingHeight).map { it.url to null }
            }
            val done = downloadBestVariant(
                candidates = candidates,
                headers = headers,
                masterCookies = masterCookies,
                title = title,
                startMs = startMs,
                endMs = endMs,
                onProgress = onProgress,
                playingHeight = playingHeight,
                fileNameFor = null,
                what = "o trecho",
            )
            tmpFile = null
            finalFile = done.destFile
            // Pasta SAF escolhida (Lote 5): move na conclusão; se falhar, o
            // arquivo fica na pasta do app — nunca se perde um clipe por isso.
            val localUri = moveToDownloadFolder(
                done.fileName, done.destFile, if (done.isFmp4) "video/mp4" else "video/mp2t",
            ) ?: "file://${done.destFile.absolutePath}"
            dao.updateStatus(id, "done", localUri)
            ClipResult.Done(displayTitle, done.fileName, done.segments, done.bytes)
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

    /**
     * Baixa o vídeo INTEIRO a partir da fonte guardada num clipe (Lote mpv
     * 4 — Frente 2, v1.39.0) e registra como [DownloadEntity] com ":full"
     * no id — vai p/ a lista principal de Downloads (não p/ o slot do
     * clipe), com o título "Filme (completo)". A fonte é o `sourceUrl` da
     * entidade do clipe (a URL master + headers Kodi, gravada pelo
     * [downloadClip] desde a v1.28.0): nada de novo é persistido, e o
     * `playingHeight` não é guardado — sem ele, vale o maior BANDWIDTH.
     * - HLS: baixa TODOS os segmentos (intervalo (0, Long.MAX_VALUE)) com
     *   a mesma maquinaria do trecho — ordem de variantes, fallback só no
     *   403, cookies, AES-128 e fMP4.
     * - MP4 direto: baixa o arquivo inteiro, sem corte e sem remontar o
     *   `moov` (não há corte aqui).
     * O progresso ([onProgress]) é (feito, total): segmentos no HLS e KB
     * no MP4 — nos dois casos a % é feito * 100 / total quando total > 0.
     * "Só Wi-Fi" e pasta SAF valem como no trecho. Clipes sem `sourceUrl`
     * (antigos, de antes da URL ser guardada) devolvem [ClipResult.Failed]
     * com motivo — nunca crash.
     */
    suspend fun downloadFull(
        tmdbId: Int,
        type: String,
        title: String,
        posterUrl: String,
        lastEpisode: String,
        streamUrl: String,
        onProgress: ((downloaded: Int, total: Int) -> Unit)? = null,
        playingHeight: Int? = null,
    ): ClipResult = withContext(Dispatchers.IO) {
        val eligibility = ClipDownload.classifyFullSource(streamUrl)
        when (eligibility) {
            ClipDownload.FullEligibility.Hls -> { /* segue */ }
            ClipDownload.FullEligibility.Mp4 -> { /* segue */ }
            is ClipDownload.FullEligibility.Unsupported ->
                return@withContext ClipResult.Unsupported(eligibility.reason)
        }
        // "Só Wi-Fi" (Lote 5): igual ao trecho — sem Wi-Fi nem começa e
        // não cria entidade "queued" (a fila retomaria como .mp4 inteiro).
        if (prefs.downloadsWifiOnly && !MobileDataUsage.isWifiActive(appContext)) {
            return@withContext ClipResult.BlockedWifi
        }
        val (cleanUrl, extraHeaders) = splitKodiStreamHeaders(streamUrl)
        val headers = STREAM_HEADERS + extraHeaders
        val id = ClipDownload.fullId(tmdbId, type, lastEpisode)
        val displayTitle = ClipDownload.fullDisplayTitle(title)
        if (dao.get(id)?.status == "downloading") {
            return@withContext ClipResult.Failed("O vídeo inteiro já está baixando.")
        }
        dao.upsert(
            DownloadEntity(
                id = id, tmdbId = tmdbId, type = type, title = displayTitle,
                posterUrl = posterUrl, sourceUrl = streamUrl, lastEpisode = lastEpisode,
                downloadId = 0, status = "downloading", createdAt = System.currentTimeMillis(),
            ),
        )
        var tmpFile: File? = null
        var finalFile: File? = null
        try {
            val done: VariantAttempt = if (eligibility is ClipDownload.FullEligibility.Hls) {
                val (masterText, masterCookies) = fetchText(cleanUrl, headers)
                val variants = ClipDownload.listMasterVariants(masterText, cleanUrl)
                val candidates: List<Pair<String, String?>> = if (variants.isEmpty()) {
                    listOf(cleanUrl to masterText)
                } else {
                    ClipDownload.orderVariantsForPlayback(variants, playingHeight).map { it.url to null }
                }
                downloadBestVariant(
                    candidates = candidates,
                    headers = headers,
                    masterCookies = masterCookies,
                    title = title,
                    startMs = 0L,
                    endMs = Long.MAX_VALUE,
                    onProgress = onProgress,
                    playingHeight = playingHeight,
                    fileNameFor = { extension -> ClipDownload.fullFileName(title, extension) },
                    what = "o vídeo inteiro",
                )
            } else if (eligibility is ClipDownload.FullEligibility.Mp4) {
                downloadMp4Direct(
                    url = cleanUrl,
                    headers = headers,
                    fileName = ClipDownload.fullFileName(title, "mp4"),
                    onProgress = onProgress,
                )
            } else {
                // Inalcançável: o Unsupported já retornou lá em cima.
                throw IOException("Fonte sem suporte para o vídeo inteiro.")
            }
            tmpFile = null
            finalFile = done.destFile
            val localUri = moveToDownloadFolder(
                done.fileName, done.destFile, if (done.isFmp4) "video/mp4" else "video/mp2t",
            ) ?: "file://${done.destFile.absolutePath}"
            dao.updateStatus(id, "done", localUri)
            ClipResult.Done(displayTitle, done.fileName, done.segments, done.bytes)
        } catch (e: CancellationException) {
            runCatching { tmpFile?.delete() }
            runCatching { dao.updateStatus(id, "failed", "") }
            throw e
        } catch (e: Exception) {
            runCatching { tmpFile?.delete() }
            runCatching { finalFile?.delete() }
            runCatching { dao.updateStatus(id, "failed", "") }
            ClipResult.Failed(e.message?.take(200) ?: "Falha no download do vídeo inteiro.")
        }
    }

    /**
     * Tenta as variantes do master EM ORDEM e devolve a primeira que baixa
     * (Lote mpv 4 — Frente 2: extraído do [downloadClip] sem mudar o
     * comportamento, p/ o [downloadFull] reaproveitar). Só o 403 troca de
     * variante; outro erro desiste na hora. [fileNameFor] dá o nome do
     * arquivo por extensão (null = nome de clipe); [what] é o que está
     * sendo baixado, p/ a mensagem de erro ("o trecho" / "o vídeo inteiro").
     */
    private suspend fun downloadBestVariant(
        candidates: List<Pair<String, String?>>,
        headers: Map<String, String>,
        masterCookies: String?,
        title: String,
        startMs: Long,
        endMs: Long,
        onProgress: ((downloaded: Int, total: Int) -> Unit)?,
        playingHeight: Int?,
        fileNameFor: ((String) -> String)?,
        what: String,
    ): VariantAttempt {
        var lastError: String? = null
        var attempt: VariantAttempt? = null
        for ((variantUrl, prefetchedText) in candidates) {
            try {
                attempt = downloadVariantAttempt(
                    variantUrl = variantUrl,
                    headers = headers,
                    masterCookies = masterCookies,
                    title = title,
                    startMs = startMs,
                    endMs = endMs,
                    onProgress = onProgress,
                    playingHeight = playingHeight,
                    prefetchedText = prefetchedText,
                    fileNameFor = fileNameFor,
                )
                break
            } catch (e: IOException) {
                lastError = e.message
                if (!e.message.orEmpty().contains("HTTP 403")) throw e
            }
        }
        return attempt ?: throw IOException(
            "Nenhuma variante baixou $what (tentei ${candidates.size} variante(s); último erro: ${lastError ?: "desconhecido"})." +
                if (playingHeight == null) " A variante em reprodução era desconhecida." else "",
        )
    }

    /**
     * Baixa um MP4 direto inteiro (Lote mpv 4 — Frente 2): GET com os
     * headers do stream e cópia por streaming p/ o arquivo, sem remontar
     * nada — o arquivo que chega é o arquivo final. O progresso é em KB
     * (baixado, total; total 0 = tamanho desconhecido) e só é reportado a
     * cada 512 KB, p/ não afogar a UI. Lança IOException em qualquer falha
     * — o parcial é apagado pelo chamador.
     */
    private suspend fun downloadMp4Direct(
        url: String,
        headers: Map<String, String>,
        fileName: String,
        onProgress: ((downloaded: Int, total: Int) -> Unit)?,
    ): VariantAttempt {
        val moviesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: appContext.filesDir
        moviesDir.mkdirs()
        val partFile = File(moviesDir, "$fileName.part")
        val destFile = File(moviesDir, fileName)
        try {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            var bytes = 0L
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} no vídeo")
                val body = resp.body ?: throw IOException("Resposta sem corpo no vídeo")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: 0L
                val totalKb = (totalBytes / 1024).toInt()
                var lastReportedKb = 0
                body.byteStream().use { input ->
                    partFile.outputStream().buffered().use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            bytes += read
                            val downloadedKb = (bytes / 1024).toInt()
                            if (totalKb > 0 && downloadedKb - lastReportedKb >= 512) {
                                lastReportedKb = downloadedKb
                                onProgress?.invoke(downloadedKb, totalKb)
                            }
                        }
                    }
                }
                if (totalKb > 0) onProgress?.invoke((bytes / 1024).toInt(), totalKb)
            }
            if (!partFile.renameTo(destFile)) {
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
            }
            return VariantAttempt(fileName, true, 1, bytes, destFile)
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            throw e
        }
    }

    /** Resultado de uma tentativa de variante (o arquivo já está no disco). */
    private data class VariantAttempt(
        val fileName: String,
        val isFmp4: Boolean,
        val segments: Int,
        val bytes: Long,
        val destFile: File,
    )

    /**
     * Uma tentativa de download do trecho numa variante: lê a playlist de
     * mídia, baixa os segmentos do intervalo e salva o arquivo. Lança
     * IOException em qualquer falha — o chamador decide se tenta outra
     * variante (só o 403) ou desiste. O parcial é apagado na falha.
     * [prefetchedText] evita buscar de novo uma playlist já lida (o master
     * que já é mídia). [playingHeight] só vale p/ descer um master aninhado.
     * [fileNameFor] dá o nome do arquivo por extensão (null = nome de
     * clipe com o intervalo — ver [ClipDownload.clipFileName]).
     */
    private suspend fun downloadVariantAttempt(
        variantUrl: String,
        headers: Map<String, String>,
        masterCookies: String?,
        title: String,
        startMs: Long,
        endMs: Long,
        onProgress: ((downloaded: Int, total: Int) -> Unit)?,
        playingHeight: Int?,
        prefetchedText: String?,
        fileNameFor: ((String) -> String)? = null,
    ): VariantAttempt {
        val moviesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: appContext.filesDir
        moviesDir.mkdirs()
        var playlistUrl = variantUrl
        var (playlistText, mediaCookies) = if (prefetchedText != null) {
            prefetchedText to null
        } else {
            fetchText(variantUrl, headers)
        }
        var playlist = ClipDownload.parseHlsSegments(playlistText, playlistUrl)
        if (playlist.segments.isEmpty()) {
            // A "variante" era outro master aninhado: desce um nível (1x).
            val nested = ClipDownload.listMasterVariants(playlistText, playlistUrl)
            if (nested.isNotEmpty()) {
                val top = ClipDownload.orderVariantsForPlayback(nested, playingHeight).first()
                playlistUrl = top.url
                fetchText(playlistUrl, headers).let {
                    playlistText = it.first
                    mediaCookies = mergeCookies(mediaCookies, it.second)
                }
                playlist = ClipDownload.parseHlsSegments(playlistText, playlistUrl)
            }
        }
        val cookies = mergeCookies(masterCookies, mediaCookies)
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
        val fileName = fileNameFor?.invoke(if (isFmp4) "mp4" else "ts")
            ?: ClipDownload.clipFileName(title, startMs, endMs, if (isFmp4) "mp4" else "ts")
        val partFile = File(moviesDir, "$fileName.part")
        val destFile = File(moviesDir, fileName)
        var bytes = 0L
        val keyCache = HashMap<String, ByteArray>()
        // Baixa init + segmentos do intervalo com os headers dados (suspend:
        // o ensureActive() cancela o download quando o usuário sai).
        // Lote VPN (v1.40.0): .ts re-basifica os timestamps por segmento
        // (ver [TsRebase]) — sem isso o trecho concatenado tinha duração
        // e relógio errados no player externo (começava em 1:57, voltava
        // ~30 s a cada ~5 s). O offset de cada segmento é (duração
        // acumulada dos anteriores − base do segmento) mod 2^33, então
        // descontinuidade entre segmentos também cai no lugar certo; se
        // os timestamps já forem contínuos desde 0, o offset é ~0 e o
        // fix vira no-op. fMP4 NÃO passa por aqui (os tempos vivem nos
        // boxes moof — continua o caminho direto de antes).
        // Lote dos 3 fixes (v1.41.0): a v1.40.0 não funcionou no ALYA
        // porque o `firstTimestamp90k` só achava timestamp em segmento
        // 188 bytes alinhado no byte 0 — fora disso a base era null e o
        // segmento ia cru (offset 0). O [TsRebase] agora detecta o
        // layout (188/192/204 bytes, prefixo) e normaliza p/ TS limpo
        // de 188 bytes em todo segmento, mesmo com offset 0.
        suspend fun downloadAll(hdrs: Map<String, String>): Long {
            var total = 0L
            var accumulated90k = 0L
            partFile.outputStream().buffered().use { out ->
                if (isFmp4) {
                    // Init segment primeiro: sem ele os .m4s não tocam.
                    val initUrl = playlist.initSegmentUrl
                    if (initUrl != null) {
                        total += fetchSegmentTo(initUrl, hdrs, out, cookies)
                    }
                }
                wanted.forEachIndexed { index, segment ->
                    currentCoroutineContext().ensureActive()
                    total += if (isFmp4) {
                        fetchClipSegmentTo(segment, hdrs, out, keyCache, cookies)
                    } else {
                        val raw = fetchClipSegmentBytes(segment, hdrs, keyCache, cookies)
                        val base = TsRebase.firstTimestamp90k(raw)
                        if (base == null) {
                            // Lote dos 3 fixes (v1.41.0): sem base não há
                            // re-basing — o segmento vai cru e a linha do
                            // tempo fica errada (foi o que aconteceu na
                            // v1.40.0). Loga p/ o diagnóstico: o layout
                            // detectado diz se o segmento é TS fora do
                            // padrão (192/204 bytes, prefixo) ou nem TS é
                            // (ex.: fMP4 sem #EXT-X-MAP). Só o host vai
                            // p/ o log, nunca a URL assinada inteira.
                            android.util.Log.w(
                                "ClipDownload",
                                "TsRebase: segmento sem timestamp (base null) — vai cru; " +
                                    "layout=${TsRebase.detectPacketLayout(raw)} " +
                                    "bytes=${raw.size} host=" +
                                    runCatching { java.net.URI(segment.url).host }.getOrNull(),
                            )
                        }
                        val offset = TsRebase.offsetForSegment(base, accumulated90k)
                        // O rebase SEMPRE roda: além de deslocar os
                        // timestamps, ele normaliza o layout (192/204
                        // bytes ou prefixo -> TS limpo de 188). Pular a
                        // chamada com offset 0 (como antes) escreveria o
                        // segmento cru e misturaria strides no arquivo.
                        val rebased = TsRebase.rebase(raw, offset)
                        out.write(rebased)
                        accumulated90k += segment.durationMs * TsRebase.TICKS_PER_MS
                        rebased.size.toLong()
                    }
                    onProgress?.invoke(index + 1, wanted.size)
                }
            }
            return total
        }
        try {
            bytes = downloadAll(headers)
        } catch (e: IOException) {
            runCatching { partFile.delete() }
            if (!e.message.orEmpty().contains("HTTP 403")) throw e
            // Hotfix v1.38.1: 403 persistente — alguns CDNs exigem que o
            // Referer dos segmentos seja a playlist de mídia que os listou
            // (e não o Referer genérico do player), quando o segmento está
            // noutro host. Uma única nova tentativa com esse Referer; se o
            // host for o mesmo, não há o que tentar (cai no erro original).
            val retryHeaders = refererRetryHeaders(headers, playlistUrl, wanted) ?: throw e
            bytes = runCatching { downloadAll(retryHeaders) }.getOrElse { retryError ->
                runCatching { partFile.delete() }
                throw retryError
            }
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            throw e
        }
        if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }
        return VariantAttempt(fileName, isFmp4, wanted.size, bytes, destFile)
    }

    /**
     * Lê uma playlist (texto + cookies da resposta). Os cookies voltam
     * como um header "Cookie" pronto ("a=b; c=d") — o OkHttp do app não tem
     * CookieJar, então sem isto um CDN que assina a sessão na playlist
     * recusaria os segmentos (403). Só o name=value de cada Set-Cookie.
     */
    private fun fetchText(url: String, headers: Map<String, String>): Pair<String, String?> {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} na playlist")
            val cookies = resp.headers("Set-Cookie")
                .mapNotNull { it.substringBefore(';').trim().takeIf { part -> part.isNotEmpty() } }
                .joinToString("; ").takeIf { it.isNotBlank() }
            return (resp.body?.string() ?: throw IOException("Resposta sem corpo na playlist")) to cookies
        }
    }

    /** Junta dois headers "Cookie" (null some). */
    private fun mergeCookies(a: String?, b: String?): String? =
        listOfNotNull(a?.takeIf { it.isNotBlank() }, b?.takeIf { it.isNotBlank() })
            .joinToString("; ").takeIf { it.isNotBlank() }

    /**
     * Headers da tentativa extra no 403 (hotfix v1.38.1): Referer = a URL
     * da playlist de mídia, mas SÓ quando algum segmento está noutro host
     * que o da playlist — com o mesmo host não há o que mudar (null = não
     * tentar). Não inventa host: compara os hosts reais das URLs.
     */
    private fun refererRetryHeaders(
        headers: Map<String, String>,
        playlistUrl: String,
        segments: List<ClipSegment>,
    ): Map<String, String>? {
        val playlistHost = runCatching { java.net.URL(playlistUrl).host }.getOrNull() ?: return null
        val differs = segments.any { seg ->
            runCatching { java.net.URL(seg.url).host }.getOrNull()?.let { it != playlistHost } == true
        }
        if (!differs) return null
        return headers + ("Referer" to playlistUrl)
    }

    /** Baixa um arquivo (segmento ou init) p/ o `out`, com os cookies da playlist quando houver. */
    private fun fetchSegmentTo(
        url: String,
        headers: Map<String, String>,
        out: OutputStream,
        cookies: String? = null,
    ): Long {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (cookies != null) header("Cookie", cookies)
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} no segmento")
            resp.body?.byteStream()?.use { input -> return input.copyTo(out) }
                ?: throw IOException("Resposta sem corpo no segmento")
        }
    }

    /**
     * Baixa um segmento do clipe p/ o `out`: em claro, copia os bytes crus;
     * com AES-128, busca a chave (cacheada por URI neste download), valida
     * chave (16 bytes) e IV (16 bytes) e escreve o segmento
     * descriptografado. Lança IOException com motivo legível em qualquer
     * falha — o chamador marca o clipe como "failed" com esse texto.
     * (Lote VPN, v1.40.0: o .ts usa [fetchClipSegmentBytes] p/ re-basificar
     * antes de escrever; o fMP4 continua por aqui, sem re-basing.)
     */
    private fun fetchClipSegmentTo(
        segment: ClipSegment,
        headers: Map<String, String>,
        out: OutputStream,
        keyCache: MutableMap<String, ByteArray>,
        cookies: String? = null,
    ): Long {
        val bytes = fetchClipSegmentBytes(segment, headers, keyCache, cookies)
        out.write(bytes)
        return bytes.size.toLong()
    }

    /**
     * Baixa um segmento do clipe EM MEMÓRIA (p/ o .ts re-basificar antes
     * de escrever — ver [TsRebase]): em claro, os bytes crus; com
     * AES-128, o segmento descriptografado. O re-basing acontece DEPOIS
     * da descriptografia, nunca sobre o cifrado. Lança IOException com
     * motivo legível em qualquer falha.
     */
    private fun fetchClipSegmentBytes(
        segment: ClipSegment,
        headers: Map<String, String>,
        keyCache: MutableMap<String, ByteArray>,
        cookies: String? = null,
    ): ByteArray {
        val keyUri = segment.keyUri ?: return fetchBytes(segment.url, headers, cookies)
        val key = keyCache.getOrPut(keyUri) { fetchKeyBytes(keyUri, headers, cookies) }
        if (key.size != 16) {
            throw IOException("Chave AES-128 inválida (não tem 16 bytes).")
        }
        val ivHex = segment.keyIvHex ?: throw IOException("Segmento cifrado sem IV.")
        val iv = ClipDownload.hexToBytes(ivHex)?.takeIf { it.size == 16 }
            ?: throw IOException("IV inválido no segmento cifrado.")
        val cipherBytes = fetchBytes(segment.url, headers, cookies)
        return try {
            ClipDownload.decryptAes128Segment(cipherBytes, key, iv)
        } catch (e: Exception) {
            throw IOException("Não deu para descriptografar o segmento (chave ou IV inválido).")
        }
    }

    /** Baixa um arquivo inteiro (segmento, init ou chave) p/ a memória. */
    private fun fetchBytes(
        url: String,
        headers: Map<String, String>,
        cookies: String? = null,
    ): ByteArray {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (cookies != null) header("Cookie", cookies)
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} no segmento")
            return resp.body?.bytes() ?: throw IOException("Resposta sem corpo no segmento")
        }
    }

    /** Baixa a chave AES-128 da URI do #EXT-X-KEY (mesmos headers do stream). */
    private fun fetchKeyBytes(url: String, headers: Map<String, String>, cookies: String? = null): ByteArray {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (cookies != null) header("Cookie", cookies)
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
