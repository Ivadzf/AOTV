package com.aotv.app.data.kodi

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL

/**
 * Suporte a repositório Kodi (v1.5.0).
 *
 * Resolve uma URL de instalação em etapas, igual ao Kodi original:
 * - ZIP direto de addon: instala como antes.
 * - ZIP de repositório (addon.xml com `xbmc.addon.repository`): lê os
 *   `<dir>`, escolhe o da versão do Kodi 21, baixa o `<info>`
 *   (addons.xml, com gzip se `compressed="true"`) e usa o `<datadir>`
 *   como base dos ZIPs.
 * - Página HTML: procura links p/ `repository.*.zip` (preferido),
 *   `addons.xml` ou ZIP direto de addon.
 * - addons.xml direto: o índice, com base = diretório da URL.
 *
 * Depois do índice resolvido, a UI pergunta qual addon de vídeo
 * instalar (ou instala direto se for um só) e o [KodiAddonStore]
 * instala o escolhido + as dependências, recursivamente, a partir do
 * mesmo índice.
 *
 * O repositório NÃO é registrado p/ atualizações (instalação única).
 * Nada aqui embute credenciais nem baixa de fonte que o usuário não
 * tenha colado.
 */
object KodiRepository {

    /** Versão do Kodi que o AOTV emula (Omega). */
    const val KODI_MAJOR_VERSION = 21

    /** Teto de profundidade da resolução de dependências. */
    const val MAX_DEP_DEPTH = 5

    /** Dependências embutidas no Kodi — nunca instaladas. */
    val BUILTIN_DEPS = setOf(
        "xbmc.python", "xbmc.gui", "xbmc.json", "xbmc.addon", "xbmc.vfs", "xbmc",
    )

    /**
     * Módulos Python providos pelo app (pip em build-time) — a
     * dependência é considerada satisfeita mesmo sem addon instalado.
     * Mesma lista do [KodiAddonStore.checkDependencies]. Importante:
     * o índice do repo costuma trazer versões antigas desses módulos
     * (ex.: o requests 2.25.1 do Vikings exige certifi/chardet/idna/
     * urllib3 mais novos que os do índice) — instalar as versões do
     * repo falharia ou sombrearia o pip do app. Por isso o plano pula
     * esses módulos e instala só o que o app não provê.
     */
    val PIP_MODULES = setOf(
        "script.module.requests",
        "script.module.beautifulsoup4",
        "script.module.six",
        "script.module.kodi-six",
        "script.module.simplejson",
    )

    // ------------------------------------------------------------------
    // Modelo.
    // ------------------------------------------------------------------

    data class RepoAddonEntry(
        val id: String,
        val version: String,
        val name: String,
        val requires: List<KodiAddonStore.Dependency> = emptyList(),
        val extensionPoints: List<String> = emptyList(),
    ) {
        val isVideoPlugin: Boolean get() = "xbmc.python.pluginsource" in extensionPoints
    }

    data class RepoIndex(
        val addons: List<RepoAddonEntry>,
        val datadirBase: String,
        val sourceUrl: String,
    ) {
        /** Por id — maior versão quando houver duplicata. */
        val byId: Map<String, RepoAddonEntry> = addons
            .groupBy { it.id }
            .mapValues { (_, list) ->
                list.maxWithOrNull(Comparator { a, b -> compareVersions(a.version, b.version) })
                    ?: list.first()
            }

        /** Addons de vídeo do índice, ordenados por nome. */
        fun videoPlugins(): List<RepoAddonEntry> =
            addons.filter { it.isVideoPlugin }
                .sortedBy { it.name.ifBlank { it.id }.lowercase() }

        /** URL do ZIP do addon no datadir do índice. */
        fun zipUrlFor(entry: RepoAddonEntry): String =
            "${datadirBase.trimEnd('/')}/${entry.id}/${entry.id}-${entry.version}.zip"
    }

    data class RepoDir(
        val minVersion: String?,
        val maxVersion: String?,
        val infoUrl: String,
        val infoCompressed: Boolean,
        val datadir: String,
    )

    data class SkippedDep(val id: String, val reason: String) {
        companion object {
            const val BINARY = "binário"
            const val BUILTIN = "embutido no Kodi"
            const val INSTALLED = "já instalado"
            const val CYCLE = "ciclo"
            const val OPTIONAL_MISSING = "opcional, ausente no índice"
            const val PROVIDED = "provido pelo app"
        }
    }

