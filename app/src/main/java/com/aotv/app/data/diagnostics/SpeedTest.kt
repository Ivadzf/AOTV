package com.aotv.app.data.diagnostics

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Resultado de um teste de velocidade (Lote 25, v1.35.0 — Frente B, item 5).
 *
 * [latencyMs] é o tempo até o primeiro byte; [mbps] é a vazão média do
 * download inteiro. [error] não nulo = o teste falhou (a tela mostra o
 * erro, nunca crasha). O último resultado fica guardado numa pref global
 * ([PlatformPrefs.speedTestLastJson]) e é mostrado na tela de reportar
 * problema sem precisar testar de novo.
 */
data class SpeedTestResult(
    val latencyMs: Long,
    val bytesDownloaded: Long,
    val mbps: Double,
    val checkedAt: Long,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null

    /** Linha curta p/ a tela ("12,4 Mbps · 180 ms de latência"). */
    fun display(): String = if (!ok) {
        "Falhou: ${error ?: "erro desconhecido"}"
    } else {
        "${String.format(Locale.US, "%.1f", mbps).replace('.', ',')} Mbps · $latencyMs ms de latência"
    }
}

/**
 * Teste de velocidade sob demanda (ideia do MKCinema, reimplementada).
 *
 * Baixa um asset público pequeno do endpoint de download do Cloudflare
 * (o mesmo que o MKCinema referencia p/ o speed test dele) e mede latência
 * + vazão. Só roda quando o usuário toca em "Testar velocidade" — nunca
 * automático, nunca em segundo plano. Timeouts curtos: sem rede ou com a
 * rede lenta, o teste falha com erro legível em vez de travar a tela.
 */
object SpeedTest {
    /** Endpoint público de download do Cloudflare, ~200 KB (nada de credencial). */
    const val SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=200000"

    const val CONNECT_TIMEOUT_SEC = 5L
    const val READ_TIMEOUT_SEC = 10L
    const val CALL_TIMEOUT_SEC = 15L

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    suspend fun run(): SpeedTestResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        var firstByteAt = 0L
        var total = 0L
        try {
            client.newCall(
                Request.Builder().url(SPEED_TEST_URL)
                    .header("User-Agent", "AOTV/1.35.0 (Android)")
                    .build(),
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext SpeedTestResult(0, 0, 0.0, started, error = "HTTP ${resp.code}")
                }
                val body = resp.body
                    ?: return@withContext SpeedTestResult(0, 0, 0.0, started, error = "Resposta vazia")
                val buf = ByteArray(8192)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (firstByteAt == 0L) firstByteAt = System.currentTimeMillis()
                        total += n
                    }
                }
            }
            if (total <= 0) {
                return@withContext SpeedTestResult(0, 0, 0.0, started, error = "Nada baixado")
            }
            val ended = System.currentTimeMillis()
            val latency = if (firstByteAt == 0L) ended - started else firstByteAt - started
            val seconds = (ended - started) / 1000.0
            val rawMbps = if (seconds > 0) total * 8.0 / 1_000_000.0 / seconds else 0.0
            SpeedTestResult(latency, total, (rawMbps * 10).roundToInt() / 10.0, started)
        } catch (e: Exception) {
            SpeedTestResult(0, total, 0.0, started, error = e.message?.take(120) ?: e.javaClass.simpleName)
        }
    }

    /** Guarda o último resultado (global, fora do perfil — é dado do aparelho). */
    fun saveLast(prefs: PlatformPrefs, result: SpeedTestResult) {
        val o = JSONObject()
        o.put("latencyMs", result.latencyMs)
        o.put("bytes", result.bytesDownloaded)
        o.put("mbps", result.mbps)
        o.put("at", result.checkedAt)
        o.put("error", result.error ?: "")
        prefs.speedTestLastJson = o.toString()
    }

    /** Último resultado guardado, ou null quando nunca houve teste. */
    fun loadLast(prefs: PlatformPrefs): SpeedTestResult? {
        val raw = prefs.speedTestLastJson
        if (raw.isBlank()) return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val error = o.optString("error").takeIf { it.isNotBlank() }
        return SpeedTestResult(
            latencyMs = o.optLong("latencyMs"),
            bytesDownloaded = o.optLong("bytes"),
            mbps = o.optDouble("mbps"),
            checkedAt = o.optLong("at"),
            error = error,
        )
    }
}
