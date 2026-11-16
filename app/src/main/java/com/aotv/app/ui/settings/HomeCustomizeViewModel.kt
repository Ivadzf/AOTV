package com.aotv.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.HomeLayout
import com.aotv.app.data.local.HomeRowPref
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.SystemRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Personalizar Início. Tudo é local e aplica na hora (a Início observa o mesmo fluxo). */
@HiltViewModel
class HomeCustomizeViewModel @Inject constructor(
    private val prefs: PlatformPrefs,
) : ViewModel() {

    /** Uma linha da lista. [blocked] explica por que a fileira ligada não aparece agora. */
    data class Row(
        val pref: HomeRowPref,
        val source: String,
        val typeLabel: String,
        val blocked: String?,
        val canUp: Boolean,
        val canDown: Boolean,
    )

    data class HeroOption(val key: String, val label: String)

    /** Um catálogo na lista "Catálogos do destaque" (Bloco 3, Lote 15). */
    data class HeroCatalogOption(
        val key: String,
        val label: String,
        val selected: Boolean,
        val canUp: Boolean,
        val canDown: Boolean,
    )

    data class UiState(
        val rows: List<Row> = emptyList(),
        val total: Int = 0,
        val enabledCount: Int = 0,
        val catalogOn: Boolean = true,
        val heroSource: String = "",
        val heroOptions: List<HeroOption> = emptyList(),
        val heroTrailer: Boolean = true,
        val onlyOn: Boolean = false,
        val addonsWithCatalogs: Int = 0,
        val kids: Boolean = false,
        /** Layout da Página Inicial (Bloco 3, Lote 15). */
        val heroOn: Boolean = true,
        val typeSuffix: Boolean = false,
        val hideUnreleased: Boolean = false,
        /** null = catálogos do destaque no automático. */
        val heroCatalogs: Set<String>? = null,
        val heroCatalogOptions: List<HeroCatalogOption> = emptyList(),
    )

    /** Prefs do Layout da Página Inicial, juntas p/ um combine só. */
    private data class LayoutPrefs(
        val heroOn: Boolean,
        val heroCatalogs: Set<String>?,
        val hideUnreleased: Boolean,
        val typeSuffix: Boolean,
    )

    private val layoutPrefsFlow: StateFlow<LayoutPrefs> = combine(
        prefs.heroOnFlow, prefs.heroCatalogsFlow, prefs.hideUnreleasedFlow, prefs.heroTypeSuffixFlow,
    ) { heroOn, heroCatalogs, hideUnreleased, typeSuffix ->
        LayoutPrefs(heroOn, heroCatalogs, hideUnreleased, typeSuffix)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        LayoutPrefs(prefs.heroOn, prefs.heroCatalogs, prefs.hideUnreleased, prefs.heroTypeSuffix),
    )

    /**
     * Destaque pode vir de qualquer fileira com pôster grande. Trailers, Gêneros, Novos
     * episódios e coleções (cartões de pasta, não de título) não servem.
     */
    private fun isHeroable(r: HomeRowPref): Boolean =
        r.enabled && r.key != SystemRow.TRAILERS.key && r.key != SystemRow.GENRES.key &&
            r.key != SystemRow.NEW_EPISODES.key && r.collectionId == null

    private val onlyOn = MutableStateFlow(false)
    private val heroTrailer = MutableStateFlow(prefs.heroTrailer)

    private val baseState: StateFlow<UiState> = combine(
        combine(prefs.homeLayoutFlow, prefs.catalogOnFlow, prefs.heroSourceFlow, ::Triple),
        prefs.addons, onlyOn, heroTrailer,
    ) { (layout, catalogOn, hero), addons, only, trailer ->
        val addonOn = addons.associate { com.aotv.app.data.remote.AddonLinks.baseOf(it.url) to it.enabled }
        val visible: (HomeRowPref) -> Boolean = { !only || it.enabled }
        val cols = prefs.collectionsFlow.value.associateBy { HomeLayout.COL_PREFIX + it.id }
        val pinned = cols.filterValues { it.pinToTop }.keys
        val movable: (HomeRowPref) -> Boolean = { visible(it) && it.key !in pinned }
        val shown = layout.filter(visible)
        val rows = shown.map { r ->
            val sys = r.system
            val col = cols[r.key]
            Row(
                pref = r,
                source = when {
                    col != null -> "Coleção"
                    sys == null -> r.ref?.addon?.ifBlank { "Addon" } ?: "Addon"
                    sys.catalog -> "AOTV"
                    else -> "Seu progresso"
                },
                typeLabel = when {
                    col != null -> if (col.pinToTop) "Fixada no topo" else com.aotv.app.ui.collections.countOf(col.folders.size, "pasta", "pastas")
                    else -> when (r.ref?.type) { "movie" -> "Filmes"; "series" -> "Séries"; else -> "" }
                },
                blocked = when {
                    !r.enabled -> null
                    col != null && col.folders.isEmpty() -> "Coleção sem pastas"
                    sys?.catalog == true && !catalogOn -> "Catálogo AOTV desligado"
                    sys == null && col == null && addonOn[r.ref?.base] == false -> "Addon desligado"
                    else -> null
                },
                canUp = r.key !in pinned && HomeLayout.canMove(layout, r.key, -1, movable),
                canDown = r.key !in pinned && HomeLayout.canMove(layout, r.key, 1, movable),
            )
        }
        val heroable = layout.filter { isHeroable(it) }
        UiState(
            rows = rows,
            total = layout.size,
            enabledCount = layout.count { it.enabled },
            catalogOn = catalogOn,
            heroSource = hero.takeIf { h -> h.isBlank() || heroable.any { it.key == h } }.orEmpty(),
            heroOptions = listOf(HeroOption("", "Automático")) + heroable.map { HeroOption(it.key, it.displayTitle) },
            heroTrailer = trailer,
            onlyOn = only,
            addonsWithCatalogs = layout.mapNotNull { it.ref?.base }.distinct().size,
            kids = prefs.isKids,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /** Estado final: o base + as prefs do Layout da Página Inicial. */
    val state: StateFlow<UiState> = combine(baseState, layoutPrefsFlow) { base, lp ->
        // Catálogos do destaque, na ordem das fileiras (a mesma do seletor de
        // fonte do destaque). Sem escolha explícita (null), todos os que servem
        // p/ o destaque estão dentro. Subir/descer aqui move a fileira na Início
        // — a ordem do destaque É a ordem das fileiras.
        val opts = base.heroOptions.drop(1)
        val heroCatalogOptions = opts.mapIndexed { i, o ->
            HeroCatalogOption(
                key = o.key,
                label = o.label,
                selected = lp.heroCatalogs?.contains(o.key) ?: true,
                canUp = i > 0,
                canDown = i < opts.size - 1,
            )
        }
        base.copy(
            heroOn = lp.heroOn,
            typeSuffix = lp.typeSuffix,
            hideUnreleased = lp.hideUnreleased,
            heroCatalogs = lp.heroCatalogs,
            heroCatalogOptions = heroCatalogOptions,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    fun toggle(key: String) {
        val r = prefs.homeLayout.firstOrNull { it.key == key } ?: return
        prefs.setHomeRowEnabled(key, !r.enabled)
    }

    fun rename(key: String, title: String) = prefs.renameHomeRow(key, title)

    fun move(key: String, delta: Int) {
        val only = onlyOn.value
        val pinned = pinnedKeys()
        if (key in pinned) return
        // Coleção fixada fica no topo, então ninguém troca de lugar com ela.
        prefs.moveHomeRow(key, delta) { (!only || it.enabled) && it.key !in pinned }
    }

    private fun pinnedKeys(): Set<String> =
        prefs.collectionsFlow.value.filter { it.pinToTop }.mapTo(HashSet()) { HomeLayout.COL_PREFIX + it.id }

    fun reset() = prefs.resetHomeLayout()

    fun setCatalogOn(on: Boolean) { prefs.catalogOn = on }

    fun setHeroSource(key: String) { prefs.heroSource = key }

    fun setHeroTrailer(on: Boolean) { prefs.heroTrailer = on; heroTrailer.value = on }

    /** Layout da Página Inicial (Bloco 3, Lote 15). */
    fun setHeroOn(on: Boolean) { prefs.heroOn = on }

    fun setTypeSuffix(on: Boolean) { prefs.heroTypeSuffix = on }

    fun setHideUnreleased(on: Boolean) { prefs.hideUnreleased = on }

    /**
     * Liga/desliga um catálogo no destaque. Na primeira mudança, a escolha
     * explícita parte de todos os que servem p/ o destaque (o padrão).
     */
    fun setHeroCatalog(key: String, on: Boolean) {
        val heroable = prefs.homeLayout.filter { isHeroable(it) }.map { it.key }
        val current = prefs.heroCatalogs ?: heroable.toSet()
        prefs.heroCatalogs = if (on) current + key else current - key
    }

    /** Catálogos do destaque de volta ao automático. */
    fun resetHeroCatalogs() { prefs.heroCatalogs = null }

    /**
     * Sobe/desce um catálogo do destaque. Move a fileira na Início — a ordem do
     * destaque é a ordem das fileiras, e só troca com outro catálogo do destaque.
     */
    fun moveHeroCatalog(key: String, delta: Int) {
        val heroable = prefs.homeLayout.filter { isHeroable(it) }.map { it.key }.toSet()
        prefs.moveHomeRow(key, delta) { it.key in heroable }
    }

    fun setOnlyOn(on: Boolean) { onlyOn.value = on }
}
