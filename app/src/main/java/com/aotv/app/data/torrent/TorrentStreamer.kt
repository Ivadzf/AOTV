package com.aotv.app.data.torrent

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.StreamingDataMeter
import fi.iki.elonen.NanoHTTPD
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Motor de torrent (libtorrent) com servidor HTTP local que entrega o arquivo ao player enquanto baixa.
 *
 * Stats p/ o player (Lote 5 item 2, v1.14.0): [status] expõe seeds, peers
 * e velocidade de download de verdade (do `TorrentStatus` do libtorrent),
 * e o painel de infos técnicas do player mostra esses números + o buffer
 * do ExoPlayer — nada inventado (ver TorrentStats em PlayerTechInfo.kt).
 *
 * Cache do torrent (Lote 5 item 3, v1.14.0): NÃO CONFIGURÁVEL — e não há
 * pref fingindo que é. O libtorrent 2.x tirou o cache de disco (o I/O
 * passou a ser por mmap) e o `SettingsPack` do libtorrent4j 2.1.0-39 não
 * tem `cache_size`: conferido no jar, nenhum `cache_size` em
 * `settings_pack$int_types` (só `max_queued_disk_bytes`, que é fila de
 * escrita em disco, não cache — não virou pref p/ não enganar). O que o
 * motor guarda em disco é o arquivo baixado em `cacheDir/torrents`,
 * apagado ao sair do player ([stopAll]) e na limpeza de ociosos.
 * FOLLOW-UP: reavaliar se o libtorrent4j voltar a expor tamanho de cache
 * (ou se surgir outro motor com o ajuste); até lá, sem controle de cache.
 *
 * Teto de espaço temporário + descarte do já assistido (Lote 18 item 2,
 * v1.28.0 — ver [TorrentTempCap]): o teto ([tempLimitBytes], pref
 * `torrent_temp_limit_mb`, 32–256 MB, padrão 128 MB) NÃO é cache — é o
 * tamanho do diretório do torrent no disco. A janela de pré-download
 * encolhe com o teto (teto/4, 4–24 MB); quando o diretório passa do
 * teto e o descarte está ligado, as peças totalmente atrás da posição
 * assistida têm os blocos liberados com `fallocate(PUNCH_HOLE)` e a
 * prioridade vira IGNORE. Sem descarte, o teto não é cumprido (não há
 * como apagar peça sem rebaixar) — ver [enforceTempCap]. Voltar p/ uma
 * peça descartada (seek p/ trás) baixa ela de novo, com 1 `forceRecheck`
 * p/ o libtorrent reconhecer a falta (ver [PieceStream]). Ao completar
 * o arquivo escolhido o torrent é pausado (sem seed do arquivo
 * completo); ao parar, tudo é apagado (item 3 do Lote 18).
 *
 * Medição de torrent (Lote 18 item 7): [onTorrentBytes] recebe o delta
 * de `totalDownload()` do libtorrent (o que o motor baixou de fato,
 * incluindo o pré-download) — o [Torrents.get] liga isso ao medidor de
 * dados móveis. [onTorrentStopped] descarrega o resto ao remover.
 */
