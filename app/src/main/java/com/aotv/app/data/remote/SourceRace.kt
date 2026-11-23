package com.aotv.app.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Corrida de fontes (v1.45.0, item 6 — ideia do Nuvio-Echo, "Race matching
 * sources", reimplementada: nada de código do Echo aqui).
 *
 * O AOTV já tinha entrega progressiva (o 1º addon que responde começa a
 * tocar), mas "respondeu" não é "entrega vídeo": um link pode responder
 * rápido e morrer no 1º byte. A corrida sonda até [MAX_RACE] links EM
 * PARALELO e toca o 1º que entrega vídeo de verdade — e cancela os
 * perdedores, sem esperar o mais lento da lista.
 *
 * O que conta como "vídeo de verdade" ([isVideoResponse]): HTTP 200/206
 * com pelo menos 1 byte lido E (Content-Type de vídeo/mpegurl/octet-stream
 * OU o corpo não ser JSON/texto/HTML de erro). Um 200 com corpo de erro
 * em JSON não vence. A sonda é um GET com `Range: bytes=0-1023` (só 1 KB)
 * — o suficiente p/ provar que o servidor entrega bytes, sem baixar o
 * arquivo.
 *
 * Banda/dados móveis: a corrida multiplica o tráfego do início (até 8
 * sondas de 1 KB + o handshake de cada uma). Por isso o padrão é
 * [PlatformPrefs.sourceRaceWifiOnly] = true: em dados móveis, a corrida
 * NÃO roda — toca a 1ª fonte da lista, como antes. Com o toggle geral
 * ([PlatformPrefs.sourceRace]) desligado, também não roda.
 *
 * Troca de qualidade/áudio dispara a corrida de novo (quem chama passa a
 * lista filtrada pelo idioma/qualidade preferidos); uma fonte que não
 * aceita seek NÃO reinicia do zero — quem chama guarda a posição e dá
 * seek após a troca (o `activeSeekTo` do player já existe p/ isso).
 */
@Singleton
class SourceRaceService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * Testa até [MAX_RACE] URLs em paralelo e devolve a 1ª que entrega
     * vídeo de verdade, ou null quando nenhuma entrega. As sondas
     * perdedoras são canceladas quando há vencedor. Nunca lança exceção.
     * [isWifi] é dito por quem chama (aqui não há Context): com
     * `wifiOnly = true` e sem Wi-Fi, devolve null sem sondar — quem chama
     * toca a 1ª fonte da lista.
     */
    suspend fun race(
        urls: List<String>,
        wifiOnly: Boolean,
        isWifi: Boolean,
        timeoutMs: Long = RACE_TIMEOUT_MS,
    ): String? {
        val pool = urls.filter { it.isNotBlank() }.distinct().take(MAX_RACE)
        if (pool.isEmpty()) return null
        if (wifiOnly && !isWifi) return null
        if (pool.size == 1) return if (probe(pool[0]) != null) pool[0] else null
        return runCatching {
            coroutineScope {
                val jobs = pool.map { url -> async { probe(url) } }
                val remaining = jobs.toMutableList()
                val deadline = System.currentTimeMillis() + timeoutMs
                var winner: String? = null
                while (winner == null && remaining.isNotEmpty()) {
                    val got = select<String?> {
                        remaining.forEach { job -> job.onAwait { it } }
                    }
                    if (got != null) {
                        winner = got
                    } else {
                        remaining.removeAll { it.isCompleted }
                        if (System.currentTimeMillis() >= deadline) break
                    }
                }
                jobs.forEach { if (!it.isCompleted) it.cancel() }
                winner
            }
        }.getOrNull()
    }

    /**
     * Sonda UMA url: GET com Range de 1 KB. Devolve a url quando o servidor
     * entrega vídeo de verdade, ou null. Nunca lança exceção.
     */
    fun probe(url: String): String? = runCatching {
        val req = Request.Builder().url(url)
            .header("Range", "bytes=0-1023")
            .header("User-Agent", "AOTV/1.45.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code != 200 && resp.code != 206) return@runCatching null
            val body = resp.body ?: return@runCatching null
            val bytes = body.bytes()
            if (!isVideoResponse(resp.header("Content-Type"), bytes.size)) return@runCatching null
            url
        }
    }.getOrNull()

    companion object {
        /** Até 8 links em paralelo (o mesmo teto do Echo). */
        const val MAX_RACE = 8

        /** Teto total da corrida: sem vencedor até aqui, toca a 1ª da lista. */
        const val RACE_TIMEOUT_MS = 5_000L

        /**
         * O corpo é vídeo de verdade? Content-Type de vídeo/mpegurl ou
         * octet-stream passa; JSON/texto/HTML (página de erro) não passa.
         * Sem Content-Type, vale o tamanho: 0 bytes nunca é vídeo.
         */
        fun isVideoResponse(contentType: String?, bytesRead: Int): Boolean {
            if (bytesRead <= 0) return false
            val ct = contentType?.lowercase().orEmpty()
            if (ct.isBlank()) return true
            if (ct.contains("json") || ct.contains("text/html") || ct.contains("text/plain")) return false
            return ct.contains("video") || ct.contains("mpegurl") || ct.contains("octet-stream") ||
                ct.contains("mp2t") || ct.contains("mp4") || ct.contains("webm")
        }
    }
}
