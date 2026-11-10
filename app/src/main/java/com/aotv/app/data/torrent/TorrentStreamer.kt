package com.aotv.app.data.torrent

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Motor de torrent (libtorrent) com servidor HTTP local que entrega o arquivo ao player enquanto baixa. */
class TorrentStreamer(private val root: File) {

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

    fun status(magnet: String?): Status? {
        val spec = TorrentMagnet.parse(magnet) ?: return null
        val a = active[spec.infoHash]?.takeIf { it.handle.isValid } ?: return null
        val s = a.handle.status()
        return Status(s.numPeers(), s.numSeeds(), s.downloadPayloadRate(), s.hasMetadata())
    }

    /** Para tudo e apaga o que baixou (sair do player não deixa o aparelho compartilhando). */
    fun stopAll() = active.keys.toList().forEach(::remove)

    private fun remove(hash: String) {
        val a = active.remove(hash) ?: return
        runCatching { session.remove(a.handle, SessionHandle.DELETE_FILES) }
        File(root, hash).deleteRecursively()
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
        val n = (WINDOW_BYTES / f.pieceLength).toInt().coerceIn(4, 64)
        deadline(a.handle, piece, minOf(piece + n - 1, f.last), 0)
    }

    private fun reapLoop() {
        var idleSince = 0L
        while (true) {
            Thread.sleep(10_000)
            val now = System.currentTimeMillis()
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
            if (r > 0) { pos += r; a.lastUse = System.currentTimeMillis() }
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
        const val WINDOW_BYTES = 24L * 1024 * 1024
        const val STEP_MS = 120
        const val IDLE_MS = 45_000L
        const val SESSION_IDLE_MS = 120_000L
    }
}

/** Motor único do app, criado só quando a primeira fonte de torrent toca. */
object Torrents {
    @Volatile private var engine: TorrentStreamer? = null

    fun get(context: Context): TorrentStreamer = engine ?: synchronized(this) {
        engine ?: TorrentStreamer(File(context.applicationContext.cacheDir, "torrents")).also { engine = it }
    }

    fun ifStarted(): TorrentStreamer? = engine
}
