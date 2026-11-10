package com.aotv.app.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Catálogo de um addon que pode virar fileira na Início (só filme e série sem filtro obrigatório). */
data class AddonCatalog(val type: String, val id: String, val name: String)

/** Catálogo que pode ser fonte de coleção. [genres] vem do extra "genre" do manifest. */
data class AddonCatalogOption(
    val addonId: String,
    val addonName: String,
    val type: String,
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
    val genreRequired: Boolean = false,
)

/** Campo do formulário de configuração que o manifest declara em `config`. */
data class AddonConfigField(
    val key: String,
    val type: String,
    val title: String,
    val default: String?,
    val options: List<String>,
    val required: Boolean,
)

/** Addon do catálogo da comunidade (stremio-addons.net). */
data class CommunityAddon(
    val rank: Int,
    val url: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val logo: String,
    val types: List<String>,
    val resources: List<String>,
    val config: List<AddonConfigField>,
    val configurable: Boolean,
    val configRequired: Boolean,
    val p2p: Boolean,
    val adult: Boolean,
)

/**
 * Regras puras de link de addon. Sem Android e sem rede, por isso tem teste em app/src/test.
 * Espelha as funções do webOS em webos/src/js/sources.js.
 */
object AddonLinks {

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /**
     * Normaliza o que o usuário colar, igual o Stremio aceita.
     * stremio://host/... vira https://host/..., sem /manifest.json no fim ganha o sufixo,
     * espaços e barra final saem. Retorna "" se não der para montar uma URL http(s).
     */
    fun normalizeManifestUrl(input: String): String {
        var u = input.trim()
        if (u.isBlank()) return ""
        if (u.startsWith("stremio://", true)) u = "https://" + u.substring("stremio://".length)
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        u = u.trimEnd('/')
        if (!u.endsWith("/manifest.json", true)) u = "$u/manifest.json"
        return encodePath(u)
    }

