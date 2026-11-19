// ─── ui/movies/StatsScreen.kt (Lote 23, v1.33.0) ───────────────────────────
// Tela "Estatísticas": heatmap estilo GitHub (grade 7x12 em Compose, sem
// biblioteca externa), os 4 números, seletor de período e o recap
// exportável como imagem (Bitmap via Canvas, salvo pela ViewModel).
// Tudo agrega o histórico que já existe; período sem histórico mostra
// zeros e "Sem dados neste período" — nada é inventado.

package com.aotv.app.ui.movies

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aotv.app.data.stats.StatsSummary
import com.aotv.app.data.stats.formatHours
import com.aotv.app.data.stats.formatWeekOverWeek
import com.aotv.app.data.stats.heatmapGrid
import com.aotv.app.data.stats.weekdayNamePt
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.Background
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.ManropeFontFamily
import com.aotv.app.ui.theme.SoraFontFamily
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary
import java.time.LocalDate

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    vm: StatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.consumeExportedUri() }
    val exportedUri = state.exportedUri
    LaunchedEffect(exportedUri) {
        val uri = exportedUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareLauncher.launch(Intent.createChooser(send, "Compartilhar recap"))
    }
    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
            Text(
                "Estatísticas",
                fontFamily = SoraFontFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, color = TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        // Seletor de período (o valor fica em prefs.statsPeriod).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("7d" to "7 dias", "30d" to "30 dias", "90d" to "90 dias", "all" to "Tudo").forEach { periodAndLabel ->
                val period = periodAndLabel.first
                val label = periodAndLabel.second
                FilterChip(
                    selected = state.period == period,
                    onClick = { vm.setPeriod(period) },
                    label = { Text(label, fontFamily = ManropeFontFamily, fontSize = 13.sp) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Column
        }
        val summary = state.summary
        if (summary == null) {
            Text("Sem dados neste período.", fontFamily = ManropeFontFamily, fontSize = 14.sp, color = TextSecondary)
            return@Column
        }
        if (!summary.hasData) {
            Text(
                "Sem dados neste período.",
                fontFamily = ManropeFontFamily, fontSize = 14.sp, color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Horas assistidas", formatHours(summary.hoursWatched), Modifier.weight(1f))
            StatCard("Títulos", summary.titlesWatched.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Episódios", summary.episodesWatched.toString(), Modifier.weight(1f))
            StatCard("Sequência", "${summary.streakDays} dia(s)", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatCard(
            "Dia mais ativo",
            summary.bestWeekday?.let { weekdayNamePt(it) } ?: "—",
            Modifier.fillMaxWidth(),
        )
        val weekText = formatWeekOverWeek(summary.weekOverWeekPct)
        if (weekText != null) {
            Spacer(Modifier.height(8.dp))
            Text(weekText, fontFamily = ManropeFontFamily, fontSize = 13.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Últimas 12 semanas",
            fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        HeatmapGrid(summary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cada quadrado é um dia; quanto mais títulos, mais forte a cor.",
            fontFamily = ManropeFontFamily, fontSize = 12.sp, color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.exportRecap() },
            enabled = !state.exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.exporting) "Exportando…" else "Exportar recap como imagem",
                fontFamily = ManropeFontFamily, fontSize = 14.sp,
            )
        }
        val message = state.exportMessage
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, fontFamily = ManropeFontFamily, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundCard)
            .padding(14.dp),
    ) {
        Text(label, fontFamily = ManropeFontFamily, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontFamily = SoraFontFamily, fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp, color = TextPrimary,
        )
    }
}

/** Grade 7 linhas (segunda a domingo) x 12 colunas (semanas). */
@Composable
private fun HeatmapGrid(summary: StatsSummary) {
    val grid = heatmapGrid(summary.heatmap, LocalDate.now())
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 12) {
                    val count = grid[col][row]
                    Box(
                        Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                            .background(heatmapColor(count)),
                    )
                }
            }
        }
    }
}

/** Cor do quadrado do heatmap conforme o nº de títulos do dia. */
private fun heatmapColor(count: Int): Color = when {
    count <= 0 -> Color(0xFF2E2E2E)
    count == 1 -> Accent.copy(alpha = 0.35f)
    count == 2 -> Accent.copy(alpha = 0.55f)
    count == 3 -> Accent.copy(alpha = 0.75f)
    else -> Accent
}

