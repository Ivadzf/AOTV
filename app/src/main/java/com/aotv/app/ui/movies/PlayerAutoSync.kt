package com.aotv.app.ui.movies

import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.math.abs

// Sincronização automática da legenda (Lote 9 item 1, v1.19.0).
//
// O QUE DÁ P/ MEDIR DE VERDADE: a diferença entre os tempos da legenda
// EXTERNA (SRT/VTT baixado, [externalCues]) e os tempos da legenda
// EMBUTIDA no vídeo (a que vem muxada no arquivo e, por definição, está
// em sincronia com a fala). O player amostra as falas embutidas enquanto
// o vídeo toca ([sampleEmbeddedCues]) e casa cada fala com a fala de
// mesmo texto na legenda externa ([estimateSubDelay]).
//
// O QUE NÃO DÁ P/ MEDIR (e por isso NÃO é feito): correlação com picos de
// áudio/fala. O ExoPlayer não entrega o PCM do áudio sem um AudioProcessor
// próprio no renderer — fora do escopo — e nenhum valor de atraso é
// chutado: sem casamento suficiente entre as duas legendas, NADA é
// aplicado e o ajuste manual ± continua valendo.
//
// Regras honestas da medição (ver [estimateSubDelay]):
// - casa por texto normalizado (minúsculas, sem pontuação/espaços extras);
// - precisa de pelo menos 3 falas casadas, senão não há mediana confiável;
// - a mediana dos desvios tem que caber em ±10 s; fora disso é outro
//   arquivo/versão e não se aplica nada;
// - desvio medido menor que 0,5 s = "já está sincronizada", não muda nada;
// - o atraso aplicado SUBSTITUI o ajuste manual atual (o desvio medido é
//   absoluto: tempos do arquivo externo contra tempos do vídeo, e não
//   depende do atraso que já estava aplicado).
//
// Limites da amostragem: a fala embutida só aparece em `currentCues` no
// instante em que está na tela, então o instante registrado é a posição
// do primeiro poll que a viu (erro de até 500 ms por fala). A mediana
// sobre 3+ falas dilui esse erro. A amostragem precisa do vídeo TOCANDO
// com a faixa embutida ligada — ela é ligada só durante a medição e
// devolvida ao estado anterior depois (a legenda externa continua sendo
// a desenhada pelo app).

/** Estado da medição p/ a tela do player mostrar. */
enum class AutoSyncState { IDLE, MEASURING }

/** Mínimo de falas casadas p/ a mediana valer alguma coisa. */
const val AUTOSYNC_MIN_MATCHES = 3

/** Desvio abaixo disto é "já sincronizada" (ruído da amostragem). */
const val AUTOSYNC_MIN_OFFSET_MS = 500L

/** Desvio acima disto é outro arquivo/versão — não se aplica nada. */
const val AUTOSYNC_MAX_OFFSET_MS = 10_000L

/** Fim da amostragem: falas distintas suficientes ou tempo esgotado. */
const val AUTOSYNC_TARGET_CUES = 10
const val AUTOSYNC_MAX_WAIT_MS = 90_000L
const val AUTOSYNC_POLL_MS = 500L

/** Resultado da medição: quantas falas casaram, a mediana do desvio e o ajuste a aplicar. */
data class AutoSyncEstimate(
    val matchedCues: Int,
    val medianOffsetMs: Long,
    /** Ajuste em segundos a aplicar em `subDelay` (negativo = adiantar a legenda). */
    val adjustmentSec: Double,
)

/** Texto de fala normalizado p/ casar embutida com externa (pontuação e caixa não importam). */
internal fun normalizeCueText(text: String): String =
    text.lowercase()
        .replace(Regex("[^a-z0-9à-ú ]"), " ")
        .split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        .trim()

/**
 * Estima o atraso da legenda externa a partir das falas embutidas amostradas.
 *
 * [embedded] são as falas do vídeo (start = posição em que foram vistas);
 * [external] são as falas do arquivo externo. Casa por texto normalizado
 * (primeira ocorrência de cada texto na externa) e tira a mediana dos
 * desvios `externa.start - embutida.start`. Null quando casa menos que
 * [AUTOSYNC_MIN_MATCHES] falas ou a mediana passa de ±10 s.
 */
internal fun estimateSubDelay(embedded: List<SubCue>, external: List<SubCue>): AutoSyncEstimate? {
    if (embedded.isEmpty() || external.isEmpty()) return null
    val extByText = LinkedHashMap<String, SubCue>()
    for (cue in external) {
        val key = normalizeCueText(cue.text)
        if (key.isNotBlank() && key !in extByText) extByText[key] = cue
    }
    val offsets = mutableListOf<Long>()
    for (cue in embedded) {
        val key = normalizeCueText(cue.text)
        if (key.isBlank()) continue
        val match = extByText[key] ?: continue
        offsets += match.start - cue.start
    }
    if (offsets.size < AUTOSYNC_MIN_MATCHES) return null
    val sorted = offsets.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    if (abs(median) > AUTOSYNC_MAX_OFFSET_MS) return null
    // A externa aparece `median` ms DEPOIS da fala: p/ alinhar, adianta a
    // legenda nesse tanto (ajuste negativo). Arredonda em décimos.
    val adjustment = -(median / 100.0).let { Math.round(it) / 10.0 }
    return AutoSyncEstimate(offsets.size, median, adjustment)
}

/**
 * Amostra as falas da legenda embutida enquanto o vídeo toca: a cada
 * [AUTOSYNC_POLL_MS] lê `player.currentCues` e anota a posição da primeira
 * vez que cada texto aparece. Para com [AUTOSYNC_TARGET_CUES] falas
 * distintas ou quando [AUTOSYNC_MAX_WAIT_MS] passa. Sem fala na tela,
 * devolve o que juntou (pode ser vazio — aí a estimativa falha e nada é
 * aplicado). O vídeo precisa estar tocando; pausado, a mesma fala se
 * repete e nada de novo aparece.
 */
suspend fun sampleEmbeddedCues(player: Player): List<SubCue> {
    val seen = LinkedHashMap<String, Long>()
    val startMs = System.currentTimeMillis()
    while (System.currentTimeMillis() - startMs < AUTOSYNC_MAX_WAIT_MS && seen.size < AUTOSYNC_TARGET_CUES) {
        val pos = runCatching { player.currentPosition }.getOrDefault(0L)
        val cues = runCatching { player.currentCues.cues }.getOrDefault(emptyList())
        for (cue in cues) {
            val text = cue.text?.toString().orEmpty()
            val key = normalizeCueText(text)
            if (key.isNotBlank() && key !in seen) seen[key] = pos
        }
        if (seen.size >= AUTOSYNC_TARGET_CUES) break
        delay(AUTOSYNC_POLL_MS)
    }
    return seen.entries.sortedBy { it.value }.map { (text, start) -> SubCue(start, start, text) }
}
