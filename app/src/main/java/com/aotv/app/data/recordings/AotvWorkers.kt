package com.aotv.app.data.recordings

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.aotv.app.data.live.LiveProgram
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.sports.extractScore
import com.aotv.app.data.sports.isSportsProgram
import com.aotv.app.ui.movies.CalendarEntry
import com.aotv.app.data.sports.SportsGame
import org.json.JSONObject
import java.io.File

/**
 * Workers do WorkManager (Lote 13, v1.23.0): o que dispara com o app fechado.
 *
 * Todos são [CoroutineWorker] com construtor (Context, WorkerParameters) —
 * o WorkerFactory padrão do WorkManager instancia por reflexão, sem Hilt
 * (só usam o contexto do app + classes que já recebem Context). Todos são
 * idempotentes e honestos: sem dado de origem, sucesso sem fazer nada
 * (nunca inventam um disparo nem uma notificação).
 *
 * - [RecordingWorker]: dispara o [RecordingService] na janela da gravação.
 * - [ReminderWorker]: posta o lembrete/alerta de 1 min antes.
 * - [GameScoreWorker]: vigia periódico de fim de jogo / placar no EPG em disco.
 */
class RecordingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getString(WorkScheduler.KEY_RECORDING_ID).orEmpty()
        if (id.isBlank()) return Result.success()
        val store = RecordingStore(ctx)
        val rec = store.list().firstOrNull { it.id == id } ?: return Result.success()
        // Cancelada, concluída ou falhada: nada a fazer. Já gravando: o
        // serviço está no ar — não dispara de novo.
        if (rec.status == ScheduledRecording.STATUS_CANCELLED ||
            rec.status == ScheduledRecording.STATUS_DONE ||
            rec.status == ScheduledRecording.STATUS_FAILED ||
            rec.status == ScheduledRecording.STATUS_RECORDING
        ) return Result.success()
        val prefs = PlatformPrefs(ctx)
        val now = System.currentTimeMillis()
        // Janela passou: o in-app também não dispararia mais — deixa o
        // registro como está (a tela mostra e permite apagar).
        if (now >= rec.windowEnd(prefs.recordRunLateMin)) return Result.success()
        // Entra em 1º plano ANTES de ligar o serviço: no Android 12+, ligar
        // um foreground service vindo do 2º plano é bloqueado sem ele.
        runCatching {
            setForeground(
                ForegroundInfo(
                    FOREGROUND_NOTIFICATION_ID,
                    LocalNotifications.ongoingNotification(ctx, "Gravando: ${rec.programTitle}", rec.channelName),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                ),
            )
        }
        // O serviço espera a janela abrir sozinho quando o worker adianta.
        return runCatching {
            RecordingService.start(ctx, rec.id)
            Result.success()
        }.getOrElse {
            // Sem permissão p/ FGS (cota de expedito esgotada, por ex.):
            // tenta de novo dentro da janela — quando o app abrir, o
            // checkDue assume de qualquer jeito.
            if (System.currentTimeMillis() < rec.windowEnd(prefs.recordRunLateMin)) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 9102
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!LocalNotifications.hasPermission(ctx)) return Result.success()
        val store = RecordingStore(ctx)
        val prefs = PlatformPrefs(ctx)
        val now = System.currentTimeMillis()
        val type = inputData.getString(WorkScheduler.KEY_REMINDER_TYPE).orEmpty()
        val key = inputData.getString(WorkScheduler.KEY_REMINDER_KEY).orEmpty()
        if (key.isBlank()) return Result.success()
        // Já avisado (pelo in-app ou por outro disparo): não avisa 2x.
        if (store.wasNotified(key)) return Result.success()
        when (type) {
            WorkScheduler.REMINDER_EPISODE -> {
                val entry = CalendarEntry(
                    tmdbId = inputData.getInt(WorkScheduler.KEY_REMINDER_TMDB_ID, 0),
                    title = inputData.getString(WorkScheduler.KEY_REMINDER_TITLE).orEmpty(),
                    posterUrl = "",
                    season = inputData.getInt(WorkScheduler.KEY_REMINDER_SEASON, 0),
                    episode = inputData.getInt(WorkScheduler.KEY_REMINDER_EPISODE, 0),
                    airDate = inputData.getString(WorkScheduler.KEY_REMINDER_AIR_DATE).orEmpty(),
                    atMs = inputData.getLong(WorkScheduler.KEY_REMINDER_AT_MS, 0L),
                    isLastAired = false,
                )
                // Texto recalculado na hora: atrasou pelo Doze, diz
                // "começou agora"; fora da janela, não avisa (sucesso).
                val text = RecordingReminders.reminderTextForEpisode(entry, prefs.calendarHideSpoilers, now)
                    ?: return Result.success()
                LocalNotifications.notify(ctx, LocalNotifications.CHANNEL_REMINDERS, key.hashCode(), "Próximo episódio", text)
                store.markNotified(key)
            }
            WorkScheduler.REMINDER_GAME -> {
                if (!prefs.sportsGameAlerts) return Result.success()
                val game = SportsGame(
                    channelId = inputData.getString(WorkScheduler.KEY_REMINDER_CHANNEL_ID).orEmpty(),
                    channelName = inputData.getString(WorkScheduler.KEY_REMINDER_CHANNEL_NAME).orEmpty(),
                    channelUrl = "",
                    title = inputData.getString(WorkScheduler.KEY_REMINDER_TITLE).orEmpty(),
                    startMs = inputData.getLong(WorkScheduler.KEY_REMINDER_START_MS, 0L),
                    stopMs = inputData.getLong(WorkScheduler.KEY_REMINDER_STOP_MS, 0L),
                )
                val text = RecordingReminders.reminderTextForGame(game, now) ?: return Result.success()
                LocalNotifications.notify(ctx, LocalNotifications.CHANNEL_REMINDERS, key.hashCode(), "Alerta de jogo", text)
                store.markNotified(key)
            }
            else -> return Result.success()
        }
        return Result.success()
    }
}

