package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.StremioAddonService
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.bestLogoUrl
import com.aotv.app.data.remote.dto.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolve a URL de um episódio diretamente por (tmdbId, season, episode) — usado pelo
 * player para os botões "Próximo/Anterior episódio", sem depender do estado da tela de detalhe.
 *
 * As fontes vêm só dos addons Stremio e tocam no ExoPlayer. O player tenta manter o mesmo
 * addon e a mesma qualidade do episódio anterior.
 */
@HiltViewModel
class EpisodePlayerViewModel @Inject constructor(
    private val stremio: StremioAddonService,
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
    private val subtitleService: com.aotv.app.data.remote.SubtitleService,
    private val prober: com.aotv.app.data.remote.SourceProber,
    private val debrid: com.aotv.app.data.debrid.DebridService,
) : ViewModel() {

    /** Serviço de debrid em uso ("Real-Debrid"). null sem conta ou desligado. */
    fun debridLabel(): String? = debrid.active()?.provider?.label
    /** O serviço tem esse torrent pronto? null quando ainda não se sabe. */
    fun debridCached(url: String): Boolean? = debrid.knownCached(url)
    /** Link direto já resolvido, sem rede (serve para o Chromecast). */
    fun debridNow(url: String, season: Int, episode: Int): String? = debrid.resolvedNow(url, season, episode)
    suspend fun debridResolve(url: String, season: Int, episode: Int) = debrid.resolve(url, season, episode)
    /** Torrent sem cache fica de fora (ajuste do Debrid). */
    fun debridHideUncached(): Boolean = debrid.settings.value.hideUncached

    /** Teste da fonte já feito (no detalhe ou aqui), sem esperar. */
    fun probeCached(url: String) = prober.cached(url)
    suspend fun probe(url: String) = prober.probe(url)

    /** Duração do filme no TMDB, em minutos. 0 = desconhecida. */
    suspend fun movieRuntime(tmdbId: Int): Int {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0) return 0
        return runCatching { tmdb.getMovieDetail(tmdbId, apiKey = apiKey).runtime }.getOrNull() ?: 0
    }

    /** Legendas externas (OpenSubtitles) do filme/episódio. Vazio se sem imdb. */
    suspend fun subtitlesFor(tmdbId: Int, type: String, season: Int, episode: Int): List<com.aotv.app.data.remote.ExternalSub> {
        val imdb = imdbId(tmdbId, type) ?: return emptyList()
        return subtitleService.subtitles(imdb, type, season, episode)
    }

    /** Falas de uma legenda externa (SRT ou VTT). Vazio se não baixou. */
    suspend fun subtitleCues(url: String): List<SubCue> =
        subtitleService.download(url)?.let { parseSubs(it) }.orEmpty()

    // Cache do IMDB ID por tmdbId p/ não re-resolver a cada troca de episódio.
    private val imdbCache = mutableMapOf<Int, String?>()

    private suspend fun resolveImdb(tmdbId: Int): String? = imdbCache.getOrPut(tmdbId) {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0) null
        else runCatching { tmdb.getSeriesExternalIds(tmdbId, apiKey).imdbId }
            .getOrNull()?.takeIf { it.startsWith("tt") }
    }

    /** Nº de episódios da temporada (TMDB). 0 se desconhecido. Habilita "próximo episódio" no resume. */
    suspend fun seasonEpisodeCount(tmdbId: Int, season: Int): Int {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0 || season <= 0) return 0
        return runCatching { tmdb.getSeason(tmdbId, season, apiKey = apiKey).episodes.size }.getOrDefault(0)
    }

    private val seasonCache = mutableMapOf<Pair<Int, Int>, List<com.aotv.app.domain.model.EpisodeItem>>()

    suspend fun seasonEpisodes(tmdbId: Int, season: Int): List<com.aotv.app.domain.model.EpisodeItem> {
        seasonCache[tmdbId to season]?.let { return it }
        if (prefs.tmdbApiKey.isBlank() || tmdbId <= 0 || season <= 0) return emptyList()
        val episodes = runCatching { tmdb.getSeason(tmdbId, season, apiKey = prefs.tmdbApiKey).episodes.map { it.toDomain() } }
            .getOrDefault(emptyList())
        if (episodes.isNotEmpty()) {
            if (seasonCache.size >= 10) seasonCache.remove(seasonCache.keys.first())
            seasonCache[tmdbId to season] = episodes
        }
        return episodes
    }

    /** Nº de temporadas da série (p/ oferecer "Próxima Temporada" no fim da última). 0 se falhar. */
    suspend fun seasonCount(tmdbId: Int): Int {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0) return 0
        return runCatching { tmdb.getSeriesDetail(tmdbId, apiKey = apiKey).numberOfSeasons }.getOrDefault(0)
    }

    /** Logo do título via TMDB (PT-BR). "" se não houver. Player usa como principal (metahub fallback). */
    suspend fun tmdbLogoUrl(tmdbId: Int, type: String): String {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0) return ""
        return runCatching {
            if (type == "SERIES") tmdb.getSeriesImages(tmdbId, apiKey = apiKey)
            else tmdb.getMovieImages(tmdbId, apiKey = apiKey)
        }.getOrNull().bestLogoUrl()
    }

    /** IMDB id (tt...) por tmdbId, respeitando o tipo (série/filme). Usado p/ logo/background metahub. */
    suspend fun imdbId(tmdbId: Int, type: String): String? {
        if (type == "SERIES") return resolveImdb(tmdbId)
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank() || tmdbId <= 0) return null
        return runCatching { tmdb.getMovieExternalIds(tmdbId, apiKey).imdbId }
            .getOrNull()?.takeIf { it.startsWith("tt") }
    }

    /**
     * Streams Stremio (HTTP direto) do episódio, ordenados por qualidade (4K→720).
     * Vazio se não houver addon, imdb ou streams. Cada item vira um MoviePlayerSource.
     */
    suspend fun stremioEpisodeSources(tmdbId: Int, season: Int, episode: Int): List<MoviePlayerSource> {
        val addons = prefs.stremioAddonList()
        if (addons.isEmpty()) return emptyList()
        // Sem IMDB ainda dá pra tentar: addon que indexa por TMDB responde a "tmdb:{id}".
        val imdb = resolveImdb(tmdbId).orEmpty()
        if (imdb.isBlank() && tmdbId <= 0) return emptyList()
        return runCatching { stremio.getStreamsFrom(addons, "series", imdb, season, episode, tmdbId) }
            .getOrDefault(emptyList())
            // getStreamsFrom já devolve na ordem dos addons; só ordena por qualidade DENTRO
            // de cada addon, pra prioridade do gerenciador continuar valendo.
            .groupBy { it.addon }.values.flatMap { g -> g.sortedByDescending { it.qualityRank } }
            .let { prefs.sortStreams(it) }
            .map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) }
    }

    // ── Abertura rápida do próximo episódio ───────────────────────────────────
    // Dois ganhos. (1) Cache pré-carregado enquanto o episódio atual toca, aí o "Próximo"
    // não faz rede nenhuma. (2) Entrega progressiva, que começa a tocar no primeiro addon
    // que responde em vez de esperar o mais lento da lista.

    private class CachedEp(val at: Long, val sources: List<MoviePlayerSource>)

    private val epCache = mutableMapOf<String, CachedEp>()
    private val inFlight = mutableSetOf<String>()

    private fun key(tmdbId: Int, season: Int, episode: Int) = "$tmdbId:$season:$episode"

    /** Fontes já prontas do episódio, ou null. Link de addon expira, então vale 15 min. */
    fun cachedEpisodeSources(tmdbId: Int, season: Int, episode: Int): List<MoviePlayerSource>? {
        val c = epCache[key(tmdbId, season, episode)] ?: return null
        if (System.currentTimeMillis() - c.at > EP_CACHE_TTL_MS) {
            epCache.remove(key(tmdbId, season, episode))
            return null
        }
        return c.sources.takeIf { it.isNotEmpty() }
    }

    /** Resolve o episódio em background e guarda no cache. Chamado no meio do episódio atual. */
    fun prefetchEpisode(tmdbId: Int, season: Int, episode: Int) {
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return
        val k = key(tmdbId, season, episode)
        if (k in inFlight || cachedEpisodeSources(tmdbId, season, episode) != null) return
        inFlight += k
        viewModelScope.launch {
            val sources = runCatching { stremioEpisodeSources(tmdbId, season, episode) }.getOrDefault(emptyList())
            if (sources.isNotEmpty()) epCache[k] = CachedEp(System.currentTimeMillis(), sources)
            inFlight -= k
        }
    }

    /**
     * Igual [stremioEpisodeSources], mas chama [onPartial] a cada addon que responde. Quem
     * chama toca a primeira fonte que chegar e deixa as outras irem entrando no seletor.
     * No fim guarda o resultado completo no cache.
     */
    suspend fun stremioEpisodeSourcesProgressive(
        tmdbId: Int,
        season: Int,
        episode: Int,
        onPartial: (List<MoviePlayerSource>) -> Unit,
    ) {
        val addons = prefs.stremioAddonList()
        if (addons.isEmpty()) { onPartial(emptyList()); return }
        val imdb = resolveImdb(tmdbId).orEmpty()
        if (imdb.isBlank() && tmdbId <= 0) { onPartial(emptyList()); return }
        var last: List<MoviePlayerSource> = emptyList()
        runCatching {
            stremio.streamsProgressive(addons, "series", imdb, season, episode, tmdbId) { partial ->
                last = prefs.sortStreams(partial).map { MoviePlayerSource(it.label, it.url, addon = it.addon, stream = it) }
                onPartial(last)
            }
        }
        if (last.isNotEmpty()) epCache[key(tmdbId, season, episode)] = CachedEp(System.currentTimeMillis(), last)
    }

    private companion object {
        const val EP_CACHE_TTL_MS = 15 * 60_000L
    }
}
