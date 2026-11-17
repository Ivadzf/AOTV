package com.aotv.app.data.torrent

import java.net.URLDecoder

/**
 * Link colado na busca → tocar direto (Lote 18 item 3, v1.28.0).
 *
 * A busca aceita um magnet ou um link direto de vídeo colado e vai direto
 * p/ a reprodução (ou p/ o download, no caso de .mp4), sem precisar
 * navegar até o título. O que vale como "link colado":
 * - magnet válido ([TorrentMagnet.parse] acha o infoHash) — toca via
 *   [TorrentStreamer], streaming temporário como qualquer fonte torrent;
 *   NÃO é baixável como arquivo (não há .mp4 p/ o DownloadManager).
 * - URL http(s) de vídeo direto (.mp4, .mkv, .webm, .mov, .m3u8) —
 *   toca direto no player; só o .mp4 é baixável
 *   ([DownloadRepository.enqueueDirectLink]).
 *
 * O que NÃO vale: qualquer outro texto (busca normal), URL que não é
 * vídeo (ex.: página de um site), magnet incompleto/inválido (enquanto
 * o usuário digita, o hash ainda não fecha e cai na busca normal) e
 * texto com espaços (consulta com mais de uma palavra nunca é link).
 * O título vem do `dn` do magnet ou do nome do arquivo da URL — sem
 * `dn` e sem nome, "Torrent <hash curto>" / "Link direto".
 *
 * Lógica pura (sem Android) p/ ser testável.
 */
enum class PastedLinkKind { MAGNET, DIRECT_VIDEO }

data class PastedLink(
    val raw: String,
    val kind: PastedLinkKind,
    val title: String,
    /** Só .mp4 direto é baixável (o DownloadManager só baixa .mp4). */
    val downloadable: Boolean,
) {
    companion object {
        /** Detecta um link colado. Null = busca normal. */
        fun parse(text: String?): PastedLink? {
            val t = text?.trim() ?: return null
            if (t.length < 10 || t.any { it.isWhitespace() }) return null
            if (TorrentMagnet.isMagnet(t)) {
                val spec = TorrentMagnet.parse(t) ?: return null
                val dn = t.substringAfter('?', "").split('&')
                    .firstOrNull { it.startsWith("dn=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                    ?.takeIf { it.isNotBlank() }
                return PastedLink(
                    raw = t,
                    kind = PastedLinkKind.MAGNET,
                    title = dn ?: "Torrent ${spec.infoHash.take(8)}",
                    downloadable = false,
                )
            }
            if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
                val path = t.substringBefore('|').substringBefore('?').lowercase()
                val ext = path.substringAfterLast('.', "")
                if (ext !in setOf("mp4", "mkv", "webm", "mov", "m3u8")) return null
                val name = t.substringBefore('|').substringAfterLast('/')
                    .substringBefore('?')
                    .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                    .substringBeforeLast('.', "")
                    .takeIf { it.isNotBlank() } ?: "Link direto"
                return PastedLink(
                    raw = t,
                    kind = PastedLinkKind.DIRECT_VIDEO,
                    title = name.take(60),
                    downloadable = ext == "mp4",
                )
            }
            return null
        }
    }
}
