package com.aotv.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Legenda externa (arquivo .srt) de um filme/episódio. */
data class ExternalSub(val url: String, val lang: String, val label: String)

private data class SubResponse(val subtitles: List<SubDto> = emptyList())
private data class SubDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("lang") val lang: String? = null,
)

/**
 * Legendas externas via addon Stremio OpenSubtitles v3 (sem API key). Prioriza Português.
 * Desativada por padrão — o player só carrega quando o usuário escolhe uma legenda.
 */
@Singleton
class SubtitleService @Inject constructor(
    private val prefs: com.aotv.app.data.local.PlatformPrefs,
    private val stremio: StremioAddonService,
) {
    private val client = OkHttpClient.Builder()
        .dns(AppDns.cloudflare)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Nome e ordem vêm de SubLangs, com o idioma de legenda do perfil no topo.
    private fun rank(lang: String) = com.aotv.app.data.local.SubLangs.rank(lang, prefs.subLang)
    private fun label(lang: String) = com.aotv.app.data.local.SubLangs.label(lang)

    /** Texto do arquivo de legenda, ou null se não baixou. Trata GZIP e charsets legados. */
    suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                // GZIP: magic bytes 1f 8b no início do corpo.
                val rawBytes = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readBytes() }
                } else bytes
                // UTF-8 estrito (falha em vez de trocar por �) e, se vier com replacement
                // chars, cai para windows-1252 e depois ISO-8859-1 (nunca falha).
                fun decodeStrict(data: ByteArray, charset: java.nio.charset.Charset): String? = runCatching {
                    charset.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()
                }.getOrNull()?.takeIf { '\uFFFD' !in it }
                decodeStrict(rawBytes, java.nio.charset.StandardCharsets.UTF_8)
                    ?: decodeStrict(rawBytes, java.nio.charset.Charset.forName("windows-1252"))
                    ?: String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }.getOrNull()
    }

    /** OpenSubtitles mais as legendas dos addons instalados que têm o recurso subtitles. */
    suspend fun subtitles(imdb: String, type: String, season: Int, episode: Int): List<ExternalSub> =
        kotlinx.coroutines.coroutineScope {
            val os = async { openSubtitles(imdb, type, season, episode) }
            val addons = async { addonSubtitles(imdb, type, season, episode) }
            // Ordenação estável, então no mesmo idioma o OpenSubtitles continua antes dos addons.
            (os.await() + addons.await()).distinctBy { it.url }.sortedBy { rank(it.lang) }
        }

    // Addon -> nome, ou null quando o manifest não tem o recurso subtitles. Lido uma vez só.
    private val subAddons = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<String>>()

    private suspend fun addonSubtitles(imdb: String, type: String, season: Int, episode: Int): List<ExternalSub> =
        kotlinx.coroutines.coroutineScope {
            if (!imdb.startsWith("tt")) return@coroutineScope emptyList()
            val t = if (type == "SERIES") "series" else "movie"
            val id = if (type == "SERIES" && season > 0 && episode > 0) "$imdb:$season:$episode" else imdb
            prefs.stremioAddonList().map { u ->
                async(Dispatchers.IO) {
                    // Manifest que não respondeu não entra no cache, tenta de novo na próxima.
                    val name = (subAddons[u] ?: stremio.getManifest(u)?.let { m ->
                        java.util.Optional.ofNullable(m.takeIf { "subtitles" in it.resources }?.name).also { subAddons[u] = it }
                    })?.orElse(null) ?: return@async emptyList()
                    val base = u.trim().removeSuffix("/manifest.json").removeSuffix("/")
                    val body = runCatching {
                        client.newCall(Request.Builder().url(stremio.normalizeManifestUrl(base).removeSuffix("manifest.json") + "subtitles/$t/$id.json").build())
                            .execute().use { if (it.isSuccessful) it.body?.string() else null }
                    }.getOrNull() ?: return@async emptyList()
                    val resp = runCatching { gson.fromJson(body, SubResponse::class.java) }.getOrNull()
                    resp?.subtitles.orEmpty().mapNotNull { s ->
                        val url = s.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                        val lang = s.lang.orEmpty().lowercase()
                        ExternalSub(url, lang, label(lang) + " · " + name)
                    }
                }
            }.awaitAll().flatten().sortedBy { rank(it.lang) }.take(30)
        }

    private suspend fun openSubtitles(imdb: String, type: String, season: Int, episode: Int): List<ExternalSub> =
        withContext(Dispatchers.IO) {
            if (!imdb.startsWith("tt")) return@withContext emptyList()
            val t = if (type == "SERIES") "series" else "movie"
            val id = if (type == "SERIES" && season > 0 && episode > 0) "$imdb:$season:$episode" else imdb
            val url = "https://opensubtitles-v3.strem.io/subtitles/$t/$id.json"
            val body = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use {
                    if (it.isSuccessful) it.body?.string() else null
                }
            }.getOrNull() ?: return@withContext emptyList()

            val resp = runCatching { gson.fromJson(body, SubResponse::class.java) }.getOrNull()
                ?: return@withContext emptyList()

            resp.subtitles
                .mapNotNull { s ->
                    val u = s.url?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                    val lang = s.lang?.lowercase() ?: return@mapNotNull null
                    ExternalSub(u, lang, label(lang))
                }
                // 1 por idioma (a 1ª costuma ser a melhor), o idioma do perfil primeiro. Sem corte
                // curto, para quem escolheu outro idioma achar a legenda dele.
                .distinctBy { it.lang }
                .sortedBy { rank(it.lang) }
                .take(40)
        }
}
