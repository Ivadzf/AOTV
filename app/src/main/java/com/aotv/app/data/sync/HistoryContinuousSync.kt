package com.aotv.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.SimklService
import com.aotv.app.data.remote.TraktService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// ─── data/sync/HistoryContinuousSync.kt (Lote 25, v1.35.0) — Frente D, item 14 ──
// Sincronia contínua do HISTÓRICO (Continuar assistindo, episódios vistos,
// Minha Lista) com Nuvio/Stremio via Trakt e Simkl. NÃO confundir com
// ContinuousSync.kt (Lote 23), que é sync de BACKUP p/ o Google Drive.
//
// Desenho (parâmetros do JOTV Windows 0.2.15, §4.2 da análise):
// - 2 vias, sem eco, vence o mais recente.
// - Puxada a cada 5 min NO WINDOWS — no Android o WorkManager tem intervalo
//   mínimo de 15 min p/ trabalho periódico, então o desenho honesto é:
//   puxada na abertura do app + worker periódico de 15 min.
// - Envio 3 s após a ação local (debounce) e, durante a reprodução, no
//   máximo 1 envio a cada 45 s.
// - Sem eco: só se envia a mudança local que aconteceu DEPOIS do último
//   pull concluído (a mudança que o pull já viu não volta p/ fora); os
//   envios em rajada colapsam num só sync.
// - Vence o mais recente: o pull do Trakt só aplica o que é mais recente
//   que o histórico local (TraktService.pullPlayback); o push só sai p/
//   mudanças mais novas que o último pull. O pull do Simkl é marcação
//   idempotente (marcar visto 2x não muda nada — caveat: um "desmarcar"
//   local pode ser re-marcado pelo pull seguinte, como no Lote 23).
// - Reusa o maquinário que já existe: SimklService.sync() e TraktService
//   .sync() (pull de watchlist/playback/watched + push). A posição ao vivo
//   já é empurrada na hora pelos scrobbles do player
//   (PlayerProgressViewModel: scrobble start/pause/stop) — este objeto NÃO
//   duplica o scrobble, só o sync de histórico/lista.
// - Por perfil: o toggle e o último sync são prefs com escopo de perfil
//   (hist_cont_sync_v1, hist_cont_last_sync_v1). Desligado por padrão: o
//   usuário liga em Configurações → Contas.
//
// O worker é um CoroutineWorker com construtor (Context, WorkerParameters)
// — o WorkerFactory padrão instancia por reflexão, sem Hilt (mesmo padrão
// dos workers de gravação do Lote 13 e do ContinuousSync). As dependências
// Hilt chegam por EntryPoint.

