package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aotv.app.data.live.LiveChannel
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.ui.movies.RecordProgramDialog
import com.aotv.app.ui.theme.*

/**
 * Guia em grade (Lote 7). Canais nas linhas, horários nas colunas, programa
 * atual destacado, linha vermelha no "agora". Reutiliza o EPG já carregado
 * ([LiveTvViewModel.View.epg]); sem guia, a linha mostra só o canal, sem
 * inventar programação. Respeita os filtros e a busca da aba Ao vivo, porque
 * usa as mesmas fileiras ([LiveTvViewModel.View.rows]).
 *
 * Toques: no canal ou no programa atual abre ao vivo; no programa passado COM
 * catch-up abre a gravação ([livePlayerCatchupRoute]); no programa passado sem
 * catch-up ou futuro mostra o detalhe, sem tocar nada.
 *
 * A grade é a mesma tela no celular e na TV (a rota "Ao vivo" não tem tela
 * própria de TV): as células são clicáveis e focáveis, mas o scroll
 * horizontal por controle remoto não foi testado — ver o caveat no relatório.
 */
@Composable
fun LiveGuideScreen(
    onPlay: (String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
    /** Rota de catch-up pronta (ver [livePlayerCatchupRoute]); sem ele, passado com catch-up cai no ao vivo. */
    onPlayCatchup: ((String) -> Unit)? = null,
) {
    val v by viewModel.view.collectAsStateWithLifecycle()
    // Lote 8 (v1.18.0): janela visível continua 4 h, mas os limites vêm das
    // prefs — não rola para antes de (agora − horas atrás) nem para depois de
    // (agora + dias à frente). windowStart = agora − 1 h + deslocamento, então
    // o mínimo é −(horasAtrás − 1) e o máximo é 1 + diasÀFrente × 24.
    // Sem EPG não há programação — nunca inventar (a grade mostra "Sem guia").
    val minShift = -(v.guideHoursBack - 1)
    val maxShift = 1 + v.guideDaysAhead * 24
    var shiftHours by remember { mutableIntStateOf(0) }
    val shift = shiftHours.coerceIn(minShift, maxShift)
    val now = v.now
    val windowStart = now - HOUR_MS + shift * HOUR_MS
    val windowEnd = windowStart + WINDOW_MS
    val totalWidth = HOUR_WIDTH_DP * WINDOW_HOURS
    // Um só ScrollState para o cabeçalho e todas as linhas: as colunas ficam alinhadas.
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_WIDTH_DP.toPx() }
    val nowXdp = ((now - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    // Ao abrir (e a cada deslocamento), rola para o "agora" ficar visível.
    LaunchedEffect(shift) { scroll.scrollTo((((now - windowStart).toFloat() / HOUR_MS) * hourPx).toInt().coerceAtLeast(0)) }
    var detail by remember { mutableStateOf<Pair<LiveChannel, LiveProgram>?>(null) }
    // Lote 8 (v1.18.0): programa a gravar (diálogo da Frente 2).
    var record by remember { mutableStateOf<Pair<LiveChannel, LiveProgram>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = com.aotv.app.ui.common.pagePadding(), vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.aotv.app.ui.common.FilterChipPill("‹ 1 h", false, { shiftHours = (shiftHours - 1).coerceAtLeast(minShift) }, icon = Icons.Default.ChevronLeft)
            com.aotv.app.ui.common.FilterChipPill("Agora", shift == 0, { shiftHours = 0.coerceIn(minShift, maxShift) })
            com.aotv.app.ui.common.FilterChipPill("1 h ›", false, { shiftHours = (shiftHours + 1).coerceAtMost(maxShift) }, icon = Icons.Default.ChevronRight)
            Text(
                "${hhmm(windowStart)} – ${hhmm(windowEnd)}", color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHANNEL_WIDTH_DP))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(totalWidth).height(28.dp)) {
                    var h = windowStart - windowStart % HOUR_MS
                    while (h <= windowEnd) {
                        Text(
                            hhmm(h), color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = ((h - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp + 6.dp, y = 4.dp),
                        )
                        h += HOUR_MS
                    }
                    Box(
                        Modifier.offset(x = nowXdp).width(2.dp).fillMaxHeight().background(LiveRed),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(v.rows, key = { it.channel.id }) { row ->
                val channel = row.channel
                val programs = (v.epg[channel.id].orEmpty()).filter { it.stop > windowStart && it.start < windowEnd }
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.width(CHANNEL_WIDTH_DP).fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp)).background(BackgroundCard2)
                            .clickable { onPlay(channel.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(width = 40.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                            if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(channel.name.take(2).uppercase(), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(channel.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(totalWidth).fillMaxHeight()) {
                            if (programs.isEmpty()) {
                                Text(
                                    "Sem guia para este canal", color = TextTertiary, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 10.dp).align(Alignment.CenterStart),
                                )
                            }
                            programs.forEach { p ->
                                val isCurrent = p.isNow(now)
                                val isPast = p.stop <= now
                                val hasCatchup = channel.catchupDays > 0 && isPast &&
                                    (now - p.start) <= channel.catchupDays * 86_400_000L
                                GuideProgramCell(
                                    program = p, now = now, windowStart = windowStart,
                                    hasCatchup = hasCatchup,
                                    onClick = {
                                        when {
                                            isCurrent -> onPlay(channel.id)
                                            hasCatchup -> {
                                                val route = livePlayerCatchupRoute(channel.id, p.start / 1000, p.stop / 1000)
                                                if (onPlayCatchup != null) onPlayCatchup(route) else onPlay(channel.id)
                                            }
                                            else -> detail = channel to p
                                        }
                                    },
                                )
                            }
                            Box(Modifier.offset(x = nowXdp).width(2.dp).fillMaxHeight().background(LiveRed))
                        }
                    }
                }
            }
        }
    }

    detail?.let { pair ->
        val channel = pair.first
        val program = pair.second
        val isCurrent = program.isNow(now)
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = BackgroundCard,
            title = { Text(program.title, color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${channel.name} · ${hhmm(program.start)} até ${hhmm(program.stop)}", color = TextSecondary, fontSize = 14.sp)
                    if (program.desc.isNotBlank()) Text(program.desc, color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                when {
                    isCurrent -> TextButton(onClick = { onPlay(channel.id); detail = null }) { Text("Assistir ao vivo", color = Accent, fontWeight = FontWeight.ExtraBold) }
                    // Lote 8 (v1.18.0): gravar programa futuro ou atual (passado não grava).
                    program.stop > now -> TextButton(onClick = { record = channel to program; detail = null }) { Text("Gravar", color = Accent, fontWeight = FontWeight.ExtraBold) }
                    else -> TextButton(onClick = { detail = null }) { Text("Fechar", color = TextSecondary) }
                }
            },
            dismissButton = {
                if (isCurrent || program.stop > now) TextButton(onClick = { detail = null }) { Text("Fechar", color = TextSecondary) }
            },
        )
    }

    // Lote 8 (v1.18.0): diálogo de agendar gravação (Frente 2).
    record?.let { pair ->
        val channel = pair.first
        val program = pair.second
        RecordProgramDialog(
            channelId = channel.id, channelName = channel.name, channelUrl = channel.url,
            channelHeaders = channel.headers, programTitle = program.title,
            startMs = program.start, stopMs = program.stop,
            onDismiss = { record = null },
            onScheduled = { record = null },
        )
    }
}

@Composable
private fun GuideProgramCell(
    program: LiveProgram,
    now: Long,
    windowStart: Long,
    onClick: () -> Unit,
    hasCatchup: Boolean = false,
) {
    val isCurrent = program.isNow(now)
    val isPast = program.stop <= now
    val visibleStart = maxOf(program.start, windowStart)
    val visibleEnd = minOf(program.stop, windowStart + WINDOW_MS)
    if (visibleEnd <= visibleStart) return
    val x = ((visibleStart - windowStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    val w = ((visibleEnd - visibleStart).toFloat() / HOUR_MS * HOUR_WIDTH_DP.value).dp
    val bg = when {
        // Dourado: programa passado com catch-up disponível.
        hasCatchup -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
        isCurrent -> AccentSoft
        isPast -> Surface2
        else -> BackgroundCard2
    }
    val titleColor = when {
        isCurrent -> TextPrimary
        isPast -> TextTertiary
        else -> TextSecondary
    }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(end = 2.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (isCurrent) Modifier.border(1.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(program.title, color = titleColor, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${hhmm(program.start)}–${hhmm(program.stop)}", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private const val HOUR_MS = 3_600_000L
private const val WINDOW_HOURS = 4
private val WINDOW_MS = WINDOW_HOURS * HOUR_MS
private val HOUR_WIDTH_DP = 150.dp
private val CHANNEL_WIDTH_DP = 132.dp

/** Rota do player ao vivo com catch-up. [startSec]/[stopSec] em epoch segundos. */
fun livePlayerCatchupRoute(channelId: String, startSec: Long, stopSec: Long): String =
    "movies_live_player/$channelId?start=$startSec&stop=$stopSec"
