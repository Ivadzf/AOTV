package com.aotv.app.data.kodi

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

/**
 * Verificação de hostname relaxada SÓ p/ os hosts proxy do Vulgata
 * (`*.null-null.shop`). O proxy devolve um certificado válido p/
 * `*.null-null.shop`, mas o hostname real é um rótulo punycode com
 * underscores (`xn--l...null-null.shop`) que a verificação estrita de
 * hostname rejeita ("Hostname mismatch", provado na VM com verify=ON) —
 * o addon contorna isso com `verify=False` em todos os fetches. Aqui só
 * o CHECK DE NOME é relaxado p/ esses hosts; a cadeia de certificados
 * continua sendo verificada (o trust manager padrão não é tocado).
 * Qualquer outro host usa o verificador padrão do Android.
 *
 * Usado em dois lugares: (1) no cliente OkHttp do player (v1.2.7) —
 * que NÃO alcança o host esquisito, porque o OkHttp rejeita a URL
 * ainda no parse (STD 3 ASCII, provado no celular na v1.2.7); e
 * (2) no fetch upstream do proxy local (v1.2.8), onde ele resolve —
 * o `java.net.URL` não faz validação STD3, então o fetch chega ao
 * TLS e este verificador destrava o check de nome.
 *
 * Movido de MoviePlayerScreen.kt p/ cá na v1.2.8 (o proxy também
 * precisa dele); o player continua usando o mesmo val.
 */
internal val kodiProxyHostnameVerifier = HostnameVerifier { hostname, session ->
    if (hostname.endsWith(".null-null.shop", ignoreCase = true)) true
    else HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
}

/**
 * v1.2.8 — proxy HTTP local (127.0.0.1) p/ os streams Kodi (Vulgata).
 *
 * Por que existe: o ExoPlayer/OkHttp rejeita a URL resolvida ainda no
 * PARSE — o hostname é um rótulo punycode com underscores
 * (`xn--l...-2w85c.null-null.shop`), que viola as regras STD 3 ASCII
 * (`StringPrepParseException`, provado no celular na v1.2.7). A falha é
 * antes do TLS, então o HostnameVerifier da v1.2.7 nunca é alcançado.
 * O player nunca vê esse hostname: ele busca em `http://127.0.0.1`
 * (porta efêmera) e este proxy busca o stream real e repassa.
 *
 * Caminho do fetch (a escolha crítica): `java.net.URL` +
 * `HttpsURLConnection` (opção (a)). O `java.net.URL` NÃO faz validação
 * STD3 — ele guarda o hostname como string e o `InetAddress` passa o
 * nome ao resolver do sistema (o DNS resolve o host esquisito; provado
 * na VM por sistema e por DoH). A validação STD3 é do `OkHttp.HttpUrl`,
 * que chama `IDN.toASCII(host, USE_STD3_ASCII_RULES)` — é o bug que
 * estamos contornando, por isso o OkHttp NUNCA é usado p/ buscar a
 * URL real. O TLS usa o `kodiProxyHostnameVerifier` (só o check de
 * nome é relaxado p/ `*.null-null.shop`; a cadeia continua verificada).
 * SUPOSIÇÃO (não dá p/ provar nesta VM — não há Java): o JDK do
 * Android não impõe STD3 em `java.net.URL`/`InetAddress`. É o
 * comportamento padrão do JDK; se o celular discordar, a linha
 * `proxy:` no diag vai mostrar o erro do fetch, e o fallback é o
 * fetch via Python (`requests`, verify=False — provado pelo addon).
 *
 * O que o proxy faz:
 * - `.mp4` (filme): repassa `Range` p/ o upstream e devolve 206 com
 *   `Content-Range` (seek do ExoPlayer); se o upstream ignorar o
 *   Range, devolve 200 com o corpo completo.
 * - `.m3u8` (TV): busca a playlist, reescreve as URIs dos segmentos,
 *   playlists aninhadas, chaves (`EXT-X-KEY`), mapas (`EXT-X-MAP`) e
 *   mídias (`EXT-X-MEDIA`) p/ voltarem pelo proxy, e serve a playlist
 *   reescrita. Segmento e playlist vivem no mesmo host esquisito, por
 *   isso passam pelo mesmo caminho de fetch.
 * - Headers do sufixo Kodi `|...` (Referer/Origin/User-Agent/
 *   Accept-Language) são enviados ao upstream — ele os espera.
 *
 * Escopo: SÓ streams Kodi. O player decide (ver MoviePlayerScreen:
 * `kodiDiag` não vazio). Nuvio JS e torrent não passam por aqui.
 *
 * Ciclo de vida: sobe sob demanda no 1º `proxyUrl()` e morre no
 * `stop()` (chamado ao liberar o player). Porta efêmera, threads
 * daemon, sem dependência nova. Uma requisição por conexão
 * (`Connection: close`) — o player não reutiliza a conexão.
 */
