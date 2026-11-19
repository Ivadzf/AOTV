package com.aotv.app.ui.lote24

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lote 24 (v1.34.0), item 4 — esconder/mostrar abas + aba padrão ao abrir.
 *
 * As rotas abaixo espelham [com.aotv.app.ui.navigation.MoviesNavigation]
 * (sealed class MoviesRoute, privada): se uma rota mudar lá, mudar aqui
 * também. A lógica é pura (sem Compose): a pílula do MoviesNavigation
 * filtra os itens por [visibleTabs] e o startDestination usa
 * [resolveDefaultRoute]. A UI de Configurações (junção) liga os toggles ao
 * [BottomTabsSettings]; pelo menos 1 aba fica visível sempre.
 */
enum class BottomTab24(val id: String, val label: String, val route: String) {
    HOME("home", "Início", "movies_home"),
    LIVE("live", "Ao vivo", "movies_live"),
    SEARCH("search", "Busca", "movies_search"),
    LIBRARY("library", "Biblioteca", "movies_minha_lista"),
    SPORTS("sports", "Esportes", "movies_sports_tab"),
    // Junção: a rota do Perfil é a tela de perfis (MoviesRoute.Profiles),
    // p/ onde o avatar da pílula já navega — não a das Configurações.
    PROFILE("profile", "Perfil", "movies_profiles");

    companion object {
        fun fromId(id: String): BottomTab24? = entries.firstOrNull { it.id == id }
    }
}

object BottomTabs24 {
    /** Todas as abas, na ordem da pílula. */
    val all: List<BottomTab24> = BottomTab24.entries.toList()

    /**
     * Escondidas saneadas: nunca esconde todas — se o conjunto cobre todas
     * as abas, a Início volta a aparecer. Ids desconhecidos são descartados.
     */
    fun sanitizeHidden(hidden: Set<String>): Set<String> {
        val known = hidden.filter { BottomTab24.fromId(it) != null }.toSet()
        if (known.size >= all.size) return known - BottomTab24.HOME.id
        return known
    }

    /** Abas visíveis, na ordem da pílula. */
    fun visibleTabs(hidden: Set<String>): List<BottomTab24> {
        val clean = sanitizeHidden(hidden)
        return all.filter { it.id !in clean }
    }

    /** Rota da aba padrão ao abrir; cai p/ a Início quando a padrão está escondida. */
    fun resolveDefaultRoute(defaultTabId: String, hidden: Set<String>): String {
        val visible = visibleTabs(hidden)
        val wanted = BottomTab24.fromId(defaultTabId)
        if (wanted != null && wanted in visible) return wanted.route
        return BottomTab24.HOME.route
    }

    /** Aba padrão efetiva (o id), já saneada. */
    fun resolveDefaultTab(defaultTabId: String, hidden: Set<String>): BottomTab24 {
        val visible = visibleTabs(hidden)
        return BottomTab24.fromId(defaultTabId)?.takeIf { it in visible } ?: BottomTab24.HOME
    }

    /** CSV p/ guardar na pref [Lote24PrefKeys.BOTTOM_TABS_HIDDEN]. */
    fun hiddenToCsv(hidden: Set<String>): String = sanitizeHidden(hidden).sorted().joinToString(",")

    /** CSV de volta p/ conjunto. */
    fun hiddenFromCsv(csv: String): Set<String> =
        sanitizeHidden(csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
}

/**
 * ViewModel das abas (junção): lê as prefs do PlatformPrefs e expõe o
 * conjunto de abas escondidas + a aba padrão p/ a pílula do
 * MoviesNavigation. O startDestination é resolvido no MoviesNavigation
 * com [BottomTabs24.resolveDefaultRoute] (cai p/ a Início se a padrão
 * estiver escondida).
 */
@HiltViewModel
class BottomTabsViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {

    val hiddenTabs: StateFlow<Set<String>> = prefs.bottomTabsHiddenFlow
        .map { BottomTabs24.hiddenFromCsv(it) }
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            BottomTabs24.hiddenFromCsv(prefs.bottomTabsHidden),
        )

    val defaultTab: StateFlow<String> = prefs.bottomTabDefaultFlow
}
