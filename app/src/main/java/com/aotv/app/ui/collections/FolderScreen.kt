package com.aotv.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.common.topInsetsPadding
import com.aotv.app.ui.theme.*

@Composable
fun FolderScreen(
    onBack: () -> Unit,
    onItemClick: (MovieItem) -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Background)) {
        FolderHeader(st, onBack)
        when {
            !st.found -> com.aotv.app.ui.common.AotvEmptyState(
                Icons.Default.FolderOff, "Pasta não encontrada", "A coleção mudou ou foi apagada. Volte e abra de novo.",
                modifier = Modifier.padding(24.dp),
            )
            st.tabbed -> TabbedContent(st, viewModel, onItemClick)
            else -> RowsContent(st, viewModel, onItemClick)
        }
    }
}

@Composable
private fun FolderHeader(st: FolderViewModel.UiState, onBack: () -> Unit) {
    val pad = com.aotv.app.ui.common.pagePadding()
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        st.backdrop?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Background.copy(alpha = 0.35f), Background.copy(alpha = 0.6f), Background))))
        Row(Modifier.topInsetsPadding().padding(start = 8.dp, top = 6.dp)) {
            if (!com.aotv.app.ui.common.LocalIsTv.current) {
                com.aotv.app.ui.common.GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, contentDescription = "Voltar")
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = pad, vertical = 12.dp)) {
            if (st.collectionTitle.isNotBlank()) com.aotv.app.ui.common.Eyebrow(st.collectionTitle)
            if (st.logo != null) {
                AsyncImage(
                    model = st.logo, contentDescription = st.title, contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart, modifier = Modifier.height(64.dp).widthIn(max = 320.dp).padding(top = 4.dp),
                )
            } else {
                Text(st.title, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TabbedContent(st: FolderViewModel.UiState, vm: FolderViewModel, onItemClick: (MovieItem) -> Unit) {
    val pad = com.aotv.app.ui.common.pagePadding()
    val tab = st.tabs.getOrNull(st.selected) ?: return
    if (st.tabs.size > 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(st.tabs, key = { _, t -> t.key }) { i, t ->
                com.aotv.app.ui.common.FilterChipPill(t.label, i == st.selected, { vm.select(i) }, busy = t.loading && i == st.selected)
            }
        }
    }
    val grid = rememberLazyGridState()
    // A grade troca de aba sem levar a rolagem da aba anterior.
    LaunchedEffect(tab.key) { grid.scrollToItem(0) }
    val nearEnd by remember(tab.key) {
        derivedStateOf {
            val li = grid.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, tab.items.size, tab.key) { if (nearEnd) vm.loadMore(tab.key) }
    when {
        tab.loading && tab.items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        tab.items.isEmpty() -> Text(
            tab.error ?: "Nada aqui por enquanto.", color = TextSecondary, fontSize = 15.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = pad, vertical = 24.dp),
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            state = grid,
            contentPadding = PaddingValues(start = pad, end = pad, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tab.items, key = { "${it.type}:${it.tmdbId}" }) { item -> PosterItem(item, onItemClick) }
            if (tab.loadingMore) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun RowsContent(st: FolderViewModel.UiState, vm: FolderViewModel, onItemClick: (MovieItem) -> Unit) {
    val pad = com.aotv.app.ui.common.pagePadding()
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(st.tabs.filter { !it.isAll }, key = { it.key }) { tab ->
            Column {
                com.aotv.app.ui.common.SectionHeader(title = tab.label)
                when {
                    tab.loading && tab.items.isEmpty() -> com.aotv.app.ui.common.SkeletonRail()
                    tab.items.isEmpty() -> Text(
                        tab.error ?: "Nada aqui por enquanto.", color = TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = pad, vertical = 8.dp),
                    )
                    else -> {
                        val row = rememberLazyListState()
                        val nearEnd by remember { derivedStateOf { (row.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= row.layoutInfo.totalItemsCount - 4 } }
                        LaunchedEffect(nearEnd, tab.items.size) { if (nearEnd) vm.loadMore(tab.key) }
                        LazyRow(state = row, contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(tab.items, key = { "${it.type}:${it.tmdbId}" }) { item -> PosterItem(item, onItemClick, Modifier.width(128.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterItem(item: MovieItem, onItemClick: (MovieItem) -> Unit, modifier: Modifier = Modifier) {
    com.aotv.app.ui.common.PosterCard(item = item, onClick = { onItemClick(item) }, modifier = modifier)
}
