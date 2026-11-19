package com.aotv.app.data.remote

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.dto.TmdbProviderDto
import com.google.gson.annotations.SerializedName
import javax.inject.Inject
import javax.inject.Singleton

// ─── Lote 25 (v1.35.0) — Frente D, item 10: onde assistir ───────────────────
// Provedores de streaming de um título e por provedor, via TMDB (a chave do
// app já existe — reuse; nenhuma chave nova). Região BR.

/** Provedores de um título por região (`/movie|tv/{id}/watch/providers`). */
data class TmdbTitleProvidersDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("results") val results: Map<String, TmdbRegionProvidersDto> = emptyMap(),
)

data class TmdbRegionProvidersDto(
    @SerializedName("link") val link: String? = null,
    @SerializedName("flatrate") val flatrate: List<TmdbProviderEntryDto> = emptyList(),
    @SerializedName("rent") val rent: List<TmdbProviderEntryDto> = emptyList(),
    @SerializedName("buy") val buy: List<TmdbProviderEntryDto> = emptyList(),
    @SerializedName("ads") val ads: List<TmdbProviderEntryDto> = emptyList(),
    @SerializedName("free") val free: List<TmdbProviderEntryDto> = emptyList(),
)

data class TmdbProviderEntryDto(
    @SerializedName("provider_id") val id: Int = 0,
    @SerializedName("provider_name") val name: String = "",
    @SerializedName("logo_path") val logoPath: String? = null,
    @SerializedName("display_priority") val priority: Int = 999,
)

/** Um provedor com os tipos de acesso que ele oferece p/ o título. */
data class WatchProviderEntry(
    val id: Int,
    val name: String,
    val logoPath: String?,
    /** "flatrate" (assinatura), "rent", "buy", "ads" ou "free". */
    val types: List<String>,
)

/** Onde assistir no BR: listas por tipo + link do JustWatch. */
data class WatchProvidersResult(
    val flatrate: List<WatchProviderEntry>,
    val rent: List<WatchProviderEntry>,
    val buy: List<WatchProviderEntry>,
    val link: String,
) {
    val isEmpty get() = flatrate.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

/**
 * Achata as listas do TMDB p/ uma entrada por provedor (o mesmo provedor
 * pode estar em flatrate e em buy). Sem região no resultado, vazio honesto.
 */
fun parseTitleProviders(dto: TmdbTitleProvidersDto, region: String = "BR"): WatchProvidersResult {
    val r = dto.results[region] ?: return WatchProvidersResult(emptyList(), emptyList(), emptyList(), "")
    val byId = linkedMapOf<Int, MutableList<String>>()
    val meta = mutableMapOf<Int, TmdbProviderEntryDto>()
    fun add(list: List<TmdbProviderEntryDto>, type: String) {
        for (e in list) {
            if (e.id <= 0 || e.name.isBlank()) continue
            byId.getOrPut(e.id) { mutableListOf() }.add(type)
            meta[e.id] = e
        }
    }
    add(r.flatrate, "flatrate")
    add(r.rent, "rent")
    add(r.buy, "buy")
    add(r.ads, "ads")
    add(r.free, "free")
    fun entries(type: String) = byId.entries
        .filter { type in it.value }
        .sortedBy { meta[it.key]?.priority ?: 999 }
        .map { WatchProviderEntry(it.key, meta[it.key]?.name.orEmpty(), meta[it.key]?.logoPath, it.value) }
    return WatchProvidersResult(entries("flatrate"), entries("rent"), entries("buy"), r.link.orEmpty())
}

/** Logo do provedor (TMDB images, w92 — o logo é quadrado e pequeno). */
fun watchProviderLogoUrl(logoPath: String?): String =
    if (logoPath.isNullOrBlank()) "" else "https://image.tmdb.org/t/p/w92$logoPath"

/** Rótulo PT-BR do tipo de acesso. */
fun watchProviderTypeLabel(type: String): String = when (type) {
    "flatrate" -> "Assinatura"
    "rent" -> "Aluguel"
    "buy" -> "Compra"
    "ads" -> "Grátis com anúncios"
    "free" -> "Grátis"
    else -> type
}

/** Um streaming p/ a tela Streaming. [id] é o provider_id do TMDB. */
data class StreamingProvider(val id: Int, val name: String)

/**
 * Streamings da tela Streaming (ids do TMDB, os mesmos do endpoint
 * `/watch/providers/movie` — Netflix 8, Disney+ 337, Prime Video 119,
 * Max 189, Globoplay 307, Apple TV+ 350, Paramount+ 531, Crunchyroll 283,
 * MUBI 11). A lista é fixa p/ não depender de rede p/ abrir a tela; o
 * discover usa o id daqui.
 */
val STREAMING_PROVIDERS = listOf(
    StreamingProvider(8, "Netflix"),
    StreamingProvider(337, "Disney+"),
    StreamingProvider(119, "Prime Video"),
    StreamingProvider(189, "Max"),
    StreamingProvider(307, "Globoplay"),
    StreamingProvider(350, "Apple TV+"),
    StreamingProvider(531, "Paramount+"),
    StreamingProvider(283, "Crunchyroll"),
    StreamingProvider(11, "MUBI"),
)

@Singleton
class WatchProvidersRepository @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) {
    /** Provedores do título no BR. null = sem chave TMDB ou sem resposta (a seção some). */
    suspend fun forTitle(tmdbId: Int, isMovie: Boolean): WatchProvidersResult? {
        val key = prefs.tmdbApiKey
        if (key.isBlank() || tmdbId <= 0) return null
        return runCatching {
            val dto = if (isMovie) tmdb.getMovieWatchProviders(tmdbId, key)
            else tmdb.getTvWatchProviders(tmdbId, key)
            parseTitleProviders(dto)
        }.getOrNull()
    }

    /** Todos os provedores de uma região (p/ validar os ids fixos). Vazio sem chave ou sem rede. */
    suspend fun providersInRegion(type: String = "movie", region: String = "BR"): List<TmdbProviderDto> {
        val key = prefs.tmdbApiKey
        if (key.isBlank()) return emptyList()
        return runCatching { tmdb.getWatchProviders(type, region, apiKey = key).results }.getOrDefault(emptyList())
    }
}
