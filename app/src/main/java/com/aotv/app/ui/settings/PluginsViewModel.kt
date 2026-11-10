package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.plugins.InstalledPlugin
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
        val installedNow: String? = null,
        val refreshing: Set<String> = emptySet(),
        val timeoutSec: Int = 60,
        /** Plugin aberto no diálogo de configurações (null = fechado). */
        val settingsPlugin: InstalledPlugin? = null,
        val settingsDescriptors: List<PluginSettingDescriptor> = emptyList(),
        val settingsLoading: Boolean = false,
        /** Valores do diálogo (chave -> valor), já com os padrões do onSettings(). */
        val settingsValues: Map<String, Any?> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState(timeoutSec = prefs.pluginTimeoutSec))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.pluginRepos.collect { list -> _uiState.update { it.copy(repos = list) } }
        }
        viewModelScope.launch {
            prefs.installedPlugins.collect { list -> _uiState.update { it.copy(plugins = list) } }
        }
        // Refresh automático dos repos vencidos (> 6 h), igual ao Nuvio.
        viewModelScope.launch { store.refreshDueRepos() }
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
        _uiState.update { it.copy(installing = true, error = null, installedNow = null) }
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
                    installedNow = "${repo.name.ifBlank { url }} — $count plugin(s) instalado(s)",
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

    /** Timeout de cada execução de plugin (30–180 s, padrão 60). */
    fun setTimeoutSec(sec: Int) {
        val v = sec.coerceIn(30, 180)
        store.timeoutSec = v
        _uiState.update { it.copy(timeoutSec = v) }
    }

    /** Apaga os cookies de todos os plugins (o próximo uso recria a sessão). */
    fun clearCookies() {
        store.clearAllCookies()
        _uiState.update { it.copy(installedNow = "Cookies dos plugins apagados") }
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
        _uiState.update { it.copy(installedNow = "Configurações de ${plugin.name} salvas") }
        closeSettings()
    }

    fun clearError() = _uiState.update { it.copy(error = null, installedNow = null) }
}
