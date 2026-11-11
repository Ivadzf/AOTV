package com.aotv.app.data.plugins

import android.content.Context
import android.util.Base64
import android.util.Log
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.AppDns
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor JS dos plugins do Nuvio (QuickJS via dokar3/quickjs-kt-android
 * 1.0.0-alpha12, mesmo lineage do motor do Nuvio).
 *
 * Por que alpha12 e não 1.0.18: o projeto usa Kotlin 2.1.20 e TODA a linha
 * 1.0.x estável do quickjs-kt foi compilada com Kotlin 2.4 — o compilador
 * 2.1.20 rejeita os metadados 2.4 e o build quebra (foi o que derrubou a
 * primeira v1.0.2). A alpha12 foi compilada com Kotlin 2.0.0 e tem a mesma
 * API usada aqui (create/evaluate/define/function/asyncFunction/close).
 * Limitação dela: NÃO tem setEvaluationTimeoutMillis/interruptEvaluation —
 * o timeout é aplicado no nível da coroutine (withTimeout); a avaliação JS
 * não é interrompida de verdade, só abandonada. Por isso o motor NUNCA é
 * fechado quando o timeout (ou o cancelamento) pode ter deixado a avaliação
 * rodando: fechar o runtime sob uma avaliação em andamento derruba o app
 * (SIGSEGV em libquickjs.so). O motor abandonado vaza — melhor que o crash.
 * Toda a execução roda numa thread dedicada por motor (nada de JS na Main).
 *
 * Diferenças para o Nuvio (o motivo do AOTV existir):
 * - fetch com cookie jar PERSISTENTE em disco por scraper (PluginCookieJar);
 * - timeout por execução ajustável (padrão 60 s, 30–180 s nas Configurações).
 *
 * Uma instância NOVA de QuickJs por execução (igual ao Nuvio): nada persiste
 * entre execuções além dos cookies (disco) — sem localStorage, sem WebSocket
 * (fica p/ v1.1), sem WASM (stub, como no Nuvio).
 *
 * v1.0.4 — POOL DE MOTORES: criar um runtime por execução custava caro (prelude
 * + crypto-js 219 KB + provider avaliados toda vez) e, somado ao abandono em
 * timeout, inflava a RAM (o app chegava a 2,5 GB). Agora cada plugin tem UM
 * runtime reutilizável no pool (máx. 10, LRU): prelude + crypto-js + código
 * são avaliados UMA vez, na criação; as execuções seguintes só avaliam o
 * driver getStreams. A chave TMDB e o timeout do fetch são atualizados a cada
 * execução (podem ter mudado desde a criação). Em timeout/cancelamento o
 * runtime é REMOVIDO do pool sem fechar (mesma regra anti-SIGSEGV de antes);
 * na reinstalação/remoção do plugin o runtime é invalidado (PluginStore).
 * Cada runtime pooled continua com sua thread dedicada (o motor exige uso
 * single-thread por instância) e um mutex: uma execução por vez por runtime.
 *
 * O Rhino foi descartado: testado localmente, nem 1.7.14 nem 1.8.1 parseiam
 * `async function` — e 49 dos 55 providers usam `async function getStreams`.
 */
