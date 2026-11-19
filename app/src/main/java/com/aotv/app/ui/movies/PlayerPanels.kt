package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.FilterChipPill
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
    /**
     * Selo de contagem (Lote 15 item 8): quantas opções reais existem nesse
     * idioma (ex.: 8 legendas em inglês). 0 = sem selo. Só conta o que existe
     * de fato — nunca inventado.
     */
    val count: Int = 0,
    /**
     * Chave do idioma p/ o painel de legendas estilo Nuvio (Lote 17 item 14):
     * entradas com o mesmo [langKey] viram um grupo na coluna "Idiomas"
     * ("eng", "pob"...). Vazio = linha comum, sem agrupar — é o que o builder
     * antigo emite (uma linha por idioma), e essas linhas continuam
     * renderizando como antes.
     */
    val langKey: String = "",
    /**
     * Selo da fonte da entrada ("OpenSubtitles v3", "PenguPlay", nome do addon,
     * "SubDL", "No vídeo"). Vazio = sem selo (o `sub` continua como antes).
     */
    val source: String = "",
    /**
     * ID da legenda na fonte (ex.: "14183659", "penguplay-vidlink-english-1").
     * Vazio = sem linha de ID.
     */
    val entryId: String = "",
    val onRun: () -> Unit,
)

/** Chip de filtro no topo de um painel (Lote 15 item 2 — folha de Fontes estilo Nuvio). */
data class SourceFilterChip(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** Chave estável da fileira (padrão = label). Provider e qualidade podem ter o mesmo rótulo ("4K"). */
    val key: String = label,
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
    /** Vidro fosco (Lote 4): painel translúcido com o toggle `pref_panel_blur` ligado. */
    frosted: Boolean = false,
    /** Chips de filtro no topo do painel (Lote 15 item 2 — folha de Fontes). Vazio = sem chips. */
    filterChips: List<SourceFilterChip> = emptyList(),
    /** Cursor D-pad na fileira de chips (Lote 23 item 13): -1 = foco na lista; >= 0 = índice do chip com o anel. */
    chipCursor: Int = -1,
    /**
     * Painel de legendas em duas colunas (Lote 17 item 14, estilo Nuvio).
     * Só vale fora da TV e quando a lista tem entradas com [TvPlayerAction.langKey];
     * sem entradas, cai na lista comum. Na TV o D-pad usa o índice plano da
     * lista, então o modo não vale lá — a lista comum já mostra selo + ID
     * quando os campos estão preenchidos (ver [PlayerActionCopy]).
     */
    subtitleMode: Boolean = false,
) {
    if (television) TvScaled { PlayerActionPanelBody(title, showCount, actions, focusIdx, pinned, movieTitle, onPick, onDismiss, true, frosted, filterChips, chipCursor) }
    else if (subtitleMode && actions.any { it.langKey.isNotBlank() }) SubtitlePanelBody(title, actions, movieTitle, onPick, onDismiss, frosted)
    else PlayerActionPanelBody(title, showCount, actions, focusIdx, pinned, movieTitle, onPick, onDismiss, false, frosted, filterChips, chipCursor)
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
    frosted: Boolean = false,
    filterChips: List<SourceFilterChip> = emptyList(),
    chipCursor: Int = -1,
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
        // Lote 4: vidro fosco no painel de fontes (e nos demais painéis do
        // player, que usam o mesmo SidePanel) com o toggle ligado.
        frosted = frosted,
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
        // Lote 23 item 13: a contagem é "N fontes", não só o número.
        titleTrailing = if (showCount && sourceCount > 0) ({
            Text(
                "$sourceCount fontes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(AccentFill).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }) else null,
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(focusIdx, actions.size) {
            if (actions.isNotEmpty()) listState.animateScrollToItem(focusIdx.coerceIn(0, actions.lastIndex))
        }
        val chipRowState = rememberLazyListState()
        LaunchedEffect(chipCursor, filterChips.size) {
            if (chipCursor >= 0 && filterChips.isNotEmpty()) chipRowState.animateScrollToItem(chipCursor.coerceIn(0, filterChips.lastIndex))
        }
        // Chips de filtro (Lote 15 item 2 + Lote 23 item 13): Todos + um por
        // provider presente + um por qualidade. No celular é toque; na TV o
        // D-pad navega a fileira (chipCursor, anel branco no chip com foco).
        if (filterChips.isNotEmpty()) {
            LazyRow(
                state = chipRowState,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filterChips, key = { _, chip -> chip.key }) { i, chip ->
                    val ring = if (i == chipCursor) Modifier.border(2.dp, Color.White, RoundedCornerShape(999.dp)) else Modifier
                    Box(ring) { FilterChipPill(chip.label, chip.selected, onClick = chip.onClick) }
                }
            }
        }
        // Lote 23 item 13: o filtro zerou a lista — estado vazio honesto em
        // vez de uma lista só com os botões de vista.
        if (showCount && sourceCount == 0) {
            Text("Nenhuma fonte neste filtro", color = TextTertiary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(actions) { i, act ->
                SourcePanelRow(act, focused = i == focusIdx, movieTitle = movieTitle, onClick = { onPick(act) })
            }
        }
    }
}

/**
 * Painel de legendas estilo Nuvio (Lote 17 item 14, v1.27.0): duas colunas —
 * "Idiomas" com a contagem real de opções + "Legendas" com as entradas do
 * idioma escolhido, cada uma com o selo da fonte (OpenSubtitles v3, PenguPlay,
 * nome do addon, SubDL, "No vídeo") e o ID (ex.: 14183659). Referência: print
 * do Nuvio (workspace/user/files/1001055086_1_s18x.jpg).
 *
 * CONTRATO p/ o builder de `subActions` em MoviePlayerScreen.kt (o coordenador
 * integra — este arquivo não toca nele): UMA ação por legenda externa, com
 * [TvPlayerAction.langKey] = chave do idioma (ex.: "eng"), [TvPlayerAction.source]
 * = fonte real e [TvPlayerAction.entryId] = id; o `sub` da entrada fica VAZIO
 * (a fonte já vai no selo — senão duplica). As linhas de grupo antigas (uma
 * por idioma, sem langKey, ex.: as embutidas sem langKey) ficam como linhas
 * comuns acima das colunas. "Desativada" fica no topo; os Ajustes e a 2ª
 * legenda ficam abaixo das colunas, como linhas comuns. A seleção da coluna
 * de idiomas começa no idioma da legenda ativa, senão no primeiro.
 */
@Composable
private fun SubtitlePanelBody(
    title: String,
    actions: List<TvPlayerAction>,
    movieTitle: String,
    onPick: (TvPlayerAction) -> Unit,
    onDismiss: () -> Unit,
    frosted: Boolean = false,
) {
    val entries = actions.filter { it.langKey.isNotBlank() }
    val langKeys = entries.map { it.langKey }.distinct()
    // Linhas de grupo do builder antigo (uma por idioma, sem langKey): não dá
    // p/ agrupar com segurança, então ficam como linhas comuns acima.
    val groupRows = actions.filter { it.langKey.isBlank() && it.count > 0 && !it.isSource && it.seasonTabs.isEmpty() }
    val offAction = actions.firstOrNull { it.label == "Desativada" && it.langKey.isBlank() && it.count == 0 }
    val trailing = actions.filter { it !in entries && it !in groupRows && it != offAction }
    val activeLang = entries.firstOrNull { it.active }?.langKey
    var selectedLang by remember(langKeys, activeLang) { mutableStateOf(activeLang ?: langKeys.firstOrNull().orEmpty()) }
    val selectedEntries = entries.filter { it.langKey == selectedLang }
    SidePanel(
        title = title,
        onDismiss = onDismiss,
        width = 600.dp,
        titleSize = 26.sp,
        dismissLabel = "Fechar",
        frosted = frosted,
        sub = "Toque em uma opção para aplicar",
    ) {
        if (offAction != null) SourcePanelRow(offAction, focused = false, movieTitle = movieTitle, onClick = { onPick(offAction) })
        groupRows.forEach { row ->
            SourcePanelRow(row, focused = false, movieTitle = movieTitle, onClick = { onPick(row) })
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Coluna "Idiomas".
            Column(Modifier.weight(0.42f)) {
                com.aotv.app.ui.common.Eyebrow("Idiomas", color = TextSecondary)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(langKeys, key = { it }) { key ->
                        val group = entries.filter { it.langKey == key }
                        val label = group.first().label
                        val n = maxOf(group.size, group.maxOfOrNull { it.count } ?: 0)
                        SubtitleLangRow(label, n, selected = key == selectedLang, onClick = { selectedLang = key })
                    }
                }
            }
            // Coluna "Legendas" — entradas do idioma escolhido.
            Column(Modifier.weight(1f)) {
                com.aotv.app.ui.common.Eyebrow("Legendas", color = TextSecondary)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(selectedEntries) { _, act ->
                        SubtitleEntryRow(act, onClick = { onPick(act) })
                    }
                }
            }
        }
        if (trailing.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(trailing) { _, act ->
                    SourcePanelRow(act, focused = false, movieTitle = movieTitle, onClick = { onPick(act) })
                }
            }
        }
    }
}

