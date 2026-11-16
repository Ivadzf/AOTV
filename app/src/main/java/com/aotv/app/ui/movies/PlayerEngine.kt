package com.aotv.app.ui.movies

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.google.android.gms.cast.CastMediaControlIntent
import java.nio.ByteBuffer

/**
 * Motor do player — Lote 4 (v1.12.0), itens 1–3: player externo, Cast e decoder.
 *
 * ── Libmpv: NÃO implementado nesta versão, de propósito ──────────────────
 * Um segundo player interno via Libmpv exigiria o mpv-android, que NÃO é
 * publicado como artefato Maven Central: a via oficial é o AAR do GitHub
 * Releases (ou JitPack `com.github.mpv-android:mpv-android`), um binário
 * externo com .so nativos por ABI (+~15–25 MB no APK), ciclo de vida
 * próprio de Surface (MPVLib) e build NDK — fora do que o CI atual
 * (arm64-only, sem NDK) compila com segurança. Também não há como testar
 * no celular do Davi sem um build real do CI. Por isso o Lote 4 entrega
 * só players EXTERNOS (item 1): o sistema escolhe o app instalado (VLC,
 * MX Player…). Se um dia o mpv entrar, ele vive aqui, ao lado do
 * [decoderRenderersFactory], como um segundo [DecoderMode]-like — nunca
 * espalhado pela tela do player.
 */

/**
 * Decoder de vídeo do ExoPlayer (pref `pref_player_decoder`, por perfil).
 *
 * O que cada modo faz DE VERDADE no app (Media3 1.6.0, sem extensão FFmpeg):
 * - [HW]: fábrica padrão. Decodificação por hardware (MediaCodec do
 *   aparelho). Se o codec de hardware falhar ao iniciar, o erro é fatal e
 *   o watchdog troca de fonte (v1.8.1).
 * - [HW_FALLBACK]: igual ao HW, mas com `setEnableDecoderFallback(true)`:
 *   se o decoder de hardware falhar ao iniciar, o ExoPlayer tenta OUTRO
 *   decoder de plataforma p/ o mesmo MIME (ex.: outro OMX) antes de
 *   desistir. NÃO é decodificação por software: não há fallback p/ CPU
 *   aqui, só p/ outro decoder de hardware do aparelho.
 *
 * NÃO há modo SW de verdade: decodificar por software exigiria a extensão
 * FFmpeg do Media3 (`media3-exoplayer-ffmpeg`), que NÃO está no
 * build.gradle.kts — ela é pesada (binários FFmpeg nativos por ABI, MBs
 * no APK, build NDK no CI) e nenhum modo inventado aqui mudaria nada sem
 * ela: `EXTENSION_RENDERER_MODE_PREFER` sem extensão instalada é no-op.
 * Por isso o controle só oferece os dois modos que mudam comportamento
 * de verdade; o SW fica como follow-up (avaliar a extensão FFmpeg).
 */
enum class DecoderMode(val id: String, val label: String) {
    HW("hw", "Hardware (HW)"),
    HW_FALLBACK("hw_fallback", "Hardware com fallback");

    companion object {
        fun fromId(id: String): DecoderMode =
            entries.firstOrNull { it.id == id } ?: HW
    }
}

/**
 * Fábrica de renderers do ExoPlayer respeitando a pref de decoder.
 * Sem extensão FFmpeg instalada, o modo de extensão fica OFF nos dois
 * modos (ligar PREFER seria fingir que existe decoder de software).
 * A pref só vale na CRIAÇÃO do player (a sessão cria o ExoPlayer 1x);
 * trocar de fonte/episódio reusa o mesmo player e não recria nada.
 * Mudar a pref em Configurações vale a partir da próxima reprodução.
 *
 * Com [delayProcessor] (Lote 14 item 3, v1.24.0), a fábrica vira uma
 * subclasse que sobrescreve [DefaultRenderersFactory.buildAudioSink]
 * p/ plugar o processador de atraso no [DefaultAudioSink] — a 1.6.0 NÃO
 * tem `setAudioProcessors` na fábrica nem `setAudioSink` no
 * `ExoPlayer.Builder` (conferido no AAR: só o `DefaultAudioSink.Builder`
 * aceita processadores). O `buildAudioSink` padrão da 1.6.0 só monta o
 * sink com float/playback-params, então a sobrescrita preserva tudo.
 *
 * Com [videoDelay] (Lote 17 item 10, v1.27.0), a mesma fábrica
 * sobrescreve [DefaultRenderersFactory.buildVideoRenderers] p/ trocar o
 * renderer de vídeo padrão por [VideoDelayRenderer], que segura os
 * frames p/ atrasar o vídeo (adiantar o som) — ver o doc da classe. A
 * troca só acontece com extensão OFF: com renderers de extensão o
 * caminho é o da fábrica-base (que usa reflection) e o atraso de vídeo
 * não vale. Sem [videoDelay] o caminho de vídeo é o padrão. O
 * `buildSecondaryVideoRenderer` (pré-aquecimento) NÃO é sobrescrito: o
 * app nunca liga o pré-aquecimento (default desligado), então ele nunca
 * é chamado — se um dia ligar, a fábrica-base montaria o renderer
 * padrão sem atraso de vídeo p/ o item pré-aquecido.
 */
