package com.aotv.app.data.remote

import com.aotv.app.data.live.LiveChannel
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catálogos de TV ao vivo dos addons ligados (Lote 15, Bloco 2, v1.25.0).
 * [addonId]/[addonName] vêm do manifest; [base] é a base do addon (sem
 * /manifest.json), para pedir as páginas do catálogo e o stream.
 */
data class AddonTvCatalog(
    val addonId: String,
    val addonName: String,
    val base: String,
    val id: String,
    val name: String,
) {
    /** Rótulo do catálogo: "Bsweb TV — Canais Abertos". */
    val label: String get() = "$addonName — $name"
    /** Grupo do canal na aba Ao vivo: cada catálogo vira um grupo. */
    val group: String get() = "$addonName · $name"
}

/** Um canal de um catálogo de TV de addon (meta do /catalog/tv/{id}.json). */
data class AddonTvChannel(
    val catalog: AddonTvCatalog,
    val metaId: String,
    val name: String,
    val poster: String,
) {
    /** Id estável e autocontido (base + catálogo + meta), p/ resolver o stream no toque. */
    val channelId: String get() = AddonTvCatalogs.channelId(catalog.base, catalog.id, metaId)

    /**
     * Vira [LiveChannel] p/ entrar na lista da aba Ao vivo. Sem EPG e sem
     * URL: o stream só é resolvido no toque ({base}/stream/tv/{metaId}.json),
     * porque a URL do stream costuma trazer token de validade curta e iria
     * expirar se fosse resolvida na carga da lista.
     */
    fun toLiveChannel(): LiveChannel = LiveChannel(
        id = channelId,
        sourceId = "addon-tv:${catalog.addonId}",
        name = name,
        url = "",
        logo = poster,
        group = catalog.group,
    )
}

/** Resultado da leitura dos manifests: catálogos de TV + se algum addon respondeu. */
data class AddonTvCatalogsResult(
    val catalogs: List<AddonTvCatalog> = emptyList(),
    val anyResponded: Boolean = false,
)

/** Id de canal de addon desmontado: base do addon + catálogo + id da meta. */
data class AddonTvRef(val base: String, val catalogId: String, val metaId: String)

/**
 * Id de canal de addon de TV ("addon-tv:...") — o formato que a aba Ao vivo
 * usa p/ reconhecer esses canais e resolver o stream deles no toque.
 */
object AddonTvCatalogs {
    const val PREFIX = "addon-tv:"

    fun isAddonTvChannelId(id: String): Boolean = id.startsWith(PREFIX)

    /**
     * Monta o id: cada parte vai com encodeURIComponent, então ":" separa sem
     * ambiguidade (a base tem "https://", a meta pode ter ":" — tudo vira %XX).
     */
    fun channelId(base: String, catalogId: String, metaId: String): String =
        PREFIX + AddonLinks.encodeURIComponent(base) + ":" +
            AddonLinks.encodeURIComponent(catalogId) + ":" +
            AddonLinks.encodeURIComponent(metaId)

    /**
     * Desmonta o id, ou null se não for um id de canal de addon. O decode usa
     * URLDecoder, que é seguro aqui porque o encodeURIComponent escapa "+"
     * (vira %2B) — nenhum "+" literal sobrevive p/ virar espaço no decode.
     */
    fun parseChannelId(id: String): AddonTvRef? {
        if (!isAddonTvChannelId(id)) return null
        val parts = id.removePrefix(PREFIX).split(":")
        if (parts.size != 3) return null
        val decoded = parts.map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: return null }
        if (decoded.any { it.isBlank() }) return null
        return AddonTvRef(decoded[0], decoded[1], decoded[2])
    }
}

/**
 * Loader dos catálogos de TV dos addons ligados (Lote 15, Bloco 2, v1.25.0).
 * Lê o manifest de cada addon ligado ([AddonLinks.tvCatalogs]), pede a 1ª
 * página de cada catálogo de TV ([StremioAddonService.catalogMetas] com tipo
 * "tv") e resolve o stream do canal no toque (/stream/tv/{id}.json).
 * Um addon morto/lento não segura os outros (paralelo + timeout curto, sem
 * repetição). Canais de addon NÃO entram no guia/EPG — só o stream.
 */
@Singleton
class AddonTvCatalogLoader @Inject constructor(
    private val stremio: StremioAddonService,
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Catálogos de TV de todos os addons ligados, em paralelo. */
    suspend fun loadCatalogs(): AddonTvCatalogsResult = coroutineScope {
        val enabled = prefs.addons.value.filter { it.enabled }
        if (enabled.isEmpty()) return@coroutineScope AddonTvCatalogsResult()
        val perAddon = enabled.map { e ->
            async { runCatching { catalogsOf(e.url) }.getOrNull() }
        }.awaitAll()
        AddonTvCatalogsResult(
            catalogs = perAddon.filterNotNull().flatten(),
            anyResponded = perAddon.any { it != null },
        )
    }

    /** Uma página de canais do catálogo (null = o addon não respondeu). */
    suspend fun loadChannels(catalog: AddonTvCatalog, skip: Int = 0): List<AddonMeta>? =
        runCatching { stremio.catalogMetas(catalog.base, "tv", catalog.id, skip) }.getOrNull()

    /**
     * Primeira URL http do stream do canal ({base}/stream/tv/{metaId}.json),
     * ou null quando o addon não trouxe stream direto. Magnet/torrent não
     * conta: canal ao vivo toca por http (.m3u8) no player.
     */
    suspend fun resolveStreamUrl(base: String, metaId: String): String? {
        if (base.isBlank() || metaId.isBlank()) return null
        val streams = runCatching { stremio.getStreams(base, "tv", metaId) }.getOrDefault(emptyList())
        return streams.firstOrNull { it.url.startsWith("http", true) }?.url
    }

    /** Catálogos de TV do manifest de um addon (null = não respondeu ou não é manifest). */
    private suspend fun catalogsOf(manifestUrl: String): List<AddonTvCatalog>? = withContext(Dispatchers.IO) {
        val url = AddonLinks.normalizeManifestUrl(manifestUrl)
        if (url.isBlank()) return@withContext null
        val body = runCatching {
            client.newCall(
                Request.Builder().url(AddonLinks.encodePath(url))
                    // UA de BROWSER, igual ao StremioAddonService: tem host com
                    // WAF que bloqueia UA de biblioteca (FrostStream).
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
            ).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        runCatching {
            val json = JsonParser.parseString(body).asJsonObject
            val addonId = json.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (addonId.isBlank()) return@runCatching null
            val addonName = json.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty().ifBlank { addonId }
            AddonLinks.tvCatalogs(json).map { c ->
                AddonTvCatalog(addonId, addonName, AddonLinks.baseOf(url), c.id, c.name)
            }
        }.getOrNull()
    }
}
