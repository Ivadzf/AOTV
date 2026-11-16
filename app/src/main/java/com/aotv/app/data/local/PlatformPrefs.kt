package com.aotv.app.data.local

import android.content.Context
import com.aotv.app.BuildConfig
import com.aotv.app.ui.theme.CustomThemeColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlatformMode { AUTO, TV, PHONE }

/** Fonte que dá o som de um título ([sig] reconhece a fonte, o link muda) e o atraso em segundos. */
data class ExtAudioPick(val sig: String, val delaySec: Double, val at: Long)

/** Atraso do áudio próprio de um título (Lote 14 item 3, v1.24.0), em segundos. Chave "TIPO:tmdbId". */
data class OwnAudioDelay(val delaySec: Double, val at: Long)

/**
 * Última fonte ESCOLHIDA À MÃO num título (Lote 4 item 6, v1.12.0).
 * Identidade estável = addon + qualidade (ver [pickPreferredSource] no player:
 * o rótulo muda a cada episódio e a URL muda a cada resolve). NUNCA guarda a
 * URL: links de addon costumam trazer token/assinatura com validade curta e
 * não podem ficar persistidos. [at] é p/ cortar os títulos mais velhos.
 */
data class LastSourcePick(val addon: String, val qualityRank: Int, val at: Long)

/** Última faixa de áudio escolhida num canal ao vivo (Lote 8, v1.18.0). [lang] é o idioma da faixa (ex. "por") ou, sem idioma, o rótulo dela. [at] é p/ cortar os canais mais velhos. */
data class LiveAudioPick(val lang: String, val at: Long)

/**
 * Um addon Stremio instalado. A ordem da lista é a prioridade (o 1º é tentado primeiro).
 * [id] é o id do manifest, serve para reconhecer o mesmo addon com URL configurada diferente.
 */
data class AddonEntry(val url: String, val enabled: Boolean = true, val id: String = "")

/** Catálogo de addon marcado para virar fileira na Início. */
data class HomeCatalogRef(val base: String, val type: String, val id: String, val name: String, val addon: String) {
    val key get() = "$base|$type|$id"
}