    data class InstallPlan(
        /** Ordem de instalação: dependências antes do addon. */
        val toInstall: List<RepoAddonEntry>,
        val skipped: List<SkippedDep>,
    )

    data class InstallSummary(
        val root: RepoAddonEntry,
        val installed: List<KodiAddonStore.KodiAddon>,
        val skipped: List<SkippedDep>,
    ) {
        fun format(): String {
            val rootName = KodiPluginRunner.cleanKodiText(root.name.ifBlank { root.id })
            val depCount = (installed.size - 1).coerceAtLeast(0)
            val base = if (depCount > 0) {
                "$rootName v${root.version} instalado + $depCount dependênci${if (depCount == 1) "a" else "as"}"
            } else {
                "$rootName v${root.version} instalado"
            }
            if (skipped.isEmpty()) return "$base."
            val skipText = skipped.joinToString("; ") { "${it.id}, ${it.reason}" }
            val word = if (skipped.size == 1) "ignorada" else "ignoradas"
            return "$base; ${skipped.size} $word ($skipText)."
        }
    }

    data class HtmlRepoLinks(
        val repoZips: List<String>,
        val indexXmls: List<String>,
        val directZips: List<String>,
    )

    // ------------------------------------------------------------------
    // Versões (sem metadado de build: "2.25.1+matrix.1" == "2.25.1").
    // ------------------------------------------------------------------

