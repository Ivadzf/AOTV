package com.aotv.app.ui.movies

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import java.nio.ByteBuffer

/**
 * Atraso do VÍDEO p/ adiantar o som (Lote 17 item 10, v1.27.0).
 *
 * A Media3 1.6.0 NÃO tem API pública de atraso de áudio nem de vídeo
 * (`Player` não tem `setAudioDelay` nem `setVideoDelay`;
 * `PlaybackParameters` só controla velocidade/pitch — conferido no AAR).
 * O atraso positivo do som vive no [AudioDelayProcessor] (buffer de
 * PCM). O adianto do som é o simétrico: segurar os frames de vídeo por
 * [VideoDelayState.delayUs], então a imagem sai atrasada e o som fica
 * adiantado em relação a ela.
 *
 * Mecanismo (provado no fonte da 1.6.0, não uma API de atraso):
 * [MediaCodecRenderer.drainOutputBuffer] reapresenta o MESMO buffer a
 * [MediaCodecRenderer.processOutputBuffer] ("possibly multiple times",
 * comentário no fonte) quando ele devolve `false`. [VideoDelayRenderer]
 * devolve `false` p/ cada frame cuja hora de exibição ainda não chegou
 * (`positionUs < frameTimeUs + delayUs`) e só entrega o frame ao `super`
 * quando chega a hora. O frame não é descartado nem decodificado de
 * novo; o `isReady()` do renderer continua true enquanto há buffer
 * pendente, então não aparece spinner de buffering.
 *
 * Por que o truque de "mentir o `positionUs`" NÃO funcionaria aqui: o
 * player SEMPRE usa o caminho `VideoSink` (o app chama
 * `exoPlayer.setVideoEffects(...)` antes do `prepare()` com lista
 * não-nula, mesmo vazia — ver `buildColorEffects` — e o `onEnabled` do
 * renderer cria o sink quando `videoEffects != null`). No caminho do
 * sink o `positionUs` não decide o timing (o sink decide sozinho); o
 * segurar-por-`false` funciona porque é agnóstico ao sink, e o ramo do
 * sink não tem descarte por atraso — só o ramo sem sink tem.
 *
 * Limites honestos (a UI diz isso; não há como contornar sem reescrever
 * o pipeline):
 * - No início, a imagem só aparece `delay` depois do som: o primeiro
 *   frame fica segurado e a tela fica preta até lá. Com passos finos
 *   (0,05–1 s) é imperceptível; com 10 s são 10 s de tela preta.
 * - A cauda do vídeo termina `delay` depois do som: o último frame só
 *   é solto quando o relógio alcança a hora dele, e o EOS só é
 *   desenfileirado depois que o frame segurado é processado
 *   (`drainOutputBuffer` só chama `dequeueOutputBufferIndex` quando não
 *   há buffer pendente).
 * - Aumentar o delay no meio da reprodução congela o vídeo até ele cair
 *   p/ trás; diminuir solta os frames segurados na hora.
 * - Seek/flush do codec descarta o frame segurado (não há estado p/
 *   resetar — o renderer é stateless; o único estado é o [delayUs] do
 *   [VideoDelayState], que sobrevive a seeks e trocas de faixa).
 * - Com delay 0 o renderer delega 100% ao `super`: quem nunca usa o
 *   adianto tem comportamento idêntico ao padrão, sem custo.
 *
 * O renderer é montado pela [DelayRenderersFactory] (ver PlayerEngine.kt)
 * com a mesma cadeia de Builder do `DefaultRenderersFactory` da 1.6.0 —
 * só muda a classe instanciada no final.
 */
@OptIn(UnstableApi::class)
class VideoDelayState {
    /** Atraso do vídeo em µs (0 = sem atraso). Só positivo; o sinal fica no `audioDelay` da tela. */
    @Volatile
    var delayUs: Long = 0L
        private set

    /** Novo atraso em ms (0 a 10 s, o mesmo teto do atraso de áudio). Vale na hora. */
    fun setDelayMs(ms: Long) {
        delayUs = ms.coerceIn(0L, (OWN_AUDIO_DELAY_MAX_SEC * 1000).toLong()) * 1000
    }
}

/** Renderer de vídeo que segura os frames p/ atrasar a imagem (ver o doc acima). */
@OptIn(UnstableApi::class)
class VideoDelayRenderer(
    private val state: VideoDelayState,
    builder: MediaCodecVideoRenderer.Builder,
) : MediaCodecVideoRenderer(builder) {

    @OptIn(UnstableApi::class)
    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format,
    ): Boolean {
        val delayUs = state.delayUs
        if (delayUs <= 0L || isDecodeOnlyBuffer) {
            return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format)
        }
        // Hora de mídia do frame, na mesma base do `positionUs`: o super
        // usa exatamente `bufferPresentationTimeUs - getOutputStreamOffsetUs()`.
        val frameTimeUs = bufferPresentationTimeUs - getOutputStreamOffsetUs()
        if (positionUs < frameTimeUs + delayUs) {
            // Segura: o `drainOutputBuffer` reapresenta o mesmo buffer na
            // próxima chamada — o frame não é perdido nem redecodificado.
            return false
        }
        return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format)
    }
}
