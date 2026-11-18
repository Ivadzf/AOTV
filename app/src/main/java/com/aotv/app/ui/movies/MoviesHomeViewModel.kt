package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.TmdbGenreDto
import com.aotv.app.data.remote.dto.bestLogoUrl
import com.aotv.app.data.remote.dto.bestTrailer
import com.aotv.app.data.remote.dto.certBR
import com.aotv.app.data.remote.dto.trailerKeys
import com.aotv.app.data.remote.dto.TmdbNextEpisodeDto
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.data.remote.dto.toSeriesItem
import com.aotv.app.data.sports.SportsGame
import com.aotv.app.data.sports.upcomingSportsGames
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MoviesHomeViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
    private val library: com.aotv.app.data.repository.LibraryRepository,
    /** Cache do detalhe de série (Lote 2, v1.10.0): compartilhado com o Calendário da
     * Biblioteca — a série buscada aqui NÃO é buscada de novo lá (TTL 6h). */
    private val seriesDetailCache: com.aotv.app.data.remote.SeriesDetailCache,
    private val youtubeTrailer: com.aotv.app.data.remote.YoutubeTrailer,
    private val stremio: com.aotv.app.data.remote.StremioAddonService,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val addonMetas: com.aotv.app.data.remote.AddonMetaResolver,
    collections: com.aotv.app.data.collections.CollectionsRepository,
    private val posterInfo: com.aotv.app.data.local.PosterInfo,
    private val continueArt: com.aotv.app.data.repository.ContinueArtworkRepository,
    /** Guia ao vivo (Lote 8, v1.18.0): os jogos da fileira "Próximos" vêm do EPG. */
    private val liveTv: com.aotv.app.data.live.LiveTvRepository,
    /** Catálogos de TV dos addons (Lote 16, v1.26.0): as fileiras de TV da Início. */
    private val tvCatalogs: com.aotv.app.data.remote.AddonTvCatalogLoader,
    /** Séries acompanhadas nos trackers, p/ o auto-add do calendário (Lote 13, v1.23.0). */
    private val trackerShows: com.aotv.app.data.remote.TrackerShowsCache,
) : ViewModel() {
    /** Coleções do perfil, para as fileiras de pastas da Início. */
    val collectionsFlow: StateFlow<List<com.aotv.app.data.collections.MediaCollection>> = collections.flow

    init {
        // Abrir a Início (e trocar de perfil, que reabre o app) sincroniza com o Trakt.
        // ponytail: sem o intervalo de 30 min do webOS, a sincronia roda a cada abertura da Início.
        trakt.syncInBackground()
    }


    /** Stream do trailer (YouTube extraído) p/ tocar no ExoPlayer. null se falhar. */
    suspend fun trailerStream(key: String): com.aotv.app.data.remote.YoutubeTrailer.TrailerStream? =
        if (key.isBlank()) null else youtubeTrailer.stream(key)

    enum class CatalogType { MOVIE, SERIES }

    /** De onde o "Ver Tudo" busca os itens. */
    enum class CatalogEndpoint { DISCOVER, TRENDING, NOW_PLAYING }

    /**
     * Descreve COMPLETAMENTE uma fileira/catálogo — fonte única de verdade do "Ver Tudo".
     * A fileira passa este objeto inteiro; o grid pagina exatamente o mesmo conteúdo.
     */
    data class CatalogQuery(
        val title: String,
        val type: CatalogType,
        val endpoint: CatalogEndpoint = CatalogEndpoint.DISCOVER,
        val genreId: Int? = null,
        val sort: String = SORT_POPULAR,
        val year: Int? = null,
        /** Gêneros combinados ("16,12" ou "16|10751"). Usado pelas fileiras do perfil infantil. */
        val genres: String? = null,
    )

    companion object {
        const val SORT_POPULAR = "popularity.desc"
        const val SORT_TOP_RATED = "vote_average.desc"
        const val SORT_RELEASE_MOVIE = "primary_release_date.desc"
        const val SORT_RELEASE_TV = "first_air_date.desc"
        // mínimo de votos para "Melhor nota" não trazer obscuridades com 1 voto 10/10
        private const val TOP_RATED_MIN_VOTES = 200
        /** Itens lidos por página do catálogo de addon (igual ao webOS). */
        private const val ADDON_PAGE = 40
        /** Itens por fileira On Demand na Início (o resto vive na tela On Demand). */
        private const val VOD_ROW_MAX = 20
        /** Intervalo mínimo entre rebuscas da fileira "Up Next — Trakt" (Lote 19 item 4). */
        private const val TRAKT_UP_NEXT_MIN_REFETCH_MS = 15 * 60 * 1000L
        /** Grades "Ver Tudo" de lista local finita (Lote 19, v1.29.0): valores de [UiState.localGrid]. */
        const val LOCAL_CONTINUE = "continue"
        const val LOCAL_NEW_EPISODES = "new_episodes"
        const val LOCAL_RECOMMENDED = "recommended"
        const val LOCAL_UP_NEXT = "up_next"
    }

    data class UiState(
        val trendingMovies: List<MovieItem> = emptyList(),
        val trendingTv: List<MovieItem> = emptyList(),
        // Logo-art (título estilizado) dos itens do hero: tmdbId -> url. Vazio = usa texto.
        val heroLogos: Map<Int, String> = emptyMap(),
        // Backdrop real do hero p/ itens sem backdrop (ex: "Continuar assistindo"): tmdbId -> url.
        val heroBackdrops: Map<Int, String> = emptyMap(),
        // Classificação indicativa do hero (catálogo não traz): tmdbId -> "L"/"10".../"18".
        val heroCerts: Map<Int, String> = emptyMap(),
        val movieGenres: List<TmdbGenreDto> = emptyList(),
        val tvGenres: List<TmdbGenreDto> = emptyList(),
        val selectedTab: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null,
        val hasTmdbKey: Boolean = false,
        // ── Catálogo paginado (scroll infinito) ──
        val catalogType: CatalogType = CatalogType.MOVIE,
        val selectedGenreId: Int? = null,        // null = Todos
        val sortBy: String = SORT_POPULAR,
        val catalogItems: List<MovieItem> = emptyList(),
        val catalogPage: Int = 1,
        val catalogTotalPages: Int = 1,
        val isLoadingCatalog: Boolean = false,
        val isLoadingMore: Boolean = false,
        // ── Grid "Ver Tudo" do celular (estado próprio, independente do catálogo da TV) ──
        val gridQuery: CatalogQuery? = null,
        val gridItems: List<MovieItem> = emptyList(),
        val gridPage: Int = 1,
        val gridTotalPages: Int = 1,
        val gridLoading: Boolean = false,
        val gridLoadingMore: Boolean = false,
        // ── Grid "Ver Tudo" de fileira de addon (Lote 19, v1.29.0) ──
        // Catálogo de addon (filme/série): a grade pagina o MESMO catálogo
        // da fileira (skip de 40 em 40), sem tocar no skip da fileira.
        val addonGridKey: String? = null,
        val addonGridTitle: String = "",
        val addonGridItems: List<MovieItem> = emptyList(),
        val addonGridSkip: Int = 0,
        val addonGridMore: Boolean = true,
        val addonGridLoading: Boolean = false,
        val addonGridLoadingMore: Boolean = false,
        // ── Grid "Ver Tudo" de fileira de TV de addon ──
        // Canais do MESMO catálogo de TV da fileira (skip de 40 em 40).
        // O toque abre o ao vivo, como o card da fileira.
        val tvGridKey: String? = null,
        val tvGridTitle: String = "",
        val tvGridChannels: List<com.aotv.app.data.live.LiveChannel> = emptyList(),
        val tvGridSkip: Int = 0,
        val tvGridMore: Boolean = true,
        val tvGridLoading: Boolean = false,
        val tvGridLoadingMore: Boolean = false,
        // ── Grid "Ver Tudo" de lista local finita (Lote 19, v1.29.0) ──
        // Continuar assistindo, Novos episódios, Up Next do Trakt ou
        // Recomendados: a lista JÁ está no estado — a grade só mostra tudo,
        // sem buscar nada. Valores: [LOCAL_CONTINUE], [LOCAL_NEW_EPISODES],
        // [LOCAL_UP_NEXT] ou [LOCAL_RECOMMENDED].
        val localGrid: String? = null,
        // ── Grid "Ver Tudo" de trailers, coleção e próximos ──
        // Também só mostram o que já está no estado (aba de trailer atual,
        // pastas da coleção, episódios + jogos). null/false = grade fechada.
        val trailersGrid: Boolean = false,
        val collectionGridId: String? = null,
        val upcomingGrid: Boolean = false,
        // ── Continuar assistindo (home TV/Cinema) ──
        val continueWatching: List<MovieItem> = emptyList(),
        val continueProgress: Map<Int, Float> = emptyMap(),  // tmdbId -> fração assistida
        val continueRemaining: Map<Int, Int> = emptyMap(),   // tmdbId -> minutos restantes
        val continueEpisode: Map<Int, String> = emptyMap(),  // tmdbId -> selo do episódio ("T2 E5")
        val continueArtwork: Map<String, com.aotv.app.data.repository.ContinueArtwork> = emptyMap(),
        // ── Fileiras fixas da home estilo Netflix ──
        val nowPlaying: List<MovieItem> = emptyList(),     // Filmes nos cinemas
        val releases2026: List<MovieItem> = emptyList(),   // Lançamentos 2026
        // ── Seções estilo Netflix (carregam mais conforme rola) ──
        val homeSections: List<HomeSection> = emptyList(),
        val isLoadingSections: Boolean = false,
        // ── Últimos Trailers ──
        val trailerTab: TrailerTab = TrailerTab.POPULAR,
        val latestTrailers: List<TrailerCard> = emptyList(),
        val trailersLoading: Boolean = false,
        // ── Recomendações montadas a partir do histórico ──
        val recommended: List<MovieItem> = emptyList(),
        /** Título que puxou a recomendação, pro cabeçalho da fileira. "" = esconde a fileira. */
        val recommendedBecause: String = "",
        /** Lote 21 (v1.31.0): títulos-semente das recomendações (histórico +
         * favoritos), p/ a tela "O que molda suas recomendações". */
        val tasteSeedTitles: List<String> = emptyList(),
        /** Lote 21 (v1.31.0): contagem de 👍/👎/favoritos do perfil. */
        val tasteLikesCount: Int = 0,
        val tasteDislikesCount: Int = 0,
        val tasteFavoritesCount: Int = 0,
        /** Lote 21 (v1.31.0): sinais de gosto com título resolvido. */
        val tasteEntries: List<TasteEntry> = emptyList(),
        // ── Fileiras de catálogo de addon, marcadas em Addons > Na Início ──
        val addonRows: List<AddonRow> = emptyList(),
        /**
         * Fileiras de TV dos addons (Lote 16, v1.26.0): catálogos type "tv" ou
         * "channel" marcados em Personalizar Início. Os canais são
         * [com.aotv.app.data.live.LiveChannel] (não MovieItem): o toque abre
         * o ao vivo, não o detalhe — por isso NÃO entram em [addonRows]
         * (o resolver de lá descarta meta sem id IMDb/TMDB e a tela de detalhe
         * não toca canal). A renderização na Início ainda não existe (ver
         * GAP no relatório do Lote 16): os dados já estão aqui.
         */
        val tvAddonRows: List<TvAddonRow> = emptyList(),
        /**
         * On Demand do Xtream (acréscimo v1.26.0): filmes e séries do painel
         * como conteúdo, nas fileiras [SystemRow.VOD_MOVIES]/[SystemRow.VOD_SERIES].
         * 1ª fonte Xtream ligada que devolve VOD (a mesma do On Demand);
         * fonte só-M3U não tem VOD e não gera fileira. [vodChecked] = já
         * verificou (mesmo que vazio — sem conta, sem rede ou sem VOD, a
         * fileira some em silêncio e não tenta de novo na sessão).
         */
        val vodMovies: List<com.aotv.app.data.live.VodItem> = emptyList(),
        val vodSeries: List<com.aotv.app.data.live.VodItem> = emptyList(),
        val vodLoading: Boolean = false,
        val vodChecked: Boolean = false,
        // ── Novos episódios das séries do histórico e da Minha Lista ──
        val newEpisodes: List<NewEpisode> = emptyList(),
        // ── Fileira "Up Next — Trakt" (Lote 19 item 4, v1.29.0): próximo
        // episódio de cada série em andamento no Trakt (ver
        // TraktService.getUpNext). Mesmo formato de Novos episódios
        // (selo "T3 E2" + nome do episódio) — o toque abre o detalhe da série.
        val traktUpNext: List<NewEpisode> = emptyList(),
        // ── Fileira "Próximos" (Lote 8, v1.18.0): episódios + jogos de esporte ──
        val upcomingEpisodes: List<CalendarEntry> = emptyList(),
        val upcomingGames: List<SportsGame> = emptyList(),
        val sportsShow: Boolean = true,
        val calendarHideSpoilers: Boolean = false,
        val sportsHideScores: Boolean = false,
        val sportsScoreDelaySec: Int = 30,
        val sportsLiveScores: Boolean = true,
        val sportsGameAlerts: Boolean = false,
        /** Trailer mudo no destaque, conforme a preferência do perfil. */
        val heroTrailer: Boolean = true,
        /** Exibição da Início (Fase B, frente compacta): tamanho do pôster, densidade, selos, herói, fundo e sublinhado. */
        val display: com.aotv.app.data.local.HomeDisplayPrefs = com.aotv.app.data.local.HomeDisplayPrefs(),
        /** Fileiras que aparecem, na ordem do perfil (Personalizar Início). Já sem as desligadas. */
        val layout: List<com.aotv.app.data.local.HomeRowPref> = emptyList(),
        /** Catálogo AOTV ligado (fileiras do TMDB). */
        val catalogOn: Boolean = true,
        /** Chave da fileira que alimenta o destaque. Vazio = automático. */
        val heroSource: String = "",
        /** Destaque ligado (Bloco 3, Lote 15). Desligado, nem a Início nem a busca mostram o carrossel. */
        val heroOn: Boolean = true,
        /** Catálogos que alimentam o destaque no automático. null = padrão. */
        val heroCatalogs: Set<String>? = null,
        /** Ocultar conteúdo não lançado nas fileiras e no destaque. */
        val hideUnreleased: Boolean = false,
        /** Sufixo de tipo (Filme/Série) nos títulos das fileiras de addon. */
        val typeSuffix: Boolean = false,
        val releasesYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    )

    /** Fileira de um catálogo de addon. [skip] é quanto já foi lido do catálogo. */
    data class AddonRow(
        val ref: com.aotv.app.data.local.HomeCatalogRef,
        val items: List<MovieItem> = emptyList(),
        val skip: Int = 0,
        val more: Boolean = true,
        val loading: Boolean = true,
        /** Nome na Início (o que a pessoa escolheu ou o do catálogo). */
        val title: String = ref.name,
    )

    /** Fileira de um catálogo de TV de addon (Lote 16, v1.26.0). 1ª página só; sem paginação. */
    data class TvAddonRow(
        val ref: com.aotv.app.data.local.HomeCatalogRef,
        val channels: List<com.aotv.app.data.live.LiveChannel> = emptyList(),
        val loading: Boolean = true,
        /** Nome na Início (o que a pessoa escolheu ou o do catálogo). */
        val title: String = ref.name,
    )

    // ── Últimos Trailers ──────────────────────────────────────────────────────
    /** Abas da fileira de trailers (mesma divisão do site do TMDB). */
    enum class TrailerTab(val label: String) {
        POPULAR("Popular"), STREAMING("Streaming"), ON_TV("Na TV"),
        RENT("Para Alugar"), THEATERS("Nos Cinemas"),
    }

    /** Um card da fileira de trailers. [videoName] é o nome do vídeo no TMDB. */
    data class TrailerCard(
        val tmdbId: Int,
        val type: MediaType,
        val title: String,
        val videoKey: String,
        val videoName: String,
        val backdropUrl: String,
        val posterUrl: String,
    )

    /** Uma fileira do catálogo. [query] descreve exatamente o que a fileira mostra (usado no "Ver Tudo"). */
    data class HomeSection(
        val id: String,
        val title: String,
        val items: List<MovieItem>,
        val query: CatalogQuery,
    )
    private data class SectionSpec(val id: String, val query: CatalogQuery)
    private var sectionSpecs: List<SectionSpec> = emptyList()
    private var nextSpecIndex: Int = 0
    private val SECTIONS_PER_BATCH = 4

    // Cache por aba: trocar de aba de novo não refaz as ~15 chamadas de vídeo.
    private val trailerCache = mutableMapOf<TrailerTab, List<TrailerCard>>()

    private val _uiState = MutableStateFlow(UiState())

    /** Perfil infantil. Fixo durante a vida da tela, a troca de perfil reabre o app. */
    private val kids = prefs.isKids

    // ── Trailer mudo do destaque ─────────────────────────────────────────────
    private val heroTrailerCache = HashMap<String, com.aotv.app.data.remote.YoutubeTrailer.TrailerStream?>()

    /** Stream do trailer de um título do destaque (os 2 melhores do TMDB). null quando não há. */
    suspend fun heroTrailerStream(item: MovieItem): com.aotv.app.data.remote.YoutubeTrailer.TrailerStream? {
        val k = "${item.type}:${item.tmdbId}"
        if (heroTrailerCache.containsKey(k)) return heroTrailerCache[k]
        val apiKey = prefs.tmdbApiKey
        val keys = runCatching {
            if (item.type == MediaType.SERIES) tmdb.getTvVideos(item.tmdbId, apiKey = apiKey) else tmdb.getMovieVideos(item.tmdbId, apiKey = apiKey)
        }.getOrNull().trailerKeys().take(2)
        val st = keys.firstNotNullOfOrNull { runCatching { youtubeTrailer.stream(it) }.getOrNull() }
        heroTrailerCache[k] = st
        return st
    }

    // ── Novos episódios ──────────────────────────────────────────────────────
    private var lastNewEpKey = ""

    /** Busca o detalhe de cada série (até 15) e monta a fileira "Novos episódios". */
    private fun buildNewEpisodes(series: List<Triple<Int, String, String>>) {
        lastNewEpSeries = series
        // Fileira desligada não busca na rede (nem consome a chave: ao religar,
        // o applyLayout repõe porque a chave não andou).
        if (!sysRowOn(com.aotv.app.data.local.SystemRow.NEW_EPISODES.key)) return
        val key = series.joinToString(",") { it.first.toString() }
        if (key == lastNewEpKey) return
        lastNewEpKey = key
        if (series.isEmpty()) { _uiState.update { it.copy(newEpisodes = emptyList()) }; return }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val out = series.map { (id, title, poster) ->
                async {
                    // Detalhe via cache (Lote 2): a mesma série não é buscada 2x
                    // entre a Início e o Calendário da Biblioteca.
                    val d = seriesDetailCache.get(id) ?: return@async null
                    val last = d.lastEpisodeToAir
                    val seen = last != null && library.isEpisodeWatched(id, last.season, last.episode)
                    val item = MovieItem(
                        tmdbId = id, embedId = id.toString(), title = title.ifBlank { d.name }, overview = d.overview.orEmpty(),
                        posterUrl = poster.ifBlank { d.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }.orEmpty() },
                        backdropUrl = d.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty(),
                        year = "", rating = d.voteAverage, type = MediaType.SERIES,
                    )
                    newEpisodeOf(item, last, d.nextEpisodeToAir, seen, now)
                }
            }.awaitAll().filterNotNull()
            _uiState.update { it.copy(newEpisodes = sortNewEpisodes(out).filterNot { ep -> prefs.isNotToday(ep.item.tmdbId) }) }
        }
    }
    // ── Fileira "Próximos" (Lote 8, v1.18.0) ─────────────────────────────
    // Próximos episódios: o MESMO conjunto e o MESMO cache da fileira
    // "Novos episódios" (calendarEntriesOf: sem air_date, sem linha) e do
    // Calendário da Biblioteca. Só entram os que ainda vão ao ar (hoje em
    // diante) — o último episódio já exibido fica só na Biblioteca.
    private var lastUpcomingKey = ""

    // ── Fileira "Up Next — Trakt" (Lote 19 item 4, v1.29.0) ─────────────
    private var lastTraktUpNextKey = ""
    private var lastTraktUpNextAt = 0L

    /**
     * Carrega a fileira "Up Next — Trakt": o próximo episódio de cada série
     * em andamento no Trakt (TraktService.getUpNext, no máximo 10). Pôsteres
     * e sinopse vêm do cache de detalhe de série (Lote 2), como em Novos
     * episódios — sem chave TMDB a fileira fica vazia e some. Sem Trakt
     * conectado, fileira vazia (nunca erro na tela). Rebusca no máximo 1x
     * a cada 15 min ([TRAKT_UP_NEXT_MIN_REFETCH_MS]) p/ a abertura da Início
     * e a mudança do histórico não martelarem a API; [force] ignora o
     * intervalo (p/ quando o usuário acabou de conectar o Trakt). A chave
     * evita refazer os detalhes do TMDB quando nada mudou no Trakt.
     */
    fun loadTraktUpNext(force: Boolean = false) {
        // Fileira desligada não busca na rede (ao religar, o applyLayout repõe).
        if (!sysRowOn(com.aotv.app.data.local.SystemRow.TRAKT_UP_NEXT.key)) return
        if (!trakt.isConnected) {
            if (_uiState.value.traktUpNext.isNotEmpty()) _uiState.update { it.copy(traktUpNext = emptyList()) }
            return
        }
        val now = System.currentTimeMillis()
        if (!force && _uiState.value.traktUpNext.isNotEmpty() && now - lastTraktUpNextAt < TRAKT_UP_NEXT_MIN_REFETCH_MS) return
        viewModelScope.launch {
            val items = trakt.getUpNext().getOrNull().orEmpty()
            val key = items.joinToString(",") { "${it.tmdbId}:${it.season}:${it.episode}" }
            if (!force && key == lastTraktUpNextKey && _uiState.value.traktUpNext.isNotEmpty()) return@launch
            lastTraktUpNextKey = key
            lastTraktUpNextAt = System.currentTimeMillis()
            if (items.isEmpty()) {
                _uiState.update { it.copy(traktUpNext = emptyList()) }
                return@launch
            }
            val out = items.map { up ->
                async {
                    // Detalhe via cache (Lote 2): a mesma série não é buscada 2x
                    // entre a Início e o Calendário da Biblioteca.
                    val d = seriesDetailCache.get(up.tmdbId) ?: return@async null
                    val item = MovieItem(
                        tmdbId = up.tmdbId, embedId = up.tmdbId.toString(),
                        title = up.title.ifBlank { d.name }, overview = d.overview.orEmpty(),
                        posterUrl = d.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }.orEmpty(),
                        backdropUrl = d.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty(),
                        year = up.year?.takeIf { it > 0 }?.toString().orEmpty(), rating = d.voteAverage, type = MediaType.SERIES,
                    )
                    NewEpisode(item, badge = "T${up.season} E${up.episode}", sub = up.episodeTitle.orEmpty(), at = System.currentTimeMillis(), upcoming = false)
                }
            }.awaitAll().filterNotNull()
            _uiState.update { it.copy(traktUpNext = out) }
        }
    }

    /** Monta os próximos episódios da fileira "Próximos". */
    private fun buildUpcomingEpisodes(series: List<Triple<Int, String, String>>) {
        val key = series.joinToString(",") { it.first.toString() }
        if (key == lastUpcomingKey) return
        lastUpcomingKey = key
        if (series.isEmpty()) { _uiState.update { it.copy(upcomingEpisodes = emptyList()) }; return }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = todayMs()
            val out = series.map { (id, title, poster) ->
                async {
                    val d = seriesDetailCache.get(id) ?: return@async emptyList<CalendarEntry>()
                    val last = d.lastEpisodeToAir
                    val seen = last != null && library.isEpisodeWatched(id, last.season, last.episode)
                    calendarEntriesOf(id, title.ifBlank { d.name }, poster, d, seen, now)
                }
            }.awaitAll().flatten()
                .filter { !it.isLastAired && it.atMs >= today }
            _uiState.update { it.copy(upcomingEpisodes = sortCalendarEntries(out).take(10)) }
        }
    }

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Assinatura do histórico já processado — evita refazer a busca a cada tick do Flow. */
    private var lastRecKey: String = ""

    /**
     * Recomendações a partir do que o usuário JÁ ASSISTIU.
     *
     * Pega os títulos mais recentes do histórico e junta as recomendações do TMDB de cada um.
     * O que aparece em MAIS de uma origem sobe (gostou de dois filmes que apontam pro mesmo
     * título = sinal forte), e tudo que ele já viu sai da lista.
     *
     * Lote 21 (v1.31.0) — "Picked for you" explicável: os favoritos do gosto
     * (3+, [TASTE_MIN_FAVORITES]) viram sementes junto com o histórico, e o
     * 👎 tira o título da lista. A chave inclui os sinais de gosto, então
     * marcar 👍/👎 no detalhe reconstrói a fileira ao voltar p/ a Início
     * (ver [refreshRecommendations]).
     */
    private fun buildRecommendations(history: List<com.aotv.app.data.local.entity.MovieHistoryEntity>) {
        lastHistory = history
        // Ao vivo (Lote 8, v1.18.0): canais no histórico (tipo "LIVE") não
        // são títulos do TMDB — não viram semente de recomendação.
        val history = history.filter { it.type != com.aotv.app.ui.live.LivePlayerViewModel.LIVE_TYPE }
        // Recomendação é do catálogo AOTV (TMDB). Desligado, nem chama a rede.
        if (!prefs.catalogOn) return
        // Fileira desligada não busca na rede (ao religar, o applyLayout repõe).
        if (!sysRowOn(com.aotv.app.data.local.SystemRow.RECOMMENDED.key)) return
        val apiKey = prefs.tmdbApiKey
        val likes = parseTasteCsv(prefs.tasteLikes)
        val dislikes = parseTasteCsv(prefs.tasteDislikes)
        val favorites = parseTasteCsv(prefs.tasteFavorites)
        val dislikedIds = dislikes.mapNotNull { tasteIdOf(it) }.toSet()
        if (apiKey.isBlank() || history.isEmpty()) {
            if (history.isEmpty()) _uiState.update { it.copy(recommended = emptyList(), recommendedBecause = "") }
            return
        }
        // Base: os 5 mais recentes + favoritos do gosto (3+). Chave evita
        // refazer quando só o progresso mudou.
        val seeds = history.take(5)
        val favSeeds = if (favorites.size >= TASTE_MIN_FAVORITES) {
            favorites.mapNotNull { key ->
                val id = tasteIdOf(key) ?: return@mapNotNull null
                val type = tasteTypeOf(key)
                if (id in seeds.map { it.tmdbId }) return@mapNotNull null
                id to type
            }
        } else emptyList()
        val key = (seeds.joinToString(",") { "${it.tmdbId}:${it.type}" } +
            "|fav:" + favSeeds.joinToString(",") { "${it.first}:${it.second}" } +
            "|no:" + dislikes.sorted().joinToString(","))
        if (key == lastRecKey) return
        lastRecKey = key

        viewModelScope.launch {
            val watched = history.mapTo(HashSet()) { it.tmdbId }
            val lists = (seeds.map { it.tmdbId to it.type } + favSeeds).map { (id, type) ->
                async {
                    val isSeries = type == MediaType.SERIES.name
                    runCatching {
                        if (isSeries) tmdb.getTvRecommendations(id, apiKey = apiKey).results
                        else tmdb.getMovieRecommendations(id, apiKey = apiKey).results
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()

            // Conta em quantas origens cada título apareceu.
            val hits = HashMap<Int, Int>()
            val byId = LinkedHashMap<Int, MovieItem>()
            lists.forEach { list ->
                list.asSequence()
                    .filterNot { it.adult }
                    .filter { !it.posterPath.isNullOrBlank() }
                    .map { it.toMovieItem() }
                    .filter { it.tmdbId !in watched && it.tmdbId !in dislikedIds && it.title.isNotBlank() }
                    .forEach { item ->
                        hits[item.tmdbId] = (hits[item.tmdbId] ?: 0) + 1
                        byId.putIfAbsent(item.tmdbId, item)
                    }
            }
            val ranked = byId.values
                .sortedWith(compareByDescending<MovieItem> { hits[it.tmdbId] ?: 0 }.thenByDescending { it.rating })
                .take(20)

            // Títulos-semente p/ a explicação ("O que molda suas
            // recomendações"): os do histórico + os favoritos que viraram
            // semente (título do histórico quando houver, senão o id).
            val byIdTitle = history.associate { it.tmdbId to it.title }
            val seedTitles = (seeds.map { it.title } + favSeeds.map { (id, _) -> byIdTitle[id] ?: "Título $id" })
                .distinct().take(8)
            _uiState.update {
                it.copy(
                    recommended = ranked,
                    recommendedBecause = if (ranked.isEmpty()) "" else seeds.first().title,
                    tasteSeedTitles = seedTitles,
                    tasteLikesCount = likes.size,
                    tasteDislikesCount = dislikes.size,
                    tasteFavoritesCount = favorites.size,
                    tasteEntries = buildTasteEntries(byIdTitle, ranked),
                )
            }
        }
    }

    /**
     * Sinais de gosto com título resolvido (p/ a tela de explicação):
     * título do histórico, senão dos recomendados, senão "Título {id}".
     */
    private fun buildTasteEntries(byIdTitle: Map<Int, String>, recommended: List<MovieItem> = emptyList()): List<TasteEntry> {
        val recTitles = recommended.associate { it.tmdbId to it.title }
        fun titleOf(key: String): String {
            val id = tasteIdOf(key) ?: return key
            return byIdTitle[id] ?: recTitles[id] ?: "Título $id"
        }
        val out = ArrayList<TasteEntry>()
        parseTasteCsv(prefs.tasteLikes).forEach { out += TasteEntry(it, titleOf(it), "like") }
        parseTasteCsv(prefs.tasteDislikes).forEach { out += TasteEntry(it, titleOf(it), "dislike") }
        parseTasteCsv(prefs.tasteFavorites).forEach { out += TasteEntry(it, titleOf(it), "favorite") }
        return out.sortedBy { it.title.lowercase() }
    }

    /** Atualiza as entradas de gosto com o que já se sabe (sem rede). */
    private fun refreshTasteEntries() {
        val byIdTitle = lastHistory.associate { it.tmdbId to it.title }
        _uiState.update {
            it.copy(
                tasteEntries = buildTasteEntries(byIdTitle, it.recommended),
                tasteLikesCount = parseTasteCsv(prefs.tasteLikes).size,
                tasteDislikesCount = parseTasteCsv(prefs.tasteDislikes).size,
                tasteFavoritesCount = parseTasteCsv(prefs.tasteFavorites).size,
            )
        }
    }

    /** Revalida as recomendações (a chave interna evita rede sem mudança). */
    fun refreshRecommendations() = buildRecommendations(lastHistory)

    /** 👍/👎/favorito do gosto p/ um título (o 👎 some das recomendações). */
    fun toggleTasteLike(tmdbId: Int, type: String) {
        val key = tasteKey(tmdbId, type)
        val likes = parseTasteCsv(prefs.tasteLikes).toMutableSet()
        val dislikes = parseTasteCsv(prefs.tasteDislikes).toMutableSet()
        if (key in likes) likes.remove(key) else { likes.add(key); dislikes.remove(key) }
        prefs.tasteLikes = tasteCsvOf(likes)
        prefs.tasteDislikes = tasteCsvOf(dislikes)
        refreshTasteEntries()
        refreshRecommendations()
    }

    fun toggleTasteDislike(tmdbId: Int, type: String) {
        val key = tasteKey(tmdbId, type)
        val likes = parseTasteCsv(prefs.tasteLikes).toMutableSet()
        val dislikes = parseTasteCsv(prefs.tasteDislikes).toMutableSet()
        if (key in dislikes) dislikes.remove(key) else { dislikes.add(key); likes.remove(key) }
        prefs.tasteLikes = tasteCsvOf(likes)
        prefs.tasteDislikes = tasteCsvOf(dislikes)
        refreshTasteEntries()
        refreshRecommendations()
    }

    fun toggleTasteFavorite(tmdbId: Int, type: String) {
        val key = tasteKey(tmdbId, type)
        val favorites = parseTasteCsv(prefs.tasteFavorites).toMutableSet()
        if (key in favorites) favorites.remove(key) else favorites.add(key)
        prefs.tasteFavorites = tasteCsvOf(favorites)
        refreshTasteEntries()
        refreshRecommendations()
    }

    /**
     * Resolve um item do histórico p/ o "Continuar assistindo": filme 90%+ assistido sai da
     * lista (retorna null). Série 90%+ no episódio atual tenta trocar pelo PRÓXIMO episódio
     * (mesma temporada, ou E1 da seguinte); sem próximo nenhum, também sai. Erro de rede mantém
     * o item como está, p/ não sumir da lista por uma falha passageira do TMDB.
     */
    private suspend fun resolveHistoryItem(h: com.aotv.app.data.local.entity.MovieHistoryEntity):
        com.aotv.app.data.local.entity.MovieHistoryEntity? {
        val finished = h.durationMs > 0 && h.positionMs >= h.durationMs * 0.9
        if (!finished) return h
        if (h.type != MediaType.SERIES.name) return null
        val m = Regex("T(\\d+):E(\\d+)").find(h.lastEpisode) ?: return null
        val season = m.groupValues[1].toIntOrNull() ?: return null
        val episode = m.groupValues[2].toIntOrNull() ?: return null
        if (season <= 0 || episode <= 0) return null
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return h
        val epsInSeason = runCatching { tmdb.getSeason(h.tmdbId, season, apiKey = apiKey).episodes.size }.getOrNull() ?: return h
        if (episode in 1 until epsInSeason) return h.copy(lastEpisode = "T$season:E${episode + 1}", positionMs = 0, durationMs = 0)
        val numberOfSeasons = runCatching { tmdb.getSeriesDetail(h.tmdbId, append = "", apiKey = apiKey).numberOfSeasons }.getOrNull() ?: return h
        if (season < numberOfSeasons) return h.copy(lastEpisode = "T${season + 1}:E1", positionMs = 0, durationMs = 0)
        return null
    }

    /** Troca a aba de trailers (usa cache quando já carregou). */
    fun selectTrailerTab(tab: TrailerTab) {
        val cached = trailerCache[tab]
        if (cached != null) {
            _uiState.update { it.copy(trailerTab = tab, latestTrailers = cached, trailersLoading = false) }
            return
        }
        _uiState.update { it.copy(trailerTab = tab, latestTrailers = emptyList(), trailersLoading = true) }
        viewModelScope.launch {
            val cards = runCatching { fetchTrailers(tab) }.getOrDefault(emptyList())
            trailerCache[tab] = cards
            _uiState.update {
                if (it.trailerTab != tab) it // usuário já trocou de aba, descarta
                else it.copy(latestTrailers = cards, trailersLoading = false)
            }
        }
    }

    /**
     * Monta a fileira de trailers. O TMDB não tem endpoint de "últimos trailers", então pega a
     * lista da aba e busca /videos de cada título EM PARALELO, ficando só com quem tem trailer.
     */
    private suspend fun fetchTrailers(tab: TrailerTab): List<TrailerCard> = coroutineScope {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return@coroutineScope emptyList()
        val isSeries = tab == TrailerTab.ON_TV
        val raw = runCatching {
            when (tab) {
                TrailerTab.POPULAR   -> tmdb.getTrendingMovies(apiKey = apiKey).results
                TrailerTab.STREAMING -> tmdb.discoverMovies(monetization = "flatrate", apiKey = apiKey).results
                TrailerTab.ON_TV     -> tmdb.getOnTheAir(apiKey = apiKey).results
                TrailerTab.RENT      -> tmdb.discoverMovies(monetization = "rent", apiKey = apiKey).results
                TrailerTab.THEATERS  -> tmdb.getNowPlaying(apiKey = apiKey).results
            }
        }.getOrDefault(emptyList())
        raw.asSequence()
            .filterNot { it.adult }
            .filter { !it.backdropPath.isNullOrBlank() }
            .take(14)
            .toList()
            .map { dto ->
                async {
                    val vids = runCatching {
                        if (isSeries) tmdb.getTvVideos(dto.id, apiKey = apiKey)
                        else tmdb.getMovieVideos(dto.id, apiKey = apiKey)
                    }.getOrNull()
                    val v = vids.bestTrailer() ?: return@async null
                    val item = dto.toMovieItem()
                    TrailerCard(
                        tmdbId = dto.id,
                        type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                        title = item.title,
                        videoKey = v.key,
                        videoName = v.name,
                        backdropUrl = "https://image.tmdb.org/t/p/w500${dto.backdropPath}",
                        posterUrl = item.posterUrl,
                    )
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    // ── Catálogos de addon na Início ──────────────────────────────────────────
    private val addonRowsInFlight = HashSet<String>()

    /** Lê a próxima página da fileira. Chamado ao montar e quando o fim da fileira aparece. */
    fun loadAddonRow(key: String) {
        val row = _uiState.value.addonRows.firstOrNull { it.ref.key == key } ?: return
        // Fileira de TV não passa pelo resolver de filme/série (o meta do
        // canal não tem id IMDb/TMDB e seria descartado): carrega por
        // [loadTvAddonRow] e vive em [UiState.tvAddonRows].
        if (row.ref.type == "tv" || row.ref.type == "channel") return
        if (!row.more || !addonRowsInFlight.add(key)) return
        viewModelScope.launch {
            val metas = stremio.catalogMetas(row.ref.base, row.ref.type, row.ref.id, row.skip)
            val page = metas.orEmpty().take(ADDON_PAGE)
            val items = resolveAddonMetas(page, row.ref.type == "series")
            addonRowsInFlight.remove(key)
            _uiState.update { st ->
                st.copy(addonRows = st.addonRows.map { r ->
                    if (r.ref.key != key) r
                    else r.copy(
                        // tmdbId 0 = item de addon com só o id IMDb; o embedId diferencia.
                        items = (r.items + items).distinctBy { it.tmdbId to it.embedId },
                        skip = r.skip + page.size,
                        more = metas != null && metas.size >= ADDON_PAGE,
                        loading = false,
                    )
                })
            }
        }
    }

    private suspend fun resolveAddonMetas(metas: List<com.aotv.app.data.remote.AddonMeta>, series: Boolean): List<MovieItem> =
        addonMetas.resolve(metas, series)

    // ── Fileiras de TV dos addons na Início (Lote 16, v1.26.0) ───────────────
    private val tvAddonRowsInFlight = HashSet<String>()

    /**
     * Monta o catálogo de TV a partir da referência da fileira. HomeCatalogRef
     * não guarda o id do manifest, só o nome do addon — o nome serve de
     * addonId aqui (só alimenta o sourceId "addon-tv:<addon>" do canal, p/
     * distinguir a fonte). Usado pela fileira ([loadTvAddonRow]) e pela grade
     * "Ver Tudo" ([openTvGrid]/[loadMoreTvGrid]).
     */
    private fun tvCatalogOf(ref: com.aotv.app.data.local.HomeCatalogRef): com.aotv.app.data.remote.AddonTvCatalog =
        com.aotv.app.data.remote.AddonTvCatalog(
            addonId = ref.addon,
            addonName = ref.addon,
            base = ref.base,
            id = ref.id,
            name = ref.name,
            type = ref.type,
        )

    /** Metas do catálogo → canais, sem id ou nome em branco e sem repetido. */
    private fun metasToChannels(
        catalog: com.aotv.app.data.remote.AddonTvCatalog,
        metas: List<com.aotv.app.data.remote.AddonMeta>,
    ): List<com.aotv.app.data.live.LiveChannel> =
        metas.take(ADDON_PAGE).mapNotNull { meta ->
            if (meta.id.isBlank() || meta.name.isBlank()) null
            else com.aotv.app.data.remote.AddonTvChannel(
                catalog, meta.id, meta.name,
                meta.poster.ifBlank { meta.background },
            ).toLiveChannel()
        }.distinctBy { it.id }

    /**
     * Lê a 1ª página de canais da fileira de TV. O catálogo vem do layout
     * (Personalizar Início); o type ("tv" ou "channel") decide as URLs, via
     * [com.aotv.app.data.remote.AddonTvCatalogLoader]. Sem paginação: a
     * fileira mostra a 1ª página (até 40 canais).
     */
    fun loadTvAddonRow(key: String) {
        val row = _uiState.value.tvAddonRows.firstOrNull { it.ref.key == key } ?: return
        if (!tvAddonRowsInFlight.add(key)) return
        viewModelScope.launch {
            val catalog = tvCatalogOf(row.ref)
            val metas = tvCatalogs.loadChannels(catalog).orEmpty()
            val channels = metasToChannels(catalog, metas)
            tvAddonRowsInFlight.remove(key)
            _uiState.update { st ->
                st.copy(tvAddonRows = st.tvAddonRows.map { r ->
                    if (r.ref.key != key) r else r.copy(channels = channels, loading = false)
                })
            }
        }
    }

    // ── Grades "Ver Tudo" (Lote 19, v1.29.0) ────────────────────────────────
    // Uma grade por vez: abrir uma fecha as outras (a Início some atrás da
    // grade, então não há como abrir duas ao mesmo tempo — a limpeza aqui é
    // defesa, não caminho normal). [closeCatalog] fecha qualquer uma delas.

    /** Zera o estado de todas as grades "Ver Tudo" (TMDB, addon, TV e locais). */
    private fun clearGrids(s: UiState): UiState = s.copy(
        gridQuery = null, gridItems = emptyList(), gridPage = 1, gridTotalPages = 1,
        gridLoading = false, gridLoadingMore = false,
        addonGridKey = null, addonGridTitle = "", addonGridItems = emptyList(),
        addonGridSkip = 0, addonGridMore = true, addonGridLoading = false, addonGridLoadingMore = false,
        tvGridKey = null, tvGridTitle = "", tvGridChannels = emptyList(),
        tvGridSkip = 0, tvGridMore = true, tvGridLoading = false, tvGridLoadingMore = false,
        localGrid = null, trailersGrid = false, collectionGridId = null, upcomingGrid = false,
    )

    /**
     * Abre a grade "Ver Tudo" da fileira de catálogo de addon [key]. Pagina
     * o MESMO catálogo da fileira (skip de [ADDON_PAGE] em [ADDON_PAGE]),
     * com o mesmo resolve da fileira — o toque no item abre o mesmo detalhe
     * que o toque no card da fileira. O skip da grade é próprio: a fileira
     * continua de onde estava quando a grade fecha.
     */
    fun openAddonGrid(key: String) {
        val row = _uiState.value.addonRows.firstOrNull { it.ref.key == key } ?: return
        // Já aberta com itens → mantém (voltando do detalhe).
        if (_uiState.value.addonGridKey == key && _uiState.value.addonGridItems.isNotEmpty()) return
        val series = row.ref.type == "series"
        val title = row.title
        _uiState.update {
            clearGrids(it).copy(addonGridKey = key, addonGridTitle = title, addonGridLoading = true)
        }
        viewModelScope.launch {
            val metas = stremio.catalogMetas(row.ref.base, row.ref.type, row.ref.id, 0)
            val page = metas.orEmpty().take(ADDON_PAGE)
            val items = resolveAddonMetas(page, series)
            _uiState.update { cur ->
                if (cur.addonGridKey != key) cur else cur.copy(
                    addonGridLoading = false,
                    addonGridItems = items.distinctBy { item -> item.tmdbId to item.embedId },
                    addonGridSkip = page.size,
                    // Página curta ou vazia = fim do catálogo. Página que não
                    // trouxe item NOVO também para: o addon pode não respeitar
                    // o skip (devolveria a mesma página p/ sempre) — sem item
                    // novo não há o que mostrar, e nada é inventado.
                    addonGridMore = metas != null && metas.size >= ADDON_PAGE && items.isNotEmpty(),
                )
            }
        }
    }

    /** Próxima página da grade "Ver Tudo" de addon (scroll infinito). */
    fun loadMoreAddonGrid() {
        val s = _uiState.value
        val key = s.addonGridKey ?: return
        if (s.addonGridLoading || s.addonGridLoadingMore || !s.addonGridMore) return
        val row = s.addonRows.firstOrNull { it.ref.key == key } ?: return
        val series = row.ref.type == "series"
        val skip = s.addonGridSkip
        viewModelScope.launch {
            _uiState.update { it.copy(addonGridLoadingMore = true) }
            val metas = stremio.catalogMetas(row.ref.base, row.ref.type, row.ref.id, skip)
            val page = metas.orEmpty().take(ADDON_PAGE)
            val items = resolveAddonMetas(page, series)
            _uiState.update { cur ->
                if (cur.addonGridKey != key) cur.copy(addonGridLoadingMore = false)
                else {
                    val known = cur.addonGridItems.mapTo(HashSet()) { item -> item.tmdbId to item.embedId }
                    val fresh = items.filter { item -> (item.tmdbId to item.embedId) !in known }
                    cur.copy(
                        addonGridLoadingMore = false,
                        addonGridItems = cur.addonGridItems + fresh,
                        addonGridSkip = skip + page.size,
                        addonGridMore = metas != null && metas.size >= ADDON_PAGE && fresh.isNotEmpty(),
                    )
                }
            }
        }
    }

    /**
     * Abre a grade "Ver Tudo" da fileira de TV de addon [key]. Pagina o
     * MESMO catálogo de TV da fileira (skip de [ADDON_PAGE] em [ADDON_PAGE]);
     * o toque no canal abre o ao vivo, como o card da fileira. A fileira só
     * mostra a 1ª página (até 40 canais) — a grade é onde o resto aparece.
     */
    fun openTvGrid(key: String) {
        val row = _uiState.value.tvAddonRows.firstOrNull { it.ref.key == key } ?: return
        if (_uiState.value.tvGridKey == key && _uiState.value.tvGridChannels.isNotEmpty()) return
        val catalog = tvCatalogOf(row.ref)
        val title = row.title
        _uiState.update {
            clearGrids(it).copy(tvGridKey = key, tvGridTitle = title, tvGridLoading = true)
        }
        viewModelScope.launch {
            val metas = tvCatalogs.loadChannels(catalog, 0).orEmpty()
            val channels = metasToChannels(catalog, metas)
            _uiState.update { cur ->
                if (cur.tvGridKey != key) cur else cur.copy(
                    tvGridLoading = false,
                    tvGridChannels = channels,
                    tvGridSkip = metas.size.coerceAtMost(ADDON_PAGE),
                    // Mesma regra da grade de addon: página curta, vazia ou
                    // sem canal novo para (o addon pode não respeitar o skip).
                    tvGridMore = metas.size >= ADDON_PAGE && channels.isNotEmpty(),
                )
            }
        }
    }

    /** Próxima página da grade "Ver Tudo" de TV (scroll infinito). */
    fun loadMoreTvGrid() {
        val s = _uiState.value
        val key = s.tvGridKey ?: return
        if (s.tvGridLoading || s.tvGridLoadingMore || !s.tvGridMore) return
        val row = s.tvAddonRows.firstOrNull { it.ref.key == key } ?: return
        val catalog = tvCatalogOf(row.ref)
        val skip = s.tvGridSkip
        viewModelScope.launch {
            _uiState.update { it.copy(tvGridLoadingMore = true) }
            val metas = tvCatalogs.loadChannels(catalog, skip).orEmpty()
            val channels = metasToChannels(catalog, metas)
            _uiState.update { cur ->
                if (cur.tvGridKey != key) cur.copy(tvGridLoadingMore = false)
                else {
                    val known = cur.tvGridChannels.mapTo(HashSet()) { channel -> channel.id }
                    val fresh = channels.filter { channel -> channel.id !in known }
                    cur.copy(
                        tvGridLoadingMore = false,
                        tvGridChannels = cur.tvGridChannels + fresh,
                        tvGridSkip = skip + metas.size.coerceAtMost(ADDON_PAGE),
                        tvGridMore = metas.size >= ADDON_PAGE && fresh.isNotEmpty(),
                    )
                }
            }
        }
    }

    /**
     * Abre a grade "Ver Tudo" de uma lista local finita: Continuar assistindo
     * ([LOCAL_CONTINUE]), Novos episódios ([LOCAL_NEW_EPISODES]), Up Next do
     * Trakt ([LOCAL_UP_NEXT]) ou Recomendados ([LOCAL_RECOMMENDED]). Nada é
     * buscado — a grade mostra todos os itens que a fileira já tem; o toque
     * abre o mesmo detalhe.
     */
    fun openLocalGrid(kind: String) {
        if (kind != LOCAL_CONTINUE && kind != LOCAL_NEW_EPISODES && kind != LOCAL_RECOMMENDED && kind != LOCAL_UP_NEXT) return
        _uiState.update { clearGrids(it).copy(localGrid = kind) }
    }

    /** Abre a grade "Ver Tudo" da fileira "Últimos Trailers" (aba atual). */
    fun openTrailersGrid() {
        _uiState.update { clearGrids(it).copy(trailersGrid = true) }
    }

    /** Abre a grade "Ver Tudo" das pastas da coleção [collectionId]. */
    fun openCollectionGrid(collectionId: String) {
        if (collectionId.isBlank()) return
        _uiState.update { clearGrids(it).copy(collectionGridId = collectionId) }
    }

    /** Abre a grade "Ver Tudo" da fileira "Próximos" (episódios + jogos). */
    fun openUpcomingGrid() {
        _uiState.update { clearGrids(it).copy(upcomingGrid = true) }
    }

    // ── Fileiras On Demand do Xtream na Início (acréscimo v1.26.0) ────────────
    private var vodRowsInFlight = false

    /**
     * Lê filmes e séries On Demand da 1ª fonte Xtream ligada (a mesma do
     * On Demand da aba Ao vivo), 1x por sessão. Sem fonte Xtream, sem VOD
     * ou com falha de rede: silêncio — as fileiras somem (a tela não mostra
     * seção vazia nem erro; o On Demand continua alcançável pelo hub do
     * Ao vivo, onde há botão de atualizar). Perfil infantil não carrega:
     * o conteúdo do painel não tem filtro de idade.
     */
    fun loadVodRows() {
        if (_uiState.value.vodChecked || vodRowsInFlight || kids) return
        vodRowsInFlight = true
        _uiState.update { it.copy(vodLoading = true) }
        viewModelScope.launch {
            val source = liveTv.xtreamSources().firstOrNull()
            if (source == null) {
                vodRowsInFlight = false
                _uiState.update { it.copy(vodLoading = false, vodChecked = true) }
                return@launch
            }
            val movies = runCatching { liveTv.fetchOnDemand(source, false)?.second }.getOrNull().orEmpty()
            val series = runCatching { liveTv.fetchOnDemand(source, true)?.second }.getOrNull().orEmpty()
            vodRowsInFlight = false
            _uiState.update {
                it.copy(
                    vodMovies = movies.take(VOD_ROW_MAX),
                    vodSeries = series.take(VOD_ROW_MAX),
                    vodLoading = false,
                    vodChecked = true,
                )
            }
        }
    }

    /** Último histórico visto, para refazer a recomendação quando o catálogo AOTV volta. */
    private var lastHistory: List<com.aotv.app.data.local.entity.MovieHistoryEntity> = emptyList()

    /** Última lista de séries do histórico/Minha Lista (para repor Novos episódios ao religar a fileira). */
    private var lastNewEpSeries: List<Triple<Int, String, String>> = emptyList()

    // Auto-add do calendário (Lote 13, v1.23.0): conjunto local (Minha Lista
    // + Continuar) e séries dos trackers, separados p/ o merge (o local vem
    // primeiro; o teto de 15 é aplicado depois da junção).
    private var lastLocalSeries: List<Triple<Int, String, String>> = emptyList()
    private var trackerSeries: List<Triple<Int, String, String>> = emptyList()

    /** Junta o local com os trackers e reconstrói "Novos episódios" e "Próximos". */
    private fun rebuildTrackedSeries() {
        val merged = (lastLocalSeries +
            if (prefs.calendarAutoAddTrackers) trackerSeries else emptyList())
            .distinctBy { it.first }.take(15)
        buildNewEpisodes(merged)
        buildUpcomingEpisodes(merged)
    }

    /** "Não hoje" (Lote 13): dispensa o episódio novo até amanhã. */
    fun markNewEpisodeNotToday(tmdbId: Int) {
        prefs.markNotToday(tmdbId)
        _uiState.update { it.copy(newEpisodes = it.newEpisodes.filterNot { ep -> ep.item.tmdbId == tmdbId }) }
    }

    /** Fileiras visíveis na última aplicação do layout (para repor dados de quem voltou a aparecer). */
    private var lastVisibleKeys: Set<String> = emptySet()

    private data class HomeInputs(
        val layout: List<com.aotv.app.data.local.HomeRowPref>,
        val catalogOn: Boolean,
        val addons: List<com.aotv.app.data.local.AddonEntry>,
        val hero: String,
    )
    private var lastCatalogOn: Boolean? = null

    /** Aplica o layout do perfil. Fileira nova de addon começa a carregar, catálogo AOTV liga e desliga. */
    private fun applyLayout(i: HomeInputs) {
        val addonOn = i.addons.associate { com.aotv.app.data.remote.AddonLinks.baseOf(it.url) to it.enabled }
        val visible = i.layout.filter { r ->
            val sys = r.system
            r.enabled && when {
                sys != null -> !sys.catalog || i.catalogOn
                // Perfil infantil não mostra catálogo de addon nem coleção (não dá pra filtrar por idade).
                r.collectionId != null -> !kids
                else -> !kids && addonOn[r.ref?.base] == true
            }
        }
        val refs = visible.mapNotNull { r -> r.ref?.let { it to r.displayTitle } }
        // Lote 16 (v1.26.0): fileira de TV ("tv"/"channel") NÃO vai para
        // [addonRows] — o resolver de lá descarta a meta do canal e a tela
        // de detalhe não toca canal. Vai para [UiState.tvAddonRows], com os
        // canais carregados por [loadTvAddonRow].
        val (tvRefs, movieRefs) = refs.partition { (ref, _) -> ref.type == "tv" || ref.type == "channel" }
        val old = _uiState.value.addonRows.associateBy { it.ref.key }
        val oldTv = _uiState.value.tvAddonRows.associateBy { it.ref.key }
        _uiState.update { st ->
            st.copy(
                layout = visible, catalogOn = i.catalogOn, heroSource = i.hero,
                addonRows = movieRefs.map { (ref, title) -> old[ref.key]?.copy(title = title) ?: AddonRow(ref, title = title) },
                tvAddonRows = tvRefs.map { (ref, title) -> oldTv[ref.key]?.copy(title = title) ?: TvAddonRow(ref, title = title) },
            )
        }
        // Fileira de TV nova começa a carregar (a de filme/série carrega ao
        // aparecer na tela, como antes).
        tvRefs.filter { oldTv[it.first.key] == null }.forEach { loadTvAddonRow(it.first.key) }
        // Fileiras de addon carregam quando APARECEM na tela (LaunchedEffect
        // no item da LazyColumn), não todas juntas na abertura: cada uma são
        // 40 metas + resolve no TMDB por item + 1 emissão de UiState — com
        // muitas fileiras, a abertura disparava tudo junto. Exceção: a fileira
        // do destaque, que alimenta o herói antes de ser rolada até ela.
        refs.filter { old[it.first.key] == null && it.first.key == i.hero }.forEach { loadAddonRow(it.first.key) }
        // Fileira que voltou a aparecer (ligaram de novo, ou o catálogo AOTV voltou)
        // precisa dos dados: trailers, novos episódios e recomendações só carregam
        // quando a fileira está ligada, então repor aqui — senão a fileira
        // religada ficava vazia até o histórico mudar.
        val visibleKeys = visible.mapTo(HashSet()) { it.key }
        val newlyVisible = visibleKeys - lastVisibleKeys
        lastVisibleKeys = visibleKeys
        val stNow = _uiState.value
        if (com.aotv.app.data.local.SystemRow.TRAILERS.key in newlyVisible && stNow.latestTrailers.isEmpty() && !stNow.trailersLoading) {
            selectTrailerTab(stNow.trailerTab)
        }
        if (com.aotv.app.data.local.SystemRow.NEW_EPISODES.key in newlyVisible && stNow.newEpisodes.isEmpty()) {
            buildNewEpisodes(lastNewEpSeries)
        }
        if (com.aotv.app.data.local.SystemRow.TRAKT_UP_NEXT.key in newlyVisible && stNow.traktUpNext.isEmpty()) {
            loadTraktUpNext()
        }
        if (com.aotv.app.data.local.SystemRow.RECOMMENDED.key in newlyVisible && stNow.recommended.isEmpty()) {
            lastRecKey = ""
            buildRecommendations(lastHistory)
        }
        val prev = lastCatalogOn
        lastCatalogOn = i.catalogOn
        if (prev == null || prev == i.catalogOn) return
        if (i.catalogOn) {
            // Trailers e recomendações já foram repostos acima (fileiras que
            // voltaram a aparecer); aqui só recarrega o catálogo TMDB.
            load()
        } else {
            _uiState.update {
                it.copy(
                    trendingMovies = emptyList(), trendingTv = emptyList(), nowPlaying = emptyList(),
                    releases2026 = emptyList(), homeSections = emptyList(), latestTrailers = emptyList(),
                    recommended = emptyList(), recommendedBecause = "", heroLogos = emptyMap(),
                )
            }
        }
    }

    init {
        // A Início segue o Personalizar Início e o liga e desliga dos addons, sem reabrir o app.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                prefs.homeLayoutFlow, prefs.catalogOnFlow, prefs.addons, prefs.heroSourceFlow,
            ) { layout, catalogOn, addons, hero -> HomeInputs(layout, catalogOn, addons, hero) }
                .collect { applyLayout(it) }
        }
        _uiState.update { it.copy(heroTrailer = prefs.heroTrailer, display = prefs.homeDisplay) }
        // Layout da Página Inicial (Bloco 3, Lote 15, v1.25.0): destaque
        // liga/desliga, catálogos do destaque, ocultar não lançados e sufixo
        // de tipo. A Início — e a busca, que lê o mesmo UiState — reage na hora.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                prefs.heroOnFlow, prefs.heroCatalogsFlow, prefs.hideUnreleasedFlow, prefs.heroTypeSuffixFlow,
            ) { heroOn, heroCatalogs, hideUnreleased, typeSuffix ->
                _uiState.update {
                    it.copy(
                        heroOn = heroOn, heroCatalogs = heroCatalogs,
                        hideUnreleased = hideUnreleased, typeSuffix = typeSuffix,
                    )
                }
            }.collect()
        }
        // A Início aplica a exibição na hora: mudou na página Layout, a fileira recompõe.
        viewModelScope.launch {
            prefs.homeDisplayFlow.collect { d -> _uiState.update { it.copy(display = d) } }
        }
        load()
        // Trailers carregam pelo applyLayout (fileira que aparece), não aqui:
        // a fileira desligada não busca dado nenhum.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(library.recentlyPlayed(), library.watchlist()) { h, w ->
                (h.filter { it.type == MediaType.SERIES.name }.map { Triple(it.tmdbId, it.title, it.posterUrl) } +
                    w.filter { it.type == MediaType.SERIES.name }.map { Triple(it.tmdbId, it.title, it.posterUrl) })
                    .distinctBy { it.first }
            }.collect { lastLocalSeries = it; rebuildTrackedSeries() }
        }
        // Auto-add do calendário (Lote 13, v1.23.0): séries do Trakt/Simkl
        // entram nas fileiras "Novos episódios" e "Próximos" sem estar na
        // Minha Lista. O collect emite o valor atual na hora (carrega) e de
        // novo quando o toggle muda.
        viewModelScope.launch {
            prefs.calendarAutoAddTrackersFlow.collect { on ->
                trackerSeries = if (on) trackerShows.get() else emptyList()
                rebuildTrackedSeries()
            }
        }
        // Esportes e calendário (Lote 8, v1.18.0): as prefs do perfil no
        // UiState (a fileira "Próximos" e os lembretes leem daqui) e os jogos
        // do EPG. Os jogos só mudam quando o guia recarrega (abrir o Ao vivo
        // ou o "Atualizar canais") — a Início não tem relógio próprio p/ o EPG.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                prefs.sportsShowFlow, prefs.calendarHideSpoilersFlow, prefs.sportsHideScoresFlow,
                prefs.sportsScoreDelaySecFlow, prefs.sportsLiveScoresFlow, prefs.sportsGameAlertsFlow,
            ) { args ->
                // O combine de 6 flows NÃO resolve nesta versão do projeto
                // (builds #234/#235: o compilador só aceita a forma vararg, e os
                // tipos explícitos não adiantaram) — lê do array, na ordem.
                val show = args[0] as Boolean
                val hideSpoilers = args[1] as Boolean
                val hideScores = args[2] as Boolean
                val delaySec = args[3] as Int
                val liveScores = args[4] as Boolean
                val alerts = args[5] as Boolean
                _uiState.update {
                    it.copy(
                        sportsShow = show, calendarHideSpoilers = hideSpoilers,
                        sportsHideScores = hideScores, sportsScoreDelaySec = delaySec,
                        sportsLiveScores = liveScores, sportsGameAlerts = alerts,
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            liveTv.state.collect { st ->
                // Lote 21 (v1.31.0) — fix do travamento da v1.30.0: a
                // varredura do EPG NÃO roda na Main (viewModelScope é Main)
                // — Dispatchers.Default, com a classificação em cache
                // (SportsScores). Antes, cada emissão do estado do Ao vivo
                // revarria todo o EPG na thread principal (ANR).
                val games = if (prefs.sportsShow) {
                    withContext(Dispatchers.Default) {
                        upcomingSportsGames(st.channels, st.epg, System.currentTimeMillis())
                    }
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(upcomingGames = games) }
            }
        }
        // Continuar assistindo: observa o histórico (reproduções recentes) e mapeia p/ MovieItem.
        viewModelScope.launch {
            library.recentlyPlayed().collectLatest { list ->
                // Terminado (90%+) some da lista; série nesse ponto mostra o PRÓXIMO episódio em
                // vez do já visto (mesmo critério e cálculo de "terminado"/"próximo episódio" de
                // DetailPrimary.kt: posição >= 90% da duração, próximo = +1 na temporada ou E1 da
                // seguinte). Recomendações abaixo seguem usando o histórico cru (`list`).
                // Removido do Continuar (v1.13.0): o oculto por perfil some da fileira
                // (e um backup antigo não traz de volta); assistir de novo libera.
                val continueList = list.filterNot { prefs.isContinueHidden(it.tmdbId, it.type) }
                    .mapNotNull { resolveHistoryItem(it) }
                val items = continueList.map { h ->
                    MovieItem(
                        tmdbId = h.tmdbId,
                        // Ao vivo (Lote 8, v1.18.0): o toque no card volta ao
                        // player pelo embedId "live|<canal>|<inicio>:<fim>"
                        // (ver MoviesNavigation) — não é um título do TMDB.
                        embedId = if (h.type == com.aotv.app.ui.live.LivePlayerViewModel.LIVE_TYPE) {
                            "live|" + h.lastSourceUrl + "|" + h.lastAddon
                        } else h.tmdbId.toString(),
                        title = h.title,
                        overview = "",
                        posterUrl = h.posterUrl,
                        backdropUrl = "",
                        year = "",
                        rating = 0.0,
                        type = runCatching { MediaType.valueOf(h.type) }.getOrDefault(MediaType.MOVIE),
                    )
                }
                // Progresso + tempo restante (min) por tmdbId — p/ o "Continuar assistindo".
                val progress = continueList.filter { it.durationMs > 0 }
                    .associate { it.tmdbId to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
                val remaining = continueList.filter { it.durationMs > 0 && it.durationMs > it.positionMs }
                    .associate { it.tmdbId to (((it.durationMs - it.positionMs) / 60000L).toInt()) }
                // Selo do episódio ("T2:E5" -> "T2 E5"), igual ao que a Minha Lista da TV já mostra.
                val episode = continueList.filter { it.lastEpisode.isNotBlank() }
                    .associate { it.tmdbId to it.lastEpisode.replace(":", " ") }
                _uiState.update {
                    it.copy(
                        continueWatching = items, continueProgress = progress,
                        continueRemaining = remaining, continueEpisode = episode,
                        continueArtwork = it.continueArtwork.filterKeys { key -> items.any { m -> com.aotv.app.data.local.posterKey(m) == key } },
                    )
                }
                // Toda vez que o histórico muda, a recomendação é refeita. É isso que faz a
                // lista acompanhar o que a pessoa anda assistindo.
                buildRecommendations(list)
                // Up Next do Trakt (Lote 19 item 4): o histórico mudou (episódio
                // visto) — o próximo episódio pode ter andado. O throttle de
                // 15 min dentro de loadTraktUpNext segura a frequência.
                loadTraktUpNext()
                coroutineScope {
                    continueList.take(20).map { h -> async {
                        // Ao vivo (Lote 8, v1.18.0): o tmdbId do canal é
                        // sintético — sem busca de arte no TMDB p/ ele.
                        if (h.type == com.aotv.app.ui.live.LivePlayerViewModel.LIVE_TYPE) return@async
                        val item = items.firstOrNull { it.tmdbId == h.tmdbId && it.type.name == h.type } ?: return@async
                        val art = continueArt.resolve(item, h.lastEpisode)
                        _uiState.update { current -> current.copy(
                            continueArtwork = current.continueArtwork + (com.aotv.app.data.local.posterKey(item) to art),
                            continueWatching = current.continueWatching.map { m ->
                                if (m.tmdbId != item.tmdbId || m.type != item.type) m else m.copy(
                                    title = m.title.ifBlank { art.title }, posterUrl = m.posterUrl.ifBlank { art.posterUrl },
                                    backdropUrl = m.backdropUrl.ifBlank { art.imageUrl },
                                )
                            },
                        ) }
                    } }.awaitAll()
                }
            }
        }
    }

    fun selectTab(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }

    /** Busca a logo-art de um item sob demanda (hero da TV) e cacheia em heroLogos. */
    // Em busca (sem o update "em busca" de antes, que emitia 1 UiState por item
    // só para marcar "" — na abertura eram até 20 emissões para 10 itens).
    private val heroLogoFetching = mutableSetOf<Int>()
    private val heroBackdropFetching = mutableSetOf<Int>()
    private val heroCertFetching = mutableSetOf<Int>()

    fun ensureHeroLogo(tmdbId: Int, isSeries: Boolean) {
        if (_uiState.value.heroLogos.containsKey(tmdbId) || !heroLogoFetching.add(tmdbId)) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) { heroLogoFetching.remove(tmdbId); return }
        viewModelScope.launch {
            val url = runCatching {
                if (isSeries) tmdb.getSeriesImages(tmdbId, apiKey = apiKey).bestLogoUrl()
                else tmdb.getMovieImages(tmdbId, apiKey = apiKey).bestLogoUrl()
            }.getOrNull().orEmpty()
            if (url.isNotBlank()) _uiState.update { it.copy(heroLogos = it.heroLogos + (tmdbId to url)) }
            else heroLogoFetching.remove(tmdbId)
        }
    }

    /** Backdrop real p/ hero de itens sem backdrop (ex: Continuar assistindo). Cacheia em heroBackdrops. */
    fun ensureHeroBackdrop(tmdbId: Int, isSeries: Boolean) {
        if (_uiState.value.heroBackdrops.containsKey(tmdbId) || !heroBackdropFetching.add(tmdbId)) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) { heroBackdropFetching.remove(tmdbId); return }
        viewModelScope.launch {
            val base = "https://image.tmdb.org/t/p/w780"
            val url = runCatching {
                if (isSeries) tmdb.getSeriesDetail(tmdbId, apiKey = apiKey).backdropPath?.let { "$base$it" }
                else tmdb.getMovieDetail(tmdbId, apiKey = apiKey).backdropPath?.let { "$base$it" }
            }.getOrNull().orEmpty()
            if (url.isNotBlank()) _uiState.update { it.copy(heroBackdrops = it.heroBackdrops + (tmdbId to url)) }
            else heroBackdropFetching.remove(tmdbId)
        }
    }

    /** Classificação indicativa do hero (catálogo não traz) — busca no detalhe e cacheia. */
    fun ensureHeroCert(tmdbId: Int, isSeries: Boolean) {
        if (_uiState.value.heroCerts.containsKey(tmdbId) || !heroCertFetching.add(tmdbId)) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) { heroCertFetching.remove(tmdbId); return }
        viewModelScope.launch {
            val cert = runCatching {
                if (isSeries) tmdb.getSeriesDetail(tmdbId, apiKey = apiKey).contentRatings.certBR()
                else tmdb.getMovieDetail(tmdbId, apiKey = apiKey).releaseDates.certBR()
            }.getOrNull().orEmpty()
            if (cert.isNotBlank()) _uiState.update { it.copy(heroCerts = it.heroCerts + (tmdbId to cert)) }
            else heroCertFetching.remove(tmdbId)
        }
    }

    // ── Catálogo: controles ────────────────────────────────────────────────
    fun setCatalogType(type: CatalogType) {
        if (_uiState.value.catalogType == type) return
        // ao trocar de tipo, reseta gênero e ajusta sort de lançamento conforme tipo
        val current = _uiState.value
        val newSort = when (current.sortBy) {
            SORT_RELEASE_MOVIE, SORT_RELEASE_TV ->
                if (type == CatalogType.MOVIE) SORT_RELEASE_MOVIE else SORT_RELEASE_TV
            else -> current.sortBy
        }
        _uiState.update { it.copy(catalogType = type, selectedGenreId = null, sortBy = newSort) }
        reloadCatalog()
    }

    fun selectGenre(genreId: Int?) {
        if (_uiState.value.selectedGenreId == genreId) return
        _uiState.update { it.copy(selectedGenreId = genreId) }
        reloadCatalog()
    }

    fun setSort(sort: String) {
        if (_uiState.value.sortBy == sort) return
        _uiState.update { it.copy(sortBy = sort) }
        reloadCatalog()
    }

    /** Recarrega o catálogo do zero (page = 1) com os filtros atuais. */
    fun reloadCatalog() {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(catalogItems = emptyList(), catalogPage = 1, catalogTotalPages = 1, isLoadingCatalog = false)
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalog = true, catalogItems = emptyList(), catalogPage = 1) }
            val s = _uiState.value
            val resp = fetchCatalogPage(apiKey, s.catalogType, s.selectedGenreId, s.sortBy, page = 1)
            _uiState.update {
                if (resp == null) {
                    it.copy(isLoadingCatalog = false, catalogItems = emptyList(), catalogPage = 1, catalogTotalPages = 1)
                } else {
                    it.copy(
                        isLoadingCatalog = false,
                        catalogItems = resp.first,
                        catalogPage = 1,
                        catalogTotalPages = resp.second,
                    )
                }
            }
        }
    }

    /** Carrega a próxima página e ANEXA aos itens atuais (scroll infinito). */
    fun loadMoreCatalog() {
        val s = _uiState.value
        if (s.isLoadingMore || s.isLoadingCatalog) return
        if (s.catalogPage >= s.catalogTotalPages) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        val nextPage = s.catalogPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val resp = fetchCatalogPage(apiKey, s.catalogType, s.selectedGenreId, s.sortBy, page = nextPage)
            _uiState.update { cur ->
                if (resp == null) {
                    cur.copy(isLoadingMore = false)
                } else {
                    val existingIds = cur.catalogItems.mapTo(HashSet()) { it.tmdbId }
                    val appended = cur.catalogItems + resp.first.filter { it.tmdbId !in existingIds }
                    cur.copy(
                        isLoadingMore = false,
                        catalogItems = appended,
                        catalogPage = nextPage,
                        catalogTotalPages = resp.second,
                    )
                }
            }
        }
    }

    /** Busca uma página do discover. Retorna (itens, totalPages) ou null em erro. */
    private suspend fun fetchCatalogPage(
        apiKey: String,
        type: CatalogType,
        genreId: Int?,
        sort: String,
        page: Int,
    ): Pair<List<MovieItem>, Int>? {
        val voteGte = if (sort == SORT_TOP_RATED) TOP_RATED_MIN_VOTES else null
        return runCatching {
            val resp = if (type == CatalogType.MOVIE) {
                tmdb.discoverMovies(
                    genreId = genreId,
                    sort = sort,
                    page = page,
                    voteCountGte = voteGte,
                    apiKey = apiKey,
                )
            } else {
                tmdb.discoverTv(
                    genreId = genreId,
                    sort = sort,
                    page = page,
                    voteCountGte = voteGte,
                    apiKey = apiKey,
                )
            }
            resp.results.map { it.toMovieItem() } to resp.totalPages
        }.getOrNull()
    }

    /** Busca uma página conforme o [CatalogQuery] — discover (com gênero/sort/ano), trending ou nos cinemas. */
    private suspend fun fetchQueryPage(
        apiKey: String,
        q: CatalogQuery,
        page: Int,
    ): Pair<List<MovieItem>, Int>? = runCatching {
        val voteGte = if (q.sort == SORT_TOP_RATED) TOP_RATED_MIN_VOTES else null
        val resp = when (q.endpoint) {
            // No infantil os "Tops" da Início são o discover de animação e família.
            CatalogEndpoint.TRENDING ->
                if (kids && q.type == CatalogType.MOVIE) tmdb.discoverMovies(genres = KIDS_MOVIES, page = page, apiKey = apiKey)
                else if (kids) tmdb.discoverTv(genres = KIDS_SERIES, page = page, apiKey = apiKey)
                else if (q.type == CatalogType.MOVIE) tmdb.getTrendingMovies(apiKey = apiKey)
                else tmdb.getTrendingTv(apiKey = apiKey)
            CatalogEndpoint.NOW_PLAYING ->
                tmdb.getNowPlaying(page = page, apiKey = apiKey)
            CatalogEndpoint.DISCOVER ->
                if (q.type == CatalogType.MOVIE)
                    tmdb.discoverMovies(genreId = q.genreId, sort = q.sort, page = page, voteCountGte = voteGte, year = q.year, apiKey = apiKey, genres = q.genres)
                else
                    tmdb.discoverTv(genreId = q.genreId, sort = q.sort, page = page, voteCountGte = voteGte, year = q.year, apiKey = apiKey, genres = q.genres)
        }
        // trending não aceita page na API → só há a 1ª página.
        val totalPages = if (q.endpoint == CatalogEndpoint.TRENDING && !kids) 1 else resp.totalPages
        resp.results.filterNot { it.adult }.map { it.toMovieItem() } to totalPages
    }.getOrNull()

    /** Abre o grid "Ver Tudo" para [query]. Carrega EXATAMENTE o conteúdo que a fileira mostra. */
    /** Fecha o grid "Ver Tudo" e volta pras fileiras da home (qualquer grade: TMDB, addon, TV ou local). */
    fun closeCatalog() {
        _uiState.update { clearGrids(it) }
    }

    fun openCatalog(query: CatalogQuery) {
        // Já aberto nessa mesma query (voltando do detalhe) → mantém itens e página carregados.
        if (_uiState.value.gridQuery == query && _uiState.value.gridItems.isNotEmpty()) return
        val apiKey = prefs.tmdbApiKey
        _uiState.update {
            clearGrids(it).copy(gridQuery = query, gridItems = emptyList(), gridPage = 1, gridTotalPages = 1, gridLoading = true, gridLoadingMore = false)
        }
        if (apiKey.isBlank()) { _uiState.update { it.copy(gridLoading = false) }; return }
        viewModelScope.launch {
            val resp = fetchQueryPage(apiKey, query, page = 1)
            _uiState.update {
                if (resp == null) it.copy(gridLoading = false)
                else it.copy(gridLoading = false, gridItems = resp.first, gridPage = 1, gridTotalPages = resp.second)
            }
        }
    }

    /** Próxima página do grid "Ver Tudo" (scroll infinito). Trending não pagina. */
    fun loadMoreGrid() {
        val s = _uiState.value
        val q = s.gridQuery ?: return
        if (s.gridLoading || s.gridLoadingMore) return
        if (s.gridPage >= s.gridTotalPages) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        val next = s.gridPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(gridLoadingMore = true) }
            val resp = fetchQueryPage(apiKey, q, page = next)
            _uiState.update { cur ->
                if (resp == null) cur.copy(gridLoadingMore = false)
                else {
                    val ids = cur.gridItems.mapTo(HashSet()) { it.tmdbId }
                    cur.copy(
                        gridLoadingMore = false,
                        gridItems = cur.gridItems + resp.first.filter { it.tmdbId !in ids },
                        gridPage = next,
                        gridTotalPages = resp.second,
                    )
                }
            }
        }
    }

    /** Remove do Continuar assistindo e guarda o oculto por perfil (long-press no card, v1.13.0). */
    fun removeFromContinue(tmdbId: Int, type: String) {
        viewModelScope.launch {
            // v1.21.0: resolve o tipo ARMAZENADO na linha do histórico (o tipo
            // do card pode ser MOVIE p/ um canal "LIVE" — ver LibraryRepository).
            library.removePlaybackAnyType(tmdbId, type)
        }
    }

    /**
     * "Marcar como assistido" no painel de opções do título (Lote 14,
     * v1.24.0): filme completa o progresso e some do Continuar; série
     * marca o episódio em andamento e avança p/ o próximo (ver
     * [LibraryRepository.markContinueWatched]). Modo privado não marca
     * nada, igual ao [MovieDetailViewModel.markEpisodeWatched].
     */
    fun markContinueWatched(item: com.aotv.app.domain.model.MovieItem) {
        if (prefs.privateMode) return
        viewModelScope.launch { library.markContinueWatched(item.tmdbId) }
    }

    fun load() {
        // Ranking de hoje para a etiqueta "#3 Hoje" dos pôsteres (vale também para fileira de addon).
        posterInfo.loadRanks()
        viewModelScope.launch {
            val apiKey = prefs.tmdbApiKey
            // Catálogo AOTV desligado. A Início fica com o progresso e as fileiras de addon.
            if (!prefs.catalogOn) {
                _uiState.update { it.copy(isLoading = false, error = null, hasTmdbKey = apiKey.isNotBlank()) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Fileira desligada não busca dado nenhum: antes as chamadas rodavam
            // sempre que o catálogo AOTV estava ligado, mesmo com as fileiras
            // desligadas no Personalizar Início. Gêneros alimenta outras telas
            // (Descobrir, Catálogo, chips de Gêneros), então sempre carrega.
            val layoutNow = prefs.homeLayout
            val rowOn = { key: String -> layoutNow.any { it.key == key && it.enabled } }
            val heroSrc = prefs.heroSource
            // Herói automático (ou fonte desligada/inexistente) usa o em alta.
            val heroNeedsTrending = heroSrc.isBlank() ||
                heroSrc == com.aotv.app.data.local.SystemRow.TRENDING_MOVIES.key ||
                heroSrc == com.aotv.app.data.local.SystemRow.TRENDING_TV.key ||
                layoutNow.none { it.key == heroSrc }
            val fetchTrendingMovies = rowOn(com.aotv.app.data.local.SystemRow.TRENDING_MOVIES.key) || heroNeedsTrending
            val fetchTrendingTv = rowOn(com.aotv.app.data.local.SystemRow.TRENDING_TV.key) || heroNeedsTrending
            val fetchNowPlaying = rowOn(com.aotv.app.data.local.SystemRow.NOW_PLAYING.key)
            val fetchReleases = rowOn(com.aotv.app.data.local.SystemRow.RELEASES.key)

            // Somente catálogo TMDB. As chamadas rodam EM PARALELO (async/await) — antes eram
            // em série (6 round-trips somados = segundos de "Carregando" em rede/CPU fraca).
            val trendingMoviesD = async {
                if (apiKey.isBlank() || !fetchTrendingMovies) emptyList()
                else runCatching {
                    (if (kids) tmdb.discoverMovies(genres = KIDS_MOVIES, apiKey = apiKey) else tmdb.getTrendingMovies(apiKey = apiKey))
                        .results.filterNot { it.adult }.map { it.toMovieItem() }
                }.getOrDefault(emptyList())
            }
            val trendingTvD = async {
                if (apiKey.isBlank() || !fetchTrendingTv) emptyList()
                else runCatching {
                    (if (kids) tmdb.discoverTv(genres = KIDS_SERIES, apiKey = apiKey) else tmdb.getTrendingTv(apiKey = apiKey))
                        .results.filterNot { it.adult }.map { it.toMovieItem() }
                }.getOrDefault(emptyList())
            }
            val genresD = async {
                if (apiKey.isBlank()) emptyList() else runCatching { tmdb.getMovieGenres(apiKey = apiKey).genres }.getOrDefault(emptyList())
            }
            val tvGenresD = async {
                if (apiKey.isBlank()) emptyList() else runCatching { tmdb.getTvGenres(apiKey = apiKey).genres }.getOrDefault(emptyList())
            }
            val nowPlayingD = async {
                if (apiKey.isBlank() || !fetchNowPlaying) emptyList()
                else runCatching { tmdb.getNowPlaying(apiKey = apiKey).results.filterNot { it.adult }.map { it.toMovieItem() } }.getOrDefault(emptyList())
            }
            val releases2026D = async {
                if (apiKey.isBlank() || !fetchReleases) emptyList()
                else runCatching { tmdb.discoverMovies(sort = SORT_RELEASE_MOVIE, year = _uiState.value.releasesYear, apiKey = apiKey).results.filterNot { it.adult }.map { it.toMovieItem() } }.getOrDefault(emptyList())
            }
            val trendingMovies = trendingMoviesD.await()
            val trendingTv = trendingTvD.await()
            val genres = genresD.await()
            val tvGenres = tvGenresD.await()
            val nowPlaying = nowPlayingD.await()
            val releases2026 = releases2026D.await()

            _uiState.update {
                it.copy(
                    trendingMovies = trendingMovies,
                    trendingTv = trendingTv,
                    movieGenres = genres,
                    tvGenres = tvGenres,
                    nowPlaying = nowPlaying,
                    releases2026 = releases2026,
                    isLoading = false,
                    hasTmdbKey = apiKey.isNotBlank(),
                )
            }

            // Logo-art do hero (estilo Nuvio): busca em paralelo os logos dos até 8 destaques.
            // Só quando o em alta foi buscado (fileira ligada ou herói automático).
            if (apiKey.isNotBlank() && (fetchTrendingMovies || fetchTrendingTv)) {
                val candidates =
                    (trendingMovies.filter { it.backdropUrl.isNotBlank() }.map { it to true } +
                        trendingTv.filter { it.backdropUrl.isNotBlank() }.map { it to false }).take(8)
                runCatching {
                    val logos = coroutineScope {
                        candidates.map { (item, isMovie) ->
                            async {
                                val url = runCatching {
                                    if (isMovie) tmdb.getMovieImages(item.tmdbId, apiKey = apiKey).bestLogoUrl()
                                    else tmdb.getSeriesImages(item.tmdbId, apiKey = apiKey).bestLogoUrl()
                                }.getOrNull().orEmpty()
                                item.tmdbId to url
                            }
                        }.awaitAll()
                    }.filter { it.second.isNotBlank() }.toMap()
                    // Soma ao que já tinha (o destaque de outra fileira busca logo sob demanda).
                    _uiState.update { it.copy(heroLogos = it.heroLogos + logos) }
                }
            }

            // carrega a primeira página do catálogo com os filtros atuais
            reloadCatalog()

            // Specs das seções de gênero (só a TV usa: o celular mostra Gêneros
            // como 1 fileira de chips e não carrega seção nenhuma). O 1º lote
            // parte do efeito da TV; o load() não dispara mais.
            sectionSpecs = buildSectionSpecs(genres, tvGenres)
            nextSpecIndex = 0
            _uiState.update { it.copy(homeSections = emptyList()) }
        }
    }

    /** Gera a lista (grande) de seções: gênero × tipo × ordenação. Intercala filmes/séries. */
    private fun buildSectionSpecs(
        movieGenres: List<TmdbGenreDto>,
        tvGenres: List<TmdbGenreDto>,
    ): List<SectionSpec> {
        val out = mutableListOf<SectionSpec>()
        if (kids) {
            // Infantil cruza cada gênero com Animação, que é o que o filtro do perfil deixa passar.
            movieGenres.filter { it.id in listOf(12, 14, 35, 10402, 10751) }.forEach {
                out += SectionSpec("kid_${it.id}", CatalogQuery("Animação · ${it.name}", CatalogType.MOVIE, genres = "16,${it.id}"))
            }
            out += SectionSpec("kid_top", CatalogQuery("Desenhos mais votados", CatalogType.MOVIE, genreId = 16, sort = SORT_TOP_RATED))
            out += SectionSpec("kid_tv", CatalogQuery("Séries infantis aclamadas", CatalogType.SERIES, genreId = 10762, sort = SORT_TOP_RATED))
            return out
        }
        val maxLen = maxOf(movieGenres.size, tvGenres.size)
        // Intercala "Gênero (filme popular)" com "Gênero · Séries"
        for (i in 0 until maxLen) {
            movieGenres.getOrNull(i)?.let {
                out += SectionSpec("mv_${it.id}", CatalogQuery(it.name, CatalogType.MOVIE, genreId = it.id, sort = SORT_POPULAR))
            }
            tvGenres.getOrNull(i)?.let {
                out += SectionSpec("tv_${it.id}", CatalogQuery("${it.name} · Séries", CatalogType.SERIES, genreId = it.id, sort = SORT_POPULAR))
            }
        }
        // Segunda passada: "Mais votados" por gênero (mais variedade = catálogo "infinito")
        movieGenres.forEach {
            out += SectionSpec("mvtop_${it.id}", CatalogQuery("${it.name} · Mais votados", CatalogType.MOVIE, genreId = it.id, sort = SORT_TOP_RATED))
        }
        tvGenres.forEach {
            out += SectionSpec("tvtop_${it.id}", CatalogQuery("${it.name} · Séries aclamadas", CatalogType.SERIES, genreId = it.id, sort = SORT_TOP_RATED))
        }
        return out
    }

    /** Fileira de sistema ligada no Personalizar Início (e catálogo AOTV ligado). */
    private fun sysRowOn(key: String) = prefs.catalogOn &&
        prefs.homeLayout.any { it.key == key && it.enabled }

    /** Fileira Gêneros ligada no Personalizar Início (e catálogo AOTV ligado). */
    private fun genresOn() = sysRowOn(com.aotv.app.data.local.SystemRow.GENRES.key)

    /** Carrega o próximo lote de seções (chamado ao rolar perto do fim). */
    fun loadMoreSections() {
        val s = _uiState.value
        if (s.isLoadingSections || !genresOn()) return
        if (nextSpecIndex >= sectionSpecs.size) return
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        val batch = sectionSpecs.subList(nextSpecIndex, minOf(nextSpecIndex + SECTIONS_PER_BATCH, sectionSpecs.size))
        nextSpecIndex += batch.size
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSections = true) }
            // As 4 seções do lote buscadas EM PARALELO (antes: uma a uma → engasgo ao rolar).
            val newSections = batch.map { spec ->
                async {
                    val resp = fetchQueryPage(apiKey, spec.query, page = 1)
                    val items = resp?.first?.filter { it.posterUrl.isNotBlank() } ?: emptyList()
                    if (items.size >= 6) HomeSection(spec.id, spec.query.title, items, spec.query) else null
                }
            }.awaitAll().filterNotNull()
            _uiState.update { it.copy(homeSections = it.homeSections + newSections, isLoadingSections = false) }
        }
    }

    // Paginação HORIZONTAL por fileira (TV): ao chegar no fim de um gênero, carrega a próxima
    // página e anexa — deixa o catálogo TV tão profundo quanto o grid "Ver Tudo" do celular.
    private val sectionPages = mutableMapOf<String, Int>()
    private val sectionLoadingMore = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    fun loadMoreSectionItems(sectionId: String) {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        if (!sectionLoadingMore.add(sectionId)) return
        viewModelScope.launch {
            val section = _uiState.value.homeSections.firstOrNull { it.id == sectionId }
            // Trending não pagina (API só tem 1 página).
            if (section == null || (section.query.endpoint == CatalogEndpoint.TRENDING && !kids)) {
                sectionLoadingMore.remove(sectionId); return@launch
            }
            val nextPage = (sectionPages[sectionId] ?: 1) + 1
            val resp = fetchQueryPage(apiKey, section.query, nextPage)
            if (resp != null && nextPage <= resp.second) {
                sectionPages[sectionId] = nextPage
                _uiState.update { st ->
                    st.copy(homeSections = st.homeSections.map { s ->
                        if (s.id == sectionId) {
                            val ids = s.items.mapTo(HashSet()) { it.tmdbId }
                            s.copy(items = s.items + resp.first.filter { it.posterUrl.isNotBlank() && it.tmdbId !in ids })
                        } else s
                    })
                }
            }
            sectionLoadingMore.remove(sectionId)
        }
    }
}

