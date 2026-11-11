package com.aotv.app.data.kodi

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
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
    // é considerada satisfeita mesmo sem addon instalado.
    private val pipModules = setOf(
        "script.module.requests",
        "script.module.beautifulsoup4",
        "script.module.six",
        "script.module.kodi-six",
    )

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
     * Instala um addon de ZIP (SAF): extrai, acha o addon.xml (na raiz ou
     * num único subdiretório, como o ZIP do Vulgata) e move para
     * filesDir/kodi/addons/<id>/, intacto.
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
                // Valida pelo conteúdo (magic PK), não pelo MIME/extensão.
                val magic = ByteArray(4)
                FileInputStream(tmpZip).use { it.read(magic) }
                require(
                    magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
                        magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
                        magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x07, 0x08)),
                ) { "O arquivo escolhido não é um ZIP válido." }
                // Extrai com proteção contra zip-slip.
                val extractDir = File(tmpDir, "extracted").apply { mkdirs() }
                ZipInputStream(FileInputStream(tmpZip)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(extractDir, entry.name)
                        require(out.canonicalPath.startsWith(extractDir.canonicalPath)) {
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

    /** Todos os addons instalados (com estado habilitado/desabilitado). */
    fun listAddons(): List<KodiAddon> {
        val dirs = addonsDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir ->
            val xml = File(dir, "addon.xml")
            if (!xml.isFile) return@mapNotNull null
            parseAddonXml(dir, xml)?.copy(enabled = isEnabled(it.id))
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
