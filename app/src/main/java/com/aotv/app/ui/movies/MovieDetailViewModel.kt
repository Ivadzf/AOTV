package com.aotv.app.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aotv.app.data.local.PlatformPrefs
import com.aotv.app.data.remote.TmdbApiService
import com.aotv.app.data.remote.dto.bestLogoUrl
import com.aotv.app.data.remote.dto.bestTrailerKey
import com.aotv.app.data.remote.dto.trailerKeys
import com.aotv.app.data.remote.dto.toCastMember
import com.aotv.app.data.remote.dto.toDomain
import com.aotv.app.data.remote.dto.toMovieItem
import com.aotv.app.data.repository.LibraryRepository
import com.aotv.app.domain.model.EpisodeItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.domain.model.SeriesItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val tmdb: TmdbApiService,
    private val prefs: PlatformPrefs,
    private val library: LibraryRepository,
    private val stremio: com.aotv.app.data.remote.StremioAddonService,
    private val downloads: com.aotv.app.data.repository.DownloadRepository,
    private val youtubeTrailer: com.aotv.app.data.remote.YoutubeTrailer,
    private val prober: com.aotv.app.data.remote.SourceProber,
    private val trakt: com.aotv.app.data.remote.TraktService,
    private val debrid: com.aotv.app.data.debrid.DebridService,
    private val posterInfo: com.aotv.app.data.local.PosterInfo,
) : ViewModel() {

    // URLs já mandadas pro teste. A lista é recriada a cada addon que responde, então o
    // resultado fica por URL em [UiState.probes].
    private val probing = mutableSetOf<String>()

    /** Testa as fontes novas (64 KB cada, 4 por vez no prober) e guarda o resultado. */
    private fun verify(streams: List<com.aotv.app.data.remote.StremioStream>) {
        streams.map { it.url }.filter { it.startsWith("http") && probing.add(it) }.forEach { u ->
            viewModelScope.launch {
                val r = prober.probe(u)
                _uiState.update { it.copy(probes = it.probes + (u to r)) }
            }
        }
        scheduleDebridCheck()
    }

    private var debridJob: kotlinx.coroutines.Job? = null

    /**
     * Pergunta ao debrid quais torrents ele tem prontos. Espera os addons pararem de chegar
     * (1,2 s), porque o Real-Debrid confere tentando resolver e só vale gastar com os melhores.
     */
    private fun scheduleDebridCheck() {
        val a = debrid.active() ?: return
        debridJob?.cancel()
        debridJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_200)
            val st = _uiState.value
            val ep = st.selectedEpisode
            val streams = if (ep != null) st.episodeStremioStreams else st.stremioStreams
            val cap = prefs.maxQuality.takeIf { it > 0 } ?: Int.MAX_VALUE
            val magnets = streams.filter { com.aotv.app.data.torrent.TorrentMagnet.isMagnet(it.url) }
                .sortedWith(
                    compareByDescending<com.aotv.app.data.remote.StremioStream> { if (it.qualityRank > cap) 0 else it.qualityRank }
                        .thenByDescending { com.aotv.app.data.torrent.TorrentMagnet.seeders(it.label) }
                ).map { it.url }
            if (magnets.isEmpty()) return@launch
            debrid.check(magnets, ep?.first ?: 0, ep?.second ?: 0)
            val known = magnets.mapNotNull { u -> debrid.knownCached(u)?.let { u to it } }.toMap()
            _uiState.update { cur ->
                val map = cur.debridCached + known
                val hide = debrid.settings.value.hideUncached
                cur.copy(
                    debridCached = map,
                    debridTag = a.provider.short,
                    stremioStreams = if (hide) cur.stremioStreams.filterNot { map[it.url] == false } else cur.stremioStreams,
                    episodeStremioStreams = if (hide) cur.episodeStremioStreams.filterNot { map[it.url] == false } else cur.episodeStremioStreams,
                )
            }
        }
    }

    /** Tira da lista o torrent que o debrid disse que não tem, quando o ajuste pede. */
    private fun dropUncached(list: List<com.aotv.app.data.remote.StremioStream>, st: UiState) =
        if (!debrid.settings.value.hideUncached || debrid.active() == null) list
        else list.filterNot { st.debridCached[it.url] == false }

    /**
     * Índice da fonte do Assistir dentro de [UiState.stremioStreams]. Melhor resolução que
     * respondeu rápido, no idioma da primeira, fugindo de fonte morta e de outro filme.
     */
    fun bestSourceIndex(): Int = com.aotv.app.data.remote.bestIndex(_uiState.value.movieFacts(), prefs.maxQuality)

    /** Extrai o stream do trailer (YouTube) p/ o ExoPlayer — toca até com embed bloqueado. null = falhou. */
    suspend fun trailerStream(): com.aotv.app.data.remote.YoutubeTrailer.TrailerStream? {
        val keys = _uiState.value.trailerKeys.ifEmpty { listOf(_uiState.value.trailerKey) }.filter { it.isNotBlank() }
        for (k in keys) { youtubeTrailer.stream(k)?.let { return it } }
        return null
    }

    /** URL é baixável (.mp4 direto)? */
    fun isDownloadable(url: String) = downloads.isDownloadable(url)

    /** Enfileira o download de uma fonte .mp4 (filme ou episódio). */
    fun downloadSource(url: String, lastEpisode: String = "") {
        val it = stubItem ?: return
        viewModelScope.launch {
            downloads.enqueue(
                tmdbId = it.tmdbId, type = it.type.name, title = it.title,
                posterUrl = it.posterUrl, sourceUrl = url, lastEpisode = lastEpisode,
            )
        }
    }

    data class UiState(
        val item: MovieItem? = null,
        val series: SeriesItem? = null,
        val seasons: Map<Int, List<EpisodeItem>> = emptyMap(),
        val selectedSeason: Int = 1,
        // (B) Minha Lista — item salvo? (reativo via LibraryRepository.isSaved)
        val isSaved: Boolean = false,
        // (D) Episódios já vistos desta série, como Set "season:episode" (ex "1:3")
        val watchedEpisodes: Set<String> = emptySet(),
        // Episódio EM ANDAMENTO (< 90%) — estado "vendo" no card. "" se nenhum.
        val watchingEpisode: String = "",
        // (A) Episódio SELECIONADO na aba Episódios (season, episode)
        val selectedEpisode: Pair<Int, Int>? = null,
        // IMDB ID da série (addons Stremio só aceitam "tt")
        val seriesImdbId: String? = null,
        // Streams Stremio (HTTP/4K) do episódio selecionado
        val episodeStremioStreams: List<com.aotv.app.data.remote.StremioStream> = emptyList(),
        val episodeStremioLoading: Boolean = false,
        // Streams de addons Stremio (só HTTP direto)
        val stremioStreams: List<com.aotv.app.data.remote.StremioStream> = emptyList(),
        val stremioLoading: Boolean = false,
        // true até os addons responderem. "Assistir" só libera quando false.
        val sourcesLoading: Boolean = true,
        // Continuar assistindo — progresso salvo (posição + última fonte + episódio)
        val resumePositionMs: Long = 0,
        val resumeDurationMs: Long = 0,
        val resumeSourceUrl: String = "",
        val resumeLastEpisode: String = "",
        /**
         * Já consultou o histórico? Enquanto false o botão principal NÃO age: tocar antes
         * disso caía no caminho "Assistir do zero" e o player salvava posição 0 por cima
         * do progresso real, apagando o "continuar assistindo".
         */
        val resumeLoaded: Boolean = false,
        /** Temporada que o usuário está assistindo (do histórico). 0 = nenhuma. */
        val watchingSeason: Int = 0,
        // Elenco + filmes/séries relacionados (TMDB credits + recommendations)
        val cast: List<com.aotv.app.domain.model.CastMember> = emptyList(),
        val related: List<MovieItem> = emptyList(),
        // ── Coleção/franquia do filme, em ordem cronológica de lançamento ──
        val collectionName: String = "",
        val collectionItems: List<MovieItem> = emptyList(),
        // Chave YouTube do trailer ("" se não houver)
        val trailerKey: String = "",
        // Todos os trailers (melhor primeiro) — tenta o próximo se um bloquear embed (erro 152).
        val trailerKeys: List<String> = emptyList(),
        // Logo do título (TMDB PT-BR → metahub fallback). "" = usa texto.
        val logoUrl: String = "",
        // Filmografia do ator selecionado (clicar no elenco) — null = fechado.
        val personName: String? = null,
        val personCredits: List<MovieItem> = emptyList(),
        val personLoading: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Duração do filme no TMDB, em minutos. 0 = desconhecida. */
        val runtimeMin: Int = 0,
        /** Teste das fontes por URL (filme e episódio). */
        val probes: Map<String, com.aotv.app.data.remote.ProbeResult> = emptyMap(),
        /** Magnet → o debrid tem pronto (true) ou não tem (false). Ausente = não se sabe. */
        val debridCached: Map<String, Boolean> = emptyMap(),
        /** Sigla do debrid em uso ("RD", "TB", "PM"). */
        val debridTag: String = "",
    ) {
        /** Fontes com o resultado do teste. [runtime] 0 desliga a checagem de outro filme. */
        fun factsOf(streams: List<com.aotv.app.data.remote.StremioStream>, runtime: Int) = streams.map {
            val p = probes[it.url]
            com.aotv.app.data.remote.SourceFacts(
                it.url, it.addon, it.releaseText.ifBlank { it.label }, it.qualityRank, p,
                wrongTitle = p != null && com.aotv.app.data.remote.durationMismatch(runtime, p.sec),
                debrid = if (debridCached[it.url] == true) 2 else 0,
            )
        }
        fun movieFacts() = factsOf(stremioStreams, runtimeMin)
        // Episódio não confere duração (o TMDB varia muito por episódio), igual ao webOS.
        fun episodeFacts() = factsOf(episodeStremioStreams, 0)

        /** Todas as fontes do filme parecem de outro filme (já testadas e com duração errada). */
        val allWrongTitle get() = stremioStreams.isNotEmpty() && movieFacts().all { it.wrongTitle }
    }

    /** Chave canônica de um episódio em [UiState.watchedEpisodes]. */
    private fun epKey(season: Int, episode: Int) = "$season:$episode"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Stub do MovieItem que abriu a tela (título/poster/ano) — usado p/ salvar na lista
    // e registrar reprodução, já que load() recebe apenas embedId/tmdbId/type.
    private var stubItem: MovieItem? = null
    // Garante que os coletores reativos (isSaved / watchedEpisodes) só sejam abertos 1x.
    private var observersStarted = false

    /**
     * Inicializa a tela. Guarda o [stub] (p/ salvar na lista e registrar histórico),
     * abre os coletores reativos da BIBLIOTECA (B: isSaved, D: episódios vistos) e
     * dispara o carregamento de detalhes/fontes.
     */
    fun load(stub: MovieItem) {
        stubItem = stub
        startLibraryObservers(stub.tmdbId, stub.type)
        // Continuar assistindo: lê progresso salvo (posição + última fonte + episódio)
        viewModelScope.launch {
            val pb = runCatching { library.getPlayback(stub.tmdbId, stub.type.name) }.getOrNull()
            if (pb == null) {
                _uiState.update { it.copy(resumeLoaded = true) }
            } else {
                // "Vendo": tem progresso mas ainda não passou de 90% (não terminou).
                val notFinished = pb.durationMs <= 0 || pb.positionMs < pb.durationMs * 0.9
                val watching = if (notFinished) {
                    Regex("T(\\d+):E(\\d+)").find(pb.lastEpisode)?.let { m ->
                        val s = m.groupValues[1].toIntOrNull(); val e = m.groupValues[2].toIntOrNull()
                        if (s != null && e != null) epKey(s, e) else ""
                    } ?: ""
                } else ""
                // Temporada do histórico: a lista de episódios abre NELA, não sempre na 1ª.
                val seasonOfHistory = Regex("T(\\d+):E(\\d+)").find(pb.lastEpisode)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                _uiState.update {
                    it.copy(
                        resumePositionMs = pb.positionMs,
                        resumeDurationMs = pb.durationMs,
                        resumeSourceUrl = pb.lastSourceUrl,
                        resumeLastEpisode = pb.lastEpisode,
                        watchingEpisode = watching,
                        resumeLoaded = true,
                        watchingSeason = seasonOfHistory,
                        selectedSeason = if (seasonOfHistory > 0) seasonOfHistory else it.selectedSeason,
                    )
                }
            }
        }
        load(stub.embedId, stub.tmdbId, stub.type)
    }

    /** Abre os Flows reativos da BIBLIOTECA (uma única vez por tela). */
    private fun startLibraryObservers(tmdbId: Int, type: MediaType) {
        if (observersStarted) return
        observersStarted = true
        // (B) item salvo na Minha Lista?
        viewModelScope.launch {
            library.isSaved(tmdbId, type.name).collect { saved ->
                _uiState.update { it.copy(isSaved = saved) }
            }
        }
        // (D) episódios vistos (apenas séries têm; p/ filmes o Flow fica vazio)
        if (type == MediaType.SERIES) {
            viewModelScope.launch {
                library.watchedEpisodes(tmdbId).collect { list ->
                    _uiState.update { st ->
                        st.copy(watchedEpisodes = list.map { epKey(it.season, it.episode) }.toSet())
                    }
                }
            }
        }
    }

    fun load(embedId: String, tmdbId: Int, type: MediaType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, sourcesLoading = true) }
            val apiKey = prefs.tmdbApiKey

            if (type == MediaType.MOVIE) {
                val addons = prefs.stremioAddonList()

                // Busca o detalhe PRIMEIRO — /movie/{id} inclui imdb_id (rápido, ~500ms).
                val detailDto = if (apiKey.isNotBlank() && tmdbId > 0) {
                    runCatching { tmdb.getMovieDetail(tmdbId, apiKey = apiKey) }.getOrNull()
                } else null

                // Resolve IMDB ID: 1) embedId já é "tt...", 2) imdb_id do detail, 3) external_ids
                val resolvedEmbedId = when {
                    embedId.startsWith("tt") -> embedId
                    detailDto?.imdbId?.startsWith("tt") == true -> detailDto.imdbId!!
                    apiKey.isNotBlank() && tmdbId > 0 ->
                        runCatching { tmdb.getMovieExternalIds(tmdbId, apiKey).imdbId }
                            .getOrNull()?.takeIf { it.startsWith("tt") } ?: embedId
                    else -> embedId
                }

                // Dispara Stremio com IMDB ID (FrostStream SÓ aceita "tt"; FenixFlix aceita tt e tmdb:).
                // IMDB quando existe. O getStreams repete com "tmdb:{id}" sozinho quando o
                // addon não devolve nada por IMDB (UnioFlix rende mais assim, FrostStream só "tt").
                val stremioId = if (resolvedEmbedId.startsWith("tt")) resolvedEmbedId else ""
                val srcJobs = mutableListOf<kotlinx.coroutines.Job>()
                val slots = arrayOfNulls<List<com.aotv.app.data.remote.StremioStream>>(addons.size)
                if (addons.isNotEmpty() && tmdbId > 0) {
                    // PROGRESSIVO por addon: cada um aparece assim que chega, na ORDEM dos addons
                    // (FrostStream 1º). Addon morto/lento preenche seu slot vazio no timeout, mas
                    // NÃO segura os que já carregaram (antes o awaitAll esperava todos).
                    _uiState.update { it.copy(stremioLoading = true) }
                    addons.forEachIndexed { idx, addon ->
                        srcJobs += launch {
                            val s = runCatching { stremio.getStreams(addon, "movie", stremioId, tmdbId = tmdbId) }.getOrDefault(emptyList())
                            slots[idx] = s
                            _uiState.update { st ->
                                st.copy(
                                    stremioStreams = dropUncached(prefs.sortStreams(slots.filterNotNull()
                                        .flatMap { g -> g.sortedByDescending { it.qualityRank } }), st),
                                    stremioLoading = slots.any { it == null },
                                )
                            }
                            verify(s)
                        }
                    }
                }

                val detail = detailDto?.toMovieItem(resolvedEmbedId)
                val castList = detailDto?.credits?.cast.orEmpty()
                    .filter { it.name.isNotBlank() }.take(20).map { it.toCastMember() }
                val relatedList = detailDto?.recommendations?.results.orEmpty()
                    .filterNot { it.adult }.map { it.toMovieItem() }.take(20)

                // Mostra o filme JÁ (hero). As fontes dos addons entram conforme chegam.
                val trailer = detailDto?.videos.bestTrailerKey()
                val trailers = detailDto?.videos.trailerKeys()
                _uiState.update { it.copy(item = detail, runtimeMin = detailDto?.runtime ?: 0, cast = castList, related = relatedList, trailerKey = trailer, trailerKeys = trailers, isLoading = false) }

                // Franquia (Harry Potter, Velozes…): carrega em paralelo e ordena por data de
                // lançamento, que é a ordem cronológica que o usuário espera assistir.
                detailDto?.collection?.takeIf { it.id > 0 }?.let { ref ->
                    launch {
                        val col = runCatching { tmdb.getCollection(ref.id, apiKey = apiKey) }.getOrNull()
                        val parts = col?.parts.orEmpty()
                            .filterNot { it.adult }
                            .filter { !it.posterPath.isNullOrBlank() }
                            // Sem data (ainda não lançado) vai pro fim, não pro começo.
                            .sortedBy { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: "9999" }
                            .map { it.toMovieItem() }
                        if (parts.size > 1) {
                            _uiState.update {
                                it.copy(collectionName = col?.name.orEmpty().ifBlank { ref.name }, collectionItems = parts)
                            }
                        }
                    }
                }

                // Logo do título: TMDB (PT-BR) principal, metahub fallback.
                launch {
                    val tmdbLogo = if (apiKey.isNotBlank() && tmdbId > 0)
                        runCatching { tmdb.getMovieImages(tmdbId, apiKey = apiKey).bestLogoUrl() }.getOrNull().orEmpty()
                    else ""
                    val logo = tmdbLogo.ifBlank {
                        if (resolvedEmbedId.startsWith("tt")) "https://images.metahub.space/logo/medium/$resolvedEmbedId/img" else ""
                    }
                    if (logo.isNotBlank()) _uiState.update { it.copy(logoUrl = logo) }
                }

                // Libera "Assistir" só quando todos os addons responderem.
                launch {
                    srcJobs.joinAll()
                    // Resumo para os selos do pôster (4K, HDR, Dolby, DUB), igual ao webOS.
                    if (srcJobs.isNotEmpty()) posterInfo.remember(com.aotv.app.domain.model.MediaType.MOVIE, tmdbId, slots.filterNotNull().flatten())
                    _uiState.update { it.copy(sourcesLoading = false) }
                }
            } else {
                // Chamadas INDEPENDENTES em paralelo (antes tudo em série → detalhe de série lento).
                val detailDtoD = async {
                    if (apiKey.isNotBlank() && tmdbId > 0) runCatching { tmdb.getSeriesDetail(tmdbId, apiKey = apiKey) }.getOrNull() else null
                }
                val seriesImdbD = async {
                    when {
                        embedId.startsWith("tt") -> embedId
                        apiKey.isNotBlank() && tmdbId > 0 ->
                            runCatching { tmdb.getSeriesExternalIds(tmdbId, apiKey).imdbId }.getOrNull()?.takeIf { it.startsWith("tt") }
                        else -> null
                    }
                }
                val episodesD = async {
                    if (apiKey.isNotBlank() && tmdbId > 0)
                        runCatching { tmdb.getSeason(tmdbId, 1, apiKey = apiKey).episodes.map { it.toDomain() } }.getOrDefault(emptyList())
                    else emptyList()
                }
                val detailDto = detailDtoD.await()
                val seriesDetail = detailDto?.toDomain(embedId)
                val seriesImdb = seriesImdbD.await()
                val episodes = episodesD.await()
                val castList = detailDto?.credits?.cast.orEmpty()
                    .filter { it.name.isNotBlank() }.take(20).map { it.toCastMember() }
                val relatedList = detailDto?.recommendations?.results.orEmpty()
                    .filterNot { it.adult }.map { it.toMovieItem() }.take(20)
                val trailer = detailDto?.videos.bestTrailerKey()
                val trailers = detailDto?.videos.trailerKeys()
                _uiState.update { state ->
                    state.copy(
                        series = seriesDetail,
                        cast = castList,
                        related = relatedList,
                        trailerKey = trailer,
                        trailerKeys = trailers,
                        seasons = if (episodes.isNotEmpty()) mapOf(1 to episodes) else emptyMap(),
                        seriesImdbId = seriesImdb,
                        isLoading = false,
                        sourcesLoading = false,
                    )
                }
                // Logo do título: TMDB (PT-BR) principal, metahub fallback.
                launch {
                    val tmdbLogo = if (apiKey.isNotBlank() && tmdbId > 0)
                        runCatching { tmdb.getSeriesImages(tmdbId, apiKey = apiKey).bestLogoUrl() }.getOrNull().orEmpty()
                    else ""
                    val logo = tmdbLogo.ifBlank {
                        if (seriesImdb?.startsWith("tt") == true) "https://images.metahub.space/logo/medium/$seriesImdb/img" else ""
                    }
                    if (logo.isNotBlank()) _uiState.update { it.copy(logoUrl = logo) }
                }
            }
        }
    }

    // ───────────────────────── (B) MINHA LISTA ─────────────────────────

    /**
     * Alterna o item atual na Minha Lista. Usa o [stubItem] (título/poster/ano/tipo)
     * passado em [load]. Sem stub não há o que salvar (no-op).
     */
    fun toggleSave() {
        val item = stubItem ?: return
        viewModelScope.launch {
            val adding = !_uiState.value.isSaved
            if (adding) library.saveToList(item) else library.removeFromList(item)
            trakt.watchlistChanged(item.tmdbId, item.type.name, adding)
            // o estado isSaved é atualizado pelo coletor reativo de startLibraryObservers
        }
    }

    // ───────────────────────── (C) CONTINUAR ASSISTINDO ─────────────────────────

    /**
     * Registra o título atual no histórico de reproduções (filme ou série). Chamado
     * no clique de "Assistir"/fonte — não depende do player abrir de fato.
     * [lastEpisode] ex "T1:E3" p/ série, vazio p/ filme.
     */
    fun recordPlayback(lastEpisode: String = "") {
        val item = stubItem ?: return
        viewModelScope.launch {
            library.recordPlayback(item, lastEpisode = lastEpisode)
        }
    }

    // ───────────────────────── FILMOGRAFIA DO ATOR ─────────────────────────

    /** Abre a filmografia do ator (combined_credits do TMDB), ordenada por popularidade/ano. */
    fun openPerson(personId: Int, name: String) {
        _uiState.update { it.copy(personName = name, personCredits = emptyList(), personLoading = true) }
        viewModelScope.launch {
            val apiKey = prefs.tmdbApiKey
            val credits = if (apiKey.isNotBlank() && personId > 0) {
                runCatching { tmdb.getPersonCredits(personId, apiKey = apiKey).cast }.getOrDefault(emptyList())
            } else emptyList()
            val items = credits
                .filterNot { it.adult }
                .filter { (it.posterPath != null) && (it.title != null || it.name != null) }
                .map { it.toMovieItem() }
                .distinctBy { it.tmdbId }
                .sortedByDescending { it.year }
            _uiState.update {
                if (it.personName != name) it // outro ator/foi fechado
                else it.copy(personCredits = items, personLoading = false)
            }
        }
    }

    fun closePerson() {
        _uiState.update { it.copy(personName = null, personCredits = emptyList(), personLoading = false) }
    }

    // ───────────────────────── (D) EPISÓDIOS VISTOS ─────────────────────────

    /** Marca um episódio como visto (chamado ao tocar o episódio). */
    fun markEpisodeWatched(season: Int, episode: Int) {
        val item = stubItem ?: return
        viewModelScope.launch {
            library.markEpisodeWatched(item.tmdbId, season, episode)
            // (C) também registra a série no "continuar assistindo" com o último episódio
            library.recordPlayback(item, lastEpisode = "T$season:E$episode")
        }
    }

    // ───────────────────────── (A) FONTES DO EPISÓDIO SELECIONADO ─────────────────────────

    /** Busca nos addons as fontes do episódio selecionado, para o painel de fontes. */
    fun selectEpisode(season: Int, episode: Int) {
        // Stremio do episódio (HTTP/4K) — PROGRESSIVO por addon: cada um aparece assim que
        // responde, na ordem dos addons. Addon lento/morto não segura os que já chegaram
        // (antes o awaitAll esperava todos → até 10s de tela vazia ao trocar de episódio).
        val imdb = _uiState.value.seriesImdbId.orEmpty()
        // Série não preenche state.item, então o tmdbId vem do stub que abriu a tela.
        val seriesTmdb = stubItem?.tmdbId ?: 0
        val addons = prefs.stremioAddonList()
        val canSearch = (imdb.isNotBlank() || seriesTmdb > 0) && addons.isNotEmpty()
        _uiState.update {
            it.copy(
                selectedEpisode = season to episode,
                episodeStremioStreams = emptyList(),
                episodeStremioLoading = canSearch,
            )
        }
        if (canSearch) {
            val slots = arrayOfNulls<List<com.aotv.app.data.remote.StremioStream>>(addons.size)
            addons.forEachIndexed { idx, addon ->
                viewModelScope.launch {
                    val s = runCatching { stremio.getStreams(addon, "series", imdb, season, episode, seriesTmdb) }
                        .getOrDefault(emptyList())
                    slots[idx] = s
                    // Todos os addons responderam. O episódio também vale para o pôster da série.
                    if (slots.none { it == null }) posterInfo.remember(com.aotv.app.domain.model.MediaType.SERIES, seriesTmdb, slots.filterNotNull().flatten())
                    if (_uiState.value.selectedEpisode == (season to episode)) verify(s)
                    _uiState.update { st ->
                        if (st.selectedEpisode != (season to episode)) st
                        else st.copy(
                            // Ordem dos ADDONS (slots seguem o gerenciador) e, dentro de cada
                            // addon, maior qualidade primeiro.
                            episodeStremioStreams = dropUncached(prefs.sortStreams(slots.filterNotNull()
                                .flatMap { g -> g.sortedByDescending { it.qualityRank } }), st),
                            episodeStremioLoading = slots.any { it == null },
                        )
                    }
                }
            }
        }
    }

    /** Fecha o painel de servidores do episódio selecionado. */
    fun clearSelectedEpisode() {
        _uiState.update {
            it.copy(
                selectedEpisode = null,
                episodeStremioStreams = emptyList(), episodeStremioLoading = false,
            )
        }
    }

    fun loadSeason(tmdbId: Int, season: Int) {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSeason = season) }
            val episodes = runCatching {
                tmdb.getSeason(tmdbId, season, apiKey = apiKey).episodes.map { it.toDomain() }
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(seasons = it.seasons + (season to episodes)) }
        }
    }
}
