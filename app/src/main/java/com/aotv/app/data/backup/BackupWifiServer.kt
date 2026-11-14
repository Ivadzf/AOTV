package com.aotv.app.data.backup

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Servidor Wi-Fi temporário do backup (v1.13.0), no desenho do JOTV 1.0.19:
 * sem servidor externo — o outro aparelho (TV, celular ou PC) abre o backup
 * direto no navegador da rede local.
 *
 * - Escuta em `0.0.0.0` (NÃO 127.0.0.1 como o TorrentStreamer: precisa
 *   alcançar a LAN) numa porta efêmera, com PIN de 4 dígitos gerado na hora.
 * - GET / → página simples (baixar com PIN + enviar arquivo com PIN).
 * - GET /backup?pin=XXXX → baixa o backup (.json, limite de 8 MB).
 * - POST /upload (PIN na query, no header `X-AOTV-PIN` ou no campo do
 *   formulário) → recebe um backup e restaura neste aparelho.
 * - GET /ping?pin=XXXX → {"ok":true}, p/ o outro aparelho conferir o PIN.
 * - PIN errado → 403. O PIN nunca vai p/ log.
 *
 * O backup servido é o instantâneo da hora em que o servidor abriu (a tela
 * monta o JSON antes de ligar o servidor). O servidor vive SÓ enquanto a
 * tela de Backup está aberta: a tela para ele ao sair (e o ViewModel no
 * onCleared).
 */
