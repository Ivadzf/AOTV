package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.QualityBox
import com.aotv.app.ui.common.SidePanel
import com.aotv.app.ui.common.qualityTier
import com.aotv.app.ui.theme.*
import com.aotv.app.ui.tv.TvScaled
import coil.compose.AsyncImage

// Painel lateral do player (fontes, legendas, áudio) no desenho do .side-panel do webOS.

/** Ação do painel do player. Episódio anterior ou próximo, uma fonte, uma legenda ou um ajuste. */
data class TvPlayerAction(
    val label: String,
    val isSource: Boolean,
    val active: Boolean,
    /** Addon de origem da fonte (nome do manifest). "" = fonte própria do app. */
    val addon: String = "",
    /** Linha de baixo (valor atual do ajuste, motivo de não servir). */
    val sub: String = "",
    /** Título de seção mostrado acima da linha. */
    val header: String = "",
    /** Ajuste que muda no lugar, o painel continua aberto. */
    val keepOpen: Boolean = false,
    /** Aparece apagada (o toque só explica o motivo). */
    val disabled: Boolean = false,
    val image: String = "",
    val detail: String = "",
    val episode: Boolean = false,
    val seasonTabs: List<Int> = emptyList(),
    val selectedSeason: Int = 0,
    val onSeasonPick: ((Int) -> Unit)? = null,
    val stream: com.aotv.app.data.remote.StremioStream? = null,
    val providers: List<String> = emptyList(),
    val onRun: () -> Unit,
)

/** Painel aberto com a lista do tipo pedido. [focusIdx] é o item com foco do D-pad. */
@Composable
internal fun PlayerActionPanel(
    title: String,
    showCount: Boolean,
    actions: List<TvPlayerAction>,
    focusIdx: Int,
    pinned: Boolean,
    movieTitle: String,
    onPick: (TvPlayerAction) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean = false,
) {
    if (television) TvScaled { PlayerActionPanelBody(title, showCount, actions, focusIdx, pinned, movieTitle, onPick, onDismiss, true) }
    else PlayerActionPanelBody(title, showCount, actions, focusIdx, pinned, movieTitle, onPick, onDismiss, false)
}

@Composable
private fun PlayerActionPanelBody(
    title: String,
    showCount: Boolean,
    actions: List<TvPlayerAction>,
    focusIdx: Int,
    pinned: Boolean,
    movieTitle: String,
    onPick: (TvPlayerAction) -> Unit,
    onDismiss: () -> Unit,
    television: Boolean,
) {
    val sourceCount = actions.count { it.isSource }
    val shortWindow = !television && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    SidePanel(
        title = title,
        onDismiss = onDismiss,
        width = if (television && (showCount || actions.any { it.episode || it.isSource })) 800.dp else 440.dp,
        // Título maior e pill "Fechar", como os painéis do Nuvio.
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        // A frase de fonte só vale no painel de fontes. Legendas e Áudio ganham a dica das teclas.
        sub = when {
            !showCount && television -> "Setas escolhem, OK aplica, Voltar fecha"
            !showCount && shortWindow -> null
            !showCount -> "Toque em uma opção para aplicar"
            pinned && shortWindow -> "Fonte fixada"
            shortWindow -> null
            pinned -> "Fonte fixada. O player não troca sozinho"
            else -> "A troca de fonte mantém o tempo"
        },
        subColor = if (pinned) Accent else TextTertiary,
        titleTrailing = if (showCount && sourceCount > 0) ({
            Text(
                "$sourceCount", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AccentFill).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }) else null,
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(focusIdx, actions.size) {
            if (actions.isNotEmpty()) listState.animateScrollToItem(focusIdx.coerceIn(0, actions.lastIndex))
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(actions) { i, act ->
                SourcePanelRow(act, focused = i == focusIdx, movieTitle = movieTitle, onClick = { onPick(act) })
            }
        }
    }
}

/** Linha do painel (.panel-item). Selecionada com borda branca, como no Nuvio
 * (antes: fundo AccentSoft + borda Accent); foco D-pad continua fundo branco. */