    /**
     * Percent-encoda o que o OkHttp recusa no path. Addon com configuração embutida na URL
     * (BestCine) traz espaço e acento em "Canais | 24h", e sem isso o HttpUrl nem constrói.
     * Só mexe no path, o host fica intacto, e não re-encoda o que já veio como %XX.
     */
    fun encodePath(url: String): String {
        val cut = url.indexOf("://").let { if (it < 0) return url else it + 3 }
        val hostEnd = url.indexOf('/', cut).let { if (it < 0) return url else it }
        val head = url.substring(0, hostEnd)
        val path = url.substring(hostEnd)
        val sb = StringBuilder(path.length + 32)
        var i = 0
        while (i < path.length) {
            val c = path[i]
            val alreadyEscaped = c == '%' && i + 2 < path.length &&
                path[i + 1].isHex() && path[i + 2].isHex()
            when {
                alreadyEscaped -> { sb.append(path, i, i + 3); i += 2 }
                c.code in 33..126 && c != '%' -> sb.append(c)
                else -> c.toString().toByteArray(Charsets.UTF_8)
                    .forEach { b -> sb.append('%').append("%02X".format(b.toInt() and 0xFF)) }
            }
            i++
        }
        return head + sb
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun baseOf(url: String) = url.trim().removeSuffix("/manifest.json").trimEnd('/')

    fun hostOf(url: String): String =
        Regex("^https?://([^/]+)", RegexOption.IGNORE_CASE).find(url.trim())?.groupValues?.get(1)
            ?: url.take(40)

    /**
     * URL do addon configurado no padrão do stremio-addon-sdk. A página de configuração dele
     * monta {base}/{JSON da config codificado}/manifest.json. Igual ao configuredUrl do webOS.
     */
    fun configuredUrl(manifestUrl: String, values: Map<String, Any>): String =
        baseOf(manifestUrl) + "/" + encodeURIComponent(gson.toJson(values)) + "/manifest.json"

    /** Mesmo resultado do encodeURIComponent do JavaScript. */
    fun encodeURIComponent(s: String): String {
        val keep = "-_.!~*'()"
        val sb = StringBuilder()
        s.toByteArray(Charsets.UTF_8).forEach { b ->
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in keep) sb.append(c)
            else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /** Catálogos do manifest que abrem sem filtro obrigatório, só de filme e série. */
    fun homeCatalogs(manifest: JsonObject): List<AddonCatalog> {
        val arr = manifest.arr("catalogs") ?: return emptyList()
        return arr.mapNotNull { el ->
            val c = el.obj() ?: return@mapNotNull null
            val type = c.str("type")
            val id = c.str("id")
            if (id.isBlank() || (type != "movie" && type != "series")) return@mapNotNull null
            if ((c.arr("extraRequired")?.size() ?: 0) > 0) return@mapNotNull null
            val extraReq = c.arr("extra")?.any { e -> e.obj()?.get("isRequired")?.let { it.isJsonPrimitive && it.asBoolean } == true } == true
            if (extraReq) return@mapNotNull null
            AddonCatalog(type, id, c.str("name").ifBlank { id })
        }
    }

    /**
     * Catálogos de filme e série que podem ser fonte de coleção. Diferente de [homeCatalogs],
     * aceita catálogo que exige gênero (a pasta guarda o gênero escolhido). Catálogo que exige
     * outro filtro, como busca, fica de fora.
     */
    fun collectionCatalogs(manifest: JsonObject): List<AddonCatalogOption> {
        val addonId = manifest.str("id")
        if (addonId.isBlank()) return emptyList()
        val addonName = manifest.str("name").ifBlank { addonId }
        val arr = manifest.arr("catalogs") ?: return emptyList()
        return arr.mapNotNull { el ->
            val c = el.obj() ?: return@mapNotNull null
            val type = c.str("type")
            val id = c.str("id")
            if (id.isBlank() || (type != "movie" && type != "series")) return@mapNotNull null
            val extras = c.arr("extra")?.mapNotNull { it.obj() }.orEmpty()
            val required = extras.filter { e -> e.get("isRequired")?.let { it.isJsonPrimitive && it.asBoolean } == true }.map { it.str("name") } +
                c.arr("extraRequired")?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }.orEmpty()
            if (required.any { it != "genre" }) return@mapNotNull null
            val genres = extras.firstOrNull { it.str("name") == "genre" }?.arr("options")
                ?.mapNotNull { o -> o.takeIf { it.isJsonPrimitive }?.asString?.takeIf { s -> s.isNotBlank() } }.orEmpty()
            AddonCatalogOption(addonId, addonName, type, id, c.str("name").ifBlank { id }, genres, "genre" in required)
        }
    }

    private val linkRegex = Regex("""(?i)(stremio|https?)://[^\s"'<>`]+?/manifest\.json""")

    /**
     * Valida um texto capturado da página de configuração (cópia, navegação ou varredura).
     * Só aceita URL http(s) ou stremio:// que aponta para manifest.json, com host de verdade.
     * Devolve a URL https normalizada, ou null se o texto não traz link de addon.
     */
    fun capturedLink(text: String?): String? {
        if (text.isNullOrBlank() || text.length > 8000) return null
        val raw = linkRegex.find(text)?.value ?: return null
        val host = Regex("^(?i)(?:stremio|https?)://([^/?#]+)").find(raw)?.groupValues?.get(1) ?: return null
        // Host só com letras, números, ponto, hífen e porta. Barra invertida, %, @ e afins ficam de fora,
        // porque parsers diferentes leem esses casos de jeitos diferentes.
        if (!Regex("""^[A-Za-z0-9.-]+(:\d{1,5})?$""").matches(host)) return null
        if (!host.contains('.') && !host.startsWith("localhost")) return null
        return normalizeManifestUrl(raw).takeIf { it.isNotBlank() }
    }

    /**
     * Site de um link, sem subdomínio (torrentio.strem.fun e strem.fun dão strem.fun). Usa o mesmo
     * parser do OkHttp que baixa o manifest e a lista pública de sufixos, para não divergir dele.
     * Serve para avisar quando o link capturado vem de outro site que não o do addon.
     */
    fun siteOf(url: String): String {
        var u = url.trim()
        if (u.startsWith("stremio://", true)) u = "https://" + u.substring("stremio://".length)
        val http = u.toHttpUrlOrNull() ?: return ""
        return http.topPrivateDomain() ?: http.host
    }

    /** Esquemas que a página de configuração pode abrir. O resto é bloqueado. */
    fun allowedScheme(url: String): Boolean {
        val s = url.substringBefore(':').lowercase()
        return s == "http" || s == "https" || s == "stremio"
    }

    /** Lê a resposta do catálogo da comunidade. Ignora addon sem manifest ou sem URL http(s). */
    fun parseCommunity(json: String): List<CommunityAddon> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return emptyList()
        val list = root.arr("addons") ?: return emptyList()
        return list.mapIndexedNotNull { i, el ->
            val a = el.obj() ?: return@mapIndexedNotNull null
            val m = a.obj("manifest") ?: return@mapIndexedNotNull null
            val url = a.str("transportUrl")
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapIndexedNotNull null
            val hints = m.obj("behaviorHints")
            CommunityAddon(
                rank = i,
                url = url,
                id = m.str("id"),
                name = m.str("name").ifBlank { hostOf(url) },
                version = m.str("version"),
                description = m.str("description"),
                logo = m.str("logo").takeIf { it.startsWith("http", true) }.orEmpty(),
                types = m.strings("types"),
                resources = resourcesOf(m),
                config = m.arr("config")?.mapNotNull { configField(it) }.orEmpty(),
                configurable = hints.bool("configurable") || hints.bool("configurationRequired"),
                configRequired = hints.bool("configurationRequired"),
                p2p = hints.bool("p2p"),
                adult = hints.bool("adult"),
            )
        }
    }

    /** "resources" aceita string ("stream") ou objeto ({name:"stream", types:[...]}). */
    fun resourcesOf(m: JsonObject): List<String> = m.arr("resources")?.mapNotNull { r ->
        when {
            r.isJsonPrimitive -> r.asString
            r.isJsonObject -> r.asJsonObject.str("name")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }.orEmpty()

    private fun configField(el: JsonElement): AddonConfigField? {
        val c = el.obj() ?: return null
        val key = c.str("key").ifBlank { return null }
        val def = c.get("default")?.takeIf { it.isJsonPrimitive }?.asString
        return AddonConfigField(
            key = key,
            type = c.str("type").ifBlank { "text" },
            title = c.str("title").ifBlank { key },
            default = def,
            options = c.strings("options"),
            required = c.bool("required"),
        )
    }

    // ── atalhos de leitura tolerante (manifest de comunidade vem de tudo que é jeito) ──
    private fun JsonElement.obj(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.obj(k: String): JsonObject? = get(k)?.obj()
    private fun JsonObject.arr(k: String): JsonArray? = get(k)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.str(k: String): String =
        get(k)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
    private fun JsonObject.strings(k: String): List<String> =
        arr(k)?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }.orEmpty()
    private fun JsonObject?.bool(k: String): Boolean =
        this?.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrDefault(false) } ?: false
}
