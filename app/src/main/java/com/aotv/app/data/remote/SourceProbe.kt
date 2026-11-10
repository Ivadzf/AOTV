package com.aotv.app.data.remote

import com.aotv.app.data.torrent.TorrentMagnet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Referer mandado para os CDNs dos addons. Mantido igual ao que as fontes diretas já usavam,
 * porque alguns servidores de .mp4 recusam pedido sem Referer.
 */
const val STREAM_REFERER = "https://viewplayer.online/"
const val STREAM_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

/**
 * Resultado do teste rápido de uma fonte. [ok] falso quando o servidor não respondeu, [ms] é o
 * tempo até os primeiros 64 KB, [sec] a duração do arquivo (0 = não deu pra saber) e [height]
 * a altura real do vídeo (0 = não deu pra saber).
 */
data class ProbeResult(val ok: Boolean, val ms: Long, val sec: Double, val height: Int)

// ── Leitura do cabeçalho MP4 ──────────────────────────────────────────────────

private fun u32(b: ByteArray, o: Int): Long =
    ((b[o].toLong() and 0xff) shl 24) or ((b[o + 1].toLong() and 0xff) shl 16) or
        ((b[o + 2].toLong() and 0xff) shl 8) or (b[o + 3].toLong() and 0xff)

private fun isBox(b: ByteArray, i: Int, name: String) =
    b[i] == name[0].code.toByte() && b[i + 1] == name[1].code.toByte() &&
        b[i + 2] == name[2].code.toByte() && b[i + 3] == name[3].code.toByte()

/** Duração em segundos achada na caixa mvhd, ou 0. */
fun mvhdSeconds(b: ByteArray): Double {
    var i = 4
    while (i + 24 <= b.size) {
        if (isBox(b, i, "mvhd")) {
            val v1 = b[i + 4].toInt() == 1
            if (v1 && i + 36 > b.size) return 0.0
            val scale = if (v1) u32(b, i + 24) else u32(b, i + 16)
            val dur = if (v1) u32(b, i + 28) * 4294967296L + u32(b, i + 32) else u32(b, i + 20)
            if (scale > 0 && dur > 0) return dur.toDouble() / scale
        }
        i++
    }
    return 0.0
}

/** Altura do vídeo lida nas caixas tkhd (a maior delas), ou 0. Fica no começo do moov. */
fun tkhdHeight(b: ByteArray): Int {
    var best = 0
    var i = 4
    while (i + 4 <= b.size) {
        if (isBox(b, i, "tkhd")) {
            val start = i - 4
            val size = u32(b, start).toInt()
            val end = start + size
            if (size in 84..120 && end <= b.size) {
                // Parte inteira do 16.16 da altura, nos 4 últimos bytes da caixa.
                val h = ((b[end - 4].toInt() and 0xff) shl 8) or (b[end - 3].toInt() and 0xff)
                if (h in (best + 1) until 5000) best = h
            }
        }
        i++
    }
    return best
}

/** Soma do EXTINF de uma lista HLS fechada (com ENDLIST). Lista ao vivo ou aberta dá 0. */
fun hlsSeconds(body: String): Double {
    if (!body.startsWith("#EXTM3U") || "#EXT-X-ENDLIST" !in body) return 0.0
    return Regex("#EXTINF:([\\d.]+)").findAll(body).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
}

/** Primeira variante de uma lista mestre HLS, ou null se não for mestre. */
fun hlsVariant(body: String): String? {
    val lines = body.lines()
    val i = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
    return lines.getOrNull(i + 1)?.trim()?.takeIf { i >= 0 && it.isNotBlank() }
}

// ── Regras de escolha (iguais às do webOS) ────────────────────────────────────

/**
 * Duração do arquivo destoa da duração do filme no TMDB. Addon que busca pelo título em
 * português devolve filme de mesmo nome (Ponto Sem Retorno trazia Rubikon), e a resposta não
 * tem ano nem id. Tolerância de 7 min ou 6%, o que for maior.
 */
fun durationMismatch(runtimeMin: Int, sec: Double): Boolean {
    if (runtimeMin <= 0 || sec.isNaN() || sec < 600) return false
    return abs(sec / 60 - runtimeMin) > max(7.0, runtimeMin * 0.06)
}

private val DUB = Regex("dublad|\\bdub\\b|dual|portugu|pt-?br|nacional", RegexOption.IGNORE_CASE)
private val LEG = Regex("legendad|\\bleg\\b|\\bsub(s|bed)?\\b", RegexOption.IGNORE_CASE)

