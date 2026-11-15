package com.aotv.app.ui.movies

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.recordings.LocalNotifications
import com.aotv.app.data.recordings.RecordingStore
import com.aotv.app.data.recordings.ScheduledRecording
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/**
 * Diálogo de agendar gravação (Lote 8, v1.18.0).
 *
 * A gravação começa (início do programa − antecedência) e termina (fim +
 * tolerância), com os valores das prefs do perfil. A pasta é a de
 * gravações (Downloads → Gravações) ou a pasta do app.
 *
 * A permissão POST_NOTIFICATIONS (Android 13+) é pedida aqui, no mesmo
 * padrão da busca por voz (VoiceSearch): negada, a gravação acontece do
 * mesmo jeito, só não avisa — o toast explica onde liberar.
 *
 * QUEM CHAMA: a fileira "Próximos" da Início (botão "Gravar" do jogo) e —
 * quando a Frente 1 ligar — o diálogo de detalhe do programa no guia
 * (LiveGuideScreen.kt, arquivo da Frente 1: falta só o botão "Gravar"
 * chamando este diálogo com o canal e o programa).
 */
@Composable
fun RecordProgramDialog(
    channelId: String,
    channelName: String,
    channelUrl: String,
    channelHeaders: Map<String, String>,
    programTitle: String,
    startMs: Long,
    stopMs: Long,
    onDismiss: () -> Unit,
    onScheduled: (ScheduledRecording) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlatformPrefs(context) }
    val store = remember { RecordingStore(context) }
    val earlyMin = prefs.recordStartEarlyMin
    val lateMin = prefs.recordRunLateMin
    val folderName = prefs.recordingsFolderName
    val windowStart = startMs - earlyMin * 60_000L
    val windowEnd = stopMs + lateMin * 60_000L

    fun doSchedule() {
        val rec = store.schedule(
            channelId = channelId, channelName = channelName, channelUrl = channelUrl,
            channelHeaders = channelHeaders, programTitle = programTitle,
            startMs = startMs, stopMs = stopMs,
        )
        onScheduled(rec)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Sem a permissão, a gravação acontece mas não avisa. Dá para liberar em Configurações do app.",
                Toast.LENGTH_LONG,
            ).show()
        }
        doSchedule()
    }

    fun onConfirm() {
        if (Build.VERSION.SDK_INT >= 33 && !LocalNotifications.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            doSchedule()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(programTitle, color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$channelName · ${upcomingHhmm(startMs)} até ${upcomingHhmm(stopMs)}",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Text(
                    "Grava de ${upcomingHhmm(windowStart)} até ${upcomingHhmm(windowEnd)} " +
                        "(${earlyMin} min antes, ${lateMin} min depois).",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Text(
                    if (folderName.isBlank()) "Pasta: pasta do app." else "Pasta: \"$folderName\".",
                    color = TextSecondary, fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Com o app fechado, a gravação só começa quando o app abre — não há agendador em segundo plano.",
                    color = TextSecondary, fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text("Agendar gravação", color = Accent, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
        },
    )
}