@OptIn(UnstableApi::class)
fun decoderRenderersFactory(context: Context, mode: DecoderMode, delayProcessor: AudioDelayProcessor? = null, videoDelay: VideoDelayState? = null): DefaultRenderersFactory {
    val base: DefaultRenderersFactory =
        if (delayProcessor != null || videoDelay != null) DelayRenderersFactory(context, delayProcessor, videoDelay)
        else DefaultRenderersFactory(context)
    return base
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        .setEnableDecoderFallback(mode == DecoderMode.HW_FALLBACK)
}

/**
 * Fábrica que pluga o [AudioDelayProcessor] no sink de áudio (Lote 14
 * item 3) e o [VideoDelayRenderer] no lugar do renderer de vídeo padrão
 * (Lote 17 item 10). Cada sobrescrita só vale quando o respectivo
 * parâmetro não é null — sem ele, delega p/ a fábrica-base.
 *
 * O renderer de vídeo é montado com a MESMA cadeia de Builder do
 * `DefaultRenderersFactory.buildVideoRenderers` da 1.6.0 (ver
 * DefaultRenderersFactory.java L790–812): o construtor público
 * `MediaCodecVideoRenderer.Builder(context)` e os mesmos setters. Os
 * dois últimos (`parseAv1SampleDependencies` e `lateThreshold...`) são
 * campos privados da fábrica-base que o app nunca muda (defaults:
 * `false` e `C.TIME_UNSET` — ver o construtor da fábrica, L222–236),
 * então são reproduzidos com os valores default; os demais vêm dos
 * parâmetros que a fábrica-base recebe. Só muda a classe instanciada
 * no final: `VideoDelayRenderer(state, builder)` em vez de
 * `builder.build()`.
 */
@OptIn(UnstableApi::class)
private class DelayRenderersFactory(
    context: Context,
    private val delayProcessor: AudioDelayProcessor?,
    private val videoDelay: VideoDelayState?,
) : DefaultRenderersFactory(context) {
    @OptIn(UnstableApi::class)
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        // buildAudioSink do super é @Nullable na Media3 1.6.0 — o fallback
        // nunca roda na prática (o DefaultRenderersFactory sempre devolve um sink).
        val processor = delayProcessor ?: return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            ?: DefaultAudioSink.Builder(context).build()
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(processor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val state = videoDelay
        if (state == null || extensionRendererMode != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF) {
            super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
            return
        }
        out.add(
            VideoDelayRenderer(
                state,
                MediaCodecVideoRenderer.Builder(context)
                    .setCodecAdapterFactory(getCodecAdapterFactory())
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                    .experimentalSetParseAv1SampleDependencies(false)
                    .experimentalSetLateThresholdToDropDecoderInputUs(C.TIME_UNSET),
            )
        )
    }
}

/**
 * Faixa da sincronia do áudio próprio (Lote 14 item 3 + Lote 17 item 10):
 * −10 s (som adiantado — o VÍDEO atrasa, via [VideoDelayRenderer]) a
 * +10 s (som atrasado — o áudio atrasa, via [AudioDelayProcessor]).
 * Um passo grosso do JOTV a partir do zero, p/ os dois lados.
 */
const val OWN_AUDIO_DELAY_MAX_SEC = 10.0
const val OWN_AUDIO_DELAY_MIN_SEC = -OWN_AUDIO_DELAY_MAX_SEC
private const val OWN_AUDIO_DELAY_MAX_MS = 10_000L

