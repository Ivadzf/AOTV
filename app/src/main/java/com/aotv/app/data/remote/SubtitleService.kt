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

/** Legenda externa (arquivo .srt) de um filme/episódio.
 * [id] é o id da legenda na fonte (ex.: "14183659" no OpenSubtitles v3) — o
 * painel de legendas mostra como no Nuvio; "" quando a fonte não dá id.
 * [source] é a fonte real ("OpenSubtitles v3", nome do addon, "SubDL") — o
 * selo do painel; "" = deduzir do rótulo (compat com o builder antigo).
 * [detail] é uma linha extra (ex.: release name do SubDL); "" = sem linha. */
data class ExternalSub(val url: String, val lang: String, val label: String,
    val id: String = "", val source: String = "", val detail: String = "")

/** Quantas legendas guardar por idioma (o teto que o Nuvio mostra, ex.:
 * "Inglês 8") e no total. Antes era 1 por idioma (ver [SubtitleService.openSubtitles]). */
private const val MAX_SUBS_PER_LANG = 8
private const val MAX_SUBS_TOTAL = 60

private data class SubResponse(val subtitles: List<SubDto> = emptyList())
private data class SubDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("id") val id: String? = null,
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

    /** Texto do arquivo de legenda, ou null se não baixou. Trata GZIP, ZIP e
     *  charsets legados (v1.13.0: ZIP + UTF-16/BOM, JOTV 1.0.19 §3.6). */
    suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                decodeSubtitleBytes(bytes)
            }
        }.getOrNull()
    }

    /**
     * Decodifica os bytes de um arquivo de legenda p/ texto (Lote do
     * remodel v1.44.0 — Frente 1, extraído do [download] sem mudar o
     * comportamento): o MESMO tratamento p/ o arquivo importado no
     * player ([com.aotv.app.ui.movies.EpisodePlayerViewModel.importedSubtitleCues])
     * e p/ o baixado — GZIP, ZIP (1º .srt/.vtt/.ass de dentro), BOM
     * UTF-16 LE/BE, BOM UTF-8, UTF-8 estrito → windows-1252 →
     * ISO-8859-1. Null quando os bytes não são um arquivo de legenda
     * utilizável (ZIP sem entrada de legenda). Não faz rede.
     */
    fun decodeSubtitleBytes(bytes: ByteArray): String? {
        // GZIP: magic bytes 1f 8b no início do corpo.
        var rawBytes = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            runCatching {
                java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrNull() ?: return null
        } else bytes
        // ZIP: magic bytes PK\x03\x04 — extrai o primeiro .srt/.vtt/.ass
        // de dentro (o .srt é o preferido). Sem entrada de legenda, null.
        if (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() &&
            rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte()
        ) {
            rawBytes = unzipFirstSubtitle(rawBytes) ?: return null
        }
        // BOM UTF-16 (FF FE = LE, FE FF = BE): decodifica direto com o
        // charset certo — o UTF-8 estrito abaixo aceitaria os bytes NUL
        // e devolveria lixo, então o BOM é testado ANTES do fallback.
        if (rawBytes.size >= 2 && rawBytes[0] == 0xFF.toByte() && rawBytes[1] == 0xFE.toByte()) {
            return String(rawBytes, 2, rawBytes.size - 2, java.nio.charset.StandardCharsets.UTF_16LE)
        }
        if (rawBytes.size >= 2 && rawBytes[0] == 0xFE.toByte() && rawBytes[1] == 0xFF.toByte()) {
            return String(rawBytes, 2, rawBytes.size - 2, java.nio.charset.StandardCharsets.UTF_16BE)
        }
        // BOM UTF-8 (EF BB BF): tira e segue o fallback normal.
        if (rawBytes.size >= 3 && rawBytes[0] == 0xEF.toByte() && rawBytes[1] == 0xBB.toByte() && rawBytes[2] == 0xBF.toByte()) {
            rawBytes = rawBytes.copyOfRange(3, rawBytes.size)
        }
        // UTF-8 estrito (falha em vez de trocar por �) e, se vier com replacement
        // chars, cai para windows-1252 e depois ISO-8859-1 (nunca falha).
        fun decodeStrict(data: ByteArray, charset: java.nio.charset.Charset): String? = runCatching {
            charset.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()
        }.getOrNull()?.takeIf { '\uFFFD' !in it }
        return decodeStrict(rawBytes, java.nio.charset.StandardCharsets.UTF_8)
            ?: decodeStrict(rawBytes, java.nio.charset.Charset.forName("windows-1252"))
            ?: String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1)
    }

    /**
     * Extrai o primeiro arquivo de legenda de dentro de um ZIP (.srt preferido,
     * depois .vtt, depois .ass). Null quando o ZIP não tem nenhuma entrada de
     * legenda. O corpo já está todo em memória (bytes do HTTP), então a leitura
     * é limitada pelo tamanho do download.
     */
    private fun unzipFirstSubtitle(bytes: ByteArray): ByteArray? = runCatching {
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var best: ByteArray? = null
            var bestRank = Int.MAX_VALUE
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val rank = when {
                        entry.name.lowercase().endsWith(".srt") -> 0
                        entry.name.lowercase().endsWith(".vtt") -> 1
                        entry.name.lowercase().endsWith(".ass") -> 2
                        else -> -1
                    }
                    if (rank in 0 until bestRank) {
                        best = zip.readBytes()
                        bestRank = rank
                        if (rank == 0) break // .srt é o preferido; achou, para
                    }
                }
                entry = zip.nextEntry
            }
            best
        }
    }.getOrNull()

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
                        ExternalSub(url, lang, label(lang) + " · " + name, id = s.id.orEmpty(), source = name)
                    }
                }
            }.awaitAll().flatten()
                .sortedBy { rank(it.lang) }
                .groupBy { com.aotv.app.data.local.SubLangs.of(it.lang)?.key ?: it.lang }
                .values.flatMap { it.take(MAX_SUBS_PER_LANG) }
                .take(30)
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
                    ExternalSub(u, lang, label(lang), id = s.id.orEmpty(), source = "OpenSubtitles v3")
                }
                // Lote 17 item 14 — CAUSA RAIZ 1: o `.distinctBy { it.lang }`
                // daqui deixava UMA legenda por idioma (de 73 legendas de
                // Dexter S01E01 sobravam 22, conferido ao vivo em 2026-11-16):
                // o painel nunca podia mostrar "Inglês 8" nem deixar escolher
                // entre os arquivos do mesmo idioma. Agora guarda até 8 por
                // idioma (o teto que o Nuvio mostra) e 60 no total, com a
                // ordem pelo idioma do perfil; a contagem do painel passa a
                // ser real. O `id` (ex.: "14183659") alimenta o painel novo.
                .sortedBy { rank(it.lang) }
                .groupBy { com.aotv.app.data.local.SubLangs.of(it.lang)?.key ?: it.lang }
                .values.flatMap { it.take(MAX_SUBS_PER_LANG) }
                .take(MAX_SUBS_TOTAL)
        }
}
