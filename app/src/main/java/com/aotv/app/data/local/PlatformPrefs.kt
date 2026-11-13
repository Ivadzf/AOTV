package com.aotv.app.data.local

import android.content.Context
import com.aotv.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlatformMode { AUTO, TV, PHONE }

/** Fonte que dá o som de um título ([sig] reconhece a fonte, o link muda) e o atraso em segundos. */
data class ExtAudioPick(val sig: String, val delaySec: Double, val at: Long)

/**
 * Um addon Stremio instalado. A ordem da lista é a prioridade (o 1º é tentado primeiro).
 * [id] é o id do manifest, serve para reconhecer o mesmo addon com URL configurada diferente.
 */
data class AddonEntry(val url: String, val enabled: Boolean = true, val id: String = "")

/** Catálogo de addon marcado para virar fileira na Início. */
data class HomeCatalogRef(val base: String, val type: String, val id: String, val name: String, val addon: String) {
    val key get() = "$base|$type|$id"
}

/** Perfil de quem assiste. [color] é o índice em [PROFILE_COLORS]. */
data class Profile(val id: String, val name: String, val color: Int = 0, val kids: Boolean = false)

/** Cores dos perfis, nas cores do AOTV (turquesa e vermelho escuro). */
val PROFILE_COLORS = listOf(0xFF00C2B8, 0xFF9E1B1B, 0xFF3DDC84, 0xFFF5A524, 0xFFA970FF, 0xFFFF6FB5)
const val MAX_PROFILES = 5
const val MAIN_PROFILE = "main"

/**
 * Chave de preferência de um perfil. O principal usa a chave antiga, então quem já usava o app
 * não perde nada. Os outros ganham o prefixo "p.<id>." (igual ao fullKey do webOS).
 */
fun scopedKey(key: String, profileId: String): String =
    if (profileId == MAIN_PROFILE) key else "p.$profileId.$key"

/** Preferências que são de cada perfil. O resto (addons, modo, chave TMDB, aberturas) é do aparelho. */
internal val SCOPED_KEYS = listOf(
    "recent_searches", "ext_audio_v1", "home_catalogs_v1", "sub_size", "sub_bg",
    "pref_audio", "pref_max_quality", "pref_hero_trailer", "trakt_v1",
    "home_layout_v1", "home_hero_v1", "pref_subtitle_on", "pref_sub_lang", "collections_v1",
    "pref_poster_size", "pref_poster_radius", "pref_home_density", "pref_poster_badges",
    "pref_hero_style", "pref_dynamic_bg", "pref_section_underline",
    "pref_detail_cinematic", "pref_detail_poster_transition", "pref_detail_icon_actions",
    "pref_detail_tabs", "pref_detail_episode_style",
    "pref_stream_search", "pref_stream_badges_size", "pref_stream_badges_codec",
    "pref_stream_badges_lang", "pref_stream_badges_pos", "pref_stream_logo",
    "pref_stream_pinned",
    "pref_player_buttons", "pref_player_top_actions", "pref_player_tap_seek",
    "pref_player_vol_boost", "pref_player_vol_boost_level", "pref_player_mini",
    "pref_player_auto_next", "pref_player_auto_next_delay",
    "pref_player_auto_switch", "pref_player_source_timeout",
    "pref_player_animeskip_client_id",
)

/**
 * Ordem padrão dos botões do player (ids usados pelo construtor de chips em
 * MoviePlayerScreen.kt). Lista vazia guardada = padrão (o construtor usa esta
 * lista quando não há nada salvo).
 */
val DEFAULT_PLAYER_BUTTONS = listOf(
    "subs", "audio", "sources", "episodes", "prev", "next", "screen", "speed", "pin", "skip",
)

/** Botões do topo do player (voltar sempre aparece; estes são os opcionais). */
val DEFAULT_PLAYER_TOP_ACTIONS = listOf("cast", "rotate", "lock")

