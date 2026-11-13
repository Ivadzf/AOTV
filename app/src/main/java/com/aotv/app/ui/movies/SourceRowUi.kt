package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.pressScale
import com.aotv.app.ui.theme.*

/**
 * Uma fonte já traduzida pro que a tela precisa mostrar. O label cru dos addons Stremio vem
 * cheio de emoji, repete o título do filme e mistura tudo numa linha só. [parseSourceLabel]
 * quebra isso em pedaços e a linha desenha cada pedaço no seu lugar.
 */
data class SourceInfo(
    /** Nome do addon de origem ("FrostStream"). Linha principal. */
    val addon: String,
    /** Servidor/provedor dentro do addon ("Servidor Athena"). "" se não houver. */
    val server: String,
    /** "4K", "1080p", "720p"… "" se desconhecido. */
    val quality: String,
    /** "Dublado", "Legendado", "Original"… "" se desconhecido. */
    val language: String,
    /** Pills técnicas extras (tamanho, MP4, HTTP, verificado…). */
    val tags: List<String>,
    val fileName: String = "",
    val size: String = "",
    val seeds: Int = 0,
    val providers: List<String> = emptyList(),
)

private val EMOJI = Regex(
    "[\\p{So}\\p{Cn}\\uFE0F\\u200D]|[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"
)

/** Tira emoji e separadores soltos que sobram no meio ("· ·" vira "·"). */
private fun clean(s: String): String = s
    .replace(EMOJI, " ")
    .replace(Regex("\\s*·\\s*(·\\s*)+"), " · ")
    .replace(Regex("\\s{2,}"), " ")
    .trim()
    .trim('·', '-', '|', ' ')

private val SIZE = Regex("""\b\d+([.,]\d+)?\s?(GB|MB)\b""", RegexOption.IGNORE_CASE)
private val SEEDS = Regex("""\b(\d+)\s*(seeds?|seeders?)\b""", RegexOption.IGNORE_CASE)
private val QUALITY_WORDS = Regex("""\b(4K|2160p?|1440p|1080p|720p|480p|360p|FHD|UHD|HDR|HD)\b""", RegexOption.IGNORE_CASE)
private val CODEC = Regex("""\b(x\.?264|h\.?264|avc|x\.?265|h\.?265|hevc|av1)\b""", RegexOption.IGNORE_CASE)
private val RELEASE = Regex("""\b(blu-?ray|remux|web-?dl|web-?rip|br-?rip|bd-?rip|hdtv|dvd-?rip)\b""", RegexOption.IGNORE_CASE)
private val ONLY_NUMBERS = Regex("""^[\d\s/.,%]+$""")

/**
 * Traduz o label cru de uma fonte em [SourceInfo].
 *
 * [mediaTitle] é o nome do filme/série aberto. Aparece repetido em toda linha de alguns addons,
 * então some daqui: a tela inteira já é sobre esse título.
 */
