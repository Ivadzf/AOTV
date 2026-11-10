package com.aotv.app.data.live

/** De onde vêm os canais. */
enum class LiveSourceType { M3U, XTREAM }

/**
 * Fonte de TV ao vivo. Lista M3U usa [url]. Conta Xtream usa [server], [user] e [pass].
 * [epgUrl] é o guia XMLTV extra (opcional, a lista e o Xtream já trazem o deles).
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
)

/** Programa do guia. Horários em epoch ms. */
data class LiveProgram(val start: Long, val stop: Long, val title: String, val desc: String = "") {
    fun isNow(now: Long) = now in start until stop
    fun progress(now: Long): Float = if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** O que a lista M3U trouxe. [epgUrls] vem do cabeçalho (url-tvg). */
data class M3uResult(val channels: List<LiveChannel>, val epgUrls: List<String>)

/** Conta Xtream. [expiresAt] em epoch ms, 0 sem data (conta vitalícia ou não informada). */
data class XtreamAccount(val status: String, val expiresAt: Long, val maxConnections: Int, val formats: List<String>)