class TorrentStreamer(
    private val root: File,
    tempLimitBytes: Long = TorrentTempCap.DEFAULT_TEMP_LIMIT_BYTES,
    discardWatched: Boolean = true,
    private val onTorrentBytes: (Long) -> Unit = {},
    private val onTorrentStopped: () -> Unit = {},
) {

    /** Teto do espaço temporário, em bytes (vale ao trocar em Configurações — ver [refreshTempSettings]). */
    @Volatile var tempLimitBytes: Long = tempLimitBytes
        private set

    /** Descartar do disco as peças já assistidas quando passar do teto. */
    @Volatile var discardWatched: Boolean = discardWatched
        private set

    /** Atualiza teto/descarte no motor já criado (a troca em Configurações vale sem reiniciar o app). */
    fun refreshTempSettings(limitBytes: Long, discard: Boolean) {
        tempLimitBytes = limitBytes.coerceAtLeast(0L)
        discardWatched = discard
    }

    data class Status(val peers: Int, val seeds: Int, val downloadBps: Int, val hasMetadata: Boolean)

    private class Picked(
        val name: String, val path: File, val offset: Long, val size: Long,
        val pieceLength: Int, val first: Int, val last: Int,
    )

    private class Active(val spec: TorrentSpec, val handle: TorrentHandle) {
        @Volatile var file: Picked? = null
        val readers = AtomicInteger()
        @Volatile var lastUse = System.currentTimeMillis()
        @Volatile var windowAt = -1
        /** Maior byte do arquivo escolhido já entregue ao player (posição assistida). */
        @Volatile var watchedThroughByte = 0L
        /** Peças descartadas do disco (descarte do já assistido, Lote 18) — baixar de novo se voltar nelas. */
        val discardedPieces = ConcurrentHashMap.newKeySet<Int>()
        /** `forceRecheck()` já rodado 1x p/ reconhecer as peças descartadas. */
        @Volatile var recheckTriggered = false
        /** Torrent pausado ao completar o arquivo (sem seed — Lote 18). */
        @Volatile var pausedWhenComplete = false
        /** Último `totalDownload()` observado (p/ o delta da medição de torrent). */
        @Volatile var lastTotalDownload = 0L
    }

    private val session = SessionManager(false)
    private val active = ConcurrentHashMap<String, Active>()
    private val lock = Any()
    // Outro app do aparelho também alcança 127.0.0.1, então só serve URL com este segredo.
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Volatile private var server: Server? = null

    init {
        root.deleteRecursively()
        Thread(::reapLoop, "torrent-limpeza").apply { isDaemon = true }.start()
    }

    /** URL local que o player abre no lugar do magnet. */
    fun playUrl(magnet: String): String {
        val spec = TorrentMagnet.parse(magnet) ?: return magnet
        return "http://127.0.0.1:${ensureServer().listeningPort}/$token/${spec.infoHash}?m=" + URLEncoder.encode(magnet, "UTF-8")
    }

    /** Stats reais do torrent p/ o painel do player (Lote 5 item 2): peers, seeds, velocidade e metadado. Null sem o torrent na sessão. */
    fun status(magnet: String?): Status? {
        val spec = TorrentMagnet.parse(magnet) ?: return null
        val a = active[spec.infoHash]?.takeIf { it.handle.isValid } ?: return null
        val s = a.handle.status()
        return Status(s.numPeers(), s.numSeeds(), s.downloadPayloadRate(), s.hasMetadata())
    }

    /** Para tudo e apaga o que baixou (sair do player não deixa o aparelho compartilhando). */
    fun stopAll() = active.keys.toList().forEach(::remove)

    /** Há um torrent baixando/tocando agora (p/ o aviso de saída do app, Lote 18 item 4). */
    fun hasActiveDownload(): Boolean = active.values.any { a ->
        a.handle.isValid && (a.readers.get() > 0 ||
            runCatching { a.handle.status().downloadPayloadRate() > 0 }.getOrDefault(false))
    }

    private fun remove(hash: String) {
        val a = active.remove(hash) ?: return
        runCatching { session.remove(a.handle, SessionHandle.DELETE_FILES) }
        File(root, hash).deleteRecursively()
        // Descarrega o resto da medição de torrent (o que ficou abaixo de 1 MB no medidor).
        onTorrentStopped()
    }

    private fun ensureServer(): Server = server ?: synchronized(lock) {
        server ?: Server().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true); server = it }
    }

    private fun ensureSession() {
        if (session.isRunning) return
        session.start(SessionParams(SettingsPack().connectionsLimit(200).activeDownloads(4).activeLimit(8)))
    }

    private fun open(spec: TorrentSpec): Active {
        val a = synchronized(lock) {
            active[spec.infoHash]?.takeIf { it.handle.isValid } ?: run {
                ensureSession()
                // Um torrent por vez. O anterior sem ninguém lendo sai e libera a banda pro novo.
                active.values.filter { it.readers.get() == 0 }.forEach { remove(it.spec.infoHash) }
                val dir = File(root, spec.infoHash).apply { mkdirs() }
                session.download(TorrentMagnet.forEngine(spec), dir, torrent_flags_t())
                val hash = Sha1Hash.parseHex(spec.infoHash)
                val h = waitFor(10_000) { session.find(hash)?.takeIf { it.isValid } }
                    ?: throw IOException("torrent não entrou na sessão")
                Active(spec, h).also { active[spec.infoHash] = it }
            }
        }
        a.lastUse = System.currentTimeMillis()
        if (a.file == null) pick(a)
        return a
    }

    private fun pick(a: Active) = synchronized(a) {
        if (a.file != null) return
        val ti = waitFor(METADATA_MS) { if (a.handle.status().hasMetadata()) a.handle.torrentFile() else null }
            ?: throw IOException("ninguém compartilhando esse torrent agora")
        val fs = ti.files()
        val files = (0 until fs.numFiles()).map { TorrentFile(it, fs.filePath(it), fs.fileSize(it)) }
        val f = TorrentFiles.pick(files, a.spec) ?: throw IOException("torrent sem arquivo")
        a.handle.prioritizeFiles(Array(fs.numFiles()) { if (it == f.index) Priority.DEFAULT else Priority.IGNORE })
        val pl = ti.pieceLength()
        val off = fs.fileOffset(f.index)
        val first = TorrentFiles.pieceAt(off, 0, pl)
        val last = TorrentFiles.pieceAt(off, f.size - 1, pl)
        a.file = Picked(f.path, File(File(root, a.spec.infoHash), f.path), off, f.size, pl, first, last)
        // Começo e fim primeiro, onde mp4 e mkv guardam cabeçalho e índice.
        deadline(a.handle, first, minOf(first + 1, last), 0)
        deadline(a.handle, maxOf(last - 1, first), last, 400)
    }

    private fun deadline(h: TorrentHandle, from: Int, to: Int, baseMs: Int) {
        for ((i, p) in (from..to).withIndex()) if (!h.havePiece(p)) h.setPieceDeadline(p, baseMs + i * STEP_MS)
    }

    private fun window(a: Active, f: Picked, piece: Int) {
        if (a.windowAt == piece) return
        a.windowAt = piece
        // Janela de pré-download encolhe com o teto (Lote 18 item 2): teto/4,
        // entre 4 MB e 24 MB — com teto de 32 MB, só 8 MB à frente.
        val n = (TorrentTempCap.windowBytes(tempLimitBytes) / f.pieceLength).toInt().coerceIn(4, 64)
        deadline(a.handle, piece, minOf(piece + n - 1, f.last), 0)
    }

    /**
     * Teto de espaço temporário (Lote 18 item 2): roda a cada 10 s na
     * limpeza. Se o diretório do torrent passou do teto e o descarte está
     * ligado, libera no disco as peças totalmente atrás da posição
     * assistida (`fallocate` com PUNCH_HOLE — best-effort: se o sistema
     * de arquivos não suportar, o `runCatching` engole e o teto só não é
     * cumprido naquele aparelho) e marca a prioridade IGNORE p/ o motor
     * não pedi-las de novo. Sem descarte ligado, não há como cumprir o
     * teto (apagar peça sem rebaixar não existe no libtorrent) — então
     * só registra: nada é apagado e nada é pausado no meio da reprodução.
     */
    private fun enforceTempCap(a: Active) {
        val f = a.file ?: return
        if (tempLimitBytes <= 0L || !discardWatched) return
        val dir = File(root, a.spec.infoHash)
        val size = runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
        if (size <= tempLimitBytes) return
        val watchedPiece = TorrentFiles.pieceAt(f.offset, a.watchedThroughByte, f.pieceLength)
        val discardable = TorrentTempCap.discardablePieces(f.first, f.last, watchedPiece)
        if (discardable.isEmpty()) return
        runCatching {
            RandomAccessFile(f.path, "rw").use { raf ->
                for (p in discardable) {
                    val range = TorrentTempCap.pieceFileRange(p, f.offset, f.pieceLength, f.size) ?: continue
                    Os.fallocate(
                        raf.fd,
                        OsConstants.FALLOC_FL_KEEP_SIZE or OsConstants.FALLOC_FL_PUNCH_HOLE,
                        range.first, range.second - range.first,
                    )
                    a.discardedPieces += p
                    a.handle.piecePriority(p, Priority.IGNORE)
                    a.handle.resetPieceDeadline(p)
                }
            }
        }
        // Os prazos antigos além da janela só gastariam banda: limpa e
        // deixa a próxima leitura remontar a janela da posição atual.
        runCatching { a.handle.clearPieceDeadlines() }
        a.windowAt = -1
    }

    /** Delta de `totalDownload()` do libtorrent desde a última observação (medição de torrent, Lote 18 item 7). */
    private fun pollTorrentBytes(a: Active) {
        val total = runCatching { if (a.handle.isValid) a.handle.status().totalDownload() else return }.getOrNull() ?: return
        val delta = (total - a.lastTotalDownload).coerceAtLeast(0L)
        a.lastTotalDownload = total
        if (delta > 0L) onTorrentBytes(delta)
    }

    /**
     * Sem seed do arquivo completo (Lote 18 item 2): quando o que o motor
     * queria baixar já chegou (`totalDone >= totalWanted`), pausa o
     * torrent — o player continua lendo do disco, mas o aparelho não fica
     * compartilhando o arquivo completo com o player aberto. Se o usuário
     * voltar p/ uma peça descartada, o caminho de releitura retoma o
     * torrent ([PieceStream]). Quando `totalWanted` inclui os arquivos
     * ignorados, a condição nunca dispara e nada muda (só não pausa).
     */
    private fun pauseWhenComplete(a: Active) {
        if (a.pausedWhenComplete) return
        val complete = runCatching {
            if (!a.handle.isValid || !a.handle.status().hasMetadata()) false
            else a.handle.status().let { it.totalDone() >= it.totalWanted() && it.totalWanted() > 0L }
        }.getOrDefault(false)
        if (complete) {
            runCatching { a.handle.pause() }
            a.pausedWhenComplete = true
        }
    }

    private fun reapLoop() {
        var idleSince = 0L
        while (true) {
            Thread.sleep(10_000)
            val now = System.currentTimeMillis()
            active.values.forEach { a ->
                pollTorrentBytes(a)
                enforceTempCap(a)
                pauseWhenComplete(a)
            }
            active.values.filter { it.readers.get() == 0 && now - it.lastUse > IDLE_MS }.forEach { remove(it.spec.infoHash) }
            if (active.isNotEmpty() || !session.isRunning) { idleSince = 0L; continue }
            if (idleSince == 0L) idleSince = now
            else if (now - idleSince > SESSION_IDLE_MS) synchronized(lock) {
                if (active.isEmpty()) runCatching { session.stop() }
                idleSince = 0L
            }
        }
    }

    /** Lê o arquivo do torrent esperando cada peça chegar, pedindo antes as que vêm logo à frente. */
    private inner class PieceStream(private val a: Active, private val f: Picked, start: Long, private val end: Long) : InputStream() {
        private var pos = start
        private var raf: RandomAccessFile? = null
        @Volatile private var closed = false

        init { a.readers.incrementAndGet() }

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed || pos > end) return -1
            val piece = TorrentFiles.pieceAt(f.offset, pos, f.pieceLength)
            // Torrent removido no meio da leitura (saiu do player) faz o libtorrent lançar erro, aí só encerra.
            try {
                // Peça descartada do disco (descarte do já assistido, Lote 18
                // item 2): o `havePiece` pode ainda dizer true (estado velho
                // do libtorrent) — NÃO dá p/ confiar nele aqui. Marca a peça
                // p/ baixar de novo, retoma o torrent se ele pausou ao
                // completar e roda 1 `forceRecheck()` p/ o libtorrent
                // reconhecer que a peça sumiu; só depois do recheck o
                // `havePiece` volta a ser confiável e a espera normal
                // (abaixo) baixa a peça de novo. Sem o recheck, o player
                // serviria zeros do buraco perfurado no arquivo.
                if (a.discardedPieces.contains(piece)) {
                    runCatching { a.handle.resume() }
                    a.pausedWhenComplete = false
                    a.handle.piecePriority(piece, Priority.DEFAULT)
                    a.handle.setPieceDeadline(piece, 0)
                    if (!a.recheckTriggered) {
                        runCatching { a.handle.forceRecheck() }
                        a.recheckTriggered = true
                    }
                    val untilRecheck = System.currentTimeMillis() + RECHECK_WAIT_MS
                    while (a.handle.status().state() == TorrentStatus.State.CHECKING_FILES &&
                        System.currentTimeMillis() < untilRecheck) {
                        Thread.sleep(200)
                    }
                    a.discardedPieces.remove(piece)
                }
                window(a, f, piece)
                val until = System.currentTimeMillis() + PIECE_WAIT_MS
                while (!a.handle.havePiece(piece)) {
                    if (closed) return -1
                    if (System.currentTimeMillis() > until) throw IOException("a peça $piece não chegou a tempo")
                    Thread.sleep(40)
                }
            } catch (e: RuntimeException) {
                if (closed || !active.containsKey(a.spec.infoHash)) return -1
                throw IOException(e.message, e)
            }
            // Fim da peça contado do início do arquivo. Leitura nunca passa dele.
            val pieceEnd = (piece + 1L) * f.pieceLength - f.offset
            val n = minOf(len.toLong(), end - pos + 1, pieceEnd - pos).toInt()
            val file = raf ?: RandomAccessFile(f.path, "r").also { raf = it }
            file.seek(pos)
            val r = file.read(b, off, n)
            if (r > 0) {
                pos += r
                a.lastUse = System.currentTimeMillis()
                // Posição assistida (p/ o descarte do já assistido): o maior byte entregue ao player.
                if (pos > a.watchedThroughByte) a.watchedThroughByte = pos
            }
            return r
        }

        override fun close() {
            if (closed) return
            closed = true
            a.readers.decrementAndGet()
            a.lastUse = System.currentTimeMillis()
            runCatching { raf?.close() }
        }
    }

    private inner class Server : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(s: IHTTPSession): Response {
            val seg = s.uri.trim('/').split('/')
            if (seg.size != 2 || seg[0] != token) return text(Response.Status.FORBIDDEN)
            val spec = TorrentMagnet.parse(s.parameters["m"]?.firstOrNull())?.takeIf { it.infoHash == seg[1] }
                ?: return text(Response.Status.BAD_REQUEST)
            val a = try { open(spec) } catch (e: Exception) { return text(Response.Status.SERVICE_UNAVAILABLE, e.message.orEmpty()) }
            val f = a.file ?: return text(Response.Status.SERVICE_UNAVAILABLE)
            val range = s.headers["range"]?.takeIf { it.startsWith("bytes=") }
            var start = 0L
            var end = f.size - 1
            if (range != null) {
                val r = range.removePrefix("bytes=").substringBefore(',').trim()
                val from = r.substringBefore('-')
                val to = r.substringAfter('-', "")
                if (from.isEmpty()) start = (f.size - (to.toLongOrNull() ?: 0L)).coerceAtLeast(0L)
                else {
                    start = from.toLongOrNull() ?: 0L
                    to.toLongOrNull()?.let { end = minOf(it, f.size - 1) }
                }
            }
            if (start >= f.size || start > end) {
                return text(Response.Status.RANGE_NOT_SATISFIABLE).apply { addHeader("Content-Range", "bytes */${f.size}") }
            }
            // Pedido novo sem outro leitor é um salto, os prazos da posição antiga só gastariam banda.
            if (a.readers.get() == 0) { runCatching { a.handle.clearPieceDeadlines() }; a.windowAt = -1 }
            val partial = range != null
            return newFixedLengthResponse(
                if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                mime(f.name), PieceStream(a, f, start, end), end - start + 1,
            ).apply {
                addHeader("Accept-Ranges", "bytes")
                if (partial) addHeader("Content-Range", "bytes $start-$end/${f.size}")
            }
        }

        private fun text(st: Response.Status, msg: String = "") = newFixedLengthResponse(st, MIME_PLAINTEXT, msg)
    }

    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }

    private inline fun <T> waitFor(ms: Long, get: () -> T?): T? {
        val until = System.currentTimeMillis() + ms
        while (true) {
            get()?.let { return it }
            if (System.currentTimeMillis() > until) return null
            Thread.sleep(200)
        }
    }

    private companion object {
        const val METADATA_MS = 60_000L
        const val PIECE_WAIT_MS = 90_000L
        /** Espera máx. pelo fim do recheck após descartar peças (Lote 18 item 2). */
        const val RECHECK_WAIT_MS = 120_000L
        const val STEP_MS = 120
        const val IDLE_MS = 45_000L
        const val SESSION_IDLE_MS = 120_000L
    }
}

