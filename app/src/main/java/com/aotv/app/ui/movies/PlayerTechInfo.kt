package com.aotv.app.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.aotv.app.ui.common.Eyebrow
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.tv.TvScaled

/**
 * Infos técnicas do vídeo (Lote 4 item 4, v1.12.0) — o painel "Informações de
 * reprodução" do Nuvio, na foto de referência (engine, codec, resolução, fps,
 * decoder, bitrate). Tudo lido do ExoPlayer de verdade (videoFormat /
 * audioFormat / MediaCodecSelector); o que não der p/ medir NÃO aparece —
 * nunca inventar valor. A lista é refeita a cada troca de fonte/episódio
 * (ver [PlayerSession.refreshTechInfo]).
 *
 * Decoder: [MediaCodecSelector] devolve os decoders que o aparelho tem p/
 * o mime do formato, na ordem de preferência do renderer — o 1º é o que o
 * ExoPlayer usa na prática. "Hardware" vem do [MediaCodecInfo] dele; sem
 * decoder listado, a linha some.
 *
 * Faixa dinâmica: só aparece quando o formato marca HDR (ST2084 = HDR10,
 * HLG = HLG) ou SDR explícito; o resto é "desconhecido" e some, como na foto.
 */
data class TechInfo(
    val engine: String = "ExoPlayer",
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val videoFps: String? = null,
    val videoDecoder: String? = null,
    /** true = hardware, false = software, null = não deu p/ saber. */
    val videoDecoderHw: Boolean? = null,
    val videoDynamicRange: String? = null,
    val videoBitrateKbps: Int? = null,
    val audioCodec: String? = null,
    val audioDecoder: String? = null,
    /**
     * Saída de áudio atual (Lote 9 item 5, v1.19.0): "Alto-falante",
     * "Fone com fio", "Bluetooth", "HDMI", "USB" ou "Chromecast" (ver
     * [collectAudioOutput]). Null = o aparelho não informou nenhuma
     * saída — aí a linha some, sem inventar valor.
     */
    val audioOutput: String? = null,
)

/** Tem ao menos um dado do stream atual (sem ele o painel mostra "ainda sem dados"). */
val TechInfo.isEmpty: Boolean
    get() = videoCodec == null && videoResolution == null && videoFps == null &&
        videoDecoder == null && videoBitrateKbps == null && audioCodec == null &&
        audioOutput == null

/**
 * Stats do torrent durante a reprodução P2P (Lote 5 item 2, v1.14.0) —
 * tudo lido de verdade, nada inventado:
 * - [seeds]/[peers]: do status do libtorrent ([TorrentStreamer.Status]:
 *   `numSeeds()`/`numPeers()` do handle). 0 é um valor real (ninguém
 *   compartilhando / sem conexão ainda), não "sem dado".
 * - [downloadBps]: taxa de download do payload, em bytes/s, do mesmo status.
 * - [bufferedMs]: quanto do vídeo o ExoPlayer já tem guardado à frente
 *   (`bufferedPosition - currentPosition`), em ms.
 *
 * A seção só aparece quando há status ([TechInfoPanel] com `torrent`
 * não-nulo): fonte que não é P2P, P2P resolvido pelo debrid ou motor sem
 * o torrent não mostram nada — o painel nunca mostra número chutado.
 */
data class TorrentStats(
    val seeds: Int,
    val peers: Int,
    val downloadBps: Int,
    val bufferedMs: Long,
)

/** "1,2 MB/s" ou "340 KB/s" — mesmo formato do overlay de carregamento P2P. */
private fun torrentSpeedLabel(bps: Int): String =
    if (bps >= 1_000_000) String.format(java.util.Locale("pt", "BR"), "%.1f MB/s", bps / 1_000_000.0)
    else "${bps / 1_000} KB/s"

/** Nome do decoder do [mime] no aparelho, ou null. [hardware] diz se ele é de hardware. */
@OptIn(UnstableApi::class)
private fun decoderFor(mime: String?): Pair<String, Boolean?>? {
    val m = mime?.takeIf { it.isNotBlank() } ?: return null
    val info = runCatching {
        MediaCodecSelector.DEFAULT.getDecoderInfos(m, false, false).firstOrNull()
    }.getOrNull() ?: return null
    val hw = runCatching { info.hardwareAccelerated }.getOrNull()
    return info.name to hw
}