@Singleton
class PluginEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PlatformPrefs,
) {

    companion object {
        private const val TAG = "AOTVPlugin"

        /** UA de browser: vários hosts bloqueiam UA vazio ou de app. */
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        /** Máx. de runtimes residentes no pool (LRU). */
        private const val MAX_POOLED_ENGINES = 10

        /** Id reservado do runtime usado pelo teste de rede (sem provider). */
        private const val NETWORK_TEST_ID = "__network_test__"
        private const val NETWORK_TEST_SHA = "net-test-v1"
    }

    /** Um runtime QuickJS reutilizável (um por plugin). */
    private class PooledEngine(
        val id: String,
        val dispatcher: ExecutorCoroutineDispatcher,
        @Volatile var fetchClient: OkHttpClient,
        val codeSha: String,
    ) {
        @Volatile var qjs: QuickJs? = null
        /** Ponte do Cheerio (reutilizada; limpa entre execuções — ver PluginCheerio.clear). */
        val cheerio: PluginCheerio = PluginCheerio()
        /** Uma execução por vez por runtime (o QuickJS exige uso single-thread). */
        val mutex: Mutex = Mutex()
        /** Status HTTP do último fetch feito neste runtime (-1 = nenhum). */
        @Volatile var lastFetchStatus: Int = -1
        /** Última mensagem de console.error da execução atual (diagnóstico). */
        @Volatile var lastConsoleError: String? = null
        /** Prévia do corpo da última resposta de fetch da execução atual. */
        @Volatile var lastBodyPreview: String? = null
        @Volatile var lastUsedAt: Long = System.currentTimeMillis()
    }

    /** Pool de runtimes (LinkedHashMap access-order = LRU). Guardado por poolLock. */
    private val pool = LinkedHashMap<String, PooledEngine>(16, 0.75f, true)
    private val poolLock = Any()

    /** Fechamento de runtimes descartados (nunca na thread de quem chamou). */
    private val bgScope = CoroutineScope(Dispatchers.Default)

    private var cachedCryptoJs: String? = null

    private fun readCryptoJsAsset(): String? {
        cachedCryptoJs?.let { return it }
        val text = runCatching {
            context.assets.open("plugin-libs/crypto-js-4.2.0.js").bufferedReader().use { it.readText() }
        }.getOrNull()
        cachedCryptoJs = text
        return text
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Roda getStreams(tmdbId, mediaType, season, episode, settings) do plugin e
     * devolve os streams crus. Erro de JS, timeout ou código inválido vira
     * Result.failure — um plugin quebrado não derruba os outros.
     */
    suspend fun runGetStreams(
        plugin: InstalledPlugin,
        code: String,
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        settings: Map<String, Any?>,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
    ): Result<List<PluginStreamRaw>> = runCatching {
        val settingsJson = JSONObject().apply {
            settings.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JSONObject.NULL)
                    is Boolean, is Number, is String -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }.toString()
        val json = execute(
            pluginId = plugin.id,
            code = code,
            codeSha = plugin.sha256.ifBlank { code.hashCode().toString() },
            timeoutMs = timeoutMs,
            cookieJar = cookieJar,
        ) { qjs ->
            // O retorno do evaluate é ignorado de propósito: o alpha12
            // devolve a string "Promise" em vez do JSON resolvido (provado
            // na v1.0.5 pelo Testar rede — era isso que zerava TODOS os
            // plugins). O driver grava o resultado no global e o Kotlin lê
            // por polling — mesmo padrão do testNetworkFetch.
            qjs.evaluate<String>(
                PluginPrelude.getStreamsDriver(tmdbId, mediaType, season, episode, settingsJson),
            )
            pollGlobal(qjs, "__aotvStreamsResult", timeoutMs)
        }
        parseStreams(json)
    }

    /** Roda onSettings() do plugin e devolve os descritores (vazio se não exportar). */
    suspend fun runOnSettings(
        plugin: InstalledPlugin,
        code: String,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
    ): Result<List<PluginSettingDescriptor>> = runCatching {
        val json = execute(
            pluginId = plugin.id,
            code = code,
            codeSha = plugin.sha256.ifBlank { code.hashCode().toString() },
            timeoutMs = timeoutMs,
            cookieJar = cookieJar,
        ) { qjs ->
            // Mesmo padrão do getStreams (v1.0.6): o retorno do evaluate é
            // ignorado, o driver grava no global e o Kotlin lê por polling.
            qjs.evaluate<String>(PluginPrelude.ON_SETTINGS_DRIVER)
            pollGlobal(qjs, "__aotvSettingsResult", timeoutMs)
        }
        parseSettings(json)
    }

    /** Status HTTP do último fetch do plugin (-1 = nenhum fetch ainda). */
    fun lastFetchStatus(pluginId: String): Int =
        synchronized(poolLock) { pool[pluginId]?.lastFetchStatus ?: -1 }

    /** Última mensagem de console.error da execução atual do plugin. */
    fun lastConsoleError(pluginId: String): String? =
        synchronized(poolLock) { pool[pluginId]?.lastConsoleError }

    /** Prévia do corpo da última resposta de fetch da execução atual. */
    fun lastBodyPreview(pluginId: String): String? =
        synchronized(poolLock) { pool[pluginId]?.lastBodyPreview }

    /**
     * Descarta o runtime em pool do plugin (código reinstalado/removido).
     * Fecha o runtime SÓ se estiver ocioso; se estiver rodando, não fecha
     * (regra anti-SIGSEGV) — o runtime vaza e a próxima execução cria um novo.
     * O fechamento roda em background: fechar um runtime na thread de quem
     * chamou (Main, nas ações de UI) travava o app na v1.0.4.
     */
    fun invalidate(pluginId: String) {
        val pooled = synchronized(poolLock) { pool.remove(pluginId) } ?: return
        if (pooled.mutex.tryLock()) {
            // Nada rodando (o tryLock provou) e nada novo pode começar (fora
            // do pool; quem estiver esperando aborta na checagem do pool) —
            // fechar em background é seguro.
            pooled.mutex.unlock()
            closeInBackground(pooled)
        }
    }

    /** Fecha um runtime ocioso em background (nunca na thread de quem chamou). */
    private fun closeInBackground(pooled: PooledEngine) {
        bgScope.launch {
            runCatching { pooled.qjs?.close() }
            pooled.dispatcher.close()
        }
    }

    /** Descarta todos os runtimes do pool (ex.: cookies apagados). */
    fun invalidateAll() {
        val ids = synchronized(poolLock) { pool.keys.toList() }
        ids.forEach { invalidate(it) }
    }

    /**
     * Fetch de controle pelo MESMO motor/fetch dos plugins: busca o manifest
     * do repo e devolve o status HTTP. É o discriminador, no celular, entre
     * "o fetch do motor não funciona" (0) e "os providers devolvem vazio" (200).
     *
     * v1.0.5 — o teste antigo avaliava uma IIFE async e fazia parse do valor
     * devolvido por evaluate<String>: em algum caminho o evaluate devolve a
     * string literal "Promise" em vez do JSON resolvido, e JSONObject("Promise")
     * lança "Value Promise of type java.lang.String cannot be converted to
     * JSONObject" no Android. O teste agora GRAVA o resultado num global e o
     * Kotlin o lê por polling — funciona devolva o evaluate uma Promise ou não.
     * O valor devolvido pelo evaluate do disparo é ignorado de propósito.
     */
    suspend fun testNetworkFetch(timeoutMs: Long = 30_000L): Result<Int> = runCatching {
        val url = PluginStore.DEFAULT_REPO_URL
        val found = execute(
            pluginId = NETWORK_TEST_ID,
            code = "",
            codeSha = NETWORK_TEST_SHA,
            timeoutMs = timeoutMs,
            cookieJar = PluginCookieJar(File(context.filesDir, "plugin-cookies/$NETWORK_TEST_ID.json")),
        ) { qjs ->
            qjs.evaluate<String>("globalThis.__netTestResult = ''; 'ok';")
            // Dispara o fetch; o retorno é ignorado (pode vir como "Promise").
            qjs.evaluate<String>(
                "(async function(){ try { var r = await fetch(" + JSONObject.quote(url) + "); " +
                    "var t = await r.text(); globalThis.__netTestResult = JSON.stringify({status: r.status, len: t.length}); } " +
                    "catch (e) { globalThis.__netTestResult = JSON.stringify({status: 0, error: String(e && e.message || e)}); } })()",
            )
            // Polling do global: lê até o fetch terminar ou o prazo acabar.
            // O delay() fica FORA do runCatching para o cancelamento (timeout)
            // continuar propagando e cair no dropEngine anti-SIGSEGV.
            var result = ""
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val cur = runCatching {
                    qjs.evaluate<String>(
                        "typeof globalThis.__netTestResult === 'string' ? globalThis.__netTestResult : ''",
                    )
                }.getOrNull().orEmpty()
                if (cur.isNotEmpty()) {
                    result = cur
                    break
                }
                delay(250)
            }
            result
        }
        val obj = JSONObject(found)
        val err = obj.optString("error").takeIf { it.isNotBlank() }
        if (err != null) throw IllegalStateException(err)
        obj.optInt("status", 0)
    }

    /**
     * Lê um global de resultado por polling (mesmo padrão do Testar rede da
     * v1.0.5, extraído p/ reutilizar no getStreams e no onSettings): o
     * evaluate do QuickJS alpha12 devolve a string "Promise" em vez do valor
     * resolvido do IIFE, então o driver grava o resultado num global e o
     * Kotlin lê até ele aparecer ou o prazo acabar. Devolve "" se o prazo
     * acabar (quem chama trata como lista vazia).
     * O delay() fica fora do runCatching para o cancelamento (timeout)
     * continuar propagando e cair no dropEngine anti-SIGSEGV.
     */
    private suspend fun pollGlobal(qjs: QuickJs, name: String, timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val cur = runCatching {
                qjs.evaluate<String>("typeof globalThis.$name === 'string' ? globalThis.$name : ''")
            }.getOrNull().orEmpty()
            if (cur.isNotEmpty()) return cur
            delay(250)
        }
        return ""
    }

    // ── Execução ──────────────────────────────────────────────────────────────

    private suspend fun <T> execute(
        pluginId: String,
        code: String,
        codeSha: String,
        timeoutMs: Long,
        cookieJar: PluginCookieJar,
        driver: suspend (QuickJs) -> T,
    ): T {
        val pooled = acquireEngine(pluginId, code, codeSha, cookieJar, timeoutMs)
        pooled.mutex.lock()
        try {
            // O runtime pode ter sido descartado enquanto aguardava o lock
            // (timeout de outra execução): não roda num motor abandonado.
            val current = synchronized(poolLock) { pool[pluginId] }
            if (current !== pooled) throw IllegalStateException("motor do plugin foi descartado")
            // Cliente com o timeout atual + chave TMDB atual (podem ter mudado
            // desde a criação do runtime; o prelude só roda na criação).
            pooled.fetchClient = buildFetchClient(cookieJar, timeoutMs)
            // Handles do Cheerio da execução anterior não valem mais (o JS da
            // execução passada foi descartado); sem limpar, o mapa cresceria
            // para sempre no runtime reutilizado.
            pooled.cheerio.clear()
            // Diagnóstico da execução atual começa zerado.
            pooled.lastConsoleError = null
            pooled.lastBodyPreview = null
            val result = withContext(pooled.dispatcher) {
                val qjs = pooled.qjs ?: throw IllegalStateException("motor sem runtime")
                val keyJson = prefs.tmdbApiKey.takeIf { it.isNotBlank() }?.let { JSONObject.quote(it) } ?: "undefined"
                qjs.evaluate<String>("TMDB_API_KEY = $keyJson; globalThis.TMDB_API_KEY = $keyJson; 'ok';")
                // Sem interrupt nativo no motor alpha12: o timeout é aplicado
                // aqui, no nível da coroutine (withTimeout). A avaliação JS
                // em andamento NÃO é interrompida — ela termina sozinha na
                // thread dela. Os 15 s extras são a margem p/ o fetch em
                // andamento devolver (callTimeout = timeoutMs).
                withTimeout(timeoutMs + 15_000) { driver(qjs) }
            }
            pooled.lastUsedAt = System.currentTimeMillis()
            synchronized(poolLock) { pool[pluginId] } // touch p/ LRU (get em access-order)
            return result
        } catch (e: CancellationException) {
            // Timeout ou cancelamento: remove do pool SEM fechar o runtime — a
            // avaliação pode ainda estar rodando e fechar derrubaria o app
            // (SIGSEGV). O runtime vaza; a próxima execução cria um novo.
            dropEngine(pluginId, pooled)
            throw e
        } finally {
            pooled.mutex.unlock()
        }
    }

    /**
     * Devolve o runtime do plugin, criando-o preguiçosamente na primeira
     * execução (prelude + crypto-js + código avaliados UMA vez). Evict LRU
     * quando o pool está cheio (só ociosos — fechar runtime em uso é proibido).
     */
    private suspend fun acquireEngine(
        id: String,
        code: String,
        codeSha: String,
        cookieJar: PluginCookieJar,
        timeoutMs: Long,
    ): PooledEngine {
        var stale: PooledEngine? = null
        synchronized(poolLock) {
            val existing = pool[id]
            if (existing != null) {
                if (existing.codeSha == codeSha) return existing
                // Código mudou sem passar pela invalidação: descarta o velho.
                pool.remove(id)
                if (existing.mutex.tryLock()) {
                    // Ocioso (o tryLock provou) e fora do pool: fechar é seguro,
                    // mas em background — fechar runtime na Main travava o app.
                    existing.mutex.unlock()
                    stale = existing
                }
                // Se estiver rodando, o runtime velho vaza (1x); a invalidação
                // normal (install/remove) já cobre o caso comum.
            }
        }
        stale?.let { closeInBackground(it) }
        val created = createEngine(id, code, codeSha, cookieJar, timeoutMs)
        val toClose = mutableListOf<PooledEngine>()
        val result: PooledEngine = synchronized(poolLock) {
            val raced = pool[id]
            if (raced != null && raced !== created) {
                if (raced.codeSha == codeSha) {
                    // Outra coroutine criou primeiro: o criado nunca atendeu
                    // execução; fechar é seguro (em background).
                    toClose += created
                    return@synchronized raced
                }
                // Corrida com código novo: o registrado é o velho — troca pelo criado.
                pool.remove(id)
                // (o velho pode estar em uso; descartado sem fechar, vaza 1x)
            }
            // Evict LRU até caber (só ociosos — fechar runtime em uso é
            // proibido). A remoção é síncrona; o fechamento é em background.
            val candidates = pool.entries.toList()
            for (victim in candidates) {
                if (pool.size < MAX_POOLED_ENGINES) break
                if (victim.key == id) continue
                if (!victim.value.mutex.tryLock()) continue
                try {
                    if (pool.remove(victim.key, victim.value)) toClose += victim.value
                } finally {
                    victim.value.mutex.unlock()
                }
            }
            pool[id] = created
            created
        }
        toClose.forEach { closeInBackground(it) }
        return result
    }

    /** Cria um runtime (thread dedicada + bindings + prelude + crypto-js + código). */
    private suspend fun createEngine(
        id: String,
        code: String,
        codeSha: String,
        cookieJar: PluginCookieJar,
        timeoutMs: Long,
    ): PooledEngine {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val pooled = PooledEngine(id, dispatcher, buildFetchClient(cookieJar, timeoutMs), codeSha)
        try {
            withContext(dispatcher) {
                val qjs = QuickJs.create(dispatcher)
                pooled.qjs = qjs
                defineBindings(qjs, pooled)
                qjs.evaluate<String>(PluginPrelude.build(prefs.tmdbApiKey) + "\n;'ok';")
                readCryptoJsAsset()?.let { cryptoJs ->
                    qjs.evaluate<String>(cryptoJs + "\n;'ok';")
                    qjs.evaluate<String>(PluginPrelude.POST_CRYPTOJS)
                }
                if (code.isNotBlank()) qjs.evaluate<String>(code + "\n;'ok';")
            }
            return pooled
        } catch (e: Exception) {
            // Criação falhou (código inválido etc.): o runtime nunca rodou, fechar é seguro.
            runCatching { pooled.qjs?.close() }
            dispatcher.close()
            throw e
        }
    }

    /** Remove o runtime do pool SEM fechar o QuickJs (regra anti-SIGSEGV). */
    private fun dropEngine(id: String, pooled: PooledEngine) {
        synchronized(poolLock) { if (pool[id] === pooled) pool.remove(id) }
        // NÃO fecha o qjs (a avaliação pode ainda estar rodando). O dispatcher
        // pode fechar: a tarefa em andamento termina sozinha e a thread morre.
        pooled.dispatcher.close()
    }

    /**
     * Cliente do fetch dos plugins — MESMA construção do cliente dos addons
     * Stremio (StremioAddonService), que funciona no celular: builder limpo +
     * DNS-over-HTTPS do AppDns. Antes era mediaClient.newBuilder() (o cliente
     * compartilhado do Coil/player) e TODO plugin devolvia lista vazia.
     */
    private fun buildFetchClient(cookieJar: PluginCookieJar, timeoutMs: Long): OkHttpClient =
        OkHttpClient.Builder()
            .dns(AppDns.cloudflare)
            .cookieJar(cookieJar)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private fun defineBindings(qjs: QuickJs, pooled: PooledEngine) {
        qjs.define("console") {
            function("log") { args -> pluginLog("D", args); 0 }
            function("info") { args -> pluginLog("I", args); 0 }
            function("warn") { args -> pluginLog("W", args); 0 }
            function("error") { args ->
                // O erro do provider (ex.: "[BRFlix] Erro: tag GCM inválida")
                // é o diagnóstico do "nenhum stream": vai para a tela de Plugins.
                pooled.lastConsoleError = args.joinToString(" ") { it?.toString().orEmpty() }.take(160)
                pluginLog("E", args)
                0
            }
        }
        qjs.function("__atob") { args ->
            runCatching { atob(argStr(args, 0)) }.getOrElse { throw IllegalArgumentException("atob: entrada inválida") }
        }
        qjs.function("__btoa") { args ->
            runCatching { btoa(argStr(args, 0)) }.getOrElse { throw IllegalArgumentException("btoa: entrada inválida") }
        }
        qjs.asyncFunction("__plugin_sleep") { args ->
            delay(argLong(args, 0).coerceAtLeast(0))
            1
        }
        qjs.asyncFunction("__nativeFetch") { args ->
            // Cliente e status lidos do runtime pooled no momento da chamada
            // (o cliente é refeito a cada execução, com o timeout atual).
            nativeFetch(
                args,
                pooled.fetchClient,
                onStatus = { pooled.lastFetchStatus = it },
                onBody = { body ->
                    // Prévia do corpo da ÚLTIMA resposta (o que o provider
                    // parseou por último antes de devolver []): distingue JSON
                    // real de página de bloqueio/desafio com status 200.
                    pooled.lastBodyPreview = body.replace(Regex("\\s+"), " ").take(80)
                },
            )
        }
        qjs.function("__parseUrl") { args -> parseUrl(argStr(args, 0), argNullableStr(args, 1)) }
        qjs.function("__aesGcmDecrypt") { args ->
            aesGcmDecrypt(argStr(args, 0), argStr(args, 1), argStr(args, 2))
        }
        qjs.function("__cheerioLoad") { args -> pooled.cheerio.load(argStr(args, 0)) }
        qjs.function("__cheerioSelect") { args -> pooled.cheerio.select(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioText") { args -> pooled.cheerio.text(argInt(args, 0)) }
        qjs.function("__cheerioAttr") { args -> pooled.cheerio.attr(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioHtml") { args -> pooled.cheerio.html(argInt(args, 0)) }
        qjs.function("__cheerioCount") { args -> pooled.cheerio.count(argInt(args, 0)) }
        qjs.function("__cheerioElement") { args -> pooled.cheerio.element(argInt(args, 0), argInt(args, 1)) }
        qjs.function("__cheerioChildren") { args -> pooled.cheerio.children(argInt(args, 0)) }
        qjs.function("__cheerioParent") { args -> pooled.cheerio.parent(argInt(args, 0)) }
        qjs.function("__cheerioFilter") { args -> pooled.cheerio.filter(argInt(args, 0), argStr(args, 1)) }
        qjs.function("__cheerioCombine") { args -> pooled.cheerio.combine(argStr(args, 0)) }
        qjs.function("__cheerioParseFragment") { args -> pooled.cheerio.parseFragment(argStr(args, 0)) }
    }

    // ── Bindings: HTTP ────────────────────────────────────────────────────────

    /**
     * fetch do JS via OkHttp, com o cookie jar do scraper. Devolve JSON
     * {status, statusText, headers: {nome: [valores]}, body}. Erro de rede
     * vira status 0 (o provider decide pelo res.ok) em vez de rejeitar a
     * Promise — um fetch que falha não pode travar a execução inteira.
     */
    private suspend fun nativeFetch(
        args: Array<Any?>, client: OkHttpClient, onStatus: ((Int) -> Unit)? = null,
        onBody: ((String) -> Unit)? = null,
    ): String {
        val url = argStr(args, 0)
        val opts = runCatching { JSONObject(argStr(args, 1)) }.getOrNull() ?: JSONObject()
        val method = opts.optString("method", "GET").uppercase().ifBlank { "GET" }
        val headersObj = opts.optJSONObject("headers")
        val bodyText = if (opts.isNull("body")) null else opts.optString("body")
        return try {
            val reqBuilder = Request.Builder().url(url)
            headersObj?.keys()?.forEach { name ->
                when (val v = headersObj.get(name)) {
                    is JSONArray -> for (i in 0 until v.length()) reqBuilder.addHeader(name, v.optString(i))
                    else -> reqBuilder.addHeader(name, v.toString())
                }
            }
            if (!hasHeaderIgnoreCase(headersObj, "user-agent")) reqBuilder.header("User-Agent", BROWSER_UA)
            val contentType = headersObj?.let { findHeaderIgnoreCase(it, "content-type") }?.toMediaTypeOrNull()
            val requestBody =
                if (bodyText != null && method != "GET" && method != "HEAD") bodyText.toRequestBody(contentType) else null
            reqBuilder.method(method, requestBody)
            withContext(Dispatchers.IO) {
                client.newCall(reqBuilder.build()).execute().use { res ->
                    onStatus?.invoke(res.code)
                    val headersJson = JSONObject()
                    res.headers.forEach { (name, value) ->
                        val key = name.lowercase()
                        val arr = headersJson.optJSONArray(key) ?: JSONArray().also { headersJson.put(key, it) }
                        arr.put(value)
                    }
                    val body = res.body?.string().orEmpty()
                    onBody?.invoke(body)
                    JSONObject()
                        .put("status", res.code)
                        .put("statusText", res.message)
                        .put("headers", headersJson)
                        .put("body", body)
                        .toString()
                }
            }
        } catch (e: Exception) {
            onStatus?.invoke(0)
            JSONObject()
                .put("status", 0)
                .put("statusText", e.message ?: "network error")
                .put("headers", JSONObject())
                .put("body", "")
                .toString()
        }
    }

    private fun hasHeaderIgnoreCase(headers: JSONObject?, name: String): Boolean {
        if (headers == null) return false
        val it = headers.keys()
        while (it.hasNext()) if (it.next().equals(name, ignoreCase = true)) return true
        return false
    }

    private fun findHeaderIgnoreCase(headers: JSONObject, name: String): String? {
        val it = headers.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k.equals(name, ignoreCase = true)) return headers.optString(k)
        }
        return null
    }

    // ── Bindings: utilidades ──────────────────────────────────────────────────

    private fun atob(s: String): String =
        String(Base64.decode(s, Base64.DEFAULT), Charsets.ISO_8859_1)

    private fun btoa(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP)

    /** new URL(url, base) — parse com java.net.URI; ok=false vira TypeError no JS. */
    private fun parseUrl(url: String, base: String?): String {
        return runCatching {
            val uri = if (base != null) URI(base).resolve(url) else URI(url)
            val scheme = uri.scheme ?: throw IllegalArgumentException("sem scheme")
            val host = uri.host.orEmpty()
            val port = if (uri.port >= 0) uri.port.toString() else ""
            val authority = uri.authority.orEmpty()
            JSONObject()
                .put("ok", true)
                .put("href", uri.toString())
                .put("protocol", "$scheme:")
                .put("host", if (port.isNotEmpty()) "$host:$port" else host)
                .put("hostname", host)
                .put("port", port)
                .put("pathname", uri.rawPath?.ifEmpty { "/" } ?: "/")
                .put("search", uri.rawQuery?.let { "?$it" } ?: "")
                .put("hash", uri.rawFragment?.let { "#$it" } ?: "")
                .put("origin", "$scheme://$authority")
                .put("username", "")
                .put("password", "")
                .toString()
        }.getOrElse { JSONObject().put("ok", false).toString() }
    }

    /**
     * AES-GCM (javax.crypto) p/ o patch do CryptoJS: o crypto-js 4.2.0 não tem
     * modo GCM. Entradas em Base64 padrão; o ciphertext traz a tag de 16 bytes
     * no fim (formato do CryptoJS). Falha de autenticação lança — o provider
     * trata no try/catch dele.
     */
    private fun aesGcmDecrypt(ciphertextB64: String, keyB64: String, ivB64: String): String {
        val ct = Base64.decode(ciphertextB64, Base64.DEFAULT)
        val key = Base64.decode(keyB64, Base64.DEFAULT)
        val iv = Base64.decode(ivB64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun pluginLog(level: String, args: Array<Any?>) {
        val msg = args.joinToString(" ") { it?.toString().orEmpty() }.take(2000)
        when (level) {
            "E" -> Log.e(TAG, msg)
            "W" -> Log.w(TAG, msg)
            else -> Log.d(TAG, msg)
        }
    }

    // ── Parse dos resultados ──────────────────────────────────────────────────

    /** Normaliza o retorno de getStreams: array de objetos ou de strings. */
    private fun parseStreams(json: String): List<PluginStreamRaw> {
        val arr = runCatching {
            val trimmed = json.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optJSONArray("streams") ?: JSONArray()
            } else {
                JSONArray(trimmed)
            }
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PluginStreamRaw>()
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> if (item.startsWith("http")) out += PluginStreamRaw(url = item)
                is JSONObject -> {
                    val url = item.optString("url").ifBlank { item.optString("file") }
                        .ifBlank { item.optString("stream") }
                    if (!url.startsWith("http")) continue
                    val title = item.optString("title")
                    val label = item.optString("label").ifBlank { item.optString("name") }
                    out += PluginStreamRaw(url = url, title = title, label = label)
                }
            }
        }
        return out
    }

    private fun parseSettings(json: String): List<PluginSettingDescriptor> {
        val arr = runCatching { JSONArray(json.trim()) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PluginSettingDescriptor>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type")
            if (type.isBlank()) continue
            val options = mutableListOf<PluginSettingOption>()
            val optsArr = o.optJSONArray("options")
            if (optsArr != null) {
                for (j in 0 until optsArr.length()) {
                    val oo = optsArr.optJSONObject(j) ?: continue
                    options += PluginSettingOption(
                        label = oo.optString("label"),
                        value = oo.optString("value"),
                    )
                }
            }
            out += PluginSettingDescriptor(
                type = type,
                key = o.optString("key"),
                label = o.optString("label"),
                options = options,
                defaultValue = o.opt("defaultValue"),
            )
        }
        return out
    }

    // ── Argumentos dos bindings ───────────────────────────────────────────────

    private fun argStr(args: Array<Any?>, i: Int): String = args.getOrNull(i)?.toString().orEmpty()

    private fun argNullableStr(args: Array<Any?>, i: Int): String? = args.getOrNull(i)?.toString()

    private fun argInt(args: Array<Any?>, i: Int): Int =
        (args.getOrNull(i) as? Number)?.toInt() ?: args.getOrNull(i)?.toString()?.toIntOrNull() ?: 0

    private fun argLong(args: Array<Any?>, i: Int): Long =
        (args.getOrNull(i) as? Number)?.toLong() ?: args.getOrNull(i)?.toString()?.toLongOrNull() ?: 0L
}
