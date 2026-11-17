package com.aotv.app.data.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aotv.app.MainActivity
import com.aotv.app.data.recordings.LocalNotifications

/**
 * Aviso ao sair do app com um torrent baixando (Lote 18 item 4,
 * v1.28.0).
 *
 * O torrent do app é temporário: o motor baixa em `cacheDir/torrents`
 * e apaga tudo ao parar (45 s ocioso ou saída do player). Sair do app
 * no meio do streaming mata o download — o aviso é uma notificação
 * local no momento da saída (o app em 2º plano não pode abrir diálogo).
 *
 * Quando dispara: [MainActivity.onStop] (sair p/ a Home ou trocar de
 * app), com um torrent ATIVO (player lendo ou motor baixando) e sem
 * troca de configuração em andamento (girar a tela não é sair do app).
 * NÃO dispara ao entrar em Picture-in-Picture: o PiP pausa a Activity
 * sem chamar onStop, e o filme continua na mini tela de propósito.
 * Sem a permissão POST_NOTIFICATIONS (Android 13+), não faz nada — o
 * download continua até o motor limpar os 45 s ociosos, só não avisa
 * (mesma regra do [LocalNotifications]).
 *
 * Tocar na notificação reabre o app. O aviso é o texto honesto: sair
 * interrompe o download e o arquivo temporário é apagado.
 */
object TorrentExitWarning {

    /** Canal próprio (não é gravação nem lembrete). */
    const val CHANNEL_DOWNLOADS = "aotv_downloads"

    /** Id fixo da notificação (um aviso por vez — o novo substitui o antigo). */
    const val NOTIFICATION_ID = 9001

    /** Posta o aviso quando há um torrent baixando. Sem torrent ativo, não faz nada. */
    fun notifyIfTorrentDownloading(context: Context) {
        val active = Torrents.ifStarted()?.hasActiveDownload() ?: false
        if (!active) return
        if (!LocalNotifications.hasPermission(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Aviso ao sair do app com um torrent baixando" },
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_DOWNLOADS)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setContentTitle("Torrent baixando")
            .setContentText("Sair do app interrompe o download — o arquivo temporário é apagado.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