@Singleton
class PlatformPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        // Chave TMDB do app. Vem da gradle property TMDB_API_KEY (~/.gradle/gradle.properties,
        // fora do controle de versão, veja o README). Sem ela fica em branco e cada pessoa
        // informa a própria chave em Configurações.
        private val DEFAULT_TMDB_KEY = BuildConfig.TMDB_API_KEY

        /** Chave do formato novo (JSON com ordem + ativo). */
        private const val KEY_ADDONS_V2 = "stremio_addons_v2"

        /** Histórico de busca (uma consulta por linha, mais recente primeiro). */
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT_SEARCHES = 10

        /** Aberturas aprendidas por série (JSON tmdbId → [inicioMs, fimMs]). */
        private const val KEY_INTRO_MARKS = "intro_marks_v1"

        /** Catálogos de addon que viram fileira na Início (JSON, na ordem em que foram marcados). */
        private const val KEY_HOME_CATALOGS = "home_catalogs_v1"

        /** Vínculo perfil local -> índice do perfil do Nuvio (JSON id -> index). */
        private const val KEY_NUVIO_PROFILE_LINKS = "nuvio_profile_links_v1"

        /** Repositórios de plugins do Nuvio instalados (JSON array). */
        private const val KEY_PLUGIN_REPOS = "plugin_repos_v1"

        /** Plugins do Nuvio instalados (JSON array). O código JS fica em arquivo, não aqui. */
        private const val KEY_PLUGINS = "plugins_v1"

        /** Configurações dos plugins (JSON id -> {chave: valor}, do onSettings()). */
        private const val KEY_PLUGIN_SETTINGS = "plugin_settings_v1"

        /** Timeout de cada execução de plugin, em segundos (30–180, padrão 60). */
        private const val KEY_PLUGIN_TIMEOUT = "plugin_timeout_sec"

        /** Último resultado de cada plugin (JSON id -> PluginLastRun, p/ diagnóstico). */
        private const val KEY_PLUGIN_LAST_RUN = "plugin_last_run_v1"

        /** Id da paleta de cores ativa (ver [com.aotv.app.ui.theme.AotvPalette]). */
        private const val KEY_THEME_PALETTE = "theme_palette_v1"

        /** Paleta padrão (Padrão, id "default"). */
        private const val DEFAULT_THEME_PALETTE = "default"
    }

    private val prefs = context.getSharedPreferences("aotv_prefs", Context.MODE_PRIVATE)

    // ── Perfis ────────────────────────────────────────────────────────────────
    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _profileId = MutableStateFlow(
        (prefs.getString("profile", MAIN_PROFILE) ?: MAIN_PROFILE).takeIf { id -> _profiles.value.any { it.id == id } }
            ?: _profiles.value.first().id,
    )
    /** Perfil ativo. Quem lê dado de perfil observa isso para trocar junto. */
    val profileId: StateFlow<String> = _profileId.asStateFlow()

    val profile: Profile get() = _profiles.value.firstOrNull { it.id == _profileId.value } ?: _profiles.value.first()
    val isKids: Boolean get() = profile.kids

    /** Chave já no escopo do perfil ativo. */
    private fun sk(key: String) = scopedKey(key, _profileId.value)

    private fun readProfiles(): List<Profile> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("profiles", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Profile(o.getString("id"), o.optString("name", "Perfil"), o.optInt("color", 0), o.optBoolean("kids", false))
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(Profile(MAIN_PROFILE, "Principal")) }

    private fun saveProfiles(list: List<Profile>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("kids", it.kids)) }
        prefs.edit().putString("profiles", arr.toString()).apply()
        _profiles.value = list
    }

    /** Cria um perfil. null quando já tem o máximo. */
    fun addProfile(name: String, kids: Boolean): Profile? {
        val list = _profiles.value
        if (list.size >= MAX_PROFILES) return null
        val p = Profile(
            "p" + java.lang.Long.toString(System.currentTimeMillis(), 36),
            name.trim().take(20).ifBlank { "Perfil" }, list.size % PROFILE_COLORS.size, kids,
        )
        saveProfiles(list + p)
        return p
    }

    fun updateProfile(p: Profile) =
        saveProfiles(_profiles.value.map { if (it.id == p.id) p.copy(name = p.name.trim().take(20).ifBlank { "Perfil" }) else it })

    /**
     * Apaga o perfil e as preferências dele. Nunca apaga o último. O que fica no banco (lista,
     * histórico) quem apaga é o LibraryRepository. Devolve false quando não pôde remover.
     */
    fun removeProfile(id: String): Boolean {
        val list = _profiles.value
        if (list.size <= 1 || list.none { it.id == id }) return false
        val ed = prefs.edit()
        SCOPED_KEYS.forEach { ed.remove(scopedKey(it, id)) }
        ed.apply()
        removeNuvioProfileLink(id)
        saveProfiles(list.filterNot { it.id == id })
        if (_profileId.value == id) selectProfile(_profiles.value.first().id)
        return true
    }

    fun selectProfile(id: String) {
        if (_profiles.value.none { it.id == id }) return
        prefs.edit().putString("profile", id).apply()
        _profileId.value = id
        refreshLayout()
    }

    // ── Vínculo perfil local -> perfil do Nuvio ───────────────────────────────
    // Quando a conta Nuvio conecta, os perfis dela viram perfis locais com o mesmo nome
    // e cada um guarda aqui o índice do perfil do Nuvio, para a importação (addons, lista,
    // progresso, debrid, coleções) usar o perfil certo do Nuvio.

    /** Mapa perfil local -> índice do perfil do Nuvio. */
    var nuvioProfileLinks: Map<String, Int>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(KEY_NUVIO_PROFILE_LINKS, null) ?: "{}")
            o.keys().asSequence().associateWith { o.optInt(it, -1) }.filterValues { it >= 0 }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.forEach { (k, v) -> o.put(k, v) }
            prefs.edit().putString(KEY_NUVIO_PROFILE_LINKS, o.toString()).apply()
        }

    /** Índice do perfil do Nuvio vinculado ao perfil local. null = sem vínculo. */
    fun nuvioProfileIndexFor(profileId: String): Int? = nuvioProfileLinks[profileId]

    /** Vincula um perfil local a um perfil do Nuvio. */
    fun setNuvioProfileLink(profileId: String, nuvioIndex: Int) {
        nuvioProfileLinks = nuvioProfileLinks + (profileId to nuvioIndex)
    }

    /** Desfaz o vínculo de um perfil local. */
    fun removeNuvioProfileLink(profileId: String) {
        if (profileId in nuvioProfileLinks) nuvioProfileLinks = nuvioProfileLinks - profileId
    }

    // ── Reprodução (por perfil) ───────────────────────────────────────────────

    /** Áudio preferido das fontes. "any", "dub" ou "leg". */
    var audioPref: String
        get() = prefs.getString(sk("pref_audio"), "any") ?: "any"
        set(value) { prefs.edit().putString(sk("pref_audio"), value).apply() }

    /** Qualidade máxima (0 sem limite, 2160, 1080 ou 720). */
    var maxQuality: Int
        get() = prefs.getInt(sk("pref_max_quality"), 0)
        set(value) { prefs.edit().putInt(sk("pref_max_quality"), value).apply() }

    /** Trailer mudo no destaque da Início. */
    var heroTrailer: Boolean
        get() = prefs.getBoolean(sk("pref_hero_trailer"), true)
        set(value) { prefs.edit().putBoolean(sk("pref_hero_trailer"), value).apply() }

    /** Legenda ligada ou não, lembrada entre episódios e sessões do mesmo perfil. */
    var subtitleOn: Boolean
        get() = prefs.getBoolean(sk("pref_subtitle_on"), false)
        set(value) { prefs.edit().putBoolean(sk("pref_subtitle_on"), value).apply() }

    /** Idioma em que a legenda liga sozinha, código do OpenSubtitles (ver [SubLangs]). */
    var subLang: String
        get() = prefs.getString(sk("pref_sub_lang"), null)?.takeIf { SubLangs.of(it) != null } ?: SubLangs.DEFAULT
        set(value) { prefs.edit().putString(sk("pref_sub_lang"), value).apply() }

    /** Fontes na ordem da preferência do perfil (estável, igual ao sortSources do webOS). */
    fun sortStreams(list: List<com.aotv.app.data.remote.StremioStream>) =
        com.aotv.app.data.remote.sortByPrefs(list, audioPref, maxQuality, { it.label }, { it.qualityRank })

    /** Sessão do Trakt do perfil (JSON cru). Vazio sem login. */
    var traktSession: String
        get() = prefs.getString(sk("trakt_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("trakt_v1"), value).apply() }

    var platformMode: PlatformMode
        get() = PlatformMode.valueOf(prefs.getString("platform_mode", PlatformMode.AUTO.name)!!)
        set(value) { prefs.edit().putString("platform_mode", value.name).apply() }

    /**
     * Já passou pela tela de nome + plataforma na primeira abertura. Só pergunta uma vez.
     * Sem a chave ainda (app nunca gravou nada), o padrão olha se já existe QUALQUER
     * preferência salva. Instalação zerada = prefs vazio = pergunta. Quem já usa o app
     * (ou restaurou backup) já tem alguma chave salva, então não pergunta de novo.
     */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", prefs.all.isNotEmpty())
        set(value) { prefs.edit().putBoolean("onboarded", value).apply() }

    // ── Tema (do aparelho) ───────────────────────────────────────────────────
    // Paleta de cores do app (Fase A, Frente 3). É do aparelho, não do perfil:
    // troca o app inteiro e vale para todo mundo que usa o aparelho. O id
    // guardado aqui é o [com.aotv.app.ui.theme.AotvPalette.id]; id desconhecido
    // volta ao Padrão na leitura (ver AotvPalette.fromId).

    private val _themePalette = MutableStateFlow(
        prefs.getString(KEY_THEME_PALETTE, DEFAULT_THEME_PALETTE) ?: DEFAULT_THEME_PALETTE,
    )
    /** Id da paleta ativa, que avisa quem observa. */
    val themePalette: StateFlow<String> = _themePalette.asStateFlow()

    /** Id da paleta ativa. Gravar aqui já avisa quem observa. */
    var themePaletteId: String
        get() = _themePalette.value
        set(value) { prefs.edit().putString(KEY_THEME_PALETTE, value).apply(); _themePalette.value = value }

    // ── Exibição da Início (por perfil, Fase B) ─────────────────────────────
    // Pôster pequeno por padrão (o Davi achou a Início grande demais). Um fluxo
    // só carrega as 7 prefs juntas; cada setter reescreve o conjunto e avisa.
    // Troca de perfil passa por refreshLayout() e recarrega as dele.
    private fun readHomeDisplay(): HomeDisplayPrefs {
        val size = runCatching { PosterSize.valueOf(prefs.getString(sk("pref_poster_size"), null) ?: "") }
            .getOrDefault(PosterSize.SMALL)
        val density = runCatching { HomeDensity.valueOf(prefs.getString(sk("pref_home_density"), null) ?: "") }
            .getOrDefault(HomeDensity.COMPACT)
        val hero = runCatching { HeroStyle.valueOf(prefs.getString(sk("pref_hero_style"), null) ?: "") }
            .getOrDefault(HeroStyle.FULL_BLEED)
        return HomeDisplayPrefs(
            posterSize = size,
            posterRadiusDp = prefs.getInt(sk("pref_poster_radius"), 14).coerceIn(4, 24),
            density = density,
            showPosterBadges = prefs.getBoolean(sk("pref_poster_badges"), false),
            heroStyle = hero,
            dynamicBackground = prefs.getBoolean(sk("pref_dynamic_bg"), false),
            sectionUnderline = prefs.getBoolean(sk("pref_section_underline"), true),
        )
    }

    private val _homeDisplay = MutableStateFlow(readHomeDisplay())
    /** Exibição da Início do perfil ativo, que avisa quem observa. */
    val homeDisplayFlow: StateFlow<HomeDisplayPrefs> = _homeDisplay.asStateFlow()

    /** Exibição da Início do perfil ativo. Gravar aqui já avisa quem observa. */
    var homeDisplay: HomeDisplayPrefs
        get() = _homeDisplay.value
        set(value) {
            prefs.edit()
                .putString(sk("pref_poster_size"), value.posterSize.name)
                .putInt(sk("pref_poster_radius"), value.posterRadiusDp)
                .putString(sk("pref_home_density"), value.density.name)
                .putBoolean(sk("pref_poster_badges"), value.showPosterBadges)
                .putString(sk("pref_hero_style"), value.heroStyle.name)
                .putBoolean(sk("pref_dynamic_bg"), value.dynamicBackground)
                .putBoolean(sk("pref_section_underline"), value.sectionUnderline)
                .apply()
            _homeDisplay.value = value
        }

    /** Reescreve a exibição da Início a partir da atual. */
    fun updateHomeDisplay(transform: (HomeDisplayPrefs) -> HomeDisplayPrefs) {
        homeDisplay = transform(homeDisplay)
    }

    // ── Página de detalhes (por perfil, Fase B) ─────────────────────────────
    // Fundo cinematográfico, transição do pôster, ações em ícones, layout de
    // abas e estilo do card de episódio. Cada pref tem um fluxo que avisa quem
    // observa (o detalhe lê daqui e aplica na hora, mesmo voltando das
    // Configurações); o var é o valor guardado, no mesmo padrão do heroTrailer.
    // Troca de perfil passa por refreshLayout() e recarrega as dele.

    private val _detailCinematic = MutableStateFlow(prefs.getBoolean(sk("pref_detail_cinematic"), false))
    /** Fundo cinematográfico ligado. Desligado, fica o backdrop no topo. */
    val detailCinematicFlow: StateFlow<Boolean> = _detailCinematic.asStateFlow()
    var detailCinematic: Boolean
        get() = _detailCinematic.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_cinematic"), value).apply(); _detailCinematic.value = value }

    private val _detailPosterTransition = MutableStateFlow(prefs.getBoolean(sk("pref_detail_poster_transition"), true))
    /** Transição do pôster ao abrir os detalhes. */
    val detailPosterTransitionFlow: StateFlow<Boolean> = _detailPosterTransition.asStateFlow()
    var detailPosterTransition: Boolean
        get() = _detailPosterTransition.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_poster_transition"), value).apply(); _detailPosterTransition.value = value }

    private val _detailIconActions = MutableStateFlow(prefs.getBoolean(sk("pref_detail_icon_actions"), true))
    /** Ações em ícones com rótulo abaixo do Reproduzir (em vez dos botões em vidro). */
    val detailIconActionsFlow: StateFlow<Boolean> = _detailIconActions.asStateFlow()
    var detailIconActions: Boolean
        get() = _detailIconActions.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_icon_actions"), value).apply(); _detailIconActions.value = value }

    private val _detailTabs = MutableStateFlow(prefs.getBoolean(sk("pref_detail_tabs"), false))
    /** Seções do detalhe em abas. Desligado, fica a rolagem única atual. */
    val detailTabsFlow: StateFlow<Boolean> = _detailTabs.asStateFlow()
    var detailTabs: Boolean
        get() = _detailTabs.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_tabs"), value).apply(); _detailTabs.value = value }

    private val _detailEpisodeStyle = MutableStateFlow(prefs.getString(sk("pref_detail_episode_style"), "horizontal") ?: "horizontal")
    /** Estilo do card de episódio: "horizontal" (com arte, o atual) ou "stacked" (foco em texto). */
    val detailEpisodeStyleFlow: StateFlow<String> = _detailEpisodeStyle.asStateFlow()
    var detailEpisodeStyle: String
        get() = _detailEpisodeStyle.value
        set(value) { prefs.edit().putString(sk("pref_detail_episode_style"), value).apply(); _detailEpisodeStyle.value = value }

    // ── Streams: folha de fontes (por perfil, Fase C) ───────────────────────
    // Pesquisa, emblemas, logo do addon e fontes fixadas. Cada pref tem um fluxo
    // que avisa quem observa (a folha lê daqui e aplica na hora); o var é o valor
    // guardado, no mesmo padrão das prefs da Fase B. Troca de perfil passa por
    // refreshLayout() e recarrega as dele.

    private val _streamSearch = MutableStateFlow(prefs.getBoolean(sk("pref_stream_search"), true))
    /** Barra de pesquisa na folha de fontes. */
    val streamSearchFlow: StateFlow<Boolean> = _streamSearch.asStateFlow()
    var streamSearch: Boolean
        get() = _streamSearch.value
        set(value) { prefs.edit().putBoolean(sk("pref_stream_search"), value).apply(); _streamSearch.value = value }

    private val _streamBadgesSize = MutableStateFlow(prefs.getBoolean(sk("pref_stream_badges_size"), true))
    /** Emblema de tamanho nas fontes (ex.: "2,4 GB", quando o provider informa). */
    val streamBadgesSizeFlow: StateFlow<Boolean> = _streamBadgesSize.asStateFlow()
    var streamBadgesSize: Boolean
        get() = _streamBadgesSize.value
        set(value) { prefs.edit().putBoolean(sk("pref_stream_badges_size"), value).apply(); _streamBadgesSize.value = value }

    private val _streamBadgesCodec = MutableStateFlow(prefs.getBoolean(sk("pref_stream_badges_codec"), true))
    /** Emblema de codec nas fontes (ex.: "HEVC", "AV1", quando o provider informa). */
    val streamBadgesCodecFlow: StateFlow<Boolean> = _streamBadgesCodec.asStateFlow()
    var streamBadgesCodec: Boolean
        get() = _streamBadgesCodec.value
        set(value) { prefs.edit().putBoolean(sk("pref_stream_badges_codec"), value).apply(); _streamBadgesCodec.value = value }

    private val _streamBadgesLang = MutableStateFlow(prefs.getBoolean(sk("pref_stream_badges_lang"), true))
    /** Emblema de idioma nas fontes ("Dublado" / "Legendado"). */
    val streamBadgesLangFlow: StateFlow<Boolean> = _streamBadgesLang.asStateFlow()
    var streamBadgesLang: Boolean
        get() = _streamBadgesLang.value
        set(value) { prefs.edit().putBoolean(sk("pref_stream_badges_lang"), value).apply(); _streamBadgesLang.value = value }

    private val _streamBadgesPos = MutableStateFlow(prefs.getString(sk("pref_stream_badges_pos"), "stats") ?: "stats")
    /** Posição dos emblemas: "stats" (no canto superior direito, o atual) ou "pills" (na linha de pills). */
    val streamBadgesPosFlow: StateFlow<String> = _streamBadgesPos.asStateFlow()
    var streamBadgesPos: String
        get() = _streamBadgesPos.value
        set(value) { prefs.edit().putString(sk("pref_stream_badges_pos"), value).apply(); _streamBadgesPos.value = value }

    private val _streamLogo = MutableStateFlow(prefs.getBoolean(sk("pref_stream_logo"), true))
    /** Logo do addon ao lado do nome da fonte (quando o addon tem logo no manifest). */
    val streamLogoFlow: StateFlow<Boolean> = _streamLogo.asStateFlow()
    var streamLogo: Boolean
        get() = _streamLogo.value
        set(value) { prefs.edit().putBoolean(sk("pref_stream_logo"), value).apply(); _streamLogo.value = value }

    private fun readStringList(key: String): List<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(sk(key), "[]"))
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun writeStringList(key: String, value: List<String>) {
        val arr = org.json.JSONArray()
        value.forEach { arr.put(it) }
        prefs.edit().putString(sk(key), arr.toString()).apply()
    }

    private val _streamPinned = MutableStateFlow(readStringList("pref_stream_pinned"))
    /** Addons fixados no topo da folha de fontes, na ordem (nomes de addon). Vazio = nenhum fixado. */
    val streamPinnedFlow: StateFlow<List<String>> = _streamPinned.asStateFlow()
    var streamPinned: List<String>
        get() = _streamPinned.value
        set(value) { writeStringList("pref_stream_pinned", value); _streamPinned.value = value }

    // ── Player (por perfil, Fase C) ──────────────────────────────────────────
    // Botões configuráveis, tocar-p/-buscar, Volume Boost, mini player e
    // "A seguir" automático. Mesmo padrão de fluxo + var das prefs da Fase B.

    private val _playerButtons = MutableStateFlow(readStringList("pref_player_buttons"))
    /** Botões do player na ordem escolhida (ids de [DEFAULT_PLAYER_BUTTONS]). Vazio = padrão. */
    val playerButtonsFlow: StateFlow<List<String>> = _playerButtons.asStateFlow()
    var playerButtons: List<String>
        get() = _playerButtons.value
        set(value) { writeStringList("pref_player_buttons", value); _playerButtons.value = value }

    private val _playerTopActions = MutableStateFlow(readStringList("pref_player_top_actions"))
    /** Botões do topo do player (ids de [DEFAULT_PLAYER_TOP_ACTIONS]). Vazio = padrão. "Voltar" sempre aparece. */
    val playerTopActionsFlow: StateFlow<List<String>> = _playerTopActions.asStateFlow()
    var playerTopActions: List<String>
        get() = _playerTopActions.value
        set(value) { writeStringList("pref_player_top_actions", value); _playerTopActions.value = value }

    private val _playerTapSeek = MutableStateFlow(prefs.getBoolean(sk("pref_player_tap_seek"), true))
    /** Tocar na barra de progresso pula para aquele ponto (além de arrastar). */
    val playerTapSeekFlow: StateFlow<Boolean> = _playerTapSeek.asStateFlow()
    var playerTapSeek: Boolean
        get() = _playerTapSeek.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_tap_seek"), value).apply(); _playerTapSeek.value = value }

    private val _playerVolBoost = MutableStateFlow(prefs.getBoolean(sk("pref_player_vol_boost"), false))
    /** Volume Boost: deixa o volume passar de 100% (via LoudnessEnhancer). */
    val playerVolBoostFlow: StateFlow<Boolean> = _playerVolBoost.asStateFlow()
    var playerVolBoost: Boolean
        get() = _playerVolBoost.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_vol_boost"), value).apply(); _playerVolBoost.value = value }

    private val _playerVolBoostLevel = MutableStateFlow(prefs.getFloat(sk("pref_player_vol_boost_level"), 1.5f))
    /** Nível do Volume Boost como multiplicador (1,0 = 100% até 2,0 = 200%). */
    val playerVolBoostLevelFlow: StateFlow<Float> = _playerVolBoostLevel.asStateFlow()
    var playerVolBoostLevel: Float
        get() = _playerVolBoostLevel.value
        set(value) { prefs.edit().putFloat(sk("pref_player_vol_boost_level"), value).apply(); _playerVolBoostLevel.value = value }

    private val _playerMini = MutableStateFlow(prefs.getBoolean(sk("pref_player_mini"), true))
    /** Mini player: permite minimizar o player para uma janela flutuante e navegar no app. */
    val playerMiniFlow: StateFlow<Boolean> = _playerMini.asStateFlow()
    var playerMini: Boolean
        get() = _playerMini.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_mini"), value).apply(); _playerMini.value = value }

    private val _playerAutoNext = MutableStateFlow(prefs.getBoolean(sk("pref_player_auto_next"), true))
    /** "A seguir" automático: ao fim do episódio, avança sozinho para o próximo. */
    val playerAutoNextFlow: StateFlow<Boolean> = _playerAutoNext.asStateFlow()
    var playerAutoNext: Boolean
        get() = _playerAutoNext.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_auto_next"), value).apply(); _playerAutoNext.value = value }

    private val _playerAutoNextDelay = MutableStateFlow(prefs.getInt(sk("pref_player_auto_next_delay"), 10))
    /** Contagem regressiva do "A seguir" automático, em segundos. */
    val playerAutoNextDelayFlow: StateFlow<Int> = _playerAutoNextDelay.asStateFlow()
    var playerAutoNextDelay: Int
        get() = _playerAutoNextDelay.value
        set(value) { prefs.edit().putInt(sk("pref_player_auto_next_delay"), value).apply(); _playerAutoNextDelay.value = value }

    private val _playerAutoSwitch = MutableStateFlow(prefs.getBoolean(sk("pref_player_auto_switch"), true))
    /** Troca automática de fonte (v1.8.1): com o toggle ligado, o watchdog troca de fonte quando a atual não inicia/trava; desligado, o player espera sem trocar. */
    val playerAutoSwitchFlow: StateFlow<Boolean> = _playerAutoSwitch.asStateFlow()
    var playerAutoSwitch: Boolean
        get() = _playerAutoSwitch.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_auto_switch"), value).apply(); _playerAutoSwitch.value = value }

    private val _playerSourceTimeout = MutableStateFlow(prefs.getInt(sk("pref_player_source_timeout"), 12))
    /** Tempo que o watchdog espera antes de trocar de fonte, em segundos (8/12/20/30/45/60). */
    val playerSourceTimeoutFlow: StateFlow<Int> = _playerSourceTimeout.asStateFlow()
    var playerSourceTimeout: Int
        get() = _playerSourceTimeout.value
        set(value) { prefs.edit().putInt(sk("pref_player_source_timeout"), value).apply(); _playerSourceTimeout.value = value }

    private val _playerAnimeSkipClientId = MutableStateFlow(prefs.getString(sk("pref_player_animeskip_client_id"), "") ?: "")
    /** Client ID do Anime-Skip (v1.8.1), digitado pelo usuário em Configurações. "" = Anime-Skip desligado. NUNCA embutir um ID no código. */
    val playerAnimeSkipClientIdFlow: StateFlow<String> = _playerAnimeSkipClientId.asStateFlow()
    var playerAnimeSkipClientId: String
        get() = _playerAnimeSkipClientId.value
        set(value) { prefs.edit().putString(sk("pref_player_animeskip_client_id"), value).apply(); _playerAnimeSkipClientId.value = value }

    // ── PIN dos pais ─────────────────────────────────────────────────────────
    // Vale pro aparelho inteiro (não é por perfil): trava sair do infantil e abrir
    // Addons/TMDB/Aparelho nas Configurações enquanto o perfil infantil está ativo.
    // Hash simples (SHA-256 com um sal fixo do app), suficiente pra um gate de uso doméstico,
    // não é cofre contra ataque sério. "" = nenhum PIN definido ainda (gate desligado).
    var parentalPinHash: String
        get() = prefs.getString("parental_pin_hash", "") ?: ""
        set(value) { prefs.edit().putString("parental_pin_hash", value).apply() }

    val hasParentalPin: Boolean get() = parentalPinHash.isNotBlank()

    fun hashPin(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("aotv-parental-pin:$pin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setParentalPin(pin: String) { parentalPinHash = hashPin(pin) }
    fun clearParentalPin() { parentalPinHash = "" }
    fun checkParentalPin(pin: String): Boolean = hasParentalPin && parentalPinHash == hashPin(pin)

    var customTmdbApiKey: String
        get() = prefs.getString("tmdb_api_key", "").orEmpty().trim().takeUnless { it == DEFAULT_TMDB_KEY }.orEmpty()
        set(value) {
            val key = value.trim()
            prefs.edit().apply {
                if (key.isBlank() || key == DEFAULT_TMDB_KEY) remove("tmdb_api_key")
                else putString("tmdb_api_key", key)
            }.apply()
        }

    var tmdbApiKey: String
        get() = customTmdbApiKey.ifBlank { DEFAULT_TMDB_KEY }
        set(value) { customTmdbApiKey = value }

    /**
     * Addons Stremio (uma URL de manifest por linha).
     * Ausente (nunca configurado) → padrões. Campo vazio ("") → usuário OPTOU POR NENHUM.
     * Mantido por compatibilidade; a fonte de verdade agora é [addonEntries] (com ordem e ativo).
     */
    var stremioAddons: String
        get() = addonEntries.joinToString("\n") { it.url }
        set(value) {
            val urls = value.split("\n", ",").map { it.trim() }.filter { it.startsWith("http") }
            // Preserva o estado "ativo" dos que já existiam.
            val prev = addonEntries.associateBy { it.url }
            addonEntries = urls.map { AddonEntry(it, prev[it]?.enabled ?: true) }
        }

    /** URLs dos addons ATIVOS, na ordem de prioridade definida pelo usuário. */
    fun stremioAddonList(): List<String> = addonEntries.filter { it.enabled }.map { it.url }

    /** Últimas buscas do usuário, mais recente primeiro. Alimenta a tela de busca vazia. */
    var recentSearches: List<String>
        get() = prefs.getString(sk(KEY_RECENT_SEARCHES), "").orEmpty()
            .split("\n").map { it.trim() }.filter { it.isNotBlank() }
        set(value) {
            val clean = value.map { it.trim() }.filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }.take(MAX_RECENT_SEARCHES)
            prefs.edit().putString(sk(KEY_RECENT_SEARCHES), clean.joinToString("\n")).apply()
        }

    /** Empurra uma consulta para o topo do histórico (sem duplicar). */
    fun pushRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        recentSearches = listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }
    }

    // ── Abertura das séries ───────────────────────────────────────────────────
    // Não existe API pública de timestamp de intro fora do AniSkip (só anime). Então o app
    // aprende sozinho: quando o usuário arrasta a barra pra frente no começo do episódio,
    // esse pulo vira o marcador da série e nos próximos episódios vira o botão "Pular abertura".

    /** Abertura aprendida da série (inicioMs, fimMs). null enquanto o usuário nunca pulou. */
    fun introMarker(tmdbId: Int): Pair<Long, Long>? {
        if (tmdbId <= 0) return null
        val raw = prefs.getString(KEY_INTRO_MARKS, null) ?: return null
        val arr = runCatching { org.json.JSONObject(raw).optJSONArray(tmdbId.toString()) }.getOrNull() ?: return null
        if (arr.length() < 2) return null
        val start = arr.optLong(0, -1L)
        val end = arr.optLong(1, -1L)
        return if (start >= 0 && end > start) start to end else null
    }

    /** Grava a abertura da série (sobrescreve a anterior, o último pulo é o mais confiável). */
    fun saveIntroMarker(tmdbId: Int, startMs: Long, endMs: Long) {
        if (tmdbId <= 0 || endMs <= startMs) return
        val root = runCatching { org.json.JSONObject(prefs.getString(KEY_INTRO_MARKS, null) ?: "{}") }
            .getOrDefault(org.json.JSONObject())
        root.put(tmdbId.toString(), org.json.JSONArray().put(startMs).put(endMs))
        prefs.edit().putString(KEY_INTRO_MARKS, root.toString()).apply()
    }

    // ── Legenda e som de outra fonte (player) ─────────────────────────────────

    /** Tamanho da legenda (0,8 pequena, 1 média, 1,25 grande, 1,5 muito grande). */
    var subSize: Float
        get() = prefs.getFloat(sk("sub_size"), 1f)
        set(value) { prefs.edit().putFloat(sk("sub_size"), value).apply() }

    /** Fundo escuro atrás da legenda. */
    var subBg: Boolean
        get() = prefs.getBoolean(sk("sub_bg"), true)
        set(value) { prefs.edit().putBoolean(sk("sub_bg"), value).apply() }

    /** Escolhas de som de outra fonte por título, chave "TIPO:tmdbId". */
    var extAudioPicks: Map<String, ExtAudioPick>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(sk("ext_audio_v1"), null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                ExtAudioPick(v.getString("s"), v.optDouble("d", 0.0), v.optLong("t", 0L))
            }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.forEach { (k, p) -> o.put(k, org.json.JSONObject().put("s", p.sig).put("d", p.delaySec).put("t", p.at)) }
            prefs.edit().putString(sk("ext_audio_v1"), o.toString()).apply()
        }

    /**
     * Lista completa de addons (ordem = prioridade, 1º é tentado primeiro) com o flag de ativo.
     * Guardada em JSON. Migra automaticamente do formato antigo (uma URL por linha).
     */
    var addonEntries: List<AddonEntry>
        get() {
            val raw = prefs.getString(KEY_ADDONS_V2, null)
            if (raw != null) return decodeEntries(raw)
            // Migração do formato legado (texto com uma URL por linha). Instalação nova
            // entra sem addon nenhum — a pessoa escolhe e instala pela Loja.
            val legacy = prefs.getString("stremio_addons", null)
            return (legacy?.split("\n", ",") ?: emptyList()).map { it.trim() }
                .filter { it.startsWith("http") }
                .map { AddonEntry(it, true) }
        }
        set(value) {
            val clean = value.distinctBy { it.url }
            prefs.edit().putString(KEY_ADDONS_V2, encodeEntries(clean)).apply()
            _addons.value = clean
            refreshLayout()
        }

    private val _addons = MutableStateFlow(addonEntries)
    /** Lista de addons que avisa quem observa. A loja instala e o gerenciador já vê. */
    val addons: StateFlow<List<AddonEntry>> = _addons.asStateFlow()

    /** Posição do addon pela URL ou pelo id do manifest. -1 se não está instalado. */
    fun addonIndex(url: String, id: String = ""): Int = addonEntries.indexOfFirst {
        it.url.equals(url, true) || (id.isNotBlank() && it.id == id)
    }

    /**
     * Instala no fim da fila, ou troca a URL no lugar quando o mesmo addon já existe.
     * Os catálogos de addon instalado daqui em diante entram ligados na Início.
     */
    fun installAddon(url: String, id: String = "") {
        val list = addonEntries.toMutableList()
        val i = addonIndex(url, id)
        if (i >= 0) list[i] = AddonEntry(url, true, id.ifBlank { list[i].id })
        else list += AddonEntry(url, true, id)
        autoOnBases = autoOnBases + baseOf(url)
        addonEntries = list
    }

    /** Remove o addon. As fileiras dele saem da Início de todos os perfis (o normalize tira). */
    fun removeAddon(url: String, id: String = "") {
        val i = addonIndex(url, id)
        if (i < 0) return
        val gone = addonEntries[i]
        autoOnBases = autoOnBases - baseOf(gone.url)
        addonEntries = addonEntries.filterIndexed { idx, _ -> idx != i }
    }

    /** Mensagem de addon instalado, a mesma na loja, no link e na importação das contas. */
    fun installText(name: String, url: String): String {
        val rows = homeLayout.filter { it.ref?.base == baseOf(url) }
        return HomeLayout.installText(name, rows.count { it.enabled }, rows.size)
    }

    /** A Minha Início deve rolar até as fileiras novas do addon recém instalado. */
    var homeFocusNew: Boolean
        get() = prefs.getBoolean("home_focus_new", false)
        set(value) { prefs.edit().putBoolean("home_focus_new", value).apply() }

    private fun baseOf(url: String) = url.trim().removeSuffix("/manifest.json").trimEnd('/')

    /**
     * Addon que mudou de URL (token novo, host novo). As fileiras dele na Início de todos os
     * perfis passam para a base nova, com o liga e desliga e o nome que cada perfil escolheu.
     */
    fun rebaseAddon(oldUrl: String, newUrl: String) {
        val old = baseOf(oldUrl)
        val new = baseOf(newUrl)
        if (old == new) return
        val ed = prefs.edit()
        _profiles.value.forEach { p ->
            val k = scopedKey("home_layout_v1", p.id)
            HomeLayout.decode(prefs.getString(k, null))?.let { list ->
                ed.putString(k, HomeLayout.encode(list.map { r ->
                    val ref = r.ref
                    if (ref != null && ref.base == old) ref.copy(base = new).let { nr -> r.copy(key = nr.key, ref = nr) } else r
                }))
            }
            val hk = scopedKey("home_hero_v1", p.id)
            val hero = prefs.getString(hk, "").orEmpty()
            if (hero.startsWith("$old|")) ed.putString(hk, new + hero.removePrefix(old))
        }
        ed.apply()
        if (old in autoOnBases) autoOnBases = autoOnBases - old + new
        refreshLayout()
    }

    // ── Plugins do Nuvio (scrapers JS) ────────────────────────────────────────
    // Mesmo padrão dos addons Stremio: metadados em JSON no SharedPreferences, com
    // StateFlow para a tela de Plugins observar. O código JS de cada plugin fica em
    // filesDir/plugin-code/<id>.js e os cookies em filesDir/plugin-cookies/<id>.json
    // (ver PluginStore); aqui só vão metadados, nunca código nem cookie.

    private fun readPluginRepos(): List<com.aotv.app.data.plugins.PluginRepo> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(KEY_PLUGIN_REPOS, null) ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            com.aotv.app.data.plugins.PluginRepo(
                url = o.optString("url"), name = o.optString("name"),
                version = o.optString("version"), updatedAt = o.optLong("updatedAt", 0L),
                enabled = o.optBoolean("enabled", true),
            )
        }
    }.getOrDefault(emptyList())

    private val _pluginRepos = MutableStateFlow(readPluginRepos())
    val pluginRepos: StateFlow<List<com.aotv.app.data.plugins.PluginRepo>> = _pluginRepos.asStateFlow()

    /** Repositórios instalados (ordem = ordem de instalação). */
    var pluginRepoList: List<com.aotv.app.data.plugins.PluginRepo>
        get() = _pluginRepos.value
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach { r ->
                arr.put(org.json.JSONObject().put("url", r.url).put("name", r.name)
                    .put("version", r.version).put("updatedAt", r.updatedAt)
                    .put("enabled", r.enabled))
            }
            prefs.edit().putString(KEY_PLUGIN_REPOS, arr.toString()).apply()
            _pluginRepos.value = value
        }

    fun upsertPluginRepo(repo: com.aotv.app.data.plugins.PluginRepo) {
        val list = pluginRepoList.toMutableList()
        val i = list.indexOfFirst { it.url.equals(repo.url, true) }
        if (i >= 0) list[i] = repo else list += repo
        pluginRepoList = list
    }

    fun removePluginRepo(url: String) {
        pluginRepoList = pluginRepoList.filterNot { it.url.equals(url, true) }
    }

    /** Pausa/retoma um repositório (os plugins dele param de rodar nas fontes). */
    fun setPluginRepoEnabled(url: String, enabled: Boolean) {
        pluginRepoList = pluginRepoList.map { if (it.url.equals(url, true)) it.copy(enabled = enabled) else it }
    }

    /** Liga/desliga todos os plugins de um repositório de uma vez. */
    fun setPluginsOfRepoEnabled(repoUrl: String, enabled: Boolean) {
        installedPluginList = installedPluginList.map {
            if (it.repoUrl.equals(repoUrl, true)) it.copy(enabled = enabled) else it
        }
    }

    private fun readPlugins(): List<com.aotv.app.data.plugins.InstalledPlugin> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(KEY_PLUGINS, null) ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            com.aotv.app.data.plugins.InstalledPlugin(
                id = o.optString("id"), name = o.optString("name"),
                repoUrl = o.optString("repoUrl"), version = o.optString("version"),
                enabled = o.optBoolean("enabled", true), sha256 = o.optString("sha256"),
                updatedAt = o.optLong("updatedAt", 0L),
                supportedTypes = o.optJSONArray("supportedTypes")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                }.orEmpty().ifEmpty { listOf("movie", "tv") },
            )
        }
    }.getOrDefault(emptyList())

    private val _installedPlugins = MutableStateFlow(readPlugins())
    /** Plugins instalados, que avisam quem observa. A tela de detalhe lê daqui. */
    val installedPlugins: StateFlow<List<com.aotv.app.data.plugins.InstalledPlugin>> =
        _installedPlugins.asStateFlow()

    /** Plugins instalados (ordem = ordem de instalação). */
    var installedPluginList: List<com.aotv.app.data.plugins.InstalledPlugin>
        get() = _installedPlugins.value
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach { p ->
                val types = org.json.JSONArray()
                p.supportedTypes.forEach { types.put(it) }
                arr.put(org.json.JSONObject().put("id", p.id).put("name", p.name)
                    .put("repoUrl", p.repoUrl).put("version", p.version)
                    .put("enabled", p.enabled).put("sha256", p.sha256)
                    .put("updatedAt", p.updatedAt).put("supportedTypes", types))
            }
            prefs.edit().putString(KEY_PLUGINS, arr.toString()).apply()
            _installedPlugins.value = value
        }

    fun upsertPlugin(plugin: com.aotv.app.data.plugins.InstalledPlugin) {
        val list = installedPluginList.toMutableList()
        val i = list.indexOfFirst { it.id == plugin.id }
        if (i >= 0) list[i] = plugin else list += plugin
        installedPluginList = list
    }

    fun removePlugin(id: String) {
        installedPluginList = installedPluginList.filterNot { it.id == id }
        val settings = pluginSettingsMap - id
        pluginSettingsMap = settings
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        installedPluginList = installedPluginList.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    /** Configurações de todos os plugins (id -> mapa chave/valor, do onSettings()). */
    var pluginSettingsMap: Map<String, Map<String, Any?>>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(KEY_PLUGIN_SETTINGS, null) ?: "{}")
            o.keys().asSequence().associateWith { id ->
                val inner = o.optJSONObject(id) ?: org.json.JSONObject()
                inner.keys().asSequence().associateWith { k -> inner.opt(k) as Any? }
            }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.forEach { (id, map) ->
                val inner = org.json.JSONObject()
                map.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> inner.put(k, v)
                        is Number -> inner.put(k, v)
                        is String -> inner.put(k, v)
                        null -> inner.put(k, org.json.JSONObject.NULL)
                        else -> inner.put(k, v.toString())
                    }
                }
                o.put(id, inner)
            }
            prefs.edit().putString(KEY_PLUGIN_SETTINGS, o.toString()).apply()
        }

    /** Configurações de um plugin (vazio = usar os padrões do onSettings()). */
    fun pluginSettings(id: String): Map<String, Any?> = pluginSettingsMap[id].orEmpty()

    fun setPluginSettings(id: String, values: Map<String, Any?>) {
        pluginSettingsMap = pluginSettingsMap + (id to values)
    }

    /** Timeout de cada execução de plugin, em segundos (30–180, padrão 60). */
    var pluginTimeoutSec: Int
        get() = prefs.getInt(KEY_PLUGIN_TIMEOUT, 60).coerceIn(30, 180)
        set(value) { prefs.edit().putInt(KEY_PLUGIN_TIMEOUT, value.coerceIn(30, 180)).apply() }

    /** Pausa global: desligado, nenhum plugin roda nas fontes (botão "Pausar plugins"). */
    var pluginGlobalEnabled: Boolean
        get() = prefs.getBoolean("plugin_global_enabled", true)
        set(value) { prefs.edit().putBoolean("plugin_global_enabled", value).apply() }

    /** Atualização automática dos repositórios vencidos ao abrir a tela de Plugins. */
    var pluginAutoUpdate: Boolean
        get() = prefs.getBoolean("plugin_auto_update", true)
        set(value) { prefs.edit().putBoolean("plugin_auto_update", value).apply() }

    /**
     * Plugins rodando ao mesmo tempo na folha de fontes (1–32, padrão 10, o teto da v1.0.11).
     * 32 é o do JOTV ("Buscar em paralelo"); 1 é "Um de cada vez".
     */
    var pluginConcurrency: Int
        get() = prefs.getInt("plugin_concurrency", 10).coerceIn(1, 32)
        set(value) { prefs.edit().putInt("plugin_concurrency", value.coerceIn(1, 32)).apply() }

    /** Último resultado de cada plugin (id -> PluginLastRun, p/ diagnóstico). */
    private fun readPluginLastRuns(): Map<String, com.aotv.app.data.plugins.PluginLastRun> = runCatching {
        val o = org.json.JSONObject(prefs.getString(KEY_PLUGIN_LAST_RUN, null) ?: "{}")
        o.keys().asSequence().associateWith { id ->
            val r = o.optJSONObject(id) ?: org.json.JSONObject()
            com.aotv.app.data.plugins.PluginLastRun(
                status = r.optString("status", "error"),
                streams = r.optInt("streams", 0),
                millis = r.optLong("millis", 0L),
                at = r.optLong("at", 0L),
                detail = r.optString("detail", ""),
            )
        }
    }.getOrDefault(emptyMap())

    private val _pluginLastRuns = MutableStateFlow(readPluginLastRuns())
    /** Último resultado por plugin, que avisa quem observa (tela de Plugins). */
    val pluginLastRuns: StateFlow<Map<String, com.aotv.app.data.plugins.PluginLastRun>> =
        _pluginLastRuns.asStateFlow()

    fun setPluginLastRun(id: String, run: com.aotv.app.data.plugins.PluginLastRun) {
        val map = _pluginLastRuns.value + (id to run)
        val o = org.json.JSONObject()
        map.forEach { (pid, r) ->
            o.put(pid, org.json.JSONObject().put("status", r.status).put("streams", r.streams)
                .put("millis", r.millis).put("at", r.at).put("detail", r.detail))
        }
        prefs.edit().putString(KEY_PLUGIN_LAST_RUN, o.toString()).apply()
        _pluginLastRuns.value = map
    }

    // ── Catálogo AOTV (o catálogo próprio do app, fixo e desligável como um addon) ──
    private val _catalogOn = MutableStateFlow(prefs.getBoolean("aotv_catalog_on", true))
    /** Catálogo AOTV ligado. Desligado, as fileiras do TMDB somem da Início de todos os perfis. */
    val catalogOnFlow: StateFlow<Boolean> = _catalogOn.asStateFlow()
    var catalogOn: Boolean
        get() = _catalogOn.value
        set(value) { prefs.edit().putBoolean("aotv_catalog_on", value).apply(); _catalogOn.value = value }

    // ── Manifests guardados (nome e catálogos de cada addon, para a Início abrir sem rede) ──
    private val _addonMeta = MutableStateFlow(HomeLayout.decodeAddons(prefs.getString("addon_meta_v1", null)))
    val addonMetaFlow: StateFlow<Map<String, CachedAddon>> = _addonMeta.asStateFlow()

    /**
     * Guarda o que o manifest diz. Chamado toda vez que um manifest é lido com sucesso, às vezes
     * vários ao mesmo tempo (o gerenciador lê todos em paralelo), daí o synchronized.
     */
    fun cacheManifest(url: String, name: String, catalogs: List<com.aotv.app.data.remote.AddonCatalog>, id: String = "") {
        val base = baseOf(url)
        val now = CachedAddon(name, catalogs, id)
        synchronized(_addonMeta) {
            if (_addonMeta.value[base] == now) return
            val map = _addonMeta.value + (base to now)
            prefs.edit().putString("addon_meta_v1", HomeLayout.encodeAddons(map)).apply()
            _addonMeta.value = map
        }
        refreshLayout()
    }

    /** Bases de addon instalado depois da Início personalizável. Catálogo novo deles entra ligado. */
    private var autoOnBases: Set<String>
        get() = prefs.getStringSet("home_auto_on_v1", emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet("home_auto_on_v1", value.toSet()).apply() }

    // ── Início personalizável (por perfil) ────────────────────────────────────
    /** De onde vem o destaque do topo. Vazio = automático (em alta do AOTV ou a 1ª fileira). */
    private val _heroSource = MutableStateFlow(prefs.getString(sk("home_hero_v1"), "").orEmpty())
    val heroSourceFlow: StateFlow<String> = _heroSource.asStateFlow()
    var heroSource: String
        get() = _heroSource.value
        set(value) { prefs.edit().putString(sk("home_hero_v1"), value).apply(); _heroSource.value = value }

    // ── Coleções (formato do Nuvio), por perfil. Antes do layout, que conta com elas. ──
    private fun readCollections(): List<com.aotv.app.data.collections.MediaCollection> =
        prefs.getString(sk("collections_v1"), null)
            ?.let { com.aotv.app.data.collections.CollectionsJson.parse(it).getOrNull() }
            .orEmpty()

    private val _collections = MutableStateFlow(readCollections())
    val collectionsFlow: StateFlow<List<com.aotv.app.data.collections.MediaCollection>> = _collections.asStateFlow()

    fun saveCollections(list: List<com.aotv.app.data.collections.MediaCollection>) {
        prefs.edit().putString(sk("collections_v1"), com.aotv.app.data.collections.CollectionsJson.encode(list)).apply()
        refreshLayout()
    }

    private val _homeLayout = MutableStateFlow(computeLayout())
    /** Todas as fileiras do perfil na ordem escolhida, ligadas ou não. */
    val homeLayoutFlow: StateFlow<List<HomeRowPref>> = _homeLayout.asStateFlow()
    val homeLayout: List<HomeRowPref> get() = _homeLayout.value

    /** Liga ou desliga a fileira. [ref] serve para catálogo que ainda não estava na lista. */
    fun setHomeRowEnabled(key: String, on: Boolean, ref: HomeCatalogRef? = null) {
        val cur = homeLayout
        saveLayout(
            if (cur.any { it.key == key }) cur.map { if (it.key == key) it.copy(enabled = on) else it }
            else if (ref != null) cur + HomeRowPref(key, on, "", ref) else cur
        )
    }

    /** Liga ou desliga várias fileiras de uma vez (Marcar/Desmarcar todas do painel). */
    fun setHomeRowsEnabled(keys: Set<String>, on: Boolean) {
        val cur = homeLayout
        saveLayout(cur.map { if (it.key in keys) it.copy(enabled = on) else it })
    }

    /** Dá outro nome à fileira. Vazio volta ao nome original. */
    fun renameHomeRow(key: String, title: String) =
        saveLayout(homeLayout.map { if (it.key == key) it.copy(title = title.trim().take(60)) else it })

    fun moveHomeRow(key: String, delta: Int, visible: (HomeRowPref) -> Boolean = { true }) {
        val cur = homeLayout
        val moved = HomeLayout.move(cur, key, delta, visible)
        if (moved !== cur) saveLayout(moved)
    }

    fun resetHomeLayout() {
        saveLayout(HomeLayout.reset(homeLayout))
        heroSource = ""
    }

    private fun saveLayout(list: List<HomeRowPref>) {
        prefs.edit().putString(sk("home_layout_v1"), HomeLayout.encode(list)).apply()
        refreshLayout()
    }

    private fun refreshLayout() {
        // Proteção para chamada durante a construção, antes do fluxo existir.
        @Suppress("SENSELESS_COMPARISON")
        if (_homeLayout == null) return
        // Coleções são do perfil. Troca de perfil passa por aqui e recarrega as dele.
        _collections.value = readCollections()
        _homeLayout.value = computeLayout()
        _heroSource.value = prefs.getString(sk("home_hero_v1"), "").orEmpty()
        _homeDisplay.value = readHomeDisplay()
        _detailCinematic.value = prefs.getBoolean(sk("pref_detail_cinematic"), false)
        _detailPosterTransition.value = prefs.getBoolean(sk("pref_detail_poster_transition"), true)
        _detailIconActions.value = prefs.getBoolean(sk("pref_detail_icon_actions"), true)
        _detailTabs.value = prefs.getBoolean(sk("pref_detail_tabs"), false)
        _detailEpisodeStyle.value = prefs.getString(sk("pref_detail_episode_style"), "horizontal") ?: "horizontal"
        _streamSearch.value = prefs.getBoolean(sk("pref_stream_search"), true)
        _streamBadgesSize.value = prefs.getBoolean(sk("pref_stream_badges_size"), true)
        _streamBadgesCodec.value = prefs.getBoolean(sk("pref_stream_badges_codec"), true)
        _streamBadgesLang.value = prefs.getBoolean(sk("pref_stream_badges_lang"), true)
        _streamBadgesPos.value = prefs.getString(sk("pref_stream_badges_pos"), "stats") ?: "stats"
        _streamLogo.value = prefs.getBoolean(sk("pref_stream_logo"), true)
        _streamPinned.value = readStringList("pref_stream_pinned")
        _playerButtons.value = readStringList("pref_player_buttons")
        _playerTopActions.value = readStringList("pref_player_top_actions")
        _playerTapSeek.value = prefs.getBoolean(sk("pref_player_tap_seek"), true)
        _playerVolBoost.value = prefs.getBoolean(sk("pref_player_vol_boost"), false)
        _playerVolBoostLevel.value = prefs.getFloat(sk("pref_player_vol_boost_level"), 1.5f)
        _playerMini.value = prefs.getBoolean(sk("pref_player_mini"), true)
        _playerAutoNext.value = prefs.getBoolean(sk("pref_player_auto_next"), true)
        _playerAutoNextDelay.value = prefs.getInt(sk("pref_player_auto_next_delay"), 10)
        _playerAutoSwitch.value = prefs.getBoolean(sk("pref_player_auto_switch"), true)
        _playerSourceTimeout.value = prefs.getInt(sk("pref_player_source_timeout"), 12)
        _playerAnimeSkipClientId.value = prefs.getString(sk("pref_player_animeskip_client_id"), "") ?: ""
    }

    private fun computeLayout(): List<HomeRowPref> {
        val entries = addonEntries
        val meta = _addonMeta.value
        val available = entries.flatMap { e ->
            val base = baseOf(e.url)
            val m = meta[base] ?: return@flatMap emptyList()
            m.catalogs.map { c -> HomeCatalogRef(base, c.type, c.id, c.name, m.name) }
        }
        val saved = HomeLayout.decode(prefs.getString(sk("home_layout_v1"), null)) ?: HomeLayout.default(readHomeCatalogs())
        return HomeLayout.normalize(saved, entries.mapTo(HashSet()) { baseOf(it.url) }, available, autoOnBases, _collections.value)
    }

    private fun readHomeCatalogs(): List<HomeCatalogRef> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(sk(KEY_HOME_CATALOGS), "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HomeCatalogRef(o.optString("b"), o.optString("t"), o.optString("i"), o.optString("n"), o.optString("a"))
                .takeIf { it.base.startsWith("http") && it.id.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun decodeEntries(raw: String): List<AddonEntry> = runCatching {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optString("u").trim()
            if (u.startsWith("http")) AddonEntry(u, o.optBoolean("e", true), o.optString("i")) else null
        }
    }.getOrDefault(emptyList())

    private fun encodeEntries(list: List<AddonEntry>): String {
        val arr = org.json.JSONArray()
        list.forEach {
            val o = org.json.JSONObject().put("u", it.url).put("e", it.enabled)
            if (it.id.isNotBlank()) o.put("i", it.id)
            arr.put(o)
        }
        return arr.toString()
    }
}