/** Linha da coluna "Idiomas": nome + selo de contagem. A selecionada é pill branca, como no Nuvio. */
@Composable
private fun SubtitleLangRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = if (selected) TextOnLight else Color.White, fontSize = 15.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(count.toString(), color = if (selected) Color.White else TextOnLight,
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = ManropeFontFamily,
                modifier = Modifier.clip(CircleShape)
                    .background(if (selected) Color(0xFF222222) else Color.White)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/** Selo da fonte (pill escura, como "OpenSubtitles v3" / "PenguPlay" no Nuvio). */
@Composable
private fun SourceBadge(text: String, onLight: Boolean) {
    Text(text, color = if (onLight) TextOnLight else Color.White, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (onLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Entrada da coluna "Legendas": selo da fonte + idioma + ID (+ detalhe, ex.: release do SubDL). */
@Composable
private fun SubtitleEntryRow(action: TvPlayerAction, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (action.active) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f))
            .then(if (action.active) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (action.source.isNotBlank()) SourceBadge(action.source, onLight = false)
            Text(action.label, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (action.entryId.isNotBlank()) Text(action.entryId, color = TextTertiary, fontSize = 12.sp,
                lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (action.detail.isNotBlank()) Text(action.detail, color = TextTertiary, fontSize = 13.sp,
                lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action.active) Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(20.dp))
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
            // Selo de contagem (Lote 15 item 8): círculo branco com o número,
            // como o seletor do Nuvio. Na linha com foco (fundo branco), o
            // selo vira escuro p/ continuar legível.
            if (action.count > 0) {
                Text(
                    action.count.toString(),
                    color = if (focused) Color.White else TextOnLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ManropeFontFamily,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (focused) Color(0xFF222222) else Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Icon(when { action.active -> Icons.Default.Check; action.keepOpen -> Icons.Default.Tune; else -> Icons.Default.SkipNext },
                null, tint = if (focused) TextOnLight else if (action.active) Accent else TextTertiary, modifier = Modifier.size(20.dp))
        }
        if (compactEpisode) Column(rowMod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EpisodeThumb(action, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerActionCopy(action, fg, subColor, Modifier.weight(1f), titleSize = 16.sp, focused = focused)
                trailing()
            }
        } else Row(rowMod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (action.episode) EpisodeThumb(action,
                Modifier.width(if (shortWindow) 104.dp else 146.dp).aspectRatio(16f / 9f))
            PlayerActionCopy(action, fg, subColor, Modifier.weight(1f), titleSize = if (action.episode) 16.sp else 15.sp, focused = focused)
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
    titleSize: androidx.compose.ui.unit.TextUnit = 15.sp, focused: Boolean = false) {
    val shortWindow = !com.aotv.app.ui.common.LocalIsTv.current && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    Column(modifier) {
        // Selo da fonte (Lote 17 item 14): só aparece quando o builder
        // preencheu [TvPlayerAction.source] — nas linhas antigas não muda nada.
        if (action.source.isNotBlank()) SourceBadge(action.source, onLight = focused)
        Text(action.label, color = foreground, fontSize = titleSize, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (action.sub.isNotBlank()) Text(action.sub, color = secondary, fontSize = 13.sp, lineHeight = 18.sp,
            maxLines = if (shortWindow) 1 else 3, overflow = TextOverflow.Ellipsis)
        // ID da legenda na fonte (ex.: "14183659") — mesma linha do painel novo.
        if (action.entryId.isNotBlank()) Text(action.entryId, color = secondary, fontSize = 12.sp, lineHeight = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action.detail.isNotBlank()) Text(action.detail, color = secondary, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = if (shortWindow) 1 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}
