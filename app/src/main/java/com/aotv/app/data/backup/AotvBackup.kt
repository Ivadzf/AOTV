package com.aotv.app.data.backup

import com.aotv.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Formato do backup do AOTV (v1.13.0), compatível com o desenho do JOTV 1.0.19:
 * JSON versionado, sem servidor externo, com limite de 8 MB.
 *
 * ```json
 * {
 *   "format": "aotv-backup-v1", "appVersion": "1.13.0", "includesSecrets": false,
 *   "profile": { "id": "main", ... },          // perfil ativo na hora do backup
 *   "profiles": [ { "id","name","color","kids","avatarUrl","avatarId","usePrimaryAddons" } ],
 *   "addons": [ { "url","enabled","id" } ],    // ordem = prioridade
 *   "plugins": { "format": "aotv.plugins", ... }, // backup de plugins (PluginStore)
 *   "watchlist": [ { "tmdbId","type","title","posterUrl","year","addedAt" } ],
 *   "continue": [ { "tmdbId","type","title","posterUrl","positionMs","durationMs",
 *                   "lastEpisode","lastWatchedAt" } ],
 *   "watched": [ { "seriesTmdbId","season","episode","watchedAt" } ],
 *   "profilePrefs": { "chave": { "t": "s|i|j|f|b|set", "v": ... } },
 *   "globalPrefs": { "chave": { "t": ..., "v": ... } }
 * }
 * ```
 *
 * `profilePrefs` cobre TUDO o que é do perfil (SCOPED_KEYS): layout da Início,
 * catálogos, coleções, buscas recentes, títulos ocultos do Continuar, prefs do
 * player/detalhe/streams e o modo privado. `globalPrefs` cobre o aparelho
 * (tema, blur, catálogo AOTV, timeouts dos plugins...).
 *
 * NUNCA entram URLs de fonte (`lastSourceUrl` fica fora: links de addon trazem
 * token/assinatura) e, com `includesSecrets` desligado (o padrão), NEM sessões,
 * chaves ou hashes de PIN. O que é segredo e fica fora por padrão está listado
 * em [SECRET_SCOPED_KEYS] e [SECRET_GLOBAL_KEYS] — o backup restaurado sem
 * eles mostra "Backup restaurado. Configure novamente suas senhas e sessões".
 *
 * Sessões de Debrid, Nuvio e Stremio NUNCA entram no backup: vivem em
 * SharedPreferences próprias, sem caminho de exportação/importação nesses
 * serviços. O mesmo vale para os servidores de mídia (Jellyfin/Emby/Plex,
 * `aotv_mediaservers`). Restaurar exige reconectar essas contas.
 */
object AotvBackup {
    /** Formato do arquivo. Mudar aqui = formato novo (o restore recusa o antigo). */
    const val FORMAT = "aotv-backup-v1"

    /** Limite por backup, nos dois sentidos (o mesmo do JOTV 1.0.19). */
    const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

    /** Chaves por perfil que são segredo: só entram com o toggle ligado. */
    val SECRET_SCOPED_KEYS = setOf(
        "trakt_access_token_v1", "trakt_refresh_token_v1", "simkl_v1", "simkl_client_id_v1", "mdblist_key_v1", "omdb_key_v1",
        "anilist_v1", "wetrakr_v1", "publicmetadb_v1", "discord_v1", "jellyfin_v1",
        "emby_v1", "pref_player_animeskip_client_id",
        // v1.17.0 — token do AniList (por perfil): segredo, como os tokens do Trakt.
        "anilist_access_token",
        // Lote 10 (v1.20.0) — perfis de VPN (JSON com a chave privada do
        // WireGuard/OpenVPN): segredo, como os tokens. A Frente 3 (VPN) é
        // dona de quem escreve a chave; o backup/sync só a carrega.
        "vpn_profiles_v1",
    )

    /** Chaves do aparelho que são segredo: só entram com o toggle ligado. */
    val SECRET_GLOBAL_KEYS = setOf(
        "tmdb_api_key", "parental_pin_hash", "plugin_settings_v1",
        "trakt_client_id_v1", "trakt_client_secret_v1",
        // v1.17.0 — chaves de conta do aparelho: segredo, como as do Trakt.
        // O doc das chaves pedia estas em GLOBAL_KEYS; vão p/ SECRET_GLOBAL_KEYS
        // de propósito: GLOBAL_KEYS entra no backup SEMPRE, e chave de API
        // nunca viaja sem o toggle de segredos (mesma regra do Trakt).
        "mdblist_api_key", "anilist_client_id", "tvdb_api_key", "tvdb_auth_token",
        // Lote 10 (v1.20.0) — Client ID OAuth do Google Drive (configurável,
        // vazio por padrão): mesmo precedente do client ID do Trakt.
        "drive_client_id",
    )

