package com.aotv.app.ui.movies

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aotv.app.data.local.ExtAudioPick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

// Ajustes do player (legenda, relógio, som de outra fonte). Tudo que dá para testar sem
// aparelho fica aqui em funções puras. O sincronizador do som está no fim do arquivo.

/** Tamanhos de legenda, na ordem em que o toque passa por eles. */
internal val SUB_SIZES = listOf(0.8f to "Pequena", 1f to "Média", 1.25f to "Grande", 1.5f to "Muito grande")

internal fun subSizeLabel(v: Float) = SUB_SIZES.firstOrNull { it.first == v }?.second ?: "Média"
internal fun nextSubSize(v: Float) = SUB_SIZES[(SUB_SIZES.indexOfFirst { it.first == v } + 1) % SUB_SIZES.size].first

/** 1,35 e 2 e 0,05. Valor absoluto com até duas casas e vírgula. */
internal fun fmtSec(x: Double): String {
    val c = (abs(x) * 100).roundToLong()
    val i = c / 100
    val f = c % 100
    return when {
        f == 0L -> "$i"
        f % 10 == 0L -> "$i,${f / 10}"
        else -> "$i," + "%02d".format(f)
    }
}

/** Texto do atraso do som. Positivo é som atrasado. */
internal fun audioDelayLabel(d: Double) = when {
    abs(d) < 0.005 -> "Sincronia original"
    d > 0 -> "Som atrasado ${fmtSec(d)} s"
    else -> "Som adiantado ${fmtSec(d)} s"
}

/** Valor grande da barra de sincronia, sempre com centésimos (+1,35 s). */
internal fun syncValue(d: Double): String {
    val c = (abs(d) * 100).roundToLong()
    val sign = if (c == 0L) "" else if (d > 0) "+" else "−"
    return sign + "${c / 100}," + "%02d".format(c % 100) + " s"
}

/** Texto do atraso da legenda. Positivo é legenda atrasada (aparece depois). */
internal fun subDelayLabel(d: Double) = when {
    abs(d) < 0.05 -> "Sincronia original"
    d > 0 -> "Legenda atrasada ${fmtSec(d)} s"
    else -> "Legenda adiantada ${fmtSec(d)} s"
}

/** Soma um passo e arredonda em centésimos, para não acumular 0,30000000004. */
internal fun addDelay(d: Double, step: Double) = Math.round((d + step) * 100) / 100.0

/**
 * Passo de quem segura o botão (ou a seta). [rep] conta as repetições. O fino passa a 0,25 s
 * depois de 5 e a 1 s depois de 15. O de 1 s passa a 5 s depois de 6. O de 10 s não muda.
 */
internal fun syncStep(base: Double, rep: Int): Double {
    val s = if (base < 0) -1.0 else 1.0
    return when (abs(base)) {
        0.05 -> s * if (rep > 15) 1.0 else if (rep > 5) 0.25 else 0.05
        1.0 -> s * if (rep > 6) 5.0 else 1.0
        else -> base
    }
}

