package com.aotv.app.data.kodi

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor de serviços Kodi (v1.3, Fase 3).
 *
 * Roda o `service.py` de um addon numa thread de fundo, com a API
 * `xbmc.Monitor` (waitForAbort bloqueia de verdade até o abort).
 * Espelha o KodiPluginRunner no acesso ao Python (Chaquopy,
 * aotv_kodi.*), mas NÃO usa o _run_lock do dispatch de plugin: o
 * serviço roda em paralelo com a navegação/play e nunca pode
 * segurá-los nem ser travado por eles.
 *
 * Ciclo de vida (só UM serviço por vez — o abort do Monitor é
 * global, como no Kodi):
 * - startService(addonId): idempotente; se outro serviço estiver
 *   rodando, para-o e ESPERA a thread sair (até 5s) antes de limpar
 *   o flag de abort e subir o novo. Sem addon com service.py, só
 *   para o anterior e devolve false.
 * - stopService(addonId): não-bloqueante — tira do mapa e pede o
 *   abort; a thread sai sozinha do waitForAbort. É o que o
 *   onCleared da ViewModel chama (nunca dar join na main thread).
 * - stopAll(): para tudo (saída do app / troca de addon).
 *
 * Presença/analytics e self-update do Vulgata ficam DESLIGADOS
 * (decisão do Davi, §9): a Fase 1 força os settings
 * analytics_enabled=false e automatic_updates=false, e o serviço
 * só lê esses settings — o runner não precisa (nem pode) desligar
 * nada além disso. O diag do run_service registra o HTTP do
 * serviço, p/ o teste provar que nada foi enviado.
 *
 * Corrida conhecida: se a thread antiga estiver presa fora do
 * waitForAbort (ex.: fetch longo) quando o join de 5s estourar, o
 * flag é limpo mesmo assim e ela pode rodar mais um ciclo ocioso
 * (waitForAbort, no máximo 60s) antes de sair no próximo abort.
 * Inofensivo — o serviço do Vulgata, com os settings forçados off,
 * só espera no Monitor — e documentado aqui p/ não redescobrir.
 */
