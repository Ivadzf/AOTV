package com.aotv.app.data.recordings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gravações agendadas (Lote 8, v1.18.0; disparo com o app fechado no Lote 13, v1.23.0).
 *
 * Guarda em SharedPreferences próprio ("aotv_recordings") a lista de
 * gravações do aparelho — NÃO vai p/ o backup (o arquivo gravado é local e
 * a URI da pasta não viaja com a permissão, igual à pasta de downloads).
 * O agendamento em si é só um registro: quem dispara a gravação na hora
 * certa é o [RecordingService] — com o app aberto, via [RecordingService.checkDue]
 * (chamado pela Início); com o app fechado, via WorkManager ([WorkScheduler]:
 * [schedule] agenda o disparo, [cancel]/[remove] cancelam o trabalho).
 */
data class ScheduledRecording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val channelHeaders: Map<String, String> = emptyMap(),
    val programTitle: String,
    /** Título da série, quando a gravação é de uma série (Lote 12 — Frente 2):
     *  a gravação vai p/ a pasta da série (ver [RecordingStore.folderNameFor]).
     *  "" = sem pasta (filme, jogo ou programa sem série conhecida). */
    val seriesTitle: String = "",
    /** Início do programa (epoch ms, como veio do EPG). */
    val startMs: Long,
    /** Fim do programa (epoch ms, como veio do EPG). */
    val stopMs: Long,
    /** "scheduled", "recording", "done", "failed" ou "cancelled". */
    val status: String = STATUS_SCHEDULED,
    /** Nome do arquivo gravado (vazio até a gravação começar). */
    val fileName: String = "",
    /** Erro REAL da falha (mensagem da exceção). Vazio sem falha — nunca genérico. */
    val error: String = "",
    val createdAt: Long = 0L,
) {
    companion object {
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_RECORDING = "recording"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }

    /** Início da janela de gravação (programa − minutos de antecedência). */
    fun windowStart(earlyMin: Int) = startMs - earlyMin * 60_000L

    /** Fim da janela de gravação (programa + minutos de tolerância). */
    fun windowEnd(lateMin: Int) = stopMs + lateMin * 60_000L

    /** Rótulo do status p/ a tela. */
    fun statusLabel(): String = when (status) {
        STATUS_SCHEDULED -> "Agendada"
        STATUS_RECORDING -> "Gravando"
        STATUS_DONE -> "Concluída"
        STATUS_FAILED -> "Falhou"
        STATUS_CANCELLED -> "Cancelada"
        else -> status
    }
}