/** 21h04 no fuso dado. */
internal fun hhmm(epochMs: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
    val c = java.util.Calendar.getInstance(tz).apply { timeInMillis = epochMs }
    return "%02dh%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

/** Hora atual e, com duração conhecida, a hora em que o vídeo acaba na velocidade atual. */
internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Largura preserva a classificação do vídeo quando o filme tem barras de cinema. */
internal fun videoResolutionLabel(width: Int, height: Int): String = when {
    width <= 0 || height <= 0 -> ""
    width >= 3800 -> "4K"
    width >= 2500 -> "2K"
    width >= 1900 -> "1080p"
    width >= 1200 -> "720p"
    else -> "${height}p"
}

internal fun playbackSpeedText(speed: Float): String = (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()).replace('.', ',') + "x"

/**
 * Ganho do Volume Boost (Fase C) em milibéis p/ o `LoudnessEnhancer.setTargetGain`.
 * O nível da pref é um multiplicador de amplitude (1,0–2,0x): ganho em dB =
 * 20·log10(nível), em milibéis ×100. 1,5x ≈ 352 mB; 2,0x ≈ 602 mB.
 * Limitado a 0–1000 mB (teto do efeito). 1,0x ou menos = 0 (sem boost).
 */
internal fun volBoostGainMb(level: Float): Int {
    if (level <= 1f) return 0
    val db = 20 * kotlin.math.log10(level.toDouble())
    return (db * 100).toInt().coerceIn(0, 1000)
}

internal fun clockLine(nowMs: Long, posMs: Long, durMs: Long, speed: Float, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
    val now = hhmm(nowMs, tz)
    if (durMs <= 0 || posMs >= durMs) return now
    val left = ((durMs - posMs) / speed.coerceAtLeast(0.1f)).toLong()
    return "$now  ·  termina às ${hhmm(nowMs + left, tz)}"
}

/**
 * Assinatura de uma fonte para lembrar dela depois. O link muda a cada vez (token), então vale
 * o addon com o nome do espelho, a qualidade e o idioma.
 */
internal fun sourceSig(src: MoviePlayerSource, title: String): String {
    val v = parseSourceLabel(src.label, src.addon, sourceQualityRank(src.label), title)
    return listOf(src.addon, v.server, v.quality, v.language).joinToString("|")
}

/** Fica com as [max] escolhas mais recentes. */
internal fun keepNewest(all: Map<String, ExtAudioPick>, max: Int = 60): Map<String, ExtAudioPick> =
    if (all.size <= max) all
    else all.entries.sortedByDescending { it.value.at }.take(max).associate { it.key to it.value }

/** Por que uma fonte não serve como som, ou "" se serve. */
internal fun extAudioBlock(url: String) =
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true))
        "Link que não é de vídeo direto, não serve como som" else ""

/**
 * Correção de deriva do som. [errMs] positivo é som adiantado em relação à imagem.
 * Devolve null quando a diferença passa de 0,3 s (aí pula direto), senão a velocidade do som
 * com até 4% de correção, em degraus de 0,5%.
 */
internal fun driftSpeed(errMs: Long, speed: Float): Float? {
    if (abs(errMs) > 300) return null
    if (abs(errMs) < 25) return speed
    val c = (errMs / 2500f).coerceIn(-0.04f, 0.04f)
    return Math.round(speed * (1 - c) * 200) / 200f
}

// ── Legenda externa ───────────────────────────────────────────────────────────

/** Uma fala da legenda, em ms. */
data class SubCue(val start: Long, val end: Long, val text: String)