fun parseSourceLabel(
    rawLabel: String,
    addonName: String,
    qualityRank: Int,
    mediaTitle: String = "",
): SourceInfo {
    val label = clean(rawLabel)

    // Normaliza pra comparar título: sem acento, sem ano entre parênteses, sem pontuação.
    fun chave(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\(\\s*\\d{4}\\s*\\)"), " ")
        .replace(Regex("[^A-Za-z0-9]+"), " ")
        .trim().lowercase()

    val tituloChave = chave(mediaTitle)
    // Quebra pelos separadores comuns dos addons.
    val parts = label.split("·", "|", "•", "\n")
        .map { clean(it) }
        .filter { it.isNotBlank() }
        // Tira o nome do título. Comparação tolerante porque o addon escreve
        // "Tron O Legado (2010)" e o TMDB "Tron O Legado" — igualdade exata não pegava, e o
        // nome do filme acabava aparecendo como se fosse o servidor.
        .filterNot { p -> tituloChave.isNotBlank() && chave(p).let { it == tituloChave || it.startsWith(tituloChave) } }

    val quality = when {
        qualityRank >= 2160 -> "4K"
        qualityRank >= 1440 -> "1440p"
        qualityRank >= 1080 -> "1080p"
        qualityRank >= 720 -> "720p"
        qualityRank >= 480 -> "480p"
        else -> QUALITY_WORDS.find(label)?.value?.uppercase()?.replace("2160P", "4K") ?: ""
    }

    val language = parts.firstOrNull { p ->
        p.contains("dubl", true) || p.contains("legend", true) || p.contains("dual", true) ||
            p.equals("dub", true) || p.equals("leg", true) || p.contains("nacional", true)
    }?.let {
        when {
            it.contains("dubl", true) || it.equals("dub", true) || it.contains("nacional", true) -> "Dublado"
            it.contains("dual", true) -> "Dual áudio"
            else -> "Legendado"
        }
    } ?: ""

    // Servidor: pedaço que fala de servidor, senão o primeiro que sobrar sem ser qualidade/idioma.
    // Torrent não tem servidor, a origem (Bluray, WEB-DL) é o que diferencia uma da outra.
    val server = parts.firstOrNull { it.contains("servidor", true) || it.contains("sevidor", true) || it.contains("server", true) }
        ?: parts.firstOrNull { RELEASE.containsMatchIn(it) && it.length <= 28 }
        ?: parts.firstOrNull { p ->
            !QUALITY_WORDS.matches(p) && !p.contains("dubl", true) && !p.contains("legend", true) &&
                !ONLY_NUMBERS.matches(p) && !p.contains("dual", true) &&
                !p.contains("filme", true) && !p.contains("série", true) && !p.contains("serie", true) &&
                !SIZE.containsMatchIn(p) && p.length in 3..28 &&
                // Pedaço que começa igual ao título é o nome do filme, não servidor.
                !(tituloChave.isNotBlank() && chave(p).startsWith(tituloChave.take(8)))
        }
        ?: ""

    val tags = buildList {
        // Addon de torrent marca quem compartilha com 👤 (some na limpeza de emoji, então lê do rótulo cru).
        (SEEDS.find(label)?.groupValues?.get(1) ?: com.aotv.app.data.torrent.TorrentMagnet.seeders(rawLabel).takeIf { it > 0 }?.toString())
            ?.let { add("$it seeds") }
        SIZE.find(label)?.value?.let { add(it.uppercase()) }
        CODEC.find(label)?.value?.lowercase()?.let { c ->
            add(when { "av1" in c -> "AV1"; "265" in c || "hevc" in c -> "HEVC"; else -> "H.264" })
        }
        if (label.contains("HDR", true)) add("HDR")
        if (label.contains("verificad", true)) add("Verificado")
        if (label.contains("mp4", true)) add("MP4")
    }.distinct().take(3)

    return SourceInfo(
        addon = addonName.ifBlank { "Fonte" },
        server = if (server.equals(addonName, true)) "" else server,
        quality = quality,
        language = language,
        tags = tags,
    )
}

/** Cor da faixa de qualidade. 4K em dourado, o resto no azul do app, baixa resolução apagada. */
private fun qualityColor(q: String): Color = when {
    q == "4K" || q == "1440p" -> Gold
    q == "1080p" -> Accent
    q == "720p" -> TextSecondary
    else -> TextTertiary
}

/**
 * Linha de fonte do detalhe. Faixa de qualidade à esquerda, addon em destaque, servidor e
 * idioma embaixo, pills técnicas e os botões de tocar e baixar à direita.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceCard(
    info: SourceInfo,
    tag: String,
    tagColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    note: String? = null,
    selected: Boolean = false,
    suggested: Boolean = false,
    focusedByDpad: Boolean = false,
    manualFocus: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val focus = focused || focusedByDpad
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().pressScale(interaction).clip(shape)
            .background(if (focus || selected) AccentSoft else BackgroundCard2)
            .border(if (focus) 2.dp else 1.dp, if (focus) FocusRing else if (selected) Accent else Border, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (manualFocus) Modifier.focusProperties { canFocus = false } else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected || suggested) Text(if (selected) "Tocando agora" else "Melhor opção", color = Accent,
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val font = androidx.compose.ui.platform.LocalDensity.current.fontScale
            if (maxWidth < (280f * font.coerceAtLeast(1f)).dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceIdentity(info, Modifier.fillMaxWidth())
                SourceStats(info)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                SourceIdentity(info, Modifier.weight(1f))
                SourceStats(info)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (info.quality.isNotBlank()) com.aotv.app.ui.common.Badge(info.quality,
                        if (info.quality in listOf("4K", "2K")) Gold else Accent)
                    info.tags.take(10).forEach { SourcePill(it) }
                    if (info.language.isNotBlank()) SourcePill(info.language)
                    if (tag.isNotBlank()) com.aotv.app.ui.common.Badge(tag, tagColor)
                }
                if (info.providers.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    info.providers.distinct().take(3).forEach { SourcePill(it) }
                    if (info.providers.distinct().size > 3) SourcePill("+${info.providers.distinct().size - 3}")
                }
            }
            if (onDownload != null) Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDownload), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Download, "Baixar", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (focus) FocusSurface else FillTint.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = if (focus) OnFocusSurface else TextPrimary, modifier = Modifier.size(24.dp))
            }
        }
        if (note != null) Text(note, color = Gold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.fileName.isNotBlank()) Text(info.fileName, color = TextTertiary, fontSize = 11.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceIdentity(info: SourceInfo, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(info.addon.ifBlank { "Fonte" }, color = TextPrimary, fontSize = 16.sp, lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.server.isNotBlank()) Text(info.server, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceStats(info: SourceInfo) {
    if (info.size.isBlank() && info.seeds <= 0) return
    Column(horizontalAlignment = Alignment.End) {
        if (info.size.isNotBlank()) Text(info.size, color = TextPrimary, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
        if (info.seeds > 0) Text("${info.seeds} seeds", color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SourcePill(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = ManropeFontFamily,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(FillSubtle)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Pôster de um filme da coleção. Traz o NÚMERO da ordem cronológica no canto e marca o
 * título que está aberto agora, pra pessoa se localizar dentro da franquia.
 */
@Composable
fun CollectionPoster(
    order: Int,
    movie: com.aotv.app.domain.model.MovieItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    // O número da ordem ocupa o canto, então o pôster sai sem etiqueta (igual ao webOS).
    Box(Modifier.width(128.dp)) {
        com.aotv.app.ui.common.PosterCard(
            item = movie, onClick = onClick, modifier = Modifier.fillMaxWidth(), showTag = false,
            caption = movie.title, sub = if (isCurrent) "Assistindo" else movie.year.ifBlank { "Sem data" }, highlight = isCurrent,
        )
        // Selo da ordem. Accent no filme atual, escuro nos outros.
        Box(
            Modifier
                .padding(8.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCurrent) Accent else Color.Black.copy(alpha = 0.72f))
                .border(1.dp, if (isCurrent) Accent else Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                order.toString(),
                color = if (isCurrent) OnAccent else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = SoraFontFamily,
            )
        }
    }
}
