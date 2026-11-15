package com.aotv.app.data.live

/** De onde vêm os canais. */
enum class LiveSourceType { M3U, XTREAM }

/**
 * Fonte de TV ao vivo. Lista M3U usa [url]. Conta Xtream usa [server], [user] e [pass].
 * [epgUrl] é o guia XMLTV extra (opcional, a lista e o Xtream já trazem o deles).
 * [isFileUri] distingue uma lista importada por arquivo (URI SAF `content://`) de
 * uma lista por endereço http — o endereço não pode ser buscado por HTTP.
 */
data class LiveSource(
    val id: String,
    val name: String,
    val type: LiveSourceType,
    val url: String = "",
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val epgUrl: String = "",
    val enabled: Boolean = true,
    val isFileUri: Boolean = false,
)

/** Um canal. [id] é estável entre atualizações (fonte + id do canal na fonte). */
data class LiveChannel(
    val id: String,
    val sourceId: String,
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val epgId: String = "",
    val number: Int = 0,
    /** Cabeçalhos que a lista pede (User-Agent, Referer). */
    val headers: Map<String, String> = emptyMap(),
    /** Dias de catch-up que a lista declara (`catchup-days` do #EXTINF). 0 = sem catch-up. */
    val catchupDays: Int = 0,
    /** `catchup-source` do #EXTINF (`default`, `append`, `timeshift` ou vazio). */
    val catchupSource: String = "",
    /** `catchup-template` do #EXTINF (com `{utc}`, `{utcend}`, `{Y}`, `{m}`, `{d}`, `{hh}`, `{duration}`). */
    val catchupTemplate: String = "",
)

/** Programa do guia. Horários em epoch ms. [image] é o pôster do programa quando o XMLTV traz `<icon src>` — vazio sem imagem (nunca inventada). */
data class LiveProgram(val start: Long, val stop: Long, val title: String, val desc: String = "", val image: String = "") {
    fun isNow(now: Long) = now in start until stop
    fun progress(now: Long): Float = if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** O que a lista M3U trouxe. [epgUrls] vem do cabeçalho (url-tvg). */
data class M3uResult(val channels: List<LiveChannel>, val epgUrls: List<String>)

/** Conta Xtream. [expiresAt] em epoch ms, 0 sem data (conta vitalícia ou não informada). */
data class XtreamAccount(val status: String, val expiresAt: Long, val maxConnections: Int, val formats: List<String>)

/** Categoria do On Demand Xtream (get_vod_categories / get_series_categories). */
data class VodCategory(val id: String, val name: String)

/**
 * Item do On Demand Xtream (filme de get_vod_streams ou série de get_series).
 * [id] é o stream_id da conta. Só o que a API devolver — sem dado, sem seção.
 */
data class VodItem(
    val id: String,
    val sourceId: String,
    val title: String,
    val cover: String = "",
    val plot: String = "",
    val categoryId: String = "",
    val category: String = "",
    val containerExtension: String = "",
    val year: String = "",
    val rating: String = "",
    val isSeries: Boolean = false,
)

/** Episódio de uma série do Xtream (get_series_info). [id] é o id do episódio. */
data class VodEpisode(
    val id: String,
    val title: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val containerExtension: String = "",
    val plot: String = "",
    val cover: String = "",
    val duration: String = "",
)

/** Detalhe de uma série: a série + os episódios por temporada, na ordem da API. */
data class VodSeriesDetail(
    val series: VodItem,
    val episodes: List<VodEpisode> = emptyList(),
)
