package com.aotv.app.ui.kodi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.kodi.KodiAddonStore
import com.aotv.app.data.kodi.KodiPluginRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Navegação de um addon Kodi (v1.2, Fase 2).
 *
 * Abre a raiz do addon (menu), navega nas pastas com uma pilha de
 * migalhas (cada nível re-despacha o diretório pai ao voltar, como o
 * Kodi) e, ao tocar um item que o plugin resolve, emite o evento de
 * playback — a tela entrega a URL ao player do app. O runner decide se
 * o dispatch voltou lista ou URL resolvida; esta ViewModel só empilha
 * e exibe.
 */
@HiltViewModel
class KodiBrowserViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val runner: KodiPluginRunner,
    private val addonStore: KodiAddonStore,
) : ViewModel() {

    val addonId: String = saved.get<String>("addonId").orEmpty()

    /** Um nível da pilha de navegação (p/ voltar re-despachando o pai). */
    data class Crumb(val path: String, val query: String, val title: String)

    data class UiState(
        val addonName: String = "",
        val title: String = "",
        val items: List<KodiPluginRunner.KodiItem> = emptyList(),
        val loading: Boolean = false,
        val loadingLabel: String = "Carregando…",
        val error: String? = null,
        val empty: Boolean = false,
        /** Diagnóstico do dispatch vazio (lookup do run) — p/ o print. */
        val diag: String = "",
        val canGoBack: Boolean = false,
    )

    /** URL resolvida — a tela entrega ao player do app. */
    /** v1.2.7: `diag` é o diagnóstico do dispatch (resolveu= + http:) — vai p/
     *  a tela de erro do player, p/ o Davi mandar o print quando o ExoPlayer
     *  não carregar o stream resolvido. */
    data class PlayEvent(val url: String, val title: String, val diag: String = "")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _playEvents = MutableSharedFlow<PlayEvent>(extraBufferCapacity = 1)
    val playEvents: SharedFlow<PlayEvent> = _playEvents.asSharedFlow()

    private val crumbs = ArrayDeque<Crumb>()
    private var loadJob: Job? = null

    init {
        openRoot()
    }

    /** Raiz do addon (menu) — limpa a pilha. */
    fun openRoot() {
        crumbs.clear()
        val rawName = addonStore.getAddon(addonId)?.name?.ifBlank { addonId } ?: addonId
        // O <name> do addon.xml traz tags de formatação do Kodi
        // (ex.: "[B][COLOR deepskyblue]VULGATA[/COLOR][/B]") — limpar
        // p/ exibir, como já é feito nos títulos dos itens.
        val name = KodiPluginRunner.cleanKodiText(rawName).ifBlank { addonId }
        _uiState.update { it.copy(addonName = name, title = name) }
        load("", "", pushCrumb = null, loadingLabel = "Carregando…", playTitle = "")
    }

    /** Toque num item: despacha a URL dele (lista -> navega; URL -> toca). */
    fun openItem(item: KodiPluginRunner.KodiItem) {
        if (_uiState.value.loading) return
        val target = KodiPluginRunner.parsePluginUrl(item.url, addonId) ?: run {
            _uiState.update { it.copy(error = "URL do item não reconhecida: ${item.url}") }
            return
        }
        load(
            path = target.path,
            query = target.query,
            pushCrumb = Crumb(target.path, target.query, item.title),
            loadingLabel = if (item.isFolder) "Carregando…" else "Resolvendo stream…",
            playTitle = item.title,
        )
    }

    /** Volta um nível (re-despacha o pai). False = já está na raiz. */
    fun goBack(): Boolean {
        if (crumbs.isEmpty()) return false
        crumbs.removeLast()
        val top = crumbs.lastOrNull()
        _uiState.update { it.copy(title = top?.title ?: _uiState.value.addonName) }
        load(top?.path ?: "", top?.query ?: "", pushCrumb = null,
            loadingLabel = "Carregando…", playTitle = "")
        return true
    }

    /** Repete o nível atual (após um erro). */
    fun retry() {
        val top = crumbs.lastOrNull()
        load(top?.path ?: "", top?.query ?: "", pushCrumb = null,
            loadingLabel = "Carregando…", playTitle = "")
    }

    private fun load(
        path: String,
        query: String,
        pushCrumb: Crumb?,
        loadingLabel: String,
        playTitle: String,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadingLabel = loadingLabel, error = null, empty = false, diag = "") }
            when (val res = runner.open(addonId, path, query)) {
                is KodiPluginRunner.KodiOpenResult.Listing -> {
                    if (pushCrumb != null) crumbs.addLast(pushCrumb)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            items = res.items,
                            title = pushCrumb?.title ?: it.title,
                            canGoBack = crumbs.isNotEmpty(),
                            empty = false,
                        )
                    }
                }
                is KodiPluginRunner.KodiOpenResult.Resolved -> {
                    _uiState.update { it.copy(loading = false) }
                    _playEvents.emit(PlayEvent(res.url, playTitle.ifBlank { _uiState.value.title }, res.diag))
                }
                is KodiPluginRunner.KodiOpenResult.Empty -> {
                    _uiState.update { it.copy(loading = false, items = emptyList(), empty = true, diag = res.diag) }
                }
                is KodiPluginRunner.KodiOpenResult.Failed -> {
                    _uiState.update { it.copy(loading = false, error = res.error) }
                }
            }
        }
    }
}