    /** Globais que o backup leva (fora os segredos): tema, blur e o resto do aparelho. */
    val GLOBAL_KEYS = setOf(
        "theme_palette_v1", "custom_theme_v1", "pref_panel_blur",
        "plugin_timeout_sec", "plugin_concurrency", "plugin_auto_update", "plugin_global_enabled",
        "aotv_catalog_on", "platform_mode",
        "intro_marks_v1", "home_auto_on_v1", "nuvio_profile_links_v1",
        // Lote 5 (v1.14.0): "Só Wi-Fi", pasta SAF e contadores/limite de dados
        // móveis. A URI da pasta viaja como texto, mas a permissão persistida
        // NÃO viaja no backup — depois de restaurar, a tela de Downloads pede
        // p/ escolher a pasta de novo (ver DownloadsPage).
        "downloads_wifi_only", "downloads_folder_uri", "downloads_folder_name",
        "mobile_data_today_bytes", "mobile_data_month_bytes",
        "mobile_data_day", "mobile_data_month", "mobile_data_limit_mb",
        // v1.17.0 — expiração do token do TheTVDB: só um timestamp, não é
        // segredo (o token em si está em SECRET_GLOBAL_KEYS).
        "tvdb_token_expiry",
        // Lote 10 (v1.20.0) — sync do Google Drive: conta, id do arquivo e
        // datas do último sync (nada disso é segredo; o client ID está em
        // SECRET_GLOBAL_KEYS). Viajar no backup faz o outro aparelho achar
        // o mesmo arquivo no Drive sem procurar de novo.
        "drive_account_email", "drive_file_id", "drive_last_sync_at",
        "drive_last_remote_modified_ms",
        // Lote 11 (v1.21.0) — variante de ícone do app (não é segredo: só o
        // nome da cor). Restaurar o backup volta o ícone escolhido (a troca
        // é aplicada no restore, ver BackupViewModel.refreshGlobalFlows).
        "app_icon_variant",
        // Lote 12 — Frente 3 (Notas e atualizador, v1.22.0): checagem de
        // atualização ligada + data da última checagem. Nada é segredo.
        "app_update_check_enabled", "app_update_last_check_v1",
        // Lote 18 (v1.28.0): download em 2º plano + "What's new". Nada é
        // segredo: tag pronta, se o hash foi conferido, versão vista e o
        // texto do changelog guardado.
        "app_update_background_enabled", "app_update_ready_tag",
        "app_update_ready_hash_verified", "app_last_seen_version",
        // Lote 18 (v1.28.0) — torrent/Downloads: teto de espaço temporário
        // do torrent + contadores de streaming/torrent. Nada é segredo.
        "torrent_temp_limit_mb", "torrent_discard_watched",
        "mobile_data_streaming_today_bytes", "mobile_data_streaming_month_bytes",
        "mobile_data_torrent_today_bytes", "mobile_data_torrent_month_bytes",
        "app_whatsnew_version", "app_whatsnew_changelog",
        "app_whatsnew_seen_version",
    )

    /** Contagens p/ o resumo antes de restaurar ("3 addons · 12 na lista…"). */
    data class Summary(
        val addons: Int = 0,
        val addonsEnabled: Int = 0,
        val pluginRepos: Int = 0,
        val plugins: Int = 0,
        val watchlist: Int = 0,
        val continueWatching: Int = 0,
        val watchedEpisodes: Int = 0,
        val recentSearches: Int = 0,
        val collections: Int = 0,
        val profiles: Int = 0,
        val includesSecrets: Boolean = false,
        val appVersion: String = "",
    ) {
        /** Linhas do diálogo de confirmação, só com o que o backup traz. */
        fun lines(): List<String> = buildList {
            if (addons > 0) add("$addons addon(s) ($addonsEnabled ativado(s))")
            if (pluginRepos > 0) add("$pluginRepos repositório(s) de plugins")
            if (plugins > 0) add("$plugins plugin(s)")
            if (watchlist > 0) add("$watchlist item(ns) na Minha Lista")
            if (continueWatching > 0) add("$continueWatching em Continuar assistindo")
            if (watchedEpisodes > 0) add("$watchedEpisodes episódio(s) visto(s)")
            if (recentSearches > 0) add("$recentSearches busca(s) recente(s)")
            if (collections > 0) add("$collections coleção(ões)")
            if (profiles > 1) add("$profiles perfis")
        }
    }

