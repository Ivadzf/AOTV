package com.aotv.app.ui.movies

import android.content.Context
import android.os.SystemClock
import com.aotv.app.data.local.PlatformPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Piscina quente do Libmpv (Lote mpv 4, v1.39.0).
 *
 * O crash da 1ª abertura de uma fonte com o Libmpv acontece SEMPRE na
 * janela fria entre o log `create: MpvPlayer` e o log `init: opções` —
 * antes de qualquer código nativo do mpv rodar (o `initIfNeeded` grava
 * `init: opções` ANTES de `mpv.create`/`mpv.init`). O `MpvPlayer` era
 * construído do zero a cada abertura do player; aqui o motor é criado e
 * iniciado ANTES, quando o usuário abre o detalhe do título, e o player
 * reabre um motor já quente.
 *
 * A piscina guarda NO MÁXIMO UM motor: o devolvido no fechamento do
 * player (`giveBack`) ou o pré-carregado (`warmup`). Motor sem uso por
 * mais de [IDLE_TIMEOUT_MS] é destruído (não segura memória/EGL à toa).
 * A causa do crash NÃO foi provada — só o celular do Davi valida; a
 * piscina é a melhor aposta sobre os fatos medidos (janela fria +
 * mortes em aberturas seguintes no mesmo processo), não uma correção
 * comprovada. O log de passos (`surface: ok`, `create`, `init`, `destroy`)
 * continua sendo o instrumento p/ apontar onde a próxima morte cai.
 *
 * Por que reutilizar UMA instância em vez de serializar destroy→create:
 * os intervalos medidos no log do Davi já eram de 5–8 s entre o
 * `destroy: ok` e o próximo `create` — um atraso de teardown não teria
 * ajudado. Reutilizar elimina a janela fria (sem `create` novo) E a
 * corrida destroy→create de uma vez.
 */
object MpvWarmPool {
    /** Motor quente sem uso por mais que isto é destruído (2 min). */
    private const val IDLE_TIMEOUT_MS = 120_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    @Volatile
    private var warm: MpvPlayer? = null
    @Volatile
    private var warming = false
    @Volatile
    private var warmSinceMs = 0L
    private var warmJob: Job? = null

    private object NoOpListener : MpvPlayer.MpvListener {
        override fun onFileLoaded() = Unit
        override fun onEndFile(eofReached: Boolean) = Unit
        override fun onInitFailed() = Unit
    }

    /**
     * Pré-carrega o motor em 2º plano (chamado ao abrir o detalhe do
     * título). Só quando a pref `pref_mpv_preload_v1` está ligada E o
     * motor do perfil é o Libmpv; preguiçoso (não trava a abertura do
     * detalhe — o init nativo roda em Dispatchers.Default) e descartável
     * (sem uso em 2 min, o motor é destruído).
     */
    fun warmup(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (warm != null || warming) return
            val prefs = runCatching { PlatformPrefs(appContext) }.getOrNull() ?: return
            if (!prefs.mpvPreload || prefs.playerEngine != "libmpv") return
            warming = true
        }
        warmJob = scope.launch {
            val player = runCatching { MpvPlayer(appContext, NoOpListener) }.getOrNull()
            player?.initIfNeeded()
            synchronized(lock) {
                warming = false
                if (player != null && player.isWarm) {
                    warm = player
                    warmSinceMs = SystemClock.elapsedRealtime()
                } else {
                    runCatching { player?.destroy() }
                }
            }
        }
    }

    /**
     * Adota o motor quente (o player re-liga o listener da sessão).
     * Null = sem motor quente (o player constrói a frio, como antes).
     */
    fun take(listener: MpvPlayer.MpvListener): MpvPlayer? {
        expireIfStale()
        val player = synchronized(lock) {
            val p = warm
            warm = null
            p
        } ?: return null
        if (!player.isWarm) {
            runCatching { player.destroy() }
            return null
        }
        player.rebindListener(listener)
        return player
    }

    /**
     * Devolve um motor saudável à piscina em vez de destruí-lo (chamado
     * no fechamento do player). O motor é parado e a superfície é solta
     * antes de guardar; o que não está saudável é destruído na hora.
     */
    fun giveBack(player: MpvPlayer) {
        expireIfStale()
        synchronized(lock) {
            if (warm != null || !player.isWarm) {
                runCatching { player.destroy() }
                return
            }
        }
        runCatching { player.onSurfaceDestroyed() }
        runCatching { player.stopPlayback() }
        synchronized(lock) {
            if (!player.isWarm) {
                runCatching { player.destroy() }
                return
            }
            warm = player
            warmSinceMs = SystemClock.elapsedRealtime()
        }
    }

    /** Descarta o motor quente sem usar (ex.: troca de motor). */
    fun discard() {
        warmJob?.cancel()
        warmJob = null
        synchronized(lock) {
            warming = false
            val player = warm
            warm = null
            runCatching { player?.destroy() }
        }
    }

    private fun expireIfStale() {
        val player = warm ?: return
        if (SystemClock.elapsedRealtime() - warmSinceMs <= IDLE_TIMEOUT_MS) return
        synchronized(lock) {
            if (warm === player) warm = null
        }
        runCatching { player.destroy() }
    }
}
