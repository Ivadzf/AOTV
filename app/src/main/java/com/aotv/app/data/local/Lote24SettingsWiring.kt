// ─── data/local/Lote24SettingsWiring.kt (Lote 24, v1.34.0 — junção) ────────
// Implementações das interfaces das frentes do Lote 24 sobre o
// PlatformPrefs — a única fonte de verdade das prefs. As interfaces vivem
// em ui/lote24/Lote24Prefs.kt (Frente A) e data/ratings/EpisodeRatings.kt
// (Frente C, item 12); as implementações em memória de lá são só fallback
// e NÃO são usadas pelo app depois desta ligação.
//
// Pastas e abas escondidas são valores DERIVADOS (JSON/CSV guardados como
// String no PlatformPrefs): a classe espelha o fluxo cru do PlatformPrefs
// num MutableStateFlow próprio, com um escopo interno (o singleton vive
// enquanto o app vive). A troca de perfil recarrega o fluxo cru em
// refreshLayout() e o espelho acompanha — sem estado parado no perfil
// antigo. As mutações escrevem na pref E atualizam o espelho na hora.
package com.aotv.app.data.local

import com.aotv.app.data.ratings.EPISODE_RATING_MODES
import com.aotv.app.data.ratings.EpisodeRatingSettings
import com.aotv.app.data.ratings.MODE_AVERAGE
import com.aotv.app.ui.lote24.BottomTab24
import com.aotv.app.ui.lote24.BottomTabs24
import com.aotv.app.ui.lote24.BottomTabsSettings
import com.aotv.app.ui.lote24.HeroSettings
import com.aotv.app.ui.lote24.HomeExperienceSettings
import com.aotv.app.ui.lote24.HomeFolder24
import com.aotv.app.ui.lote24.HomeFolderSettings
import com.aotv.app.ui.lote24.HomeThemeSettings
import com.aotv.app.ui.lote24.Lote24Themes
import com.aotv.app.ui.lote24.MkChannelSource
import com.aotv.app.ui.lote24.PEAK_CINEMA_PRESETS
import com.aotv.app.ui.lote24.PeakPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformPrefsLote24Settings @Inject constructor(
    private val prefs: PlatformPrefs,
) : HomeThemeSettings,
    HeroSettings,
    HomeFolderSettings,
    BottomTabsSettings,
    HomeExperienceSettings {

    // Escopo interno do singleton: espelha os fluxos crus do PlatformPrefs.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ── Item 1: tema + AMOLED ──────────────────────────────────────────────
    // O tema NÃO tem segunda fonte de verdade: lê/escreve o themePaletteId
    // existente (chave theme_palette_v1). O AMOLED é pref global própria.
    override val themeFlow: StateFlow<String> = prefs.themePalette
    override fun setTheme(id: String) {
        prefs.themePaletteId = Lote24Themes.fromId(id).id
    }

    override val amoledFlow: StateFlow<Boolean> = prefs.homeAmoledFlow
    override fun setAmoled(on: Boolean) {
        prefs.homeAmoled = on
    }

    // ── Item 2: herói auto-rotativo + prévia ao focar ──────────────────────
    override val autoRotateFlow: StateFlow<String> = prefs.heroAutoRotateFlow
    override fun setAutoRotate(value: String) {
        prefs.heroAutoRotate = value
    }

    override val previewOnFocusFlow: StateFlow<Boolean> = prefs.heroPreviewOnFocusFlow
    override fun setPreviewOnFocus(on: Boolean) {
        prefs.heroPreviewOnFocus = on
    }

    // ── Item 3: pastas da Home ─────────────────────────────────────────────
    private val _folders = MutableStateFlow(HomeFolder24.listFromJson(prefs.homeFolders))
    override val foldersFlow: StateFlow<List<HomeFolder24>> = _folders.asStateFlow()

    init {
        scope.launch {
            prefs.homeFoldersFlow.collect { raw ->
                _folders.value = HomeFolder24.listFromJson(raw)
            }
        }
        scope.launch {
            prefs.bottomTabsHiddenFlow.collect { csv ->
                _hiddenTabs.value = BottomTabs24.hiddenFromCsv(csv)
            }
        }
    }

    private fun persistFolders(next: List<HomeFolder24>) {
        val clean = next.take(HomeFolder24.MAX_FOLDERS)
        _folders.value = clean
        prefs.homeFolders = HomeFolder24.listToJson(clean)
    }

    override fun addFolder(name: String): HomeFolder24? {
        val folder = HomeFolder24.new(name) ?: return null
        persistFolders(_folders.value + folder)
        return folder
    }

    override fun renameFolder(id: String, name: String) {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return
        persistFolders(_folders.value.map { if (it.id == id) it.copy(name = clean) else it })
    }

    override fun deleteFolder(id: String) {
        persistFolders(_folders.value.filter { it.id != id })
    }

    override fun setFolderRows(id: String, rowKeys: List<String>) {
        persistFolders(_folders.value.map {
            if (it.id == id) it.copy(rowKeys = rowKeys.distinct().take(HomeFolder24.MAX_ROWS)) else it
        })
    }

    override fun moveFolder(id: String, delta: Int) {
        val list = _folders.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val moved = list.removeAt(i)
        list.add(j, moved)
        persistFolders(list)
    }

    // ── Item 4: abas da pílula + aba padrão ────────────────────────────────
    private val _hiddenTabs = MutableStateFlow(BottomTabs24.hiddenFromCsv(prefs.bottomTabsHidden))
    override val hiddenTabsFlow: StateFlow<Set<String>> = _hiddenTabs.asStateFlow()

    override fun setTabHidden(tabId: String, hidden: Boolean) {
        if (BottomTab24.fromId(tabId) == null) return
        val next = BottomTabs24.sanitizeHidden(
            if (hidden) _hiddenTabs.value + tabId else _hiddenTabs.value - tabId,
        )
        _hiddenTabs.value = next
        prefs.bottomTabsHidden = BottomTabs24.hiddenToCsv(next)
    }

    override val defaultTabFlow: StateFlow<String> = prefs.bottomTabDefaultFlow
    override fun setDefaultTab(tabId: String) {
        val tab = BottomTab24.fromId(tabId) ?: return
        prefs.bottomTabDefault = tab.id
        // Aba padrão nunca fica escondida (mesma regra do fallback em memória).
        if (tab.id in _hiddenTabs.value) setTabHidden(tab.id, false)
    }

    // ── Itens 5 e 6: MK Channel e Peak Cinema ───────────────────────────────
    override val mkChannelSourceFlow: StateFlow<String> = prefs.mkChannelSourceFlow
    override fun setMkChannelSource(sourceId: String) {
        prefs.mkChannelSource = MkChannelSource.fromId(sourceId)?.id ?: MkChannelSource.UP_NEXT.id
    }

    override val peakCinemaEnabledFlow: StateFlow<Boolean> = prefs.peakCinemaEnabledFlow
    override fun setPeakCinemaEnabled(on: Boolean) {
        prefs.peakCinemaEnabled = on
    }

    override val peakCinemaMoodFlow: StateFlow<String> = prefs.peakCinemaMoodFlow
    override fun setPeakCinemaMood(presetId: String) {
        prefs.peakCinemaMood = PeakPreset.fromId(presetId)?.id ?: PEAK_CINEMA_PRESETS.first().id
    }
}

/**
 * Item 12 — prefs da nota dos episódios sobre o PlatformPrefs (ver
 * data/ratings/EpisodeRatings.kt). Modo por perfil; chave do OMDb global,
 * do usuário, sem valor embutido.
 */
@Singleton
class PlatformPrefsEpisodeRatingSettings @Inject constructor(
    private val prefs: PlatformPrefs,
) : EpisodeRatingSettings {

    override val modeFlow: StateFlow<String> = prefs.episodeRatingModeFlow
    override val omdbKeyFlow: StateFlow<String> = prefs.omdbApiKeyFlow

    override fun setMode(v: String) {
        prefs.episodeRatingMode = if (v in EPISODE_RATING_MODES) v else MODE_AVERAGE
    }

    override fun setOmdbKey(v: String) {
        prefs.omdbApiKey = v
    }
}
