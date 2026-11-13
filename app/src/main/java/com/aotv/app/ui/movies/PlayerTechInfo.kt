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
)

/** Tem ao menos um dado do stream atual (sem ele o painel mostra "ainda sem dados"). */
val TechInfo.isEmpty: Boolean
    get() = videoCodec == null && videoResolution == null && videoFps == null &&
        videoDecoder == null && videoBitrateKbps == null && audioCodec == null

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
fun collectTechInfo(player: ExoPlayer): TechInfo = runCatching {
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
    )
}.getOrDefault(TechInfo())

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
) {
    if (television) TvScaled { TechInfoPanelBody(info, onDismiss, true) }
    else TechInfoPanelBody(info, onDismiss, false)
}

@Composable
private fun TechInfoPanelBody(
    info: TechInfo?,
    onDismiss: () -> Unit,
    television: Boolean,
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
            ).takeIf { it.isNotEmpty() }?.let { "AUDIO STREAM" to it },
        )
        LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (info == null || info.isEmpty) {
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
