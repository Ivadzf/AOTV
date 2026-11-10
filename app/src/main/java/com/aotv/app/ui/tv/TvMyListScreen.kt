package com.aotv.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.ui.movies.MinhaListaViewModel
import com.aotv.app.ui.theme.*

/**
 * Minha Lista da TV no desenho do webOS (screens/mylist.js). Continuar assistindo em fileira e os
 * salvos em grade de 8 com filtro Tudo, Filmes e Séries. Segurar OK remove, com confirmação.
 */
@Composable
fun TvMyListScreen(
    onItemClick: (MovieItem) -> Unit,
    vm: MinhaListaViewModel = hiltViewModel(),
) {
    TvText { TvMyListScreenBody(onItemClick, vm) }
}

@Composable
private fun TvMyListScreenBody(
    onItemClick: (MovieItem) -> Unit,
    vm: MinhaListaViewModel = hiltViewModel(),
) {
    val saved by vm.watchlist.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val artwork by vm.recentArtwork.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    val gs = rememberLazyGridState()

    fun type(raw: String) = runCatching { MediaType.valueOf(raw) }.getOrDefault(MediaType.MOVIE)
    fun stub(id: Int, t: String, title: String, poster: String, year: String) =
        MovieItem(id, "", title, "", poster, "", year, 0.0, type = type(t))

    confirm?.let { (t, body, ok) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(t) }, text = { Text(body) },
            confirmButton = { TvButton("Remover", { ok(); confirm = null }, primary = true) },
            dismissButton = { TvButton("Cancelar", { confirm = null }) },
            containerColor = BackgroundCard,
        )
    }

    val list = if (filter.isBlank()) saved else saved.filter { it.type == filter }
    val hasBoth = saved.any { it.type == "MOVIE" } && saved.any { it.type == "SERIES" }
    TvScrollArea(inset = 2.r) {
        LazyVerticalGrid(
            GridCells.Fixed(8), Modifier.fillMaxSize().background(Background), state = gs,
            contentPadding = PaddingValues(start = tvPad, end = tvPad, bottom = 5.r),
            horizontalArrangement = Arrangement.spacedBy(1.3.r),
            verticalArrangement = Arrangement.spacedBy(2.r),
        ) {
            val full = { s: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope -> GridItemSpan(s.maxLineSpan) }
            item(span = full, key = "head") {
                Column(Modifier.padding(top = 2.6.r).tvScrollTop { gs.animateScrollToItem(0) }) {
                    TvEyebrow("Biblioteca")
                    Text("Minha Lista", color = TextPrimary, fontSize = 2.6.rs, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (saved.isEmpty() && recent.isEmpty()) {
                item(span = full) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.r), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(Icons.Default.BookmarkBorder, null, tint = TextTertiary, modifier = Modifier.size(3.6.r))
                        Text("Sua lista está vazia", color = TextSecondary, fontSize = 1.5.rs, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.r))
                        Text(
                            "Salve filmes e séries no detalhe para vê-los aqui. O que você começar a assistir aparece em Continuar assistindo.",
                            color = TextTertiary, fontSize = 1.1.rs, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 0.6.r).widthIn(max = 42.r),
                        )
                    }
                }
                return@LazyVerticalGrid
            }
            if (recent.isNotEmpty()) item(span = full, key = "cw") {
                // A fileira está dentro da grade, então desconta a margem da grade dos dois lados.
                val extra = (tvPadPx * 2).toInt()
                Box(Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
                }) {
                    TvRail("Continuar assistindo", eyebrow = "Recentes", trailing = { TvCountPill(count(recent.size)) }) {
                        items(recent, key = { "${it.tmdbId}:${it.type}" }) { h ->
                            val ep = h.lastEpisode.replace(":", " ")
                            TvContinueCard(
                                stub(h.tmdbId, h.type, h.title, h.posterUrl, ""), { onItemClick(stub(h.tmdbId, h.type, h.title, h.posterUrl, "")) },
                                artwork = artwork[com.aotv.app.data.local.posterKey(type(h.type), h.tmdbId)],
                                progress = if (h.durationMs > 0) h.positionMs.toFloat() / h.durationMs else null,
                                remaining = if (h.durationMs > h.positionMs) ((h.durationMs - h.positionMs) / 60_000L).toInt() else null,
                                episode = ep,
                                onLongClick = { confirm = Triple("Remover de Continuar assistindo?", h.title) { vm.removeFromContinue(h.tmdbId, h.type) } },
                            )
                        }
                    }
                }
            }
            item(span = full, key = "savedHead") {
                Column(Modifier.padding(top = 1.r).tvRow()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            TvEyebrow("Minha Lista")
                            Text("Salvos", color = TextPrimary, fontSize = 1.6.rs, fontWeight = FontWeight.Bold)
                        }
                        TvCountPill(count(saved.size))
                    }
                    if (hasBoth) Row(Modifier.padding(top = 1.r), horizontalArrangement = Arrangement.spacedBy(0.75.r)) {
                        listOf("" to "Tudo", "MOVIE" to "Filmes", "SERIES" to "Séries").forEach { (k, l) ->
                            TvChip(l, filter == k, { filter = k })
                        }
                    }
                }
            }
            if (list.isEmpty()) item(span = full) {
                Text(
                    if (saved.isEmpty()) "Nada salvo ainda. Abra um título e use Minha Lista para guardar aqui." else "Nenhum título salvo nesse filtro.",
                    color = TextTertiary, fontSize = 1.05.rs, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(1.r))
                        .background(Color(0x08FFFFFF), RoundedCornerShape(1.r)).padding(1.5.r),
                )
            } else {
                items(list.size, key = { "s:${list[it].tmdbId}:${list[it].type}" }) { i ->
                    val w = list[i]
                    TvPoster(
                        stub(w.tmdbId, w.type, w.title, w.posterUrl, w.year), { onItemClick(stub(w.tmdbId, w.type, w.title, w.posterUrl, w.year)) },
                        width = Dp.Unspecified,
                        onLongClick = { confirm = Triple("Tirar da Minha Lista?", w.title) { vm.removeFromWatchlist(w.tmdbId, w.type) } },
                    )
                }
                item(span = full) { Text("Segure OK em um título para remover", color = TextTertiary, fontSize = 0.85.rs) }
            }
        }
    }
}

private val tvPadPx: Float @Composable get() = with(androidx.compose.ui.platform.LocalDensity.current) { tvPad.toPx() }

private fun count(n: Int) = if (n == 1) "1 título" else "$n títulos"

private fun remaining(pos: Long, dur: Long): String? {
    if (dur <= 0 || pos <= 0) return null
    val left = ((dur - pos) / 60_000).toInt()
    return when {
        left <= 0 -> "Quase no fim"
        left < 60 -> "Faltam $left min"
        else -> "Faltam ${left / 60}h ${left % 60}min"
    }
}