/**
 * Perfil de quem assiste. [color] é o índice em [PROFILE_COLORS].
 * Campos novos (v1.11.0, Lote 3) são todos opcionais com default, então perfis
 * salvos antes deles continuam válidos: sem avatar = inicial colorida, sem
 * PIN = troca livre, sem "addons primários" = comportamento atual.
 * [avatarUrl] URL de imagem colada pelo usuário (http/https); [avatarId] id
 * de um preset do catálogo embutido ([AVATAR_CATALOG]); [pinHash] hash do
 * PIN do perfil ("" = sem PIN); [usePrimaryAddons] resolve as fontes fixadas
 * com a lista do perfil principal em vez da própria (ver [pinnedOwnerId]).
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val kids: Boolean = false,
    val avatarUrl: String = "",
    val avatarId: String = "",
    val pinHash: String = "",
    val usePrimaryAddons: Boolean = false,
)

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
    "recent_searches", "ext_audio_v1", "own_audio_delay_v1", "home_catalogs_v1", "sub_size", "sub_bg",
    "sub_color", "sub_bg_opacity", "sub_outline", "sub_font",
    "pref_audio", "pref_max_quality", "pref_hero_trailer",
    "trakt_access_token_v1", "trakt_refresh_token_v1", "trakt_expires_at_v1",
    "trakt_enabled_v1", "trakt_user_v1",
    "home_layout_v1", "home_hero_v1", "pref_subtitle_on", "pref_sub_lang", "collections_v1",
    "pref_hero_on", "pref_hero_type_suffix", "pref_hide_unreleased", "home_hero_catalogs_v1",
    "pref_poster_size", "pref_poster_radius", "pref_home_density", "pref_poster_badges",
    "pref_poster_quality_badges",
    "pref_hero_style", "pref_dynamic_bg", "pref_section_underline",
    "pref_detail_cinematic", "pref_detail_poster_transition", "pref_detail_icon_actions",
    "pref_detail_tabs", "pref_detail_episode_style", "pref_detail_episode_ratings",
    "pref_stream_search", "pref_stream_badges_size", "pref_stream_badges_codec",
    "pref_stream_badges_lang", "pref_stream_badges_pos", "pref_stream_logo",
    "pref_stream_pinned",
    "pref_player_buttons", "pref_player_top_actions", "pref_player_tap_seek",
    "pref_player_vol_boost", "pref_player_vol_boost_level", "pref_player_mini",
    "pref_player_auto_next", "pref_player_auto_next_delay",
    "pref_player_auto_switch", "pref_player_source_timeout",
    // Lote 11 — Guarda de duração (v1.21.0): avisar antes de tocar um
    // arquivo cuja duração destoa >10% da do título no TMDB.
    "pref_duration_guard",
    "pref_player_animeskip_client_id", "pref_player_speed", "pref_player_swipe_seek",
    // Lote 4 — Player parte 2 (v1.12.0): idioma de áudio preferido, reutilizar
    // a última fonte do título e o mapa da última fonte escolhida por título.
    "pref_player_audio_lang", "pref_player_reuse_last_source", "last_source_v1",
    "pref_player_color_brightness", "pref_player_color_contrast", "pref_player_color_saturation",
    "pref_player_color_hue", "pref_player_color_preset", "pref_player_color_daltonism",
    // Lote 4 — motor do player (v1.12.0): player padrão (interno/externo) e decoder.
    "pref_player_engine", "pref_player_decoder",
    // Lote 5 — buffer do player (v1.14.0): perfil de buffer do ExoPlayer.
    "pref_player_buffer",
    // Lote 9 — Player parte 3 (v1.19.0): AutoSync de legendas, pular abertura
    // automático, cartão "A seguir" e equalizador (liga, preset, bandas em dB).
    "pref_player_autosync", "pref_player_auto_skip", "pref_player_up_next",
    "pref_player_equalizer", "pref_player_eq_preset", "pref_player_eq_bands",
    // Lote 12 — Frente 2 (Player e fontes, v1.22.0): modo de tela padrão,
    // vista em ranking da folha de fontes e gravações marcadas como vistas.
    "pref_player_screen_mode", "pref_player_sources_ranked", "recordings_seen_v1",
    // Lote 3 — Página de detalhes: título em idioma original (v1.11.0).
    "pref_detail_original_title",
    // Lote 8 — Tela de detalhe (v1.18.0): toggles das seções novas.
    "pref_detail_show_aggregate_rating", "pref_detail_show_trakt_reviews", "pref_detail_show_quotes",
    // Lote 12 — Frente 3 (Notas e atualizador, v1.22.0): fonte da nota
    // exibida de filmes e séries ("auto" = MDBList quando conectado, senão
    // TMDB; "mdblist" / "tmdb" forçam uma fonte). Episódios seguem TMDB.
    "pref_ratings_source",
    // Lote 2 — modo privado (v1.10.0): assistir sem gravar nada no perfil.
    "pref_private_mode",
    // Lote 2 — Biblioteca e Descoberta: Continuar assistindo novo + Modo aleatório.
    "pref_random_include_continue", "pref_random_include_watchlist", "pref_random_unwatched_only",
    // Lote 8 — Esportes, Gravações e Calendário (v1.18.0).
    "sports_show", "sports_hide_scores", "sports_score_delay_sec",
    "sports_live_scores", "sports_game_alerts",
    // Lote 13 — Esportes parte 2 (v1.23.0): Fix Channel — override
    // jogo/rede/time → canal (mapa JSON, por perfil, no padrão de
    // "live_audio_v1"). Só ids de canal das listas do usuário.
    "sports_fix_channel_v1",
    // Lote 15 — Aba de Esportes (v1.25.0, Bloco 1): ligas ligadas no seletor
    // da aba, ligas seguidas e times seguidos (listas de ids/nomes, por perfil).
    "sports_tab_leagues_v1", "sports_followed_leagues_v1", "sports_followed_teams_v1",
    "record_start_early_min", "record_run_late_min", "record_keep_episodes",
    "record_low_space_mb", "record_notifications",
    "calendar_hide_spoilers",
    // Lote 13 (v1.23.0): auto-add do calendário via trackers + "Não hoje"
    // dos episódios novos (mapa tmdbId -> data do dispenso).
    "calendar_auto_add_trackers", "not_today_v1",
    // v1.13.0 — Backup: títulos removidos do Continuar assistindo (por perfil).
    "hidden_continue_v1",
    // Fase D — trackers, Discord e servidores de mídia (por perfil, como o Trakt).
    "simkl_v1", "simkl_client_id_v1", "mdblist_key_v1", "omdb_key_v1",
    "anilist_v1", "wetrakr_v1", "publicmetadb_v1",
    "discord_v1", "jellyfin_v1", "emby_v1",
    // v1.17.0 — MDBList, AniList, TheTVDB e avaliação pós-filme (por perfil).
    // As chaves de conta (API key / client ID / token) são DO APARELHO e
    // vivem nas listas do AotvBackup, não aqui — ver o comentário lá.
    "mdblist_enabled", "mdblist_rating_sources",
    "anilist_access_token", "anilist_enabled",
    "tvdb_episode_ordering", "tvdb_ordering_enabled",
    "post_play_rating_enabled",
    // Lote 8 — Live TV (v1.18.0): guia, atualização, exibição de canais,
    // canal reserva, Continuar assistindo, faixa de áudio e catch-up HLS
    // (tudo por perfil). O formato do stream Xtream é GLOBAL ("live_xtream_format")
    // e NÃO entra aqui. A última faixa de áudio por canal ("live_audio_v1")
    // segue o mapa JSON de "last_source_v1".
    // Lote 9 — Hub da Live TV (v1.19.0): grupos favoritos do hub
    // ("live_group_favorites_v1", lista de nomes de grupo, por perfil).
    "live_guide_days_ahead", "live_guide_hours_back",
    "live_refresh_lists_hours", "live_refresh_epg_hours",
    "live_show_logos", "live_show_names", "live_show_numbers", "live_clean_group_names",
    "live_backup_channel", "live_in_continue", "live_remember_audio", "live_catchup_hls",
    "live_audio_v1", "live_group_favorites_v1",
    // Lote 12 (v1.22.0) — Live TV parte 2 (Frente 1): Multiview, gerenciar
    // canais (ocultar, ordem, renomear, grupo, número), Assign EPG + time
    // shift e logo próprio por canal (tudo por perfil, mapas JSON).
    "live_multiview_layout",
    "live_hidden_channels_v1", "live_channel_order_v1",
    "live_channel_names_v1", "live_channel_groups_v1", "live_channel_numbers_v1",
    "live_epg_assign_v1", "live_epg_shift_v1", "live_channel_logos_v1",
    // Lote 11 (v1.21.0) — PIN parental por grupo (Frente 2): grupos de canais
    // trancados com PIN (lista de nomes de grupo, por perfil). O PIN em si é
    // o PIN dos pais do aparelho ("parental_pin_hash", segredo global — ver
    // AotvBackup.SECRET_GLOBAL_KEYS); só os NOMES dos grupos viajam aqui.
    "live_locked_groups_v1",
    // Lote 11 (v1.21.0) — Blur anti-spoiler (Frente 2): borrar arte e esconder
    // título/sinopse dos episódios não assistidos no detalhe da série.
    "pref_detail_spoiler_blur",
    // Lote 10 — VPN (v1.20.0): perfis trazidos pelo usuário. A Frente 3 (VPN)
    // é dona de quem ESCREVE estas chaves; o backup/sync só as CARREGA no
    // payload (o mesmo JSON do backup local). `vpn_profiles_v1` é JSON com os
    // perfis (inclui a chave privada do WireGuard/OpenVPN) e por isso é
    // segredo: só viaja com o toggle "Incluir chaves e sessões" (ver
    // AotvBackup.SECRET_SCOPED_KEYS). As outras duas não são segredo: só o
    // perfil ativo (id/nome) e o ligar automático.
    "vpn_profiles_v1", "vpn_active_profile_v1", "vpn_auto_connect_v1",
    // Lote 10 — Servidores de mídia (v1.20.0, Frente 1): mostrar o atalho dos
    // servidores (Jellyfin/Emby/Plex) na Biblioteca. As contas em si são do
    // aparelho (SharedPreferences própria, fora do backup) e NÃO entram aqui.
    "media_servers_show_v1",
    // Lote 10 — Watch Party (v1.20.0, Frente 3): sessão local e efêmera
    // (código + papel). Sem servidor, sem segredo: o código expira ao sair
    // da sessão.
    "party_session_v1",
    // Lote 14 (v1.24.0) — Descobrir com catálogos dos addons: o catálogo escolhido
    // ("addonId|type|id", vazio = TMDB). Só a escolha, sem credencial.
    "pref_discover_catalog_v1",
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

        /**
         * As 6 cores editáveis do tema personalizado (JSON, ver [CustomThemeColors]).
         * Do aparelho, como o tema: vale p/ todo mundo que usa o aparelho.
         */
        private const val KEY_CUSTOM_THEME = "custom_theme_v1"

        /** Blur real atrás das folhas/painéis (Lote 3, v1.11.0). Do aparelho, como o tema. */
        private const val KEY_PANEL_BLUR = "pref_panel_blur"

        /** Incluir chaves e sessões no backup (v1.13.0). Do aparelho. DESLIGADO por padrão. */
        private const val KEY_BACKUP_INCLUDE_SECRETS = "backup_include_secrets"

        // ── Sync via Google Drive (Lote 10, v1.20.0) — do aparelho ─────
        // Client ID OAuth configurável, vazio por padrão (ver DriveSyncService):
        // sem ele a função fica desligada. A conta, o id do arquivo e as
        // datas do último sync viajam no backup (ver AotvBackup.GLOBAL_KEYS);
        // o client ID é segredo (ver AotvBackup.SECRET_GLOBAL_KEYS) e só
        // viaja com o toggle "Incluir chaves e sessões" — mesmo precedente
        // do client ID do Trakt.

        /** Client ID OAuth do Google Drive (tipo "Aplicativo da Web", do projeto do usuário no Google Cloud). "" = sync desligado. */
        private const val KEY_DRIVE_CLIENT_ID = "drive_client_id"

        /** E-mail da conta Google conectada ao Drive (só p/ mostrar na tela). */
        private const val KEY_DRIVE_ACCOUNT_EMAIL = "drive_account_email"

        /** Id do arquivo `aotv-backup.json` no Drive (p/ atualizar o mesmo arquivo). */
        private const val KEY_DRIVE_FILE_ID = "drive_file_id"

        /** Quando foi o último sync com o Drive (epoch ms). 0 = nunca. */
        private const val KEY_DRIVE_LAST_SYNC_AT = "drive_last_sync_at"

        /** `modifiedTime` do arquivo remoto no último sync (epoch ms): base do conflito. 0 = nunca. */
        private const val KEY_DRIVE_LAST_REMOTE_MODIFIED = "drive_last_remote_modified_ms"

        // ── Downloads e dados móveis (Lote 5, v1.14.0) — do aparelho ─────
        // Tudo global (NÃO vai p/ SCOPED_KEYS): a lista de downloads, a pasta
        // e a rede são do aparelho, não do perfil — a fila é a mesma p/ todo
        // mundo que usa o aparelho. O backup leva estas chaves (ver
        // AotvBackup.GLOBAL_KEYS); a URI da pasta viaja como texto, mas a
        // permissão persistida NÃO viaja no backup — depois de restaurar, a
        // tela de Downloads pede p/ escolher a pasta de novo.

        /** "Só Wi-Fi" nos downloads: ligado, download só roda em Wi-Fi. */
        private const val KEY_DOWNLOADS_WIFI_ONLY = "downloads_wifi_only"

        /** URI da pasta de download escolhida (SAF, ACTION_OPEN_DOCUMENT_TREE). "" = pasta do app. */
        private const val KEY_DOWNLOADS_FOLDER_URI = "downloads_folder_uri"

        /** Nome de exibição da pasta escolhida (só p/ mostrar na tela). */
        private const val KEY_DOWNLOADS_FOLDER_NAME = "downloads_folder_name"

        /** Bytes de dados móveis medidos hoje (ver MobileDataUsage). */
        private const val KEY_MOBILE_DATA_TODAY = "mobile_data_today_bytes"

        /** Bytes de dados móveis medidos no mês. */
        private const val KEY_MOBILE_DATA_MONTH = "mobile_data_month_bytes"

        /** Dia do contador de hoje ("yyyy-MM-dd"); mudou = zera o de hoje. */
        private const val KEY_MOBILE_DATA_DAY = "mobile_data_day"

        /** Mês do contador ("yyyy-MM"); mudou = zera o do mês. */
        private const val KEY_MOBILE_DATA_MONTH_KEY = "mobile_data_month"

        /** Limite mensal de dados móveis, em MB. 0 = sem limite (padrão). */
        private const val KEY_MOBILE_DATA_LIMIT_MB = "mobile_data_limit_mb"

        /** URI da pasta de gravações escolhida (SAF, ACTION_OPEN_DOCUMENT_TREE). "" = pasta do app. */
        private const val KEY_RECORDINGS_FOLDER_URI = "recordings_folder_uri"

        /** Nome de exibição da pasta de gravações (só p/ mostrar na tela). */
        private const val KEY_RECORDINGS_FOLDER_NAME = "recordings_folder_name"
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
            Profile(
                o.getString("id"), o.optString("name", "Perfil"), o.optInt("color", 0), o.optBoolean("kids", false),
                // Campos novos (v1.11.0): opt* com default = perfil antigo lê como se
                // nunca tivesse avatar/PIN/primários, sem perder nome, cor ou infantil.
                o.optString("avatar_url", ""), o.optString("avatar_id", ""),
                o.optString("pin_hash", ""), o.optBoolean("use_primary_addons", false),
            )
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(Profile(MAIN_PROFILE, "Principal")) }

    private fun saveProfiles(list: List<Profile>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(
                org.json.JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("kids", it.kids)
                    .put("avatar_url", it.avatarUrl).put("avatar_id", it.avatarId)
                    .put("pin_hash", it.pinHash).put("use_primary_addons", it.usePrimaryAddons),
            )
        }
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

    fun updateProfile(p: Profile) {
        // Antes só copiava o nome (o resto — cor, infantil, avatar, PIN,
        // primários — se perdia no salvar). Agora grava o perfil inteiro,
        // só normalizando o nome; quem chama passa o copy completo.
        val clean = p.copy(name = p.name.trim().take(20).ifBlank { "Perfil" })
        saveProfiles(_profiles.value.map { if (it.id == p.id) clean else it })
        // Trocar "usar addons primários" muda DE QUAL perfil vem a lista de
        // fontes fixadas: recarrega o fluxo efetivo na hora.
        _streamPinned.value = readStringListFor("pref_stream_pinned", pinnedOwnerId())
    }

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

    // ── Trakt (v1.17.0) ───────────────────────────────────────────────────
    // Client ID/secret são DO APARELHO (não por perfil): a conta do Trakt é
    // de cada perfil, mas o app registrado em trakt.tv/oauth/applications é
    // um só. Sessão (tokens, expiração, ligado, usuário) é POR PERFIL, como
    // o Simkl. A sessão antiga ("trakt_v1", JSON cru) foi removida: quem
    // tinha login precisa conectar de novo (o formato mudou e a chave não
    // existe mais). Nenhum destes campos tem valor padrão embutido.

    /** Client ID do Trakt, colado pelo usuário em Configurações. Vazio = desligado. */
    var traktClientId: String
        get() = prefs.getString("trakt_client_id_v1", "") ?: ""
        set(value) { prefs.edit().putString("trakt_client_id_v1", value.trim()).apply() }

    /** Client secret do Trakt, colado pelo usuário. Opcional: sem ele o refresh não roda e o usuário reconecta quando o token expira. */
    var traktClientSecret: String
        get() = prefs.getString("trakt_client_secret_v1", "") ?: ""
        set(value) { prefs.edit().putString("trakt_client_secret_v1", value.trim()).apply() }

    /** Access token do Trakt do perfil. Vazio sem login. */
    var traktAccessToken: String
        get() = prefs.getString(sk("trakt_access_token_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("trakt_access_token_v1"), value).apply() }

    /** Refresh token do Trakt do perfil. Vazio sem login (ou quando o fluxo não devolveu um). */
    var traktRefreshToken: String
        get() = prefs.getString(sk("trakt_refresh_token_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("trakt_refresh_token_v1"), value).apply() }

    /** Expiração do access token do perfil (ms desde epoch). 0 = sem token. */
    var traktExpiresAt: Long
        get() = prefs.getLong(sk("trakt_expires_at_v1"), 0L)
        set(value) { prefs.edit().putLong(sk("trakt_expires_at_v1"), value).apply() }

    /** Scrobble do Trakt ligado neste perfil (só vale com login). */
    var traktEnabled: Boolean
        get() = prefs.getBoolean(sk("trakt_enabled_v1"), false)
        set(value) { prefs.edit().putBoolean(sk("trakt_enabled_v1"), value).apply() }

    /** Nome da conta do Trakt conectada neste perfil. Vazio quando o Trakt não deu o nome. */
    var traktUser: String
        get() = prefs.getString(sk("trakt_user_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("trakt_user_v1"), value).apply() }

    // ── Lote 8 — Live TV (v1.18.0) ──────────────────────────────────────────
    // Guia configurável, atualização, exibição de canais, canal reserva,
    // Continuar assistindo, faixa de áudio e catch-up HLS. Tudo por perfil
    // (sk), exceto o formato do stream Xtream, que é GLOBAL (vale p/ todas
    // as contas do aparelho) e por isso NÃO usa sk() nem entra em SCOPED_KEYS.
    // Os fluxos são recarregados na troca de perfil em refreshLayout().

    private val _liveGuideDaysAhead = MutableStateFlow(prefs.getInt(sk("live_guide_days_ahead"), 3))
    /** Dias à frente que o guia permite alcançar (1–7, padrão 3). */
    val liveGuideDaysAheadFlow: StateFlow<Int> = _liveGuideDaysAhead.asStateFlow()
    var liveGuideDaysAhead: Int
        get() = _liveGuideDaysAhead.value
        set(value) { val v = value.coerceIn(1, 7); prefs.edit().putInt(sk("live_guide_days_ahead"), v).apply(); _liveGuideDaysAhead.value = v }

    private val _liveGuideHoursBack = MutableStateFlow(prefs.getInt(sk("live_guide_hours_back"), 1))
    /** Horas para trás que o guia permite alcançar (0–24, padrão 1). */
    val liveGuideHoursBackFlow: StateFlow<Int> = _liveGuideHoursBack.asStateFlow()
    var liveGuideHoursBack: Int
        get() = _liveGuideHoursBack.value
        set(value) { val v = value.coerceIn(0, 24); prefs.edit().putInt(sk("live_guide_hours_back"), v).apply(); _liveGuideHoursBack.value = v }

    private val _liveRefreshListsHours = MutableStateFlow(prefs.getInt(sk("live_refresh_lists_hours"), 24))
    /** Intervalo da atualização automática das listas, em horas (1–168, padrão 24). */
    val liveRefreshListsHoursFlow: StateFlow<Int> = _liveRefreshListsHours.asStateFlow()
    var liveRefreshListsHours: Int
        get() = _liveRefreshListsHours.value
        set(value) { val v = value.coerceIn(1, 168); prefs.edit().putInt(sk("live_refresh_lists_hours"), v).apply(); _liveRefreshListsHours.value = v }

    private val _liveRefreshEpgHours = MutableStateFlow(prefs.getInt(sk("live_refresh_epg_hours"), 12))
    /** Intervalo da atualização automática do guia, em horas (1–168, padrão 12). */
    val liveRefreshEpgHoursFlow: StateFlow<Int> = _liveRefreshEpgHours.asStateFlow()
    var liveRefreshEpgHours: Int
        get() = _liveRefreshEpgHours.value
        set(value) { val v = value.coerceIn(1, 168); prefs.edit().putInt(sk("live_refresh_epg_hours"), v).apply(); _liveRefreshEpgHours.value = v }

    private val _liveShowLogos = MutableStateFlow(prefs.getBoolean(sk("live_show_logos"), true))
    /** Logo do canal visível nas fileiras da aba Ao vivo. */
    val liveShowLogosFlow: StateFlow<Boolean> = _liveShowLogos.asStateFlow()
    var liveShowLogos: Boolean
        get() = _liveShowLogos.value
        set(value) { prefs.edit().putBoolean(sk("live_show_logos"), value).apply(); _liveShowLogos.value = value }

    private val _liveShowNames = MutableStateFlow(prefs.getBoolean(sk("live_show_names"), true))
    /** Nome do canal visível nas fileiras da aba Ao vivo. */
    val liveShowNamesFlow: StateFlow<Boolean> = _liveShowNames.asStateFlow()
    var liveShowNames: Boolean
        get() = _liveShowNames.value
        set(value) { prefs.edit().putBoolean(sk("live_show_names"), value).apply(); _liveShowNames.value = value }

    private val _liveShowNumbers = MutableStateFlow(prefs.getBoolean(sk("live_show_numbers"), false))
    /** Número do canal (tvg-chno) visível nas fileiras da aba Ao vivo. */
    val liveShowNumbersFlow: StateFlow<Boolean> = _liveShowNumbers.asStateFlow()
    var liveShowNumbers: Boolean
        get() = _liveShowNumbers.value
        set(value) { prefs.edit().putBoolean(sk("live_show_numbers"), value).apply(); _liveShowNumbers.value = value }

    private val _liveCleanGroupNames = MutableStateFlow(prefs.getBoolean(sk("live_clean_group_names"), false))
    /** Limpa prefixos tipo "BR | " dos nomes de grupos SÓ na exibição (os dados não mudam). */
    val liveCleanGroupNamesFlow: StateFlow<Boolean> = _liveCleanGroupNames.asStateFlow()
    var liveCleanGroupNames: Boolean
        get() = _liveCleanGroupNames.value
        set(value) { prefs.edit().putBoolean(sk("live_clean_group_names"), value).apply(); _liveCleanGroupNames.value = value }

    private val _liveBackupChannel = MutableStateFlow(prefs.getBoolean(sk("live_backup_channel"), true))
    /** Canal reserva ligado: se o canal falhar, tenta outro com o mesmo nome de outra fonte (1 vez). */
    val liveBackupChannelFlow: StateFlow<Boolean> = _liveBackupChannel.asStateFlow()
    var liveBackupChannel: Boolean
        get() = _liveBackupChannel.value
        set(value) { prefs.edit().putBoolean(sk("live_backup_channel"), value).apply(); _liveBackupChannel.value = value }

    private val _liveInContinue = MutableStateFlow(prefs.getBoolean(sk("live_in_continue"), true))
    /** O que é assistido ao vivo entra no Continuar assistindo. */
    val liveInContinueFlow: StateFlow<Boolean> = _liveInContinue.asStateFlow()
    var liveInContinue: Boolean
        get() = _liveInContinue.value
        set(value) { prefs.edit().putBoolean(sk("live_in_continue"), value).apply(); _liveInContinue.value = value }

    private val _liveRememberAudio = MutableStateFlow(prefs.getBoolean(sk("live_remember_audio"), true))
    /** Lembra a última faixa de áudio escolhida em cada canal ao vivo. */
    val liveRememberAudioFlow: StateFlow<Boolean> = _liveRememberAudio.asStateFlow()
    var liveRememberAudio: Boolean
        get() = _liveRememberAudio.value
        set(value) { prefs.edit().putBoolean(sk("live_remember_audio"), value).apply(); _liveRememberAudio.value = value }

    private val _liveCatchupHls = MutableStateFlow(prefs.getBoolean(sk("live_catchup_hls"), false))
    /** Catch-up prefere a variante HLS (.m3u8) do timeshift Xtream em vez de .ts. */
    val liveCatchupHlsFlow: StateFlow<Boolean> = _liveCatchupHls.asStateFlow()
    var liveCatchupHls: Boolean
        get() = _liveCatchupHls.value
        set(value) { prefs.edit().putBoolean(sk("live_catchup_hls"), value).apply(); _liveCatchupHls.value = value }

    private val _liveXtreamFormat = MutableStateFlow(prefs.getString("live_xtream_format", "auto") ?: "auto")
    /** Formato do stream Xtream: "auto" (o da conta), "ts" ou "hls". GLOBAL, do aparelho. */
    val liveXtreamFormatFlow: StateFlow<String> = _liveXtreamFormat.asStateFlow()
    var liveXtreamFormat: String
        get() = _liveXtreamFormat.value
        set(value) { val v = if (value == "ts" || value == "hls") value else "auto"; prefs.edit().putString("live_xtream_format", v).apply(); _liveXtreamFormat.value = v }

    /**
     * Última faixa de áudio escolhida por canal ao vivo (Lote 8, v1.18.0):
     * id do canal → idioma da faixa (ex. "por") ou, sem idioma, o rótulo
     * ("Português"). No máximo 60 canais (os mais recentes); o resto cai no
     * cortar por [at]. Por perfil (SCOPED_KEYS "live_audio_v1"). Sem fluxo:
     * só o player lê e grava, como "last_source_v1".
     */
    var liveAudioPicks: Map<String, LiveAudioPick>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(sk("live_audio_v1"), null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                LiveAudioPick(v.optString("l"), v.optLong("t", 0L))
            }.filterValues { it.lang.isNotBlank() }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.entries.sortedByDescending { it.value.at }.take(60).forEach { (k, p) ->
                o.put(k, org.json.JSONObject().put("l", p.lang).put("t", p.at))
            }
            prefs.edit().putString(sk("live_audio_v1"), o.toString()).apply()
        }

    // ── Lote 9 — Hub da Live TV (v1.19.0) ───────────────────────────────────
    // Grupos de canais favoritos do hub (a estrela no cartão do grupo).
    // Lista de NOMES DE GRUPO como aparecem na tela (com a limpeza de
    // prefixo aplicada, igual a LiveTvViewModel.groups) — se a limpeza
    // ligar/desligar, o nome muda e o favorito pode não casar; é só
    // exibição, o grupo em si não muda. Guardada no mesmo formato de
    // lista JSON das outras prefs de lista (readStringList/writeStringList,
    // como "pref_stream_pinned"). Por perfil (SCOPED_KEYS). Vazio = nenhum
    // favorito. Grupos favoritos sobem p/ o topo do hub e a estrela fica
    // preenchida (ver LiveTvHub.kt).

    private val _liveGroupFavorites = MutableStateFlow(readStringList("live_group_favorites_v1"))
    /** Nomes dos grupos de canais favoritos no hub da Live TV, que avisam quem observa. */
    val liveGroupFavoritesFlow: StateFlow<List<String>> = _liveGroupFavorites.asStateFlow()
    /** Nomes dos grupos de canais favoritos no hub da Live TV. Vazio = nenhum favorito. */
    var liveGroupFavorites: List<String>
        get() = _liveGroupFavorites.value
        set(value) { writeStringList("live_group_favorites_v1", value); _liveGroupFavorites.value = value }

    /** Liga/desliga o favorito de um grupo de canais no hub da Live TV. */
    fun toggleLiveGroupFavorite(group: String) {
        val clean = group.trim()
        if (clean.isBlank()) return
        liveGroupFavorites = if (clean in liveGroupFavorites) liveGroupFavorites - clean else liveGroupFavorites + clean
    }

    // ── PIN parental por grupo (Lote 11, v1.21.0) ─────────────────────────
    // Grupos de canais trancados com o PIN dos pais (o mesmo PIN global de
    // "parental_pin_hash" — sem PIN novo e sem hash novo). A lista guarda só
    // os NOMES dos grupos, como "live_group_favorites_v1" (por perfil,
    // SCOPED_KEYS). Vazio = nenhum grupo trancado. O desbloqueio vale só
    // p/ a sessão (a tela guarda os grupos liberados em memória); fechar o
    // app tranca de novo. NÃO há detecção de conteúdo adulto — o usuário
    // marca os grupos que quer trancar, um por um, no cartão do grupo.

    private val _liveLockedGroups = MutableStateFlow(readStringList("live_locked_groups_v1"))
    /** Nomes dos grupos de canais trancados com o PIN dos pais, que avisam quem observa. */
    val liveLockedGroupsFlow: StateFlow<List<String>> = _liveLockedGroups.asStateFlow()
    /** Nomes dos grupos de canais trancados com o PIN dos pais. Vazio = nenhum trancado. */
    var liveLockedGroups: List<String>
        get() = _liveLockedGroups.value
        set(value) { writeStringList("live_locked_groups_v1", value); _liveLockedGroups.value = value }

    /** O grupo está trancado com o PIN dos pais? */
    fun isLiveGroupLocked(group: String): Boolean = group.trim() in liveLockedGroups

    /** Liga/desliga o cadeado de um grupo de canais. */
    fun toggleLiveGroupLocked(group: String) {
        val clean = group.trim()
        if (clean.isBlank()) return
        liveLockedGroups = if (clean in liveLockedGroups) liveLockedGroups - clean else liveLockedGroups + clean
    }

    /** Destranca um grupo (tira da lista). Usado ao remover o cadeado pelas Configurações. */
    fun unlockLiveGroup(group: String) {
        val clean = group.trim()
        if (clean.isBlank()) return
        liveLockedGroups = liveLockedGroups - clean
    }

    // ── Lote 12 — Live TV parte 2 (v1.22.0, Frente 1) ─────────────────────
    // Multiview, gerenciar canais, Assign EPG + time shift e logo próprio
    // por canal. Tudo por perfil (sk + SCOPED_KEYS). Os mapas são JSON
    // (canal → valor) no mesmo padrão de "live_audio_v1"; as listas usam
    // readStringList/writeStringList. Sem fluxo p/ os mapas: quem lê é o
    // repositório ao montar a lista de exibição (ver LiveTvRepository).
    // As listas (ocultos e ordem) têm fluxo: a aba Ao vivo reage na hora.

    private val _liveMultiviewLayout = MutableStateFlow(prefs.getInt(sk("live_multiview_layout"), 1))
    /** Canais na tela do Multiview: 1, 2 ou 4 (padrão 1). Fora da faixa volta p/ 1. */
    val liveMultiviewLayoutFlow: StateFlow<Int> = _liveMultiviewLayout.asStateFlow()
    var liveMultiviewLayout: Int
        get() = _liveMultiviewLayout.value
        set(value) { val v = if (value == 2 || value == 4) value else 1; prefs.edit().putInt(sk("live_multiview_layout"), v).apply(); _liveMultiviewLayout.value = v }

    private val _liveHiddenChannels = MutableStateFlow(readStringList("live_hidden_channels_v1"))
    /** Ids dos canais ocultos pelo usuário — somem de todas as vistas (hub, grade, lista, busca, guia e player). */
    val liveHiddenChannelsFlow: StateFlow<List<String>> = _liveHiddenChannels.asStateFlow()
    var liveHiddenChannels: List<String>
        get() = _liveHiddenChannels.value
        set(value) { writeStringList("live_hidden_channels_v1", value); _liveHiddenChannels.value = value }

    /** Oculta ou mostra um canal. */
    fun toggleLiveChannelHidden(channelId: String) {
        if (channelId.isBlank()) return
        liveHiddenChannels = if (channelId in liveHiddenChannels) liveHiddenChannels - channelId else liveHiddenChannels + channelId
    }

    private val _liveChannelOrder = MutableStateFlow(readStringList("live_channel_order_v1"))
    /** Ordem própria dos canais (ids, na ordem de exibição). Canal fora da lista fica depois, na ordem da fonte. */
    val liveChannelOrderFlow: StateFlow<List<String>> = _liveChannelOrder.asStateFlow()
    var liveChannelOrder: List<String>
        get() = _liveChannelOrder.value
        set(value) { writeStringList("live_channel_order_v1", value); _liveChannelOrder.value = value }

    /** Nome próprio de um canal (vazio = o nome da lista). */
    var liveChannelNames: Map<String, String>
        get() = readStringMap("live_channel_names_v1")
        set(value) { writeStringMap("live_channel_names_v1", value) }

    /** Renomeia um canal (nome em branco volta ao nome da lista). */
    fun setLiveChannelName(channelId: String, name: String) {
        if (channelId.isBlank()) return
        val m = liveChannelNames.toMutableMap()
        if (name.isBlank()) m.remove(channelId) else m[channelId] = name.trim()
        liveChannelNames = m
    }

    /** Grupo próprio de um canal (vazio = o grupo da lista). */
    var liveChannelGroups: Map<String, String>
        get() = readStringMap("live_channel_groups_v1")
        set(value) { writeStringMap("live_channel_groups_v1", value) }

    /** Muda o grupo de um canal (grupo em branco volta ao grupo da lista). */
    fun setLiveChannelGroup(channelId: String, group: String) {
        if (channelId.isBlank()) return
        val m = liveChannelGroups.toMutableMap()
        if (group.isBlank()) m.remove(channelId) else m[channelId] = group.trim()
        liveChannelGroups = m
    }

    /** Número próprio de um canal (id → número). */
    var liveChannelNumbers: Map<String, Int>
        get() = readStringMap("live_channel_numbers_v1").mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap()
        set(value) { writeStringMap("live_channel_numbers_v1", value.mapValues { it.value.toString() }) }

    /** Renumera um canal (null ou ≤ 0 volta ao número da lista). */
    fun setLiveChannelNumber(channelId: String, number: Int?) {
        if (channelId.isBlank()) return
        val m = liveChannelNumbers.toMutableMap()
        if (number == null || number <= 0) m.remove(channelId) else m[channelId] = number
        liveChannelNumbers = m
    }

    /** EPG associado manualmente a um canal (id do canal → id do guia). */
    var liveEpgAssign: Map<String, String>
        get() = readStringMap("live_epg_assign_v1")
        set(value) { writeStringMap("live_epg_assign_v1", value) }

    /** Associa uma fonte de EPG a um canal (id em branco desfaz a associação). */
    fun setLiveEpgAssign(channelId: String, epgId: String) {
        if (channelId.isBlank()) return
        val m = liveEpgAssign.toMutableMap()
        if (epgId.isBlank()) m.remove(channelId) else m[channelId] = epgId.trim()
        liveEpgAssign = m
    }

    /** Deslocamento do guia por canal, em horas (id → -24..+24). */
    var liveEpgShift: Map<String, Int>
        get() = readStringMap("live_epg_shift_v1").mapNotNull { (k, v) -> v.toIntOrNull()?.takeIf { it in -24..24 }?.let { k to it } }.toMap()
        set(value) { writeStringMap("live_epg_shift_v1", value.mapValues { it.value.toString() }) }

    /** Desloca o guia de um canal em horas (passo 1 h, -24..+24; 0 limpa). */
    fun setLiveEpgShift(channelId: String, hours: Int) {
        if (channelId.isBlank()) return
        val m = liveEpgShift.toMutableMap()
        if (hours == 0) m.remove(channelId) else m[channelId] = hours.coerceIn(-24, 24)
        liveEpgShift = m
    }

    /** Logo próprio de um canal (id → URL). Vazio = a logo da lista. */
    var liveChannelLogos: Map<String, String>
        get() = readStringMap("live_channel_logos_v1")
        set(value) { writeStringMap("live_channel_logos_v1", value) }

    /** Troca a logo de um canal (URL em branco volta à logo da lista). */
    fun setLiveChannelLogo(channelId: String, url: String) {
        if (channelId.isBlank()) return
        val m = liveChannelLogos.toMutableMap()
        if (url.isBlank()) m.remove(channelId) else m[channelId] = url.trim()
        liveChannelLogos = m
    }

    // ── MDBList / AniList / TheTVDB (v1.17.0) ───────────────────────────────
    // Mesmo padrão do Trakt: chave de conta é DO APARELHO (a chave de API
    // ou o client ID é um só p/ o aparelho), sessão e toggles são POR
    // PERFIL (sk). Os nomes das chaves são os dos companions dos serviços
    // (MDBListService.PREF_*, AniListService.PREF_*, TvdbService.PREF_*),
    // repetidos aqui como literais, no mesmo padrão dos vars do Trakt —
    // se um lado mudar, o outro tem que mudar junto. Nenhum destes campos
    // tem valor padrão embutido; vazio = serviço desligado. As chaves
    // antigas da Fase D ("mdblist_key_v1", "anilist_v1") continuam onde
    // estão, inertes: os serviços novos NÃO as leem.

    /** Chave de API do MDBList, colada pelo usuário. Vazio = desligado. */
    var mdbListApiKey: String
        get() = prefs.getString("mdblist_api_key", "") ?: ""
        set(value) { prefs.edit().putString("mdblist_api_key", value.trim()).apply() }

    /** MDBList ligado neste perfil (só vale com chave de API). */
    var mdbListEnabled: Boolean
        get() = prefs.getBoolean(sk("mdblist_enabled"), false)
        set(value) { prefs.edit().putBoolean(sk("mdblist_enabled"), value).apply() }

    /** Fontes de nota do MDBList ativas neste perfil, em CSV. "" = padrão do serviço. */
    var mdbListRatingSources: String
        get() = prefs.getString(sk("mdblist_rating_sources"), "") ?: ""
        set(value) { prefs.edit().putString(sk("mdblist_rating_sources"), value).apply() }

    /** Client ID do AniList, colado pelo usuário. Vazio = desligado. */
    var anilistClientId: String
        get() = prefs.getString("anilist_client_id", "") ?: ""
        set(value) { prefs.edit().putString("anilist_client_id", value.trim()).apply() }

    /** Access token do AniList do perfil. Vazio sem login. */
    var anilistAccessToken: String
        get() = prefs.getString(sk("anilist_access_token"), "") ?: ""
        set(value) { prefs.edit().putString(sk("anilist_access_token"), value).apply() }

    /** AniList ligado neste perfil (só vale com login). */
    var anilistEnabled: Boolean
        get() = prefs.getBoolean(sk("anilist_enabled"), false)
        set(value) { prefs.edit().putBoolean(sk("anilist_enabled"), value).apply() }

    /** Chave de API do TheTVDB, colada pelo usuário. Vazio = desligado. */
    var tvdbApiKey: String
        get() = prefs.getString("tvdb_api_key", "") ?: ""
        set(value) { prefs.edit().putString("tvdb_api_key", value.trim()).apply() }

    /** Token de auth do TheTVDB, cacheado (renovado pelo serviço). Vazio sem login. */
    var tvdbAuthToken: String
        get() = prefs.getString("tvdb_auth_token", "") ?: ""
        set(value) { prefs.edit().putString("tvdb_auth_token", value).apply() }

    /** Expiração do token do TheTVDB (ms desde epoch). 0 = sem token. */
    var tvdbTokenExpiry: Long
        get() = prefs.getLong("tvdb_token_expiry", 0L)
        set(value) { prefs.edit().putLong("tvdb_token_expiry", value).apply() }

    /** Ordem de episódios do TheTVDB neste perfil: "official", "dvd" ou "absolute". */
    var tvdbOrdering: String
        get() = prefs.getString(sk("tvdb_episode_ordering"), "official") ?: "official"
        set(value) { prefs.edit().putString(sk("tvdb_episode_ordering"), value).apply() }

    /** Ordenação do TheTVDB ligada neste perfil (só vale com chave de API). */
    var tvdbOrderingEnabled: Boolean
        get() = prefs.getBoolean(sk("tvdb_ordering_enabled"), false)
        set(value) { prefs.edit().putBoolean(sk("tvdb_ordering_enabled"), value).apply() }

    /** Avaliação pós-filme ligada neste perfil (o diálogo é de outra frente). */
    var postPlayRatingEnabled: Boolean
        get() = prefs.getBoolean(sk("post_play_rating_enabled"), false)
        set(value) { prefs.edit().putBoolean(sk("post_play_rating_enabled"), value).apply() }

    // ── Lote 10 — VPN e Watch Party (v1.20.0, Frente 3) ──────────────────
    // Perfis de VPN importados pelo usuário (ver VpnProfileStore): o JSON
    // de `vpn_profiles_v1` guarda metadados + segredos e é chave SECRETA
    // (só viaja no backup/sync com o toggle de segredos). O escopo vive
    // DENTRO de cada perfil ("app" = só o AOTV, "device" = todo o aparelho).
    // A sessão da Watch Party (`party_session_v1`) é local e efêmera.

    /** Perfis de VPN (JSON array de VpnProfile, com segredos embutidos). "[]" = nenhum perfil. */
    var vpnProfilesJson: String
        get() = prefs.getString(sk("vpn_profiles_v1"), "[]") ?: "[]"
        set(value) { prefs.edit().putString(sk("vpn_profiles_v1"), value).apply() }

    /** Id do perfil de VPN ativo. "" = nenhum. */
    var vpnActiveProfileId: String
        get() = prefs.getString(sk("vpn_active_profile_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("vpn_active_profile_v1"), value).apply() }

    /** Ligar o perfil de VPN ativo ao abrir o app. Só vale quando a conexão existir (sem motor, sem efeito). */
    var vpnAutoConnect: Boolean
        get() = prefs.getBoolean(sk("vpn_auto_connect_v1"), false)
        set(value) { prefs.edit().putBoolean(sk("vpn_auto_connect_v1"), value).apply() }

    /** Sessão atual da Watch Party (JSON {"code","role","createdAt"}). "" = sem sessão. */
    var partySessionJson: String
        get() = prefs.getString(sk("party_session_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("party_session_v1"), value).apply() }

    // ── Fase D — trackers, Discord e servidores de mídia (por perfil) ─────────
    // Sessões em JSON cru, como o Trakt. IDs/chaves são configuráveis pelo usuário
    // em Configurações e NUNCA vão para o código (regra dura do projeto): vazio =
    // serviço desligado. Nenhum destes campos tem valor padrão embutido.
    /** Sessão do SIMKL do perfil (JSON cru: access/refresh/expires/user). */
    var simklSession: String
        get() = prefs.getString(sk("simkl_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("simkl_v1"), value).apply() }

    /** Client ID do SIMKL, colado pelo usuário em Configurações. Vazio = desligado. */
    var simklClientId: String
        get() = prefs.getString(sk("simkl_client_id_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("simkl_client_id_v1"), value.trim()).apply() }

    /** Chave de API do MDBList, colada pelo usuário. Vazio = desligado. */
    var mdblistApiKey: String
        get() = prefs.getString(sk("mdblist_key_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("mdblist_key_v1"), value.trim()).apply() }

    /** Chave de API do OMDb, colada pelo usuário. Vazio = desligado. */
    var omdbApiKey: String
        get() = prefs.getString(sk("omdb_key_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("omdb_key_v1"), value.trim()).apply() }

    /** Token do AniList do perfil (JSON cru). Vazio sem login. */
    var anilistSession: String
        get() = prefs.getString(sk("anilist_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("anilist_v1"), value).apply() }

    /** Token do WeTrakr do perfil (JSON cru). Vazio sem login. */
    var wetrakrSession: String
        get() = prefs.getString(sk("wetrakr_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("wetrakr_v1"), value).apply() }

    /** Token do PublicMetaDB do perfil (JSON cru). Vazio sem login. */
    var publicMetaDbSession: String
        get() = prefs.getString(sk("publicmetadb_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("publicmetadb_v1"), value).apply() }

    /** Sessão do Discord do perfil (JSON cru). Vazio sem login. */
    var discordSession: String
        get() = prefs.getString(sk("discord_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("discord_v1"), value).apply() }

    /** Servidor Jellyfin do perfil (JSON cru: url/userId/token/user). Vazio sem servidor. */
    var jellyfinSession: String
        get() = prefs.getString(sk("jellyfin_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("jellyfin_v1"), value).apply() }

    /** Servidor Emby do perfil (JSON cru: url/userId/token/user). Vazio sem servidor. */
    var embySession: String
        get() = prefs.getString(sk("emby_v1"), "") ?: ""
        set(value) { prefs.edit().putString(sk("emby_v1"), value).apply() }

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

    // ── Tema personalizado (Lote 3, v1.11.0) — do aparelho, como o tema ─────
    // As 6 cores editáveis (ver CustomThemeColors); o resto da paleta é
    // derivado delas em derivedCustomPalette. Editar aqui já avisa a
    // MainActivity, então com o Personalizado selecionado a mudança aparece
    // na hora, sem reabrir nada. NÃO vai p/ SCOPED_KEYS: tema é do aparelho,
    // não do perfil.

    private val _customTheme = MutableStateFlow(
        CustomThemeColors.fromJson(prefs.getString(KEY_CUSTOM_THEME, null)),
    )
    /** As 6 cores editáveis do tema personalizado, que avisam quem observa. */
    val customThemeFlow: StateFlow<CustomThemeColors> = _customTheme.asStateFlow()

    /** As 6 cores editáveis do tema personalizado. Gravar aqui já avisa quem observa. */
    var customTheme: CustomThemeColors
        get() = _customTheme.value
        set(value) { prefs.edit().putString(KEY_CUSTOM_THEME, value.toJson()).apply(); _customTheme.value = value }

    /** Troca uma das 6 cores editáveis (chave desconhecida = sem mudança). */
    fun updateCustomThemeColor(key: String, argb: Int) {
        customTheme = customTheme.withColor(key, argb)
    }

    /** Volta as 6 cores editáveis p/ as do tema Padrão. */
    fun resetCustomTheme() {
        customTheme = CustomThemeColors()
    }

    // ── Blur nos painéis (Lote 3, v1.11.0) — do aparelho, como o tema ───────
    // Liga o blur real (RenderEffect, API 31+) atrás das folhas do detalhe
    // (fontes e filmografia). Padrão LIGADO: em aparelho abaixo do Android 12
    // não há efeito (só o scrim escuro), então ligar por padrão não muda nada
    // p/ quem não tem o recurso. NÃO vai p/ SCOPED_KEYS: é do aparelho.

    private val _panelBlur = MutableStateFlow(prefs.getBoolean(KEY_PANEL_BLUR, true))
    /** Blur nos painéis ligado, que avisa quem observa. */
    val panelBlurFlow: StateFlow<Boolean> = _panelBlur.asStateFlow()

    /** Blur nos painéis ligado. Gravar aqui já avisa quem observa. */
    var panelBlur: Boolean
        get() = _panelBlur.value
        set(value) { prefs.edit().putBoolean(KEY_PANEL_BLUR, value).apply(); _panelBlur.value = value }

    // ── Ícone do app (Lote 11, v1.21.0) — do aparelho, como o tema ────────
    // Variante de ícone colorido (ver AppIconVariants): o ícone é do
    // aparelho, não do perfil — trocar vale p/ todo mundo que usa o aparelho.
    // "default" é o ícone atual (fundo preto, wordmark vermelho + turquesa).
    // NÃO vai p/ SCOPED_KEYS: é global e viaja no backup (ver
    // AotvBackup.GLOBAL_KEYS). A troca em si (liga/desliga os activity-alias)
    // mora em [AppIconVariants.applyVariant].

    /** Id da variante de ícone ativa ("default" = o ícone atual). */
    var appIconVariant: String
        get() = prefs.getString("app_icon_variant", AppIconVariants.DEFAULT_ID) ?: AppIconVariants.DEFAULT_ID
        set(value) {
            val clean = if (AppIconVariants.byId(value) != null) value else AppIconVariants.DEFAULT_ID
            prefs.edit().putString("app_icon_variant", clean).apply()
        }

    // ── Ajuste inicial (Lote 11, v1.21.0) — do aparelho ───────────────────
    // O auto-ajuste da primeira abertura só pergunta uma vez: true depois
    // que a sugestão foi mostrada (aplicada ou dispensada) ou quando não
    // havia nada a sugerir. NÃO vai p/ SCOPED_KEYS nem p/ o backup: num
    // aparelho novo a medição é outra e a sugestão vale ser refeita.

    /** O ajuste inicial da primeira abertura já foi oferecido (ou não havia o que sugerir). */
    var tuningSuggested: Boolean
        get() = prefs.getBoolean("tuning_suggested_v1", false)
        set(value) { prefs.edit().putBoolean("tuning_suggested_v1", value).apply() }

    // ── Backup e restauração (v1.13.0) ───────────────────────────────────────
    // Títulos removidos do Continuar assistindo: segurar o card esconde o
    // título por perfil, para um backup antigo (ou a sincronia) não trazer
    // ele de volta. Assistir de novo libera o progresso (ver
    // LibraryRepository.recordPlayback/saveProgress).

    /** Chave estável de um título no oculto: "TIPO:tmdbId". */
    fun continueKey(tmdbId: Int, type: String) = "$type:$tmdbId"

    private val _hiddenContinue = MutableStateFlow(readStringList("hidden_continue_v1").toSet())
    /** Títulos ocultos do Continuar assistindo no perfil ativo, que avisam quem observa. */
    val hiddenContinueFlow: StateFlow<Set<String>> = _hiddenContinue.asStateFlow()

    /** Títulos ocultos do Continuar assistindo no perfil ativo. */
    var hiddenContinue: Set<String>
        get() = _hiddenContinue.value
        set(value) { writeStringList("hidden_continue_v1", value.sorted()); _hiddenContinue.value = value }

    /** Esconde um título do Continuar assistindo (long-press no card). */
    fun hideContinue(tmdbId: Int, type: String) { hiddenContinue = hiddenContinue + continueKey(tmdbId, type) }

    /** Libera o título (assistir de novo chama isto via LibraryRepository). */
    fun unhideContinue(tmdbId: Int, type: String) { hiddenContinue = hiddenContinue - continueKey(tmdbId, type) }

    /** O título está oculto do Continuar assistindo neste perfil? */
    fun isContinueHidden(tmdbId: Int, type: String): Boolean = continueKey(tmdbId, type) in hiddenContinue

    /** Incluir chaves e sessões no backup. DESLIGADO por padrão (ver BackupViewModel). */
    var backupIncludeSecrets: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_INCLUDE_SECRETS, false)
        set(value) { prefs.edit().putBoolean(KEY_BACKUP_INCLUDE_SECRETS, value).apply() }

    // ── Sync via Google Drive (Lote 10, v1.20.0) — do aparelho ──────────
    // Client ID configurável, vazio por padrão (ver DriveSyncService): sem
    // ele a função fica desligada e o backup local/Wi-Fi continua. A conta,
    // o id do arquivo e as datas do último sync são do aparelho (não do
    // perfil) e viajam no backup (ver AotvBackup.GLOBAL_KEYS); o client ID
    // é segredo (ver AotvBackup.SECRET_GLOBAL_KEYS) e só viaja com o toggle
    // "Incluir chaves e sessões" — mesmo precedente do client ID do Trakt.

    /** Client ID OAuth do Google Drive ("" = sync desligado). */
    var driveClientId: String
        get() = prefs.getString(KEY_DRIVE_CLIENT_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DRIVE_CLIENT_ID, value.trim()).apply() }

    /** E-mail da conta Google conectada ("" = desconectado). */
    var driveAccountEmail: String
        get() = prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DRIVE_ACCOUNT_EMAIL, value).apply() }

    /** Id do arquivo de backup no Drive ("" = ainda não subiu nada). */
    var driveFileId: String
        get() = prefs.getString(KEY_DRIVE_FILE_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DRIVE_FILE_ID, value).apply() }

    /** Último sync com o Drive, em epoch ms (0 = nunca). */
    var driveLastSyncAt: Long
        get() = prefs.getLong(KEY_DRIVE_LAST_SYNC_AT, 0L)
        set(value) { prefs.edit().putLong(KEY_DRIVE_LAST_SYNC_AT, value).apply() }

    /** `modifiedTime` do arquivo remoto no último sync, em epoch ms (0 = nunca). */
    var driveLastRemoteModifiedMs: Long
        get() = prefs.getLong(KEY_DRIVE_LAST_REMOTE_MODIFIED, 0L)
        set(value) { prefs.edit().putLong(KEY_DRIVE_LAST_REMOTE_MODIFIED, value).apply() }

    // ── Downloads e dados móveis (Lote 5, v1.14.0) — do aparelho ──────────
    // "Só Wi-Fi", pasta SAF e contadores de dados móveis. Tudo global: a fila
    // de downloads e a rede são do aparelho, não do perfil (ver as chaves no
    // companion). Os contadores só somam o que o app MEDIU de fato (downloads
    // .mp4 via DownloadManager, enquanto a rede é móvel — ver MobileDataUsage);
    // streaming do player, torrents e API/pôsteres NÃO entram na conta.

    private val _downloadsWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOADS_WIFI_ONLY, false))
    /** "Só Wi-Fi" nos downloads ligado, que avisa quem observa. */
    val downloadsWifiOnlyFlow: StateFlow<Boolean> = _downloadsWifiOnly.asStateFlow()
    /** "Só Wi-Fi" nos downloads. Ligado, download novo em dados móveis entra na fila ("queued") em vez de baixar. Padrão DESLIGADO: tudo funciona como antes. */
    var downloadsWifiOnly: Boolean
        get() = _downloadsWifiOnly.value
        set(value) { prefs.edit().putBoolean(KEY_DOWNLOADS_WIFI_ONLY, value).apply(); _downloadsWifiOnly.value = value }

    // Lote 12 — Frente 3 (Notas e atualizador, v1.22.0): checagem de
    // atualização do app (releases do GitHub). Tudo GLOBAL (do aparelho,
    // não do perfil; ver AotvBackup.GLOBAL_KEYS): a versão instalada é uma
    // só p/ o aparelho. Nada aqui é segredo.
    private val _appUpdateCheckEnabled = MutableStateFlow(prefs.getBoolean("app_update_check_enabled", true))
    /** Checagem automática de atualização ligada. Padrão: ligada. */
    val appUpdateCheckEnabledFlow: StateFlow<Boolean> = _appUpdateCheckEnabled.asStateFlow()
    var appUpdateCheckEnabled: Boolean
        get() = _appUpdateCheckEnabled.value
        set(value) { prefs.edit().putBoolean("app_update_check_enabled", value).apply(); _appUpdateCheckEnabled.value = value }

    private val _appUpdateLastCheck = MutableStateFlow(prefs.getLong("app_update_last_check_v1", 0L))
    /** Última checagem de atualização (ms desde epoch). 0 = nunca checado. */
    val appUpdateLastCheckFlow: StateFlow<Long> = _appUpdateLastCheck.asStateFlow()
    var appUpdateLastCheck: Long
        get() = _appUpdateLastCheck.value
        set(value) { prefs.edit().putLong("app_update_last_check_v1", value).apply(); _appUpdateLastCheck.value = value }

    private val _downloadFolderUri = MutableStateFlow(prefs.getString(KEY_DOWNLOADS_FOLDER_URI, "") ?: "")
    /** URI SAF da pasta de download, que avisa quem observa. "" = pasta do app (padrão). */
    val downloadFolderUriFlow: StateFlow<String> = _downloadFolderUri.asStateFlow()
    /** URI SAF da pasta de download (ACTION_OPEN_DOCUMENT_TREE, permissão persistida). "" = pasta do app. */
    var downloadFolderUri: String
        get() = _downloadFolderUri.value
        set(value) { prefs.edit().putString(KEY_DOWNLOADS_FOLDER_URI, value).apply(); _downloadFolderUri.value = value }

    private val _downloadFolderName = MutableStateFlow(prefs.getString(KEY_DOWNLOADS_FOLDER_NAME, "") ?: "")
    /** Nome de exibição da pasta de download, que avisa quem observa. */
    val downloadFolderNameFlow: StateFlow<String> = _downloadFolderName.asStateFlow()
    /** Nome de exibição da pasta de download (só p/ mostrar na tela). */
    var downloadFolderName: String
        get() = _downloadFolderName.value
        set(value) { prefs.edit().putString(KEY_DOWNLOADS_FOLDER_NAME, value).apply(); _downloadFolderName.value = value }

    // ── Lote 8 — Esportes, Gravações e Calendário (v1.18.0) ───────────────
    // Tudo por perfil (SCOPED_KEYS), menos a pasta de gravações, que é do
    // aparelho como a de downloads. NENHUMA destas prefs inventa dado: sem
    // EPG não há jogo, sem placar no texto do EPG não há placar na tela
    // (ver SportsScores), sem air_date não há episódio no calendário.

    private val _sportsShow = MutableStateFlow(prefs.getBoolean(sk("sports_show"), true))
    /** Mostrar a seção de esportes no Ao vivo e os jogos na fileira "Próximos". */
    val sportsShowFlow: StateFlow<Boolean> = _sportsShow.asStateFlow()
    var sportsShow: Boolean
        get() = _sportsShow.value
        set(value) { prefs.edit().putBoolean(sk("sports_show"), value).apply(); _sportsShow.value = value }

    // ── Lote 10 — Servidores de mídia (v1.20.0) ─────────────────────────────
    private val _mediaServersShow = MutableStateFlow(prefs.getBoolean(sk("media_servers_show_v1"), true))
    /** Mostrar o atalho dos servidores de mídia (Jellyfin/Emby/Plex) na Biblioteca. */
    val mediaServersShowFlow: StateFlow<Boolean> = _mediaServersShow.asStateFlow()
    var mediaServersShow: Boolean
        get() = _mediaServersShow.value
        set(value) { prefs.edit().putBoolean(sk("media_servers_show_v1"), value).apply(); _mediaServersShow.value = value }

    private val _sportsHideScores = MutableStateFlow(prefs.getBoolean(sk("sports_hide_scores"), false))
    /** Esconder os placares extraídos do EPG (anti-spoiler). */
    val sportsHideScoresFlow: StateFlow<Boolean> = _sportsHideScores.asStateFlow()
    var sportsHideScores: Boolean
        get() = _sportsHideScores.value
        set(value) { prefs.edit().putBoolean(sk("sports_hide_scores"), value).apply(); _sportsHideScores.value = value }

    private val _sportsScoreDelaySec = MutableStateFlow(prefs.getInt(sk("sports_score_delay_sec"), 30))
    /** Atraso p/ mostrar o placar extraído, em segundos (anti-spoiler). */
    val sportsScoreDelaySecFlow: StateFlow<Int> = _sportsScoreDelaySec.asStateFlow()
    var sportsScoreDelaySec: Int
        get() = _sportsScoreDelaySec.value
        set(value) { prefs.edit().putInt(sk("sports_score_delay_sec"), value.coerceIn(0, 600)).apply(); _sportsScoreDelaySec.value = value.coerceIn(0, 600) }

    private val _sportsLiveScores = MutableStateFlow(prefs.getBoolean(sk("sports_live_scores"), true))
    /** Placares ao vivo nos canais de esporte (só o que o EPG traz no texto). */
    val sportsLiveScoresFlow: StateFlow<Boolean> = _sportsLiveScores.asStateFlow()
    var sportsLiveScores: Boolean
        get() = _sportsLiveScores.value
        set(value) { prefs.edit().putBoolean(sk("sports_live_scores"), value).apply(); _sportsLiveScores.value = value }

    private val _sportsGameAlerts = MutableStateFlow(prefs.getBoolean(sk("sports_game_alerts"), false))
    /** Alertar quando um jogo de esporte começa (notificação local, só com o app aberto — ver RecordingReminders). */
    val sportsGameAlertsFlow: StateFlow<Boolean> = _sportsGameAlerts.asStateFlow()
    var sportsGameAlerts: Boolean
        get() = _sportsGameAlerts.value
        set(value) { prefs.edit().putBoolean(sk("sports_game_alerts"), value).apply(); _sportsGameAlerts.value = value }

    // ── Lote 13 — Esportes parte 2 (v1.23.0): Fix Channel ─────────────────
    // Override jogo/rede/time → canal (ver FixChannel.kt): quando o canal
    // achado p/ um jogo está errado, o usuário escolhe o certo uma vez e o
    // app lembra. Mapa JSON (chave "game:<id>" / "network:<rede>" /
    // "team:<time>" → {c: id do canal, n: nome do canal, t: quando}),
    // no mesmo padrão de "live_audio_v1". No máximo 100 (os mais recentes);
    // o resto cai no cortar por [at]. Por perfil (SCOPED_KEYS
    // "sports_fix_channel_v1"). Sem fluxo: só a tela de esportes lê e grava.
    var sportsChannelFixes: Map<String, com.aotv.app.data.sports.ChannelFix>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(sk("sports_fix_channel_v1"), null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                val scope = runCatching { com.aotv.app.data.sports.FixScope.valueOf(v.optString("s")) }.getOrDefault(com.aotv.app.data.sports.FixScope.GAME)
                val key = v.optString("k")
                com.aotv.app.data.sports.ChannelFix(scope, key, v.optString("c"), v.optString("n"), v.optLong("t", 0L))
            }.filterValues { it.channelId.isNotBlank() && it.key.isNotBlank() }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.entries.sortedByDescending { it.value.at }.take(100).forEach { (k, fix) ->
                o.put(k, org.json.JSONObject().put("s", fix.scope.name).put("k", fix.key).put("c", fix.channelId).put("n", fix.channelName).put("t", fix.at))
            }
            prefs.edit().putString(sk("sports_fix_channel_v1"), o.toString()).apply()
        }

    /** Guarda um override de canal (Fix Channel). */
    fun setChannelFix(fix: com.aotv.app.data.sports.ChannelFix) {
        if (fix.channelId.isBlank() || fix.key.isBlank()) return
        sportsChannelFixes = sportsChannelFixes + (com.aotv.app.data.sports.fixMapKey(fix.scope, fix.key) to fix)
    }

    /** Apaga um override de canal (pela chave do mapa). */
    fun removeChannelFix(mapKey: String) {
        if (mapKey.isBlank()) return
        sportsChannelFixes = sportsChannelFixes - mapKey
    }

    // ── Lote 15 — Aba de Esportes (v1.25.0, Bloco 1) ─────────────────────
    // Ligas ligadas no seletor da aba ("sports_tab_leagues_v1", lista de ids
    // de SPORTS_LEAGUES): null (chave ausente) = TODAS ligadas; lista vazia
    // = nenhuma ligada. Ligas/times seguidos ("sports_followed_*"): vazio
    // = nada seguido — "Meus esportes" começa vazio, com empty state
    // honesto; nenhum default inventado (nada de "futebol BR + NHL").
    // Listas de nomes/ids do usuário — nada embutido, nada de fora.
    var sportsTabEnabledLeagues: List<String>?
        get() = if (prefs.contains(sk("sports_tab_leagues_v1"))) readStringList("sports_tab_leagues_v1") else null
        set(value) {
            if (value == null) prefs.edit().remove(sk("sports_tab_leagues_v1")).apply()
            else writeStringList("sports_tab_leagues_v1", value.distinct())
        }

    /** Ids das ligas ligadas no seletor (só as que o app conhece). */
    fun sportsEnabledLeagueIds(allIds: List<String>): List<String> {
        val enabled = sportsTabEnabledLeagues ?: return allIds
        return enabled.filter { it in allIds }
    }

    /** Liga/desliga uma liga no seletor (volta a null = todas quando completa). */
    fun setSportsLeagueEnabled(leagueId: String, enabled: Boolean, allIds: List<String>) {
        if (leagueId.isBlank()) return
        val current = sportsEnabledLeagueIds(allIds).toMutableSet()
        if (enabled) current += leagueId else current -= leagueId
        sportsTabEnabledLeagues = if (current.size == allIds.size) null else current.toList()
    }

    /** Ligas seguidas (ids de SPORTS_LEAGUES). Vazio = nenhuma seguida. */
    var sportsFollowedLeagues: List<String>
        get() = readStringList("sports_followed_leagues_v1")
        set(value) { writeStringList("sports_followed_leagues_v1", value.distinct()) }

    /** Times seguidos (nomes como a ESPN traz). Vazio = nenhum seguido. */
    var sportsFollowedTeams: List<String>
        get() = readStringList("sports_followed_teams_v1")
        set(value) { writeStringList("sports_followed_teams_v1", value.distinct()) }

    /** Seguir/deixar de seguir uma liga. */
    fun toggleSportsFollowedLeague(leagueId: String) {
        if (leagueId.isBlank()) return
        val cur = sportsFollowedLeagues
        sportsFollowedLeagues = if (leagueId in cur) cur - leagueId else cur + leagueId
    }

    /** Seguir/deixar de seguir um time. */
    fun toggleSportsFollowedTeam(team: String) {
        val clean = team.trim()
        if (clean.isBlank()) return
        val cur = sportsFollowedTeams
        sportsFollowedTeams = if (clean in cur) cur - clean else cur + clean
    }

    private val _recordStartEarlyMin = MutableStateFlow(prefs.getInt(sk("record_start_early_min"), 2))
    /** Começar a gravação este tanto de minutos ANTES do programa. */
    val recordStartEarlyMinFlow: StateFlow<Int> = _recordStartEarlyMin.asStateFlow()
    var recordStartEarlyMin: Int
        get() = _recordStartEarlyMin.value
        set(value) { prefs.edit().putInt(sk("record_start_early_min"), value.coerceIn(0, 30)).apply(); _recordStartEarlyMin.value = value.coerceIn(0, 30) }

    private val _recordRunLateMin = MutableStateFlow(prefs.getInt(sk("record_run_late_min"), 5))
    /** Continuar gravando este tanto de minutos DEPOIS do fim do programa. */
    val recordRunLateMinFlow: StateFlow<Int> = _recordRunLateMin.asStateFlow()
    var recordRunLateMin: Int
        get() = _recordRunLateMin.value
        set(value) { prefs.edit().putInt(sk("record_run_late_min"), value.coerceIn(0, 60)).apply(); _recordRunLateMin.value = value.coerceIn(0, 60) }

    private val _recordKeepEpisodes = MutableStateFlow(prefs.getInt(sk("record_keep_episodes"), 5))
    /** Nº de gravações a manter; acima disso apaga a mais antiga. */
    val recordKeepEpisodesFlow: StateFlow<Int> = _recordKeepEpisodes.asStateFlow()
    var recordKeepEpisodes: Int
        get() = _recordKeepEpisodes.value
        set(value) { prefs.edit().putInt(sk("record_keep_episodes"), value.coerceIn(1, 50)).apply(); _recordKeepEpisodes.value = value.coerceIn(1, 50) }

    private val _recordLowSpaceMb = MutableStateFlow(prefs.getInt(sk("record_low_space_mb"), 1024))
    /** Avisar (e parar a gravação) quando o espaço livre ficar abaixo disto, em MB. */
    val recordLowSpaceMbFlow: StateFlow<Int> = _recordLowSpaceMb.asStateFlow()
    var recordLowSpaceMb: Int
        get() = _recordLowSpaceMb.value
        set(value) { prefs.edit().putInt(sk("record_low_space_mb"), value.coerceIn(0, 10240)).apply(); _recordLowSpaceMb.value = value.coerceIn(0, 10240) }

    private val _recordNotifications = MutableStateFlow(prefs.getBoolean(sk("record_notifications"), true))
    /** Notificar início, fim e falha da gravação (precisa da permissão de notificação). */
    val recordNotificationsFlow: StateFlow<Boolean> = _recordNotifications.asStateFlow()
    var recordNotifications: Boolean
        get() = _recordNotifications.value
        set(value) { prefs.edit().putBoolean(sk("record_notifications"), value).apply(); _recordNotifications.value = value }

    private val _calendarHideSpoilers = MutableStateFlow(prefs.getBoolean(sk("calendar_hide_spoilers"), false))
    /** Esconder título/sinopse do episódio até ele ir ao ar (fileira "Próximos" e lembretes). */
    val calendarHideSpoilersFlow: StateFlow<Boolean> = _calendarHideSpoilers.asStateFlow()
    var calendarHideSpoilers: Boolean
        get() = _calendarHideSpoilers.value
        set(value) { prefs.edit().putBoolean(sk("calendar_hide_spoilers"), value).apply(); _calendarHideSpoilers.value = value }

    private val _calendarAutoAddTrackers = MutableStateFlow(prefs.getBoolean(sk("calendar_auto_add_trackers"), true))
    /**
     * Auto-add do calendário (Lote 13, v1.23.0): séries acompanhadas no Trakt/Simkl
     * entram no calendário e nas fileiras "Novos episódios"/"Próximos" sozinhas,
     * sem estar na Minha Lista. Ligado por padrão (é o ponto do recurso); desligar
     * volta ao conjunto local. Por perfil (SCOPED_KEYS "calendar_auto_add_trackers").
     */
    val calendarAutoAddTrackersFlow: StateFlow<Boolean> = _calendarAutoAddTrackers.asStateFlow()
    var calendarAutoAddTrackers: Boolean
        get() = _calendarAutoAddTrackers.value
        set(value) { prefs.edit().putBoolean(sk("calendar_auto_add_trackers"), value).apply(); _calendarAutoAddTrackers.value = value }

    /**
     * "Não hoje" (Lote 13, v1.23.0): tmdbId -> data local (yyyy-MM-dd) em que o
     * episódio novo da série foi dispensado. O cartão some até amanhã — a data
     * guardada é a de hoje, então amanhã ele reaparece sozinho. Mapa lido do
     * disco a cada acesso (como os mapas de canal), sem fluxo. Por perfil
     * (SCOPED_KEYS "not_today_v1").
     */
    private var notTodayMap: Map<String, String>
        get() = readStringMap("not_today_v1")
        set(value) { writeStringMap("not_today_v1", value) }

    /** O episódio novo desta série foi dispensado hoje ("Não hoje"). */
    fun isNotToday(tmdbId: Int): Boolean =
        tmdbId > 0 && notTodayMap[tmdbId.toString()] == dayString()

    /** Dispensa o episódio novo desta série até amanhã. */
    fun markNotToday(tmdbId: Int) {
        if (tmdbId <= 0) return
        val m = notTodayMap.toMutableMap()
        m[tmdbId.toString()] = dayString()
        notTodayMap = m
    }

    // ── Blur anti-spoiler (Lote 11, v1.21.0) ───────────────────────────────
    // Borra a arte e esconde título/sinopse dos episódios NÃO assistidos no
    // detalhe da série (mesma lógica de "não assistido = sem spoiler visível"
    // da pref `calendar_hide_spoilers` do Lote 8). Episódio visto ou em
    // andamento nunca é borrado. Desligado por padrão (opt-in). Reaproveita
    // o blur do Lote 3/4 (RenderEffect no Android 12+, véu escuro abaixo).
    private val _detailSpoilerBlur = MutableStateFlow(prefs.getBoolean(sk("pref_detail_spoiler_blur"), false))
    /** Blur anti-spoiler nos cards de episódio não assistidos, que avisa quem observa. */
    val detailSpoilerBlurFlow: StateFlow<Boolean> = _detailSpoilerBlur.asStateFlow()
    /** Blur anti-spoiler nos cards de episódio não assistidos. */
    var detailSpoilerBlur: Boolean
        get() = _detailSpoilerBlur.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_spoiler_blur"), value).apply(); _detailSpoilerBlur.value = value }

    private val _recordingsFolderUri = MutableStateFlow(prefs.getString(KEY_RECORDINGS_FOLDER_URI, "") ?: "")
    /** URI SAF da pasta de gravações, que avisa quem observa. "" = pasta do app (padrão). Do aparelho, como a de downloads. */
    val recordingsFolderUriFlow: StateFlow<String> = _recordingsFolderUri.asStateFlow()
    /** URI SAF da pasta de gravações (ACTION_OPEN_DOCUMENT_TREE, permissão persistida). "" = pasta do app. */
    var recordingsFolderUri: String
        get() = _recordingsFolderUri.value
        set(value) { prefs.edit().putString(KEY_RECORDINGS_FOLDER_URI, value).apply(); _recordingsFolderUri.value = value }

    private val _recordingsFolderName = MutableStateFlow(prefs.getString(KEY_RECORDINGS_FOLDER_NAME, "") ?: "")
    /** Nome de exibição da pasta de gravações, que avisa quem observa. */
    val recordingsFolderNameFlow: StateFlow<String> = _recordingsFolderName.asStateFlow()
    /** Nome de exibição da pasta de gravações (só p/ mostrar na tela). */
    var recordingsFolderName: String
        get() = _recordingsFolderName.value
        set(value) { prefs.edit().putString(KEY_RECORDINGS_FOLDER_NAME, value).apply(); _recordingsFolderName.value = value }

    /** Lê o contador de hoje já com a virada do dia (0 quando o dia guardado não é hoje). */
    private fun readMobileToday(): Long =
        if (prefs.getString(KEY_MOBILE_DATA_DAY, "") == MobileDataUsage.dayKey()) prefs.getLong(KEY_MOBILE_DATA_TODAY, 0L) else 0L

    /** Lê o contador do mês já com a virada do mês (0 quando o mês guardado não é o atual). */
    private fun readMobileMonth(): Long =
        if (prefs.getString(KEY_MOBILE_DATA_MONTH_KEY, "") == MobileDataUsage.monthKey()) prefs.getLong(KEY_MOBILE_DATA_MONTH, 0L) else 0L

    private val _mobileDataToday = MutableStateFlow(readMobileToday())
    /** Bytes de dados móveis medidos hoje, que avisam quem observa. */
    val mobileDataTodayFlow: StateFlow<Long> = _mobileDataToday.asStateFlow()

    private val _mobileDataMonth = MutableStateFlow(readMobileMonth())
    /** Bytes de dados móveis medidos no mês, que avisam quem observa. */
    val mobileDataMonthFlow: StateFlow<Long> = _mobileDataMonth.asStateFlow()

    private val _mobileDataLimitMb = MutableStateFlow(prefs.getLong(KEY_MOBILE_DATA_LIMIT_MB, 0L))
    /** Limite mensal de dados móveis em MB, que avisa quem observa. 0 = sem limite. */
    val mobileDataLimitMbFlow: StateFlow<Long> = _mobileDataLimitMb.asStateFlow()
    /** Limite mensal de dados móveis, em MB. 0 = sem limite (só aviso, nunca bloqueia nada). */
    var mobileDataLimitMb: Long
        get() = _mobileDataLimitMb.value
        set(value) {
            val clean = value.coerceAtLeast(0L)
            prefs.edit().putLong(KEY_MOBILE_DATA_LIMIT_MB, clean).apply()
            _mobileDataLimitMb.value = clean
            refreshMobileDataLimitReached()
        }

    private val _mobileDataLimitReached = MutableStateFlow(mobileDataLimitReached())
    /** Limite mensal atingido (mês >= limite), que avisa quem observa. false sem limite. */
    val mobileDataLimitReachedFlow: StateFlow<Boolean> = _mobileDataLimitReached.asStateFlow()

    /** Limite mensal atingido? false sem limite configurado. */
    fun mobileDataLimitReached(): Boolean {
        val limit = prefs.getLong(KEY_MOBILE_DATA_LIMIT_MB, 0L)
        return limit > 0 && readMobileMonth() >= limit * 1024L * 1024L
    }

    private fun refreshMobileDataLimitReached() {
        _mobileDataLimitReached.value = mobileDataLimitReached()
    }

    /** Soma bytes MEDIDOS em dados móveis aos contadores (hoje + mês), com virada de dia/mês. Ignora <= 0. Quem chama já verificou que a rede é móvel (ver DownloadRepository). */
    fun recordMobileBytes(bytes: Long) {
        if (bytes <= 0L) return
        val today = MobileDataUsage.dayKey()
        val month = MobileDataUsage.monthKey()
        val ed = prefs.edit()
        var t = prefs.getLong(KEY_MOBILE_DATA_TODAY, 0L)
        var m = prefs.getLong(KEY_MOBILE_DATA_MONTH, 0L)
        if (prefs.getString(KEY_MOBILE_DATA_DAY, "") != today) {
            t = 0L
            ed.putString(KEY_MOBILE_DATA_DAY, today)
        }
        if (prefs.getString(KEY_MOBILE_DATA_MONTH_KEY, "") != month) {
            m = 0L
            ed.putString(KEY_MOBILE_DATA_MONTH_KEY, month)
        }
        t += bytes
        m += bytes
        ed.putLong(KEY_MOBILE_DATA_TODAY, t).putLong(KEY_MOBILE_DATA_MONTH, m).apply()
        _mobileDataToday.value = t
        _mobileDataMonth.value = m
        refreshMobileDataLimitReached()
    }

    /** Recarrega os contadores com a virada de dia/mês (p/ a tela de Downloads abrir com o número certo). */
    fun refreshMobileDataCounters() {
        _mobileDataToday.value = readMobileToday()
        _mobileDataMonth.value = readMobileMonth()
        refreshMobileDataLimitReached()
    }

    /** Substitui a lista de perfis pela do backup (o hash do PIN só vem com segredos). */
    fun restoreProfiles(list: List<Profile>) {
        if (list.isEmpty()) return
        saveProfiles(list)
        if (_profiles.value.none { it.id == _profileId.value }) selectProfile(list.first().id)
    }

    // ── Acesso cru às prefs (p/ o backup) ────────────────────────────────────
    // O backup lê e grava as prefs cruas (por perfil via SCOPED_KEYS + as
    // globais da lista do BackupViewModel), sem passar pelos vars tipados:
    // são dezenas de chaves e o formato precisa sobreviver a prefs novas
    // sem tocar no backup. O que é segredo (sessões, chaves) só entra com
    // o toggle `backupIncludeSecrets` ligado (ver BackupViewModel).

    /** Valor cru de uma pref do perfil (String/Int/Long/Float/Boolean/Set). null = ausente. */
    fun rawPref(key: String, profileId: String = _profileId.value): Any? =
        prefs.all[scopedKey(key, profileId)]

    /** Grava o valor cru de uma pref do perfil. Tipos aceitos: os do SharedPreferences. */
    fun writeRawPref(key: String, value: Any?, profileId: String = _profileId.value) {
        val k = scopedKey(key, profileId)
        val ed = prefs.edit()
        when (value) {
            null -> ed.remove(k)
            is String -> ed.putString(k, value)
            is Int -> ed.putInt(k, value)
            is Long -> ed.putLong(k, value)
            is Float -> ed.putFloat(k, value)
            is Boolean -> ed.putBoolean(k, value)
            is Set<*> -> ed.putStringSet(k, value.filterIsInstance<String>().toSet())
            else -> return
        }
        ed.apply()
    }

    /** Valor cru de uma pref do aparelho (não é por perfil). null = ausente. */
    fun rawGlobalPref(key: String): Any? = prefs.all[key]

    /** Grava o valor cru de uma pref do aparelho. */
    fun writeRawGlobalPref(key: String, value: Any?) {
        val ed = prefs.edit()
        when (value) {
            null -> ed.remove(key)
            is String -> ed.putString(key, value)
            is Int -> ed.putInt(key, value)
            is Long -> ed.putLong(key, value)
            is Float -> ed.putFloat(key, value)
            is Boolean -> ed.putBoolean(key, value)
            is Set<*> -> ed.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> return
        }
        ed.apply()
    }

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
            // Lote 11 (v1.21.0): selos de qualidade/fonte LIGADOS por padrão
            // (decisão do Davi) — diferente do showPosterBadges da Fase B.
            showQualityBadges = prefs.getBoolean(sk("pref_poster_quality_badges"), true),
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
                .putBoolean(sk("pref_poster_quality_badges"), value.showQualityBadges)
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

    // (v1.10.0, Lote 2 item 5) Mostrar ou ocultar as notas (ratings do TMDB) nos
    // cards de episódio — detalhe e painel de episódios do player. Padrão true:
    // quem não mexer continua vendo as notas como antes. Desligado, a nota some
    // da meta do card E o Rating Map some junto (o mapa É a nota, em cor).
    private val _detailEpisodeRatings = MutableStateFlow(prefs.getBoolean(sk("pref_detail_episode_ratings"), true))
    /** Notas dos episódios visíveis nos cards (e no Rating Map / painel do player). */
    val detailEpisodeRatingsFlow: StateFlow<Boolean> = _detailEpisodeRatings.asStateFlow()
    var detailEpisodeRatings: Boolean
        get() = _detailEpisodeRatings.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_episode_ratings"), value).apply(); _detailEpisodeRatings.value = value }

    // (v1.11.0, Lote 3 item 6) Título em idioma original na página de
    // detalhes — linha secundária abaixo do título traduzido (ou do logo).
    // Padrão false: quem não mexer continua vendo só o título traduzido.
    // Sem dado do TMDB (original_title/original_name vazio) não mostra nada.
    private val _detailOriginalTitle = MutableStateFlow(prefs.getBoolean(sk("pref_detail_original_title"), false))
    /** Título original (TMDB) visível na página de detalhes. */
    val detailOriginalTitleFlow: StateFlow<Boolean> = _detailOriginalTitle.asStateFlow()
    var detailOriginalTitle: Boolean
        get() = _detailOriginalTitle.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_original_title"), value).apply(); _detailOriginalTitle.value = value }

    // Lote 8 — Tela de detalhe (v1.18.0): nota agregada, avaliações do Trakt
    // e frases/ficha (Wikiquote/Wikipédia). Padrão true: quem não mexer
    // continua vendo as seções quando houver dado real para mostrar.
    private val _detailShowAggregateRating = MutableStateFlow(prefs.getBoolean(sk("pref_detail_show_aggregate_rating"), true))
    /** Nota agregada (MDBList) visível na página de detalhes. */
    val detailShowAggregateRatingFlow: StateFlow<Boolean> = _detailShowAggregateRating.asStateFlow()
    var detailShowAggregateRating: Boolean
        get() = _detailShowAggregateRating.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_show_aggregate_rating"), value).apply(); _detailShowAggregateRating.value = value }

    private val _detailShowTraktReviews = MutableStateFlow(prefs.getBoolean(sk("pref_detail_show_trakt_reviews"), true))
    /** Avaliações do Trakt ("O que estão dizendo") visíveis na página de detalhes. */
    val detailShowTraktReviewsFlow: StateFlow<Boolean> = _detailShowTraktReviews.asStateFlow()
    var detailShowTraktReviews: Boolean
        get() = _detailShowTraktReviews.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_show_trakt_reviews"), value).apply(); _detailShowTraktReviews.value = value }

    private val _detailShowQuotes = MutableStateFlow(prefs.getBoolean(sk("pref_detail_show_quotes"), true))
    /** Frases e ficha de produção (Wikiquote/Wikipédia) visíveis na página de detalhes. */
    val detailShowQuotesFlow: StateFlow<Boolean> = _detailShowQuotes.asStateFlow()
    var detailShowQuotes: Boolean
        get() = _detailShowQuotes.value
        set(value) { prefs.edit().putBoolean(sk("pref_detail_show_quotes"), value).apply(); _detailShowQuotes.value = value }

    // Lote 12 — Frente 3 (Notas e atualizador, v1.22.0): fonte da nota
    // exibida de FILMES e SÉRIES no detalhe (e da série no navegador de
    // episódios). "auto" (padrão) = MDBList quando conectado e ligado, senão
    // TMDB; "mdblist" força o MDBList (sem nota p/ o título, o TMDB assume
    // como fallback — nunca se inventa nota); "tmdb" força o TMDB.
    // EPISÓDIOS seguem sempre com TMDB: a API do MDBList não tem nota por
    // episódio (`m` só aceita `movie|show`). Por perfil (SCOPED_KEYS).
    private val _ratingsSource = MutableStateFlow(prefs.getString(sk("pref_ratings_source"), "auto") ?: "auto")
    /** Fonte da nota exibida de filmes e séries: "auto" | "mdblist" | "tmdb". */
    val ratingsSourceFlow: StateFlow<String> = _ratingsSource.asStateFlow()
    var ratingsSource: String
        get() = _ratingsSource.value
        set(value) {
            val clean = if (value in setOf("auto", "mdblist", "tmdb")) value else "auto"
            prefs.edit().putString(sk("pref_ratings_source"), clean).apply(); _ratingsSource.value = clean
        }

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

    /**
     * SEMÂNTICA DE "USAR ADDONS PRIMÁRIOS" (Lote 3, v1.11.0) — espelho do
     * NuvioAccountService (perfil do Nuvio com usesPrimaryAddons resolve os
     * addons com o índice 1, o principal). No AOTV a lista de addons Stremio
     * em si é DO APARELHO (addonEntries, sem escopo por perfil — ver o
     * comentário em SCOPED_KEYS), então não há "addons do perfil" para
     * trocar. O que É por perfil é a lista de FONTES FIXADAS
     * (pref_stream_pinned, Fase C: a ordem/fixação na folha de fontes). Com
     * o toggle ligado, o perfil lê E grava a lista de fixadas do perfil
     * principal em vez da sua — igual ao Nuvio, só que sobre o que de fato
     * é por perfil aqui. O perfil principal com o toggle ligado é no-op
     * (ele já é o primário). A importação da conta Nuvio (que já usa
     * usesPrimaryAddons no servidor) NÃO muda com isto.
     */
    fun pinnedOwnerId(): String =
        if (profile.usePrimaryAddons && _profileId.value != MAIN_PROFILE) MAIN_PROFILE else _profileId.value

    private fun readStringList(key: String): List<String> = readStringListFor(key, _profileId.value)

    private fun readStringListFor(key: String, profileId: String): List<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(scopedKey(key, profileId), "[]"))
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun writeStringList(key: String, value: List<String>) {
        val arr = org.json.JSONArray()
        value.forEach { arr.put(it) }
        prefs.edit().putString(sk(key), arr.toString()).apply()
    }

    // Lote 12 (v1.22.0): mapa canal → texto guardado como objeto JSON
    // (mesmo padrão de "live_audio_v1"). Chave vazia ou valor em branco
    // não entram; leitura tolera JSON quebrado (volta vazio).
    private fun readStringMap(key: String): Map<String, String> = runCatching {
        val o = org.json.JSONObject(prefs.getString(sk(key), null) ?: "{}")
        o.keys().asSequence().associateWith { k -> o.optString(k) }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())

    private fun writeStringMap(key: String, value: Map<String, String>) {
        val o = org.json.JSONObject()
        value.entries.filter { it.key.isNotBlank() && it.value.isNotBlank() }.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(sk(key), o.toString()).apply()
    }

    /** Grava a lista de fixadas no dono efetivo (ver [pinnedOwnerId]). */
    private fun writePinnedList(value: List<String>) {
        val arr = org.json.JSONArray()
        value.forEach { arr.put(it) }
        prefs.edit().putString(scopedKey("pref_stream_pinned", pinnedOwnerId()), arr.toString()).apply()
    }

    private val _streamPinned = MutableStateFlow(readStringListFor("pref_stream_pinned", pinnedOwnerId()))
    /** Addons fixados no topo da folha de fontes, na ordem (nomes de addon). Vazio = nenhum fixado. Com "usar addons primários" ligado, é a lista do perfil principal (ver [pinnedOwnerId]). */
    val streamPinnedFlow: StateFlow<List<String>> = _streamPinned.asStateFlow()
    var streamPinned: List<String>
        get() = _streamPinned.value
        set(value) { writePinnedList(value); _streamPinned.value = value }

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

    private val _playerSwipeSeek = MutableStateFlow(prefs.getBoolean(sk("pref_player_swipe_seek"), true))
    /** Deslizar horizontalmente sobre o vídeo avança/retrocede 10s (Lote 1, v1.9.0). */
    val playerSwipeSeekFlow: StateFlow<Boolean> = _playerSwipeSeek.asStateFlow()
    var playerSwipeSeek: Boolean
        get() = _playerSwipeSeek.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_swipe_seek"), value).apply(); _playerSwipeSeek.value = value }

    private val _playerSpeed = MutableStateFlow(prefs.getFloat(sk("pref_player_speed"), 1f))
    /** Velocidade de reprodução salva do perfil (0,5x–2x; Lote 1, v1.9.0). */
    val playerSpeedFlow: StateFlow<Float> = _playerSpeed.asStateFlow()
    var playerSpeed: Float
        get() = _playerSpeed.value
        set(value) { prefs.edit().putFloat(sk("pref_player_speed"), value).apply(); _playerSpeed.value = value }

    // ── Filtro de correção de cor do player (Lote 1, v1.9.0) — por perfil ──

    private val _playerColorBrightness = MutableStateFlow(prefs.getFloat(sk("pref_player_color_brightness"), 0f))
    /** Brilho do vídeo (−1..1; 0 = sem mudança). */
    val playerColorBrightnessFlow: StateFlow<Float> = _playerColorBrightness.asStateFlow()
    var playerColorBrightness: Float
        get() = _playerColorBrightness.value
        set(value) { prefs.edit().putFloat(sk("pref_player_color_brightness"), value).apply(); _playerColorBrightness.value = value }

    private val _playerColorContrast = MutableStateFlow(prefs.getFloat(sk("pref_player_color_contrast"), 1f))
    /** Contraste do vídeo (0..2; 1 = sem mudança). */
    val playerColorContrastFlow: StateFlow<Float> = _playerColorContrast.asStateFlow()
    var playerColorContrast: Float
        get() = _playerColorContrast.value
        set(value) { prefs.edit().putFloat(sk("pref_player_color_contrast"), value).apply(); _playerColorContrast.value = value }

    private val _playerColorSaturation = MutableStateFlow(prefs.getFloat(sk("pref_player_color_saturation"), 1f))
    /** Saturação do vídeo (0..2; 1 = sem mudança). */
    val playerColorSaturationFlow: StateFlow<Float> = _playerColorSaturation.asStateFlow()
    var playerColorSaturation: Float
        get() = _playerColorSaturation.value
        set(value) { prefs.edit().putFloat(sk("pref_player_color_saturation"), value).apply(); _playerColorSaturation.value = value }

    private val _playerColorHue = MutableStateFlow(prefs.getFloat(sk("pref_player_color_hue"), 0f))
    /** Matiz do vídeo em graus (−180..180; 0 = sem mudança). */
    val playerColorHueFlow: StateFlow<Float> = _playerColorHue.asStateFlow()
    var playerColorHue: Float
        get() = _playerColorHue.value
        set(value) { prefs.edit().putFloat(sk("pref_player_color_hue"), value).apply(); _playerColorHue.value = value }

    private val _playerColorPreset = MutableStateFlow(prefs.getString(sk("pref_player_color_preset"), "padrao") ?: "padrao")
    /** Preset de cor do vídeo ("padrao", "vivo", "cinema", "pb" ou "custom"). */
    val playerColorPresetFlow: StateFlow<String> = _playerColorPreset.asStateFlow()
    var playerColorPreset: String
        get() = _playerColorPreset.value
        set(value) { prefs.edit().putString(sk("pref_player_color_preset"), value).apply(); _playerColorPreset.value = value }

    private val _playerColorDaltonism = MutableStateFlow(prefs.getString(sk("pref_player_color_daltonism"), "off") ?: "off")
    /** Correção p/ daltonismo no vídeo ("off", "deuteranomalia", "protanomalia", "tritanomalia"). */
    val playerColorDaltonismFlow: StateFlow<String> = _playerColorDaltonism.asStateFlow()
    var playerColorDaltonism: String
        get() = _playerColorDaltonism.value
        set(value) { prefs.edit().putString(sk("pref_player_color_daltonism"), value).apply(); _playerColorDaltonism.value = value }

    // ── Motor do player (Lote 4, v1.12.0) — por perfil ─────────────────────
    // Player padrão: "exoplayer" (interno) ou "external" (abre o link
    // resolvido num player instalado, via ACTION_VIEW + seletor do sistema).
    // Libmpv NÃO entra aqui: sem artefato Maven Central (ver PlayerEngine.kt).

    private val _playerEngine = MutableStateFlow(prefs.getString(sk("pref_player_engine"), "exoplayer") ?: "exoplayer")
    /** Player padrão do perfil ("exoplayer" ou "external"). */
    val playerEngineFlow: StateFlow<String> = _playerEngine.asStateFlow()
    var playerEngine: String
        get() = _playerEngine.value
        set(value) { prefs.edit().putString(sk("pref_player_engine"), value).apply(); _playerEngine.value = value }

    // Decoder de vídeo: "hw" (só hardware) ou "hw_fallback" (tenta outro
    // decoder de plataforma se o de hardware falhar ao iniciar). NÃO há
    // modo SW: exigiria a extensão FFmpeg do Media3, não incluída
    // (ver DecoderMode em PlayerEngine.kt).

    private val _playerDecoder = MutableStateFlow(prefs.getString(sk("pref_player_decoder"), "hw") ?: "hw")
    /** Decoder de vídeo do perfil ("hw" ou "hw_fallback"). Vale a partir da próxima reprodução. */
    val playerDecoderFlow: StateFlow<String> = _playerDecoder.asStateFlow()
    var playerDecoder: String
        get() = _playerDecoder.value
        set(value) { prefs.edit().putString(sk("pref_player_decoder"), value).apply(); _playerDecoder.value = value }

    // ── Buffer do player (Lote 5 item 1, v1.14.0) — por perfil ─────────────
    // Perfil de buffer do ExoPlayer ("low", "normal" ou "high", ver
    // [BufferProfile] em PlayerEngine.kt). "normal" é o padrão do ExoPlayer,
    // então quem não mexer continua exatamente como antes. Só vale na
    // criação do player: mudar em Configurações vale a partir da próxima
    // reprodução. Valor desconhecido volta p/ "normal".

    private val _playerBuffer = MutableStateFlow(prefs.getString(sk("pref_player_buffer"), "normal") ?: "normal")
    /** Perfil de buffer do ExoPlayer do perfil ("low", "normal" ou "high"). */
    val playerBufferFlow: StateFlow<String> = _playerBuffer.asStateFlow()
    var playerBuffer: String
        get() = _playerBuffer.value
        set(value) {
            val clean = if (value in listOf("low", "normal", "high")) value else "normal"
            prefs.edit().putString(sk("pref_player_buffer"), clean).apply(); _playerBuffer.value = clean
        }

    // ── Player parte 3 (Lote 9, v1.19.0) — por perfil ───────────────────
    // AutoSync: mede o atraso da legenda externa contra a embutida (ver
    // PlayerAutoSync.kt) — nunca inventa atraso. Pular abertura automático:
    // pula intro/encerramento sozinho ao entrar no segmento (ver
    // PlayerSession.maybeAutoSkip). "A seguir": o cartão no fim do episódio.
    // Equalizador: liga, preset do aparelho ("flat" = tudo em 0 dB) e bandas
    // em dB inteiros separadas por vírgula ("0,3,-2,0,0").

    private val _playerAutoSync = MutableStateFlow(prefs.getBoolean(sk("pref_player_autosync"), false))
    /** Sincronização automática da legenda ligada (botão no painel de Legendas mede o atraso). */
    val playerAutoSyncFlow: StateFlow<Boolean> = _playerAutoSync.asStateFlow()
    var playerAutoSync: Boolean
        get() = _playerAutoSync.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_autosync"), value).apply(); _playerAutoSync.value = value }

    private val _playerAutoSkip = MutableStateFlow(prefs.getBoolean(sk("pref_player_auto_skip"), false))
    /** Pular abertura/encerramento automaticamente ao entrar no segmento. */
    val playerAutoSkipFlow: StateFlow<Boolean> = _playerAutoSkip.asStateFlow()
    var playerAutoSkip: Boolean
        get() = _playerAutoSkip.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_auto_skip"), value).apply(); _playerAutoSkip.value = value }

    private val _playerUpNext = MutableStateFlow(prefs.getBoolean(sk("pref_player_up_next"), true))
    /** Cartão "A seguir" no fim do episódio (o avanço automático continua sendo `pref_player_auto_next`). */
    val playerUpNextFlow: StateFlow<Boolean> = _playerUpNext.asStateFlow()
    var playerUpNext: Boolean
        get() = _playerUpNext.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_up_next"), value).apply(); _playerUpNext.value = value }

    private val _playerEqualizer = MutableStateFlow(prefs.getBoolean(sk("pref_player_equalizer"), false))
    /** Equalizador ligado (só vale com o player aberto; ao fechar, desliga junto). */
    val playerEqualizerFlow: StateFlow<Boolean> = _playerEqualizer.asStateFlow()
    var playerEqualizer: Boolean
        get() = _playerEqualizer.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_equalizer"), value).apply(); _playerEqualizer.value = value }

    private val _playerEqPreset = MutableStateFlow(prefs.getString(sk("pref_player_eq_preset"), "flat") ?: "flat")
    /** Preset do equalizador (nome do preset do aparelho; "flat" = tudo em 0 dB). */
    val playerEqPresetFlow: StateFlow<String> = _playerEqPreset.asStateFlow()
    var playerEqPreset: String
        get() = _playerEqPreset.value
        set(value) { prefs.edit().putString(sk("pref_player_eq_preset"), value).apply(); _playerEqPreset.value = value }

    private val _playerEqBands = MutableStateFlow(prefs.getString(sk("pref_player_eq_bands"), "") ?: "")
    /** Bandas do equalizador em dB inteiros, separadas por vírgula ("0,3,-2,0,0"). "" = tudo em 0 dB. */
    val playerEqBandsFlow: StateFlow<String> = _playerEqBands.asStateFlow()
    var playerEqBands: String
        get() = _playerEqBands.value
        set(value) { prefs.edit().putString(sk("pref_player_eq_bands"), value).apply(); _playerEqBands.value = value }

    // ── Lote 12 — Frente 2 (Player e fontes, v1.22.0) ───────────────────────
    // Modo de tela padrão do player (0 = Automático, 1 = Ajustar, 2 =
    // Preencher, 3 = Zoom, 4 = Esticar). O chip "Tela" do player salva aqui
    // a cada troca; o player abre no modo guardado (antes zerava sempre).

    private val _playerScreenMode = MutableStateFlow(prefs.getInt(sk("pref_player_screen_mode"), 0).coerceIn(0, 4))
    /** Modo de tela padrão do player (0 = Automático). Vale a partir da próxima reprodução. */
    val playerScreenModeFlow: StateFlow<Int> = _playerScreenMode.asStateFlow()
    var playerScreenMode: Int
        get() = _playerScreenMode.value
        set(value) { val clean = value.coerceIn(0, 4); prefs.edit().putInt(sk("pref_player_screen_mode"), clean).apply(); _playerScreenMode.value = clean }

    private val _playerSourcesRanked = MutableStateFlow(prefs.getBoolean(sk("pref_player_sources_ranked"), false))
    /** Folha de fontes em ranking (melhor → pior) em vez de agrupada por addon. */
    val playerSourcesRankedFlow: StateFlow<Boolean> = _playerSourcesRanked.asStateFlow()
    var playerSourcesRanked: Boolean
        get() = _playerSourcesRanked.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_sources_ranked"), value).apply(); _playerSourcesRanked.value = value }

    private val _recordingsSeen = MutableStateFlow(readStringList("recordings_seen_v1").toSet())
    /** Ids das gravações marcadas como vistas (por perfil). */
    val recordingsSeenFlow: StateFlow<Set<String>> = _recordingsSeen.asStateFlow()
    var recordingsSeen: Set<String>
        get() = _recordingsSeen.value
        set(value) { writeStringList("recordings_seen_v1", value.sorted()); _recordingsSeen.value = value }

    /** Marca (ou desmarca) uma gravação como vista. */
    fun setRecordingSeen(id: String, seen: Boolean) {
        recordingsSeen = if (seen) recordingsSeen + id else recordingsSeen - id
    }

    /** Esta gravação já foi marcada como vista. */
    fun isRecordingSeen(id: String): Boolean = id in recordingsSeen

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

    private val _durationGuard = MutableStateFlow(prefs.getBoolean(sk("pref_duration_guard"), true))
    /** Guarda de duração (Lote 11, v1.21.0): antes de tocar um filme, avisa quando a duração do arquivo destoa >10% da do título no TMDB. Ligada por padrão. */
    val durationGuardFlow: StateFlow<Boolean> = _durationGuard.asStateFlow()
    var durationGuard: Boolean
        get() = _durationGuard.value
        set(value) { prefs.edit().putBoolean(sk("pref_duration_guard"), value).apply(); _durationGuard.value = value }

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

    // ── Idiomas preferidos de áudio/legenda do player (Lote 4 item 7, v1.12.0) ──
    // Áudio: código do OpenSubtitles (ver [SubLangs], mesma lista da legenda) — o
    // player usa a família ISO 639-1 dele ("pob" → "pt") no ExoPlayer. A legenda
    // já tinha a pref (`pref_sub_lang`, `subLang`); o áudio era "pt" FIXO na
    // criação do ExoPlayer. Padrão "pob" = mesmo comportamento de antes.

    private val _playerAudioLang = MutableStateFlow(
        prefs.getString(sk("pref_player_audio_lang"), null)?.takeIf { SubLangs.of(it) != null } ?: SubLangs.DEFAULT,
    )
    /** Idioma preferido da faixa de áudio (código SubLangs, ex. "pob", "eng"). */
    val playerAudioLangFlow: StateFlow<String> = _playerAudioLang.asStateFlow()
    var playerAudioLang: String
        get() = _playerAudioLang.value
        set(value) {
            val clean = value.takeIf { SubLangs.of(it) != null } ?: SubLangs.DEFAULT
            prefs.edit().putString(sk("pref_player_audio_lang"), clean).apply(); _playerAudioLang.value = clean
        }

    // ── Reutilizar a última fonte do título (Lote 4 item 6, v1.12.0) ──────────
    // Ligado, o player abre o título na última fonte escolhida À MÃO nele
    // (addon + qualidade, ver [LastSourcePick]) quando ela ainda existe na
    // lista; sem ela, cai no comportamento atual sem erro. Desligado, nem
    // grava nem lê a escolha. Padrão ligado.

    private val _playerReuseLastSource = MutableStateFlow(prefs.getBoolean(sk("pref_player_reuse_last_source"), true))
    /** Reutilizar a última fonte escolhida à mão em cada título. */
    val playerReuseLastSourceFlow: StateFlow<Boolean> = _playerReuseLastSource.asStateFlow()
    var playerReuseLastSource: Boolean
        get() = _playerReuseLastSource.value
        set(value) { prefs.edit().putBoolean(sk("pref_player_reuse_last_source"), value).apply(); _playerReuseLastSource.value = value }

    // ── Modo privado (Lote 2, v1.10.0) — por perfil ─────────────────────────
    // Ligado, assistir não grava nada: nem progresso (poll do player, saída
    // e ON_STOP), nem Continuar assistindo, nem episódio visto, nem scrobble
    // pro Trakt. O bloqueio mora nos ViewModels que gravam (PlayerProgress e
    // MovieDetail) e lê esta pref — desligar volta ao normal e o que foi
    // assistido no privado NÃO aparece depois, porque nunca foi gravado.
    // NÃO apaga o progresso anterior: só impede o novo.

    private val _privateMode = MutableStateFlow(prefs.getBoolean(sk("pref_private_mode"), false))
    /** Modo privado ligado no perfil ativo. */
    val privateModeFlow: StateFlow<Boolean> = _privateMode.asStateFlow()
    var privateMode: Boolean
        get() = _privateMode.value
        set(value) { prefs.edit().putBoolean(sk("pref_private_mode"), value).apply(); _privateMode.value = value }

    // ── Modo aleatório (Lote 2, v1.10.0) — por perfil ───────────────────────
    // O que entra no sorteio (ver RandomEpisode.kt p/ a decisão do conjunto):
    // séries do Continuar assistindo e/ou da Minha Lista. "Só não vistos" tenta
    // evitar episódios já marcados como vistos; sem episódio não visto no
    // sorteio, o modo mostra o estado vazio em vez de tocar um repetido.

    private val _randomIncludeContinue = MutableStateFlow(prefs.getBoolean(sk("pref_random_include_continue"), true))
    /** Séries do Continuar assistindo entram no sorteio do Modo aleatório. */
    val randomIncludeContinueFlow: StateFlow<Boolean> = _randomIncludeContinue.asStateFlow()
    var randomIncludeContinue: Boolean
        get() = _randomIncludeContinue.value
        set(value) { prefs.edit().putBoolean(sk("pref_random_include_continue"), value).apply(); _randomIncludeContinue.value = value }

    private val _randomIncludeWatchlist = MutableStateFlow(prefs.getBoolean(sk("pref_random_include_watchlist"), true))
    /** Séries da Minha Lista entram no sorteio do Modo aleatório. */
    val randomIncludeWatchlistFlow: StateFlow<Boolean> = _randomIncludeWatchlist.asStateFlow()
    var randomIncludeWatchlist: Boolean
        get() = _randomIncludeWatchlist.value
        set(value) { prefs.edit().putBoolean(sk("pref_random_include_watchlist"), value).apply(); _randomIncludeWatchlist.value = value }

    private val _randomUnwatchedOnly = MutableStateFlow(prefs.getBoolean(sk("pref_random_unwatched_only"), false))
    /** Sortear só episódios ainda não vistos. Desligado, um episódio já visto pode sair (o sorteio tenta evitá-lo primeiro). */
    val randomUnwatchedOnlyFlow: StateFlow<Boolean> = _randomUnwatchedOnly.asStateFlow()
    var randomUnwatchedOnly: Boolean
        get() = _randomUnwatchedOnly.value
        set(value) { prefs.edit().putBoolean(sk("pref_random_unwatched_only"), value).apply(); _randomUnwatchedOnly.value = value }

    // ── PIN do perfil (Lote 3, v1.11.0) ─────────────────────────────────────
    // Um PIN por perfil, guardado como hash no próprio Profile (pinHash).
    // Mesmo padrão do PIN dos pais (SHA-256 com sal fixo do app, gate de uso
    // doméstico, não cofre). O sal NÃO inclui o id do perfil de propósito:
    // o PIN pode ser definido no editor ANTES de o perfil existir (id novo
    // só nasce no addProfile) e precisa continuar válido depois de criado.
    // "" = perfil sem PIN (troca livre).
    fun hashProfilePin(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("aotv-profile-pin:$pin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun checkProfilePin(p: Profile, pin: String): Boolean =
        p.pinHash.isNotBlank() && p.pinHash == hashProfilePin(pin)

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

    // ── Personalização de legenda (Lote 1, v1.9.0) — por perfil, com Flow p/ o player ──

    private val _subColor = MutableStateFlow(prefs.getInt(sk("sub_color"), -1))
    /** Cor do texto da legenda (ARGB; -1 = branco). */
    val subColorFlow: StateFlow<Int> = _subColor.asStateFlow()
    var subColor: Int
        get() = _subColor.value
        set(value) { prefs.edit().putInt(sk("sub_color"), value).apply(); _subColor.value = value }

    private val _subBgOpacity = MutableStateFlow(prefs.getFloat(sk("sub_bg_opacity"), 0.78f))
    /** Opacidade do fundo da legenda (0,5–0,9). */
    val subBgOpacityFlow: StateFlow<Float> = _subBgOpacity.asStateFlow()
    var subBgOpacity: Float
        get() = _subBgOpacity.value
        set(value) { prefs.edit().putFloat(sk("sub_bg_opacity"), value).apply(); _subBgOpacity.value = value }

    private val _subOutline = MutableStateFlow(prefs.getBoolean(sk("sub_outline"), true))
    /** Contorno preto ao redor do texto da legenda quando não há fundo. */
    val subOutlineFlow: StateFlow<Boolean> = _subOutline.asStateFlow()
    var subOutline: Boolean
        get() = _subOutline.value
        set(value) { prefs.edit().putBoolean(sk("sub_outline"), value).apply(); _subOutline.value = value }

    private val _subFont = MutableStateFlow(prefs.getString(sk("sub_font"), "default") ?: "default")
    /** Fonte da legenda ("default", "monospace", "serif" ou "sans"). */
    val subFontFlow: StateFlow<String> = _subFont.asStateFlow()
    var subFont: String
        get() = _subFont.value
        set(value) { prefs.edit().putString(sk("sub_font"), value).apply(); _subFont.value = value }

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
     * Atraso do áudio próprio por título (Lote 14 item 3, v1.24.0), chave
     * "TIPO:tmdbId". Só guarda atraso != 0 (0 = sincronia original, não
     * precisa de registro) e no máximo 60 títulos, os mais recentes.
     * Por perfil (SCOPED_KEYS "own_audio_delay_v1"). Quem grava é o
     * PlayerProgressViewModel, no mesmo padrão do som de outra fonte.
     */
    var ownAudioDelays: Map<String, OwnAudioDelay>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(sk("own_audio_delay_v1"), null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                OwnAudioDelay(v.optDouble("d", 0.0), v.optLong("t", 0L))
            }.filterValues { it.delaySec != 0.0 }
        }.getOrDefault(emptyMap())
        set(value) {
            val kept = value.entries.sortedByDescending { it.value.at }.take(60)
            val o = org.json.JSONObject()
            kept.forEach { (k, p) -> o.put(k, org.json.JSONObject().put("d", p.delaySec).put("t", p.at)) }
            prefs.edit().putString(sk("own_audio_delay_v1"), o.toString()).apply()
        }

    /**
     * Última fonte escolhida À MÃO por título (Lote 4 item 6, v1.12.0), chave
     * "TIPO:tmdbId". Guarda SÓ addon + qualidade — nunca a URL (token/assinatura).
     * No máximo 60 títulos (os mais recentes); o resto cai no cortar por [LastSourcePick.at].
     * Por perfil (SCOPED_KEYS "last_source_v1"). Quem grava é o
     * PlayerProgressViewModel, que já bloqueia no modo privado.
     */
    var lastSourcePicks: Map<String, LastSourcePick>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(sk("last_source_v1"), null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                LastSourcePick(v.optString("a"), v.optInt("q", 0), v.optLong("t", 0L))
            }.filterValues { it.addon.isNotBlank() }
        }.getOrDefault(emptyMap())
        set(value) {
            val kept = value.entries.sortedByDescending { it.value.at }.take(60)
            val o = org.json.JSONObject()
            kept.forEach { (k, p) -> o.put(k, org.json.JSONObject().put("a", p.addon).put("q", p.qualityRank).put("t", p.at)) }
            prefs.edit().putString(sk("last_source_v1"), o.toString()).apply()
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

    /**
     * Catálogo escolhido no Descobrir (Lote 14, v1.24.0): "addonId|type|id", vazio = TMDB.
     * Por perfil, para o catálogo sobreviver ao fechar o app e à troca Filmes/Séries.
     */
    var discoverCatalog: String
        get() = prefs.getString(sk("pref_discover_catalog_v1"), "").orEmpty()
        set(value) { prefs.edit().putString(sk("pref_discover_catalog_v1"), value).apply() }

    // ── Manifests guardados (nome e catálogos de cada addon, para a Início abrir sem rede) ──
    private val _addonMeta = MutableStateFlow(HomeLayout.decodeAddons(prefs.getString("addon_meta_v1", null)))
    val addonMetaFlow: StateFlow<Map<String, CachedAddon>> = _addonMeta.asStateFlow()

    /**
     * Guarda o que o manifest diz. Chamado toda vez que um manifest é lido com sucesso, às vezes
     * vários ao mesmo tempo (o gerenciador lê todos em paralelo), daí o synchronized.
     */
    fun cacheManifest(url: String, name: String, catalogs: List<com.aotv.app.data.remote.AddonCatalog>, id: String = "", logo: String = "") {
        val base = baseOf(url)
        val now = CachedAddon(name, catalogs, id, logo)
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

    // ── Layout da Página Inicial (Bloco 3, Lote 15, v1.25.0) ────────────────
    // Por perfil, como o resto do Personalizar Início. Defaults = comportamento
    // atual: destaque ligado, sem sufixo de tipo, sem ocultar não lançados e
    // catálogos do destaque no automático (null). Quem nunca abrir a tela não
    // percebe diferença.
    /** Carrossel do destaque no topo da Início (e Destaques da busca, mesma fonte). */
    private val _heroOn = MutableStateFlow(prefs.getBoolean(sk("pref_hero_on"), true))
    val heroOnFlow: StateFlow<Boolean> = _heroOn.asStateFlow()
    var heroOn: Boolean
        get() = _heroOn.value
        set(value) { prefs.edit().putBoolean(sk("pref_hero_on"), value).apply(); _heroOn.value = value }

    /** Sufixo de tipo (" · Filme" / " · Série") no título das fileiras de addon na Início. */
    private val _heroTypeSuffix = MutableStateFlow(prefs.getBoolean(sk("pref_hero_type_suffix"), false))
    val heroTypeSuffixFlow: StateFlow<Boolean> = _heroTypeSuffix.asStateFlow()
    var heroTypeSuffix: Boolean
        get() = _heroTypeSuffix.value
        set(value) { prefs.edit().putBoolean(sk("pref_hero_type_suffix"), value).apply(); _heroTypeSuffix.value = value }

    /** Ocultar conteúdo não lançado (estreia depois de hoje) nas fileiras e no destaque. */
    private val _hideUnreleased = MutableStateFlow(prefs.getBoolean(sk("pref_hide_unreleased"), false))
    val hideUnreleasedFlow: StateFlow<Boolean> = _hideUnreleased.asStateFlow()
    var hideUnreleased: Boolean
        get() = _hideUnreleased.value
        set(value) { prefs.edit().putBoolean(sk("pref_hide_unreleased"), value).apply(); _hideUnreleased.value = value }

    /**
     * Catálogos que alimentam o destaque no automático (chaves das fileiras).
     * null = padrão (o automático de sempre); vazio = nenhum catálogo no destaque.
     * A escolha é por chave estável da fileira, não por posição — a ordem muda.
     */
    private fun readHeroCatalogs(): Set<String>? = prefs.getStringSet(sk("home_hero_catalogs_v1"), null)?.toSet()
    private val _heroCatalogs = MutableStateFlow(readHeroCatalogs())
    val heroCatalogsFlow: StateFlow<Set<String>?> = _heroCatalogs.asStateFlow()
    var heroCatalogs: Set<String>?
        get() = _heroCatalogs.value
        set(value) {
            val ed = prefs.edit()
            if (value == null) ed.remove(sk("home_hero_catalogs_v1"))
            else ed.putStringSet(sk("home_hero_catalogs_v1"), value.toSet())
            ed.apply()
            _heroCatalogs.value = value
        }

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
        // O destaque volta ao padrão: automático e ligado. Sufixo de tipo e
        // ocultar não lançados são prefs de exibição e ficam como estão.
        heroOn = true
        heroCatalogs = null
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
        _heroOn.value = prefs.getBoolean(sk("pref_hero_on"), true)
        _heroTypeSuffix.value = prefs.getBoolean(sk("pref_hero_type_suffix"), false)
        _hideUnreleased.value = prefs.getBoolean(sk("pref_hide_unreleased"), false)
        _heroCatalogs.value = readHeroCatalogs()
        _homeDisplay.value = readHomeDisplay()
        _detailCinematic.value = prefs.getBoolean(sk("pref_detail_cinematic"), false)
        _detailPosterTransition.value = prefs.getBoolean(sk("pref_detail_poster_transition"), true)
        _detailIconActions.value = prefs.getBoolean(sk("pref_detail_icon_actions"), true)
        _detailTabs.value = prefs.getBoolean(sk("pref_detail_tabs"), false)
        _detailEpisodeStyle.value = prefs.getString(sk("pref_detail_episode_style"), "horizontal") ?: "horizontal"
        _detailEpisodeRatings.value = prefs.getBoolean(sk("pref_detail_episode_ratings"), true)
        _detailOriginalTitle.value = prefs.getBoolean(sk("pref_detail_original_title"), false)
        _detailShowAggregateRating.value = prefs.getBoolean(sk("pref_detail_show_aggregate_rating"), true)
        _detailShowTraktReviews.value = prefs.getBoolean(sk("pref_detail_show_trakt_reviews"), true)
        _detailShowQuotes.value = prefs.getBoolean(sk("pref_detail_show_quotes"), true)
        _ratingsSource.value = prefs.getString(sk("pref_ratings_source"), "auto") ?: "auto"
        _streamSearch.value = prefs.getBoolean(sk("pref_stream_search"), true)
        _streamBadgesSize.value = prefs.getBoolean(sk("pref_stream_badges_size"), true)
        _streamBadgesCodec.value = prefs.getBoolean(sk("pref_stream_badges_codec"), true)
        _streamBadgesLang.value = prefs.getBoolean(sk("pref_stream_badges_lang"), true)
        _streamBadgesPos.value = prefs.getString(sk("pref_stream_badges_pos"), "stats") ?: "stats"
        _streamLogo.value = prefs.getBoolean(sk("pref_stream_logo"), true)
        _streamPinned.value = readStringListFor("pref_stream_pinned", pinnedOwnerId())
        _playerButtons.value = readStringList("pref_player_buttons")
        _playerTopActions.value = readStringList("pref_player_top_actions")
        _playerTapSeek.value = prefs.getBoolean(sk("pref_player_tap_seek"), true)
        _playerSwipeSeek.value = prefs.getBoolean(sk("pref_player_swipe_seek"), true)
        _playerSpeed.value = prefs.getFloat(sk("pref_player_speed"), 1f)
        _playerColorBrightness.value = prefs.getFloat(sk("pref_player_color_brightness"), 0f)
        _playerColorContrast.value = prefs.getFloat(sk("pref_player_color_contrast"), 1f)
        _playerColorSaturation.value = prefs.getFloat(sk("pref_player_color_saturation"), 1f)
        _playerColorHue.value = prefs.getFloat(sk("pref_player_color_hue"), 0f)
        _playerColorPreset.value = prefs.getString(sk("pref_player_color_preset"), "padrao") ?: "padrao"
        _playerColorDaltonism.value = prefs.getString(sk("pref_player_color_daltonism"), "off") ?: "off"
        _playerEngine.value = prefs.getString(sk("pref_player_engine"), "exoplayer") ?: "exoplayer"
        _playerDecoder.value = prefs.getString(sk("pref_player_decoder"), "hw") ?: "hw"
        _playerBuffer.value = prefs.getString(sk("pref_player_buffer"), "normal") ?: "normal"
        _playerAutoSync.value = prefs.getBoolean(sk("pref_player_autosync"), false)
        _playerAutoSkip.value = prefs.getBoolean(sk("pref_player_auto_skip"), false)
        _playerUpNext.value = prefs.getBoolean(sk("pref_player_up_next"), true)
        _playerEqualizer.value = prefs.getBoolean(sk("pref_player_equalizer"), false)
        _playerEqPreset.value = prefs.getString(sk("pref_player_eq_preset"), "flat") ?: "flat"
        _playerEqBands.value = prefs.getString(sk("pref_player_eq_bands"), "") ?: ""
        // Lote 12 — Frente 2 (Player e fontes, v1.22.0).
        _playerScreenMode.value = prefs.getInt(sk("pref_player_screen_mode"), 0).coerceIn(0, 4)
        _playerSourcesRanked.value = prefs.getBoolean(sk("pref_player_sources_ranked"), false)
        _recordingsSeen.value = readStringList("recordings_seen_v1").toSet()
        _subColor.value = prefs.getInt(sk("sub_color"), -1)
        _subBgOpacity.value = prefs.getFloat(sk("sub_bg_opacity"), 0.78f)
        _subOutline.value = prefs.getBoolean(sk("sub_outline"), true)
        _subFont.value = prefs.getString(sk("sub_font"), "default") ?: "default"
        _playerVolBoost.value = prefs.getBoolean(sk("pref_player_vol_boost"), false)
        _playerVolBoostLevel.value = prefs.getFloat(sk("pref_player_vol_boost_level"), 1.5f)
        _playerMini.value = prefs.getBoolean(sk("pref_player_mini"), true)
        _playerAutoNext.value = prefs.getBoolean(sk("pref_player_auto_next"), true)
        _playerAutoNextDelay.value = prefs.getInt(sk("pref_player_auto_next_delay"), 10)
        _playerAutoSwitch.value = prefs.getBoolean(sk("pref_player_auto_switch"), true)
        _durationGuard.value = prefs.getBoolean(sk("pref_duration_guard"), true)
        _playerSourceTimeout.value = prefs.getInt(sk("pref_player_source_timeout"), 12)
        _playerAnimeSkipClientId.value = prefs.getString(sk("pref_player_animeskip_client_id"), "") ?: ""
        _playerAudioLang.value =
            prefs.getString(sk("pref_player_audio_lang"), null)?.takeIf { SubLangs.of(it) != null } ?: SubLangs.DEFAULT
        _playerReuseLastSource.value = prefs.getBoolean(sk("pref_player_reuse_last_source"), true)
        _privateMode.value = prefs.getBoolean(sk("pref_private_mode"), false)
        _hiddenContinue.value = readStringList("hidden_continue_v1").toSet()
        _randomIncludeContinue.value = prefs.getBoolean(sk("pref_random_include_continue"), true)
        _randomIncludeWatchlist.value = prefs.getBoolean(sk("pref_random_include_watchlist"), true)
        _randomUnwatchedOnly.value = prefs.getBoolean(sk("pref_random_unwatched_only"), false)
        // Lote 8 — Live TV (v1.18.0): o formato Xtream é GLOBAL e não precisa
        // recarregar aqui; o resto é por perfil.
        _liveGuideDaysAhead.value = prefs.getInt(sk("live_guide_days_ahead"), 3).coerceIn(1, 7)
        _liveGuideHoursBack.value = prefs.getInt(sk("live_guide_hours_back"), 1).coerceIn(0, 24)
        _liveRefreshListsHours.value = prefs.getInt(sk("live_refresh_lists_hours"), 24).coerceIn(1, 168)
        _liveRefreshEpgHours.value = prefs.getInt(sk("live_refresh_epg_hours"), 12).coerceIn(1, 168)
        _liveShowLogos.value = prefs.getBoolean(sk("live_show_logos"), true)
        _liveShowNames.value = prefs.getBoolean(sk("live_show_names"), true)
        _liveShowNumbers.value = prefs.getBoolean(sk("live_show_numbers"), false)
        _liveCleanGroupNames.value = prefs.getBoolean(sk("live_clean_group_names"), false)
        _liveBackupChannel.value = prefs.getBoolean(sk("live_backup_channel"), true)
        _liveInContinue.value = prefs.getBoolean(sk("live_in_continue"), true)
        _liveRememberAudio.value = prefs.getBoolean(sk("live_remember_audio"), true)
        _liveCatchupHls.value = prefs.getBoolean(sk("live_catchup_hls"), false)
        // Lote 9 — Hub da Live TV (v1.19.0): grupos favoritos, por perfil.
        _liveGroupFavorites.value = readStringList("live_group_favorites_v1")
        // Lote 11 (v1.21.0): grupos trancados com o PIN dos pais, por perfil.
        _liveLockedGroups.value = readStringList("live_locked_groups_v1")
        // Lote 12 (v1.22.0): Multiview e gerenciar canais (listas com fluxo).
        // Os mapas (nomes, grupos, números, EPG, logos) são lidos direto do
        // disco a cada acesso, sem fluxo — não precisam de reload aqui.
        _liveMultiviewLayout.value = prefs.getInt(sk("live_multiview_layout"), 1).let { if (it == 2 || it == 4) it else 1 }
        _liveHiddenChannels.value = readStringList("live_hidden_channels_v1")
        _liveChannelOrder.value = readStringList("live_channel_order_v1")
        // Lote 11 (v1.21.0): blur anti-spoiler + o esconder-spoilers do
        // calendário (Lote 8, que não era recarregado na troca de perfil —
        // o valor ficava o do perfil anterior até reiniciar o app).
        _detailSpoilerBlur.value = prefs.getBoolean(sk("pref_detail_spoiler_blur"), false)
        _calendarHideSpoilers.value = prefs.getBoolean(sk("calendar_hide_spoilers"), false)
        // Lote 13 (v1.23.0): auto-add do calendário via trackers. O mapa
        // "Não hoje" é lido do disco a cada acesso — não precisa de reload.
        _calendarAutoAddTrackers.value = prefs.getBoolean(sk("calendar_auto_add_trackers"), true)
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
