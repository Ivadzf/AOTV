// ─── data/sync/ContinuousSync.kt (Lote 23, v1.33.0) ────────────────────────
// Sync contínuo de addons/preferências p/ o Google Drive: sobe o MESMO
// JSON `aotv-backup-v1` do backup manual (montado por
// AotvBackup.buildBackupJson — o payload já inclui addons e preferências),
// sem esperar o botão manual. Sem Drive configurado ou sem conta, falha
// com mensagem explicativa — nunca crash.
//
// - syncNow: upload imediato, respeitando o conflito (o Drive com versão
//   mais nova NÃO é sobrescrito: devolve a mensagem de conflito).
// - Trabalho periódico único (6 h, rede conectada, sem exigir carregamento)
//   via WorkManager 2.10.0; só existe quando o toggle está ligado.
// - Na abertura, se o último sync tem mais de 24 h, dispara um syncNow em
//   2º plano (sem travar a abertura).
//
// O worker é um CoroutineWorker com construtor (Context, WorkerParameters)
// — o WorkerFactory padrão instancia por reflexão, sem Hilt (mesmo padrão
// dos workers de gravação do Lote 13). As dependências Hilt (singletons)
// chegam por EntryPoint, como no ClipDownload.

package com.aotv.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aotv.app.data.backup.AotvBackup
import com.aotv.app.data.drive.DriveSyncService
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.plugins.PluginStore
import com.aotv.app.data.repository.LibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Singletons do grafo Hilt que o sync precisa fora de um componente Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContinuousSyncEntryPoint {
    fun prefs(): PlatformPrefs
    fun library(): LibraryRepository
    fun plugins(): PluginStore
    fun drive(): DriveSyncService
}

object ContinuousSync {

    /** Nome do trabalho periódico único no WorkManager. */
    const val UNIQUE_WORK_NAME = "aotv-continuous-sync"

    /** Intervalo do trabalho periódico (mínimo do WorkManager: 15 min). */
    const val SYNC_INTERVAL_HOURS = 6L

    /** Na abertura, dispara um sync se o último tem mais que isso. */
    const val OPEN_SYNC_MIN_AGE_MS = 24L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sobe o backup agora p/ o Drive. Devolve Ok com a confirmação, ou
     * falha explicativa: sync desligado, Drive sem configurar, conta
     * desconectada, conflito (o Drive tem versão mais nova — sincronize
     * manualmente, sem subir por cima) ou erro do Drive. Grava
     * `continuousSyncLastMs` (e as marcas do Drive) só quando subiu.
     */
    suspend fun syncNow(context: Context): Result<String> = runCatching {
        val appContext = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(appContext, ContinuousSyncEntryPoint::class.java)
        val prefs = entry.prefs()
        val drive = entry.drive()
        if (!prefs.continuousSyncEnabled) {
            throw IllegalStateException("Sync contínuo desligado.")
        }
        val clientId = prefs.driveClientId
        if (clientId.isBlank()) {
            throw IllegalStateException("Configure o Client ID do Google Drive nas Configurações para ligar o sync contínuo.")
        }
        val account = drive.lastSignedInAccount()
            ?: throw IllegalStateException("Conecte sua conta Google nas Configurações para ligar o sync contínuo.")
        val json = AotvBackup.buildBackupJson(prefs, entry.library(), entry.plugins()).getOrThrow()
        val lastRemote = prefs.driveLastRemoteModifiedMs.takeIf { it > 0L }
        val uploaded = drive.uploadBackup(account, json, lastRemote, false).getOrThrow()
        when (uploaded) {
            is DriveSyncService.UploadResult.Conflict ->
                throw IllegalStateException("Conflito: o Drive tem uma versão mais nova — sincronize manualmente.")
            is DriveSyncService.UploadResult.Uploaded -> {
                val now = System.currentTimeMillis()
                prefs.driveFileId = uploaded.fileId
                prefs.driveLastRemoteModifiedMs = uploaded.modifiedTimeMs
                prefs.driveLastSyncAt = now
                prefs.continuousSyncLastMs = now
                "Sincronizado com o Google Drive."
            }
        }
    }

    /**
     * Agenda o trabalho periódico (6 h, só com rede; sem exigir
     * carregamento). KEEP = agendar 2x não duplica. false = não conseguiu
     * enfileirar (o app segue funcionando com o backup manual).
     */
    fun schedule(context: Context): Boolean = runCatching {
        val request = PeriodicWorkRequestBuilder<ContinuousSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(false)
                    .build(),
            )
            .addTag("aotv-continuous-sync")
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

    /** Liga ou desliga o agendamento conforme o toggle. Chamado na abertura. */
    fun applyEnabled(context: Context): Boolean {
        val prefs = EntryPointAccessors.fromApplication(
            context.applicationContext, ContinuousSyncEntryPoint::class.java,
        ).prefs()
        return if (prefs.continuousSyncEnabled) schedule(context) else cancel(context)
    }

    /**
     * Na abertura, se o sync está ligado e o último sync tem mais de 24 h,
     * dispara um syncNow em 2º plano. Não trava a abertura e não propaga
     * falha: sem Drive ou sem rede, o próximo ciclo (periódico ou abertura)
     * tenta de novo.
     */
    fun maybeSyncOnOpen(context: Context) {
        val appContext = context.applicationContext
        val prefs = runCatching {
            EntryPointAccessors.fromApplication(appContext, ContinuousSyncEntryPoint::class.java).prefs()
        }.getOrNull() ?: return
        if (!prefs.continuousSyncEnabled) return
        val last = prefs.continuousSyncLastMs
        if (last > 0L && System.currentTimeMillis() - last < OPEN_SYNC_MIN_AGE_MS) return
        scope.launch { runCatching { syncNow(appContext) } }
    }
}

/**
 * Worker do sync contínuo: um syncNow por ciclo. Sem toggle, sem Drive ou
 * com conflito, é sucesso sem fazer nada (erro de configuração não deve
 * ficar repetindo). Só falha de rede pede retry.
 */
class ContinuousSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = ContinuousSync.syncNow(applicationContext)
        if (result.isSuccess) return Result.success()
        val message = result.exceptionOrNull()?.message.orEmpty()
        val networkError = message.contains("Sem conexão") || message.contains("não respondeu")
        return if (networkError) Result.retry() else Result.success()
    }
}
