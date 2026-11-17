package com.aotv.app.data.party

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Medidor de voz da Watch Party (Lote 19, v1.29.0) — o "anel em quem fala".
 *
 * O WebRTC não expõe o nível do microfone pela API pública, então o nível
 * é medido aqui, em paralelo, com um [AudioRecord] só de leitura
 * (VOICE_COMMUNICATION, 16 kHz, mono): RMS do trecho → 0–100, com
 * suavização (o anel não pisca a cada sílaba). O [AudioRecord] não
 * grava nada nem sai do aparelho — só vira número, e o número viaja
 * no canal de dados (SPEAKING) p/ os outros participantes.
 *
 * Honesto: isto é detecção por ENERGIA, não por fala de verdade — barulho
 * alto (TV alta, porta batendo) acende o anel também. O limiar é
 * [PARTY_SPEAKING_THRESHOLD] (25). Se o microfone não abrir (sem
 * permissão, em uso), [start] devolve false e o anel fica apagado — sem
 * travar a sala.
 */
class PartySpeakingMeter {
    /** Nível atual do microfone (0–100). */
    private val _level = MutableStateFlow(0)
    val level: StateFlow<Int> = _level.asStateFlow()

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Começa a medir. false quando o microfone não abriu. */
    fun start(): Boolean {
        if (running) return true
        return runCatching {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) return false
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { record.release() }
                return false
            }
            running = true
            thread = Thread({
                val buf = ShortArray(320) // 20 ms a 16 kHz
                var smooth = 0f
                runCatching { record.startRecording() }
                while (running) {
                    val read = runCatching { record.read(buf, 0, buf.size) }.getOrDefault(-1)
                    if (read <= 0) {
                        runCatching { Thread.sleep(20) }
                        continue
                    }
                    var sum = 0.0
                    for (i in 0 until read) {
                        val v = buf[i].toDouble() / Short.MAX_VALUE
                        sum += v * v
                    }
                    val rms = sqrt(sum / read).toFloat()
                    // RMS 0,25 (voz normal perto do microfone) vira 100.
                    val instant = (rms / 0.25f * 100f).coerceIn(0f, 100f)
                    smooth = if (instant > smooth) instant else smooth * 0.7f + instant * 0.3f
                    _level.value = smooth.toInt()
                }
                runCatching { record.stop() }
                runCatching { record.release() }
                _level.value = 0
            }, "party-speaking").apply { isDaemon = true; start() }
            true
        }.getOrDefault(false)
    }

    /** Para de medir (o nível volta a 0). */
    fun stop() {
        running = false
        runCatching { thread?.join(500) }
        thread = null
        _level.value = 0
    }

    /** true quando o nível atual conta como "falando". */
    fun isSpeaking(level: Int = _level.value): Boolean = level >= PARTY_SPEAKING_THRESHOLD
}
