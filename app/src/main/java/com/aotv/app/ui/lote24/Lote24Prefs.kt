package com.aotv.app.ui.lote24

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lote 24 (v1.34.0) — Frente A: chaves e acesso às prefs novas.
 *
 * CONTRATO COM O COORDENADOR (junção): este arquivo NÃO toca em
 * PlatformPrefs.kt. Cada pref nova tem chave com sufixo _v1, tipo, default e
 * escopo (global = do aparelho; perfil = por perfil, via sk() na junção).
 * O acesso é por interfaces pequenas; a implementação em memória abaixo é o
 * fallback até a ligação com o PlatformPrefs. Na junção, o coordenador
 * implementa cada interface lendo/gravando no PlatformPrefs (o tema
 * [home_theme_v1] mapeia p/ o `themePaletteId` existente, chave
 * `theme_palette_v1` — NÃO criar segunda fonte de verdade p/ o tema).
 */
object Lote24PrefKeys {
    /** Tema da tela inicial (String, global, default "default"). Valores: ids de [Lote24Themes]. */
    const val HOME_THEME = "home_theme_v1"
    /** Preto puro por cima do tema escolhido (Boolean, global, default false). */
    const val HOME_AMOLED = "home_amoled_v1"
    /** Rotação automática do herói (String, global, default "auto"). Valores: "auto" | "on" | "off". */
    const val HERO_AUTO_ROTATE = "hero_autorotate_v1"
    /** Prévia de cena ao focar (String, global, default "on"). Valores: "on" | "off". */
    const val HERO_PREVIEW_ON_FOCUS = "hero_preview_on_focus_v1"
    /** Pastas da Home (String JSON, por perfil, default "[]"). Ver [HomeFolder24]. */
    const val HOME_FOLDERS = "home_folders_v1"
    /** Último código de setup de addons gerado (String, global, default ""). */
    const val ADDON_SHARE_LAST_CODE = "addon_share_code_last_v1"
    /** Abas escondidas (String, por perfil, default ""). CSV de ids de [BottomTab24]. */
    const val BOTTOM_TABS_HIDDEN = "bottom_tabs_hidden_v1"
    /** Aba padrão ao abrir (String, por perfil, default "home"). Id de [BottomTab24]. */
    const val BOTTOM_TAB_DEFAULT = "bottom_tab_default_v1"
    /** Fonte do MK Channel (String, por perfil, default "up_next"). Valores: ids de [MkChannelSource]. */
    const val MK_CHANNEL_SOURCE = "mk_channel_source_v1"
    /** Peak Cinema ligado (Boolean, por perfil, default true). */
    const val PEAK_CINEMA_ENABLED = "peak_cinema_enabled_v1"
    /** Humor/gênero do Peak Cinema (String, por perfil, default "acao_adrenalina"). Id de [PeakPreset]. */
    const val PEAK_CINEMA_MOOD = "peak_cinema_mood_v1"
}

/** Tema + AMOLED puro (item 1). */
interface HomeThemeSettings {
    val themeFlow: StateFlow<String>
    fun setTheme(id: String)
    val amoledFlow: StateFlow<Boolean>
    fun setAmoled(on: Boolean)
}

/** Herói auto-rotativo + prévia ao focar (item 2). */
interface HeroSettings {
    /** "auto" | "on" | "off". */
    val autoRotateFlow: StateFlow<String>
    fun setAutoRotate(value: String)
    val previewOnFocusFlow: StateFlow<Boolean>
    fun setPreviewOnFocus(on: Boolean)
}

/** Pastas da Home (item 3, primeira metade). */
interface HomeFolderSettings {
    val foldersFlow: StateFlow<List<HomeFolder24>>
    fun addFolder(name: String): HomeFolder24?
    fun renameFolder(id: String, name: String)
    fun deleteFolder(id: String)
    fun setFolderRows(id: String, rowKeys: List<String>)
    fun moveFolder(id: String, delta: Int)
}

/** Abas da navegação inferior + aba padrão (item 4). */
interface BottomTabsSettings {
    val hiddenTabsFlow: StateFlow<Set<String>>
    fun setTabHidden(tabId: String, hidden: Boolean)
    val defaultTabFlow: StateFlow<String>
    fun setDefaultTab(tabId: String)
}

/** MK Channel (item 5) e Peak Cinema (item 6). */
interface HomeExperienceSettings {
    val mkChannelSourceFlow: StateFlow<String>
    fun setMkChannelSource(sourceId: String)
    val peakCinemaEnabledFlow: StateFlow<Boolean>
    fun setPeakCinemaEnabled(on: Boolean)
    val peakCinemaMoodFlow: StateFlow<String>
    fun setPeakCinemaMood(presetId: String)
}