/** Idioma da fonte pelo rótulo. "dub", "leg" ou "" quando não dá pra saber. */
fun langOf(label: String): String = when {
    ptAudio(label) -> "dub"
    LEG.containsMatchIn(label) -> "leg"
    DUB.containsMatchIn(label) -> "dub"
    else -> ""
}

// ── Texto do addon lido para os selos do pôster (iguais ao webOS) ─────────────────────────────

// Seeds e tamanho saem antes da leitura, senão "👤 1080" virava resolução. Repetições com teto
// porque o texto vem do addon.
private val COUNTS = Regex(
    "(?:👤|👥|🌱|⬆️?|↑)\\s{0,3}\\d[\\d.,]{0,12}|\\bseed(?:er)?s?:?\\s{0,3}\\d[\\d.,]{0,12}" +
        "|\\b\\d[\\d.,]{0,12}\\s{0,3}seed(?:er)?s?\\b|\\b\\d{1,6}(?:[.,]\\d{1,3})?\\s?(?:TB|TiB|GB|GiB|MB|MiB)\\b",
    RegexOption.IGNORE_CASE,
)
private val RANKS = listOf(
    Regex("\\b(2160[pi]?|4k|uhd)\\b", RegexOption.IGNORE_CASE) to 2160,
    Regex("\\b(1440[pi]?|2k|qhd)\\b", RegexOption.IGNORE_CASE) to 1440,
    Regex("\\b(1080[pi]?|fhd)\\b", RegexOption.IGNORE_CASE) to 1080,
    Regex("\\b720[pi]?\\b", RegexOption.IGNORE_CASE) to 720,
    Regex("\\b(576[pi]?|480[pi]?|360p|sd)\\b", RegexOption.IGNORE_CASE) to 480,
    Regex("\\bhd\\b", RegexOption.IGNORE_CASE) to 720,
)

/** Resolução citada no texto. Só palavra inteira, então 4KHDHub, HDR e HDTV não contam. */
fun qualityRankOf(text: String): Int {
    val t = COUNTS.replace(text, " ")
    return RANKS.firstOrNull { it.first.containsMatchIn(t) }?.second ?: 0
}

private val DOLBY_VISION = Regex("\\b(dv|dovi|dolby[ .-]?vision)\\b", RegexOption.IGNORE_CASE)
// HDRip é só a origem do arquivo, então HDR precisa ser palavra inteira (ou HDR10, HDR10+).
private val HDR = Regex("\\b(hdr|hlg)\\b|\\bhdr10", RegexOption.IGNORE_CASE)
private val ATMOS = Regex("\\batmos\\b", RegexOption.IGNORE_CASE)
// IGNORE_CASE do Java só junta maiúscula e minúscula no ASCII, então o acento vai nas duas formas.
private val DUBBED = Regex("dublad|dual[\\s.-]?[aáÁ]udio", RegexOption.IGNORE_CASE)
private val SUBBED = Regex("legendad", RegexOption.IGNORE_CASE)
private val SUBS = Regex("legendad|legendas?\\b", RegexOption.IGNORE_CASE)
private val PT_WORD = Regex("\\b(portugu[eêÊ]s|portuguese|pt-?br)\\b", RegexOption.IGNORE_CASE)

fun hasDolbyVision(text: String) = DOLBY_VISION.containsMatchIn(text)
fun hasHdr(text: String) = HDR.containsMatchIn(text)
fun hasAtmos(text: String) = ATMOS.containsMatchIn(text)

/**
 * Áudio em português. Dublado ou dual áudio, ou a bandeira e o nome do idioma quando o texto não
 * fala de legenda. "Legendado PT-BR" não conta.
 */
fun ptAudio(text: String): Boolean =
    (DUBBED.containsMatchIn(text) && !SUBBED.containsMatchIn(text)) ||
        (!SUBS.containsMatchIn(text) && ("🇧🇷" in text || PT_WORD.containsMatchIn(text)))

/**
 * Ordena as fontes pela preferência do perfil sem perder a ordem dos addons dentro de cada grupo
 * (igual ao sortSources do webOS). Primeiro o idioma pedido, depois quem respeita o limite de
 * qualidade, depois a maior. [audio] é "any", "dub" ou "leg" e [maxQuality] 0 é sem limite.
 */
