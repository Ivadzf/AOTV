package com.aotv.app.ui.movies

import android.content.Context
import android.content.SharedPreferences
import com.aotv.app.data.remote.IntroDbSegments
import com.aotv.app.data.remote.IntroDbSegment
import org.json.JSONObject

// ── Pular trechos — modelo p/ a barra, alvo do botão e auto-skip aprendido ──
// (Lote 16, v1.26.0). Este arquivo NÃO desenha nada: entrega os dados p/ o
// coordenador plugar no MoviePlayerScreen.kt (botão "Pular abertura" e
// barra de progresso existentes). A fonte dos trechos é o IntroDbService
// (já filtrado por confiança) — a sobrecarga de buildProgressMarkers que
// aceita com.aotv.app.data.remote.SkipSegments existe p/ o caminho antigo
// (SkipSegmentsService, v1.8.1, sem filtro) continuar funcionando.
//
// COMO PLUGAR (no MoviePlayerScreen.kt, onde hoje se lê skipSegments):
//   val segs = introDb.get(imdb, type == "SERIES", curSeason, curEpisode)
//   val markers = buildProgressMarkers(segs, chapters = emptyList(), durationMs)
//       // capítulos: ver o GAP abaixo — hoje sempre emptyList().
//   val target = skipTargetAt(position, segs, hasNextEpisode)
//       // target?.label ("Pular abertura", "Ir para pós-créditos"...),
//       // target?.targetMs (seek), target?.goNext (vai p/ o próximo ep).
//   val notice = postCreditsNotice(segs, position)
//       // != null dentro do encerramento quando há pós-créditos: mostrar
//       // o aviso em vez de pular direto (ver [postCreditsNotice]).
//   // Auto-skip aprendido (desligado por padrão):
//   val learned = LearnedAutoSkipStore.fromContext(context)
//   learned.noteManualSkip(seriesKey)   // quando o usuário toca em Pular
//   if (learned.shouldOfferAutoSkip(seriesKey)) { /* oferecer o toggle */ }
//   learned.setAutoSkip(seriesKey, true) // só com o OK do usuário
//   val auto = learned.shouldAutoSkip(seriesKey, globalAutoSkipEnabled)
//
// ── GAP EXPLÍCITO — capítulos MKV ─────────────────────────────────────────
// O Media3 1.6.0 do projeto NÃO expõe capítulos de MKV p/ o app. Verificado
// nos AARs 1.6.0 do cache local do Gradle em 2026-11-16:
//  - media3-common: nenhuma classe de capítulo; Timeline.Window, Timeline
//    .Period e MediaMetadata não têm nenhum campo de capítulo (só existe
//    MEDIA_TYPE_AUDIO_BOOK_CHAPTER, que não tem nada a ver com MKV);
//  - media3-extractor: as únicas classes "Chapter" são as do ID3
//    (ChapterFrame/ChapterTocFrame, metadados de MP3 via onMetadata);
//    a classe MatroskaExtractor não contém nenhuma string "Chapter" —
//    ela nem lê os capítulos do container;
//  - media3-exoplayer: nenhuma classe de capítulo.
// Ou seja: não há de onde tirar os capítulos sem escrever um parser de
// Matroska do zero (fora do escopo — e o briefing proíbe inventar parser
// de MKV). O que ESTÁ pronto aqui: o modelo [ChapterMark], o snap
// [snapToChapter] e os marcadores de capítulo em [buildProgressMarkers] —
// tudo puro e testável; basta o coordenador passar a lista quando (e se)
// alguma fonte de capítulos existir no futuro. Até lá, chapters fica vazio
// e a barra só marca intro/recap/créditos da IntroDB.

/** Tipo de trecho marcado na barra de progresso. */
enum class SkipKind { RECAP, INTRO, OUTRO, POST_CREDITS, CHAPTER }

/** Uma divisão da barra de progresso (item 5): trecho com início, fim e tipo. */
data class ProgressMarker(
    val startMs: Long,
    val endMs: Long,
    val kind: SkipKind,
    val label: String,
)

