package com.aotv.app.data.recordings

import android.content.Context
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.ui.movies.CalendarEntry

/**
 * Lembretes e alertas (Lote 8, v1.18.0; app fechado no Lote 13, v1.23.0).
 *
 * - Lembrete 1 min antes: episódio da fileira "Próximos" ou jogo de esporte
 *   que começa dentro de 1 minuto (ou começou há menos de 1 minuto, caso o
 *   app tenha aberto atrasado — o texto diz "começou agora" nesse caso).
 * - Alerta de jogo: quando um programa de esporte começa (pref
 *   `sports_game_alerts`), na mesma janela de 1 minuto.
 *
 * AGENDAMENTO (Lote 13): [checkDue] continua sendo o caminho com o app
 * aberto (chamado pela Início ao abrir e a cada minuto com ela aberta, ver
 * MoviesHomeScreen), e o [WorkScheduler] agenda o MESMO lembrete no
 * WorkManager p/ disparar com o app fechado (ver [ReminderWorker]). Os dois
 * caminhos usam a MESMA chave ([reminderKeyFor]) e o MESMO registro de
 * "já avisado" no [RecordingStore]: o que já avisou por um caminho não
 * avisa de novo pelo outro. O texto é recalculado na hora de avisar
 * ([reminderTextForEpisode]/[reminderTextForGame]), então um lembrete
 * atrasado pelo Doze diz "começou agora" em vez de mentir "em 1 minuto".
 *
 * Modo privado: lembretes NÃO gravam nada (só leem a lista de episódios e
 * jogos e postam a notificação) — não há scrobble nem registro de assistido
 * aqui. Spoilers: com `calendar_hide_spoilers` ligado e o episódio ainda
 * sem ir ao ar, o lembrete esconde T/E (só diz que um episódio da série
 * começa em 1 minuto).
 */
object RecordingReminders {

    /** Janela do lembrete: 1 minuto antes do início. */
    const val REMINDER_MS = 60_000L

    /** Chave única do lembrete de um episódio (in-app e worker usam a mesma). */
    fun reminderKeyFor(entry: CalendarEntry) =
        "ep:${entry.tmdbId}:${entry.season}:${entry.episode}:${entry.airDate}"

    /** Chave única do alerta de um jogo (in-app e worker usam a mesma). */
    fun reminderKeyFor(game: SportsGame) = "game:${game.channelId}:${game.startMs}"

    /**
     * Texto do lembrete do episódio no instante [now]. null = fora da janela
     * de 1 minuto (antes ou depois) — não há o que avisar.
     */
    fun reminderTextForEpisode(entry: CalendarEntry, hideSpoilers: Boolean, now: Long): String? {
        val untilStart = entry.atMs - now
        val startedAgo = now - entry.atMs
        return when {
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
            else -> null
        }
    }

    /** Texto do alerta do jogo no instante [now]. null = fora da janela de 1 minuto. */
    fun reminderTextForGame(game: SportsGame, now: Long): String? {
        val untilStart = game.startMs - now
        val startedAgo = now - game.startMs
        return when {
            untilStart in 0..REMINDER_MS -> "Jogo começando: ${game.title} (${game.channelName})"
            startedAgo in 0..REMINDER_MS -> "Jogo começou agora: ${game.title} (${game.channelName})"
            else -> null
        }
    }

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
            val key = reminderKeyFor(entry)
            if (store.wasNotified(key)) return@forEach
            val text = reminderTextForEpisode(entry, hideSpoilers, now) ?: return@forEach
            LocalNotifications.notify(
                context, LocalNotifications.CHANNEL_REMINDERS,
                key.hashCode(), "Próximo episódio", text,
            )
            store.markNotified(key)
        }
        if (!gameAlerts) return
        games.forEach { game ->
            val key = reminderKeyFor(game)
            if (store.wasNotified(key)) return@forEach
            val text = reminderTextForGame(game, now) ?: return@forEach
            LocalNotifications.notify(
                context, LocalNotifications.CHANNEL_REMINDERS,
                key.hashCode(), "Alerta de jogo", text,
            )
            store.markNotified(key)
        }
    }
}
