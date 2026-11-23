package com.aotv.app.data.remote

import com.aotv.app.data.backup.BackupWifiServer
import com.aotv.app.data.local.PlatformPrefs
import fi.iki.elonen.NanoHTTPD
import kotlin.random.Random

/**
 * Chaves pelo celular via QR + servidor local (v1.45.0, item 9 — ideia do
 * Notflix, `NotflixKeysConfigServer`, reimplementada: página e rotas são
 * escritas aqui do zero, nada de HTML do Notflix).
 *
 * O controle remoto por LAN (Lote 22) já deixava colar chaves pelo celular,
 * mas exigia abrir a tela de controle remoto inteira. Este servidor é a
 * versão mínima e dedicada: um QR nas Configurações → Integrações abre
 * UMA página no navegador do celular, onde o Davi digita a chave TMDB, o
 * Trakt Client ID e a URL da IntroDB no teclado do celular — sem digitar
 * nada no controle remoto/teclado da TV.
 *
 * - Escuta em `0.0.0.0` (precisa alcançar a LAN) numa porta efêmera, com
 *   PIN de 4 dígitos gerado na hora (mesmo desenho do BackupWifiServer).
 * - GET / → formulário simples (3 campos + PIN). GET /ping?pin= → ok.
 * - POST /save (PIN na query ou no campo do formulário) → grava nas
 *   prefs o que veio preenchido (campo vazio = mantém o valor atual) e
 *   devolve o que foi aplicado. Só chaves que o app já guarda nas prefs
 *   (TMDB, Trakt Client ID, URL da IntroDB); nunca senhas, nunca tokens
 *   de sessão.
 * - PIN errado → 403. O PIN e os valores nunca vão p/ log.
 * - O servidor vive SÓ enquanto a tela "Chaves pelo celular" está aberta:
 *   a tela para ele ao sair (precedente do BackupWifiServer).
 *
 * Atenção NanoHTTPD 2.3.1: `Response.IStatus` só tem `getDescription` e
 * `getRequestStatus` — em Kotlin, `resp.status.requestStatus`; `statusCode`
 * NÃO existe. E POST precisa de `parseBody` antes de ler os parâmetros.
 */
class KeysQrServer(
    private val prefs: PlatformPrefs,
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
            val files = HashMap<String, String>()
            if (s.method == Method.POST) {
                runCatching { s.parseBody(files) }
                    .onFailure { return text(Response.Status.BAD_REQUEST, "Não foi possível ler o envio.") }
            }
            val pinParam = s.parameters["pin"]?.firstOrNull().orEmpty()
            return when (s.uri) {
                "/" -> htmlForm()
                "/ping" -> {
                    if (pinParam != pin) text(Response.Status.FORBIDDEN, "PIN errado.")
                    else json("{\"ok\":true}")
                }
                "/save" -> {
                    if (s.method != Method.POST) return text(Response.Status.METHOD_NOT_ALLOWED, "Use o formulário.")
                    if (pinParam != pin) return text(Response.Status.FORBIDDEN, "PIN errado.")
                    val applied = mutableListOf<String>()
                    val tmdb = s.parameters["tmdb"]?.firstOrNull().orEmpty().trim()
                    if (tmdb.isNotBlank()) {
                        prefs.customTmdbApiKey = tmdb
                        applied += "TMDB"
                    }
                    val trakt = s.parameters["trakt_client_id"]?.firstOrNull().orEmpty().trim()
                    if (trakt.isNotBlank()) {
                        prefs.traktClientId = trakt
                        applied += "Trakt Client ID"
                    }
                    val introdb = s.parameters["introdb_url"]?.firstOrNull().orEmpty().trim()
                    if (introdb.isNotBlank()) {
                        prefs.introDbBaseUrl = introdb
                        applied += "URL da IntroDB"
                    }
                    if (applied.isEmpty()) {
                        htmlResult("Nada para aplicar.", "Preencha ao menos um campo.")
                    } else {
                        htmlResult(
                            "Aplicado: ${applied.joinToString(", ")}.",
                            "Pode fechar esta página — as chaves já estão no app.",
                        )
                    }
                }
                else -> text(Response.Status.NOT_FOUND, "Página não encontrada.")
            }
        }

        private fun text(status: Response.Status, msg: String) =
            newFixedLengthResponse(status, MIME_PLAINTEXT, msg)

        private fun json(body: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)

        private fun htmlForm() = newFixedLengthResponse(
            Response.Status.OK, MIME_HTML,
            """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Chaves do AOTV</title></head>
            <body style="font-family:sans-serif;max-width:560px;margin:32px auto;padding:0 16px">
            <h1>Chaves do AOTV</h1>
            <p>Digite as chaves aqui no celular — elas vão direto p/ o app na TV, sem digitar no controle remoto. Campo vazio mantém o valor atual.</p>
            <form method="post" action="/save">
            <label>PIN (4 dígitos, mostrado na TV)<br>
            <input name="pin" inputmode="numeric" autocomplete="off" required></label><br><br>
            <label>Chave TMDB<br>
            <input name="tmdb" autocomplete="off" style="width:100%"></label><br><br>
            <label>Trakt Client ID<br>
            <input name="trakt_client_id" autocomplete="off" style="width:100%"></label><br><br>
            <label>URL da IntroDB<br>
            <input name="introdb_url" autocomplete="off" value="https://api.introdb.app" style="width:100%"></label><br><br>
            <button type="submit">Salvar no app</button></form>
            <p>As chaves ficam só no aparelho. Nada é enviado p/ nenhum servidor externo.</p>
            </body></html>
            """.trimIndent(),
        )

        private fun htmlResult(title: String, detail: String) = newFixedLengthResponse(
            Response.Status.OK, MIME_HTML,
            """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Chaves do AOTV</title></head>
            <body style="font-family:sans-serif;max-width:560px;margin:32px auto;padding:0 16px">
            <h1>$title</h1>
            <p>$detail</p>
            <p><a href="/">Voltar</a></p>
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
    fun url(): String? = BackupWifiServer.lanIpAddress()?.let { "http://$it:${server.listeningPort}/" }
}
