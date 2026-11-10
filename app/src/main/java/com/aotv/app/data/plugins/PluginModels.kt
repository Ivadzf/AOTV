package com.aotv.app.data.plugins

/**
 * Metadados de um repositório de plugins do Nuvio (o manifest.json).
 * O manifest do repo do Davi tem "name", "version" e "scrapers": cada scraper
 * traz id, name, description, version, author, supportedTypes ["movie"/"tv"],
 * filename (caminho relativo ao manifest, ex. "providers/fshd.js") e enabled.
 */
data class PluginRepoManifest(
    val name: String,
    val version: String,
    val scrapers: List<PluginScraperMeta>,
)

data class PluginScraperMeta(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val filename: String,
    val enabled: Boolean = true,
    val contentLanguage: String = "",
)

/** Repositório instalado (a URL do manifest + nome/versão lidos dele). */
data class PluginRepo(
    val url: String,
    val name: String = "",
    val version: String = "",
    /** Última atualização dos códigos desse repo (ms desde epoch). Refresh a cada 6h. */
    val updatedAt: Long = 0L,
)

/**
 * Plugin instalado. O código JS fica em filesDir/plugin-code/<id>.js (com SHA256
 * guardado aqui); os cookies ficam em filesDir/plugin-cookies/<id>.json.
 * As configurações do plugin (onSettings) ficam em PlatformPrefs.pluginSettings.
 */
data class InstalledPlugin(
    val id: String,
    val name: String,
    val repoUrl: String,
    val version: String = "",
    val enabled: Boolean = true,
    val sha256: String = "",
    val updatedAt: Long = 0L,
    /** Tipos que o scraper atende, do manifest ("movie" e/ou "tv"). */
    val supportedTypes: List<String> = listOf("movie", "tv"),
)

/** Um descritor de configuração devolvido por onSettings() do plugin. */
data class PluginSettingDescriptor(
    val type: String, // "header" | "select" | "toggle" | "text"
    val key: String = "",
    val label: String = "",
    val options: List<PluginSettingOption> = emptyList(),
    val defaultValue: Any? = null,
)

data class PluginSettingOption(
    val label: String,
    val value: String,
)

/** Stream cru devolvido por getStreams(), antes de normalizar p/ StremioStream. */
data class PluginStreamRaw(
    val url: String,
    val title: String = "",
    val label: String = "",
)

/**
 * Último resultado de uma execução de plugin (diagnóstico visível na tela de
 * Plugins — sem ele, uma falha só aparece no logcat, que o usuário não vê).
 */
data class PluginLastRun(
    /** "ok" | "empty" | "timeout" | "error". */
    val status: String,
    /** Quantos streams a execução devolveu (0 quando falhou). */
    val streams: Int = 0,
    /** Duração da execução em ms. */
    val millis: Long = 0L,
    /** Quando terminou (ms desde epoch). */
    val at: Long = 0L,
    /** Detalhe curto do erro (vazio em "ok"/"empty"). */
    val detail: String = "",
)