/** Um capítulo do arquivo (quando houver fonte — ver o GAP no topo). */
data class ChapterMark(val startMs: Long, val title: String = "")

/** Alvo do botão de pular trechos na posição atual. */
data class SkipUiTarget(
    val label: String,
    val targetMs: Long,
    val kind: SkipKind,
    /** true = o alvo é o próximo episódio (pular pós-créditos no fim). */
    val goNext: Boolean = false,
)

/** Margem p/ o botão aparecer um pouco antes do trecho começar (igual ao player atual). */
const val SKIP_ENTER_EARLY_MS = 2000L

/** Margem p/ o botão sumir um pouco antes do trecho acabar. */
const val SKIP_EXIT_EARLY_MS = 1000L

/** Aviso de pós-créditos (ver [postCreditsNotice]). */
const val POST_CREDITS_NOTICE = "Tem cena pós-créditos — fica até o fim"

/**
 * Marcadores da barra de progresso (item 5): trechos da IntroDB +
 * capítulos, ordenados por início. Segmentos fora da duração do arquivo
 * saem (são de outro corte — mesma regra do merge da v1.13.0); fim além
 * da duração é aparado; o que sobra com menos de 3 s sai. Quando o
 * encerramento se sobrepõe à cena pós-créditos, ele é aparado até o
 * início dela — a barra nunca sugere pular por cima de uma cena ainda
 * não vista. Sem duração conhecida (durationMs <= 0), vale só o corte
 * de 3 s. Capítulos viram marcadores do início de um até o início do
 * próximo (o último vai até a duração, quando conhecida).
 */
fun buildProgressMarkers(
    segments: IntroDbSegments,
    chapters: List<ChapterMark>,
    durationMs: Long,
): List<ProgressMarker> {
    val out = mutableListOf<ProgressMarker>()
    fun add(seg: IntroDbSegment?, kind: SkipKind, label: String) {
        if (seg == null) return
        val start = seg.startMs
        var end = seg.endMs
        if (durationMs > 0) {
            if (start >= durationMs) return
            end = minOf(end, durationMs)
        }
        if (end - start < 3000L) return
        out += ProgressMarker(start, end, kind, label)
    }
    add(segments.recap, SkipKind.RECAP, "Resumo")
    add(segments.intro, SkipKind.INTRO, "Abertura")
    add(segments.outro, SkipKind.OUTRO, "Encerramento")
    add(segments.postCredits, SkipKind.POST_CREDITS, "Pós-créditos")
    // Pós-créditos tem prioridade na sobreposição (mesma regra do merge):
    // o encerramento termina onde a cena começa.
    val pc = out.firstOrNull { it.kind == SkipKind.POST_CREDITS }
    if (pc != null) {
        val idx = out.indexOfFirst { it.kind == SkipKind.OUTRO }
        if (idx >= 0) {
            val outro = out[idx]
            if (outro.endMs > pc.startMs) {
                if (pc.startMs - outro.startMs < 3000L) out.removeAt(idx)
                else out[idx] = outro.copy(endMs = pc.startMs)
            }
        }
    }
    val sortedChapters = chapters.filter { it.startMs >= 0 && (durationMs <= 0 || it.startMs < durationMs) }
        .sortedBy { it.startMs }
    for (i in sortedChapters.indices) {
        val start = sortedChapters[i].startMs
        val end = sortedChapters.getOrNull(i + 1)?.startMs ?: durationMs
        if (end <= 0 || end - start < 3000L) continue
        val title = sortedChapters[i].title.takeIf { it.isNotBlank() } ?: "Capítulo ${i + 1}"
        out += ProgressMarker(start, end, SkipKind.CHAPTER, title)
    }
    return out.sortedBy { it.startMs }
}

/** Mesma construção, a partir do resultado do SkipSegmentsService (v1.8.1,
 *  sem filtro de confiança). Os segmentos já vêm aparados p/ a duração lá;
 *  aqui só se reaplica o corte de 3 s e a prioridade da pós-créditos. */
