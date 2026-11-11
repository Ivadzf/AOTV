package com.aotv.app.data.accounts

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Addon de outra conta. [canUse] diz se ele serve no AOTV (fonte ou catálogo, fora do aparelho). */
data class RemoteAddon(val url: String, val name: String, val canUse: Boolean)

/** Item da biblioteca de outra conta. [id] é o id do Stremio ("tt...", "tmdb:..."). */
data class RemoteLibraryItem(val id: String, val series: Boolean, val name: String, val poster: String, val addedAt: Long)

/** Progresso de outra conta. Tempos em ms. [season] e [episode] 0 em filme. */
data class RemoteProgress(
    val id: String, val series: Boolean, val name: String, val poster: String,
    val season: Int, val episode: Int, val positionMs: Long, val durationMs: Long, val watchedAt: Long,
)

data class ImportReport(val added: Int, val skipped: Int, val failed: Int) {
    val total get() = added + skipped + failed
}

/**
 * Traz para o AOTV o que veio de outra conta. Addon entra no fim da fila, título vira o id do
 * TMDB (o app inteiro usa esse id) e progresso só entra quando é mais novo que o do aparelho.
 */
@Singleton
class AccountImporter @Inject constructor(
    private val prefs: PlatformPrefs,
    private val tmdb: TmdbApiService,
    private val library: LibraryRepository,
    private val stremio: StremioAddonService,
) {
    private val ids = ConcurrentHashMap<String, Pair<Int, com.aotv.app.domain.model.MovieItem?>>()

    suspend fun importAddons(list: List<RemoteAddon>, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportReport = withContext(Dispatchers.IO) {
        var added = 0; var skipped = 0; var failed = 0
        list.forEachIndexed { i, a ->
            onProgress(i + 1, list.size)
            val url = stremio.normalizeManifestUrl(a.url)
            when {
                !a.canUse || url.isBlank() -> skipped++
                prefs.addonIndex(url) >= 0 -> skipped++
                else -> {
                    // Lê o manifest antes, que é o que guarda os catálogos para a Início.
                    val m = stremio.getManifest(url)
                    if (m == null) failed++ else { prefs.installAddon(url, m.id); added++ }
                }
            }
        }
        ImportReport(added, skipped, failed)
    }

    suspend fun importLibrary(items: List<RemoteLibraryItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportReport {
        val profile = prefs.profileId.value
        val done = java.util.concurrent.atomic.AtomicInteger()
        val results = parallel(items) { it ->
            val (tmdbId, found) = resolve(it.id, it.series) ?: return@parallel 2.also { onProgress(done.incrementAndGet(), items.size) }
            val meta = found ?: if (it.name.isBlank() || it.poster.isBlank()) details(tmdbId, it.series) else null
            val ok = library.importToList(
                tmdbId, if (it.series) MediaType.SERIES.name else MediaType.MOVIE.name,
                it.name.ifBlank { meta?.title.orEmpty() }, meta?.posterUrl?.ifBlank { null } ?: it.poster, meta?.year.orEmpty(),
                it.addedAt, profile,
            )
            onProgress(done.incrementAndGet(), items.size)
            if (ok) 0 else 1
        }
        return ImportReport(results.count { it == 0 }, results.count { it == 1 }, results.count { it == 2 })
    }

    suspend fun importProgress(items: List<RemoteProgress>, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportReport {
        val profile = prefs.profileId.value
        // Só o mais recente de cada título (série tem um por episódio).
        val latest = items.filter { it.positionMs > 0 }
            .groupBy { baseId(it.id) + it.series }
            .map { (_, g) -> g.maxBy { it.watchedAt } }
            .sortedByDescending { it.watchedAt }
            .take(60)
        val done = java.util.concurrent.atomic.AtomicInteger()
        val results = parallel(latest) { p ->
            val (tmdbId, found) = resolve(baseId(p.id), p.series)
                ?: return@parallel 2.also { onProgress(done.incrementAndGet(), latest.size) }
            // O progresso do Nuvio às vezes chega sem nome e sem pôster (id tmdb:). Sem isso o
            // Continuar assistindo ganhava cartão preto.
            val meta = found ?: if (p.name.isBlank() || p.poster.isBlank()) details(tmdbId, p.series) else null
            val ok = library.importPlayback(
                tmdbId, if (p.series) MediaType.SERIES.name else MediaType.MOVIE.name,
                p.name.ifBlank { meta?.title.orEmpty() }, meta?.posterUrl?.ifBlank { null } ?: p.poster,
                p.positionMs, p.durationMs, if (p.series && p.season > 0) "T${p.season}:E${p.episode}" else "",
                p.watchedAt, profile,
            )
            onProgress(done.incrementAndGet(), latest.size)
            if (ok) 0 else 1
        }
        return ImportReport(results.count { it == 0 }, results.count { it == 1 }, results.count { it == 2 })
    }

    /** Nome, pôster e ano pelo id do TMDB, para item que chegou da conta sem eles. */
    private suspend fun details(id: Int, series: Boolean): com.aotv.app.domain.model.MovieItem? {
        val apiKey = prefs.tmdbApiKey.ifBlank { return null }
        return runCatching {
            if (series) tmdb.getSeriesDetail(id, append = "", apiKey = apiKey).let { d ->
                com.aotv.app.domain.model.MovieItem(
                    tmdbId = d.id, embedId = d.id.toString(), title = d.name, overview = d.overview.orEmpty(),
                    posterUrl = d.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }.orEmpty(), backdropUrl = "",
                    year = d.firstAirDate.orEmpty().take(4), rating = d.voteAverage, type = MediaType.SERIES,
                )
            } else tmdb.getMovieDetail(id, append = "", apiKey = apiKey).toMovieItem()
        }.getOrNull()
    }

    /** Id do título sem temporada e episódio ("tt1:2:3" vira "tt1", "tmdb:5:1:2" vira "tmdb:5"). */
    private fun baseId(id: String) = if (id.startsWith("tmdb:")) id.split(':').take(2).joinToString(":") else id.substringBefore(':')

    /** Id do Stremio para o do TMDB. Aceita "tt123" e "tmdb:123". null se não achar. */
    private suspend fun resolve(id: String, series: Boolean): Pair<Int, com.aotv.app.domain.model.MovieItem?>? {
        val key = "$series|$id"
        ids[key]?.let { return it }
        Regex("^tmdb:(\\d+)").find(id)?.groupValues?.get(1)?.toIntOrNull()?.let { return (it to null).also { r -> ids[key] = r } }
        val tt = Regex("^tt\\d+").find(id)?.value ?: return null
        val apiKey = prefs.tmdbApiKey.ifBlank { return null }
        val r = runCatching { tmdb.findByImdb(tt, apiKey = apiKey) }.getOrNull() ?: return null
        val hit = if (series) r.tvResults.firstOrNull() ?: r.movieResults.firstOrNull() else r.movieResults.firstOrNull() ?: r.tvResults.firstOrNull()
        val item = hit?.toMovieItem() ?: return null
        return (item.tmdbId to item).also { ids[key] = it }
    }

    private suspend fun <T> parallel(items: List<T>, block: suspend (T) -> Int): List<Int> = coroutineScope {
        val gate = Semaphore(6)
        items.map { async(Dispatchers.IO) { gate.withPermit { runCatching { block(it) }.getOrDefault(2) } } }.awaitAll()
    }
}
