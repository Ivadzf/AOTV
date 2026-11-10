package com.aotv.app.data.plugins

import android.content.Context
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AppDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciamento dos repositórios e plugins do Nuvio.
 *
 * Metadados em PlatformPrefs (mesmo padrão dos addons Stremio); código JS em
 * filesDir/plugin-code/<id>.js (com SHA256 guardado nos metadados, como o
 * Nuvio guarda em code/scraper-<id>.js); cookies em
 * filesDir/plugin-cookies/<id>.json (um PluginCookieJar por scraper).
 *
 * Instalar = baixar o manifest, baixar o .js de cada scraper habilitado e
 * gravar tudo. Atualizar = o mesmo, preservando enabled e configurações.
 * Refresh automático a cada 6 h (igual ao Nuvio) quando a tela de Plugins abre.
 */
@Singleton
class PluginStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
) {

    companion object {
        /** URL do repo do Davi, pré-preenchida na tela de Plugins (não instalada sozinha). */
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/Ivadzf/ivadz-nuvio-repo/main/manifest.json"

        /** Intervalo de refresh automático dos repos (6 h, igual ao Nuvio). */
        const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000
    }

    private val httpClient = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val codeDir: File get() = File(context.filesDir, "plugin-code").apply { mkdirs() }
    private val cookieDir: File get() = File(context.filesDir, "plugin-cookies").apply { mkdirs() }

    fun codeFile(pluginId: String): File = File(codeDir, "$pluginId.js")

    fun readCode(pluginId: String): String? =
        runCatching { codeFile(pluginId).readText() }.getOrNull()

    fun cookieJar(pluginId: String): PluginCookieJar =
        PluginCookieJar(File(cookieDir, "$pluginId.json"))

    /** Apaga os cookies de TODOS os plugins (botão "Limpar cookies dos plugins"). */
    fun clearAllCookies() {
        runCatching { cookieDir.listFiles()?.forEach { it.delete() } }
    }

    val repos: List<PluginRepo> get() = prefs.pluginRepoList
    val plugins: List<InstalledPlugin> get() = prefs.installedPluginList

    /** Plugins habilitados que atendem ao tipo ("movie" ou "tv"). */
    fun enabledPlugins(mediaType: String): List<InstalledPlugin> =
        plugins.filter { it.enabled && mediaType in it.supportedTypes }

    /** Baixa o manifest de um repo (sem instalar). null quando inválido/sem rede. */
    suspend fun fetchManifest(manifestUrl: String): PluginRepoManifest? = withContext(Dispatchers.IO) {
        val url = manifestUrl.trim()
        val body = runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null
        runCatching { parseManifest(body) }.getOrNull()
    }

    /**
     * Instala (ou atualiza) um repositório: baixa o manifest e o .js de cada
     * scraper habilitado no manifest. Devolve o repo instalado, ou null se o
     * manifest não baixou. Scrapers que falham baixar são pulados (os outros
     * instalam normalmente) e o erro vai p/ o log.
     */
    suspend fun installRepo(manifestUrl: String): PluginRepo? {
        val manifest = fetchManifest(manifestUrl) ?: return null
        val base = manifestUrl.trim().removeSuffix("/manifest.json").removeSuffix("/")
        val now = System.currentTimeMillis()
        val repo = PluginRepo(
            url = manifestUrl.trim(), name = manifest.name,
            version = manifest.version, updatedAt = now,
        )
        prefs.upsertPluginRepo(repo)
        for (scraper in manifest.scrapers) {
            if (!scraper.enabled) continue
            val codeUrl = resolveRelative(base, scraper.filename)
            val code = downloadText(codeUrl) ?: continue
            val sha = sha256(code)
            val prev = prefs.installedPluginList.firstOrNull { it.id == scraper.id }
            runCatching { codeFile(scraper.id).writeText(code) }.onFailure { continue }
            prefs.upsertPlugin(
                InstalledPlugin(
                    id = scraper.id,
                    name = scraper.name.ifBlank { scraper.id },
                    repoUrl = repo.url,
                    version = scraper.version,
                    enabled = prev?.enabled ?: true,
                    sha256 = sha,
                    updatedAt = now,
                    supportedTypes = scraper.supportedTypes,
                ),
            )
        }
        return repo
    }

    /** Atualiza um repo já instalado (mesmo que instalar, preservando enabled). */
    suspend fun refreshRepo(repoUrl: String): PluginRepo? = installRepo(repoUrl)

    /** Atualiza os repos vencidos (> 6 h). Devolve quantos atualizaram. */
    suspend fun refreshDueRepos(): Int {
        val now = System.currentTimeMillis()
        var count = 0
        for (repo in prefs.pluginRepoList) {
            if (now - repo.updatedAt < REFRESH_INTERVAL_MS) continue
            if (refreshRepo(repo.url) != null) count++
        }
        return count
    }

    /** Remove o repo e os plugins que vieram dele (código e cookies juntos). */
    fun removeRepo(repoUrl: String) {
        val gone = prefs.installedPluginList.filter { it.repoUrl.equals(repoUrl, true) }
        gone.forEach { removePlugin(it.id) }
        prefs.removePluginRepo(repoUrl)
    }

    /** Remove um plugin (metadados, código e cookies). */
    fun removePlugin(pluginId: String) {
        prefs.removePlugin(pluginId)
        runCatching { codeFile(pluginId).delete() }
        runCatching { File(cookieDir, "$pluginId.json").delete() }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) = prefs.setPluginEnabled(pluginId, enabled)

    fun pluginSettings(pluginId: String): Map<String, Any?> = prefs.pluginSettings(pluginId)

    fun setPluginSettings(pluginId: String, values: Map<String, Any?>) = prefs.setPluginSettings(pluginId, values)

    var timeoutSec: Int
        get() = prefs.pluginTimeoutSec
        set(value) { prefs.pluginTimeoutSec = value }

    // ── Internos ──────────────────────────────────────────────────────────────

    private suspend fun downloadText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        }.getOrNull()
    }

    private fun resolveRelative(base: String, filename: String): String {
        if (filename.startsWith("http://") || filename.startsWith("https://")) return filename
        return "$base/${filename.removePrefix("/")}"
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun parseManifest(body: String): PluginRepoManifest? {
        val o = JSONObject(body)
        val scrapersJson = o.optJSONArray("scrapers") ?: return null
        val scrapers = mutableListOf<PluginScraperMeta>()
        for (i in 0 until scrapersJson.length()) {
            val s = scrapersJson.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val filename = s.optString("filename")
            if (id.isBlank() || filename.isBlank()) continue
            val types = mutableListOf<String>()
            val typesArr = s.optJSONArray("supportedTypes")
            if (typesArr != null) {
                for (j in 0 until typesArr.length()) types += typesArr.optString(j)
            }
            scrapers += PluginScraperMeta(
                id = id,
                name = s.optString("name").ifBlank { id },
                description = s.optString("description"),
                version = s.optString("version"),
                author = s.optString("author"),
                supportedTypes = types.ifEmpty { listOf("movie", "tv") },
                filename = filename,
                enabled = s.optBoolean("enabled", true),
                contentLanguage = s.optString("contentLanguage"),
            )
        }
        return PluginRepoManifest(
            name = o.optString("name"),
            version = o.optString("version"),
            scrapers = scrapers,
        )
    }
}
