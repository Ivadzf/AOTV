package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado do teste de uma fonte/API. */
data class SourceStatus(
    val name: String,
    val url: String,
    val online: Boolean,
    val latencyMs: Long,
    val detail: String, // "HTTP 200" ou mensagem de erro
)

/**
 * Verifica se o TMDB e os addons Stremio do usuário respondem. Faz GET curto (via DNS sobre HTTPS
 * da Cloudflare) em cada host e mede a latência. Só informa nas Configurações, não derruba fonte.
 */
@Singleton
class SourceHealthChecker @Inject constructor(
    private val prefs: PlatformPrefs,
) {
    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    /** Testa o TMDB e os addons do usuário em paralelo. */
    suspend fun checkAll(): List<SourceStatus> = withContext(Dispatchers.IO) {
        val targets = buildList {
            // /3/ sozinho (sem api_key) sempre dá 401 — o TMDB exige chave em toda rota real.
            // Isso fazia o teste mostrar "fora do ar" mesmo com o TMDB funcionando normalmente
            // no resto do app. /configuration é a rota padrão pra testar chave + API de uma vez.
            add("TMDB (catálogo)" to "https://api.themoviedb.org/3/configuration?api_key=${prefs.tmdbApiKey}")
            prefs.stremioAddonList().forEach { url ->
                val host = runCatching { java.net.URI(url).host }.getOrNull() ?: "addon"
                add("Addon Stremio — $host" to url)
            }
        }
        coroutineScope {
            targets.map { (name, url) -> async { check(name, url) } }.awaitAll()
        }.sortedWith(compareByDescending<SourceStatus> { it.online }.thenBy { it.latencyMs })
    }

    private fun check(name: String, url: String): SourceStatus {
        val start = System.currentTimeMillis()
        return runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", ua)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val ms = System.currentTimeMillis() - start
                // 2xx/3xx = online; 4xx ainda indica host VIVO (responde), mas marca como instável.
                val ok = resp.code in 200..399
                SourceStatus(name, url, ok, ms, "HTTP ${resp.code}")
            }
        }.getOrElse {
            val ms = System.currentTimeMillis() - start
            SourceStatus(name, url, false, ms, it.message?.take(60) ?: "sem resposta")
        }
    }
}