class BackupWifiServer(
    /** JSON do backup, montado quando o servidor abre. */
    private val backupJson: String,
    /** Recebe o JSON enviado p/ cá e devolve a mensagem de resultado. Roda na thread do servidor. */
    private val onUpload: (String) -> String,
) {
    /** PIN de 4 dígitos, gerado na hora. Mostrado na tela junto do QR. */
    val pin: String = "%04d".format(Random.nextInt(0, 10000))

    private val server = object : NanoHTTPD("0.0.0.0", 0) {
        override fun serve(s: IHTTPSession): Response {
            return try {
                route(s)
            } catch (e: Exception) {
                text(Response.Status.INTERNAL_ERROR, "Erro no servidor")
            }
        }

        private fun route(s: IHTTPSession): Response {
            // POST precisa do parseBody antes de ler os parâmetros (formulário/multipart).
            val files = HashMap<String, String>()
            if (s.method == Method.POST) {
                val length = s.headers["content-length"]?.toLongOrNull() ?: 0L
                if (length > AotvBackup.MAX_BACKUP_BYTES) return text(Response.Status.PAYLOAD_TOO_LARGE, "Backup grande demais (limite de 8 MB)")
                runCatching { s.parseBody(files) }.onFailure { return text(Response.Status.BAD_REQUEST, "Não foi possível ler o envio.") }
            }
            val path = s.uri.trimEnd('/')
            if (path.isEmpty() || path == "/") return htmlPage()
            if (!pinOk(s)) return text(Response.Status.FORBIDDEN, "PIN inválido.")
            return when (path) {
                "/ping" -> json(JSONObject().put("ok", true).toString())
                "/backup" -> {
                    if (backupJson.toByteArray(Charsets.UTF_8).size > AotvBackup.MAX_BACKUP_BYTES) {
                        return text(Response.Status.PAYLOAD_TOO_LARGE, "Backup grande demais (limite de 8 MB)")
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json", backupJson).apply {
                        addHeader("Content-Disposition", "attachment; filename=\"aotv-backup.json\"")
                    }
                }
                "/upload" -> {
                    val text = readUpload(s, files)
                        ?: return text(Response.Status.BAD_REQUEST, "Nenhum backup enviado.")
                    if (text.toByteArray(Charsets.UTF_8).size > AotvBackup.MAX_BACKUP_BYTES) {
                        return text(Response.Status.PAYLOAD_TOO_LARGE, "Backup grande demais (limite de 8 MB)")
                    }
                    val message = onUpload(text)
                    json(JSONObject().put("ok", true).put("message", message).toString())
                }
                else -> text(Response.Status.NOT_FOUND, "Não encontrado.")
            }
        }

        /** PIN na query, no header X-AOTV-PIN ou no campo do formulário. */
        private fun pinOk(s: IHTTPSession): Boolean {
            val pin = s.parameters["pin"]?.firstOrNull()
                ?: s.headers["x-aotv-pin"]
                ?: return false
            return pin == this@BackupWifiServer.pin
        }

        /** Corpo do upload: arquivo do formulário, campo de texto ou JSON cru. */
        private fun readUpload(s: IHTTPSession, files: Map<String, String>): String? {
            files["file"]?.let { path ->
                return runCatching { java.io.File(path).readText(Charsets.UTF_8) }.getOrNull()
            }
            files["postData"]?.let { if (it.isNotBlank()) return it }
            s.parameters["json"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            return null
        }

        private fun text(status: Response.Status, msg: String) =
            newFixedLengthResponse(status, MIME_PLAINTEXT, msg)

        private fun json(body: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)

        private fun htmlPage() = newFixedLengthResponse(
            Response.Status.OK, MIME_HTML,
            """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Backup do AOTV</title></head>
            <body style="font-family:sans-serif;max-width:560px;margin:32px auto;padding:0 16px">
            <h1>Backup do AOTV</h1>
            <p>Sem servidor externo. Tudo fica entre os seus aparelhos.</p>
            <h2>Baixar backup</h2>
            <form method="get" action="/backup">
            <label>PIN <input name="pin" inputmode="numeric" autocomplete="off" required></label>
            <button type="submit">Baixar (.json)</button></form>
            <h2>Enviar backup p/ este aparelho</h2>
            <form method="post" action="/upload" enctype="multipart/form-data">
            <label>PIN <input name="pin" inputmode="numeric" autocomplete="off" required></label><br><br>
            <input type="file" name="file" accept=".json,application/json" required><br><br>
            <button type="submit">Enviar e restaurar</button></form>
            <p>O backup enviado restaura neste aparelho. Senhas e sessões só vêm
            quando o backup foi feito com "Incluir chaves e sessões" ligado.</p>
            </body></html>
            """.trimIndent(),
        )
    }

    /** Liga o servidor. Devolve a URL p/ mostrar na tela (null sem IP de LAN). */
    fun start(): String? {
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        return url()
    }

    fun stop() = runCatching { server.stop() }

    /** Porta efêmera que o servidor abriu. 0 antes de ligar. */
    val port: Int get() = server.listeningPort

    /** URL da página inicial, ex. http://192.168.1.10:12345/ (o que vai no QR). */
    fun url(): String? = lanIpAddress()?.let { "http://$it:${server.listeningPort}/" }

    companion object {
        /** IP da LAN (Wi-Fi/Ethernet). null sem rede local. Sem WifiManager: só enumeração. */
        fun lanIpAddress(): String? {
            val ifs = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            val up = ifs.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            fun firstIp(list: List<NetworkInterface>): String? = list
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .firstOrNull()?.hostAddress
            // Prefere Wi-Fi/Ethernet; qualquer IPv4 não-loopback serve de fallback.
            return firstIp(up.filter { it.name.startsWith("wlan") || it.name.startsWith("eth") })
                ?: firstIp(up)
        }

        private val lanClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Baixa o backup de outro aparelho pelo Wi-Fi (GET /backup?pin=).
         * Cliente próprio, sem DoH: IP de LAN não passa pelo DNS.
         */
        suspend fun fetchBackupFrom(host: String, port: Int, pin: String): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
                val url = "http://$host:$port/backup?pin=$pin".toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Endereço inválido.")
                val req = Request.Builder().url(url).get().build()
                lanClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException(
                        if (resp.code == 403) "PIN inválido." else "O outro aparelho respondeu ${resp.code}.",
                    )
                    val bytes = resp.body?.bytes() ?: throw IllegalStateException("Resposta vazia.")
                    if (bytes.size > AotvBackup.MAX_BACKUP_BYTES) throw IllegalStateException("Backup grande demais (limite de 8 MB)")
                    String(bytes, Charsets.UTF_8)
                }
            }
        }

        /** Envia o backup p/ outro aparelho pelo Wi-Fi (POST /upload?pin=). */
        suspend fun sendBackupTo(host: String, port: Int, pin: String, json: String): Result<String> = withContext(Dispatchers.IO) {
            runCatching {
                val url = "http://$host:$port/upload?pin=$pin".toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("Endereço inválido.")
                val body = json.toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body).build()
                lanClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException(
                        if (resp.code == 403) "PIN inválido." else "O outro aparelho respondeu ${resp.code}.",
                    )
                    runCatching { JSONObject(text).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "Backup enviado."
                }
            }
        }
    }
}