@Singleton
class RecordingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("aotv_recordings", Context.MODE_PRIVATE)

    private val _recordings = MutableStateFlow(readAll())
    /** Gravações agendadas, que avisam quem observa (tela de Downloads). */
    val recordingsFlow: StateFlow<List<ScheduledRecording>> = _recordings.asStateFlow()

    /** Gravações agendadas (leitura direta). */
    fun list(): List<ScheduledRecording> = _recordings.value

    /** Agenda a gravação de um programa. Devolve o registro criado. Também agenda o disparo com o app fechado (WorkManager, Lote 13). */
    fun schedule(
        channelId: String,
        channelName: String,
        channelUrl: String,
        channelHeaders: Map<String, String>,
        programTitle: String,
        startMs: Long,
        stopMs: Long,
        seriesTitle: String = "",
    ): ScheduledRecording {
        val rec = ScheduledRecording(
            id = UUID.randomUUID().toString(),
            channelId = channelId, channelName = channelName, channelUrl = channelUrl,
            channelHeaders = channelHeaders, programTitle = programTitle,
            seriesTitle = seriesTitle,
            startMs = startMs, stopMs = stopMs,
            createdAt = System.currentTimeMillis(),
        )
        save(_recordings.value + rec)
        WorkScheduler.scheduleRecording(context, rec)
        return rec
    }

    /** Atualiza um registro (status, arquivo, erro). Sem efeito quando o id não existe. */
    fun update(id: String, transform: (ScheduledRecording) -> ScheduledRecording) {
        val cur = _recordings.value
        val idx = cur.indexOfFirst { it.id == id }
        if (idx < 0) return
        val next = cur.toMutableList()
        next[idx] = transform(next[idx])
        save(next)
    }

    /** Cancela uma gravação agendada (só vale antes de começar). Também cancela o disparo com o app fechado (WorkManager, Lote 13). */
    fun cancel(id: String) {
        update(id) { rec ->
            if (rec.status == ScheduledRecording.STATUS_SCHEDULED) rec.copy(status = ScheduledRecording.STATUS_CANCELLED) else rec
        }
        WorkScheduler.cancelRecording(context, id)
    }

    /** Apaga o registro (o arquivo, quando existe, é apagado pelo chamador). Também cancela o disparo com o app fechado. */
    fun remove(id: String) {
        save(_recordings.value.filterNot { it.id == id })
        WorkScheduler.cancelRecording(context, id)
    }

    /** Recarrega do disco (o serviço grava enquanto as Configurações estão abertas). */
    fun refresh() {
        _recordings.value = readAll()
    }

    /** Esta chave já gerou um lembrete/alerta (p/ não notificar 2x). */
    fun wasNotified(key: String): Boolean =
        prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(key)

    /** Marca a chave como já notificada. */
    fun markNotified(key: String) {
        val set = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        if (set.add(key)) prefs.edit().putStringSet(KEY_NOTIFIED, set).apply()
    }

    /** Nome do arquivo da gravação: "aotv-rec-<canal>-<aaaammdd-hhmm>.ts". */
    fun fileNameFor(rec: ScheduledRecording): String {
        val clean = rec.channelName.lowercase()
            .map { c -> if (c.isLetterOrDigit()) c else '_' }
            .joinToString("").trim('_').take(24).ifBlank { "canal" }
        val date = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale("pt", "BR"))
            .format(java.util.Date(rec.startMs))
        return "aotv-rec-$clean-$date.ts"
    }

    /**
     * Pasta da série da gravação (Lote 12 — Frente 2): o título da série
     * limpo p/ nome de pasta ("" = sem pasta, vai na raiz das gravações).
     * O serviço cria a pasta na hora de gravar; a lista agrupa por ela.
     */
    fun folderNameFor(rec: ScheduledRecording): String =
        rec.seriesTitle.trim().lowercase()
            .map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
            .joinToString("").trim('_').take(48)

    private fun save(list: List<ScheduledRecording>) {
        val arr = JSONArray()
        list.forEach { rec ->
            val headers = JSONObject()
            rec.channelHeaders.forEach { (k, v) -> headers.put(k, v) }
            arr.put(
                JSONObject()
                    .put("id", rec.id).put("channelId", rec.channelId)
                    .put("channelName", rec.channelName).put("channelUrl", rec.channelUrl)
                    .put("headers", headers.toString())
                    .put("programTitle", rec.programTitle)
                    .put("seriesTitle", rec.seriesTitle)
                    .put("startMs", rec.startMs).put("stopMs", rec.stopMs)
                    .put("status", rec.status).put("fileName", rec.fileName)
                    .put("error", rec.error).put("createdAt", rec.createdAt),
            )
        }
        prefs.edit().putString(KEY_RECORDINGS, arr.toString()).apply()
        _recordings.value = list
    }

    private fun readAll(): List<ScheduledRecording> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_RECORDINGS, null) ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val headers = runCatching { JSONObject(o.optString("headers", "{}")) }.getOrNull()
                ?: JSONObject()
            ScheduledRecording(
                id = o.optString("id"), channelId = o.optString("channelId"),
                channelName = o.optString("channelName"), channelUrl = o.optString("channelUrl"),
                channelHeaders = headers.keys().asSequence().associateWith { headers.optString(it) },
                programTitle = o.optString("programTitle"),
                seriesTitle = o.optString("seriesTitle", ""),
                startMs = o.optLong("startMs"), stopMs = o.optLong("stopMs"),
                status = o.optString("status", ScheduledRecording.STATUS_SCHEDULED),
                fileName = o.optString("fileName"), error = o.optString("error"),
                createdAt = o.optLong("createdAt"),
            )
        }.filter { it.id.isNotBlank() && it.channelUrl.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_RECORDINGS = "recordings_v1"
        private const val KEY_NOTIFIED = "notified_v1"
    }
}
