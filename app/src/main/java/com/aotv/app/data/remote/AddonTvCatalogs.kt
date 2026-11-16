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
 * Catálogos de TV ao vivo dos addons ligados (Lote 15, Bloco 2, v1.25.0;
 * Lote 16, v1.26.0: type "tv" ou "channel").
 * [addonId]/[addonName] vêm do manifest; [base] é a base do addon (sem
 * /manifest.json), para pedir as páginas do catálogo e o stream. [type] é o
 * type declarado no manifest ("tv" ou "channel") e decide as URLs
 * (/catalog/{type}/{id}.json e /stream/{type}/{id}.json) — "tv" continua
 * funcionando exatamente como antes.
 */
data class AddonTvCatalog(
    val addonId: String,
    val addonName: String,
    val base: String,
    val id: String,
    val name: String,
    val type: String = "tv",
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
    /** Id estável e autocontido (base + catálogo + meta + type), p/ resolver o stream no toque. */
    val channelId: String get() = AddonTvCatalogs.channelId(catalog.base, catalog.id, metaId, catalog.type)

    /**
     * Vira [LiveChannel] p/ entrar na lista da aba Ao vivo. Sem EPG e sem
     * URL: o stream só é resolvido no toque ({base}/stream/{type}/{metaId}.json),
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
    /**
     * Addons que RESPONDERAM mas não renderam catálogo de TV (Lote 16,
     * v1.26.0): o que cada um DECLARA (ex.: ["movie", "series", "channel"]).
     * Vazio = todo addon que respondeu trouxe catálogo de TV, ou nenhum
     * respondeu (aí vale o erro "Não deu para ler os addons...").
     */
    val diagnostics: List<AddonTvDiag> = emptyList(),
)

/**
 * Um addon ligado que respondeu mas não tem catálogo de TV: [declaredTypes]
 * é o que os catálogos dele declaram no manifest. P/ o diagnóstico do app
 * ("BestCine respondeu, mas só declara Filmes e Séries") em vez do silêncio.
 */
data class AddonTvDiag(
    val addonName: String,
    val addonBase: String,
    val declaredTypes: List<String>,
)

/** Id de canal de addon desmontado: base do addon + catálogo + id da meta + type. */
data class AddonTvRef(val base: String, val catalogId: String, val metaId: String, val type: String = "tv")

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
     * O [type] vai como 4ª parte (Lote 16, v1.26.0): sem ele o resolve do
     * stream não saberia se pede /stream/tv/... ou /stream/channel/....
     * "tv" continua o padrão — id antigo de 3 partes ainda desmonta.
     */
    fun channelId(base: String, catalogId: String, metaId: String, type: String = "tv"): String =
        PREFIX + AddonLinks.encodeURIComponent(base) + ":" +
            AddonLinks.encodeURIComponent(catalogId) + ":" +
            AddonLinks.encodeURIComponent(metaId) + ":" +
            AddonLinks.encodeURIComponent(type.ifBlank { "tv" })

    /**
     * Desmonta o id, ou null se não for um id de canal de addon. O decode usa
     * URLDecoder, que é seguro aqui porque o encodeURIComponent escapa "+"
     * (vira %2B) — nenhum "+" literal sobrevive p/ virar espaço no decode.
     * Aceita o formato antigo de 3 partes (type = "tv") e o novo de 4.
     */
    fun parseChannelId(id: String): AddonTvRef? {
        if (!isAddonTvChannelId(id)) return null
        val parts = id.removePrefix(PREFIX).split(":")
        if (parts.size != 3 && parts.size != 4) return null
        val decoded = parts.map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: return null }
        if (decoded.any { it.isBlank() }) return null
        val type = if (parts.size == 4) decoded[3].ifBlank { "tv" } else "tv"
        if (type != "tv" && type != "channel") return null
        return AddonTvRef(decoded[0], decoded[1], decoded[2], type)
    }
}

/**
 * Loader dos catálogos de TV dos addons ligados (Lote 15, Bloco 2, v1.25.0;
 * Lote 16, v1.26.0: type "tv" ou "channel").
 * Lê o manifest de cada addon ligado ([AddonLinks.tvCatalogs]), pede a 1ª
 * página de cada catálogo de TV ([StremioAddonService.catalogMetas] com o
 * type do catálogo) e resolve o stream do canal no toque
 * (/stream/{type}/{id}.json).
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
        // Diagnóstico (Lote 16, v1.26.0): addon que respondeu mas não rendeu
        // catálogo de TV — guarda o que ele DECLARA, em vez do silêncio.
        val diagnostics = perAddon.filterNotNull().filter { it.catalogs.isEmpty() }.map { r ->
            AddonTvDiag(r.addonName, r.base, r.declaredTypes)
        }
        AddonTvCatalogsResult(
            catalogs = perAddon.filterNotNull().flatMap { it.catalogs },
            anyResponded = perAddon.any { it != null },
            diagnostics = diagnostics,
        )
    }

    /** Uma página de canais do catálogo (null = o addon não respondeu). */
    suspend fun loadChannels(catalog: AddonTvCatalog, skip: Int = 0): List<AddonMeta>? =
        runCatching { stremio.catalogMetas(catalog.base, catalog.type, catalog.id, skip) }.getOrNull()

    /**
     * Primeira URL http do stream do canal ({base}/stream/{type}/{metaId}.json),
     * ou null quando o addon não trouxe stream direto. Magnet/torrent não
     * conta: canal ao vivo toca por http (.m3u8) no player. [type] é o type
     * do catálogo ("tv" ou "channel"); o padrão "tv" mantém o comportamento
     * antigo de quem chama sem ele.
     */
    suspend fun resolveStreamUrl(base: String, metaId: String, type: String = "tv"): String? {
        if (base.isBlank() || metaId.isBlank()) return null
        val streams = runCatching { stremio.getStreams(base, type.ifBlank { "tv" }, metaId) }.getOrDefault(emptyList())
        return streams.firstOrNull { it.url.startsWith("http", true) }?.url
    }

    /** O que a leitura do manifest de um addon rendeu (null = não respondeu ou não é manifest). */
    private data class CatalogsOfResult(
        val catalogs: List<AddonTvCatalog>,
        val addonName: String,
        val base: String,
        val declaredTypes: List<String>,
    )

    /** Catálogos de TV do manifest de um addon (null = não respondeu ou não é manifest). */
    private suspend fun catalogsOf(manifestUrl: String): CatalogsOfResult? = withContext(Dispatchers.IO) {
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
            val base = AddonLinks.baseOf(url)
            CatalogsOfResult(
                catalogs = AddonLinks.tvCatalogs(json).map { c ->
                    AddonTvCatalog(addonId, addonName, base, c.id, c.name, c.type)
                },
                addonName = addonName,
                base = base,
                declaredTypes = AddonLinks.declaredCatalogTypes(json),
            )
        }.getOrNull()
    }
}