fun <T> sortByPrefs(list: List<T>, audio: String, maxQuality: Int, label: (T) -> String, rank: (T) -> Int): List<T> {
    if (audio == "any" && maxQuality <= 0) return list
    // sortedWith é estável, então empate mantém a ordem dos addons.
    return list.sortedWith(
        compareBy(
            { s -> if (audio == "any") 0 else langOf(label(s)).let { if (it == audio) 0 else if (it.isEmpty()) 1 else 2 } },
            { s -> if (maxQuality > 0 && rank(s) > maxQuality) 1 else 0 },
            { s -> if (maxQuality > 0) -rank(s) else 0 },
        ),
    )
}

/** "4K" acima de 2000 linhas, senão a altura com p ("800p"). */
fun heightLabel(h: Int): String = if (h >= 2000) "4K" else "${h}p"

/** Uma fonte com o que se sabe dela, na forma que as regras abaixo precisam. */
data class SourceFacts(
    val url: String,
    val addon: String,
    val label: String,
    /** Qualidade lida no nome da fonte (2160, 1080...). 0 = o nome não diz. */
    val labelRank: Int,
    val probe: ProbeResult? = null,
    val wrongTitle: Boolean = false,
    /** Torrent no debrid. 2 = o serviço tem pronto (toca por link direto), 0 = não se sabe ou não tem. */
    val debrid: Int = 0,
) {
    val dead get() = probe?.ok == false
    /** Resolução real quando o teste achou, senão a do nome. */
    val rank get() = probe?.height?.takeIf { it > 0 } ?: labelRank
    val lang get() = langOf(label)
    /** Torrent que vai tocar pelo motor do aparelho (com cache no debrid ele vira link direto). */
    val p2p get() = TorrentMagnet.isMagnet(url) && debrid < 2
}

/**
 * Fonte do Assistir. No idioma da primeira da lista (que já vem na ordem dos addons), entre as
 * que responderam em até 2,5 s, a de maior resolução respeitando [maxQuality] (0 = sem limite),
 * desempate pela mais rápida. Fonte morta ou de outro filme só entra se não sobrar nada.
 */
fun bestIndex(list: List<SourceFacts>, maxQuality: Int = 0): Int {
    val cands = list.indices.filter { !list[it].wrongTitle && !list[it].dead }
    if (cands.isEmpty()) return list.indexOfFirst { !it.wrongTitle }.coerceAtLeast(0)
    val lang = list[cands[0]].lang
    val cap = if (maxQuality > 0) maxQuality else Int.MAX_VALUE
    // P2P acima de 1080p é arquivo enorme que engasga em TV box, fica atrás e desempata por seeders.
    // Torrent pronto no debrid conta como fonte rápida (o serviço entrega na hora).
    val byKey = compareBy<SourceFacts>(
        { if (it.debrid >= 2 || (it.probe != null && it.probe.ms <= 2500)) 1 else 0 },
        { if (it.rank > cap) -1 else if (it.p2p && it.rank > 1080) 1 else it.rank },
        { if (TorrentMagnet.isMagnet(it.url)) TorrentMagnet.seeders(it.label) else 0 },
        { -(it.probe?.ms ?: 99_999L) },
    )
    var best = cands[0]
    for (i in cands) if (list[i].lang == lang && byKey.compare(list[i], list[best]) > 0) best = i
    return best
}

/**
 * Outro espelho da fonte atual. Mesmo addon, mesmo idioma e mesma resolução (10% de folga, e
 * resolução desconhecida de um dos lados conta como igual), ainda não tentado. -1 se não houver.
 */
fun sameMirror(list: List<SourceFacts>, cur: Int, tried: Set<String>): Int {
    val c = list.getOrNull(cur) ?: return -1
    list.forEachIndexed { i, s ->
        if (i == cur || s.url in tried || s.dead || s.wrongTitle || !s.url.startsWith("http")) return@forEachIndexed
        if (!s.addon.equals(c.addon, true) || s.lang != c.lang) return@forEachIndexed
        val hs = s.rank; val hc = c.rank
        if (hs == 0 || hc == 0 || abs(hs - hc) <= max(hs, hc) * 0.1) return i
    }
    return -1
}

/**
 * Próxima fonte ainda não tentada, na ordem da lista. Primeiro as que estão vivas, não são de
 * outro filme e não vêm de addon que acabou de falhar (os servidores de um addon caem juntos).
 * Depois qualquer uma que não seja de outro filme e, por último, o que sobrar. -1 se acabou.
 */
fun nextIndex(list: List<SourceFacts>, tried: Set<String>, failedAddons: Set<String>): Int {
    val passes = listOf<(SourceFacts) -> Boolean>(
        { !it.wrongTitle && !it.dead && it.addon !in failedAddons },
        { !it.wrongTitle },
        { true },
    )
    for (p in passes) list.forEachIndexed { i, s -> if (s.url !in tried && p(s)) return i }
    return -1
}

