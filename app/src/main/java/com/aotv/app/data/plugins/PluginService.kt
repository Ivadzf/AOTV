package com.aotv.app.data.plugins

import android.util.Log
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.StremioStream
import com.aotv.app.data.remote.hasAtmos
import com.aotv.app.data.remote.hasDolbyVision
import com.aotv.app.data.remote.hasHdr
import com.aotv.app.data.remote.ptAudio
import com.aotv.app.data.remote.qualityRankOf
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        private const val MAX_CONCURRENT_PLUGINS = 10
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
    ): List<StremioStream> {
        if (tmdbId <= 0) return emptyList()
        val code = store.readCode(plugin.id) ?: return emptyList()
        val timeoutMs = prefs.pluginTimeoutSec * 1000L
        val settings = store.pluginSettings(plugin.id)
        val started = System.currentTimeMillis()
        val result = pluginSemaphore.withPermit {
            engine.runGetStreams(
                plugin = plugin, code = code, tmdbId = tmdbId, mediaType = mediaType,
                season = season, episode = episode, settings = settings,
                timeoutMs = timeoutMs, cookieJar = store.cookieJar(plugin.id),
            )
        }
        val elapsed = System.currentTimeMillis() - started
        val raw = result.getOrElse { e ->
            recordLastRun(plugin.id, e, 0, elapsed)
            Log.w(TAG, "plugin ${plugin.id} falhou: ${e.message}")
            return emptyList()
        }
        val streams = raw.mapNotNull { normalize(it, plugin.name) }
            .distinctBy { it.url }
            .sortedByDescending { it.qualityRank }
        recordLastRun(plugin.id, null, streams.size, elapsed)
        return streams
    }

    /**
     * Registra o resultado da execução p/ o diagnóstico da tela de Plugins.
     * Nunca lança nem atrasa o retorno: uma falha aqui não pode derrubar
     * a lista de fontes.
     */
    private fun recordLastRun(pluginId: String, error: Throwable?, count: Int, millis: Long) {
        runCatching {
            val now = System.currentTimeMillis()
            val run = when {
                error is TimeoutCancellationException -> PluginLastRun(
                    status = "timeout", millis = millis, at = now,
                    detail = "Tempo esgotado (${millis / 1000}s)",
                )
                error != null -> PluginLastRun(
                    status = "error", millis = millis, at = now,
                    detail = (error.message ?: error.javaClass.simpleName).take(120),
                )
                count > 0 -> PluginLastRun(status = "ok", streams = count, millis = millis, at = now)
                else -> PluginLastRun(status = "empty", millis = millis, at = now)
            }
            prefs.setPluginLastRun(pluginId, run)
        }
    }

    /** Descritores de onSettings() (p/ a tela de Plugins). Vazio se não exportar. */
    suspend fun loadSettingDescriptors(plugin: InstalledPlugin): List<PluginSettingDescriptor> {
        val code = store.readCode(plugin.id) ?: return emptyList()
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