private val CUE_TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{3})""")

/** Lê SRT ou VTT. Marcação (<i>, {\an8}) sai. */
internal fun parseSubs(body: String): List<SubCue> {
    val out = mutableListOf<SubCue>()
    for (block in body.replace("\r", "").split(Regex("\n{2,}"))) {
        val lines = block.trim().lines()
        val i = lines.indexOfFirst { CUE_TIME.containsMatchIn(it) }
        if (i < 0) continue
        val g = CUE_TIME.find(lines[i])!!.groupValues
        fun ms(h: String, m: String, s: String, f: String) =
            ((h.ifBlank { "0" }.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + f.toLong()
        val text = lines.drop(i + 1).joinToString("\n")
            .replace(Regex("<[^>]+>|\\{[^}]*\\}"), "").trim()
        if (text.isNotEmpty()) out += SubCue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), text)
    }
    return out.sortedBy { it.start }
}

/** Fala na tela no instante [posMs] com a legenda atrasada [delayMs]. Falas sobrepostas se juntam com "\n". */
internal fun cueAt(cues: List<SubCue>, posMs: Long, delayMs: Long): String {
    val t = posMs - delayMs
    return cues.filter { it.start <= t && t <= it.end }.joinToString("\n") { it.text }
}

// ── Som de outra fonte ────────────────────────────────────────────────────────

/**
 * Faz [ext] (um ExoPlayer só de áudio) acompanhar [main] pelo relógio do vídeo.
 *
 * O som sai de [ext] no tempo da imagem menos o atraso. A cada 200 ms compara os dois. Acima
 * de 0,3 s de diferença pula, abaixo muda a velocidade do som em poucos por cento até alinhar.
 * Se o som precisar carregar, a imagem espera ([onHold]). O principal fica mudo depois que
 * o som da outra fonte entra.
 */
class ExtAudioSync(
    private val main: ExoPlayer,
    private val ext: ExoPlayer,
    scope: CoroutineScope,
    delaySec: Double,
    private val onHold: (Boolean) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private var delayMs = (delaySec * 1000).roundToLong()
    private var ready = false
    private var held = false
    private var dead = false
    private var lastSeekAt = 0L
    // O som leva uns 300 ms para voltar a sair depois de um pulo com a imagem andando. O pulo
    // mira esse tanto à frente, e o valor se ajusta pelo erro medido depois de cada pulo.
    private var lead = 0L

    private fun target() = main.currentPosition - delayMs

    private fun seekExt() {
        ext.seekTo((target() + if (main.isPlaying) lead else 0L).coerceAtLeast(0L))
        lastSeekAt = System.currentTimeMillis()
    }

    private fun hold(on: Boolean) {
        if (held == on) return
        held = on
        main.playWhenReady = !on
        onHold(on)
    }

    private val mainL = object : Player.Listener {
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (ready && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)) seekExt()
        }
    }

    private val extL = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !ready) {
                if (!ext.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
                    fail("Essa fonte não tem som que o aparelho toque. Tente outra.")
                    return
                }
                ready = true
                android.util.Log.i("ExtAudio", "som pronto em ${ext.currentPosition} ms")
                main.volume = 0f
                seekExt()
                hold(false)
                onReady()
            } else if (state == Player.STATE_READY) hold(false)
            else if (state == Player.STATE_BUFFERING && ready && main.playWhenReady) hold(true)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            fail("Não deu para usar o som dessa fonte. Voltou o som original.")
        }
    }

    private val loop: Job

    init {
        main.addListener(mainL)
        ext.addListener(extL)
        ext.seekTo(target().coerceAtLeast(0L))
        ext.playWhenReady = false
        ext.prepare()
        hold(true) // a imagem espera o som entrar
        loop = scope.launch {
            val t0 = System.currentTimeMillis()
            while (isActive && !dead) {
                if (!ready && System.currentTimeMillis() - t0 > 60_000) {
                    fail("Não deu para usar o som dessa fonte. Voltou o som original.")
                    break
                }
                tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        if (!ready) return
        val t = target()
        val speed = main.playbackParameters.speed
        // Imagem tocando e o som travou carregando depois de um pulo feito com o vídeo parado.
        if (main.isPlaying && !held && ext.playbackState == Player.STATE_BUFFERING) { hold(true); return }
        val play = main.isPlaying && !held && t >= 0
        if (ext.playWhenReady != play) ext.playWhenReady = play
        if (!play) {
            if (t < 0 && ext.currentPosition > 0) ext.seekTo(0)
            return
        }
        if (!ext.isPlaying || System.currentTimeMillis() - lastSeekAt < 1_000) return
        val fix = driftSpeed(ext.currentPosition - t, speed)
        if (fix == null) {
            val err = ext.currentPosition - t
            // Meio passo por vez, um pulo que caiu em dado já baixado não desfaz o aprendizado.
            if (abs(err) < 1_500) lead = (lead - err / 2).coerceIn(0L, 1_500L)
            android.util.Log.i("ExtAudio", "deriva $err ms, pulando com $lead ms de antecipação")
            seekExt()
            return
        }
        if (ext.playbackParameters.speed != fix) ext.playbackParameters = PlaybackParameters(fix)
    }

    /** Novo atraso. Chamado só depois que a pessoa para de mexer, para não cortar o som a cada toque. */
    fun setDelay(sec: Double) {
        val ms = (sec * 1000).roundToLong()
        if (ms == delayMs) return
        delayMs = ms
        if (ready) seekExt()
    }

    private fun fail(msg: String) {
        if (dead) return
        android.util.Log.i("ExtAudio", "falhou $msg")
        release()
        onError(msg)
    }

    /** Desliga o som da outra fonte e devolve o som original. */
    fun release() {
        if (dead) return
        dead = true
        loop.cancel()
        main.removeListener(mainL)
        ext.removeListener(extL)
        runCatching { ext.release() }
        main.volume = 1f
        if (held) { held = false; main.playWhenReady = true; onHold(false) }
    }
}
