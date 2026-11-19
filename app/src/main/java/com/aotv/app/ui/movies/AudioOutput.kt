package com.aotv.app.ui.movies

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink

// Saída de áudio: TV speakers vs Receiver — passthrough Dolby/DTS
// (Lote 25 item 9, v1.35.0 — Alder). Reimplementação da ideia, sem
// copiar código (Alder é GPL-3.0).
//
// Pref GLOBAL (a saída é do aparelho, não do perfil):
// - `audio_output_v1`: "auto" (padrão — o sistema escolhe) | "speakers"
//   (alto-falantes do aparelho) | "receiver" (saída HDMI p/ o receiver).
// - `audio_passthrough_v1`: passthrough Dolby/DTS ligado/desligado.
//   Nenhuma das duas entra em SCOPED_KEYS.
//
// ── O QUE O GANCHO FAZ DE VERDADE (Media3 1.6.0, conferido no AAR) ──
// O `DefaultAudioSink.Builder` da 1.6.0 NÃO tem `setOffloadMode` — o
// modo offload é pedido no sink já construído, por
// `AudioSink.setOffloadMode(OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED)`
// (método default da interface, implementado pelo DefaultAudioSink).
// É um PEDIDO, não uma garantia: o sink só usa o caminho offload
// quando o formato aceita offload; sem suporte, ele decodifica p/
// PCM sozinho (sem crash). O passthrough de verdade (bitstream AC-3 /
// DTS intacto até o receiver) depende das capacidades que o aparelho
// anuncia ([AudioCapabilities.getCapabilities]): o sink padrão já usa
// essas capacidades, então o áudio codificado passa direto quando o
// receiver anuncia o codec, e vira PCM quando não anuncia — não dá p/
// forçar bitstream p/ um receiver que não anuncia o codec.
// Por isso o toggle é honesto: ele pede o offload e deixa o sink
// decidir; o painel de infos técnicas mostra a saída real (ver
// [collectAudioOutput]) e o modo noturno/Clear Dialogue é bloqueado
// com o passthrough ligado (o bitstream não passa pelo processamento
// — ver [NightModeController.passthrough]).
//
// O sink nasce 1x com o player (ver `decoderRenderersFactory`):
// mudar o passthrough em Configurações ou no painel de áudio só vale
// a partir da PRÓXIMA reprodução. A rota (speakers/receiver) é
// aplicada na hora via `ExoPlayer.setPreferredAudioDevice` — com
// "auto", o dispositivo preferido é limpo (null) e o sistema decide.

/** Saída de áudio do aparelho. */
enum class AudioOutputMode(val id: String, val label: String) {
    AUTO("auto", "Automática"),
    SPEAKERS("speakers", "Alto-falantes"),
    RECEIVER("receiver", "Receiver (HDMI)");

    companion object {
        fun fromId(id: String): AudioOutputMode =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Dispositivo de saída preferido p/ o modo, ou null ("auto" = o sistema
 * decide; sem dispositivo do tipo pedido, também null — sem inventar
 * rota). Receiver = saída HDMI (HDMI, HDMI ARC ou HDMI eARC).
 */
fun preferredAudioDevice(context: Context, mode: AudioOutputMode): AudioDeviceInfo? {
    if (mode == AudioOutputMode.AUTO) return null
    val manager = runCatching {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }.getOrNull() ?: return null
    val devices = runCatching {
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull().orEmpty()
    return when (mode) {
        AudioOutputMode.SPEAKERS ->
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        AudioOutputMode.RECEIVER ->
            devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_HDMI ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_EARC
            }
        AudioOutputMode.AUTO -> null
    }
}

/** Aplica a rota preferida no player (null = automático). Não lança. */
fun applyAudioOutputMode(player: Player, context: Context, mode: AudioOutputMode) {
    runCatching {
        player.setPreferredAudioDevice(preferredAudioDevice(context, mode))
    }
}

/**
 * true quando o aparelho/receiver anuncia suporte a pelo menos um
 * codec de bitstream (Dolby Digital / Dolby Digital Plus / DTS).
 * Só um indício p/ a UI — o sink decide por formato na hora de tocar.
 */
fun receiverPassthroughSupported(context: Context): Boolean {
    val caps = runCatching {
        AudioCapabilities.getCapabilities(context)
    }.getOrNull() ?: return false
    return caps.supportsEncoding(C.ENCODING_AC3) ||
        caps.supportsEncoding(C.ENCODING_EAC3) ||
        caps.supportsEncoding(C.ENCODING_DTS)
}

/**
 * Pede o modo offload no sink (passthrough). Ver o doc do arquivo:
 * é um pedido — sem suporte ao formato, o sink decodifica p/ PCM.
 * Desligado, não mexe em nada (o sink volta ao padrão na próxima
 * reprodução, quando é reconstruído sem o pedido).
 */
@OptIn(UnstableApi::class)
fun applyPassthroughToSink(sink: AudioSink, enabled: Boolean) {
    if (!enabled) return
    runCatching {
        sink.setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED)
    }
}