// Perfil infantil (iguais ao webOS). Animação ou família, e animação ou infantil nas séries.
private const val KIDS_MOVIES = "16|10751"
private const val KIDS_SERIES = "16|10762"

/** Um cartão da fileira "Novos episódios". [badge] é NOVO ou a data da estreia. */
data class NewEpisode(val item: MovieItem, val badge: String, val sub: String, val at: Long, val upcoming: Boolean)

private const val DAY_MS = 86_400_000L

/** "2026-10-20" ao meio-dia UTC, em ms. null quando a data não vem. */
internal fun airDateMs(iso: String?): Long? {
    val p = iso?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
    if (p.size != 3) return null
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    c.clear(); c.set(p[0], p[1] - 1, p[2], 12, 0, 0)
    return c.timeInMillis
}

private fun brDate(iso: String) = iso.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else iso }

/**
 * Regra do webOS. Episódio que saiu nas últimas 3 semanas e o perfil ainda não viu ganha NOVO.
 * Senão, episódio que estreia em até 10 dias ganha a data. Fora disso a série não entra.
 */
internal fun newEpisodeOf(item: MovieItem, last: TmdbNextEpisodeDto?, next: TmdbNextEpisodeDto?, lastSeen: Boolean, now: Long): NewEpisode? {
    val lt = airDateMs(last?.airDate)
    if (last != null && lt != null && now >= lt && now - lt <= 21 * DAY_MS && !lastSeen) {
        return NewEpisode(item, "NOVO", "T${last.season} E${last.episode} saiu ${brDate(last.airDate!!)}", lt, false)
    }
    val nt = airDateMs(next?.airDate)
    if (next != null && nt != null && nt - now <= 10 * DAY_MS && nt >= now - DAY_MS) {
        val d = brDate(next.airDate!!)
        return NewEpisode(item, d.take(5), "T${next.season} E${next.episode} estreia $d", nt, true)
    }
    return null
}

/** Os que já saíram primeiro (o mais recente na frente), depois as estreias pela mais próxima. */
internal fun sortNewEpisodes(list: List<NewEpisode>) =
    list.sortedWith(compareBy<NewEpisode> { it.upcoming }.thenBy { if (it.upcoming) it.at else -it.at })