// ── Teste das fontes pela rede ────────────────────────────────────────────────

/**
 * Testa fontes sem tocar o vídeo. Baixa só 64 KB com Range e lê ali duração e resolução.
 * Resultado fica em cache por URL (10 min, link de addon expira), e no máximo 4 testes rodam
 * ao mesmo tempo no app inteiro. O detalhe e o player compartilham o mesmo cache.
 */
@Singleton
class SourceProber @Inject constructor() {
    private val client = AppDns.mediaClient.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)

    private class Done(val at: Long, val r: ProbeResult)
    private val done = ConcurrentHashMap<String, Done>()
    private val running = ConcurrentHashMap<String, Deferred<ProbeResult>>()

    /** Resultado já pronto, sem esperar. null = ainda não testou. */
    fun cached(url: String): ProbeResult? =
        done[url]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.r

    suspend fun probe(url: String): ProbeResult {
        cached(url)?.let { return it }
        val d = running.computeIfAbsent(url) {
            scope.async {
                val r = gate.withPermit { runCatching { measure(url) }.getOrElse { ProbeResult(false, 0, 0.0, 0) } }
                done[url] = Done(System.currentTimeMillis(), r)
                android.util.Log.d("SourceProber", "${r.ms} ms ok=${r.ok} min=${(r.sec / 60).toInt()} alt=${r.height} ${runCatching { java.net.URI(url).host }.getOrNull()}")
                r
            }
        }
        return d.await().also { running.remove(url, d) }
    }

    private class Range(val bytes: ByteArray, val total: Long, val url: String)

    /** GET com Range, lendo no máximo [cap] bytes. null se o servidor não deu 200 ou 206. */
    private fun rangeGet(url: String, range: String?, timeoutSec: Long, cap: Long): Range? {
        val req = Request.Builder().url(url)
            .header("Referer", STREAM_REFERER)
            .header("User-Agent", STREAM_UA)
            .apply { if (range != null) header("Range", range) }
            .build()
        val call = client.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).build().newCall(req)
        return call.execute().use { r ->
            if (r.code != 200 && r.code != 206) return null
            val total = Regex("/(\\d+)\\s*$").find(r.header("Content-Range").orEmpty())
                ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            // Servidor que ignora o Range manda o arquivo inteiro. Lê só o pedaço que importa.
            val src = r.body?.source() ?: return null
            src.request(cap)
            val buf = src.buffer
            Range(buf.readByteArray(minOf(buf.size, cap)), total, r.request.url.toString())
        }
    }

    private fun measure(url: String): ProbeResult {
        val t0 = System.currentTimeMillis()
        val r = runCatching { rangeGet(url, "bytes=0-65535", 10, 65_536) }.getOrNull()
        val ms = System.currentTimeMillis() - t0
        if (r == null || r.bytes.isEmpty()) return ProbeResult(false, ms, 0.0, 0)
        val text = String(r.bytes, Charsets.ISO_8859_1)
        if (text.startsWith("#EXTM3U")) return ProbeResult(true, ms, hlsDuration(text, r.url), 0)
        val sec = mvhdSeconds(r.bytes)
        val h = tkhdHeight(r.bytes)
        if (sec > 0 || r.total <= 65_536) return ProbeResult(true, ms, sec, h)
        // moov no fim do arquivo (MP4 sem faststart). Lê os últimos 3 MB.
        val tail = runCatching {
            rangeGet(url, "bytes=${max(0, r.total - TAIL)}-", 15, TAIL)
        }.getOrNull()
        return ProbeResult(true, ms, tail?.let { mvhdSeconds(it.bytes) } ?: 0.0, tail?.let { tkhdHeight(it.bytes) } ?: 0)
    }

    /** Duração do HLS. Lista mestre desce um nível para a primeira variante. */
    private fun hlsDuration(body: String, base: String, depth: Int = 0): Double {
        val variant = hlsVariant(body) ?: return hlsSeconds(body)
        if (depth > 0) return 0.0
        val next = runCatching { java.net.URI(base).resolve(variant).toString() }.getOrNull() ?: return 0.0
        val b = runCatching { rangeGet(next, null, 10, 2_000_000) }.getOrNull() ?: return 0.0
        return hlsDuration(String(b.bytes, Charsets.UTF_8), b.url, depth + 1)
    }

    private companion object {
        const val TAIL = 3_145_728L
        const val TTL_MS = 10 * 60_000L
    }
}
