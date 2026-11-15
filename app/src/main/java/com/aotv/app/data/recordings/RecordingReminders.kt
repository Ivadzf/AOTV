package com.aotv.app.data.recordings

import android.content.Context
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.ui.movies.CalendarEntry

/**
 * Lembretes e alertas (Lote 8, v1.18.0).
 *
 * - Lembrete 1 min antes: episódio da fileira "Próximos" ou jogo de esporte
 *   que começa dentro de 1 minuto (ou começou há menos de 1 minuto, caso o
 *   app tenha aberto atrasado — o texto diz "começou agora" nesse caso).
 * - Alerta de jogo: quando um programa de esporte começa (pref
 *   `sports_game_alerts`), na mesma janela de 1 minuto.
 *
 * LIMITAÇÃO HONESTA (agendamento): `androidx.work:work-runtime` NÃO está
 * no gradle e o brief proíbe adicionar dependência nova — então NÃO há
 * WorkManager nem alarme exato. [checkDue] só roda quando o app está
 * aberto (chamado pela Início ao abrir e a cada minuto com ela aberta, ver
 * MoviesHomeScreen): com o app fechado, NÃO há lembrete nem alerta. Cada
 * chave notificada é marcada no [RecordingStore] p/ não avisar 2x.
 *
 * Modo privado: lembretes NÃO gravam nada (só leem a lista de episódios e
 * jogos e postam a notificação) — não há scrobble nem registro de assistido
 * aqui. Spoilers: com `calendar_hide_spoilers` ligado e o episódio ainda
 * sem ir ao ar, o lembrete esconde T/E (só diz que um episódio da série
 * começa em 1 minuto).
 */
object RecordingReminders {

    /** Janela do lembrete: 1 minuto antes do início. */
    private const val REMINDER_MS = 60_000L

    /**
     * Checa episódios e jogos devidos e posta as notificações. [hideSpoilers]
     * e [gameAlerts] vêm das prefs do perfil (a Início passa os valores do
     * UiState). Sem permissão de notificação, não faz nada.
     */
    fun checkDue(
        context: Context,
        episodes: List<CalendarEntry>,
        games: List<SportsGame>,
        hideSpoilers: Boolean,
        gameAlerts: Boolean,
    ) {
        if (!LocalNotifications.hasPermission(context)) return
        val store = RecordingStore(context)
        val now = System.currentTimeMillis()
        episodes.forEach { entry ->
            if (entry.isLastAired) return@forEach
            val key = "ep:${entry.tmdbId}:${entry.season}:${entry.episode}:${entry.airDate}"
            if (store.wasNotified(key)) return@forEach
            val untilStart = entry.atMs - now
            val startedAgo = now - entry.atMs
            val text = when {
                untilStart in 0..REMINDER_MS -> if (hideSpoilers) {
                    "Um episódio de ${entry.title} começa em 1 minuto"
                } else {
                    "${entry.title} — ${entry.seLabel} começa em 1 minuto"
                }
                startedAgo in 0..REMINDER_MS -> if (hideSpoilers) {
                    "Um episódio de ${entry.title} começou agora"
                } else {
                    "${entry.title} — ${entry.seLabel} começou agora"
                }
                else -> return@forEach
            }
            LocalNotifications.notify(
                context, LocalNotifications.CHANNEL_REMINDERS,
                key.hashCode(), "Próximo episódio", text,
            )
            store.markNotified(key)
        }
        if (!gameAlerts) return
        games.forEach { game ->
            val key = "game:${game.channelId}:${game.startMs}"
            if (store.wasNotified(key)) return@forEach
            val untilStart = game.startMs - now
            val startedAgo = now - game.startMs
            val text = when {
                untilStart in 0..REMINDER_MS -> "Jogo começando: ${game.title} (${game.channelName})"
                startedAgo in 0..REMINDER_MS -> "Jogo começou agora: ${game.title} (${game.channelName})"
                else -> return@forEach
            }
            LocalNotifications.notify(
                context, LocalNotifications.CHANNEL_REMINDERS,
                key.hashCode(), "Alerta de jogo", text,
            )
            store.markNotified(key)
        }
    }
}
