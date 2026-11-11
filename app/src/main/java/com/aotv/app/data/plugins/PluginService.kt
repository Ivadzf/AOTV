package com.aotv.app.data.plugins

import android.util.Log
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.StremioStream
import com.aotv.app.data.remote.hasAtmos
import com.aotv.app.data.remote.hasDolbyVision
import com.aotv.app.data.remote.hasHdr
import com.aotv.app.data.remote.ptAudio
import com.aotv.app.data.remote.qualityRankOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestra os plugins: roda os habilitados em paralelo (o limite de 10
 * simultâneos fica no ViewModel, igual aos addons) e normaliza o retorno
 * de getStreams() p/ StremioStream, com addon = nome do provider — a lista
 * de fontes (que já agrupa por addon desde a v1.0.1) mostra "FSHD",
 * "Redeflix" etc. como mais um grupo, junto com os addons Stremio.
 *
 * Os streams passam pelo mesmo probe de saúde (verify) da tela de detalhe,
 * porque entram na mesma lista de StremioStream.
 */
@Singleton
class PluginService @Inject constructor(
    private val store: PluginStore,
    private val engine: PluginEngine,
    private val prefs: PlatformPrefs,
) {

    companion object {
        private const val TAG = "AOTVPlugin"

        /** Máx. de plugins rodando ao mesmo tempo (igual ao teto do Nuvio). */
        // v1.0.9: 5 por vez (era 10 na v1.0.7). A folha lança os 55 plugins de
        // uma vez e o semáforo é o gargalo da fila; com 10, na internet lenta
        // do Davi os 10 fetches disputavam a mesma conexão fina, estouravam o
        // timeout e voltavam vazios (fetch: 0). Com 5, cada plugin tem mais
        // banda e menos timeout — a folha drena em mais lotes, mas traz stream.
        // O Testar manual não passa por aqui (lane própria, v1.0.9).
        private const val MAX_CONCURRENT_PLUGINS = 5
    }

    /** Cada execução abre um contexto QuickJS; sem o semáforo, 55 plugins
     * habilitados abririam 55 motores ao mesmo tempo no celular. */
    private val pluginSemaphore = Semaphore(MAX_CONCURRENT_PLUGINS)

    /** Plugins habilitados p/ o tipo ("movie" ou "tv"). */
    fun enabledPlugins(mediaType: String): List<InstalledPlugin> = store.enabledPlugins(mediaType)

    /**
     * Roda UM plugin e devolve os streams normalizados. Qualquer falha
     * (código ausente, erro de JS, timeout) devolve lista vazia e registra
     * no log — um plugin quebrado não derruba os outros nem a tela.
     */
    suspend fun getStreamsForPlugin(
        plugin: InstalledPlugin,
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        fresh: Boolean = false,
    ): List<StremioStream> {
        if (tmdbId <= 0) return emptyList()
        // Leitura do arquivo do provider fora da Main (era I/O de disco na
        // thread de UI a cada execução — um dos travamentos da v1.0.4).
        val code = withContext(Dispatchers.IO) { store.readCode(plugin.id) } ?: return emptyList()
        val timeoutMs = prefs.pluginTimeoutSec * 1000L
        val settings = store.pluginSettings(plugin.id)
        val started = System.currentTimeMillis()
        // fresh = true (Testar manual): fora da fila da folha e num motor novo
        // (v1.0.9). A folha lança 55 jobs que ocupam as 10 vagas por minutos e
        // o Testar ficava esperando atrás deles (306s) e estourava o timeout
        // vazio — o cronômetro aqui começa ANTES do semáforo, então a duração
        // que aparece na tela é maioria espera, não execução.
        val result = if (fresh) {
            engine.runGetStreams(
                plugin = plugin, code = code, tmdbId = tmdbId, mediaType = mediaType,
                season = season, episode = episode, settings = settings,
                timeoutMs = timeoutMs, cookieJar = store.cookieJar(plugin.id), fresh = true,
            )
        } else {
            pluginSemaphore.withPermit {
                engine.runGetStreams(
                    plugin = plugin, code = code, tmdbId = tmdbId, mediaType = mediaType,
                    season = season, episode = episode, settings = settings,
                    timeoutMs = timeoutMs, cookieJar = store.cookieJar(plugin.id),
                )
            }
        }
        val elapsed = System.currentTimeMillis() - started
        val raw = result.getOrElse { e ->
            recordLastRun(
                plugin.id, e, 0, elapsed,
                engine.lastFetchStatus(plugin.id),
                engine.lastConsoleError(plugin.id),
                engine.lastBodyPreview(plugin.id),
            )
            Log.w(TAG, "plugin ${plugin.id} falhou: ${e.message}")
            return emptyList()
        }
        val fetchStatus = engine.lastFetchStatus(plugin.id)
        val streams = raw.mapNotNull { normalize(it, plugin.name) }
            .distinctBy { it.url }
            .sortedByDescending { it.qualityRank }
        recordLastRun(
            plugin.id, null, streams.size, elapsed, fetchStatus,
            engine.lastConsoleError(plugin.id), engine.lastBodyPreview(plugin.id),
        )
        return streams
    }

    /**
     * Testar manual da tela de Plugins (v1.0.9): mesma execução do
     * getStreamsForPlugin, mas fora da fila da folha e num motor novo
     * (fresh = true). Sem isso, o Testar esperava atrás dos 55 jobs da folha
     * e voltava vazio depois de 300s — parecia que o Teste estava quebrado.
     */
    suspend fun getStreamsForPluginTest(
        plugin: InstalledPlugin,
        tmdbId: Int,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
    ): List<StremioStream> = getStreamsForPlugin(plugin, tmdbId, mediaType, season, episode, fresh = true)

    /**
     * Fetch de controle pelo motor dos plugins (diagnóstico de rede): devolve
     * o status HTTP do manifest do repo, ou falha com a mensagem do erro.
     */
    suspend fun testNetwork(): Result<Int> = engine.testNetworkFetch()

    /** Descarta todos os runtimes do pool (ex.: cookies apagados). */
    fun dropAllEngines() = engine.invalidateAll()

    /**
     * Registra o resultado da execução p/ o diagnóstico da tela de Plugins.
     * Nunca lança nem atrasa o retorno: uma falha aqui não pode derrubar
     * a lista de fontes.
     */
    private fun recordLastRun(
        pluginId: String, error: Throwable?, count: Int, millis: Long, fetchStatus: Int = -1,
        consoleError: String? = null, bodyPreview: String? = null,
    ) {
        runCatching {
            val now = System.currentTimeMillis()
            // "fetch: 200 · 3s": o status HTTP do último fetch da execução + a
            // duração. É o que separa "o provider não tem o título" (fetch 200
            // e vazio) de "o fetch do motor não funciona" (fetch 0 e vazio).
            val fetchDetail = if (fetchStatus >= 0) "fetch: $fetchStatus · ${millis / 1000}s" else "${millis / 1000}s"
            // O erro do console do provider e a prévia do corpo são o que
            // explica o "nenhum stream" com fetch 200: o provider engole o
            // erro no try/catch dele e devolve [] sem deixar rastro.
            val extras = buildString {
                if (!consoleError.isNullOrBlank()) append(" · erro: ${consoleError.take(100)}")
                if (!bodyPreview.isNullOrBlank()) append(" · body: ${bodyPreview.take(60)}")
            }
            val run = when {
                error is TimeoutCancellationException -> PluginLastRun(
                    status = "timeout", millis = millis, at = now,
                    detail = "Tempo esgotado (${millis / 1000}s)",
                )
                error != null -> PluginLastRun(
                    status = "error", millis = millis, at = now,
                    detail = ((error.message ?: error.javaClass.simpleName).take(120) + extras),
                )
                count > 0 -> PluginLastRun(status = "ok", streams = count, millis = millis, at = now, detail = fetchDetail)
                else -> PluginLastRun(status = "empty", millis = millis, at = now, detail = fetchDetail + extras)
            }
            prefs.setPluginLastRun(pluginId, run)
        }
    }

    /** Descritores de onSettings() (p/ a tela de Plugins). Vazio se não exportar. */
    suspend fun loadSettingDescriptors(plugin: InstalledPlugin): List<PluginSettingDescriptor> {
        val code = withContext(Dispatchers.IO) { store.readCode(plugin.id) } ?: return emptyList()
        val timeoutMs = prefs.pluginTimeoutSec * 1000L
        return engine.runOnSettings(
            plugin = plugin, code = code,
            timeoutMs = timeoutMs, cookieJar = store.cookieJar(plugin.id),
        ).getOrDefault(emptyList())
    }

    private fun normalize(raw: PluginStreamRaw, providerName: String): StremioStream? {
        val title = raw.title.ifBlank { raw.label }.ifBlank { providerName }
        val full = "${raw.title}\n${raw.label}\n$providerName".take(3000)
        val rank = qualityRankOf(raw.label).takeIf { it > 0 }
            ?: qualityRankOf(raw.title).takeIf { it > 0 }
            ?: qualityRankOf(full)
        return StremioStream(
            label = title.replace("\n", " · ").take(80),
            url = raw.url,
            addon = providerName,
            qualityRank = rank,
            hdr = hasHdr(full),
            dolbyVision = hasDolbyVision(full),
            atmos = hasAtmos(full),
            ptAudio = ptAudio(full),
            releaseText = full,
            fileName = "",
            fileSize = 0,
        )
    }
}
