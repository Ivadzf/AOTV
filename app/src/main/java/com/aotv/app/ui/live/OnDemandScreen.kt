package com.aotv.app.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
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
import com.aotv.app.data.live.VodItem
import com.aotv.app.data.live.VodSeriesDetail
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

/** On Demand Xtream (Lote 9, v1.19.0): filmes e séries da conta já configurada. */
@Composable
fun OnDemandScreen(
    onBack: () -> Unit,
    onSources: () -> Unit,
    onPlayMovie: (String, String) -> Unit,
    onPlayEpisode: (String, String, Int, Int) -> Unit,
    viewModel: OnDemandViewModel = hiltViewModel(),
    /** Deep link da Início (acréscimo v1.26.0): abre a aba e o detalhe do item. */
    initialTab: String = "",
    initialItemId: String = "",
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    val pad = com.aotv.app.ui.common.pagePadding()
    var searching by remember { mutableStateOf(false) }
    val hasDetail = v.detail.movie != null || v.detail.series != null

    // Deep link: 1x por entrada na tela; o ViewModel consome e não reabre.
    LaunchedEffect(initialTab, initialItemId) {
        if (initialItemId.isNotBlank()) viewModel.openById(initialTab, initialItemId)
    }

    // Voltar com detalhe aberto fecha o detalhe; sem detalhe, sai da tela.
    BackHandler(enabled = hasDetail) { viewModel.closeDetail() }

    Column(Modifier.fillMaxSize().background(Background).topInsetsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = pad, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) IconButton(
                onClick = { if (hasDetail) viewModel.closeDetail() else onBack() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextPrimary)
            }
            com.aotv.app.ui.common.PageHeader(
                "On Demand", Modifier.weight(1f),
                sub = when {
                    !v.hasXtream -> "Filmes e séries da conta Xtream"
                    v.loading && v.total == 0 -> "Carregando…"
                    else -> "${v.total} ${if (v.tab == OnDemandViewModel.SERIES) "séries" else "filmes"} · ${v.sourceName}"
                },
            )
            if (v.hasXtream && !hasDetail) {
                com.aotv.app.ui.common.GlassCircleButton(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    { searching = !searching; if (!searching) viewModel.setQuery("") },
                    contentDescription = if (searching) "Fechar busca" else "Buscar título",
                )
                com.aotv.app.ui.common.GlassCircleButton(
                    Icons.Default.Refresh, viewModel::refresh,
                    contentDescription = if (v.loading) "Atualizando" else "Atualizar", busy = v.loading,
                )
            }
        }

        if (!v.hasXtream) {
            com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.Movie, "Nenhuma conta Xtream configurada",
                "Adicione uma conta Xtream em Fontes de canais para ver os filmes e séries On Demand dela aqui.",
                action = { com.aotv.app.ui.common.AotvButton("Configurar conta Xtream", onSources, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY) },
            )
            return@Column
        }

        if (hasDetail) {
            OnDemandDetail(
                movie = v.detail.movie,
                series = v.detail.series,
                loading = v.detail.loading,
                onPlayMovie = { item ->
                    viewModel.streamUrl(item)?.let { onPlayMovie(it, item.title) }
                },
                onPlayEpisode = { ep ->
                    viewModel.episodeUrl(ep)?.let {
                        onPlayEpisode(it, v.detail.series?.series?.title.orEmpty(), ep.season, ep.episode)
                    }
                },
            )
            return@Column
        }

        if (searching) {
            OutlinedTextField(
                value = v.query, onValueChange = viewModel::setQuery, singleLine = true,
                placeholder = { Text("Nome do filme ou da série") },
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
            item { com.aotv.app.ui.common.FilterChipPill("Filmes", v.tab == OnDemandViewModel.MOVIES, { viewModel.setTab(OnDemandViewModel.MOVIES) }, icon = Icons.Default.Movie) }
            item { com.aotv.app.ui.common.FilterChipPill("Séries", v.tab == OnDemandViewModel.SERIES, { viewModel.setTab(OnDemandViewModel.SERIES) }, icon = Icons.Default.Tv) }
            item { com.aotv.app.ui.common.FilterChipPill("Todas ${v.total}", v.category.isBlank(), { viewModel.setCategory("") }) }
            items(v.categories, key = { it.id }) { cat ->
                com.aotv.app.ui.common.FilterChipPill(
                    "${cat.name} ${v.categoryCounts[cat.id] ?: 0}",
                    v.category == cat.id, { viewModel.setCategory(cat.id) },
                )
            }
        }

        if (v.loading && v.total == 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(BackgroundCard2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Carregando On Demand…", color = TextSecondary, fontSize = 13.sp)
            }
        }

        v.error?.let {
            Text(it, color = DangerText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad, vertical = 4.dp))
        }

        if (v.items.isEmpty()) {
            Text(
                when {
                    v.loading -> "Carregando…"
                    v.query.isNotBlank() || v.category.isNotBlank() -> "Nada com esse filtro."
                    v.tab == OnDemandViewModel.SERIES -> "Esta conta não tem séries no On Demand."
                    else -> "Esta conta não tem filmes no On Demand."
                },
                color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(pad),
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(v.items, key = { it.id }) { item ->
                com.aotv.app.ui.common.MediaPosterCard(
                    title = item.title,
                    imageUrl = item.cover.ifBlank { null },
                    subtitle = item.year.ifBlank { item.category.ifBlank { null } },
                    onClick = {
                        if (item.isSeries) viewModel.openSeries(item) else viewModel.openMovie(item)
                    },
                )
            }
        }
    }
}

/**
 * Detalhe simples do On Demand: capa e sinopse SÓ quando a API devolveu
 * (sem dado, sem seção). Filme tem o botão Assistir; série lista os
 * episódios por temporada e o toque toca o episódio no player.
 */
@Composable
private fun OnDemandDetail(
    movie: VodItem?,
    series: VodSeriesDetail?,
    loading: Boolean,
    onPlayMovie: (VodItem) -> Unit,
    onPlayEpisode: (com.aotv.app.data.live.VodEpisode) -> Unit,
) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val item = movie ?: series?.series ?: return
    val episodes = series?.episodes.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(width = 120.dp, height = 180.dp).clip(RoundedCornerShape(14.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                if (item.cover.isNotBlank()) AsyncImage(item.cover, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(item.title.take(2).uppercase(), color = TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val meta = listOf(item.year, item.rating, item.category).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = TextSecondary, fontSize = 14.sp)
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("Carregando detalhe…", color = TextTertiary, fontSize = 13.sp)
                    }
                }
                if (movie != null) {
                    com.aotv.app.ui.common.AotvButton(
                        "Assistir", { onPlayMovie(movie) },
                        icon = Icons.Default.PlayArrow, tone = com.aotv.app.ui.common.ButtonTone.PRIMARY,
                    )
                }
            }
        }
        if (item.plot.isNotBlank()) {
            Text(item.plot, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        }
        if (series != null) {
            if (episodes.isEmpty() && !loading) {
                Text("Esta série não trouxe episódios.", color = TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
            episodes.groupBy { it.season }.toSortedMap().forEach { (season, eps) ->
                Text("Temporada $season", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                eps.sortedBy { it.episode }.forEach { ep ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundCard2)
                            .clickable { onPlayEpisode(ep) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "E${ep.episode} · ${ep.title}",
                                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (ep.duration.isNotBlank()) Text(ep.duration, color = TextTertiary, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
