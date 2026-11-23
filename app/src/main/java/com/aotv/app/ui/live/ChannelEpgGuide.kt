package com.aotv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextSecondary
import com.aotv.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Guia EPG do canal sob o preview (v1.45.0, item 3 — ideia do NuvioAstra,
 * `EpgGuide`, reimplementada: nada de código do Astra aqui).
 *
 * Programa atual no topo com barra de progresso, próximos abaixo, passados
 * acima (rolar p/ cima). Os dados já existem no app ([LiveProgram] e o
 * agora/a seguir do `LiveTvRepository`): este guia NÃO busca nada — ele lê
 * a lista que o ViewModel já carrega (`st.epg`) e só pede atualização
 * quando o programa atual termina ([onCurrentProgramEnded], que o pai liga
 * em `refreshEpgIfStale`). 1 request por canal, novo request só no fim do
 * programa atual; o relógio é o `now` do pai (tick de 30 s, igual ao
 * `LivePlayerScreen`).
 *
 * Melhor na TV/tablet (painel ao lado/abaixo do preview); no celular, sob
 * o preview em paisagem ou numa aba — o pai escolhe onde montar.
 */
@Composable
fun ChannelEpgGuide(
    programs: List<LiveProgram>,
    now: Long,
    onProgramClick: (LiveProgram) -> Unit = {},
    onCurrentProgramEnded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sorted = remember(programs) { programs.sortedBy { it.start } }
    val current = remember(sorted, now) { sorted.firstOrNull { it.isNow(now) } }
    val past = remember(sorted, now) { sorted.filter { it.stop <= now } }
    val upcoming = remember(sorted, now) { sorted.filter { it.start > now } }

    // Fim do programa atual: pede atualização 1x (o pai decide se busca).
    // O `current` muda quando a lista nova chega — o efeito rearma sozinho.
    LaunchedEffect(current?.start, current?.stop) {
        val cur = current ?: return@LaunchedEffect
        val wait = (cur.stop - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(wait)
        onCurrentProgramEnded()
    }

    val listState = rememberLazyListState()
    // Abre rolado até o programa atual (os passados ficam acima).
    LaunchedEffect(sorted) {
        if (past.isNotEmpty()) runCatching { listState.scrollToItem(past.size) }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    fun hhmm(ms: Long): String = timeFmt.format(Date(ms))

    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE080B12))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(past, key = { "past:${it.start}" }) { program ->
            EpgRow(
                program = program,
                time = "${hhmm(program.start)}–${hhmm(program.stop)}",
                dimmed = true,
                onClick = { onProgramClick(program) },
            )
        }
        if (current != null) {
            item(key = "now:${current.start}") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onProgramClick(current) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "AGORA", color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(Accent).padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                        Text(
                            current.title, color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { current.progress(now) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Text(
                        "${hhmm(current.start)} até ${hhmm(current.stop)}",
                        color = TextSecondary, fontSize = 12.sp,
                    )
                    if (current.desc.isNotBlank()) {
                        Text(current.desc, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        items(upcoming, key = { "next:${it.start}" }) { program ->
            EpgRow(
                program = program,
                time = "${hhmm(program.start)}–${hhmm(program.stop)}",
                dimmed = false,
                onClick = { onProgramClick(program) },
            )
        }
        if (sorted.isEmpty()) {
            item(key = "empty") {
                Text("Sem guia para este canal", color = TextTertiary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EpgRow(program: LiveProgram, time: String, dimmed: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            program.title,
            color = if (dimmed) TextTertiary else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(time, color = TextTertiary, fontSize = 12.sp)
    }
}