/** "avc1.640028 (video/avc)" — codec do formato + mime, como na foto. */
private fun codecLabel(format: androidx.media3.common.Format): String? {
    val codecs = format.codecs?.takeIf { it.isNotBlank() }
    val mime = format.sampleMimeType?.takeIf { it.isNotBlank() }
    return when {
        codecs != null && mime != null -> "$codecs ($mime)"
        codecs != null -> codecs
        else -> mime
    }
}

/** Lê os dados do stream que está tocando agora. Nunca lança (o painel não pode travar o player). */
@OptIn(UnstableApi::class)
fun collectTechInfo(player: ExoPlayer, context: android.content.Context? = null, casting: Boolean = false): TechInfo = runCatching {
    val video = player.videoFormat
    val audio = player.audioFormat
    // decoderFor devolve Pair anulável — desmontar direto não compila
    // (component1/component2 exigem valor não-nulo); ler com ?.first/?.second.
    val videoDec = decoderFor(video?.sampleMimeType)
    val videoDecoder = videoDec?.first
    val videoHw = videoDec?.second
    val audioDec = decoderFor(audio?.sampleMimeType)
    val audioDecoder = audioDec?.first
    val fps = video?.frameRate?.takeIf { it > 0f }?.let {
        "%.3f".format(it).trimEnd('0').trimEnd('.')
    }
    TechInfo(
        videoCodec = video?.let { codecLabel(it) },
        videoResolution = video?.let {
            if (it.width > 0 && it.height > 0) "${it.width}x${it.height}" else null
        },
        videoFps = fps,
        videoDecoder = videoDecoder,
        videoDecoderHw = videoHw,
        videoDynamicRange = when (video?.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10"
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_SDR -> "SDR"
            // Qualquer outro (inclui "desconhecido") some da lista.
            else -> null
        },
        videoBitrateKbps = video?.bitrate?.takeIf { it > 0 }?.div(1000),
        audioCodec = audio?.let { codecLabel(it) },
        audioDecoder = audioDecoder,
        audioOutput = collectAudioOutput(context, casting),
    )
}.getOrDefault(TechInfo())

/**
 * Saída de áudio atual (Lote 9 item 5, v1.19.0) — o que o aparelho
 * informa em `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`, sem inventar:
 *
 * - Transmitindo p/ o Chromecast, o som sai na TV: "Chromecast".
 * - Com mais de uma saída conectada, vale a de maior prioridade
 *   (Bluetooth > fone com fio > USB > HDMI > alto-falante): o Android não
 *   expõe a rota ativa diretamente, e a saída "extra" conectada é a que
 *   recebe o som na prática (fone/Bluetooth tomam o lugar do alto-falante).
 * - Nome do produto (ex.: o modelo do fone Bluetooth) vem do próprio
 *   `AudioDeviceInfo.productName`, quando o aparelho informa.
 * - Tipo desconhecido com nome informa o nome; sem nome nem tipo
 *   conhecido, null — a linha some do painel.
 */
internal fun collectAudioOutput(context: android.content.Context?, casting: Boolean): String? {
    if (casting) return "Chromecast"
    val ctx = context ?: return null
    val manager = runCatching {
        ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }.getOrNull() ?: return null
    val devices = runCatching {
        manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
    }.getOrNull().orEmpty()
    if (devices.isEmpty()) return null
    fun priority(type: Int): Int = when (type) {
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 5
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> 4
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> 3
        android.media.AudioDeviceInfo.TYPE_HDMI,
        android.media.AudioDeviceInfo.TYPE_HDMI_ARC,
        android.media.AudioDeviceInfo.TYPE_HDMI_EARC -> 2
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 1
        else -> 0
    }
    val best = devices.maxByOrNull { priority(it.type) } ?: return null
    val name = runCatching { best.productName?.toString().orEmpty() }.getOrDefault("").trim()
    val base = when (best.type) {
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        android.media.AudioDeviceInfo.TYPE_HDMI,
        android.media.AudioDeviceInfo.TYPE_HDMI_ARC,
        android.media.AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Fone de ouvido"
        android.media.AudioDeviceInfo.TYPE_HEARING_AID -> "Aparelho auditivo"
        android.media.AudioDeviceInfo.TYPE_LINE_ANALOG -> "Saída de linha"
        android.media.AudioDeviceInfo.TYPE_DOCK -> "Dock"
        else -> ""
    }
    return when {
        base.isNotBlank() && name.isNotBlank() && base != "Alto-falante" -> "$base · $name"
        base.isNotBlank() -> base
        name.isNotBlank() -> name
        else -> null
    }
}

