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
    /** Provider p/ invalidar o runtime em pool do plugin quando o código muda. */
    private val engineProvider: javax.inject.Provider<PluginEngine>,
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

    /** Plugins habilitados que atendem ao tipo ("movie" ou "tv"). Respeita a pausa global e a do repositório. */
    fun enabledPlugins(mediaType: String): List<InstalledPlugin> {
        if (!prefs.pluginGlobalEnabled) return emptyList()
        val paused = repos.filter { !it.enabled }.map { it.url }
        return plugins.filter {
            it.enabled && mediaType in it.supportedTypes &&
                paused.none { r -> r.equals(it.repoUrl, true) }
        }
    }

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
            // Refresh não despausa um repositório que o usuário pausou.
            enabled = prefs.pluginRepoList.firstOrNull { it.url.equals(manifestUrl.trim(), true) }?.enabled ?: true,
        )
        prefs.upsertPluginRepo(repo)
        for (scraper in manifest.scrapers) {
            if (!scraper.enabled) continue
            val codeUrl = resolveRelative(base, scraper.filename)
            val code = downloadText(codeUrl) ?: continue
            val sha = sha256(code)
            val prev = prefs.installedPluginList.firstOrNull { it.id == scraper.id }
            // O `continue` não pode ficar dentro do lambda do onFailure
            // ("break/continue in inline lambdas" só existe no Kotlin 2.2+;
            // o projeto usa 2.1.20) — por isso o teste é feito fora do lambda.
            if (runCatching { codeFile(scraper.id).writeText(code) }.isFailure) continue
            // Código novo: o runtime em pool desse plugin (se existir) tem o
            // código velho — descarta p/ a próxima execução recriar.
            if (prev?.sha256 != sha) runCatching { engineProvider.get().invalidate(scraper.id) }
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
        runCatching { engineProvider.get().invalidate(pluginId) }
        prefs.removePlugin(pluginId)
        runCatching { codeFile(pluginId).delete() }
        runCatching { File(cookieDir, "$pluginId.json").delete() }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) = prefs.setPluginEnabled(pluginId, enabled)

    /** Pausa/retoma um repositório (os plugins dele param de rodar nas fontes). */
    fun setRepoEnabled(repoUrl: String, enabled: Boolean) = prefs.setPluginRepoEnabled(repoUrl, enabled)

    /** Liga/desliga todos os plugins de um repositório de uma vez. */
    fun setAllPluginsEnabled(repoUrl: String, enabled: Boolean) = prefs.setPluginsOfRepoEnabled(repoUrl, enabled)

    /** Sobe (direction = -1) ou desce (+1) um repositório na ordem de prioridade. */
    fun moveRepo(url: String, direction: Int) {
        val list = prefs.pluginRepoList.toMutableList()
        val i = list.indexOfFirst { it.url.equals(url, true) }
        val j = (i + direction).coerceIn(0, list.size - 1)
        if (i < 0 || i == j) return
        val item = list.removeAt(i)
        list.add(j, item)
        prefs.pluginRepoList = list
    }

    /** Sobe (direction = -1) ou desce (+1) um plugin na ordem de prioridade dentro do repositório dele. */
    fun movePlugin(id: String, direction: Int) {
        val list = prefs.installedPluginList.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        val repo = list[i].repoUrl
        val same = list.indices.filter { list[it].repoUrl.equals(repo, true) }
        val pos = same.indexOf(i)
        val other = (pos + direction).coerceIn(0, same.size - 1)
        if (pos == other) return
        val item = list.removeAt(i)
        list.add(same[other], item)
        prefs.installedPluginList = list
    }

    /** Limpa o cache dos provedores: cookies + runtimes em pool (o próximo uso recria a sessão). */
    fun clearCache() {
        clearAllCookies()
        prefs.installedPluginList.forEach { runCatching { engineProvider.get().invalidate(it.id) } }
    }

    // ── Backup / restore (sem senhas nem sessões, como no JOTV) ─────────────

    /**
     * Exporta repos, plugins habilitados, ordem, timeout, concorrência e pausas num JSON.
     * NÃO inclui configurações dos plugins (podem ter senhas) nem cookies — igual ao backup do JOTV.
     */
    fun backup(): String {
        val o = JSONObject()
        o.put("format", "aotv.plugins")
        o.put("version", 1)
        o.put("enabled", prefs.pluginGlobalEnabled)
        o.put("autoUpdate", prefs.pluginAutoUpdate)
        o.put("timeoutSec", prefs.pluginTimeoutSec)
        o.put("concurrency", prefs.pluginConcurrency)
        val reposArr = JSONArray()
        for (repo in prefs.pluginRepoList) {
            val r = JSONObject()
            r.put("url", repo.url)
            r.put("name", repo.name)
            r.put("enabled", repo.enabled)
            val provArr = JSONArray()
            for (p in prefs.installedPluginList.filter { it.repoUrl.equals(repo.url, true) }) {
                provArr.put(JSONObject().put("id", p.id).put("enabled", p.enabled))
            }
            r.put("providers", provArr)
            reposArr.put(r)
        }
        o.put("repos", reposArr)
        return o.toString()
    }

    /**
     * Restaura um backup ("aotv.plugins", e também aceita o "jotv.plugins" do JOTV, com o
     * timeout em ms). Reinstala os repos que faltam, aplica habilitados, ordem, timeout e
     * concorrência. Devolve quantos repos voltaram e quantos falharam.
     */
    suspend fun restore(json: String): PluginRestoreResult {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return PluginRestoreResult(0, 1, 0)
        val format = o.optString("format")
        if (format != "aotv.plugins" && format != "jotv.plugins") return PluginRestoreResult(0, 1, 0)
        val fromJotv = format == "jotv.plugins"
        if (o.has("timeoutSec")) prefs.pluginTimeoutSec = o.optInt("timeoutSec", 60)
        else if (fromJotv && o.has("timeout")) prefs.pluginTimeoutSec = (o.optLong("timeout", 45_000L) / 1000).toInt()
        if (o.has("concurrency")) prefs.pluginConcurrency = o.optInt("concurrency", 10)
        if (o.has("autoUpdate")) prefs.pluginAutoUpdate = o.optBoolean("autoUpdate", true)
        if (o.has("enabled")) prefs.pluginGlobalEnabled = o.optBoolean("enabled", true)
        val reposArr = o.optJSONArray("repos") ?: return PluginRestoreResult(0, 1, 0)
        var reposOk = 0
        var reposFail = 0
        var pluginsOk = 0
        val wantedRepoOrder = mutableListOf<String>()
        val wantedPluginOrder = mutableMapOf<String, List<String>>()
        val wantedEnabled = mutableMapOf<String, Boolean>()
        for (i in 0 until reposArr.length()) {
            val r = reposArr.optJSONObject(i) ?: continue
            val url = r.optString("url")
            if (url.isBlank()) continue
            wantedRepoOrder += url
            val provArr = r.optJSONArray("providers")
            val ids = mutableListOf<String>()
            if (provArr != null) {
                for (j in 0 until provArr.length()) {
                    val p = provArr.optJSONObject(j) ?: continue
                    val id = p.optString("id")
                    if (id.isBlank()) continue
                    ids += id
                    wantedEnabled[id] = p.optBoolean("enabled", true)
                }
            }
            wantedPluginOrder[url] = ids
            val installed = installRepo(url)
            if (installed == null) {
                reposFail++
                continue
            }
            reposOk++
            setRepoEnabled(url, r.optBoolean("enabled", true))
            for (id in ids) {
                val plugin = prefs.installedPluginList.firstOrNull { it.id == id } ?: continue
                if (plugin.enabled != wantedEnabled[id]) setPluginEnabled(id, wantedEnabled[id] == true)
                pluginsOk++
            }
        }
        // Ordem do backup: repos na ordem do arquivo, e dentro de cada repo os plugins na ordem dele.
        if (wantedRepoOrder.isNotEmpty()) {
            val order = wantedRepoOrder.mapIndexed { idx, u -> u.lowercase() to idx }.toMap()
            prefs.pluginRepoList = prefs.pluginRepoList.sortedBy { order[it.url.lowercase()] ?: Int.MAX_VALUE }
        }
        for ((url, ids) in wantedPluginOrder) {
            if (ids.isEmpty()) continue
            val order = ids.mapIndexed { idx, id -> id to idx }.toMap()
            val all = prefs.installedPluginList
            val mine = all.filter { it.repoUrl.equals(url, true) }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
            val rest = all.filterNot { it.repoUrl.equals(url, true) }
            // Mantém a posição do bloco do repo onde estava o primeiro plugin dele.
            val first = all.indexOfFirst { it.repoUrl.equals(url, true) }.coerceAtLeast(0)
            val merged = (rest.take(first) + mine + rest.drop(first)).distinctBy { it.id }
            prefs.installedPluginList = merged
        }
        return PluginRestoreResult(reposOk, reposFail, pluginsOk)
    }

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