@Composable
private fun SourcePanelRow(action: TvPlayerAction, focused: Boolean, movieTitle: String, onClick: () -> Unit) {
    val bg = when {
        focused -> Color.White
        action.active -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    val fg = if (focused) TextOnLight else Color.White
    val subColor = if (focused) Color(0xFF444444) else TextTertiary
    val rowMod = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
        .then(if (action.active && !focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)) else Modifier)
        .focusProperties { canFocus = false }
        .clickable { onClick() }
        .padding(horizontal = 14.dp, vertical = 10.dp)

    Column(Modifier.alpha(if (action.disabled) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Rótulo de seção em cinza, como o "Idiomas"/"Legendas" do Nuvio (antes: Accent).
        if (action.header.isNotBlank()) com.aotv.app.ui.common.Eyebrow(action.header, Modifier.padding(top = 10.dp, start = 4.dp), color = TextSecondary)
        if (action.seasonTabs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                action.seasonTabs.forEach { season ->
                    // Chip de temporada do Nuvio: a selecionada é pill BRANCA com texto
                    // escuro; as outras, pill escura com texto claro (antes: AccentFill).
                    val selected = season == action.selectedSeason
                    Text("Temporada $season", color = if (selected) TextOnLight else TextPrimary,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(30.dp))
                            .background(if (selected) Color.White else Surface2)
                            .focusProperties { canFocus = false }.clickable { action.onSeasonPick?.invoke(season) }
                            .padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            return@Column
        }
        if (action.isSource) {
            val stream = action.stream ?: com.aotv.app.data.remote.StremioStream(action.label, "", action.addon,
                qualityRank = com.aotv.app.data.remote.qualityRankOf(action.label))
            SourceCard(releaseInfo(stream, movieTitle, action.providers), "", TextTertiary, onClick,
                selected = action.active, focusedByDpad = focused, manualFocus = true,
                note = action.sub.ifBlank { null })
            return@Column
        }
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val shortWindow = !com.aotv.app.ui.common.LocalIsTv.current && config.screenHeightDp < 480
        val compactEpisode = action.episode && !com.aotv.app.ui.common.LocalIsTv.current && !shortWindow &&
            (config.screenWidthDp < 600 ||
                androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f)
        val trailing: @Composable () -> Unit = {
            Icon(when { action.active -> Icons.Default.Check; action.keepOpen -> Icons.Default.Tune; else -> Icons.Default.SkipNext },
                null, tint = if (focused) TextOnLight else if (action.active) Accent else TextTertiary, modifier = Modifier.size(20.dp))
        }
        if (compactEpisode) Column(rowMod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EpisodeThumb(action, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerActionCopy(action, fg, subColor, Modifier.weight(1f), titleSize = 16.sp)
                trailing()
            }
        } else Row(rowMod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (action.episode) EpisodeThumb(action,
                Modifier.width(if (shortWindow) 104.dp else 146.dp).aspectRatio(16f / 9f))
            PlayerActionCopy(action, fg, subColor, Modifier.weight(1f), titleSize = if (action.episode) 16.sp else 15.sp)
            trailing()
        }

    }
}

/** "T1 E1" que o Nuvio grava na miniatura do episódio — o nosso vem no início do sub
 * ("T1 E1 · 45 min · ★ 8.5"), então é só extrair, sem campo novo no TvPlayerAction. */
private val EPISODE_CODE = Regex("""T(\d+)\s*E(\d+)""")

private fun episodeCode(sub: String): String? =
    EPISODE_CODE.find(sub)?.let { "T${it.groupValues[1]} E${it.groupValues[2]}" }

/** Miniatura 16:9 do episódio com o selo "T1 E1" no canto inferior esquerdo, como no Nuvio. */
@Composable
private fun EpisodeThumb(action: TvPlayerAction, modifier: Modifier) {
    val code = episodeCode(action.sub)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Surface2)) {
        AsyncImage(action.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (code != null) Text(code, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun PlayerActionCopy(action: TvPlayerAction, foreground: Color, secondary: Color, modifier: Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit = 15.sp) {
    val shortWindow = !com.aotv.app.ui.common.LocalIsTv.current && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    Column(modifier) {
        Text(action.label, color = foreground, fontSize = titleSize, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (action.sub.isNotBlank()) Text(action.sub, color = secondary, fontSize = 13.sp, lineHeight = 18.sp,
            maxLines = if (shortWindow) 1 else 3, overflow = TextOverflow.Ellipsis)
        if (action.detail.isNotBlank()) Text(action.detail, color = secondary, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = if (shortWindow) 1 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}
