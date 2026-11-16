package com.aotv.app.data.remote

import android.media.MediaCodecList
import com.aotv.app.data.local.LastSourcePick
import com.aotv.app.data.torrent.TorrentMagnet
import kotlin.math.min

// ── Ranking de fontes, Lote 17 (v1.27.0) ─────────────────────────────────────
// O ranking antigo (bestIndex, Lote 12) escolhia por resolução + "respondeu
// em até 2,5 s" e o Davi viu stream ruim em rank alto e bom em rank baixo:
// sem motivo visível, a ordem parecia sorteio. Aqui o score 0–100 é
// DETERMINÍSTICO — função pura dos sinais da fonte (resolução, tempo até o
// primeiro byte, falhas anteriores, tamanho, CAM/sample, idioma, debrid) —
// e a UI mostra o score com o motivo ("#1 · 84 — 1080p · rápido · 0 falhas").
// O mesmo conjunto de fontes, nas mesmas condições, dá a MESMA ordem: o
// desempate final é pela URL (ordem alfabética), nunca por ordem de chegada
// nem por aleatório. O detalhe e a folha do player usam o MESMO score
// ([rankSources]); só os filtros de exibição diferem.

/** Preset de fonte em palavras simples (Lote 17 item 2). */
enum class SourcePreset(val id: String, val label: String) {
    BEST_PICTURE("best", "Melhor imagem"),
    BALANCED("balanced", "Equilibrado"),
    FAST_START("fast", "Início rápido"),
    ;
    companion object {
        fun of(id: String): SourcePreset = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/** Estratégia de ranking configurável (Lote 17 item 4). */
enum class SourceStrategy(val id: String, val label: String) {
    QUALITY("quality", "Qualidade primeiro"),
    SPEED("speed", "Rapidez primeiro"),
    RELIABILITY("reliability", "Confiabilidade primeiro"),
    LANGUAGE("language", "Idioma primeiro"),
    ;
    companion object {
        fun of(id: String): SourceStrategy = entries.firstOrNull { it.id == id } ?: QUALITY
    }
}

/** Filtro de idioma acima da lista de fontes (Lote 17 item 5). "" = todos. */
const val SOURCE_LANG_ALL = ""
const val SOURCE_LANG_DUB = "dub"
const val SOURCE_LANG_LEG = "leg"
/** "Original" = sem marca de dublado nem legendado no rótulo (áudio original). */
const val SOURCE_LANG_ORIGINAL = "original"

/**
 * Saúde aprendida de um host (Lote 17 item 1): o que funciona NAQUELE
 * aparelho. A chave é o host (nunca a URL inteira — pode ter token).
 * Só contagens e tempos; nada de credencial.
 */
data class SourceHealth(
    val successes: Int = 0,
    val failures: Int = 0,
    /** Última falha (epoch ms). 0 = nunca falhou. */
    val lastFailureMs: Long = 0,
    /** Tempo médio até o primeiro byte das tentativas que deram certo (ms). */
    val avgMs: Long = 0,
    /** Travadas seguidas dentro de 60 s (p/ o downgrade adaptativo). */
    val stalls: Int = 0,
    val lastStallMs: Long = 0,
    val lastSeenMs: Long = 0,
) {
    val failureRate: Double get() =
        if (successes + failures == 0) 0.0 else failures.toDouble() / (successes + failures)
}

/** Host da URL, em minúsculas. "" quando não dá pra extrair. */
fun sourceHost(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.lowercase().orEmpty()

private fun encodeHealth(h: SourceHealth): org.json.JSONObject =
    org.json.JSONObject()
        .put("s", h.successes).put("f", h.failures).put("lf", h.lastFailureMs)
        .put("avg", h.avgMs).put("st", h.stalls).put("ls", h.lastStallMs)
        .put("seen", h.lastSeenMs)

private fun decodeHealth(o: org.json.JSONObject): SourceHealth = SourceHealth(
    successes = o.optInt("s", 0), failures = o.optInt("f", 0),
    lastFailureMs = o.optLong("lf", 0), avgMs = o.optLong("avg", 0),
    stalls = o.optInt("st", 0), lastStallMs = o.optLong("ls", 0),
    lastSeenMs = o.optLong("seen", 0),
)

/** Mapa host → saúde, em JSON (p/ guardar num pref de texto). No máx. 100 hosts. */
fun encodeHealthMap(map: Map<String, SourceHealth>): String {
    val o = org.json.JSONObject()
    map.entries.sortedByDescending { it.value.lastSeenMs }.take(100)
        .forEach { (k, v) -> o.put(k, encodeHealth(v)) }
    return o.toString()
}

fun decodeHealthMap(raw: String?): Map<String, SourceHealth> = runCatching {
    val o = org.json.JSONObject(raw ?: "{}")
    o.keys().asSequence().associateWith { k -> decodeHealth(o.getJSONObject(k)) }
}.getOrDefault(emptyMap())

/** Uma tentativa que deu certo (tocou). Média móvel do tempo até o 1º byte. */
fun recordSourceSuccess(map: Map<String, SourceHealth>, url: String, ms: Long, nowMs: Long): Map<String, SourceHealth> {
    val host = sourceHost(url)
    if (host.isBlank()) return map
    val h = map[host] ?: SourceHealth()
    // Sem tempo medido (ms <= 0), só conta o acerto — a média não muda.
    val avg = if (ms <= 0) h.avgMs
        else if (h.successes <= 0) ms
        else (h.avgMs * h.successes + ms) / (h.successes + 1)
    return map + (host to h.copy(successes = h.successes + 1, avgMs = avg, lastSeenMs = nowMs))
}

/** Uma tentativa que falhou (não respondeu / travou na troca). */
fun recordSourceFailure(map: Map<String, SourceHealth>, url: String, nowMs: Long): Map<String, SourceHealth> {
    val host = sourceHost(url)
    if (host.isBlank()) return map
    val h = map[host] ?: SourceHealth()
    return map + (host to h.copy(failures = h.failures + 1, lastFailureMs = nowMs, lastSeenMs = nowMs))
}

/** Uma travada no meio da reprodução (buffer parado / timeout). */
fun recordSourceStall(map: Map<String, SourceHealth>, url: String, nowMs: Long): Map<String, SourceHealth> {
    val host = sourceHost(url)
    if (host.isBlank()) return map
    val h = map[host] ?: SourceHealth()
    // Janela de 60 s: travada velha não conta (ver [shouldOfferDowngrade]).
    val stalls = if (nowMs - h.lastStallMs > STALL_WINDOW_MS) 1 else h.stalls + 1
    return map + (host to h.copy(stalls = stalls, lastStallMs = nowMs, lastSeenMs = nowMs))
}

/** Janela do downgrade adaptativo: 3 travadas em 1 minuto. */
const val STALL_WINDOW_MS = 60_000L
const val DOWNGRADE_STALLS = 3

/** A fonte 1080p parou 3x em 1 minuto? Aí vale oferecer "Mudar para 720p?". */
fun shouldOfferDowngrade(health: SourceHealth?, nowMs: Long): Boolean {
    if (health == null) return false
    return health.stalls >= DOWNGRADE_STALLS && nowMs - health.lastStallMs <= STALL_WINDOW_MS
}

// ── Sinais lidos do rótulo ──────────────────────────────────────────────────

private val CAM_RE = Regex("\\b(cam|ts|camrip|telesync|hdcam|hdts)\\b", RegexOption.IGNORE_CASE)
private val SAMPLE_RE = Regex("\\bsample\\b", RegexOption.IGNORE_CASE)
private val SIZE_RE = Regex("\\b(\\d+(?:[.,]\\d+)?)\\s?(GB|MB)\\b", RegexOption.IGNORE_CASE)
private val CODEC_AV1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
private val CODEC_HEVC = Regex("\\b(x\\.?265|h\\.?265|hevc)\\b", RegexOption.IGNORE_CASE)
private val CODEC_VP9 = Regex("\\bvp9\\b", RegexOption.IGNORE_CASE)

/** CAM / telesync no rótulo — pré-checagem pula estas cópias (Lote 17 item 1). */
fun isCamLabel(label: String): Boolean = CAM_RE.containsMatchIn(label)

/** "Sample" no rótulo — trecho de demonstração, não o filme. */
fun isSampleLabel(label: String): Boolean = SAMPLE_RE.containsMatchIn(label)

/** Tamanho em bytes lido do rótulo ("2,4 GB"). 0 = não informado. */
fun sizeBytesOf(label: String): Long {
    val m = SIZE_RE.find(label) ?: return 0
    val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0
    return (n * if (m.groupValues[2].equals("GB", ignoreCase = true)) 1_073_741_824L else 1_048_576L).toLong()
}

/** Codec citado no rótulo ("AV1", "HEVC", "H264", "VP9", "" = não diz). */
fun codecOf(label: String): String = when {
    CODEC_AV1.containsMatchIn(label) -> "AV1"
    CODEC_HEVC.containsMatchIn(label) -> "HEVC"
    CODEC_VP9.containsMatchIn(label) -> "VP9"
    Regex("\\b(x\\.?264|h\\.?264|avc)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label) -> "H264"
    else -> ""
}

/**
 * Codecs que ESTE aparelho decodifica (MediaCodecList, inclui software).
 * Sem Media3 aqui de propósito: `getCapabilitiesForType` NÃO existe na
 * Media3 1.6.0 — o caminho certo é o MediaCodecList do Android.
 */
fun deviceSupportedCodecs(): Set<String> {
    val out = mutableSetOf<String>()
    runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.forEach { info ->
            if (info.isEncoder) return@forEach
            info.supportedTypes.forEach { mime ->
                when (mime.lowercase()) {
                    "video/av01" -> out += "AV1"
                    "video/hevc" -> out += "HEVC"
                    "video/avc" -> out += "H264"
                    "video/x-vnd.on2.vp9" -> out += "VP9"
                }
            }
        }
    }
    // Sem dado (falha ao listar): não esconde nada — o filtro só age com dado real.
    return out
}

/** O aparelho toca esta fonte? Sem codec citado, assume que sim. */
fun isPlayableOnDevice(label: String, supported: Set<String>): Boolean {
    if (supported.isEmpty()) return true
    val codec = codecOf(label)
    return codec.isBlank() || codec in supported
}

/**
 * Cópia suspeita que a pré-checagem pula (Lote 17 item 1): CAM, sample ou
 * arquivo pequeno demais p/ a duração (menos de 50 MB num filme).
 */
fun isSuspiciousSource(label: String, fileSizeBytes: Long, durationSec: Double, runtimeMin: Int): Boolean {
    if (isCamLabel(label) || isSampleLabel(label)) return true
    if (runtimeMin > 30 && durationSec <= 0 && fileSizeBytes in 1 until 50_000_000) return true
    return false
}

// ── Score ───────────────────────────────────────────────────────────────────

/** Score de uma fonte + o motivo em palavras simples, na ordem fixa. */
data class SourceScore(val score: Int, val reason: String, val suspicious: Boolean)

/**
 * Score 0–100, determinístico e explicado. Componentes (soma, teto 100):
 * resolução até 30 · velocidade até 20 · saúde até 5 (falhas até −20) ·
 * idioma até 10 · tamanho até 5 · debrid pronto +8 · origem até 5 ·
 * seeds até 4 · última escolha +6. CAM/sample −30. Morta/outro filme = 0.
 * Preset e estratégia só mudam os pesos, nunca a ordem dos empates: o
 * desempate final ([rankSources]) é por URL, então a ordem é estável.
 */
fun scoreSource(
    fact: SourceFacts,
    health: SourceHealth?,
    preset: SourcePreset = SourcePreset.BALANCED,
    strategy: SourceStrategy = SourceStrategy.QUALITY,
    langPref: String = "any",
    maxQuality: Int = 0,
    lastPick: LastSourcePick? = null,
    originalFirst: Boolean = false,
    /** Duração do título no TMDB (min). 0 = desconhecida (desliga o corte por tamanho). */
    runtimeMin: Int = 0,
): SourceScore {
    if (fact.dead) return SourceScore(0, "não respondeu no teste", suspicious = false)
    if (fact.wrongTitle) return SourceScore(0, "parece outro título", suspicious = false)
    val suspicious = isSuspiciousSource(fact.label, sizeBytesOf(fact.label), fact.probe?.sec ?: 0.0, runtimeMin)
    val parts = mutableListOf<String>()
    var score = 0

    // Resolução (até 30). FAST_START trava em 18: começar rápido vale mais que 4K.
    val resPts = when {
        fact.rank >= 2160 -> 30
        fact.rank >= 1440 -> 26
        fact.rank >= 1080 -> 24
        fact.rank >= 720 -> 18
        fact.rank >= 480 -> 10
        fact.rank > 0 -> 6
        else -> 8
    }.let { if (preset == SourcePreset.FAST_START) min(it, 18) else it }
    score += resPts + if (preset == SourcePreset.BEST_PICTURE && fact.rank >= 1080) 4 else 0
    if (strategy == SourceStrategy.QUALITY && fact.rank >= 1080) score += 6
    parts += when {
        fact.rank >= 2160 -> "4K"
        fact.rank >= 1440 -> "1440p"
        fact.rank >= 1080 -> "1080p"
        fact.rank >= 720 -> "720p"
        fact.rank > 0 -> "${fact.rank}p"
        else -> "sem resolução"
    }

    // Velocidade até o primeiro byte (probe de 64 KB). Sem probe, neutro (10).
    val ms = fact.probe?.ms
    val speedPts = when {
        ms == null -> 10
        ms <= 500 -> 20
        ms <= 1_200 -> 16
        ms <= 2_500 -> 12
        ms <= 5_000 -> 6
        else -> 2
    }
    score += speedPts + if (strategy == SourceStrategy.SPEED) {
        when {
            ms != null && ms <= 1_200 -> 6
            ms != null && ms > 2_500 -> -4
            else -> 0
        }
    } else 0
    parts += when {
        ms == null -> "sem teste de velocidade"
        ms <= 1_200 -> "rápido"
        ms <= 2_500 -> "normal"
        else -> "lento"
    }

    // Saúde aprendida: cada falha −5 (teto −20), cada acerto +1 (teto +5).
    val h = health
    val healthPts = if (h == null) 0 else {
        val raw = min(h.successes, 5) - 5 * min(h.failures, 4)
        if (strategy == SourceStrategy.RELIABILITY) (raw * 2).coerceIn(-20, 10) else raw.coerceIn(-20, 5)
    }
    score += healthPts
    if (h != null) parts += if (h.failures == 0 && h.successes > 0) "${h.successes} acertos"
        else if (h.failures > 0) "${h.failures} ${if (h.failures == 1) "falha" else "falhas"}"
        else "sem histórico"

    // Idioma: bate com a pref +10; original primeiro dá +6 ao áudio sem marca.
    val langPts = when {
        langPref == "dub" || langPref == "leg" ->
            if (fact.lang == langPref) 10 else if (fact.lang.isEmpty()) 3 else 0
        originalFirst && fact.lang.isEmpty() -> 6
        fact.lang.isNotEmpty() -> 2
        else -> 3
    }
    score += langPts + if (strategy == SourceStrategy.LANGUAGE && langPts >= 10) 5 else 0
    parts += when (fact.lang) {
        "dub" -> "dublado"
        "leg" -> "legendado"
        else -> "áudio original"
    }

    // Tamanho: 200 MB–8 GB é o esperado; <50 MB é sample/cortado; >20 GB engasga.
    val size = sizeBytesOf(fact.label)
    score += when {
        size in 200_000_000..8_000_000_000 -> 5
        size in 1 until 50_000_000 -> -10
        size > 20_000_000_000 -> -3
        else -> 0
    }
    if (size in 200_000_000..8_000_000_000) parts += "tamanho ok"

    // Debrid pronto vira link direto: +8. Torrent com seeds, +4 no máximo.
    if (fact.debrid >= 2) {
        score += 8
        parts += "debrid pronto"
    }
    if (TorrentMagnet.isMagnet(fact.url)) {
        val seeds = TorrentMagnet.seeders(fact.label)
        score += when {
            seeds >= 20 -> 4
            seeds > 0 -> 2
            else -> -5
        }
        parts += "$seeds seeds"
    }

    // Origem do arquivo: Remux > BluRay > WEB-DL.
    when (releaseKindOf(fact.label)) {
        "Remux" -> { score += 5; parts += "Remux" }
        "BluRay" -> { score += 3; parts += "BluRay" }
        "WEB-DL" -> { score += 1; parts += "WEB-DL" }
    }

    // Acima do teto de qualidade do perfil: penaliza, não some (ainda é tocável).
    if (maxQuality > 0 && fact.rank > maxQuality) {
        score -= 12
        parts += "acima do limite"
    }

    // Última escolha à mão neste título (Lote 17 item 4/6): +6, sem travar a lista.
    if (lastPick != null && lastPick.addon.equals(fact.addon, ignoreCase = true) &&
        (lastPick.qualityRank == 0 || lastPick.qualityRank == fact.rank)
    ) {
        score += 6
        parts += "última escolha"
    }

    if (suspicious) {
        score -= 30
        parts += "suspeita (CAM/sample)"
    }
    return SourceScore(score.coerceIn(0, 100), parts.joinToString(" · "), suspicious)
}

/** Uma fonte com score, posição no ranking e motivo. [index] = posição na lista original. */
data class RankedSource(
    val fact: SourceFacts,
    val index: Int,
    val score: Int,
    val reason: String,
    val rank: Int,
    val suspicious: Boolean,
)

/**
 * Ordena as fontes pelo score — a MESMA ordem no detalhe e na folha do
 * player. Filtros (só escondem, nunca reordenam por fora do score):
 * [langFilter] ("", "dub", "leg", "original"), [hideUnplayable] (codec que
 * o aparelho não toca) e [manualOrder] (addons na ordem escolhida pelo
 * usuário, por cima do ranking — Lote 17 item 3). Desempate final por URL:
 * sem ele, dois scores iguais voltariam na ordem de chegada (parecia sorteio).
 */
fun rankSources(
    facts: List<SourceFacts>,
    health: Map<String, SourceHealth> = emptyMap(),
    preset: SourcePreset = SourcePreset.BALANCED,
    strategy: SourceStrategy = SourceStrategy.QUALITY,
    langPref: String = "any",
    maxQuality: Int = 0,
    manualOrder: List<String> = emptyList(),
    langFilter: String = SOURCE_LANG_ALL,
    hideUnplayable: Boolean = false,
    supportedCodecs: Set<String> = emptySet(),
    lastPick: LastSourcePick? = null,
    originalFirst: Boolean = false,
    runtimeMin: Int = 0,
): List<RankedSource> {
    val manualIdx = manualOrder.withIndex().associate { it.value.lowercase() to it.index }
    val scored = facts.mapIndexed { i, fact ->
        val s = scoreSource(fact, health[sourceHost(fact.url)], preset, strategy, langPref, maxQuality, lastPick, originalFirst, runtimeMin)
        RankedSource(fact, i, s.score, s.reason, 0, s.suspicious)
    }.filter { r ->
        when (langFilter) {
            SOURCE_LANG_DUB -> r.fact.lang == "dub"
            SOURCE_LANG_LEG -> r.fact.lang == "leg"
            SOURCE_LANG_ORIGINAL -> r.fact.lang.isEmpty()
            else -> true
        }
    }.filter { r -> !hideUnplayable || isPlayableOnDevice(r.fact.label, supportedCodecs) }
    val ordered = scored.sortedWith(
        compareBy<RankedSource> { manualIdx[it.fact.addon.lowercase()] ?: Int.MAX_VALUE }
            .thenByDescending { it.score }
            .thenByDescending { it.fact.rank }
            .thenBy { it.fact.probe?.ms ?: Long.MAX_VALUE }
            .thenBy { it.fact.url },
    )
    return ordered.mapIndexed { i, r -> r.copy(rank = i + 1) }
}

/** Os N primeiros do ranking, vivos e do título certo — a "corrida" (Lote 17 item 1). */
fun raceCandidates(ranked: List<RankedSource>, n: Int = 3): List<RankedSource> =
    ranked.filter { !it.fact.dead && !it.fact.wrongTitle }.take(n.coerceAtLeast(1))

// ── Diagnóstico honesto quando não há fonte (Lote 17 item 7) ────────────────

/**
 * Por que não há nada tocável — sem inventar. Os casos: sem fonte nenhuma,
 * só torrents sem debrid, tudo morto, tudo de outro filme, tudo suspeito.
 * O texto vai p/ a tela de detalhe com o botão "Tentar novamente" e um QR
 * com o mesmo diagnóstico (p/ pedir ajuda mostrando a tela).
 */
fun diagnoseNoSources(
    facts: List<SourceFacts>,
    debridActive: Boolean,
    debridLabel: String = "",
): String {
    if (facts.isEmpty()) return "Nenhuma fonte encontrada para este título. Os addons não devolveram nada — toque em Tentar novamente."
    val alive = facts.filter { !it.dead && !it.wrongTitle }
    if (alive.isEmpty()) {
        return if (facts.all { it.dead }) "As fontes não responderam no teste agora. Pode ser a conexão — toque em Tentar novamente."
        else "As fontes encontradas parecem ser de outro filme com nome parecido. Toque em Tentar novamente para buscar de novo."
    }
    val onlyTorrents = alive.all { TorrentMagnet.isMagnet(it.url) }
    if (onlyTorrents && !debridActive) return "Só há torrents para este título — e torrent precisa de debrid para tocar direto. Conecte um debrid em Configurações ou baixe o torrent."
    if (onlyTorrents) return "Só há torrents para este título (${debridLabel.ifBlank { "debrid" }} conectado). Se nenhum tocar, o torrent pode não estar no cache do serviço."
    if (alive.all { isSuspiciousSource(it.label, sizeBytesOf(it.label), it.probe?.sec ?: 0.0, 0) }) return "As únicas cópias são CAM ou sample (qualidade ruim). Toque em Tentar novamente — pode aparecer uma cópia melhor."
    return "Há ${alive.size} ${if (alive.size == 1) "fonte" else "fontes"}, mas nenhuma passou na pré-checagem. Toque em Tentar novamente."
}
