package com.aotv.app.data.diagnostics

import androidx.compose.ui.graphics.Color
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AddonLinks
import com.aotv.app.data.remote.AppDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Estado de saúde de um addon/provider (Lote 25, v1.35.0 — Frente B, item 6). */
enum class AddonHealthStatus {
    /** Respondeu dentro do limite. */
    OK,
    /** Respondeu, mas devagar (acima de [SLOW_THRESHOLD_MS]). */
    SLOW,
    /** Não respondeu ou o manifest veio inválido. */
    ERROR,
    /** Instalado mas desativado pelo usuário — não é erro, só não roda. */
    DISABLED,
    /** Só existe no aparelho (manifest local, sem chamada de rede). */
    LOCAL,
    /** Nunca verificado nesta instalação. */
    UNKNOWN,
}

/** De onde vem o item da lista de saúde. */
enum class AddonKind { STREMIO, NUVIO, KODI }

/** Uma linha da tela de saúde dos addons. */
data class AddonHealthEntry(
    /** Chave estável p/ o histórico (URL do manifest, URL do repo ou id Kodi). */
    val key: String,
    val name: String,
    val url: String,
    val kind: AddonKind,
    val status: AddonHealthStatus,
    val latencyMs: Long? = null,
    val lastError: String? = null,
    val lastCheckedAt: Long = 0L,
    /** Versão, contagem de providers, ou outro detalhe local. */
    val detail: String = "",
)

/** Uma medição de latência guardada no histórico do addon. */
data class LatencySample(val at: Long, val ms: Long, val ok: Boolean)

/** Uma chamada registrada no log do Addon Lab. */
data class AddonCallLogEntry(
    val at: Long,
    val addonKey: String,
    val url: String,
    val ms: Long?,
    val ok: Boolean,
    val error: String?,
)

/** Acima disto o addon é "lento" (âmbar) em vez de "ok" (verde). */
const val SLOW_THRESHOLD_MS = 2000L

/** Classifica uma latência medida (null = não respondeu). */
fun classifyLatency(latencyMs: Long?): AddonHealthStatus = when {
    latencyMs == null -> AddonHealthStatus.ERROR
    latencyMs > SLOW_THRESHOLD_MS -> AddonHealthStatus.SLOW
    else -> AddonHealthStatus.OK
}

/** Rótulo PT p/ o estado (a tela mostra este texto, nunca o nome do enum). */
fun statusLabel(status: AddonHealthStatus): String = when (status) {
    AddonHealthStatus.OK -> "OK"
    AddonHealthStatus.SLOW -> "Lento"
    AddonHealthStatus.ERROR -> "Erro"
    AddonHealthStatus.DISABLED -> "Desativado"
    AddonHealthStatus.LOCAL -> "Local"
    AddonHealthStatus.UNKNOWN -> "Não verificado"
}

/** Cor do ponto de estado (verde/âmbar/vermelho; cinza p/ desativado e sem verificação). */
fun statusColor(status: AddonHealthStatus): Color = when (status) {
    AddonHealthStatus.OK -> Color(0xFF4CAF50)
    AddonHealthStatus.SLOW -> Color(0xFFE8A33D)
    AddonHealthStatus.ERROR -> Color(0xFFE57373)
    AddonHealthStatus.DISABLED -> Color(0xFF9E9E9E)
    AddonHealthStatus.LOCAL -> Color(0xFF4CAF50)
    AddonHealthStatus.UNKNOWN -> Color(0xFF9E9E9E)
}

/**
 * Probe do manifest de um addon Stremio (ideia do MKCinema, reimplementada).
 *
 * GET no manifest com timeouts curtos, medindo a latência. Só conta como OK
 * quando o HTTP é 200 E o corpo parece um manifest de verdade (tem `id` e
 * `resources`) — um erro JSON com HTTP 200 não é um addon saudável. O corpo
 * é descartado depois da checagem; nada é instalado por aqui.
 */
