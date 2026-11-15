package com.aotv.app.data.recordings

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.ui.movies.CalendarEntry
import java.util.concurrent.TimeUnit

/**
 * Agendamento em 2º plano (Lote 13, v1.23.0): gravações, lembretes e alertas
 * de jogo com o app FECHADO, via WorkManager (`androidx.work:work-runtime-ktx`).
 *
 * Como funciona (o app aberto NÃO muda nada — os caminhos antigos continuam):
 * - Gravação agendada → [scheduleRecording] enfileira um [RecordingWorker]
 *   único p/ a janela da gravação (início − antecedência); o worker dispara
 *   o [RecordingService] existente. Cancelar/apagar a gravação cancela o
 *   trabalho ([cancelRecording]).
 * - Lembrete/alerta → [scheduleReminders] enfileira um [ReminderWorker]
 *   único por episódio/jogo p/ 1 min antes do início; o worker recalcula o
 *   texto na hora (mesma regra do in-app) e só avisa 1x (mesma chave e
 *   mesmo registro de "já avisado" do [RecordingReminders]).
 * - Fim de jogo / placar → [syncGameScoreWatch] mantém um [GameScoreWorker]
 *   periódico (15 min, o mínimo do WorkManager) que relê o EPG em disco e
 *   avisa quando um jogo que estava no ar termina ou o placar do texto do
 *   EPG muda. Só quando `sports_game_alerts` está ligado.
 *
 * IDEMPOTÊNCIA: todo trabalho é único por nome (`enqueueUniqueWork` com
 * `KEEP` / `enqueueUniquePeriodicWork` com `KEEP`) — agendar 2x o mesmo
 * alerta/gravação não duplica nada. O worker também é idempotente: gravação
 * cancelada, concluída ou fora da janela vira sucesso sem fazer nada, e o
 * lembrete já avisado não avisa de novo.
 *
 * HONESTIDADE: sem permissão de notificação, o worker não avisa (sucesso
 * silencioso); sem EPG em disco, o [GameScoreWorker] não tem o que ler e
 * não inventa jogo nenhum; sem dado de origem (gravação apagada), o worker
 * não dispara. Tudo que precisa de rede além do disco (atualizar o EPG)
 * continua dependendo do app aberto — o worker só LÊ o que já está em disco.
 *
 * FALHA DE INICIALIZAÇÃO: cada método engole a falha do WorkManager e
 * devolve false — sem o WorkManager (ou sem conseguir enfileirar), o app
 * segue funcionando com os caminhos com-o-app-aberto de sempre. Nenhum
 * método aqui pode derrubar quem chama.
 *
 * LIMITES DA PLATAFORMA (não são bugs do app): o trabalho periódico só roda
 * a cada 15 min no mínimo e o Doze pode adiar o disparo — o alerta de fim
 * de jogo pode chegar até ~15 min depois do apito, e o lembrete de 1 min
 * pode atrasar em aparelho em repouso profundo (o texto vira "começou
 * agora" nesses casos). O WorkManager sobrevive a reboot (reagenda sozinho).
 */
object WorkScheduler {

    /** Prefixo do trabalho único de uma gravação ("aotv-rec:<id da gravação>"). */
    const val UNIQUE_RECORDING_PREFIX = "aotv-rec:"

    /** Prefixo do trabalho único de um lembrete ("aotv-rem:<chave do lembrete>"). */
    const val UNIQUE_REMINDER_PREFIX = "aotv-rem:"

    /** Nome do trabalho periódico de fim de jogo / placar. */
    const val UNIQUE_GAME_SCORES = "aotv-game-scores"

    /** Intervalo do [GameScoreWorker]: 15 min, o mínimo do WorkManager. */
    const val GAME_SCORE_INTERVAL_MIN = 15L

    const val KEY_RECORDING_ID = "recording_id"
    const val KEY_REMINDER_TYPE = "reminder_type"
    const val KEY_REMINDER_KEY = "reminder_key"
    const val KEY_REMINDER_TITLE = "reminder_title"
    const val KEY_REMINDER_TMDB_ID = "reminder_tmdb_id"
    const val KEY_REMINDER_SEASON = "reminder_season"
    const val KEY_REMINDER_EPISODE = "reminder_episode"
    const val KEY_REMINDER_AIR_DATE = "reminder_air_date"
    const val KEY_REMINDER_AT_MS = "reminder_at_ms"
    const val KEY_REMINDER_CHANNEL_ID = "reminder_channel_id"
    const val KEY_REMINDER_CHANNEL_NAME = "reminder_channel_name"
    const val KEY_REMINDER_START_MS = "reminder_start_ms"
    const val KEY_REMINDER_STOP_MS = "reminder_stop_ms"

