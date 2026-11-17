package com.aotv.app.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.TextSecondary

/**
 * Diálogo "Baixar trecho" — Lote 18 item 10 — CLIPES (v1.28.0).
 *
 * O usuário escolhe início e fim com dois sliders dentro da duração do
 * conteúdo, com os tempos visíveis; "Usar posição atual" põe o ponto onde o
 * vídeo está. O início começa na posição atual e o fim 60 s depois (ou no
 * fim do conteúdo). Confirmar chama [onDownload] — o download em si mora em
 * [com.aotv.app.data.clips.ClipDownloader].
 *
 * Honestidade do diálogo: o arquivo salvo cobre os segmentos HLS inteiros
 * do intervalo, então o início/fim exato varia alguns segundos (borda de
 * segmento); e só HLS permite trecho — MP4/torrent nem chegam aqui (o chip
 * avisa o motivo no toque, ver
 * [com.aotv.app.data.clips.ClipDownload.classifyClipSource]).
 */
@Composable
fun ClipDownloadDialog(
    title: String,
    positionMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    onDownload: (startMs: Long, endMs: Long) -> Unit,
) {
    val initialEnd = minOf(positionMs + 60_000, durationMs)
    val initialStart = if (initialEnd > 1_000) minOf(positionMs, initialEnd - 1_000) else 0L
    var startMs by remember { mutableLongStateOf(initialStart.coerceIn(0, durationMs)) }
    var endMs by remember { mutableLongStateOf(initialEnd.coerceIn(0, durationMs)) }
    val valid = startMs < endMs && endMs - startMs >= 1_000 && endMs <= durationMs

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Baixar trecho", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    "Duração ${formatTime(durationMs)} · posição atual ${formatTime(positionMs)}",
                    fontSize = 13.sp, color = TextSecondary,
                )
                Text(
                    "Início — ${formatTime(startMs)}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = startMs.toFloat(),
                    onValueChange = { startMs = it.toLong().coerceIn(0, durationMs) },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { startMs = positionMs.coerceIn(0, durationMs) }) {
                        Text("Usar posição atual")
                    }
                }
                Text(
                    "Fim — ${formatTime(endMs)}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                Slider(
                    value = endMs.toFloat(),
                    onValueChange = { endMs = it.toLong().coerceIn(0, durationMs) },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { endMs = positionMs.coerceIn(0, durationMs) }) {
                        Text("Usar posição atual")
                    }
                }
                if (!valid) {
                    Text(
                        "O início precisa ser antes do fim, com pelo menos 1 s de trecho.",
                        fontSize = 13.sp, color = com.aotv.app.ui.theme.DangerText,
                    )
                }
                Text(
                    "O arquivo salvo cobre os segmentos inteiros do trecho — o início e o fim exatos variam alguns segundos. O clipe aparece em Downloads como \"$title (${formatTime(startMs)}–${formatTime(endMs)})\".",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onDownload(startMs, endMs) }, enabled = valid) {
                Text("Baixar trecho")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