// ── Recap como imagem (Bitmap via Canvas, sem biblioteca externa) ───────────
// O desenho usa Paint.FontMetrics p/ a altura das linhas (nunca chuta
// offset): cada linha começa no topo livre e a próxima começa depois da
// altura medida da anterior.

/** Altura de uma linha de texto nesse Paint (ascent até descent + leading). */
private fun lineHeight(paint: Paint): Float {
    val fm = paint.fontMetrics
    return fm.descent - fm.ascent + fm.leading
}

/** Desenha o texto com o topo em `yTop`; devolve o topo da próxima linha. */
private fun drawLine(canvas: Canvas, text: String, x: Float, yTop: Float, paint: Paint): Float {
    canvas.drawText(text, x, yTop - paint.fontMetrics.ascent, paint)
    return yTop + lineHeight(paint)
}

/** Intensidade do quadrado do heatmap no Bitmap (0 = vazio). */
private fun heatCellColor(count: Int): Int = when {
    count <= 0 -> 0xFF2A2A2A.toInt()
    count == 1 -> 0xFF0E5F5A.toInt()
    count == 2 -> 0xFF0E8A83.toInt()
    count == 3 -> 0xFF00A89E.toInt()
    else -> 0xFF00C2B8.toInt()
}

/**
 * Desenha o recap (título, período, horas, sequência, dia mais ativo,
 * heatmap) num Bitmap 1080 px de largura. A altura é medida antes com as
 * mesmas fontes do desenho — nada é chutado.
 */
fun drawRecapBitmap(summary: StatsSummary, periodLabel: String, profileName: String): Bitmap {
    val width = 1080
    val pad = 64f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 64f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt()
        textSize = 36f
        typeface = Typeface.DEFAULT
    }
    val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 40f
        typeface = Typeface.DEFAULT
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt()
        textSize = 30f
        typeface = Typeface.DEFAULT
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C2B8.toInt()
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val weekText = formatWeekOverWeek(summary.weekOverWeekPct)
    val grid = heatmapGrid(summary.heatmap, LocalDate.now())
    val cell = 56f
    val gap = 10f
    val gridHeight = 7 * cell + 6 * gap
    // Altura medida antes de criar o Bitmap (mesmas fontes do desenho).
    var height = pad
    height += lineHeight(titlePaint)
    height += lineHeight(subPaint)
    height += 32f
    height += lineHeight(statPaint) * 4
    if (weekText != null) height += lineHeight(statPaint)
    if (!summary.hasData) height += lineHeight(subPaint)
    height += 40f + lineHeight(smallPaint) + 16f + gridHeight
    height += 40f + lineHeight(accentPaint)
    height += pad
    val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFF161616.toInt())
    var y = pad
    y = drawLine(canvas, "Meu recap no AOTV", pad, y, titlePaint)
    val profileSuffix = if (profileName.isNotBlank()) " · $profileName" else ""
    y = drawLine(canvas, "$periodLabel$profileSuffix", pad, y, subPaint)
    y += 32f
    y = drawLine(canvas, "Horas assistidas: ${formatHours(summary.hoursWatched)}", pad, y, statPaint)
    y = drawLine(canvas, "Títulos: ${summary.titlesWatched}  ·  Episódios: ${summary.episodesWatched}", pad, y, statPaint)
    y = drawLine(canvas, "Sequência: ${summary.streakDays} dia(s) seguidos", pad, y, statPaint)
    val bestDay = summary.bestWeekday?.let { weekdayNamePt(it) } ?: "—"
    y = drawLine(canvas, "Dia mais ativo: $bestDay", pad, y, statPaint)
    if (weekText != null) y = drawLine(canvas, "vs. semana passada: $weekText", pad, y, statPaint)
    if (!summary.hasData) y = drawLine(canvas, "Sem dados neste período.", pad, y, subPaint)
    y += 40f
    y = drawLine(canvas, "Últimas 12 semanas (segunda a domingo)", pad, y, smallPaint)
    y += 16f
    val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (col in 0 until 12) {
        for (row in 0 until 7) {
            cellPaint.color = heatCellColor(grid[col][row])
            val left = pad + col * (cell + gap)
            val top = y + row * (cell + gap)
            canvas.drawRect(left, top, left + cell, top + cell, cellPaint)
        }
    }
    y += gridHeight + 40f
    drawLine(canvas, "Gerado no AOTV", pad, y, accentPaint)
    return bitmap
}
