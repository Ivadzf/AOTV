package com.aotv.app.ui.movies

import com.aotv.app.data.remote.StremioStream
import com.aotv.app.data.remote.hasAtmos
import com.aotv.app.data.remote.hasDolbyVision
import com.aotv.app.data.remote.hasHdr
import com.aotv.app.data.remote.ptAudio
import com.aotv.app.data.remote.qualityRankOf
import java.util.Locale

internal data class ReleaseGroup(val members: List<StremioStream>) {
    val lead get() = members.first()
}

/** Só agrupa arquivos cuja identidade foi informada. Qualidade igual não prova que é o mesmo vídeo. */
internal fun groupReleases(streams: List<StremioStream>): List<ReleaseGroup> = streams.groupBy { s ->
    if (s.fileName.isNotBlank() && s.fileSize > 0) "file:${s.fileName.lowercase(Locale.ROOT)}:${s.fileSize}"
    else "url:${s.url}"
}.values.map { ReleaseGroup(it) }

private val RELEASE_CODEC = Regex("\\b(hevc|x265|h[ .]?265|av1|x264|h[ .]?264|avc)\\b", RegexOption.IGNORE_CASE)
private val RELEASE_KIND = Regex("\\b(web[ .-]?dl|web[ .-]?rip|blu[ .-]?ray|remux|hdtv|dvd[ .-]?rip)\\b", RegexOption.IGNORE_CASE)
private val RELEASE_SIZE = Regex("\\b(\\d{1,6}(?:[.,]\\d{1,3})?)\\s?(TiB|GiB|MiB|TB|GB|MB)\\b", RegexOption.IGNORE_CASE)
private val RELEASE_SEEDS = Regex("(?:👤|👥|🌱|\\bseed(?:er)?s?\\s?)\\s{0,3}[:=]?\\s{0,3}(\\d{1,8})", RegexOption.IGNORE_CASE)
private val RELEASE_FILE = Regex("(?:\\.mkv|\\.mp4|\\.avi|\\.S\\d{1,2}E\\d{1,3}\\.)", RegexOption.IGNORE_CASE)

internal fun releaseInfo(stream: StremioStream, title: String, providers: List<String> = emptyList()): SourceInfo {
    val text = stream.releaseText.ifBlank { stream.label }.take(3000)
    val base = parseSourceLabel(stream.label, stream.addon, stream.qualityRank, title)
    val quality = stream.qualityRank.takeIf { it > 0 } ?: qualityRankOf(text)
    val codec = RELEASE_CODEC.find(text)?.value?.uppercase(Locale.ROOT)
        ?.replace("X265", "HEVC")?.replace("H.265", "HEVC")?.replace("H265", "HEVC")
        ?.replace("X264", "H.264")?.replace("AVC", "H.264")
    val kind = RELEASE_KIND.find(text)?.value?.uppercase(Locale.ROOT)?.replace("WEBDL", "WEB-DL")
        ?.replace("WEBRIP", "WEBRip")?.replace("BLURAY", "BluRay")
    val badges = buildList {
        if (stream.dolbyVision || hasDolbyVision(text)) add("Dolby Vision")
        if (stream.hdr || hasHdr(text)) add(if (Regex("HDR10\\+|HDR10Plus", RegexOption.IGNORE_CASE).containsMatchIn(text)) "HDR10+"
            else if (text.contains("HDR10", true)) "HDR10" else "HDR")
        if (stream.atmos || hasAtmos(text)) add("Atmos")
        if (Regex("\\btrue[ .-]?hd\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) add("TrueHD")
        if (Regex("\\bddp|e[ .-]?ac3|dd\\+", RegexOption.IGNORE_CASE).containsMatchIn(text)) add("DD+")
        if (Regex("\\bdts(?:[ .-]?hd)?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) add("DTS")
        Regex("\\b[257]\\.[01]\\b").find(text)?.value?.let(::add)
    }
    val language = when {
        stream.ptAudio || ptAudio(text) -> "Dublado"
        Regex("legendad|\\bleg\\b|\\bsubbed\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Legendado"
        else -> base.language
    }
    val bytes = stream.fileSize
    val size = if (bytes > 0) {
        val gb = bytes / (1024.0 * 1024 * 1024)
        if (gb >= 1) String.format(Locale.US, "%.2f GB", gb).replace('.', ',')
        else "${bytes / (1024 * 1024)} MB"
    } else RELEASE_SIZE.find(text)?.let { it.groupValues[1].replace('.', ',') + " " + it.groupValues[2].uppercase(Locale.ROOT) }.orEmpty()
    val file = stream.fileName.ifBlank { text.lineSequence().firstOrNull { RELEASE_FILE.containsMatchIn(it) }.orEmpty() }.take(512)
    return base.copy(
        quality = when { quality >= 2160 -> "4K"; quality >= 1440 -> "2K"; quality > 0 -> "${quality}p"; else -> "" },
        server = listOfNotNull(kind, codec).joinToString(" · ").ifBlank { base.server.takeUnless { it == file }.orEmpty() },
        language = language, tags = badges.distinct(), fileName = file, size = size,
        seeds = RELEASE_SEEDS.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        providers = providers.ifEmpty { listOf(stream.addon).filter { it.isNotBlank() } },
    )
}