/**
 * Vigia periódico de fim de jogo / placar (Lote 13, v1.23.0).
 *
 * Relê o EPG em disco (`filesDir/live/epg.json`, o MESMO arquivo que o
 * LiveTvRepository grava) e os nomes dos canais (`ch_*.json`), e compara
 * com o último estado visto:
 * - jogo que estava no ar e agora passou do fim → "Fim de jogo";
 * - placar no texto do EPG diferente do último visto → "Placar atualizado".
 *
 * Só o que o EPG traz: o placar só existe quando o texto do guia traz
 * "2 x 1" ([extractScore]) — sem placar no texto, sem alerta de placar.
 * Sem EPG em disco, sem alerta de jogo ligado ou sem permissão de
 * notificação: sucesso sem fazer nada. O EPG em disco só é atualizado com
 * o app aberto (o worker NÃO baixa guia nenhum) — então o placar só muda
 * quando o app já atualizou o guia antes; o fim de jogo funciona mesmo
 * assim, porque o fim vem do horário do programa, não de dado novo.
 *
 * Cadência: 15 min (o mínimo do WorkManager) + Doze — o fim de jogo pode
 * chegar até ~15 min depois do apito, e um jogo curto (que começa e termina
 * entre duas passadas sem nunca ser visto no ar) não gera alerta de fim.
 */
class GameScoreWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = PlatformPrefs(ctx)
        if (!prefs.sportsGameAlerts) return Result.success()
        if (!LocalNotifications.hasPermission(ctx)) return Result.success()
        val guide = readDiskGuide(ctx) ?: return Result.success()
        val epg = guide.first
        val channelNames = guide.second
        val now = System.currentTimeMillis()
        val seenScores = WorkScheduler.readGameScores(ctx).toMutableMap()
        val seenLive = WorkScheduler.readGameLiveSeen(ctx).toMutableSet()
        val currentKeys = HashSet<String>()
        epg.forEach { (channelId, programs) ->
            val channelName = channelNames[channelId].orEmpty()
            programs.forEach { program ->
                if (!isSportsProgram(program)) return@forEach
                val key = "$channelId:${program.start}"
                currentKeys += key
                val score = extractScore(program.title + " " + program.desc)
                if (program.isNow(now)) {
                    if (score != null) {
                        val prev = seenScores[key]
                        // Só avisa MUDANÇA: a 1ª vez que o placar aparece no
                        // guia é o estado inicial, não uma virada.
                        if (prev != null && prev != score) {
                            val where = if (channelName.isNotBlank()) " ($channelName)" else ""
                            LocalNotifications.notify(
                                ctx, LocalNotifications.CHANNEL_REMINDERS,
                                key.hashCode(), "Placar atualizado",
                                "${program.title} — $score$where",
                            )
                        }
                        seenScores[key] = score
                    }
                    seenLive += key
                } else if (now >= program.stop && key in seenLive) {
                    // Estava no ar e acabou.
                    val scoreText = if (score != null) " — $score" else ""
                    val where = if (channelName.isNotBlank()) " ($channelName)" else ""
                    LocalNotifications.notify(
                        ctx, LocalNotifications.CHANNEL_REMINDERS,
                        key.hashCode(), "Fim de jogo", "${program.title}$scoreText$where",
                    )
                    seenLive -= key
                    seenScores.remove(key)
                }
            }
        }
        // Poda: jogo que sumiu do guia não ocupa mais o estado.
        seenScores.keys.retainAll(currentKeys)
        seenLive.retainAll(currentKeys)
        WorkScheduler.writeGameScores(ctx, seenScores)
        WorkScheduler.writeGameLiveSeen(ctx, seenLive)
        return Result.success()
    }

    /**
     * Lê o guia em disco no MESMO formato que o LiveTvRepository grava
     * (ver `writeEpg`/`writeChannels`): epg.json + ch_*.json. null sem arquivo.
     */
    private fun readDiskGuide(context: Context): Pair<Map<String, List<LiveProgram>>, Map<String, String>>? {
        val dir = File(context.filesDir, "live")
        val epgFile = File(dir, "epg.json")
        if (!epgFile.exists()) return null
        val epg = runCatching {
            val o = JSONObject(epgFile.readText())
            val p = o.getJSONObject("p")
            p.keys().asSequence().associateWith { k ->
                val a = p.getJSONArray(k)
                (0 until a.length()).map { i ->
                    a.getJSONObject(i).let {
                        LiveProgram(it.getLong("s"), it.getLong("e"), it.optString("t"), it.optString("d"), it.optString("i"))
                    }
                }
            }
        }.getOrNull() ?: return null
        val names = HashMap<String, String>()
        dir.listFiles()?.filter { it.name.startsWith("ch_") && it.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val a = o.getJSONArray("ch")
                (0 until a.length()).forEach { i ->
                    val c = a.optJSONObject(i) ?: return@forEach
                    val id = c.optString("i")
                    val name = c.optString("n")
                    if (id.isNotBlank() && name.isNotBlank()) names[id] = name
                }
            }
        }
        return epg to names
    }
}
