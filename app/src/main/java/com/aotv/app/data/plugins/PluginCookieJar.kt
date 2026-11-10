package com.aotv.app.data.plugins

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * CookieJar do OkHttp com persistência em disco, UM arquivo por scraper
 * (filesDir/plugin-cookies/<scraperId>.json). É o que o Nuvio não faz: lá o
 * fetch é stateless e o cookie morre no fim da execução; aqui o cookie
 * sobrevive entre duas execuções do app — critério de aceite da v1.
 *
 * O arquivo é regravado a cada resposta que traz Set-Cookie (são poucos
 * cookies por plugin; o custo é desprezível). Cookies expirados são
 * descartados na leitura.
 */
class PluginCookieJar(
    private val file: File,
) : CookieJar {

    @Volatile
    private var cookies: MutableList<Cookie> = load()

    private fun load(): MutableList<Cookie> {
        val raw = runCatching { file.readText() }.getOrNull() ?: return mutableListOf()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return mutableListOf()
        val now = System.currentTimeMillis()
        val list = mutableListOf<Cookie>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val c = runCatching {
                Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .domain(o.optString("domain"))
                    .path(o.optString("path", "/"))
                    .expiresAt(o.optLong("expiresAt", Long.MAX_VALUE))
                    .apply {
                        if (o.optBoolean("secure", false)) secure()
                        if (o.optBoolean("httpOnly", false)) httpOnly()
                        if (o.optBoolean("hostOnly", false)) hostOnlyDomain(o.optString("domain"))
                    }
                    .build()
            }.getOrNull() ?: continue
            if (c.expiresAt > now) list += c
        }
        return list
    }

    private fun persist() {
        runCatching {
            val arr = JSONArray()
            val now = System.currentTimeMillis()
            cookies.filter { it.expiresAt > now }.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("name", c.name)
                        .put("value", c.value)
                        .put("domain", c.domain)
                        .put("path", c.path)
                        .put("expiresAt", c.expiresAt)
                        .put("secure", c.secure)
                        .put("httpOnly", c.httpOnly)
                        .put("hostOnly", c.hostOnly),
                )
            }
            file.parentFile?.mkdirs()
            file.writeText(arr.toString())
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        val kept = this.cookies.filter { it.expiresAt > now }.toMutableList()
        for (c in cookies) {
            kept.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > now) kept += c
        }
        this.cookies = kept
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookies.filter { it.expiresAt > now && it.matches(url) }
    }

    /** Apaga todos os cookies desse scraper (botão "Limpar cookies dos plugins"). */
    fun clear() {
        cookies = mutableListOf()
        runCatching { if (file.exists()) file.delete() }
    }
}