object KodiLocalProxy {

    /** Alvo upstream registrado (URL real + headers do sufixo Kodi). */
    private data class Target(val url: String, val headers: Map<String, String>)

    private val targets = ConcurrentHashMap<String, Target>()
    private val byUpstream = ConcurrentHashMap<String, String>()
    private val firstDiag = AtomicReference<String?>(null)
    private val lastDiag = AtomicReference("")
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "kodi-proxy").apply { isDaemon = true }
    }
    private val lock = Any()
    private var server: ServerSocket? = null
    private var port: Int = 0

    private const val CHROME_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    /**
     * Registra o alvo e devolve (URL p/ o ExoPlayer, headers p/ a factory).
     * A URL localhost carrega a extensão do alvo (.mp4/.m3u8/.ts) p/ o
     * player detectar o tipo (ver mediaItemFor). Não lança: se o proxy
     * não subir, devolve o alvo original (e registra o motivo no diag)
     * p/ o player tentar o caminho direto — o erro do player + a linha
     * `proxy:` mostram o que falhou.
     */
    fun proxyUrl(targetUrl: String, headers: Map<String, String>): Pair<String, Map<String, String>> {
        return try {
            val p = start()
            proxyUrlFor(targetUrl, headers, p) to emptyMap()
        } catch (e: Exception) {
            noteDiag("ERRO ao iniciar proxy p/ ${shortUpstream(targetUrl)}: ${e.javaClass.simpleName}: ${e.message}")
            targetUrl to headers
        }
    }

    /** Linhas de diag do proxy (1ª + última fetch) — "" se nunca buscou. */
    fun lastDiag(): String {
        val first = firstDiag.get()
        val last = lastDiag.get()
        if (first == null && last.isBlank()) return ""
        val lines = listOfNotNull(first, last.takeIf { it.isNotBlank() && it != first }).distinct()
        return "\n" + lines.joinToString("\n")
    }

    /** Para o servidor e limpa tokens + diag. Chamado ao liberar o player. */
    fun stop() {
        val srv = synchronized(lock) {
            val s = server
            server = null
            port = 0
            s
        }
        runCatching { srv?.close() }
        targets.clear()
        byUpstream.clear()
        firstDiag.set(null)
        lastDiag.set("")
    }

    // ── servidor ──────────────────────────────────────────────────────

    private fun start(): Int {
        synchronized(lock) {
            val s = server
            if (s != null && !s.isClosed) return port
            val bound = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            server = bound
            port = bound.localPort
            val acceptThread = Thread({ acceptLoop(bound) }, "kodi-proxy-accept").apply { isDaemon = true }
            acceptThread.start()
            return port
        }
    }

    private fun acceptLoop(bound: ServerSocket) {
        while (true) {
            val socket = try {
                bound.accept()
            } catch (_: Exception) {
                return
            }
            pool.execute { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            socket.use {
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream())
                val requestLine = readLine(input)
                if (requestLine == null) return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    writeSimple(output, 400, "Bad Request")
                    output.flush()
                    return
                }
                val reqHeaders = readHeaders(input)
                if (reqHeaders == null) return
                val token = tokenOf(parts[1])
                val target = token?.let { targets[it] }
                if (target == null) {
                    writeSimple(output, 404, "Not Found")
                    output.flush()
                    return
                }
                serve(target, reqHeaders, output)
                output.flush()
            }
        } catch (_: Exception) {
            // Cliente desconectou (seek cancelado, player liberado) — o
            // player re-tenta; não há o que registrar.
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        var b = -1
        while (true) {
            b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        if (b < 0 && sb.isEmpty()) return null
        return sb.toString()
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String>? {
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) return headers
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }
    }

    /** `/p/<token>.mp4` → token (o resto do caminho é só a extensão). */
    private fun tokenOf(rawPath: String): String? {
        val path = rawPath.substringBefore('?')
        if (!path.startsWith("/p/")) return null
        val rest = path.removePrefix("/p/")
        val token = rest.substringBefore('.').substringBefore('/')
        return token.ifBlank { null }
    }

    // ── fetch upstream ────────────────────────────────────────────────

    private fun serve(target: Target, reqHeaders: Map<String, String>, output: BufferedOutputStream) {
        val isPlaylist = target.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val t0 = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            conn = openUpstream(target, reqHeaders["range"])
            val status = conn.responseCode
            if (isPlaylist && status == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val bytes = rewritePlaylist(text, target).toByteArray(Charsets.UTF_8)
                noteDiag("GET ${shortUpstream(target.url)} $status ${bytes.size}B ${System.currentTimeMillis() - t0}ms")
                writeHeaders(output, status, "application/vnd.apple.mpegurl", bytes.size.toLong())
                output.write(bytes)
                return
            }
            // Mídia (.mp4/.ts/...): repassa status, Content-Type, Range e
            // o corpo em streaming (sem guardar em memória).
            val contentType = conn.contentType ?: guessContentType(target.url)
            val contentLength = conn.getHeaderFieldLong("Content-Length", -1)
            val contentRange = conn.getHeaderField("Content-Range")
            val sizeTxt = if (contentLength >= 0) "${contentLength}B" else "?B"
            noteDiag("GET ${shortUpstream(target.url)} $status $sizeTxt ${System.currentTimeMillis() - t0}ms")
            writeHeaders(output, status, contentType, contentLength, contentRange, acceptRanges = true)
            val input = if (status >= 400) conn.errorStream else conn.inputStream
            if (input != null) {
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n >= 0) {
                    output.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        } catch (e: Exception) {
            noteDiag("GET ${shortUpstream(target.url)} ERRO ${e.javaClass.simpleName}: ${e.message} ${System.currentTimeMillis() - t0}ms")
            // O player já está esperando bytes — um 502 diz que o upstream
            // falhou, e o playerError + o diag mostram o motivo.
            runCatching { writeSimple(output, 502, "Bad Gateway") }
        } finally {
            // Libera o socket do upstream (o proxy é por play; não há
            // reuso de conexão p/ justificar manter o socket aberto).
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * Abre a conexão com o upstream (com redirect manual, até 5, e os
     * headers registrados). `java.net.URL` NÃO faz validação STD3 — é
     * por isso que este caminho alcança o host que o OkHttp rejeita.
     */
    private fun openUpstream(target: Target, range: String?): HttpURLConnection {
        var url = URL(target.url)
        var redirects = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", target.headers["User-Agent"] ?: CHROME_UA)
            for ((k, v) in target.headers) conn.setRequestProperty(k, v)
            if (range != null) conn.setRequestProperty("Range", range)
            if (conn is HttpsURLConnection) conn.hostnameVerifier = kodiProxyHostnameVerifier
            val status = conn.responseCode
            val location = conn.getHeaderField("Location")
            if (location != null && status in intArrayOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308,
                ) && redirects < 5
            ) {
                conn.disconnect()
                url = URL(url, location)
                redirects++
                continue
            }
            return conn
        }
    }

    // ── playlist HLS ──────────────────────────────────────────────────

    /**
     * Reescreve as URIs de uma playlist p/ voltarem pelo proxy. Linhas
     * `#` com `URI="..."` (EXT-X-KEY/MAP/MEDIA) têm a URI reescrita
     * dentro das aspas; linhas sem `#` são segmentos ou playlists
     * aninhadas (relativas ou absolutas). URIs não-http(s) passam
     * intactas (ex.: `data:`).
     */
    private fun rewritePlaylist(text: String, target: Target): String {
        val base = URL(target.url)
        val out = StringBuilder()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("#") -> out.append(rewriteTagUris(line, base, target.headers)).append('\n')
                line.isBlank() -> out.append('\n')
                else -> {
                    val abs = runCatching { URL(base, line).toString() }.getOrDefault(line)
                    out.append(proxyUrlFor(abs, target.headers, port)).append('\n')
                }
            }
        }
        return out.toString()
    }

    /**
     * Reescreve as URIs dentro de `URI="..."` numa linha de tag
     * (#EXT-X-KEY/MAP/MEDIA). Sem regex (a raw string confundia o
     * ktbalance) — procura `URI="` e reescreve até a aspa seguinte.
     */
    private fun rewriteTagUris(line: String, base: URL, headers: Map<String, String>): String {
        val out = StringBuilder()
        var i = 0
        while (true) {
            val start = line.indexOf("URI=\"", i)
            if (start < 0) {
                out.append(line.substring(i))
                break
            }
            val valueStart = start + 5
            val valueEnd = line.indexOf('"', valueStart)
            if (valueEnd < 0) {
                out.append(line.substring(i))
                break
            }
            out.append(line.substring(i, valueStart))
            val raw = line.substring(valueStart, valueEnd)
            val abs = runCatching { URL(base, raw).toString() }.getOrNull()
            if (abs != null && (abs.startsWith("http://") || abs.startsWith("https://"))) {
                out.append(proxyUrlFor(abs, headers, port))
            } else {
                out.append(raw)
            }
            out.append('"')
            i = valueEnd + 1
        }
        return out.toString()
    }

    // ── registro de alvos ─────────────────────────────────────────────

    private fun proxyUrlFor(upstreamUrl: String, headers: Map<String, String>, p: Int): String {
        val key = upstreamUrl + "\n" + headers.entries.sortedBy { it.key }
            .joinToString("&") { (k, v) -> "$k=$v" }
        val token = byUpstream.getOrPut(key) {
            val t = UUID.randomUUID().toString().replace("-", "").take(16)
            targets[t] = Target(upstreamUrl, headers)
            t
        }
        return "http://127.0.0.1:$p/p/$token${extensionOf(upstreamUrl)}"
    }

    /** Extensão do caminho do alvo (.mp4/.m3u8/.ts/...) — "" se não houver. */
    private fun extensionOf(url: String): String {
        val seg = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val dot = seg.lastIndexOf('.')
        if (dot < 0) return ""
        val ext = seg.substring(dot + 1)
        return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ".$ext" else ""
    }

    // ── resposta HTTP ─────────────────────────────────────────────────

    private fun writeHeaders(
        out: BufferedOutputStream,
        status: Int,
        contentType: String?,
        contentLength: Long,
        contentRange: String? = null,
        acceptRanges: Boolean = false,
    ) {
        val reason = when (status) {
            200 -> "OK"
            206 -> "Partial Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            502 -> "Bad Gateway"
            else -> "OK"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
        if (contentType != null) sb.append("Content-Type: ").append(contentType).append("\r\n")
        if (contentLength >= 0) sb.append("Content-Length: ").append(contentLength).append("\r\n")
        if (contentRange != null) sb.append("Content-Range: ").append(contentRange).append("\r\n")
        if (acceptRanges) sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeSimple(out: BufferedOutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeHeaders(out, status, "text/plain; charset=utf-8", bytes.size.toLong())
        out.write(bytes)
    }

    private fun guessContentType(url: String): String {
        val path = url.substringBefore('?')
        return when {
            path.endsWith(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
            path.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            path.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
            else -> "application/octet-stream"
        }
    }

    // ── diag ──────────────────────────────────────────────────────────

    private fun noteDiag(line: String) {
        val full = "proxy: $line"
        firstDiag.compareAndSet(null, full)
        lastDiag.set(full)
    }

    /** host + caminho truncado p/ caber no cartão de diag. */
    private fun shortUpstream(url: String): String {
        val hp = runCatching {
            val u = URL(url)
            "${u.host}${u.path}"
        }.getOrDefault(url)
        return if (hp.length > 80) hp.take(80) + "…" else hp
    }
}