/**
 * Implementação em memória de todas as interfaces acima (fallback até a
 * junção com o PlatformPrefs). Não persiste nada: é só p/ a UI e os testes
 * locais não dependerem do SharedPreferences.
 */
class InMemoryLote24Settings :
    HomeThemeSettings,
    HeroSettings,
    HomeFolderSettings,
    BottomTabsSettings,
    HomeExperienceSettings {

    private val _theme = MutableStateFlow(Lote24Themes.DEFAULT_ID)
    override val themeFlow: StateFlow<String> = _theme.asStateFlow()
    override fun setTheme(id: String) {
        _theme.value = Lote24Themes.fromId(id).id
    }

    private val _amoled = MutableStateFlow(false)
    override val amoledFlow: StateFlow<Boolean> = _amoled.asStateFlow()
    override fun setAmoled(on: Boolean) {
        _amoled.value = on
    }

    private val _autoRotate = MutableStateFlow("auto")
    override val autoRotateFlow: StateFlow<String> = _autoRotate.asStateFlow()
    override fun setAutoRotate(value: String) {
        _autoRotate.value = if (value in setOf("auto", "on", "off")) value else "auto"
    }

    private val _previewOnFocus = MutableStateFlow(true)
    override val previewOnFocusFlow: StateFlow<Boolean> = _previewOnFocus.asStateFlow()
    override fun setPreviewOnFocus(on: Boolean) {
        _previewOnFocus.value = on
    }

    private val _folders = MutableStateFlow(emptyList<HomeFolder24>())
    override val foldersFlow: StateFlow<List<HomeFolder24>> = _folders.asStateFlow()
    override fun addFolder(name: String): HomeFolder24? {
        val folder = HomeFolder24.new(name) ?: return null
        _folders.value = _folders.value + folder
        return folder
    }

    override fun renameFolder(id: String, name: String) {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return
        _folders.value = _folders.value.map { if (it.id == id) it.copy(name = clean) else it }
    }

    override fun deleteFolder(id: String) {
        _folders.value = _folders.value.filter { it.id != id }
    }

    override fun setFolderRows(id: String, rowKeys: List<String>) {
        _folders.value = _folders.value.map {
            if (it.id == id) it.copy(rowKeys = rowKeys.distinct().take(HomeFolder24.MAX_ROWS)) else it
        }
    }

    override fun moveFolder(id: String, delta: Int) {
        val list = _folders.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val moved = list.removeAt(i)
        list.add(j, moved)
        _folders.value = list
    }

    private val _hiddenTabs = MutableStateFlow(emptySet<String>())
    override val hiddenTabsFlow: StateFlow<Set<String>> = _hiddenTabs.asStateFlow()
    override fun setTabHidden(tabId: String, hidden: Boolean) {
        if (BottomTab24.fromId(tabId) == null) return
        val next = if (hidden) _hiddenTabs.value + tabId else _hiddenTabs.value - tabId
        _hiddenTabs.value = BottomTabs24.sanitizeHidden(next)
    }

    private val _defaultTab = MutableStateFlow(BottomTab24.HOME.id)
    override val defaultTabFlow: StateFlow<String> = _defaultTab.asStateFlow()
    override fun setDefaultTab(tabId: String) {
        val tab = BottomTab24.fromId(tabId) ?: return
        _defaultTab.value = tab.id
        if (tab.id in _hiddenTabs.value) _hiddenTabs.value = _hiddenTabs.value - tab.id
    }

    private val _mkSource = MutableStateFlow(MkChannelSource.UP_NEXT.id)
    override val mkChannelSourceFlow: StateFlow<String> = _mkSource.asStateFlow()
    override fun setMkChannelSource(sourceId: String) {
        _mkSource.value = MkChannelSource.fromId(sourceId)?.id ?: MkChannelSource.UP_NEXT.id
    }

    private val _peakEnabled = MutableStateFlow(true)
    override val peakCinemaEnabledFlow: StateFlow<Boolean> = _peakEnabled.asStateFlow()
    override fun setPeakCinemaEnabled(on: Boolean) {
        _peakEnabled.value = on
    }

    private val _peakMood = MutableStateFlow(PEAK_CINEMA_PRESETS.first().id)
    override val peakCinemaMoodFlow: StateFlow<String> = _peakMood.asStateFlow()
    override fun setPeakCinemaMood(presetId: String) {
        _peakMood.value = PeakPreset.fromId(presetId)?.id ?: PEAK_CINEMA_PRESETS.first().id
    }
}