/**
 * Atraso do áudio próprio (Lote 14 item 3, v1.24.0) — um [AudioProcessor]
 * que segura o PCM e o solta [delayMs] depois, então o som sai atrasado
 * em relação à imagem. O Media3 1.6.0 NÃO tem API pública de atraso de
 * áudio (`Player` não tem `setAudioDelay`; `PlaybackParameters` só
 * controla velocidade/pitch) — este processador é a implementação, não
 * uma chamada a uma API que não existe.
 *
 * Dois lados (Lote 17 item 10, v1.27.0): o atraso POSITIVO (som depois
 * da imagem) é este processador; o NEGATIVO (adiantar o som) atrasa o
 * VÍDEO, via [VideoDelayRenderer] — ver o doc da classe. A faixa é
 * −10 s a +10 s ([OWN_AUDIO_DELAY_MIN_SEC]..[OWN_AUDIO_DELAY_MAX_SEC]).
 *
 * Troca de faixa de áudio (Lote 17 item 13, v1.27.0) — o que foi provado
 * no fonte da 1.6.0 e o que NÃO era a causa:
 * - A suspeita do brief (o sink/processador recriado na troca) NÃO se
 *   confirma: a sessão cria UMA instância deste processador
 *   (propriedade da `PlayerSession`, 1x por sessão) e o sink é
 *   construído 1x por player; o `DefaultAudioSink.configure()`
 *   REUTILIZA o pipeline quando as referências dos processadores são
 *   iguais. O que se perde na troca é o PCM BUFFERIZADO — e ele PRECISA
 *   ser descartado (é o áudio da faixa/posição antiga). A troca
 *   desabilita e reabilita o renderer de áudio (`MediaCodecRenderer`
 *   1.6.0 não tem override de `replaceStream`), e o caminho
 *   `onDisabled` → `audioSink.flush()` → `pipeline.flush()` →
 *   `flush()` aqui limpa a linha.
 * - Efeito real pós-troca: com atraso D > 0 salvo, a linha precisa
 *   reacumular D do áudio da NOVA faixa antes de emitir — o som fica
 *   silencioso por ~D enquanto o vídeo continua. É inerente a uma
 *   delay-line pura em qualquer descontinuidade (troca E busca); não
 *   dá p/ pré-preencher (o áudio novo ainda não existe) nem tocar o
 *   que ficou (seria o idioma errado por D segundos). Com atraso 0
 *   (o padrão) resta só o gap normal de re-init do codec/AudioTrack.
 * - O que este arquivo endurece: [delayMs] e [delayBytes] NÃO são
 *   zerados no [reset] (o atraso é do título e vale p/ a sessão
 *   inteira). O formato da última faixa fica guardado
 *   ([lastSampleRate]/[lastBytesPerFrame]) p/ que um `setDelayMs`
 *   chamado na janela entre o `reset()` e o próximo `configure()`
 *   (ex.: o usuário arrasta a barra durante a troca) recalcule o
 *   [delayBytes] certo em vez de zerar — o próximo `configure()`
 *   recalcula de qualquer forma com o formato novo.
 *
 * Detalhe de Media3 que decide o desenho: o `AudioProcessingPipeline`
 * congela a lista de processadores ativos no `configure()` (e no
 * `flush()`), a partir de `isActive()` — um processador inativo no
 * `configure()` fica FORA da lista até a próxima troca de formato ou
 * busca, e mudar o atraso depois não faria nada. Por isso [isActive]
 * é true sempre que o formato é PCM conhecido, mesmo com atraso 0:
 * com 0 o processador só passa o som direto (sem latência extra além
 * da cópia), e o atraso passa a valer na hora quando [setDelayMs] muda.
 * [delayMs] NÃO é zerado no [reset] (troca de faixa): o atraso é do
 * título e vale p/ a sessão inteira; só o buffer é limpo no [flush]
 * (busca) e no fim do stream o que ficou guardado é solto ([queueEndOfStream]).
 *
 * Pior caso de memória: 10 s de PCM 48 kHz 5.1 16 bits ≈ 5,7 MB
 * transitórios no heap; o caso comum (estéreo) é ~1,9 MB.
 */
