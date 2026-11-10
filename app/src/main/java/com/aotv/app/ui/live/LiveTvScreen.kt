package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** Aba "Ao vivo". Canais das listas e contas, com o que está passando agora e a seguir. */
@Composable
fun LiveTvScreen(
    onPlay: (String) -> Unit,
    onSources: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var searching by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.aotv.app.ui.common.PageHeader(
                "Ao vivo", Modifier.weight(1f),
                sub = when {
                    !v.hasSources -> "Listas M3U e contas Xtream"
                    v.loading && v.total == 0 -> "Carregando canais…"
                    v.epgLoading -> "${v.total} canais · carregando o guia…"
                    else -> "${v.total} canais"
                },
            )
            if (v.hasSources) {
                com.aotv.app.ui.common.GlassCircleButton(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    { searching = !searching; if (!searching) viewModel.setQuery("") },
                    contentDescription = if (searching) "Fechar busca" else "Buscar canal",
                )
                com.aotv.app.ui.common.GlassCircleButton(
                    Icons.Default.Refresh, viewModel::reload,
                    contentDescription = if (v.loading) "Atualizando canais" else "Atualizar canais", busy = v.loading,
                )
            }
            com.aotv.app.ui.common.GlassCircleButton(Icons.Default.Settings, onSources, contentDescription = "Fontes de canais")
        }

        if (!v.hasSources) {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.LiveTv, "Nenhuma fonte de canais",
                "Adicione uma lista M3U ou uma conta Xtream para ver os canais ao vivo aqui, com o guia de programação.",
                action = { com.aotv.app.ui.common.AotvButton("Adicionar fonte", onSources, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY) },
            )
            return@Column
        }

        if (searching) {
            OutlinedTextField(
                value = v.query, onValueChange = viewModel::setQuery, singleLine = true,
                placeholder = { Text("Nome do canal ou grupo") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                ),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { com.aotv.app.ui.common.FilterChipPill("Todos ${v.total}", v.filter == LiveTvViewModel.ALL, { viewModel.setFilter(LiveTvViewModel.ALL) }) }
            if (v.favoriteCount > 0) item {
                com.aotv.app.ui.common.FilterChipPill("Favoritos ${v.favoriteCount}", v.filter == LiveTvViewModel.FAV, { viewModel.setFilter(LiveTvViewModel.FAV) }, icon = Icons.Default.Star)
            }
            if (v.recentCount > 0) item {
                com.aotv.app.ui.common.FilterChipPill("Recentes ${v.recentCount}", v.filter == LiveTvViewModel.RECENT, { viewModel.setFilter(LiveTvViewModel.RECENT) })
            }
            if (v.groups.size > 1) items(v.groups, key = { "g:" + it.first }) { (g, n) ->
                com.aotv.app.ui.common.FilterChipPill("$g $n", v.filter == "g:$g", { viewModel.setFilter("g:$g") })
            }
        }

        v.error?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }

        if (v.rows.isEmpty()) {
            Text(
                if (v.loading) "Carregando canais…" else "Nenhum canal aqui.",
                color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(pad),
            )
            return@Column
        }
        // Celular em uma coluna, tela larga em mais de uma.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(v.rows, key = { it.channel.id }) { row ->
                ChannelRow(row, v.now, onClick = { onPlay(row.channel.id) }, onFavorite = { viewModel.toggleFavorite(row.channel.id) })
            }
        }
    }
}

@Composable
private fun ChannelRow(row: LiveRow, now: Long, onClick: () -> Unit, onFavorite: () -> Unit) {
    val c = row.channel
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard2)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            if (c.logo.isNotBlank()) AsyncImage(c.logo, c.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            else Text(c.name.take(2).uppercase(), color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(c.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = row.now
            if (n != null) {
                Text(n.title, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.aotv.app.ui.common.ProgressBar(n.progress(now), Modifier.fillMaxWidth(0.9f), height = 3.dp)
            } else if (c.group.isNotBlank()) {
                Text(LiveTvViewModel.groupLabel(c), color = TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            row.next?.let { nx ->
                Text("A seguir ${hhmm(nx.start)} ${nx.title}", color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                if (row.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (row.favorite) "Tirar ${c.name} dos favoritos" else "Favoritar ${c.name}",
                tint = if (row.favorite) Gold else TextTertiary,
            )
        }
    }
}

internal fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(ms))