fun buildProgressMarkers(
    segments: com.aotv.app.data.remote.SkipSegments,
    chapters: List<ChapterMark>,
    durationMs: Long,
): List<ProgressMarker> {
    fun conv(seg: com.aotv.app.data.remote.SkipSegment?): IntroDbSegment? {
        if (seg == null) return null
        return IntroDbSegment(seg.start, seg.end, 1.0, 1)
    }
    val asIntroDb = IntroDbSegments(
        imdbId = "",
        isSeries = false,
        season = 0,
        episode = 0,
        recap = conv(segments.recap),
        intro = conv(segments.intro),
        outro = conv(segments.outro),
        postCredits = conv(segments.postCredits),
    )
    return buildProgressMarkers(asIntroDb, chapters, durationMs)
}

/**
 * Chapter snap (item 2): o capítulo mais próximo da posição, quando ele
 * está a até [thresholdMs] (padrão 10 s). Null sem capítulos (o caso de
 * hoje — ver o GAP no topo) ou sem capítulo perto. O coordenador usa no
 * arrastar da barra: soltou perto de um capítulo, encaixa nele.
 */
fun snapToChapter(positionMs: Long, chapters: List<ChapterMark>, thresholdMs: Long = 10_000L): Long? {
    var best: Long? = null
    var bestDist = Long.MAX_VALUE
    for (ch in chapters) {
        val dist = kotlin.math.abs(ch.startMs - positionMs)
        if (dist <= thresholdMs && dist < bestDist) {
            bestDist = dist
            best = ch.startMs
        }
    }
    return best
}

/**
 * Trecho pulável ativo na posição atual, ou null. Ordem: resumo, abertura,
 * encerramento (vira "Ir para pós-créditos" quando há cena depois),
 * pós-créditos (vai p/ o próximo episódio, se houver). Mesma ordem e
 * mesmas margens do player atual — só que sobre os segmentos JÁ filtrados
 * da IntroDB, então um trecho de baixa confiança nunca vira botão.
 */
fun skipTargetAt(positionMs: Long, segments: IntroDbSegments, hasNextEpisode: Boolean): SkipUiTarget? {
    fun inside(seg: IntroDbSegment?): Boolean {
        if (seg == null) return false
        return positionMs >= seg.startMs - SKIP_ENTER_EARLY_MS && positionMs < seg.endMs - SKIP_EXIT_EARLY_MS
    }
    val recap = segments.recap
    if (inside(recap)) return SkipUiTarget("Pular resumo", recap!!.endMs, SkipKind.RECAP)
    val intro = segments.intro
    if (inside(intro)) return SkipUiTarget("Pular abertura", intro!!.endMs, SkipKind.INTRO)
    val outro = segments.outro
    if (inside(outro)) {
        val pc = segments.postCredits
        if (pc != null) return SkipUiTarget("Ir para pós-créditos", pc.startMs, SkipKind.OUTRO)
        return SkipUiTarget("Pular encerramento", outro!!.endMs, SkipKind.OUTRO)
    }
    val postCredits = segments.postCredits
    if (inside(postCredits)) {
        return SkipUiTarget("Pular pós-créditos", postCredits!!.endMs, SkipKind.POST_CREDITS, goNext = hasNextEpisode)
    }
    return null
}

/**
 * Aviso de pós-créditos (item 1): dentro do encerramento, quando existe
 * cena pós-créditos, o player NÃO pula direto — avisa ("Tem cena
 * pós-créditos — fica até o fim") e deixa o usuário decidir. Fora do
 * encerramento, ou sem cena, null (nada a avisar).
 */
fun postCreditsNotice(segments: IntroDbSegments, positionMs: Long): String? {
    val outro = segments.outro ?: return null
    if (segments.postCredits == null) return null
    if (positionMs < outro.startMs - SKIP_ENTER_EARLY_MS || positionMs >= outro.endMs - SKIP_EXIT_EARLY_MS) return null
    return POST_CREDITS_NOTICE
}

/** Chave de título p/ o aprendizado por série ("SERIES:1396" / "MOVIE:27205"). */
fun learnedSkipKey(type: String, tmdbId: Int): String = "${type.uppercase()}:$tmdbId"

