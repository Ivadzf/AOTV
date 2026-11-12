package com.aotv.app.ui.kodi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.kodi.KodiAddonStore
import com.aotv.app.data.kodi.KodiPluginRunner
import com.aotv.app.data.kodi.KodiRepository
import com.aotv.app.data.kodi.KodiServiceRunner
import com.aotv.app.data.kodi.KodiSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Gerenciador de addons Kodi (v1.4, Fase 4).
 *
 * Lista os instalados, instala por ZIP (SAF) ou por URL (download
 * direto do ZIP, igual ao Kodi original), liga/desliga, remove, edita
 * os settings do addon e mostra o diagnóstico. A instalação usa o
 * [KodiAddonStore] — o addon entra intacto em filesDir/kodi/addons/.
 *
 * Settings forçados (decisão do Davi, §9): analytics_enabled e
 * automatic_updates do Vulgata continuam desligados — a UI os mostra
 * travados e o save os ignora (rede de segurança).
 */
@HiltViewModel
class KodiAddonsViewModel @Inject constructor(
    private val addonStore: KodiAddonStore,
    private val settingsStore: KodiSettingsStore,
    private val serviceRunner: KodiServiceRunner,
) : ViewModel() {

    data class UiState(
        val addons: List<KodiAddonStore.KodiAddon> = emptyList(),
        val installing: Boolean = false,
        val installingUrl: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        /** Índice de repositório resolvido, aguardando a escolha do addon. */
        val installIndex: KodiRepository.RepoIndex? = null,
        /** Filtro de busca do seletor de addon do repositório. */
        val installFilter: String = "",
        /** Progresso da instalação por repositório ("Baixando X… (2/6)"). */
        val installProgress: String? = null,
        /** Addon com o diálogo de configurações aberto (null = fechado). */
        val settingsAddon: KodiAddonStore.KodiAddon? = null,
        val settingsDefs: List<KodiSettingsStore.SettingDefinition> = emptyList(),
        /** Valores editados no diálogo (id -> valor string, formato Kodi). */
        val settingsValues: Map<String, String> = emptyMap(),
        val settingsSaving: Boolean = false,
        /** Addon com o diálogo de diagnóstico aberto. */
        val diagAddon: KodiAddonStore.KodiAddon? = null,
        val diagLoading: Boolean = false,
        val diagIssues: List<KodiAddonStore.DependencyIssue> = emptyList(),
        val diagHasService: Boolean = false,
        val diagServiceRunning: Boolean = false,
        /** Addon com o diálogo de remoção aberto. */
        val removeAddon: KodiAddonStore.KodiAddon? = null,
        /** Ação de setting em execução (RunScript) — "addonId:func". */
        val runningAction: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun displayName(addon: KodiAddonStore.KodiAddon): String =
        KodiPluginRunner.cleanKodiText(addon.name.ifBlank { addon.id })

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching { addonStore.listAddons() }.getOrDefault(emptyList())
            _uiState.update { it.copy(addons = list) }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    // ------------------------------------------------------------------
    // Instalação.
    // ------------------------------------------------------------------

    fun installZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(installing = true, message = null, error = null) }
            val res = withContext(Dispatchers.IO) { addonStore.installZip(uri) }
            _uiState.update {
                it.copy(
                    installing = false,
                    message = res.getOrNull()?.let { a -> "${displayName(a)} v${a.version} instalado." },
                    error = res.exceptionOrNull()?.message,
                )
            }
            if (res.isSuccess) refresh()
        }
    }

    /**
     * Instala por URL (v1.5, repositório): resolve a URL — ZIP direto
     * instala na hora; índice de repositório abre o seletor de addon
     * (ou instala direto se houver um único addon de vídeo).
     */
    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installingUrl = true, message = null, error = null,
                    installIndex = null, installFilter = "", installProgress = null,
                )
            }
            val res = addonStore.resolveInstallUrl(url)
            res.onSuccess { resolution ->
                when (resolution) {
                    is KodiAddonStore.UrlResolution.Installed -> {
                        val summary = resolution.summary
                        _uiState.update {
                            it.copy(
                                installingUrl = false,
                                message = summary?.format()
                                    ?: resolution.addon.let { a ->
                                        "${displayName(a)} v${a.version} instalado."
                                    },
                            )
                        }
                        refresh()
                    }
                    is KodiAddonStore.UrlResolution.NeedsChoice -> {
                        val videos = resolution.index.videoPlugins()
                        when {
                            videos.size == 1 -> installFromIndex(resolution.index, videos[0].id)
                            videos.isEmpty() -> _uiState.update {
                                it.copy(
                                    installingUrl = false,
                                    error = "O repositório não tem nenhum addon de vídeo.",
                                )
                            }
                            else -> _uiState.update {
                                it.copy(installingUrl = false, installIndex = resolution.index)
                            }
                        }
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(installingUrl = false, error = e.message) }
            }
        }
    }

    fun setInstallFilter(text: String) {
        _uiState.update { it.copy(installFilter = text) }
    }

    fun closeInstallIndex() {
        _uiState.update { it.copy(installIndex = null, installFilter = "") }
    }

    /** Instala o addon escolhido do índice + as dependências (v1.5). */
    fun installFromIndex(index: KodiRepository.RepoIndex, addonId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installingUrl = true, installProgress = "Preparando…",
                    message = null, error = null,
                )
            }
            val res = addonStore.installFromIndex(index, addonId) { progress ->
                _uiState.update { it.copy(installProgress = progress) }
            }
            _uiState.update {
                it.copy(
                    installingUrl = false, installProgress = null,
                    installIndex = null, installFilter = "",
                    message = res.getOrNull()?.format(),
                    error = res.exceptionOrNull()?.message,
                )
            }
            if (res.isSuccess) refresh()
        }
    }

    // ------------------------------------------------------------------
    // Ligar/desligar e remover.
    // ------------------------------------------------------------------

    fun toggleEnabled(addon: KodiAddonStore.KodiAddon, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { addonStore.setEnabled(addon.id, enabled) }
            // Desligar para o serviço do addon junto (ele só roda p/
            // addon habilitado — o open() do plugin runner já barra).
            if (!enabled) runCatching { serviceRunner.stopService(addon.id) }
            refresh()
        }
    }

    fun askRemove(addon: KodiAddonStore.KodiAddon) {
        _uiState.update { it.copy(removeAddon = addon) }
    }

    fun dismissRemove() {
        _uiState.update { it.copy(removeAddon = null) }
    }

    fun remove(addon: KodiAddonStore.KodiAddon) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { serviceRunner.stopService(addon.id) }
            val ok = runCatching { addonStore.removeAddon(addon.id) }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    removeAddon = null,
                    message = if (ok) "${displayName(addon)} removido." else null,
                    error = if (ok) null else "Não consegui remover ${displayName(addon)}.",
                )
            }
            refresh()
        }
    }

    // ------------------------------------------------------------------
    // Settings do addon.
    // ------------------------------------------------------------------

    fun openSettings(addon: KodiAddonStore.KodiAddon) {
        viewModelScope.launch(Dispatchers.IO) {
            val defs = runCatching { settingsStore.getDefinitions(addon.id) }.getOrDefault(emptyList())
            // Só defs com id entram no mapa de valores — action/lsep sem
            // id são botões/separadores, não valores persistidos.
            val values = defs.filter { it.id.isNotBlank() }
                .associate { d -> d.id to settingsStore.getSetting(addon.id, d.id) }
            _uiState.update { it.copy(settingsAddon = addon, settingsDefs = defs, settingsValues = values) }
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(settingsAddon = null, settingsDefs = emptyList(), settingsValues = emptyMap()) }
    }

    fun setSettingsValue(id: String, value: String) {
        _uiState.update { it.copy(settingsValues = it.settingsValues + (id to value)) }
    }

    fun isForced(addonId: String, settingId: String): Boolean =
        settingsStore.isForced(addonId, settingId)

    fun saveSettings() {
        val addon = _uiState.value.settingsAddon ?: return
        val values = _uiState.value.settingsValues
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(settingsSaving = true) }
            var ok = true
            for ((id, v) in values) {
                // Settings forçados não são gravados — a UI já os mostra
                // travados; isto é rede de segurança.
                if (settingsStore.isForced(addon.id, id)) continue
                if (!settingsStore.setSetting(addon.id, id, v)) ok = false
            }
            _uiState.update {
                it.copy(
                    settingsSaving = false,
                    settingsAddon = null, settingsDefs = emptyList(), settingsValues = emptyMap(),
                    message = if (ok) "Configurações de ${displayName(addon)} salvas." else "Algumas configurações não foram salvas.",
                )
            }
        }
    }

    /**
     * Executa um setting do tipo action ("RunScript(addon, func)") —
     * ex.: o "LIMPAR FAVORITOS" do BrazucaPlay. Usa o runScript da
     * Fase 3; o resultado vai p/ a mensagem da tela.
     */
    fun runAction(addon: KodiAddonStore.KodiAddon, action: String) {
        val match = Regex(
            """RunScript\(\s*([^,\s)]+)\s*(?:,\s*([^)\s]+)\s*)?\)""",
            RegexOption.IGNORE_CASE,
        ).find(action)
        if (match == null) {
            _uiState.update { it.copy(error = "Ação não reconhecida: $action") }
            return
        }
        val targetAddon = match.groupValues[1]
        val func = match.groupValues[2]
        viewModelScope.launch {
            _uiState.update { it.copy(runningAction = "$targetAddon:$func", message = null, error = null) }
            val res = runCatching { serviceRunner.runScript(targetAddon, func) }.getOrNull()
            _uiState.update {
                it.copy(
                    runningAction = null,
                    message = if (res?.ok == true) "Ação executada." else null,
                    error = if (res?.ok == true) null else (res?.error?.ifBlank { "A ação falhou." } ?: "A ação falhou."),
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Diagnóstico.
    // ------------------------------------------------------------------

    fun openDiagnostics(addon: KodiAddonStore.KodiAddon) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(diagAddon = addon, diagLoading = true, diagIssues = emptyList()) }
            val issues = runCatching { addonStore.checkDependencies(addon) }.getOrDefault(emptyList())
            // hasService sobe o Python na 1ª vez — por isso só aqui,
            // quando o usuário abre o diagnóstico, não na lista.
            val hasService = runCatching { serviceRunner.hasService(addon.id) }.getOrDefault(false)
            val running = serviceRunner.isRunning(addon.id)
            _uiState.update {
                it.copy(
                    diagLoading = false,
                    diagIssues = issues,
                    diagHasService = hasService,
                    diagServiceRunning = running,
                )
            }
        }
    }

    fun closeDiagnostics() {
        _uiState.update { it.copy(diagAddon = null) }
    }
}