object AddonHealthProbe {
    const val CONNECT_TIMEOUT_SEC = 8L
    const val READ_TIMEOUT_SEC = 10L
    const val CALL_TIMEOUT_SEC = 15L

    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    /**
     * Devolve (latência em ms, erro ou null). Latência null = sem resposta
     * (o erro explica o motivo). Nunca lança exceção.
     */
    suspend fun probeManifest(manifestUrl: String): Pair<Long?, String?> = withContext(Dispatchers.IO) {
        val url = AddonLinks.normalizeManifestUrl(manifestUrl)
        if (url.isBlank()) return@withContext null to "URL vazia"
        val started = System.currentTimeMillis()
        try {
            client.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "AOTV/1.35.0 (Android)")
                    .header("Accept", "application/json")
                    .build(),
            ).execute().use { resp ->
                val latency = System.currentTimeMillis() - started
                if (!resp.isSuccessful) return@withContext null to "HTTP ${resp.code}"
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null to "Resposta vazia"
                val looksLikeManifest = runCatching {
                    val o = JSONObject(body)
                    o.optString("id").isNotBlank() && o.has("resources")
                }.getOrDefault(false)
                if (!looksLikeManifest) return@withContext null to "Não é um manifest válido"
                latency to null
            }
        } catch (e: Exception) {
            null to (e.message?.take(120) ?: e.javaClass.simpleName)
        }
    }
}

/**
 * Histórico de latência + log de chamadas (Addon Studio / Addon Lab).
 *
 * Guardados numa pref GLOBAL em JSON ([PlatformPrefs.addonHealthHistoryJson]
 * e [PlatformPrefs.addonHealthLogJson]) — é dado do aparelho, não do perfil.
 * Teto de [MAX_SAMPLES_PER_ADDON] amostras por addon e [MAX_LOG_ENTRIES]
 * entradas no log; o mais antigo sai quando estoura (nunca cresce sem limite).
 */
object AddonHealthHistory {
    const val MAX_SAMPLES_PER_ADDON = 20
    const val MAX_LOG_ENTRIES = 50

    /** Amostras de um addon, da mais recente p/ a mais antiga. */
    fun samplesFor(prefs: PlatformPrefs, key: String): List<LatencySample> {
        val raw = prefs.addonHealthHistoryJson
        if (raw.isBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<LatencySample>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += LatencySample(o.optLong("at"), o.optLong("ms"), o.optBoolean("ok", true))
        }
        return out.sortedByDescending { it.at }
    }

    /** Última amostra de um addon, ou null quando nunca foi verificado. */
    fun lastSample(prefs: PlatformPrefs, key: String): LatencySample? = samplesFor(prefs, key).firstOrNull()

    /** Log de chamadas, da mais recente p/ a mais antiga. */
    fun log(prefs: PlatformPrefs): List<AddonCallLogEntry> {
        val raw = prefs.addonHealthLogJson
        if (raw.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<AddonCallLogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ms = if (o.has("ms")) o.optLong("ms") else null
            out += AddonCallLogEntry(
                at = o.optLong("at"),
                addonKey = o.optString("key"),
                url = o.optString("url"),
                ms = ms,
                ok = o.optBoolean("ok", false),
                error = o.optString("error").takeIf { it.isNotBlank() },
            )
        }
        return out.sortedByDescending { it.at }
    }

    /** Registra uma medição no histórico do addon e no log (com os tetos). */
    fun record(prefs: PlatformPrefs, key: String, sample: LatencySample, call: AddonCallLogEntry) {
        val root = runCatching { JSONObject(prefs.addonHealthHistoryJson) }.getOrNull() ?: JSONObject()
        val arr = root.optJSONArray(key) ?: JSONArray()
        val sampleObj = JSONObject()
        sampleObj.put("at", sample.at)
        sampleObj.put("ms", sample.ms)
        sampleObj.put("ok", sample.ok)
        arr.put(sampleObj)
        while (arr.length() > MAX_SAMPLES_PER_ADDON) arr.remove(0)
        root.put(key, arr)
        prefs.addonHealthHistoryJson = root.toString()

        val logArr = runCatching { JSONArray(prefs.addonHealthLogJson) }.getOrNull() ?: JSONArray()
        val callObj = JSONObject()
        callObj.put("at", call.at)
        callObj.put("key", call.addonKey)
        callObj.put("url", call.url)
        if (call.ms != null) callObj.put("ms", call.ms)
        callObj.put("ok", call.ok)
        callObj.put("error", call.error ?: "")
        logArr.put(callObj)
        while (logArr.length() > MAX_LOG_ENTRIES) logArr.remove(0)
        prefs.addonHealthLogJson = logArr.toString()
    }

    /** Apaga o log do Addon Lab (o histórico de latência continua). */
    fun clearLog(prefs: PlatformPrefs) {
        prefs.addonHealthLogJson = ""
    }
}