    const val REMINDER_EPISODE = "ep"
    const val REMINDER_GAME = "game"

    /**
     * Agenda o disparo de uma gravação p/ o início da janela (programa −
     * antecedência da pref). Único por gravação, KEEP = agendar 2x não duplica.
     * false = não conseguiu enfileirar (o caminho com-o-app-aberto continua valendo).
     */
    fun scheduleRecording(context: Context, rec: ScheduledRecording): Boolean = runCatching {
        val prefs = PlatformPrefs(context.applicationContext)
        val delay = (rec.windowStart(prefs.recordStartEarlyMin) - System.currentTimeMillis()).coerceAtLeast(0L)
        val req = OneTimeWorkRequestBuilder<RecordingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            // Expedito p/ o worker poder entrar em 1º plano e ligar o
            // RecordingService (Android 12+ bloqueia FGS vindo do 2º plano
            // sem ele); sem cota, roda como trabalho comum e o worker tenta
            // de novo dentro da janela.
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 60_000L, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_RECORDING_ID to rec.id))
            .addTag("aotv-recording")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_RECORDING_PREFIX + rec.id, ExistingWorkPolicy.KEEP, req)
        true
    }.getOrDefault(false)

    /** Cancela o disparo agendado de uma gravação (cancelar/apagar). Sem efeito sem trabalho. */
    fun cancelRecording(context: Context, recordingId: String): Boolean = runCatching {
        if (recordingId.isBlank()) return@runCatching false
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_RECORDING_PREFIX + recordingId)
        true
    }.getOrDefault(false)

    /**
     * Agenda o disparo de TODAS as gravações agendadas (rede de segurança:
     * gravações criadas antes desta versão, ou cujo agendamento falhou).
     * KEEP = as já agendadas não são reagendadas.
     */
    fun scheduleAllRecordings(context: Context): Boolean = runCatching {
        val store = RecordingStore(context.applicationContext)
        store.list()
            .filter { it.status == ScheduledRecording.STATUS_SCHEDULED }
            .forEach { scheduleRecording(context, it) }
        true
    }.getOrDefault(false)

    /**
     * Agenda os lembretes dos episódios e os alertas dos jogos que ainda vão
     * começar (1 min antes). Um trabalho único por episódio/jogo, KEEP. O
     * texto é recalculado pelo worker na hora de avisar. Episódios fora da
     * janela (±1 min do início) não são agendados — o in-app também não
     * avisaria. [gameAlerts] desliga só os alertas de jogo (a pref
     * `sports_game_alerts`); o lembrete de episódio não tem toggle.
     */
    fun scheduleReminders(
        context: Context,
        episodes: List<CalendarEntry>,
        games: List<SportsGame>,
        gameAlerts: Boolean,
    ): Boolean = runCatching {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val wm = WorkManager.getInstance(appContext)
        episodes.forEach { entry ->
            if (entry.isLastAired) return@forEach
            val fireAt = entry.atMs - RecordingReminders.REMINDER_MS
            if (entry.atMs < now - RecordingReminders.REMINDER_MS) return@forEach
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((fireAt - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_REMINDER_TYPE to REMINDER_EPISODE,
                        KEY_REMINDER_KEY to RecordingReminders.reminderKeyFor(entry),
                        KEY_REMINDER_TITLE to entry.title,
                        KEY_REMINDER_TMDB_ID to entry.tmdbId,
                        KEY_REMINDER_SEASON to entry.season,
                        KEY_REMINDER_EPISODE to entry.episode,
                        KEY_REMINDER_AIR_DATE to entry.airDate,
                        KEY_REMINDER_AT_MS to entry.atMs,
                    ),
                )
                .addTag("aotv-reminder")
                .build()
            wm.enqueueUniqueWork(
                UNIQUE_REMINDER_PREFIX + RecordingReminders.reminderKeyFor(entry),
                ExistingWorkPolicy.KEEP, req,
            )
        }
        if (!gameAlerts) return@runCatching true
        games.forEach { game ->
            val fireAt = game.startMs - RecordingReminders.REMINDER_MS
            if (game.startMs < now - RecordingReminders.REMINDER_MS) return@forEach
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((fireAt - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_REMINDER_TYPE to REMINDER_GAME,
                        KEY_REMINDER_KEY to RecordingReminders.reminderKeyFor(game),
                        KEY_REMINDER_TITLE to game.title,
                        KEY_REMINDER_CHANNEL_ID to game.channelId,
                        KEY_REMINDER_CHANNEL_NAME to game.channelName,
                        KEY_REMINDER_START_MS to game.startMs,
                        KEY_REMINDER_STOP_MS to game.stopMs,
                    ),
                )
                .addTag("aotv-reminder")
                .build()
            wm.enqueueUniqueWork(
                UNIQUE_REMINDER_PREFIX + RecordingReminders.reminderKeyFor(game),
                ExistingWorkPolicy.KEEP, req,
            )
        }
        true
    }.getOrDefault(false)

    /**
     * Liga/desliga o vigia periódico de fim de jogo / placar. Ligado =
     * enfileira (KEEP, 15 min); desligado = cancela o trabalho. O worker em
     * si também checa a pref e não faz nada desligado (defesa em 2 camadas).
     */
    fun syncGameScoreWatch(context: Context, enabled: Boolean): Boolean = runCatching {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            wm.cancelUniqueWork(UNIQUE_GAME_SCORES)
        } else {
            val req = PeriodicWorkRequestBuilder<GameScoreWorker>(GAME_SCORE_INTERVAL_MIN, TimeUnit.MINUTES)
                .addTag("aotv-game-scores")
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_GAME_SCORES, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        true
    }.getOrDefault(false)

    /**
     * Anota os jogos que estão no ar agora como "vistos ao vivo" (p/ o
     * [GameScoreWorker] saber que um jogo terminou: ele só avisa o fim de
     * quem ele — ou a Início — viu no ar antes). Chamado pela Início com a
     * lista de jogos do EPG. Sem EPG, sem anotação.
     */
    fun noteLiveGames(context: Context, games: List<SportsGame>): Boolean = runCatching {
        val now = System.currentTimeMillis()
        val live = games.filter { it.isNow(now) }.map { "${it.channelId}:${it.startMs}" }.toSet()
        if (live.isEmpty()) return@runCatching true
        val seen = readGameLiveSeen(context).toMutableSet()
        if (seen.addAll(live)) writeGameLiveSeen(context, seen)
        true
    }.getOrDefault(false)

    // ── Estado do vigia de jogos (SharedPreferences próprio) ─────────────
    // "aotv_game_scores": o último placar visto por jogo (chave
    // "<canal>:<início>" -> "2 x 1") e o conjunto de jogos vistos ao vivo.
    // Só o GameScoreWorker e o noteLiveGames leem/escrevem aqui.

    private const val GAME_SCORES_PREFS = "aotv_game_scores"
    private const val KEY_GAME_SCORES = "scores_v1"
    private const val KEY_GAME_LIVE = "live_v1"

    internal fun readGameScores(context: Context): Map<String, String> = runCatching {
        val o = org.json.JSONObject(
            context.getSharedPreferences(GAME_SCORES_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_GAME_SCORES, null) ?: "{}",
        )
        o.keys().asSequence().associateWith { k -> o.optString(k) }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())

    internal fun writeGameScores(context: Context, scores: Map<String, String>) {
        runCatching {
            val o = org.json.JSONObject()
            scores.entries.filter { it.key.isNotBlank() && it.value.isNotBlank() }.forEach { (k, v) -> o.put(k, v) }
            context.getSharedPreferences(GAME_SCORES_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_GAME_SCORES, o.toString()).apply()
        }
    }

    internal fun readGameLiveSeen(context: Context): Set<String> = runCatching {
        context.getSharedPreferences(GAME_SCORES_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_GAME_LIVE, emptySet()).orEmpty()
    }.getOrDefault(emptySet())

    internal fun writeGameLiveSeen(context: Context, live: Set<String>) {
        runCatching {
            context.getSharedPreferences(GAME_SCORES_PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_GAME_LIVE, live).apply()
        }
    }
}