/**
 * Auto-skip aprendido por série (item 1): conta quantas vezes o usuário
 * pulou a intro de cada série à mão e guarda o opt-in. DESLIGADO por
 * padrão — nada pula sozinho sem o usuário aceitar. Regra documentada:
 * com [LEARN_THRESHOLD] (3) pulos manuais na mesma série, o app pode
 * OFERECER o auto-skip p/ ela ([shouldOfferAutoSkip]); só depois do OK
 * ([setAutoSkip]) o [shouldAutoSkip] libera o pulo automático — e ainda
 * assim só junto com o toggle global do player (pref_player_auto_skip).
 * Guardado em SharedPreferences ("aotv_prefs", chave [STORE_KEY]), no
 * máximo [MAX_SERIES] séries (as mais recentes); sem credencial, sem rede.
 */
class LearnedAutoSkipStore(private val prefs: SharedPreferences) {

    companion object {
        const val STORE_KEY = "skip_learned_v1"

        /** Pulos manuais numa série p/ o app oferecer o auto-skip dela. */
        const val LEARN_THRESHOLD = 3

        const val MAX_SERIES = 60

        fun fromContext(context: Context): LearnedAutoSkipStore =
            LearnedAutoSkipStore(
                context.applicationContext.getSharedPreferences("aotv_prefs", Context.MODE_PRIVATE),
            )
    }

    private data class Entry(val skips: Int, val auto: Boolean, val at: Long)

    private fun read(): MutableMap<String, Entry> {
        val out = LinkedHashMap<String, Entry>()
        val raw = prefs.getString(STORE_KEY, null) ?: return out
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return out
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optJSONObject(k) ?: continue
            out[k] = Entry(v.optInt("s", 0), v.optBoolean("a", false), v.optLong("t", 0L))
        }
        return out
    }

    private fun write(map: Map<String, Entry>) {
        val obj = JSONObject()
        val entries = map.entries.sortedByDescending { it.value.at }.take(MAX_SERIES)
        for ((k, v) in entries) {
            obj.put(k, JSONObject().put("s", v.skips).put("a", v.auto).put("t", v.at))
        }
        prefs.edit().putString(STORE_KEY, obj.toString()).apply()
    }

    /** O usuário pulou a intro à mão nesta série (chamar no onClick do botão). */
    fun noteManualSkip(seriesKey: String) {
        if (seriesKey.isBlank()) return
        val map = read()
        val cur = map[seriesKey]
        val now = System.currentTimeMillis()
        if (cur == null) map[seriesKey] = Entry(1, false, now)
        else map[seriesKey] = cur.copy(skips = cur.skips + 1, at = now)
        write(map)
    }

    /** Quantos pulos manuais já foram registrados nesta série. */
    fun manualSkips(seriesKey: String): Int = read()[seriesKey]?.skips ?: 0

    /** Já dá p/ oferecer o auto-skip desta série (3+ pulos, ainda desligado). */
    fun shouldOfferAutoSkip(seriesKey: String): Boolean {
        val e = read()[seriesKey] ?: return false
        return e.skips >= LEARN_THRESHOLD && !e.auto
    }

    /** Liga/desliga o auto-skip desta série (só com o OK do usuário). */
    fun setAutoSkip(seriesKey: String, enabled: Boolean) {
        if (seriesKey.isBlank()) return
        val map = read()
        val cur = map[seriesKey]
        val now = System.currentTimeMillis()
        if (cur == null) map[seriesKey] = Entry(0, enabled, now)
        else map[seriesKey] = cur.copy(auto = enabled, at = now)
        write(map)
    }

    /** Auto-skip desta série está ligado (opt-in do usuário). */
    fun isAutoSkipEnabled(seriesKey: String): Boolean = read()[seriesKey]?.auto ?: false

    /**
     * Pode pular sozinho nesta série: toggle global do player LIGADO e
     * opt-in desta série LIGADO. Sem um dos dois, false.
     */
    fun shouldAutoSkip(seriesKey: String, globalAutoSkipEnabled: Boolean): Boolean =
        globalAutoSkipEnabled && isAutoSkipEnabled(seriesKey)
}
