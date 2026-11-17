package com.aotv.app.data.recordings

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Notificações locais (Lote 8, v1.18.0; app fechado no Lote 13, v1.23.0): gravações e lembretes.
 *
 * Dois canais, sem som de alarme e sem agendamento do sistema: o app posta
 * quando está aberto (ver [RecordingReminders]) ou gravando (o serviço em
 * 1º plano posta a persistente) — e, desde o Lote 13, o WorkManager posta
 * com o app fechado (ver [ReminderWorker] e [GameScoreWorker]). Sem a
 * permissão POST_NOTIFICATIONS (Android 13+), [notify] não faz nada — a
 * gravação continua, só não avisa; a tela de Downloads mostra o erro real.
 */
object LocalNotifications {
    /** Início, fim e falha da gravação. */
    const val CHANNEL_RECORDINGS = "aotv_recordings"

    /** Lembrete de episódio/jogo (1 min antes) e alerta de jogo. */
    const val CHANNEL_REMINDERS = "aotv_reminders"

    /** Atualização do app pronta p/ instalar (Lote 18, v1.28.0). */
    const val CHANNEL_UPDATES = "aotv_updates"

    /** O app pode postar notificações (Android 13+ pede em runtime). */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Cria os canais (sem efeito em Android < 8). */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDINGS, "Gravações", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Início, fim e falha das gravações de TV ao vivo" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Lembretes de episódios e jogos (1 min antes)" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisa quando uma atualização já foi baixada e está pronta p/ instalar" },
        )
    }

    /** Posta a notificação. Sem permissão, não faz nada (nunca trava a gravação). */
    fun notify(context: Context, channelId: String, id: Int, title: String, text: String) {
        if (!hasPermission(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true).build()
        runCatching { manager.notify(id, notification) }
    }

    /** Notificação persistente do serviço em 1º plano (a gravação em si). */
    fun ongoingNotification(context: Context, title: String, text: String): Notification {
        ensureChannels(context)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_RECORDINGS)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder.setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build()
    }
}
