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
 * serviços. Restaurar exige reconectar essas três contas.
 */
object AotvBackup {
    /** Formato do arquivo. Mudar aqui = formato novo (o restore recusa o antigo). */
    const val FORMAT = "aotv-backup-v1"

    /** Limite por backup, nos dois sentidos (o mesmo do JOTV 1.0.19). */
    const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

    /** Chaves por perfil que são segredo: só entram com o toggle ligado. */
    val SECRET_SCOPED_KEYS = setOf(
        "trakt_v1", "simkl_v1", "simkl_client_id_v1", "mdblist_key_v1", "omdb_key_v1",
        "anilist_v1", "wetrakr_v1", "publicmetadb_v1", "discord_v1", "jellyfin_v1",
        "emby_v1", "pref_player_animeskip_client_id",
    )

    /** Chaves do aparelho que são segredo: só entram com o toggle ligado. */
    val SECRET_GLOBAL_KEYS = setOf(
        "tmdb_api_key", "parental_pin_hash", "plugin_settings_v1",
    )

    /** Globais que o backup leva (fora os segredos): tema, blur e o resto do aparelho. */
    val GLOBAL_KEYS = setOf(
        "theme_palette_v1", "custom_theme_v1", "pref_panel_blur",
        "plugin_timeout_sec", "plugin_concurrency", "plugin_auto_update", "plugin_global_enabled",
        "aotv_catalog_on", "platform_mode",
        "intro_marks_v1", "home_auto_on_v1", "nuvio_profile_links_v1",
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