    fun compareVersions(a: String, b: String): Int {
        val pa = a.substringBefore('+').split('.')
        val pb = b.substringBefore('+').split('.')
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val xa = pa.getOrNull(i) ?: "0"
            val xb = pb.getOrNull(i) ?: "0"
            val na = xa.toIntOrNull()
            val nb = xb.toIntOrNull()
            val cmp = if (na != null && nb != null) na.compareTo(nb) else xa.compareTo(xb)
            if (cmp != 0) return cmp
        }
        return 0
    }

    // ------------------------------------------------------------------
    // addon.xml de repositório — <dir>.
    // ------------------------------------------------------------------

    fun parseRepoDirs(addonXmlText: String): List<RepoDir> {
        val dirs = mutableListOf<RepoDir>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(addonXmlText))
            var event = parser.eventType
            var inRepo = false
            var curMin: String? = null
            var curMax: String? = null
            var curInfo = ""
            var curCompressed = false
            var curDatadir = ""
            var curTag: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "extension" -> inRepo =
                            parser.getAttributeValue(null, "point") == "xbmc.addon.repository"
                        "dir" -> if (inRepo) {
                            curMin = parser.getAttributeValue(null, "minversion")
                            curMax = parser.getAttributeValue(null, "maxversion")
                            curInfo = ""
                            curCompressed = false
                            curDatadir = ""
                        }
                        "info" -> if (inRepo) {
                            curCompressed = parser.getAttributeValue(null, "compressed")
                                .orEmpty().equals("true", ignoreCase = true)
                            curTag = "info"
                        }
                        "datadir" -> if (inRepo) curTag = "datadir"
                    }
                    XmlPullParser.TEXT -> if (inRepo) {
                        val t = parser.text.trim()
                        if (t.isNotEmpty()) when (curTag) {
                            "info" -> curInfo = t
                            "datadir" -> curDatadir = t
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "dir" -> if (inRepo && curInfo.isNotBlank() && curDatadir.isNotBlank()) {
                            dirs += RepoDir(curMin, curMax, curInfo, curCompressed, curDatadir)
                        }
                        "info", "datadir" -> curTag = null
                        "extension" -> inRepo = false
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // XML inválido — devolve o que conseguiu (vazio em geral).
        }
        return dirs
    }

    /**
     * Escolhe o `<dir>` p/ a versão do Kodi: minversion <= versão,
     * maxversion >= versão (quando presentes); maior minversion vence,
     * em empate prefere sem maxversion.
     */
    fun chooseDirForKodi(dirs: List<RepoDir>, kodiMajor: Int = KODI_MAJOR_VERSION): RepoDir? {
        val version = "$kodiMajor.0.0"
        val ok = dirs.filter { dir ->
            val minOk = dir.minVersion?.let { compareVersions(it, version) <= 0 } ?: true
            val maxOk = dir.maxVersion?.let { compareVersions(version, it) <= 0 } ?: true
            minOk && maxOk
        }
        return ok.maxWithOrNull(
            Comparator<RepoDir> { a, b ->
                val byMin = compareVersions(a.minVersion ?: "0", b.minVersion ?: "0")
                if (byMin != 0) byMin
                else (if (a.maxVersion == null) 1 else 0) - (if (b.maxVersion == null) 1 else 0)
            },
        )
    }

    // ------------------------------------------------------------------
    // Índice addons.xml.
    // ------------------------------------------------------------------

    /** Nome do elemento raiz do XML (ignora prólogo, comentários e DOCTYPE). */
    fun rootElementName(xmlText: String): String? {
        var i = 0
        val n = xmlText.length
        while (i < n) {
            val lt = xmlText.indexOf('<', i)
            if (lt < 0) return null
            when {
                xmlText.startsWith("<!--", lt) -> {
                    val end = xmlText.indexOf("-->", lt + 4)
                    if (end < 0) return null
                    i = end + 3
                }
                xmlText.startsWith("<?", lt) -> {
                    val end = xmlText.indexOf("?>", lt + 2)
                    if (end < 0) return null
                    i = end + 2
                }
                xmlText.startsWith("<!", lt) -> {
                    val end = xmlText.indexOf('>', lt + 2)
                    if (end < 0) return null
                    i = end + 1
                }
                else -> {
                    val start = lt + 1
                    if (start >= n || xmlText[start] == '/') return null
                    var end = start
                    while (end < n && (xmlText[end].isLetterOrDigit() || xmlText[end] == '_' ||
                            xmlText[end] == '-' || xmlText[end] == '.' || xmlText[end] == ':')
                    ) end++
                    return xmlText.substring(start, end).ifBlank { null }
                }
            }
        }
        return null
    }

    /** Parse do índice; null se a raiz não for `<addons>` ou o XML for inválido. */
    fun parseIndex(addonsXmlText: String, datadirBase: String, sourceUrl: String): RepoIndex? {
        if (rootElementName(addonsXmlText) != "addons") return null
        return try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(addonsXmlText))
            val addons = mutableListOf<RepoAddonEntry>()
            var event = parser.eventType
            var id = ""
            var version = ""
            var name = ""
            var requires = mutableListOf<KodiAddonStore.Dependency>()
            var points = mutableListOf<String>()
            var inRequires = false
            var inAddon = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "addon" -> {
                            inAddon = true
                            id = parser.getAttributeValue(null, "id").orEmpty()
                            version = parser.getAttributeValue(null, "version").orEmpty()
                            name = parser.getAttributeValue(null, "name").orEmpty()
                            requires = mutableListOf()
                            points = mutableListOf()
                        }
                        "requires" -> inRequires = true
                        "import" -> if (inAddon && inRequires) {
                            val dep = parser.getAttributeValue(null, "addon").orEmpty()
                            if (dep.isNotBlank()) {
                                requires += KodiAddonStore.Dependency(
                                    addon = dep,
                                    version = parser.getAttributeValue(null, "version").orEmpty(),
                                    optional = parser.getAttributeValue(null, "optional")
                                        .orEmpty().equals("true", ignoreCase = true),
                                )
                            }
                        }
                        "extension" -> if (inAddon) {
                            val point = parser.getAttributeValue(null, "point").orEmpty()
                            if (point.isNotBlank()) points += point
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "requires" -> inRequires = false
                        "addon" -> {
                            if (id.isNotBlank()) {
                                addons += RepoAddonEntry(id, version, name, requires, points)
                            }
                            inAddon = false
                        }
                    }
                }
                event = parser.next()
            }
            RepoIndex(addons, datadirBase, sourceUrl)
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Página HTML — links de repositório.
    // ------------------------------------------------------------------

    /** Extrai links de repositório de uma página, resolvendo URLs relativas. */
    fun extractRepoLinks(html: String, pageUrl: String): HtmlRepoLinks {
        val repoZips = mutableListOf<String>()
        val indexXmls = mutableListOf<String>()
        val directZips = mutableListOf<String>()
        val hrefRe = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        for (match in hrefRe.findAll(html)) {
            val href = match.groupValues[1].trim()
            if (href.isEmpty() || href.startsWith("#") ||
                href.startsWith("javascript:", ignoreCase = true) ||
                href.startsWith("mailto:", ignoreCase = true)
            ) continue
            val abs = runCatching { URL(URL(pageUrl), href).toString() }.getOrNull() ?: continue
            val file = abs.lowercase().substringAfterLast('/')
            // ZIP de repositório: o nome é "<id-do-repo>-<versão>.zip" e
            // o id termina em ".repo" (vkkodi.repo-3.33.zip) ou começa
            // com "repository." (repository.vulgata-1.0.3.zip).
            val isRepoZip = file.endsWith(".zip") &&
                (file.contains(".repo-") || file.startsWith("repository."))
            when {
                isRepoZip -> repoZips += abs
                file == "addons.xml" || file == "addons.xml.gz" -> indexXmls += abs
                file.endsWith(".zip") -> directZips += abs
            }
        }
        return HtmlRepoLinks(repoZips.distinct(), indexXmls.distinct(), directZips.distinct())
    }

    /** Diretório da URL (p/ base do datadir quando o índice veio de addons.xml). */
    fun dirOfUrl(url: String): String {
        return runCatching {
            val u = URL(url)
            val path = u.path.trimEnd('/')
            val dir = path.substringBeforeLast('/', "")
            val port = if (u.port != -1) ":${u.port}" else ""
            "${u.protocol}://${u.host}$port$dir"
        }.getOrDefault(url.substringBeforeLast('/'))
    }

    // ------------------------------------------------------------------
    // Plano de instalação (puro — sem rede nem disco).
    // ------------------------------------------------------------------

    /**
     * Monta o plano p/ instalar [rootId] a partir do índice.
     *
     * Ordem pós-fixada (dependências antes do addon). Pula dependência
     * já instalada (mesma versão ou mais nova), embutida no Kodi
     * ([BUILTIN_DEPS]), provida pelo app ([PIP_MODULES]) e binária
     * (`inputstream.*` — o AOTV não executa .so de inputstream).
     * Dependência opcional ausente no índice é pulada; obrigatória
     * ausente é erro. Ciclo é pulado. Profundidade maior que
     * [MAX_DEP_DEPTH] é erro.
     */
    fun planInstall(
        index: RepoIndex,
        rootId: String,
        installedVersion: (String) -> String?,
    ): InstallPlan {
        val root = index.byId[rootId]
            ?: error("Addon $rootId não encontrado no índice.")
        val toInstall = mutableListOf<RepoAddonEntry>()
        val skipped = mutableListOf<SkippedDep>()
        val done = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(id: String, minVersion: String, depth: Int, optional: Boolean) {
            if (depth > MAX_DEP_DEPTH) {
                error("Dependências muito profundas (mais de $MAX_DEP_DEPTH níveis) a partir de $id.")
            }
            if (id in visiting) {
                skipped += SkippedDep(id, SkippedDep.CYCLE)
                return
            }
            if (id in done) return
            if (id in BUILTIN_DEPS) {
                skipped += SkippedDep(id, SkippedDep.BUILTIN)
                done += id
                return
            }
            if (id.startsWith("inputstream.")) {
                skipped += SkippedDep(id, SkippedDep.BINARY)
                done += id
                return
            }
            if (id in PIP_MODULES) {
                skipped += SkippedDep(id, SkippedDep.PROVIDED)
                done += id
                return
            }
            val entry = index.byId[id]
            if (entry == null) {
                if (optional) {
                    skipped += SkippedDep(id, SkippedDep.OPTIONAL_MISSING)
                    done += id
                    return
                }
                error("Dependência $id não encontrada no índice.")
            }
            if (minVersion.isNotBlank() && compareVersions(entry.version, minVersion) < 0) {
                error("A versão de $id no índice (${entry.version}) é mais antiga que a exigida ($minVersion).")
            }
            val installed = installedVersion(id)
            if (installed != null && compareVersions(installed, entry.version) >= 0) {
                skipped += SkippedDep(id, SkippedDep.INSTALLED)
                done += id
                return
            }
            visiting += id
            for (dep in entry.requires) {
                visit(dep.addon, dep.version, depth + 1, dep.optional)
            }
            visiting -= id
            done += id
            toInstall += entry
        }

        visit(rootId, "", 0, false)
        return InstallPlan(toInstall, skipped)
    }
}
