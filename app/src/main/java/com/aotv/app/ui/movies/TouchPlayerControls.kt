package com.aotv.app.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.common.DolbyIcon
import com.aotv.app.ui.theme.*

/** Composição inferior do webOS com alvos de toque e opções acessíveis em telas pequenas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TouchPlayerControls(
    title: String, subtitle: String, clock: String, resolution: String, tags: List<String>, info: String,
    isPlaying: Boolean, position: Long, duration: Long, buffered: Long, videoUrl: String,
    onToggle: () -> Unit, onBack: () -> Unit, onForward: () -> Unit, onSeek: (Long) -> Unit,
    actions: List<PlayerChipItem>, focusedIndex: Int,
    /** Tocar na barra busca (pref `playerTapSeek`). Desligado, só o arraste busca. */
    tapSeek: Boolean = true,
) {
    var more by remember { mutableStateOf(false) }
    var controlsHeight by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val font = density.fontScale.coerceAtLeast(1f)
    val options = actions.withIndex().filter { !it.value.main }
    val optionState = rememberLazyListState()
    LaunchedEffect(focusedIndex, options.size) {
        val index = options.indexOfFirst { it.index == focusedIndex }
        if (index >= 0 && optionState.layoutInfo.visibleItemsInfo.none { it.index == index }) optionState.animateScrollToItem(index)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .72f), .30f to Color.Black.copy(alpha = .72f),
        .46f to Color.Transparent, 1f to Color.Black.copy(alpha = .94f),
    )).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 360.dp || maxHeight.value / font < 280f
        val singleRow = landscape && maxWidth.value / font >= 600f && !compact
        val optionRow = !singleRow && !compact
        val showMore = !singleRow && !optionRow && options.isNotEmpty()
        val side = if (maxWidth >= 600.dp) 24.dp else 16.dp
        val times = formatTime(position) + " / " + if (duration > 0) formatTime(duration) else "AO VIVO"
        val estimatedTimeWidth = with(density) { 12.sp.toDp() } * (times.length * .6f)
        val timeSeparate = maxWidth - side * 2 < 180.dp + (if (showMore) 56.dp else 0.dp) + estimatedTimeWidth
        val timeHeight = if (timeSeparate) with(density) { 18.sp.toDp() } + 4.dp else 0.dp
        val fixedHeight = 56.dp + 48.dp + 20.dp + timeHeight + (if (optionRow) 56.dp else 0.dp)
        val topReserve = 64.dp + WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val metadataHeight = (maxHeight - fixedHeight - topReserve).coerceIn(0.dp, if (landscape) 140.dp else 220.dp)
        val titleLine = with(density) { 26.sp.toDp() }
        val tagHeight = with(density) { 15.sp.toDp() } + 6.dp
        val detailLine = with(density) { 17.sp.toDp() }
        val titleLines = if (!landscape && metadataHeight >= titleLine * 2 + tagHeight + 6.dp) 2 else 1
        var available = metadataHeight - titleLine * titleLines
        val showTags = available >= tagHeight + 6.dp
        if (showTags) available -= tagHeight + 6.dp
        val showDetail = available >= detailLine + 6.dp
        val controlsTop = (1f - controlsHeight / with(density) { maxHeight.toPx() }.coerceAtLeast(1f)).coerceIn(0f, 1f)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Transparent, (controlsTop - .14f).coerceAtLeast(0f) to Color.Transparent,
            controlsTop to Color.Black.copy(alpha = .78f), 1f to Color.Black.copy(alpha = .94f),
        )))
        if (maxHeight.value / font >= 340f) Column(
            Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .padding(top = 66.dp, end = side), horizontalAlignment = Alignment.End,
        ) {
            Text(clock.substringBefore('·').trim(), color = Color.White, fontSize = 16.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            val end = clock.substringAfter('·', "").trim().replaceFirst("termina", "Termina")
            if (end.isNotBlank()) Text(end, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { controlsHeight = it.height.toFloat() }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = side, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (metadataHeight >= titleLine) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                if (showTags) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(resolution).filter { it.isNotBlank() } + tags.take(5)).distinct().forEach { label ->
                        Row(Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (label in listOf("Dolby Vision", "Atmos", "TrueHD", "DD+")) {
                                Icon(DolbyIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(if (label == "Dolby Vision") "Vision" else label, color = Color.White,
                                fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val detail = listOf(subtitle, info).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (showDetail && detail.isNotBlank()) Text(detail, color = TextSecondary, fontSize = 12.sp,
                    lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayerSeekRow(position, duration, onSeek, videoUrl, buffered, showTimes = false, tapSeek = tapSeek)
            if (timeSeparate) Text(times, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.align(Alignment.End))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(48.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pausar" else "Assistir", tint = TextOnLight, modifier = Modifier.size(28.dp))
                    }
                }
                CtlButton(Icons.Default.Replay10, "Voltar 10 segundos", 48.dp, onClick = onBack)
                CtlButton(Icons.Default.Forward10, "Avançar 10 segundos", 48.dp, onClick = onForward)
                if (singleRow) LazyRow(Modifier.weight(1f), state = optionState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(options, key = { it.index }) { (index, action) -> TouchPlayerActionIcon(action, index == focusedIndex) }
                } else Spacer(Modifier.weight(1f))
                if (showMore) CtlButton(Icons.Default.MoreHoriz, "Opções de reprodução", 48.dp, onClick = { more = true })
                if (!timeSeparate) Text(times, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (optionRow) LazyRow(Modifier.fillMaxWidth(), state = optionState,
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(options, key = { it.index }) { (index, action) -> TouchPlayerActionIcon(action, index == focusedIndex) }
            }
        }
    }
    if (more) ModalBottomSheet(onDismissRequest = { more = false }, containerColor = BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Reprodução", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { (_, action) ->
                TouchPlayerAction(action.copy(onRun = { more = false; action.onRun() }), false, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TouchPlayerActionIcon(action: PlayerChipItem, focused: Boolean) {
    val foreground = if (focused) TextOnLight else if (action.active) Accent else Color.White
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(action.label) } }, state = rememberTooltipState()) {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(CircleShape)
                .background(if (focused) Color.White else Color.Transparent)
                .clickable(role = Role.Button, onClick = action.onRun).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                Icon(action.icon, action.label, tint = foreground, modifier = Modifier.size(24.dp))
                if (action.value.isNotBlank()) Text(action.value, color = foreground, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (action.active && !focused) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                .size(4.dp).background(Accent, CircleShape))
        }
    }
}

@Composable
private fun TouchPlayerAction(action: PlayerChipItem, focused: Boolean, modifier: Modifier = Modifier) {
    val foreground = if (focused) TextOnLight else if (action.active) Accent else Color.White
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(if (focused) Color.White else Color.White.copy(alpha = .09f))
        .clickable(role = Role.Button, onClick = action.onRun).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(action.icon, null, tint = foreground, modifier = Modifier.size(22.dp))
        Text(action.label, color = foreground, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