    /** Erro de validação, com a mensagem que a tela mostra. */
    class BackupException(message: String) : Exception(message)

    /**
     * Valida o texto: tamanho (8 MB), JSON válido e formato `aotv-backup-v1`.
     * Devolve o JSON parseado. Nunca loga o conteúdo (pode ter segredos).
     */
    fun parse(text: String): JSONObject {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BACKUP_BYTES) throw BackupException("Backup grande demais (limite de 8 MB)")
        val root = runCatching { JSONObject(text) }.getOrElse { throw BackupException("Esse arquivo não é um backup válido.") }
        if (root.optString("format") != FORMAT) throw BackupException("Esse arquivo não é um backup do AOTV.")
        return root
    }

    /** Contagens do backup, p/ o resumo antes de restaurar. Nada aqui é segredo. */
    fun summarize(root: JSONObject): Summary {
        val pluginsObj = root.optJSONObject("plugins")
        val pluginRepos = pluginsObj?.optJSONArray("repos")?.length() ?: 0
        val plugins = pluginsObj?.optJSONArray("repos")?.let { repos ->
            (0 until repos.length()).sumOf { i -> repos.optJSONObject(i)?.optJSONArray("providers")?.length() ?: 0 }
        } ?: 0
        val profilePrefs = root.optJSONObject("profilePrefs")
        return Summary(
            addons = root.optJSONArray("addons")?.length() ?: 0,
            addonsEnabled = root.optJSONArray("addons")?.let { arr ->
                (0 until arr.length()).count { i -> arr.optJSONObject(i)?.optBoolean("enabled", true) == true }
            } ?: 0,
            pluginRepos = pluginRepos,
            plugins = plugins,
            watchlist = root.optJSONArray("watchlist")?.length() ?: 0,
            continueWatching = root.optJSONArray("continue")?.length() ?: 0,
            watchedEpisodes = root.optJSONArray("watched")?.length() ?: 0,
            recentSearches = profilePrefs?.optJSONObject("recent_searches")?.optString("v").orEmpty()
                .split("\n").count { it.isNotBlank() },
            collections = runCatching {
                JSONArray(profilePrefs?.optJSONObject("collections_v1")?.optString("v").orEmpty()).length()
            }.getOrDefault(0),
            profiles = root.optJSONArray("profiles")?.length() ?: 0,
            includesSecrets = root.optBoolean("includesSecrets", false),
            appVersion = root.optString("appVersion"),
        )
    }

    // ── Prefs cruas com tipo ──────────────────────────────────────────────────
    // SharedPreferences guarda String/Int/Long/Float/Boolean/Set<String>. O
    // JSON marca o tipo ("s"/"i"/"j"/"f"/"b"/"set") p/ a restauração gravar o
    // mesmo tipo de volta (um Int lido como String quebraria o getInt).

    /** Empacota um valor cru de SharedPreferences num {"t","v"}. null = ausente (não grava). */
    fun encodePrefValue(value: Any?): JSONObject? = when (value) {
        null -> null
        is String -> JSONObject().put("t", "s").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "j").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is Double -> JSONObject().put("t", "f").put("v", value)
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    /** Desempacota um {"t","v"} no valor cru. null = inválido/ausente. */
    fun decodePrefValue(o: JSONObject?): Any? {
        if (o == null) return null
        return when (o.optString("t")) {
            "s" -> o.optString("v")
            "i" -> o.optInt("v")
            "j" -> o.optLong("v")
            "f" -> o.optDouble("v").toFloat()
            "b" -> o.optBoolean("v")
            "set" -> o.optJSONArray("v")?.let { arr ->
                (0 until arr.length()).mapTo(LinkedHashSet()) { arr.optString(it) }
            }
            else -> null
        }
    }

    /** Versão do app que gerou o backup (p/ o campo "appVersion"). */
    fun appVersion(): String = BuildConfig.VERSION_NAME
}
