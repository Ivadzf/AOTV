package com.aotv.app.data.repository

import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.local.posterKey
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.data.remote.dto.toDomain
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ContinueArtwork(
    val title: String = "",
    val posterUrl: String = "",
    val imageUrl: String = "",
    val episodeName: String = "",
    val overview: String = "",
)

/** Arte do título e do episódio. Compartilhada entre a Início e a biblioteca. */
@Singleton
class ContinueArtworkRepository @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
) {
    private data class Cached(val at: Long, val value: ContinueArtwork)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val permits = Semaphore(3)

    suspend fun resolve(item: MovieItem, episode: String): ContinueArtwork {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || item.tmdbId <= 0) return ContinueArtwork()
        val key = posterKey(item) + ":" + episode
        fun cached() = cache[key]?.takeIf { System.currentTimeMillis() - it.at < 12 * 3_600_000L }?.value
        cached()?.let { return it }
        return permits.withPermit {
            cached()?.let { return@withPermit it }
            val art = if (item.type == MediaType.SERIES) {
                val detail = runCatching { tmdb.getSeriesDetail(item.tmdbId, append = "", apiKey = apiKey).toDomain() }.getOrNull()
                val numbers = Regex("T(\\d+)[ :]*E(\\d+)", RegexOption.IGNORE_CASE).find(episode)
                val season = numbers?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val number = numbers?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val ep = if (season > 0 && number > 0) runCatching {
                    tmdb.getSeason(item.tmdbId, season, apiKey = apiKey).episodes.firstOrNull { it.episodeNumber == number }
                }.getOrNull() else null
                ContinueArtwork(
                    title = detail?.title.orEmpty(), posterUrl = detail?.posterUrl.orEmpty(),
                    imageUrl = ep?.stillPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }
                        ?: detail?.backdropUrl.orEmpty(),
                    episodeName = ep?.name.orEmpty(), overview = ep?.overview.orEmpty(),
                )
            } else {
                val detail = runCatching { tmdb.getMovieDetail(item.tmdbId, apiKey = apiKey).toMovieItem() }.getOrNull()
                ContinueArtwork(detail?.title.orEmpty(), detail?.posterUrl.orEmpty(), detail?.backdropUrl.orEmpty())
            }
            currentCoroutineContext().ensureActive()
            if (art.title.isNotBlank() || art.imageUrl.isNotBlank()) {
                if (cache.size >= 200) cache.entries.minByOrNull { it.value.at }?.let { cache.remove(it.key, it.value) }
                cache[key] = Cached(System.currentTimeMillis(), art)
            }
            art
        }
    }
}
