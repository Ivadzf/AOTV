package com.aotv.app.data.torrent

import java.net.URLDecoder
import java.net.URLEncoder

/** Torrent de addon guardado como magnet com as dicas de arquivo, pra URL ficar estável no histórico. */
data class TorrentSpec(
    val infoHash: String,
    val trackers: List<String>,
    val fileIdx: Int? = null,
    val fileName: String? = null,
    val mustInclude: String? = null,
    val webSeeds: List<String> = emptyList(),
)

data class TorrentFile(val index: Int, val path: String, val size: Long)

object TorrentMagnet {
    private val HEX40 = Regex("^[0-9a-fA-F]{40}$")
    private val SEEDERS = Regex("👤\\s*(\\d+)")

    fun isMagnet(url: String?) = url != null && url.startsWith("magnet:", ignoreCase = true)

    /** Fonte do addon (infoHash + sources) no formato do Stremio. Null se o hash não for hex. */
    fun fromStremio(
        infoHash: String,
        sources: List<String>?,
        fileIdx: Int?,
        fileName: String?,
        mustInclude: String?,
        season: Int?,
        episode: Int?,
    ): TorrentSpec? {
        if (!HEX40.matches(infoHash)) return null
        val trackers = sources.orEmpty().filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }
        // Pacote de temporada sem dica do addon, o próprio padrão do episódio acha o arquivo.
        val fm = mustInclude?.takeIf { it.isNotBlank() }
            ?: if (fileIdx == null && season != null && episode != null) episodePattern(season, episode) else null
        return TorrentSpec(infoHash.lowercase(), trackers, fileIdx, fileName?.takeIf { it.isNotBlank() }, fm)
    }

    /** Trackers em ordem fixa, pra mesma fonte gerar sempre a mesma URL (o histórico compara). */
    fun build(spec: TorrentSpec): String = buildString {
        append("magnet:?xt=urn:btih:").append(spec.infoHash)
        spec.trackers.distinct().sorted().forEach { append("&tr=").append(enc(it)) }
        spec.fileIdx?.let { append("&x.fi=").append(it) }
        spec.fileName?.let { append("&x.fn=").append(enc(it)) }
        spec.mustInclude?.let { append("&x.fm=").append(enc(it)) }
        spec.webSeeds.forEach { append("&ws=").append(enc(it)) }
    }

    fun parse(magnet: String?): TorrentSpec? {
        if (!isMagnet(magnet)) return null
        var hash: String? = null
        val trackers = mutableListOf<String>()
        var fi: Int? = null
        var fn: String? = null
        var fm: String? = null
        val ws = mutableListOf<String>()
        for (part in magnet!!.substringAfter('?', "").split('&')) {
            val v = dec(part.substringAfter('=', ""))
            when (part.substringBefore('=').lowercase()) {
                "xt" -> v.takeIf { it.startsWith("urn:btih:", true) }?.substring(9)?.takeIf { HEX40.matches(it) }?.let { hash = it.lowercase() }
                "tr" -> if (v.isNotBlank()) trackers += v
                "x.fi" -> fi = v.toIntOrNull()
                "x.fn" -> fn = v.takeIf { it.isNotBlank() }
                "x.fm" -> fm = v.takeIf { it.isNotBlank() }
                "ws" -> if (v.startsWith("http", true)) ws += v
            }
        }
        return hash?.let { TorrentSpec(it, trackers, fi, fn, fm, ws) }
    }

    /** Magnet só com hash e trackers, que é o que o libtorrent precisa. */
    fun forEngine(spec: TorrentSpec): String =
        "magnet:?xt=urn:btih:${spec.infoHash}" + spec.trackers.distinct().joinToString("") { "&tr=" + enc(it) } +
            spec.webSeeds.joinToString("") { "&ws=" + enc(it) }

    /** S01E02 ou 1x02, o mesmo padrão que addons de torrent mandam em fileMustInclude. */
    fun episodePattern(season: Int, episode: Int) =
        "[sS]0*$season[ ._-]*[eE]0*$episode(?![0-9])|(?<![0-9])0*$season[ ._-]*[xX][ ._-]*0*$episode(?![0-9])"

    /** Seeders no rótulo ("👤 342"), convenção de Torrentio, TorrentClaw e afins. 0 se não tiver. */
    fun seeders(label: String) = SEEDERS.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}

object TorrentFiles {
    private val VIDEO = setOf("mp4", "mkv", "avi", "m4v", "mov", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv", "flv", "ogv")

    fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO

    /** Peça (no torrent inteiro) onde cai o byte [pos] de um arquivo que começa em [fileOffset]. */
    fun pieceAt(fileOffset: Long, pos: Long, pieceLength: Int): Int = ((fileOffset + pos) / pieceLength).toInt()

    /** Arquivo a tocar, na ordem fileIdx, padrão do episódio, nome parecido e maior vídeo. */
    fun pick(files: List<TorrentFile>, spec: TorrentSpec): TorrentFile? {
        if (files.isEmpty()) return null
        spec.fileIdx?.let { i -> files.firstOrNull { it.index == i }?.let { return it } }
        val videos = files.filter { isVideo(it.path) }.ifEmpty { files }
        spec.mustInclude?.let { re ->
            val r = runCatching { Regex(re) }.getOrNull()
            if (r != null) videos.filter { r.containsMatchIn(baseName(it.path)) }.maxByOrNull { it.size }?.let { return it }
        }
        spec.fileName?.let { hint ->
            val h = norm(hint)
            if (h.length >= 4) videos.filter { norm(baseName(it.path).substringBeforeLast('.')).let { n -> n.length >= 4 && (n.contains(h) || h.contains(n)) } }
                .maxByOrNull { it.size }?.let { return it }
        }
        return videos.maxByOrNull { it.size }
    }

    /**
     * Arquivo a pedir para o debrid. O nome exato do addon vem primeiro, depois o padrão do
     * episódio, e o fileIdx só quando a lista do serviço segue a ordem do torrent
     * ([indexReliable]). Sem dica nenhuma, o padrão do episódio da tela e o maior vídeo.
     */
    fun pickDebrid(files: List<TorrentFile>, spec: TorrentSpec, indexReliable: Boolean, season: Int = 0, episode: Int = 0): TorrentFile? {
        if (files.isEmpty()) return null
        val videos = files.filter { isVideo(it.path) }.ifEmpty { files }
        spec.fileName?.let { hint ->
            videos.filter { baseName(it.path).equals(baseName(hint), ignoreCase = true) }.maxByOrNull { it.size }?.let { return it }
        }
        spec.mustInclude?.let { re ->
            val r = runCatching { Regex(re) }.getOrNull()
            if (r != null) videos.filter { r.containsMatchIn(baseName(it.path)) }.maxByOrNull { it.size }?.let { return it }
        }
        if (indexReliable) spec.fileIdx?.let { i -> files.firstOrNull { it.index == i && isVideo(it.path) }?.let { return it } }
        if (spec.mustInclude == null && season > 0 && episode > 0) {
            val r = Regex(TorrentMagnet.episodePattern(season, episode))
            videos.filter { r.containsMatchIn(baseName(it.path)) }.maxByOrNull { it.size }?.let { return it }
        }
        spec.fileName?.let { hint ->
            val h = norm(hint.substringBeforeLast('.'))
            if (h.length >= 4) videos.filter { norm(baseName(it.path).substringBeforeLast('.')).let { n -> n.length >= 4 && (n.contains(h) || h.contains(n)) } }
                .maxByOrNull { it.size }?.let { return it }
        }
        return videos.maxByOrNull { it.size }
    }

    private fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
}
