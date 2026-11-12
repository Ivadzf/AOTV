package com.aotv.app.data.kodi

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loja de addons Kodi (v1.1, Fase 1).
 *
 * Instala addons de ZIP em `filesDir/kodi/addons/<id>/`, lista, habilita/
 * desabilita, remove e checa dependências. O addon é instalado INTACTO
 * (o loader do Vulgata verifica o SHA256 do .so — nada pode ser alterado).
 *
 * O Vulgata é Proprietary: nunca vai ao repo público e nunca é embutido
 * como asset — só por ZIP escolhido pelo usuário (SAF).
 *
 * Estado habilitado/desabilitado: `filesDir/kodi/addons-state.json`
 * (map id -> enabled). Sem entrada = habilitado.
 */
@Singleton
class KodiAddonStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class KodiAddon(
        val id: String,
        val version: String,
        val name: String,
        val dir: File,
        val extensionPoints: List<Pair<String, String>> = emptyList(), // (point, library)
        val requires: List<Dependency> = emptyList(),
        val enabled: Boolean = true,
    )

    data class Dependency(
        val addon: String,
        val version: String,
        val optional: Boolean,
    )

    data class DependencyIssue(
        val dependency: Dependency,
        /** "missing" (falta e trava) ou "fallback" (falta mas o addon tem fallback). */
        val severity: String,
        val detail: String,
    )

    val addonsDir: File
        get() = File(context.filesDir, "kodi/addons")

    private val stateFile: File
        get() = File(context.filesDir, "kodi/addons-state.json")

    // Módulos Python providos pelo app (pip em build-time) — a dependência
    // é considerada satisfeita mesmo sem addon instalado. simplejson é
    // o shim p/ json do aotv_kodi.py (Fase 3) — o BrazucaPlay o exige.
    // Fonte única: KodiRepository.PIP_MODULES (o instalador de
    // repositório usa a mesma lista p/ pular esses módulos).
    private val pipModules: Set<String> = KodiRepository.PIP_MODULES

    // Download de ZIP por URL (v1.4, Fase 4): mesmos timeouts do
    // PluginStore, DNS padrão (a URL do ZIP pode estar em qualquer
    // host — não é o DNS Cloudflare dos manifests).
    private val urlClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Teto do download de ZIP por URL (256 MB — não encher o aparelho). */
    private val maxDownloadBytes = 256L * 1024 * 1024

    // xbmc.python 3.0.0 = CPython 3.11 do Chaquopy (casa com o Kodi Omega).
    private val xbmcPythonVersion = "3.0.0"

    // ------------------------------------------------------------------
    // Estado habilitado/desabilitado (JSON simples em filesDir/kodi).
    // ------------------------------------------------------------------

    private fun readState(): MutableMap<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        try {
            val text = stateFile.takeIf { it.isFile }?.readText().orEmpty()
            // {"id": true, "id2": false} — parse manual, sem depender de Gson.
            val body = text.trim().removePrefix("{").removeSuffix("}").trim()
            if (body.isNotEmpty()) {
                for (part in body.split(",")) {
                    val kv = part.split(":", limit = 2)
                    if (kv.size == 2) {
                        val key = kv[0].trim().trim('"')
                        val value = kv[1].trim().equals("true", ignoreCase = true)
                        if (key.isNotEmpty()) map[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            // Estado corrompido — trata como vazio (tudo habilitado).
        }
        return map
    }

    private fun writeState(state: Map<String, Boolean>) {
        try {
            stateFile.parentFile?.mkdirs()
            val body = state.entries.joinToString(", ") { (k, v) -> "\"$k\": $v" }
            stateFile.writeText("{$body}", Charsets.UTF_8)
        } catch (e: Exception) {
            // Não travar o app por causa do estado.
        }
    }

    fun isEnabled(addonId: String): Boolean =
        readState()[addonId] ?: true

    fun setEnabled(addonId: String, enabled: Boolean) {
        val state = readState()
        state[addonId] = enabled
        writeState(state)
    }

    // ------------------------------------------------------------------
    // Instalação / listagem / remoção.
    // ------------------------------------------------------------------

    /**
     * Instala um addon de ZIP (SAF): copia o conteúdo p/ um arquivo
     * temporário e instala pelo caminho comum [installZipFile].
     */
    fun installZip(uri: Uri): Result<KodiAddon> {
        return runCatching {
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            val tmpDir = File(tmpRoot, "install-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val tmpZip = File(tmpDir, "addon.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmpZip.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Não consegui abrir o ZIP escolhido.")
                installZipFile(tmpZip).getOrThrow()
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    /**
     * Instala um addon a partir de um ZIP já em disco (v1.4, Fase 4 —
     * caminho comum do installZip (SAF), do resolveInstallUrl e do
     * installFromIndex (v1.5, repositório): valida
     * pelo conteúdo (magic PK), extrai com proteção contra zip-slip,
     * acha o addon.xml e move para filesDir/kodi/addons/<id>/, intacto
     * (substitui instalação anterior).
     */
    fun installZipFile(zipFile: File): Result<KodiAddon> {
        return runCatching {
            require(zipFile.isFile) { "Arquivo ZIP não encontrado." }
            // Valida pelo conteúdo (magic PK), não pelo MIME/extensão.
            val magic = ByteArray(4)
            FileInputStream(zipFile).use { it.read(magic) }
            require(
                magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
                    magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
                    magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08)),
            ) { "O arquivo não é um ZIP válido." }
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            val tmpDir = File(tmpRoot, "extract-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                // Extrai com proteção contra zip-slip.
                val extractDir = File(tmpDir, "extracted").apply { mkdirs() }
                extractZipTo(zipFile, extractDir)
                val addonDir = findAddonDir(extractDir)
                    ?: error("addon.xml não encontrado dentro do ZIP.")
                val xml = File(addonDir, "addon.xml")
                val parsed = parseAddonXml(addonDir, xml)
                    ?: error("addon.xml sem id/versão.")
                // Move para o destino final (substitui instalação anterior).
                val dest = File(addonsDir, parsed.id).apply {
                    if (exists()) deleteRecursively()
                    parentFile?.mkdirs()
                }
                require(addonDir.renameTo(dest)) { "Não consegui mover o addon para ${dest.path}." }
                parsed.copy(dir = dest, enabled = isEnabled(parsed.id))
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    /** Resultado da resolução de uma URL de instalação (v1.5, repositório). */
    sealed interface UrlResolution {
        /** ZIP direto instalado (ou repo com um único plugin, instalado direto). */
        data class Installed(
            val addon: KodiAddon,
            val summary: KodiRepository.InstallSummary? = null,
        ) : UrlResolution

        /** Índice de repositório resolvido — a UI pergunta qual addon instalar. */
        data class NeedsChoice(val index: KodiRepository.RepoIndex) : UrlResolution
    }

    /**
     * Baixa um arquivo p/ o disco (teto de 256 MB). Erros claros:
     * falha de rede, HTTP != 200, resposta vazia, acima do teto.
     */
    private suspend fun downloadToFile(url: String, dest: File): File {
        val request = Request.Builder().url(url).build()
        val response = runCatching { urlClient.newCall(request).execute() }.getOrElse {
            error("Falha de rede ao baixar: ${it.message}")
        }
        response.use { resp ->
            if (!resp.isSuccessful) error("O servidor devolveu HTTP ${resp.code}.")
            val body = resp.body ?: error("Resposta vazia do servidor.")
            var total = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxDownloadBytes) {
                            error("O arquivo é maior que ${maxDownloadBytes / (1024 * 1024)} MB — download cancelado.")
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
        }
        return dest
    }

    /** Baixa um texto (addons.xml); descompacta gzip quando pedido. */
    private suspend fun downloadText(url: String, gunzip: Boolean): String {
        val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
        val tmpDir = File(tmpRoot, "text-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val file = downloadToFile(url, File(tmpDir, "download"))
            val input = if (gunzip) GZIPInputStream(FileInputStream(file)) else FileInputStream(file)
            return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /** Extrai um ZIP p/ um diretório, com proteção contra zip-slip. */
    private fun extractZipTo(zipFile: File, dest: File) {
        dest.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                require(out.canonicalPath.startsWith(dest.canonicalPath)) {
                    "Entrada inválida no ZIP: ${entry.name}"
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Resolve uma URL de instalação (v1.5, suporte a repositório —
     * pedido do Davi, igual ao Kodi original): ZIP direto de addon,
     * ZIP de repositório, página HTML de repositório ou addons.xml
     * direto. ZIP direto instala na hora; índice resolvido volta
     * [UrlResolution.NeedsChoice] p/ a UI escolher o addon.
     * Erros claros em português em cada etapa.
     */
    suspend fun resolveInstallUrl(url: String): Result<UrlResolution> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = url.trim()
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "A URL precisa começar com http:// ou https://"
            }
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            val tmpDir = File(tmpRoot, "resolve-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val file = downloadToFile(trimmed, File(tmpDir, "download"))
                resolveDownloaded(file, trimmed, tmpDir, depth = 0)
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    /** Classifica o conteúdo baixado (ZIP, HTML ou XML) e resolve. */
    private suspend fun resolveDownloaded(
        file: File,
        sourceUrl: String,
        tmpDir: File,
        depth: Int,
    ): UrlResolution {
        require(depth <= 2) { "Redirecionamento de repositório muito profundo." }
        val magic = ByteArray(4)
        FileInputStream(file).use { it.read(magic) }
        val isZip = magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
            magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
            magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08))
        if (isZip) return resolveZip(file, tmpDir, depth)
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { "" }
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<")) {
            val root = KodiRepository.rootElementName(trimmed)
            if (root == "addons") {
                val index = KodiRepository.parseIndex(
                    trimmed, KodiRepository.dirOfUrl(sourceUrl), sourceUrl,
                ) ?: error("Não consegui ler o índice addons.xml.")
                return UrlResolution.NeedsChoice(index)
            }
            if (root == "addon") {
                error("A URL aponta para um addon.xml único, não para um índice de repositório.")
            }
            if (trimmed.contains("<html", ignoreCase = true)) {
                return resolveHtml(trimmed, sourceUrl, tmpDir, depth)
            }
        }
        error("O conteúdo da URL não é um ZIP, uma página de repositório nem um índice (addons.xml).")
    }

    /** ZIP baixado: addon normal instala; repositório vira índice. */
    private suspend fun resolveZip(file: File, tmpDir: File, depth: Int): UrlResolution {
        val extractDir = File(tmpDir, "zip-${System.currentTimeMillis()}").apply { mkdirs() }
        extractZipTo(file, File(extractDir, "extracted"))
        val addonDir = findAddonDir(File(extractDir, "extracted"))
            ?: error("addon.xml não encontrado dentro do ZIP.")
        val parsed = parseAddonXml(addonDir, File(addonDir, "addon.xml"))
            ?: error("addon.xml sem id/versão.")
        val isRepo = parsed.extensionPoints.any { (point, _) -> point == "xbmc.addon.repository" }
        if (!isRepo) {
            // ZIP normal de addon — instala como antes.
            return UrlResolution.Installed(installZipFile(file).getOrThrow())
        }
        val xmlText = File(addonDir, "addon.xml").readText(Charsets.UTF_8)
        val dirs = KodiRepository.parseRepoDirs(xmlText)
        val dir = KodiRepository.chooseDirForKodi(dirs)
            ?: error("O repositório não tem índice para o Kodi 21.")
        val indexText = downloadText(dir.infoUrl, dir.infoCompressed)
        val index = KodiRepository.parseIndex(indexText, dir.datadir.trimEnd('/'), dir.infoUrl)
            ?: error("Não consegui ler o índice do repositório.")
        return UrlResolution.NeedsChoice(index)
    }

    /** Página HTML: prefere repository.*.zip, depois addons.xml, depois ZIP direto. */
    private suspend fun resolveHtml(
        html: String,
        pageUrl: String,
        tmpDir: File,
        depth: Int,
    ): UrlResolution {
        val links = KodiRepository.extractRepoLinks(html, pageUrl)
        val repoZip = links.repoZips.firstOrNull()
        if (repoZip != null) {
            val file = downloadToFile(repoZip, File(tmpDir, "repo-${System.currentTimeMillis()}.zip"))
            return resolveDownloaded(file, repoZip, tmpDir, depth + 1)
        }
        val indexUrl = links.indexXmls.firstOrNull()
        if (indexUrl != null) {
            val text = downloadText(indexUrl, gunzip = indexUrl.lowercase().endsWith(".gz"))
            val index = KodiRepository.parseIndex(text, KodiRepository.dirOfUrl(pageUrl), indexUrl)
                ?: error("Não consegui ler o índice addons.xml.")
            return UrlResolution.NeedsChoice(index)
        }
        val directZip = links.directZips.firstOrNull()
        if (directZip != null) {
            val file = downloadToFile(directZip, File(tmpDir, "addon-${System.currentTimeMillis()}.zip"))
            return resolveDownloaded(file, directZip, tmpDir, depth + 1)
        }
        error("A página não tem link de repositório (repository.*.zip) nem de addons.xml.")
    }

    /**
     * Instala um addon do índice + as dependências dele, recursivamente
     * (v1.5, repositório): o plano vem de [KodiRepository.planInstall]
     * (dependências antes do addon; pula embutidas, binárias e já
     * instaladas). Cada ZIP é validado (magic PK + addon.xml com o id
     * esperado) antes de instalar pelo caminho comum [installZipFile].
     * `onProgress` recebe "Baixando X… (i/n)".
     */
    suspend fun installFromIndex(
        index: KodiRepository.RepoIndex,
        addonId: String,
        onProgress: (String) -> Unit = {},
    ): Result<KodiRepository.InstallSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = KodiRepository.planInstall(index, addonId) { id -> getAddon(id)?.version }
            val total = plan.toInstall.size
            val installed = mutableListOf<KodiAddon>()
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            for ((i, entry) in plan.toInstall.withIndex()) {
                val display = KodiPluginRunner.cleanKodiText(entry.name.ifBlank { entry.id })
                onProgress("Baixando $display… (${i + 1}/$total)")
                val zipUrl = index.zipUrlFor(entry)
                val tmpDir = File(tmpRoot, "dep-${System.currentTimeMillis()}-$i").apply { mkdirs() }
                try {
                    val zipFile = downloadToFile(zipUrl, File(tmpDir, "addon.zip"))
                    if (!validateZipAddonId(zipFile, entry.id)) {
                        error("O ZIP baixado para ${entry.id} não é o addon esperado.")
                    }
                    installed += installZipFile(zipFile).getOrThrow()
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Falha ao instalar ${entry.id} (${installed.size} de $total já instalados): ${e.message}",
                        e,
                    )
                } finally {
                    tmpDir.deleteRecursively()
                }
            }
            KodiRepository.InstallSummary(
                root = index.byId[addonId] ?: error("Addon $addonId não encontrado no índice."),
                installed = installed,
                skipped = plan.skipped,
            )
        }
    }

    /** Confere se o ZIP contém um addon.xml com o id esperado (antes de instalar). */
    private fun validateZipAddonId(zipFile: File, expectedId: String): Boolean {
        return try {
            val tmpRoot = File(context.filesDir, "kodi/addons-tmp").apply { mkdirs() }
            val checkDir = File(tmpRoot, "check-${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                extractZipTo(zipFile, File(checkDir, "extracted"))
                val addonDir = findAddonDir(File(checkDir, "extracted")) ?: return false
                val parsed = parseAddonXml(addonDir, File(addonDir, "addon.xml")) ?: return false
                parsed.id == expectedId
            } finally {
                checkDir.deleteRecursively()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Addons de vídeo habilitados (p/ a folha de fontes — grupo "Addons Kodi"). */
    fun videoAddons(): List<KodiAddon> =
        listAddons().filter { addon ->
            addon.enabled && addon.extensionPoints.any { (point, _) ->
                point == "xbmc.python.pluginsource"
            }
        }

    /** Todos os addons instalados (com estado habilitado/desabilitado). */
    fun listAddons(): List<KodiAddon> {
        val dirs = addonsDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir ->
            val xml = File(dir, "addon.xml")
            if (!xml.isFile) return@mapNotNull null
            parseAddonXml(dir, xml)?.let { addon -> addon.copy(enabled = isEnabled(addon.id)) }
        }.sortedBy { it.name.ifBlank { it.id }.lowercase() }
    }

    fun getAddon(addonId: String): KodiAddon? {
        val dir = File(addonsDir, addonId)
        val xml = File(dir, "addon.xml")
        if (!xml.isFile) return null
        return parseAddonXml(dir, xml)?.copy(enabled = isEnabled(addonId))
    }

    /** Remove o addon, seus dados (addon_data/<id>) e o estado. */
    fun removeAddon(addonId: String): Boolean {
        var ok = true
        try {
            File(addonsDir, addonId).takeIf { it.exists() }?.deleteRecursively()
        } catch (e: Exception) {
            ok = false
        }
        try {
            File(context.filesDir, "kodi/userdata/addon_data/$addonId")
                .takeIf { it.exists() }?.deleteRecursively()
        } catch (e: Exception) {
            ok = false
        }
        val state = readState()
        if (state.remove(addonId) != null) writeState(state)
        return ok
    }

    // ------------------------------------------------------------------
    // addon.xml — parse.
    // ------------------------------------------------------------------

    fun parseAddonXml(dir: File, xml: File): KodiAddon? {
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(xml.inputStream(), "UTF-8")
            var event = parser.eventType
            var id = ""
            var version = ""
            var name = ""
            val extensionPoints = mutableListOf<Pair<String, String>>()
            val requires = mutableListOf<Dependency>()
            var inRequires = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when {
                    event == XmlPullParser.START_TAG && parser.name == "addon" -> {
                        id = parser.getAttributeValue(null, "id").orEmpty()
                        version = parser.getAttributeValue(null, "version").orEmpty()
                        name = parser.getAttributeValue(null, "name").orEmpty()
                    }
                    event == XmlPullParser.START_TAG && parser.name == "requires" -> inRequires = true
                    event == XmlPullParser.END_TAG && parser.name == "requires" -> inRequires = false
                    event == XmlPullParser.START_TAG && parser.name == "import" && inRequires -> {
                        val dep = parser.getAttributeValue(null, "addon").orEmpty()
                        if (dep.isNotBlank()) {
                            requires += Dependency(
                                addon = dep,
                                version = parser.getAttributeValue(null, "version").orEmpty(),
                                optional = parser.getAttributeValue(null, "optional")
                                    .orEmpty().equals("true", ignoreCase = true),
                            )
                        }
                    }
                    event == XmlPullParser.START_TAG && parser.name == "extension" -> {
                        val point = parser.getAttributeValue(null, "point").orEmpty()
                        val library = parser.getAttributeValue(null, "library").orEmpty()
                        if (point.isNotBlank()) extensionPoints += point to library
                    }
                }
                event = parser.next()
            }
            if (id.isBlank()) return null
            KodiAddon(
                id = id, version = version, name = name, dir = dir,
                extensionPoints = extensionPoints, requires = requires,
                enabled = isEnabled(id),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun findAddonDir(root: File): File? {
        if (File(root, "addon.xml").isFile) return root
        val subdirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (subdirs.size == 1 && File(subdirs[0], "addon.xml").isFile) return subdirs[0]
        return null
    }

    // ------------------------------------------------------------------
    // Dependências.
    // ------------------------------------------------------------------

    /**
     * Checa os `<requires>` do addon. Devolve os problemas:
     * - "missing": falta e trava (addon de vídeo/script sem ele não roda).
     * - "fallback": falta mas o addon tem fallback no AOTV (ex.:
     *   inputstream.ffmpegdirect — o addon cai no player Kodi/ExoPlayer).
     * `script.module.*` via pip e `xbmc.python` 3.0.0 são satisfeitos
     * pelo app. `xbmc.addon` (versão do Kodi) é satisfeito (Omega 21).
     */
    fun checkDependencies(addon: KodiAddon): List<DependencyIssue> {
        val issues = mutableListOf<DependencyIssue>()
        for (dep in addon.requires) {
            when {
                dep.addon == "xbmc.python" -> {
                    // Chaquopy 3.11 = xbmc.python 3.0.0 (Kodi Omega).
                    if (dep.version.isNotBlank() && dep.version != xbmcPythonVersion) {
                        issues += DependencyIssue(
                            dep, "missing",
                            "Precisa de xbmc.python ${dep.version}; o AOTV tem $xbmcPythonVersion.",
                        )
                    }
                }
                dep.addon == "xbmc.addon" -> {
                    // API de addon do Kodi Omega — satisfeita.
                }
                dep.addon in pipModules -> {
                    // Provido pelo app em build-time (pip do Chaquopy).
                }
                dep.addon.startsWith("inputstream.") -> {
                    // Binário de inputstream não existe no AOTV — o addon
                    // cai no fallback do player Kodi (ExoPlayer).
                    issues += DependencyIssue(
                        dep, "fallback",
                        "${dep.addon} não existe no AOTV — o addon usa o player do app.",
                    )
                }
                dep.addon.startsWith("script.") || dep.addon.startsWith("plugin.") -> {
                    // Outro addon/script — precisa estar instalado.
                    if (getAddon(dep.addon) == null) {
                        issues += DependencyIssue(
                            dep, if (dep.optional) "fallback" else "missing",
                            "Addon ${dep.addon} não instalado.",
                        )
                    }
                }
                else -> {
                    if (getAddon(dep.addon) == null && !dep.optional) {
                        issues += DependencyIssue(
                            dep, "missing",
                            "Dependência ${dep.addon} não instalada.",
                        )
                    }
                }
            }
        }
        return issues
    }
}
