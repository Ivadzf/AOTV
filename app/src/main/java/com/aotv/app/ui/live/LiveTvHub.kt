package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aotv.app.ui.theme.*

/**
 * Hub da Live TV no formato do Nuvio+IPTV (Lote 9, v1.19.0).
 *
 * Referência: prints do Nuvio+IPTV (Live TV com barra superior de 6 ações e
 * grupos de canais em cartões expansíveis). Só vale p/ o CELULAR: na TV o
 * [LiveTvScreen] mantém a renderização atual (grade plana), para não quebrar
 * o D-pad — ver o `isTv` lá.
 *
 * Nada do que já existia some: favoritos, recentes, grupos, busca, guia em
 * grade, catch-up, canal reserva, lembrar faixa de áudio, formato Xtream e
 * edição de fonte continuam onde estavam. O hub só aparece na vista padrão
 * (sem filtro de grupo/favoritos/recentes e sem busca); com filtro ou busca
 * a tela segue com a grade plana de canais de antes.
 */

/** Rota do On Demand do Xtream (Lote 9, Frente 2 — cria a tela desta rota). */
const val LIVE_ON_DEMAND_ROUTE = "live_ondemand"

/**
 * Barra superior do hub, com as 6 ações da referência: Busca, Guia,
 * Calendário, On Demand, Esportes e Configurações.
 *
 * - Busca: abre/fecha o campo de busca da Live TV (que já existe).
 * - Guia: alterna p/ a grade do guia ([LiveGuideScreen], que já existe).
 * - Calendário: abre a Biblioteca, onde vive o calendário que já existe.
 * - On Demand: abre a rota [LIVE_ON_DEMAND_ROUTE] (Frente 2).
 * - Esportes: volta p/ a vista de canais, onde vive a seção de esportes
 *   que já existe (logo abaixo das pílulas de filtro).
 * - Configurações: abre as fontes/config da Live TV (que já existem).
 */
@Composable
fun LiveHubTopBar(
    guide: Boolean,
    onSearch: () -> Unit,
    onGuide: () -> Unit,
    onCalendar: () -> Unit,
    onVod: () -> Unit,
    onSports: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        HubAction(Icons.Default.Search, "Busca", false, onSearch)
        HubAction(Icons.Default.GridView, "Guia", guide, onGuide)
        HubAction(Icons.Default.CalendarMonth, "Calendário", false, onCalendar)
        HubAction(Icons.Default.Movie, "On Demand", false, onVod)
        HubAction(Icons.Default.SportsSoccer, "Esportes", false, onSports)
        HubAction(Icons.Default.Settings, "Configurações", false, onSettings)
    }
}

@Composable
private fun RowScope.HubAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon, label, tint = if (selected) Accent else TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            color = if (selected) Accent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ícone da categoria do grupo (Lote 9): Esportes=bola, Filmes=clausura,
 * Notícias=jornal, Kids=sorriso, Música=nota, Documentário=bússola,
 * resto=TV. O nome do grupo vem da lista (ex. "BR | Esportes"), então o
 * casamento é por palavra-chave, sem acento sensível e nos dois idiomas
 * (as listas trazem nomes em PT e em EN). "Filmes" usa [Icons.Default.Movie]
 * (clapperboard) — o Material não tem um ícone de "clausura" separado.
 */
fun liveGroupIcon(groupName: String): ImageVector {
    val g = groupName.lowercase()
    return when {
        "esport" in g || "sport" in g || "futebol" in g || "soccer" in g -> Icons.Default.SportsSoccer
        "filme" in g || "movie" in g || "cinema" in g -> Icons.Default.Movie
        "notic" in g || "notíc" in g || "news" in g || "jornal" in g -> Icons.Default.Newspaper
        "kid" in g || "infantil" in g || "criança" in g || "crianca" in g -> Icons.Default.SentimentSatisfied
        "music" in g || "músic" in g || "musica" in g -> Icons.Default.MusicNote
        "document" in g -> Icons.Default.Explore
        else -> Icons.Default.Tv
    }
}

