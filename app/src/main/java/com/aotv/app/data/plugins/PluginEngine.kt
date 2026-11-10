package com.aotv.app.data.plugins

import android.content.Context
import android.util.Base64
import android.util.Log
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AppDns
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor JS dos plugins do Nuvio (QuickJS via dokar3/quickjs-kt-android 1.0.18,
 * mesmo lineage do motor do Nuvio).
 *
 * Diferenças para o Nuvio (o motivo do AOTV existir):
 * - fetch com cookie jar PERSISTENTE em disco por scraper (PluginCookieJar);
 * - timeout por execução ajustável (padrão 60 s, 30–180 s nas Configurações).
 *
 * Uma instância NOVA de QuickJs por execução (igual ao Nuvio): nada persiste
 * entre execuções além dos cookies (disco) — sem localStorage, sem WebSocket
 * (fica p/ v1.1), sem WASM (stub, como no Nuvio).
 *
 * O Rhino foi descartado: testado localmente, nem 1.7.14 nem 1.8.1 parseiam
 * `async function` — e 49 dos 55 providers usam `async function getStreams`.
 */
@Singleton
class PluginEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
) {

    companion object {
        private const val TAG = "AOTVPlugin"

        /** UA de browser: vários hosts bloqueiam UA vazio ou de app. */
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }

    private var cachedCryptoJs: String? = null

    private fun readCryptoJsAsset(): String? {
        cachedCryptoJs?.let { return it }
        val text = runCatching {
            context.assets.open("plugin-libs/crypto-js-4.2.0.js").bufferedReader().use { it.readText() }
        }.getOrNull()
        cachedCryptoJs = text
        return text
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Roda getStreams(tmdbId, mediaType, season, episode, settings) do plugin e
     * devolve os streams crus. Erro de JS, timeout ou código inválido vira
     * Result.failure — um plugin quebrado não derruba os outros.
     */
    suspend fun runGetStreams(
        plugin: InstalledPlugin,
        code: String,
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        settings: Map<String, Any?>,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
    ): Result<List<PluginStreamRaw>> = runCatching {
        val settingsJson = JSONObject().apply {
            settings.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JSONObject.NULL)
                    is Boolean, is Number, is String -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }.toString()
        val json = execute(code, timeoutMs, cookieJar) { qjs ->
            qjs.evaluate<String>(
                PluginPrelude.getStreamsDriver(tmdbId, mediaType, season, episode, settingsJson),
            )
        }
        parseStreams(json)
    }

    /** Roda onSettings() do plugin e devolve os descritores (vazio se não exportar). */
    suspend fun runOnSettings(
        plugin: InstalledPlugin,
        code: String,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
    ): Result<List<PluginSettingDescriptor>> = runCatching {
        val json = execute(code, timeoutMs, cookieJar) { qjs ->
            qjs.evaluate<String>(PluginPrelude.ON_SETTINGS_DRIVER)
        }
        parseSettings(json)
    }

    // ── Execução ──────────────────────────────────────────────────────────────

    private suspend fun <T> execute(
        code: String,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
        driver: suspend (QuickJs) -> T,
    ): T {
        val qjs = QuickJs.create(Dispatchers.Default)
        try {
            qjs.setEvaluationTimeoutMillis(timeoutMs)
            val cheerio = PluginCheerio()
            val fetchClient = AppDns.mediaClient.newBuilder()
                .cookieJar(cookieJar)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            defineBindings(qjs, cheerio, fetchClient)
            qjs.evaluate<String>(PluginPrelude.build(prefs.tmdbApiKey) + "\n;'ok';")
            readCryptoJsAsset()?.let { cryptoJs ->
                qjs.evaluate<String>(cryptoJs + "\n;'ok';")
                qjs.evaluate<String>(PluginPrelude.POST_CRYPTOJS)
            }
            qjs.evaluate<String>(code + "\n;'ok';")
            // O timeout do motor corta o JS em timeoutMs; os 15 s extras são só
            // a margem p/ o fetch em andamento devolver (callTimeout = timeoutMs).
            return withTimeout(timeoutMs + 15_000) { driver(qjs) }
        } finally {
            runCatching { qjs.close() }
        }
    }

    private fun defineBindings(qjs: QuickJs, cheerio: PluginCheerio, fetchClient: OkHttpClient) {
        qjs.define("console") {
            function("log") { args -> pluginLog("D", args); 0 }
            function("info") { args -> pluginLog("I", args); 0 }
            function("warn") { args -> pluginLog("W", args); 0 }
            function("error") { args -> pluginLog("E", args); 0 }
        }
        qjs.function("__atob") { args ->
            runCatching { atob(argStr(args, 0)) }.getOrElse { throw IllegalArgumentException("atob: entrada inválida") }
        }
        qjs.function("__btoa") { args ->
            runCatching { btoa(argStr(args, 0)) }.getOrElse { throw IllegalArgumentException("btoa: entrada inválida") }
        }
        qjs.asyncFunction("__plugin_sleep") { args ->
            delay(argLong(args, 0).coerceAtLeast(0))
            1
        }
        qjs.asyncFunction("__nativeFetch") { args -> nativeFetch(args, fetchClient) }
        qjs.function("__parseUrl") { args -> parseUrl(argStr(args, 0), argNullableStr(args, 1)) }
        qjs.function("__aesGcmDecrypt") { args ->
            aesGcmDecrypt(argStr(args, 0), argStr(args, 1), argStr(args, 2))
        }
        qjs.function("__cheerioLoad") { args -> cheerio.load(argStr(args, 0)) }
        qjs.function("__cheerioSelect") { args -> cheerio.select(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioText") { args -> cheerio.text(argInt(args, 0)) }
        qjs.function("__cheerioAttr") { args -> cheerio.attr(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioHtml") { args -> cheerio.html(argInt(args, 0)) }
        qjs.function("__cheerioCount") { args -> cheerio.count(argInt(args, 0)) }
        qjs.function("__cheerioElement") { args -> cheerio.element(argInt(args, 0), argInt(args, 1)) }
        qjs.function("__cheerioChildren") { args -> cheerio.children(argInt(args, 0)) }
        qjs.function("__cheerioParent") { args -> cheerio.parent(argInt(args, 0)) }
        qjs.function("__cheerioFilter") { args -> cheerio.filter(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioCombine") { args -> cheerio.combine(argStr(args, 0)) }
        qjs.function("__cheerioParseFragment") { args -> cheerio.parseFragment(argStr(args, 0)) }
    }

    // ── Bindings: HTTP ────────────────────────────────────────────────────────

    /**
     * fetch do JS via OkHttp, com o cookie jar do scraper. Devolve JSON
     * {status, statusText, headers: {nome: [valores]}, body}. Erro de rede
     * vira status 0 (o provider decide pelo res.ok) em vez de rejeitar a
     * Promise — um fetch que falha não pode travar a execução inteira.
     */
    private suspend fun nativeFetch(args: Array<Any?>, client: OkHttpClient): String {
        val url = argStr(args, 0)
        val opts = runCatching { JSONObject(argStr(args, 1)) }.getOrNull() ?: JSONObject()
        val method = opts.optString("method", "GET").uppercase().ifBlank { "GET" }
        val headersObj = opts.optJSONObject("headers")
        val bodyText = if (opts.isNull("body")) null else opts.optString("body")
        return try {
            val reqBuilder = Request.Builder().url(url)
            headersObj?.keys()?.forEach { name ->
                when (val v = headersObj.get(name)) {
                    is JSONArray -> for (i in 0 until v.length()) reqBuilder.addHeader(name, v.optString(i))
                    else -> reqBuilder.addHeader(name, v.toString())
                }
            }
            if (!hasHeaderIgnoreCase(headersObj, "user-agent")) reqBuilder.header("User-Agent", BROWSER_UA)
            val contentType = headersObj?.let { findHeaderIgnoreCase(it, "content-type") }?.toMediaTypeOrNull()
            val requestBody =
                if (bodyText != null && method != "GET" && method != "HEAD") bodyText.toRequestBody(contentType) else null
            reqBuilder.method(method, requestBody)
            withContext(Dispatchers.IO) {
                client.newCall(reqBuilder.build()).execute().use { res ->
                    val headersJson = JSONObject()
                    res.headers.forEach { (name, value) ->
                        val key = name.lowercase()
                        val arr = headersJson.optJSONArray(key) ?: JSONArray().also { headersJson.put(key, it) }
                        arr.put(value)
                    }
                    JSONObject()
                        .put("status", res.code)
                        .put("statusText", res.message)
                        .put("headers", headersJson)
                        .put("body", res.body?.string().orEmpty())
                        .toString()
                }
            }
        } catch (e: Exception) {
            JSONObject()
                .put("status", 0)
                .put("statusText", e.message ?: "network error")
                .put("headers", JSONObject())
                .put("body", "")
                .toString()
        }
    }

    private fun hasHeaderIgnoreCase(headers: JSONObject?, name: String): Boolean {
        if (headers == null) return false
        val it = headers.keys()
        while (it.hasNext()) if (it.next().equals(name, ignoreCase = true)) return true
        return false
    }

    private fun findHeaderIgnoreCase(headers: JSONObject, name: String): String? {
        val it = headers.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k.equals(name, ignoreCase = true)) return headers.optString(k)
        }
        return null
    }

    // ── Bindings: utilidades ──────────────────────────────────────────────────

    private fun atob(s: String): String =
        String(Base64.decode(s, Base64.DEFAULT), Charsets.ISO_8859_1)

    private fun btoa(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP)

    /** new URL(url, base) — parse com java.net.URI; ok=false vira TypeError no JS. */
    private fun parseUrl(url: String, base: String?): String {
        return runCatching {
            val uri = if (base != null) URI(base).resolve(url) else URI(url)
            val scheme = uri.scheme ?: throw IllegalArgumentException("sem scheme")
            val host = uri.host.orEmpty()
            val port = if (uri.port >= 0) uri.port.toString() else ""
            val authority = uri.authority.orEmpty()
            JSONObject()
                .put("ok", true)
                .put("href", uri.toString())
                .put("protocol", "$scheme:")
                .put("host", if (port.isNotEmpty()) "$host:$port" else host)
                .put("hostname", host)
                .put("port", port)
                .put("pathname", uri.rawPath?.ifEmpty { "/" } ?: "/")
                .put("search", uri.rawQuery?.let { "?$it" } ?: "")
                .put("hash", uri.rawFragment?.let { "#$it" } ?: "")
                .put("origin", "$scheme://$authority")
                .put("username", "")
                .put("password", "")
                .toString()
        }.getOrElse { JSONObject().put("ok", false).toString() }
    }

    /**
     * AES-GCM (javax.crypto) p/ o patch do CryptoJS: o crypto-js 4.2.0 não tem
     * modo GCM. Entradas em Base64 padrão; o ciphertext traz a tag de 16 bytes
     * no fim (formato do CryptoJS). Falha de autenticação lança — o provider
     * trata no try/catch dele.
     */
    private fun aesGcmDecrypt(ciphertextB64: String, keyB64: String, ivB64: String): String {
        val ct = Base64.decode(ciphertextB64, Base64.DEFAULT)
        val key = Base64.decode(keyB64, Base64.DEFAULT)
        val iv = Base64.decode(ivB64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun pluginLog(level: String, args: Array<Any?>) {
        val msg = args.joinToString(" ") { it?.toString().orEmpty() }.take(2000)
        when (level) {
            "E" -> Log.e(TAG, msg)
            "W" -> Log.w(TAG, msg)
            else -> Log.d(TAG, msg)
        }
    }

    // ── Parse dos resultados ──────────────────────────────────────────────────

    /** Normaliza o retorno de getStreams: array de objetos ou de strings. */
    private fun parseStreams(json: String): List<PluginStreamRaw> {
        val arr = runCatching {
            val trimmed = json.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optJSONArray("streams") ?: JSONArray()
            } else {
                JSONArray(trimmed)
            }
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PluginStreamRaw>()
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> if (item.startsWith("http")) out += PluginStreamRaw(url = item)
                is JSONObject -> {
                    val url = item.optString("url").ifBlank { item.optString("file") }
                        .ifBlank { item.optString("stream") }
                    if (!url.startsWith("http")) continue
                    val title = item.optString("title")
                    val label = item.optString("label").ifBlank { item.optString("name") }
                    out += PluginStreamRaw(url = url, title = title, label = label)
                }
            }
        }
        return out
    }

    private fun parseSettings(json: String): List<PluginSettingDescriptor> {
        val arr = runCatching { JSONArray(json.trim()) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PluginSettingDescriptor>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type")
            if (type.isBlank()) continue
            val options = mutableListOf<PluginSettingOption>()
            val optsArr = o.optJSONArray("options")
            if (optsArr != null) {
                for (j in 0 until optsArr.length()) {
                    val oo = optsArr.optJSONObject(j) ?: continue
                    options += PluginSettingOption(
                        label = oo.optString("label"),
                        value = oo.optString("value"),
                    )
                }
            }
            out += PluginSettingDescriptor(
                type = type,
                key = o.optString("key"),
                label = o.optString("label"),
                options = options,
                defaultValue = o.opt("defaultValue"),
            )
        }
        return out
    }

    // ── Argumentos dos bindings ───────────────────────────────────────────────

    private fun argStr(args: Array<Any?>, i: Int): String = args.getOrNull(i)?.toString().orEmpty()

    private fun argNullableStr(args: Array<Any?>, i: Int): String? = args.getOrNull(i)?.toString()

    private fun argInt(args: Array<Any?>, i: Int): Int =
        (args.getOrNull(i) as? Number)?.toInt() ?: args.getOrNull(i)?.toString()?.toIntOrNull() ?: 0

    private fun argLong(args: Array<Any?>, i: Int): Long =
        (args.getOrNull(i) as? Number)?.toLong() ?: args.getOrNull(i)?.toString()?.toLongOrNull() ?: 0L
}
