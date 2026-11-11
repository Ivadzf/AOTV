package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.plugins.InstalledPlugin
import com.aotv.app.data.plugins.PluginLastRun
import com.aotv.app.data.plugins.PluginRepo
import com.aotv.app.data.plugins.PluginService
import com.aotv.app.data.plugins.PluginSettingDescriptor
import com.aotv.app.data.plugins.PluginStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tela de Plugins (scrapers JS do Nuvio). Instala repositórios por URL de
 * manifest, liga/desliga cada plugin, atualiza, remove, edita as
 * configurações do onSettings() e ajusta o timeout das execuções.
 *
 * O repo do Davi vem pré-preenchido no campo (PluginStore.DEFAULT_REPO_URL),
 * mas só instala quando o usuário toca em Instalar.
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val store: PluginStore,
    private val pluginService: PluginService,
) : ViewModel() {

    data class UiState(
        val repos: List<PluginRepo> = emptyList(),
        val plugins: List<InstalledPlugin> = emptyList(),
        val installing: Boolean = false,
        val error: String? = null,
        /** Mensagem curta do último resultado (instalação, teste, restore, cache). */
        val message: String? = null,
        val refreshing: Set<String> = emptySet(),
        val timeoutSec: Int = 60,
        /** Pausa global (nenhum plugin roda) — botão "Pausar plugins". */
        val globalEnabled: Boolean = true,
        /** Atualização automática dos repos vencidos ao abrir a tela. */
        val autoUpdate: Boolean = true,
        /** Plugins rodando ao mesmo tempo na folha (1–32, padrão 10). */
        val concurrency: Int = 10,
        /** Último resultado de cada plugin (id -> PluginLastRun, p/ diagnóstico). */
        val lastRun: Map<String, PluginLastRun> = emptyMap(),
        /** Plugins com teste manual em andamento (id). */
        val testing: Set<String> = emptySet(),
        /** Plugin aberto no diálogo de configurações (null = fechado). */
        val settingsPlugin: InstalledPlugin? = null,
        val settingsDescriptors: List<PluginSettingDescriptor> = emptyList(),
        val settingsLoading: Boolean = false,
        /** Valores do diálogo (chave -> valor), já com os padrões do onSettings(). */
        val settingsValues: Map<String, Any?> = emptyMap(),
        /** Teste de rede do motor em andamento. */
        val networkTesting: Boolean = false,
        /** Resultado do teste de rede ("Rede do motor: OK (200)" etc.). */
        val networkTest: String? = null,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            timeoutSec = prefs.pluginTimeoutSec,
            globalEnabled = prefs.pluginGlobalEnabled,
            autoUpdate = prefs.pluginAutoUpdate,
            concurrency = prefs.pluginConcurrency,
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.pluginRepos.collect { list -> _uiState.update { it.copy(repos = list) } }
        }
        viewModelScope.launch {
            prefs.installedPlugins.collect { list -> _uiState.update { it.copy(plugins = list) } }
        }
        viewModelScope.launch {
            prefs.pluginLastRuns.collect { map -> _uiState.update { it.copy(lastRun = map) } }
        }
        // Refresh automático dos repos vencidos (> 6 h), igual ao Nuvio — salvo quando desligado.
        viewModelScope.launch { if (prefs.pluginAutoUpdate) store.refreshDueRepos() }
    }

    /** Instala um repositório por URL de manifest (o manifest.json do repo). */
    fun install(raw: String) {
        val url = raw.trim()
        if (!url.startsWith("http")) {
            _uiState.update { it.copy(error = "Endereço inválido") }
            return
        }
        if (_uiState.value.repos.any { it.url.equals(url, ignoreCase = true) }) {
            _uiState.update { it.copy(error = "Esse repositório já está instalado") }
            return
        }
        _uiState.update { it.copy(installing = true, error = null, message = null) }
        viewModelScope.launch {
            val repo = store.installRepo(url)
            if (repo == null) {
                _uiState.update { it.copy(installing = false, error = "Não consegui ler o manifest desse endereço") }
                return@launch
            }
            val count = store.plugins.count { it.repoUrl.equals(url, ignoreCase = true) }
            _uiState.update {
                it.copy(
                    installing = false,
                    message = "${repo.name.ifBlank { url }} — $count plugin(s) instalado(s)",
                )
            }
        }
    }

    /** Atualiza um repo (baixa o manifest e os .js de novo). */
    fun refreshRepo(url: String) {
        if (url in _uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = it.refreshing + url, error = null) }
        viewModelScope.launch {
            val repo = store.refreshRepo(url)
            _uiState.update {
                it.copy(
                    refreshing = it.refreshing - url,
                    error = if (repo == null) "Não consegui atualizar esse repositório" else null,
                )
            }
        }
    }

    fun refreshAll() = _uiState.value.repos.forEach { refreshRepo(it.url) }

    fun removeRepo(url: String) = store.removeRepo(url)

    fun removePlugin(id: String) = store.removePlugin(id)

    fun togglePlugin(id: String) {
        val p = _uiState.value.plugins.firstOrNull { it.id == id } ?: return
        store.setPluginEnabled(id, !p.enabled)
    }

    /** Pausa/retoma um repositório (os plugins dele param de rodar nas fontes). */
    fun toggleRepo(url: String) {
        val r = _uiState.value.repos.firstOrNull { it.url.equals(url, true) } ?: return
        store.setRepoEnabled(url, !r.enabled)
    }

    /** Liga/desliga todos os plugins de um repositório de uma vez. */
    fun toggleAllPlugins(repoUrl: String, enabled: Boolean) = store.setAllPluginsEnabled(repoUrl, enabled)

    /** Sobe (direction = -1) ou desce (+1) um repositório na ordem de prioridade. */
    fun moveRepo(url: String, direction: Int) = store.moveRepo(url, direction)

    /** Sobe (direction = -1) ou desce (+1) um plugin na ordem de prioridade dentro do repositório dele. */
    fun movePlugin(id: String, direction: Int) = store.movePlugin(id, direction)

    /** Pausa global: desligado, nenhum plugin roda nas fontes. */
    fun setGlobalEnabled(enabled: Boolean) {
        prefs.pluginGlobalEnabled = enabled
        _uiState.update { it.copy(globalEnabled = enabled) }
    }

    /** Atualização automática dos repos vencidos ao abrir a tela. */
    fun setAutoUpdate(enabled: Boolean) {
        prefs.pluginAutoUpdate = enabled
        _uiState.update { it.copy(autoUpdate = enabled) }
    }

    /** Plugins rodando ao mesmo tempo na folha (1–32, padrão 10). */
    fun setConcurrency(n: Int) {
        val v = n.coerceIn(1, 32)
        prefs.pluginConcurrency = v
        _uiState.update { it.copy(concurrency = v) }
    }

    /** Limpa o cache dos provedores (cookies + runtimes em pool). */
    fun clearCache() {
        store.clearCache()
        pluginService.dropAllEngines()
        _uiState.update { it.copy(message = "Cache dos provedores limpo.") }
    }

    /** Exporta o backup sem senhas (repos, habilitados, ordem, timeout e concorrência). */
    fun backup(): String = store.backup()

    /** Restaura um backup JSON (do AOTV ou do JOTV). */
    fun restore(json: String) {
        viewModelScope.launch {
            val r = store.restore(json)
            _uiState.update {
                it.copy(
                    message = if (r.reposFail > 0)
                        "Backup restaurado: ${r.reposOk} repositório(s), ${r.reposFail} falha(s)."
                    else
                        "Backup restaurado: ${r.reposOk} repositório(s), ${r.pluginsOk} plugin(s).",
                    timeoutSec = prefs.pluginTimeoutSec,
                    concurrency = prefs.pluginConcurrency,
                    autoUpdate = prefs.pluginAutoUpdate,
                    globalEnabled = prefs.pluginGlobalEnabled,
                )
            }
        }
    }

    /** Timeout de cada execução de plugin (30–180 s, padrão 60). */
    fun setTimeoutSec(sec: Int) {
        val v = sec.coerceIn(30, 180)
        store.timeoutSec = v
        _uiState.update { it.copy(timeoutSec = v) }
    }

    /** Apaga os cookies de todos os plugins (o próximo uso recria a sessão). */
    fun clearCookies() {
        store.clearAllCookies()
        // Os runtimes em pool guardam o cookie jar na memória: sem descartá-los,
        // os cookies apagados continuariam valendo dentro do motor.
        pluginService.dropAllEngines()
        _uiState.update { it.copy(message = "Cookies dos plugins apagados") }
    }

    /**
     * Teste de rede do motor: busca o manifest do repo PELO MESMO fetch dos
     * plugins e mostra o status HTTP. É o discriminador entre "o fetch do
     * motor não funciona" (0) e "os providers devolvem vazio" (200).
     */
    fun testNetwork() {
        if (_uiState.value.networkTesting) return
        _uiState.update { it.copy(networkTesting = true, networkTest = null) }
        viewModelScope.launch {
            val result = pluginService.testNetwork()
            val text = result.fold(
                onSuccess = { status ->
                    when {
                        status in 200..299 -> "Rede do motor: OK ($status)"
                        status == 0 -> "Rede do motor: falhou (0) — sem resposta"
                        else -> "Rede do motor: HTTP $status"
                    }
                },
                onFailure = { e -> "Rede do motor: erro: ${(e.message ?: e.javaClass.simpleName).take(80)}" },
            )
            _uiState.update { it.copy(networkTesting = false, networkTest = text) }
        }
    }

    /**
     * Testa UM plugin com um título fixo (Breaking Bad 1x1 p/ séries, Clube da
     * Luta p/ filmes) e registra o resultado no diagnóstico — é o jeito de
     * descobrir, no celular, se o plugin roda e o que ele devolve.
     */
    fun testPlugin(id: String) {
        val plugin = _uiState.value.plugins.firstOrNull { it.id == id } ?: return
        if (id in _uiState.value.testing) return
        _uiState.update { it.copy(testing = it.testing + id) }
        viewModelScope.launch {
            runCatching {
                if ("tv" in plugin.supportedTypes) {
                    pluginService.getStreamsForPluginTest(plugin, 1396, "tv", 1, 1)
                } else {
                    pluginService.getStreamsForPluginTest(plugin, 550, "movie")
                }
            }
            _uiState.update { it.copy(testing = it.testing - id) }
        }
    }

    /**
     * "Testar fontes para um título": roda UM plugin com o TMDB que o usuário digitou
     * (filme ou série + temporada/episódio) e mostra quantas fontes voltaram. O teste
     * não ativa o provedor.
     */
    fun testSources(id: String, tmdbId: Int, isSeries: Boolean, season: Int?, episode: Int?) {
        val plugin = _uiState.value.plugins.firstOrNull { it.id == id } ?: return
        if (id in _uiState.value.testing) return
        _uiState.update { it.copy(testing = it.testing + id, message = null) }
        viewModelScope.launch {
            val count = runCatching {
                pluginService.getStreamsForPluginTest(
                    plugin, tmdbId, if (isSeries) "tv" else "movie", season, episode,
                ).size
            }.getOrDefault(0)
            _uiState.update {
                it.copy(
                    testing = it.testing - id,
                    message = if (count > 0) "$count fontes encontradas."
                    else "Nenhuma fonte para este título. Confira as configurações do provedor.",
                )
            }
        }
    }

    /** Abre o diálogo de configurações: roda onSettings() e mescla padrões + salvos. */
    fun openSettings(plugin: InstalledPlugin) {
        _uiState.update {
            it.copy(
                settingsPlugin = plugin, settingsDescriptors = emptyList(),
                settingsLoading = true, settingsValues = emptyMap(),
            )
        }
        viewModelScope.launch {
            val descriptors = pluginService.loadSettingDescriptors(plugin)
            val stored = store.pluginSettings(plugin.id)
            val values = mutableMapOf<String, Any?>()
            descriptors.forEach { d ->
                if (d.key.isNotBlank()) values[d.key] = stored[d.key] ?: d.defaultValue
            }
            // Valores salvos de chaves que o onSettings() atual não descreve mais.
            stored.forEach { (k, v) -> if (k !in values) values[k] = v }
            _uiState.update {
                it.copy(
                    settingsDescriptors = descriptors,
                    settingsLoading = false,
                    settingsValues = values,
                )
            }
        }
    }

    fun closeSettings() {
        _uiState.update {
            it.copy(
                settingsPlugin = null, settingsDescriptors = emptyList(),
                settingsLoading = false, settingsValues = emptyMap(),
            )
        }
    }

    fun setSettingValue(key: String, value: Any?) {
        _uiState.update { it.copy(settingsValues = it.settingsValues + (key to value)) }
    }

    fun saveSettings() {
        val plugin = _uiState.value.settingsPlugin ?: return
        store.setPluginSettings(plugin.id, _uiState.value.settingsValues)
        _uiState.update { it.copy(message = "Configurações de ${plugin.name} salvas") }
        closeSettings()
    }

    fun clearError() = _uiState.update { it.copy(error = null, message = null) }
}
