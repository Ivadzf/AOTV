package com.aotv.app.ui.lote24

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.FilterChipPill
import com.aotv.app.ui.common.MediaPosterCard
import com.aotv.app.ui.common.SectionHeader
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.Gold
import com.aotv.app.ui.theme.OnAccent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary

/**
 * Fileiras da Home do Lote 24 (v1.34.0): MK Channel (item 5) e Peak Cinema
 * (item 6). São composables puros sobre os dados que a Home já tem — a
 * MoviesHomeScreen passa Continuar/Minha Lista/em alta e os callbacks de
 * play/detalhe existentes.
 */

/**
 * Fileira "MK Channel": liga e já tem algo tocando. Três fontes em chips
 * (Up next / Minha Lista / Maratona); o cartão mostra o alvo da fonte
 * escolhida e o botão de play toca direto ([onPlay] — episódio vai direto
 * ao player, filme abre o detalhe). Sem alvo na fonte, o chip fica
 * desabilitado na prática (mostra "Nada por aqui ainda").
 */
@Composable
fun MkChannelRow(
    targets: Map<MkChannelSource, MkChannelTarget?>,
    selectedSource: MkChannelSource,
    onSelectSource: (MkChannelSource) -> Unit,
    onPlay: (MkChannelTarget) -> Unit,
    onOpenItem: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = targets[selectedSource]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = "MK Channel", eyebrow = "LIGA E JÁ TEM ALGO TOCANDO")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MkChannelSource.entries.forEach { source ->
                val hasTarget = targets[source] != null
                FilterChipPill(
                    label = source.label + if (hasTarget) "" else " · —",
                    selected = source == selectedSource,
                    onClick = { onSelectSource(source) },
                )
            }
        }
        if (target != null) {
            val item = target.item
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCard)
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .clickable(onClick = { onOpenItem(item) })
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MediaPosterCard(
                    title = item.title,
                    imageUrl = item.posterUrl.ifBlank { null },
                    onClick = { onOpenItem(item) },
                    modifier = Modifier.width(72.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(target.label, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.rating > 0) {
                        Text("★ ${"%.1f".format(item.rating)}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = { onPlay(target) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, "Tocar agora", tint = OnAccent, modifier = Modifier.size(26.dp))
                }
            }
        } else {
            Text(
                "Nada por aqui ainda — assista algo ou salve na Minha Lista.",
                color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * Fileira "Peak Cinema": modo "melhores cenas" por gênero/humor. Chips de
 * humor + fileira de pôsteres do preset escolhido, curada por
 * [pickPeakTitles] sobre os catálogos da Home. Sem títulos no corte, a
 * fileira some (o chamador só a inclui quando há títulos).
 */
@Composable
fun PeakCinemaRow(
    items: List<MovieItem>,
    selectedMoodId: String,
    onSelectMood: (String) -> Unit,
    onItemClick: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Junção (Lote 24): o humor é pref por perfil (peak_cinema_mood_v1) —
    // a escolha persiste entre aberturas, não só na sessão.
    val preset = PeakPreset.fromId(selectedMoodId) ?: PEAK_CINEMA_PRESETS.first()
    val titles = pickPeakTitles(items, preset)
    if (titles.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = "Peak Cinema", eyebrow = "MELHORES CENAS POR HUMOR")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PEAK_CINEMA_PRESETS.forEach { option ->
                FilterChipPill(
                    label = option.label,
                    selected = option.id == preset.id,
                    onClick = { onSelectMood(option.id) },
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(titles, key = { "${it.type}:${it.tmdbId}" }) { item ->
                MediaPosterCard(
                    title = item.title,
                    imageUrl = item.posterUrl.ifBlank { null },
                    onClick = { onItemClick(item) },
                    subtitle = if (item.rating > 0) "★ ${"%.1f".format(item.rating)}" else null,
                    modifier = Modifier.width(110.dp).height(165.dp),
                )
            }
        }
        Text(
            "Curadoria local: ${preset.label} com nota ${"%.1f".format(preset.minRating)}+ nos catálogos da Início.",
            color = TextTertiary, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * Cabeçalho de pasta da Home (item 3, junção): agrupa as fileiras da pasta
 * sob um título recolhível. Tocar recolhe/expande as fileiras dela (o
 * estado é da sessão — a pasta em si persiste nas prefs).
 */
@Composable
fun FolderHeaderRow(
    folder: HomeFolder24,
    rowCount: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Folder, null, tint = Accent, modifier = Modifier.size(20.dp))
        Text(
            folder.name, color = TextPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (rowCount == 1) "1 fileira" else "$rowCount fileiras",
            color = TextTertiary, fontSize = 13.sp,
        )
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            if (collapsed) "Expandir pasta" else "Recolher pasta",
            tint = TextTertiary, modifier = Modifier.size(20.dp),
        )
    }
}
