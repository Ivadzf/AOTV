package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.AddonEntry
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.CommunityAddon
import com.aotv.app.data.remote.StremioAddonService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Explorar addons. Lê o catálogo da comunidade, filtra e instala ou remove. */
@HiltViewModel
class AddonStoreViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
    private val stremio: StremioAddonService,
) : ViewModel() {

    enum class Show(val label: String) {
        ALL("Todos"), WORKS("Funcionam no AOTV"), INSTALLED("Instalados"), EASY("Sem configuração"),
    }

    data class UiState(
        val all: List<CommunityAddon> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
        val query: String = "",
        val show: Show = Show.ALL,
        val selectedUrl: String? = null,
        val installed: List<AddonEntry> = emptyList(),
        /** URL do addon com instalação ou remoção em andamento. Trava o segundo clique. */
        val busy: String? = null,
        val message: String? = null,
        /** URL do addon recém instalado. A ficha leva o foco a Escolher fileiras e depois solta. */
        val justInstalled: String? = null,
        /** Por base de addon, quantas fileiras dele aparecem na Início do perfil e quantas ele tem. */
        val rowCounts: Map<String, AddonsViewModel.RowCount> = emptyMap(),
    ) {
        fun entryOf(a: CommunityAddon): AddonEntry? =
            installed.firstOrNull { it.url.equals(a.url, true) || (a.id.isNotBlank() && it.id == a.id) }

        /** Fileiras do addon instalado, ou null quando ele não tem catálogo guardado. */
        fun rowsOf(a: CommunityAddon): AddonsViewModel.RowCount? =
            entryOf(a)?.let { rowCounts[com.aotv.app.data.remote.AddonLinks.baseOf(it.url)] }?.takeIf { it.all > 0 }

        fun isInstalled(a: CommunityAddon) =
            installed.any { it.url.equals(a.url, true) || (a.id.isNotBlank() && it.id == a.id) }

        val filtered: List<CommunityAddon>
            get() {
                val q = query.trim().lowercase()
                return all.filter { a ->
                    !a.adult &&
                        (q.isBlank() || "${a.name} ${a.description}".lowercase().contains(q)) &&
                        when (show) {
                            Show.ALL -> true
                            Show.WORKS -> worksHere(a)
                            Show.INSTALLED -> isInstalled(a)
                            Show.EASY -> !(a.configRequired && a.config.isEmpty())
                        }
                }
            }

        val selected: CommunityAddon? get() = all.firstOrNull { it.url == selectedUrl }
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load(false)
        viewModelScope.launch { prefs.addons.collect { l -> _ui.update { it.copy(installed = l) } } }
        viewModelScope.launch {
            prefs.homeLayoutFlow.collect { l ->
                _ui.update {
                    it.copy(
                        rowCounts = l.filter { r -> r.ref != null }.groupBy { r -> r.ref!!.base }
                            .mapValues { (_, rows) -> AddonsViewModel.RowCount(rows.count { r -> r.enabled }, rows.size) },
                    )
                }
            }
        }
    }

    fun load(force: Boolean) {
        _ui.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val list = stremio.communityCatalog(force)
            _ui.update {
                // Sugeridos (Lote 21, v1.31.0): entram no topo mesmo quando o
                // catálogo da comunidade falha — a lista é local; só a
                // instalação precisa de rede (o install lê o manifest antes).
                val remote = list ?: it.all
                val have = remote.map { a -> a.url.lowercase() }.toSet()
                val merged = SUGGESTED_ADDONS.filter { a -> a.url.lowercase() !in have } + remote
                it.copy(
                    loading = false,
                    failed = list == null,
                    all = merged,
                )
            }
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setShow(s: Show) = _ui.update { it.copy(show = s) }
    fun select(a: CommunityAddon?) = _ui.update { it.copy(selectedUrl = if (it.selectedUrl == a?.url) null else a?.url) }
    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun clearJustInstalled() = _ui.update { it.copy(justInstalled = null) }
    fun markHomeNew() { prefs.homeFocusNew = true }

    /** Lê o manifest antes de gravar. Addon que não responde não entra na lista. */
    fun install(a: CommunityAddon, url: String = a.url) {
        if (_ui.value.busy != null) return
        _ui.update { it.copy(busy = a.url, message = null) }
        viewModelScope.launch {
            val m = stremio.getManifest(url)
            if (m == null) {
                _ui.update { it.copy(busy = null, message = "${a.name} não respondeu. Tente de novo em instantes.") }
                return@launch
            }
            val real = stremio.normalizeManifestUrl(url)
            prefs.installAddon(real, a.id.ifBlank { m.id })
            _ui.update { it.copy(busy = null, message = prefs.installText(a.name, real), justInstalled = a.url) }
        }
    }

    fun remove(a: CommunityAddon) {
        prefs.removeAddon(a.url, a.id)
        _ui.update { it.copy(message = "${a.name} removido.") }
    }

    // Lote 24 (v1.34.0), item 3 — código de setup de addons: o setup
    // (quais addons instalados e ativos) vira um código p/ compartilhar com
    // outro aparelho, sem servidor (ver AddonShareCode). O código é longo —
    // cada addon custa ~60-120 caracteres (a URL do manifest domina); 6
    // caracteres não caberiam nem 1 addon. Aplicar instala cada addon pelo
    // fluxo normal (manifest antes de gravar) e restaura o ativo/desligado.
    /** Último código gerado neste aparelho (vazio = nenhum ainda). */
    val lastShareCode: String get() = prefs.addonShareCodeLast

    /** Gera o código do setup atual. Null sem nenhum addon instalado. */
    fun generateShareCode(): String? {
        val installed = _ui.value.installed
        if (installed.isEmpty()) return null
        val code = com.aotv.app.ui.lote24.AddonShareCode.encode(
            installed.map { com.aotv.app.ui.lote24.AddonShareCode.SharedAddon(it.url, it.enabled) },
        )
        prefs.addonShareCodeLast = code
        return code
    }

    /** Aplica um código colado: instala os addons da lista, um por vez. */
    fun applyShareCode(code: String) {
        if (_ui.value.busy != null) return
        val decoded = com.aotv.app.ui.lote24.AddonShareCode.decode(code)
        val addons = decoded.getOrElse {
            _ui.update { it.copy(message = it.message ?: "Código inválido") }
            return
        }
        _ui.update { it.copy(busy = "share-code", message = null) }
        viewModelScope.launch {
            var ok = 0
            var failed = 0
            val disabled = mutableListOf<String>()
            for (a in addons) {
                val m = stremio.getManifest(a.url)
                if (m == null) {
                    failed++
                    continue
                }
                val real = stremio.normalizeManifestUrl(a.url)
                prefs.installAddon(real, m.id)
                if (!a.enabled) disabled += real
                ok++
            }
            if (disabled.isNotEmpty()) {
                val off = disabled.toSet()
                prefs.addonEntries = prefs.addonEntries.map { e ->
                    if (e.url in off) e.copy(enabled = false) else e
                }
            }
            _ui.update {
                it.copy(
                    busy = null,
                    message = if (failed == 0) "Setup aplicado: $ok ${if (ok == 1) "addon instalado" else "addons instalados"}."
                        else "Setup aplicado: $ok instalados, $failed não responderam.",
                )
            }
        }
    }

    companion object {
        /** O que funciona no AOTV: fontes, legendas e catálogos (viram fileira na Início). */
        fun worksHere(a: CommunityAddon) = "stream" in a.resources || "subtitles" in a.resources || "catalog" in a.resources

        /**
         * Addons sugeridos (Lote 21, v1.31.0): lista local, no topo da loja
         * mesmo sem o catálogo da comunidade. O id fica vazio — o install lê
         * o manifest antes de gravar e usa o id de lá.
         * ERS ("Streaming Catalogs", catalog.ers.pw): catálogos dos grandes
         * streamings — as linhas aparecem no Descobrir e na Início pela
         * maquinaria existente de catálogos de addon. Testado: manifest 200.
         */
        val SUGGESTED_ADDONS: List<CommunityAddon> = listOf(
            CommunityAddon(
                rank = 0,
                url = "https://catalog.ers.pw/manifest.json",
                id = "",
                name = "Streaming Catalogs (ERS)",
                version = "",
                description = "Catálogos dos grandes streamings (Netflix, Disney+…) — as linhas aparecem no Descobrir e na Início.",
                logo = "",
                types = listOf("movie", "series"),
                resources = listOf("catalog"),
                config = emptyList(),
                configurable = false,
                configRequired = false,
                p2p = false,
                adult = false,
            ),
        )

        /** URLs dos sugeridos (p/ a seção "Sugeridos" da loja). */
        val SUGGESTED_URLS: Set<String> get() = SUGGESTED_ADDONS.map { it.url.lowercase() }.toSet()
    }
}
