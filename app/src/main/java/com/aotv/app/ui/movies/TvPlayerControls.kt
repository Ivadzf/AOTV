package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aotv.app.ui.theme.*
import com.aotv.app.ui.tv.r
import com.aotv.app.ui.tv.rs

/** Título, selos, barra e controles ficam juntos na base, como no player do webOS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvPlayerControls(
    title: String,
    subtitle: String,
    clock: String,
    resolution: String,
    formatTags: List<String>,
    playbackInfo: String,
    position: Long,
    duration: Long,
    buffered: Long,
    onSeekTo: (Long) -> Unit,
    actions: List<PlayerChipItem>,
    focusedIndex: Int,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .45f), .23f to Color.Transparent,
        .48f to Color.Transparent, 1f to Color.Black.copy(alpha = .94f),
    ))) {
        Column(Modifier.align(Alignment.TopEnd).padding(top = 2.2.r, end = 3.5.r), horizontalAlignment = Alignment.End) {
            Text(clock.substringBefore('·').trim(), color = Color.White, fontSize = 1.75.rs,
                lineHeight = 1.95.rs, fontWeight = FontWeight.Bold)
            val end = clock.substringAfter('·', "").trim().replaceFirst("termina", "Termina")
            if (end.isNotBlank()) Text(end, color = TextSecondary, fontSize = 1.rs, modifier = Modifier.padding(top = .3.r))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.r, vertical = 2.4.r)) {
            Text(title, color = Color.White, fontSize = 3.rs, lineHeight = 3.36.rs, letterSpacing = (-.09).rs,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.7f))
            FlowRow(Modifier.fillMaxWidth(.85f).padding(top = .9.r),
                horizontalArrangement = Arrangement.spacedBy(.6.r), verticalArrangement = Arrangement.spacedBy(.4.r)) {
                if (resolution.isNotBlank()) PlayerFormatBadge(resolution)
                formatTags.take(6).forEach { PlayerFormatBadge(it) }
            }
            val info = listOf(subtitle, playbackInfo).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (info.isNotBlank()) Text(info, color = TextSecondary, fontSize = 1.05.rs,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.85f).padding(top = .75.r))
            TvPlayerTimeline(position, duration, buffered, onSeekTo, Modifier.padding(top = 1.1.r))
            Row(Modifier.fillMaxWidth().padding(top = .55.r), verticalAlignment = Alignment.CenterVertically) {
                val row = rememberLazyListState()
                LaunchedEffect(focusedIndex, actions.size) {
                    if (focusedIndex in actions.indices && row.layoutInfo.visibleItemsInfo.none { it.index == focusedIndex }) {
                        row.animateScrollToItem((focusedIndex - 2).coerceAtLeast(0))
                    }
                }
                LazyRow(Modifier.weight(1f), state = row, horizontalArrangement = Arrangement.spacedBy(.6.r),
                    verticalAlignment = Alignment.CenterVertically) {
                    itemsIndexed(actions) { i, action ->
                        TvPlayerActionChip(action, focusedIndex == i)
                        if (action.main) Spacer(Modifier.width(.7.r))
                    }
                }
                Text(formatTime(position) + " / " + if (duration > 0) formatTime(duration) else "AO VIVO",
                    color = Color.White, fontSize = 1.2.rs, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 1.5.r), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlayerFormatBadge(text: String) {
    Row(Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(.35.r))
        .padding(horizontal = .65.r, vertical = .3.r), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(.35.r)) {
        val dolby = text in listOf("Dolby Vision", "Atmos", "TrueHD", "DD+")
        if (dolby) Icon(com.aotv.app.ui.common.DolbyIcon, null, tint = Color.White, modifier = Modifier.size(1.2.r))
        Text(if (text == "Dolby Vision") "Vision" else text, color = Color.White, fontSize = .95.rs,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TvPlayerActionChip(action: PlayerChipItem, focused: Boolean) {
    val filled = focused || action.main
    val tint = if (filled) TextOnLight else if (action.active) Accent else Color.White
    Box(contentAlignment = Alignment.Center) {
        Row(Modifier.height(if (action.main) 4.5.r else 3.4.r)
            .then(if (action.main) Modifier.width(4.5.r) else Modifier.widthIn(min = 3.4.r))
            .clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.Transparent)
            .focusProperties { canFocus = false }.clickable(role = Role.Button, onClick = action.onRun)
            .padding(horizontal = if (action.main) 0.r else .9.r),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(action.icon, action.label, tint = tint, modifier = Modifier.size(if (action.main) 2.1.r else 1.65.r))
            if (focused && !action.main) Text(action.label, color = tint, fontSize = 1.05.rs, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = .55.r), maxLines = 1)
            else if (action.value.isNotBlank()) Text(action.value, color = tint, fontSize = .95.rs, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = .4.r), maxLines = 1)
        }
        if (action.active && !focused && !action.main) Box(Modifier.align(Alignment.BottomCenter)
            .padding(bottom = .35.r).size(.35.r).background(Accent, CircleShape))
    }
}

@Composable
private fun TvPlayerTimeline(position: Long, duration: Long, buffered: Long, onSeekTo: (Long) -> Unit, modifier: Modifier) {
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffer = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var width by remember { mutableFloatStateOf(1f) }
    Box(modifier.fillMaxWidth().height(1.8.r).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(duration) { detectTapGestures { if (duration > 0) onSeekTo((duration * (it.x / width).coerceIn(0f, 1f)).toLong()) } },
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(.32.r).background(Color.White.copy(alpha = .24f), CircleShape))
        Box(Modifier.fillMaxWidth(buffer).height(.32.r).background(Color.White.copy(alpha = .35f), CircleShape))
        Box(Modifier.fillMaxWidth(frac).height(.32.r).background(Color.White, CircleShape))
        Box(Modifier.fillMaxWidth(frac), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.requiredSize(.75.r).background(Color.White, CircleShape))
        }
    }
}