/** Motor único do app, criado só quando a primeira fonte de torrent toca. */
object Torrents {
    @Volatile private var engine: TorrentStreamer? = null

    fun get(context: Context): TorrentStreamer {
        engine?.let { existing ->
            // Teto/descarte valem sem reiniciar o app (ver refreshTempSettings).
            val prefs = PlatformPrefs(context.applicationContext)
            existing.refreshTempSettings(
                prefs.torrentTempLimitMb * 1024L * 1024L,
                prefs.torrentDiscardWatched,
            )
            return existing
        }
        return synchronized(this) {
            engine ?: run {
                val appContext = context.applicationContext
                val prefs = PlatformPrefs(appContext)
                // Medição de torrent (Lote 18 item 7): o motor entrega o delta
                // de `totalDownload()` e o medidor soma em dados móveis.
                val meter = StreamingDataMeter(appContext)
                TorrentStreamer(
                    File(appContext.cacheDir, "torrents"),
                    tempLimitBytes = prefs.torrentTempLimitMb * 1024L * 1024L,
                    discardWatched = prefs.torrentDiscardWatched,
                    onTorrentBytes = { bytes -> meter.recordTorrentBytes(bytes) },
                    onTorrentStopped = { meter.flushTorrent() },
                ).also { engine = it }
            }
        }
    }

    fun ifStarted(): TorrentStreamer? = engine
}