@OptIn(UnstableApi::class)
class AudioDelayProcessor : AudioProcessor {
    /** Atraso atual em ms (0 = passa direto). Pode mudar durante a reprodução. */
    @Volatile
    var delayMs: Long = 0L
        private set

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    /** Formato da última faixa configurada — sobrevive ao [reset] (ver o doc da classe). */
    private var lastSampleRate = 0
    private var lastBytesPerFrame = 0
    private var delayBytes = 0
    private val chunks = ArrayDeque<ByteArray>()
    private var headOffset = 0
    private var bufferedBytes = 0
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    /** Novo atraso (0 a 10 s). Vale na hora; diminuir solta o som guardado de uma vez. */
    fun setDelayMs(ms: Long) {
        delayMs = ms.coerceIn(0L, OWN_AUDIO_DELAY_MAX_MS)
        updateDelayBytes()
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    /**
     * Recalcula [delayBytes] a partir de [delayMs]. Usa o formato atual
     * quando há um configurado; sem formato (janela entre o [reset] e o
     * próximo [configure]) usa o formato da última faixa, p/ o atraso
     * não zerar no meio de uma troca — ver o doc da classe.
     */
    private fun updateDelayBytes() {
        val f = inputFormat
        val sampleRate = if (f != AudioProcessor.AudioFormat.NOT_SET) f.sampleRate else lastSampleRate
        val bytesPerFrame = if (f != AudioProcessor.AudioFormat.NOT_SET) f.bytesPerFrame else lastBytesPerFrame
        delayBytes = if (sampleRate <= 0 || bytesPerFrame <= 0) 0
        else ((delayMs * sampleRate.toLong() * bytesPerFrame / 1000) / bytesPerFrame * bytesPerFrame).toInt()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = inputAudioFormat
        lastSampleRate = inputAudioFormat.sampleRate
        lastBytesPerFrame = inputAudioFormat.bytesPerFrame
        updateDelayBytes()
        return inputAudioFormat
    }

    /**
     * True com formato PCM conhecido, mesmo com atraso 0 — ver o motivo no
     * doc da classe (a lista de ativos congela no `configure()`). Com
     * formato desconhecido/não-PCM, false: o pipeline pula o processador
     * e o som passa intacto, sem quebrar a reprodução.
     */
    override fun isActive(): Boolean {
        val f = inputFormat
        return f != AudioProcessor.AudioFormat.NOT_SET && f.sampleRate > 0 &&
            f.channelCount > 0 && bytesPerSample(f.encoding) > 0
    }

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n > 0) {
            val bytes = ByteArray(n)
            input.get(bytes)
            chunks.addLast(bytes)
            bufferedBytes += n
        }
        drain()
    }

    /** Solta o que passou do atraso guardado (com atraso 0, solta tudo na hora). */
    private fun drain() {
        val emit = (bufferedBytes - delayBytes).coerceAtLeast(0)
        if (emit == 0) return
        outputBuffer = takeBytes(emit)
    }

    private fun takeBytes(n: Int): ByteBuffer {
        val out = ByteBuffer.allocate(n)
        var left = n
        while (left > 0) {
            val first = chunks.first()
            val avail = first.size - headOffset
            val take = minOf(avail, left)
            out.put(first, headOffset, take)
            headOffset += take
            left -= take
            bufferedBytes -= take
            if (headOffset == first.size) {
                chunks.removeFirst()
                headOffset = 0
            }
        }
        out.flip()
        return out
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun queueEndOfStream() {
        inputEnded = true
        if (bufferedBytes > 0) outputBuffer = takeBytes(bufferedBytes)
    }

    override fun isEnded(): Boolean =
        inputEnded && bufferedBytes == 0 && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        chunks.clear()
        headOffset = 0
        bufferedBytes = 0
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    /**
     * Limpa só o buffer. O atraso ([delayMs] e [delayBytes]) e o formato
     * da última faixa sobrevivem: o atraso é do título, não da faixa —
     * zerar aqui faria a troca de faixa soltar o som sem atraso até o
     * próximo `setDelayMs`, e o `updateDelayBytes()` da janela entre o
     * `reset()` e o próximo `configure()` precisa do formato guardado.
     * O próximo `configure()` recalcula [delayBytes] com o formato novo.
     */
    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    override fun getDurationAfterProcessorApplied(durationUs: Long): Long = durationUs + delayMs * 1000
}