@Singleton
class KodiServiceRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Resultado de um dispatch RunScript (aotv_kodi.run_script). */
    data class ScriptResult(
        val ok: Boolean,
        val error: String = "",
        val diag: String = "",
    )

    private val lock = Any()
    private val threads = HashMap<String, Thread>()
    private var currentId: String? = null

    /** True se o addon declara um xbmc.service (service.py). */
    fun hasService(addonId: String): Boolean {
        return try {
            ensurePython()
            Python.getInstance().getModule("aotv_kodi")
                .callAttr("has_service", addonId).toBoolean()
        } catch (e: Exception) {
            false
        }
    }

    /** True se o serviço deste addon está rodando. */
    fun isRunning(addonId: String): Boolean {
        synchronized(lock) {
            val t = threads[addonId] ?: return false
            return t.isAlive
        }
    }

    /**
     * Inicia o serviço do addon (se tiver). Idempotente: se já
     * estiver rodando, devolve true sem fazer nada. Se outro serviço
     * estiver rodando, para-o primeiro (com join de até 5s).
     * Pode ser chamado de qualquer thread (o open() do plugin runner
     * roda em Dispatchers.Default); nunca bloqueia a UI.
     */
    fun startService(addonId: String): Boolean {
        synchronized(lock) {
            if (threads[addonId]?.isAlive == true) return true
        }
        // Para outro serviço rodando antes de limpar o flag — sem
        // isso a thread antiga, presa no waitForAbort, veria o flag
        // limpo e rodaria mais um ciclo em vez de sair.
        val previous = synchronized(lock) { currentId }
        if (previous != null && previous != addonId) {
            stopServiceAndWait(previous, timeoutMs = 5000L)
        }
        if (!hasService(addonId)) {
            return false
        }
        return try {
            ensurePython()
            Python.getInstance().getModule("aotv_kodi")
                .callAttr("clear_service_abort")
            val thread = Thread({
                try {
                    Python.getInstance().getModule("aotv_kodi")
                        .callAttr("run_service", addonId)
                } catch (e: Exception) {
                    // O serviço nunca pode derrubar o app: registra
                    // e morre em silêncio (o log do Python já tem o
                    // erro; o diag do run_service cobre o resto).
                    android.util.Log.w(
                        "KodiServiceRunner",
                        "service $addonId terminou com erro: $e",
                    )
                } finally {
                    synchronized(lock) {
                        if (threads[addonId]?.let { it == Thread.currentThread() } == true) {
                            threads.remove(addonId)
                        }
                        if (currentId == addonId) currentId = null
                    }
                }
            }, "kodi-service-$addonId").apply { isDaemon = true }
            synchronized(lock) {
                threads[addonId] = thread
                currentId = addonId
            }
            thread.start()
            true
        } catch (e: Exception) {
            android.util.Log.w("KodiServiceRunner", "startService $addonId falhou: $e")
            false
        }
    }

    /**
     * Para o serviço do addon (não-bloqueante): tira do mapa e pede
     * o abort. A thread sai sozinha do waitForAbort e se remove do
     * mapa no finally. Nunca dar join aqui — o onCleared pode rodar
     * na main thread.
     */
    fun stopService(addonId: String) {
        synchronized(lock) {
            threads.remove(addonId)
            if (currentId == addonId) currentId = null
        }
        // Sem Python iniciado, não há serviço rodando — nem vale
        // iniciar o interpretador só p/ abortar nada.
        if (!Python.isStarted()) return
        try {
            Python.getInstance().getModule("aotv_kodi")
                .callAttr("request_service_abort")
        } catch (e: Exception) {
            // O abort não foi entregue; a thread sai no próximo
            // stop/start (o flag fica como estava).
        }
    }

    /**
     * Para o serviço e espera a thread sair (até timeoutMs). Usado
     * pelo startService antes de limpar o flag de abort. Se o join
     * estourar, a thread fica órfã (ver a corrida documentada no
     * cabeçalho) — o flag é limpo mesmo assim p/ o novo serviço
     * poder rodar.
     */
    fun stopServiceAndWait(addonId: String, timeoutMs: Long = 5000L) {
        val thread = synchronized(lock) {
            threads.remove(addonId)?.also {
                if (currentId == addonId) currentId = null
            }
        } ?: return
        if (!Python.isStarted()) return
        try {
            Python.getInstance().getModule("aotv_kodi")
                .callAttr("request_service_abort")
        } catch (e: Exception) {
            return
        }
        try {
            thread.join(timeoutMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Para todos os serviços (saída do app / troca de addon). */
    fun stopAll() {
        val ids = synchronized(lock) { threads.keys.toList() }
        for (id in ids) stopService(id)
    }

    /**
     * Dispatch RunScript (Fase 3) — ponto de entrada p/ a UI/teste
     * invocarem `RunScript(addon_id, func_name)` sem passar pelo
     * executebuiltin do Python. Roda em Dispatchers.Default (o
     * dispatch pode demorar segundos — o BrazucaPlay dorme 2s no
     * limparFavoritos). Não constrói UI (isso é Fase 4).
     */
    suspend fun runScript(addonId: String, funcName: String): ScriptResult =
        withContext(Dispatchers.Default) {
            try {
                ensurePython()
                val res = Python.getInstance().getModule("aotv_kodi")
                    .callAttr("run_script", addonId, funcName)
                ScriptResult(
                    ok = dictBool(res, "ok"),
                    error = dictStr(res, "error"),
                    diag = dictStr(res, "diag"),
                )
            } catch (e: Exception) {
                ScriptResult(
                    ok = false,
                    error = "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }

    /** Inicia o Python (só na 1ª vez) e configura os caminhos special://. */
    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        Python.getInstance().getModule("aotv_kodi").callAttr(
            "configure",
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
        )
    }

    private fun dictStr(dict: com.chaquo.python.PyObject, key: String): String =
        runCatching { dict.callAttr("__getitem__", key).toString() }.getOrDefault("")

    private fun dictBool(dict: com.chaquo.python.PyObject, key: String): Boolean =
        runCatching { dict.callAttr("__getitem__", key).toBoolean() }.getOrDefault(false)
}