/** Uma linha "rótulo — valor" do painel, no desenho da foto (rótulo cinza, valor em negrito). */
@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 15.sp)
        Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TechInfoPanel(
    info: TechInfo?,
    onDismiss: () -> Unit,
    television: Boolean = false,
    /** Stats do torrent P2P (null = fonte não é P2P; a seção some). */
    torrent: TorrentStats? = null,
) {
    if (television) TvScaled { TechInfoPanelBody(info, onDismiss, true, torrent) }
    else TechInfoPanelBody(info, onDismiss, false, torrent)
}

@Composable
private fun TechInfoPanelBody(
    info: TechInfo?,
    onDismiss: () -> Unit,
    television: Boolean,
    torrent: TorrentStats? = null,
) {
    SidePanel(
        title = "Informações de reprodução",
        onDismiss = onDismiss,
        width = if (television) 800.dp else 440.dp,
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        sub = "Dados do stream atual — atualiza ao trocar de fonte ou episódio",
    ) {
        val rows: List<Pair<String, List<Pair<String, String>>>> = listOfNotNull(
            // PLAYBACK ENGINE sempre aparece (é o próprio player, não o stream).
            "PLAYBACK ENGINE" to listOf("Engine" to (info?.engine ?: "ExoPlayer")),
            // takeIf devolve lista anulável — o par só entra quando a lista existe
            // (senão o tipo inferido é Pair<String, List<...>?>, que não bate com o
            // tipo declarado de `rows`).
            listOfNotNull(
                info?.videoCodec?.let { "Codec" to it },
                info?.videoResolution?.let { "Resolução" to it },
                info?.videoFps?.let { "Frame Rate" to "$it fps" },
                info?.videoDecoder?.let { "Decoder" to it },
                info?.videoDecoderHw?.let { "Decoder de hardware" to if (it) "hardware" else "software" },
                info?.videoDynamicRange?.let { "Faixa dinâmica" to it },
                info?.videoBitrateKbps?.let { "Bitrate" to "$it kbps" },
            ).takeIf { it.isNotEmpty() }?.let { "VIDEO STREAM" to it },
            listOfNotNull(
                info?.audioCodec?.let { "Codec" to it },
                info?.audioDecoder?.let { "Decoder" to it },
                // Saída de áudio (Lote 9 item 5): só quando o aparelho
                // informou alguma — sem saída detectável, a linha some.
                info?.audioOutput?.let { "Saída" to it },
            ).takeIf { it.isNotEmpty() }?.let { "AUDIO STREAM" to it },
            // Torrent P2P (Lote 5 item 2): só quando há status real — os 4
            // valores vêm do motor + ExoPlayer, sem nada inventado.
            torrent?.let {
                "TORRENT (P2P)" to listOf(
                    "Seeds" to "${it.seeds}",
                    "Conexões" to "${it.peers}",
                    "Velocidade" to torrentSpeedLabel(it.downloadBps),
                    "Buffer no player" to "${it.bufferedMs / 1_000} s",
                )
            },
        )
        LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if ((info == null || info.isEmpty) && torrent == null) {
                item {
                    Text(
                        "Ainda sem dados do stream — aparece quando o vídeo começar a tocar.",
                        color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
            }
            rows.forEach { (section, sectionRows) ->
                item { Eyebrow(section, Modifier.padding(top = 10.dp, start = 4.dp), color = Accent) }
                items(sectionRows) { (label, value) -> TechInfoRow(label, value) }
            }
        }
    }
}