/**
 * Tamanho do buffer do ExoPlayer (Lote 5 item 1, v1.14.0 — pref
 * `pref_player_buffer`, por perfil). Os 4 números são os do
 * [DefaultLoadControl.Builder.setBufferDurationsMs]: mínimo e máximo de
 * mídia guardada, quanto precisa ter p/ começar a tocar e quanto precisa
 * ter p/ retomar depois de travar.
 *
 * - [LOW]: 15 s / 30 s, começa com 1,5 s. Internet boa e aparelho com
 *   pouca memória; começa rápido, mas trava mais fácil se a rede oscilar.
 * - [NORMAL]: 50 s / 50 s, começa com 2,5 s, retoma com 5 s. É o PADRÃO
 *   do ExoPlayer (os mesmos números que ele usa sem LoadControl) — quem
 *   não mexer continua exatamente como antes.
 * - [HIGH]: 120 s / 180 s, começa com 5 s, retoma com 10 s. Internet
 *   instável: segura mais vídeo na frente, ao custo de mais memória e
 *   de esperar mais no início.
 *
 * Vale SÓ na criação do player (a sessão cria o ExoPlayer 1x, como o
 * decoder): mudar em Configurações vale a partir da próxima reprodução.
 * Não vale p/ TV ao vivo (LivePlayerScreen usa o próprio ExoPlayer
 * sem LoadControl), nem p/ player externo e Cast (não é o ExoPlayer do
 * app que toca), nem p/ o player de som de outra fonte (só áudio, sem
 * vídeo p/ guardar). Não há valor personalizado em ms: 3 presets cobrem
 * os casos e o campo numérico não valia a tela a mais.
 */
enum class BufferProfile(
    val id: String,
    val label: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    LOW("low", "Baixo", 15_000, 30_000, 1_500, 3_000),
    NORMAL("normal", "Normal", 50_000, 50_000, 2_500, 5_000),
    HIGH("high", "Alto", 120_000, 180_000, 5_000, 10_000);

    companion object {
        fun fromId(id: String): BufferProfile =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** LoadControl do ExoPlayer com o perfil de buffer escolhido. */
@OptIn(UnstableApi::class)
fun bufferLoadControl(profile: BufferProfile): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            profile.minBufferMs,
            profile.maxBufferMs,
            profile.bufferForPlaybackMs,
            profile.bufferForPlaybackAfterRebufferMs,
        )
        .build()

/** Seletor de rota do Cast (Default Media Receiver), compartilhado pelo botão do player. */
fun castRouteSelector(): androidx.mediarouter.media.MediaRouteSelector =
    androidx.mediarouter.media.MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
        ).build()

/** MIME p/ abrir o link num player externo (o seletor do sistema usa isso p/ filtrar apps). */
fun externalPlayerMime(url: String): String {
    val u = url.substringBefore('|').substringBefore('?').lowercase()
    return when {
        u.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
        u.endsWith(".mp4") || u.endsWith(".m4v") -> "video/mp4"
        u.endsWith(".mkv") -> "video/x-matroska"
        u.endsWith(".webm") -> "video/webm"
        u.endsWith(".mov") -> "video/quicktime"
        u.endsWith(".avi") -> "video/x-msvideo"
        else -> "video/*"
    }
}

/**
 * Abre [url] num player externo instalado (ACTION_VIEW + seletor do sistema).
 * Devolve null quando o intent foi disparado, ou a mensagem p/ o toast quando não dá:
 * link vazio, nenhum player instalado, ou o sistema recusou.
 *
 * Limites honestos (não dá p/ contornar pelo intent):
 * - Headers custom (ex.: o sufixo Kodi `|Header=...`) NÃO vão junto: o
 *   ACTION_VIEW só leva URL + MIME. Fonte que só toca com header no
 *   interno pode falhar no externo — aí é usar o ExoPlayer.
 * - A URL tem que ser direta (http/https). Magnet de torrent não abre
 *   fora do app: quem resolve é o debrid, antes de chamar aqui (ver
 *   [PlayerSession.openExternalPlayer]).
 * - Legenda externa, som de outra fonte e correção de cor ficam no app:
 *   o externo toca só o vídeo, com os próprios controles dele.
 */
fun openInExternalPlayer(context: Context, url: String, title: String): String? {
    if (url.isBlank()) return "Sem link para abrir no player externo."
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return "Esse link não abre num player externo."
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), externalPlayerMime(url))
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(Intent.createChooser(intent, "Abrir com"))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "Nenhum player de vídeo instalado para abrir esse link."
    } catch (_: Exception) {
        "Não foi possível abrir no player externo."
    }
}