/**
 * Grupos de canais em cartões expansíveis (só no celular, vista padrão).
 *
 * Cada cartão: ícone da categoria, nome do grupo, faixa horizontal com até
 * 4 logos de canais do grupo, "N canais", estrela p/ favoritar o grupo e
 * chevron p/ expandir e mostrar os canais do grupo (as mesmas fileiras de
 * [ChannelRow] da grade plana). Grupos favoritos aparecem PRIMEIRO (na
 * ordem da lista entre eles) e com a estrela preenchida — o favorito é o
 * nome do grupo como aparece na tela (ver `live_group_favorites_v1` em
 * PlatformPrefs).
 */
@Composable
fun LiveGroupCards(
    groupedRows: Map<String, List<LiveRow>>,
    favorites: List<String>,
    now: Long,
    onPlay: (String) -> Unit,
    onFavoriteChannel: (String) -> Unit,
    onToggleGroupFavorite: (String) -> Unit,
    showLogos: Boolean = true,
    showNames: Boolean = true,
    showNumbers: Boolean = false,
    cleanGroups: Boolean = false,
    // PIN parental por grupo (Lote 11, v1.21.0): grupos trancados com o PIN
    // dos pais. O desbloqueio vale só p/ a sessão (a tela guarda os grupos
    // liberados em memória). null = sem cadeado (TV, que não tem essa UI).
    lockedGroups: List<String> = emptyList(),
    unlockedGroups: Set<String> = emptySet(),
    onToggleGroupLock: ((String) -> Unit)? = null,
    onRequestUnlock: ((String) -> Unit)? = null,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    // Favoritos primeiro (estável: mantém a ordem da lista dentro de cada
    // metade, sem reordenar a cada recomposição).
    val ordered = remember(groupedRows, favorites) {
        groupedRows.entries.sortedWith(compareByDescending { it.key in favorites })
    }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ordered, key = { it.key }) { (group, rows) ->
            val isFav = group in favorites
            val isOpen = group in expanded
            // PIN por grupo (Lote 11): trancado e sem liberação na sessão =
            // o cartão não expande nem mostra logos/canais; tocar pede o PIN.
            val isLocked = group in lockedGroups && group !in unlockedGroups
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCard2)
                    .clickable {
                        if (isLocked) {
                            onRequestUnlock?.invoke(group)
                        } else {
                            expanded = if (isOpen) expanded - group else expanded + group
                        }
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Surface2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(liveGroupIcon(group), group, tint = TextSecondary, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            group, color = TextPrimary, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Logos dos canais (só os que têm logo). Grupo
                            // trancado não mostra logo — o conteúdo fica
                            // escondido até o PIN liberar.
                            if (!isLocked) {
                                rows.mapNotNull { it.channel.logo.ifBlank { null } }.take(4).forEach { logo ->
                                    Box(
                                        Modifier.size(width = 36.dp, height = 24.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(Surface2),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            logo, null, contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                if (isLocked) "Trancado com PIN"
                                else if (rows.size == 1) "1 canal" else "${rows.size} canais",
                                color = TextSecondary, fontSize = 13.sp, maxLines = 1,
                            )
                        }
                    }
                    // Cadeado do grupo (Lote 11): tranca/destranca com o PIN
                    // dos pais (o fluxo do PIN vive na tela, via callback).
                    if (onToggleGroupLock != null) {
                        IconButton(
                            onClick = { onToggleGroupLock(group) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                if (group in lockedGroups) Icons.Default.Lock else Icons.Default.LockOpen,
                                if (group in lockedGroups) "Destrancar $group (pede o PIN)" else "Trancar $group com o PIN dos pais",
                                tint = if (group in lockedGroups) Accent else TextTertiary,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleGroupFavorite(group) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            if (isFav) "Tirar $group dos favoritos" else "Favoritar $group",
                            tint = if (isFav) Gold else TextTertiary,
                        )
                    }
                    Icon(
                        if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        if (isOpen) "Recolher $group" else "Expandir $group",
                        tint = TextTertiary, modifier = Modifier.size(24.dp),
                    )
                }
                if (isOpen && !isLocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            ChannelRow(
                                row, now,
                                onClick = { onPlay(row.channel.id) },
                                onFavorite = { onFavoriteChannel(row.channel.id) },
                                showLogos = showLogos, showNames = showNames,
                                showNumbers = showNumbers, cleanGroups = cleanGroups,
                            )
                        }
                    }
                }
            }
        }
    }
}