/** Singletons do grafo Hilt que o sync de histórico precisa fora de um componente Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HistoryContinuousSyncEntryPoint {
    fun prefs(): PlatformPrefs
    fun simkl(): SimklService
    fun trakt(): TraktService
}

object HistoryContinuousSync {

    /** Nome do trabalho periódico único no WorkManager. */
    const val UNIQUE_WORK_NAME = "aotv-history-continuous-sync"

    /**
     * Intervalo do trabalho periódico: 15 min, o MÍNIMO do WorkManager.
     * O Windows puxa a cada 5 min; no Android não dá p/ menos que isso.
     */
    const val PULL_INTERVAL_MINUTES = 15L

    /** Envio 3 s após a ação local (debounce — rajada vira 1 sync). */
    const val PUSH_DEBOUNCE_MS = 3_000L

    /** Durante a reprodução, no máximo 1 envio a cada 45 s. */
    const val PUSH_MIN_INTERVAL_PLAYING_MS = 45_000L

    /** Na abertura, não puxa se o último pull tem menos que isso (o worker acabou de rodar). */
    const val OPEN_PULL_MIN_AGE_MS = 5L * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var pushJob: Job? = null

    /** Último envio (push) concluído, p/ o throttle de 45 s em reprodução. Só memória. */
    @Volatile
    private var lastPushAtMs: Long = 0L

    /**
     * Puxada + envio (2 vias) agora. Perfil capturado uma vez aqui. true
     * quando pelo menos um tracker conectado sincronizou; false quando
     * nenhum tracker está conectado ou os dois falharam. Grava
     * [PlatformPrefs.histContLastSyncMs] só no sucesso.
     */
    suspend fun pullNow(simkl: SimklService, trakt: TraktService, prefs: PlatformPrefs): Boolean {
        if (!prefs.histContSyncEnabled) return false
        val profileId = prefs.profileId.value
        val simklOk = runCatching { simkl.sync(profileId) }.getOrDefault(false)
        val traktOk = runCatching { trakt.sync() }.getOrDefault(false)
        if (!simklOk && !traktOk) return false
        prefs.histContLastSyncMs = System.currentTimeMillis()
        return true
    }

    /**
     * Sync imediato a partir de um Context (worker e "Sincronizar agora").
     * Devolve Ok com a confirmação, ou falha explicativa (sync desligado,
     * nenhum tracker conectado, ou erro de rede).
     */
    suspend fun syncNow(context: Context): Result<String> = runCatching {
        val appContext = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(appContext, HistoryContinuousSyncEntryPoint::class.java)
        val prefs = entry.prefs()
        if (!prefs.histContSyncEnabled) throw IllegalStateException("Sincronia contínua desligada.")
        val ok = pullNow(entry.simkl(), entry.trakt(), prefs)
        if (!ok) throw IllegalStateException("Nenhum tracker conectado (Trakt/Simkl) ou sem resposta.")
        "Histórico sincronizado."
    }

    /**
     * Agenda o trabalho periódico (15 min, só com rede). KEEP = agendar 2x
     * não duplica. false = não conseguiu enfileirar (o app segue com o
     * pull na abertura).
     */
    fun schedule(context: Context): Boolean = runCatching {
        val request = PeriodicWorkRequestBuilder<HistorySyncWorker>(PULL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag("aotv-history-continuous-sync")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        true
    }.getOrDefault(false)

    /** Cancela o trabalho periódico (toggle desligado). Sem efeito sem trabalho. */
    fun cancel(context: Context): Boolean = runCatching {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        true
    }.getOrDefault(false)

    /** Liga ou desliga o agendamento conforme o toggle. Chamado na abertura e ao trocar o toggle. */
    fun applyEnabled(context: Context): Boolean {
        val prefs = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext, HistoryContinuousSyncEntryPoint::class.java,
            ).prefs()
        }.getOrNull() ?: return false
        return if (prefs.histContSyncEnabled) schedule(context) else cancel(context)
    }

    /**
     * Na abertura: aplica o agendamento e, se o sync está ligado e o último
     * pull tem mais de 5 min, dispara um pull em 2º plano. Não trava a
     * abertura e não propaga falha.
     */
    fun pullOnOpen(context: Context, simkl: SimklService, trakt: TraktService, prefs: PlatformPrefs) {
        applyEnabled(context)
        if (!prefs.histContSyncEnabled) return
        val last = prefs.histContLastSyncMs
        if (last > 0L && System.currentTimeMillis() - last < OPEN_PULL_MIN_AGE_MS) return
        scope.launch { runCatching { pullNow(simkl, trakt, prefs) } }
    }

    /**
     * Avisa que o histórico local mudou (Minha Lista, visto). O envio sai
     * 3 s depois (debounce); durante a reprodução, no máximo 1 a cada 45 s.
     * Sem eco: se um pull concluiu DEPOIS desta mudança, ela já está fora
     * (ou já foi vista pelo pull) e o envio é pulado.
     */
    fun noteLocalChange(simkl: SimklService, trakt: TraktService, prefs: PlatformPrefs, playing: Boolean) {
        if (!prefs.histContSyncEnabled) return
        val changedAtMs = System.currentTimeMillis()
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            if (playing) {
                val wait = PUSH_MIN_INTERVAL_PLAYING_MS - (System.currentTimeMillis() - lastPushAtMs)
                if (wait > 0) delay(wait)
            }
            if (prefs.histContLastSyncMs >= changedAtMs) return@launch
            if (runCatching { pullNow(simkl, trakt, prefs) }.getOrDefault(false)) {
                lastPushAtMs = System.currentTimeMillis()
            }
        }
    }
}

/**
 * Worker do sync contínuo de histórico: um syncNow por ciclo (15 min). Sem
 * toggle ou sem tracker, é sucesso sem fazer nada (erro de configuração não
 * deve ficar repetindo). Só falha de rede pede retry.
 */
class HistorySyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = HistoryContinuousSync.syncNow(applicationContext)
        if (result.isSuccess) return Result.success()
        val message = result.exceptionOrNull()?.message.orEmpty()
        val networkError = message.contains("Sem conexão") || message.contains("não respondeu")
        return if (networkError) Result.retry() else Result.success()
    }
}
