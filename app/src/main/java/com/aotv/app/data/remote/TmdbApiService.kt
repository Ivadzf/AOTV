package com.aotv.app.data.remote

import com.aotv.app.data.remote.dto.*
import retrofit2.http.*

interface TmdbApiService {
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    // Ranking de hoje (#N Hoje nos pôsteres). Duas páginas dão os 40 primeiros.
    @GET("trending/movie/day")
    suspend fun getTrendingMoviesDay(
        @Query("page")     page: Int = 1,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("trending/tv/day")
    suspend fun getTrendingTvDay(
        @Query("page")     page: Int = 1,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbGenreListResponse

    @GET("genre/tv/list")
    suspend fun getTvGenres(
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbGenreListResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres")     genreId: Int? = null,
        @Query("language")        lang: String = "pt-BR",
        @Query("sort_by")         sort: String = "popularity.desc",
        @Query("page")            page: Int = 1,
        @Query("vote_count.gte")  voteCountGte: Int? = null,
        @Query("primary_release_year") year: Int? = null,
        @Query("include_adult")   includeAdult: Boolean = false,
        // Exclui keywords adultas/eróticas conhecidas (softcore etc.) do catálogo da home.
        @Query("without_keywords") withoutKeywords: String? = "190370,159615,155477,2384,313843",
        // Região Brasil: só títulos disponíveis em plataformas do BR (evita regionais chineses etc.).
        @Query("watch_region")    watchRegion: String = "BR",
        @Query("with_watch_monetization_types") monetization: String = "flatrate|free|ads|rent|buy",
        @Query("region")          region: String = "BR",
        @Query("api_key")         apiKey: String,
        // Vários gêneros de uma vez ("16|10751" é um ou outro, "16,12" os dois). Perfil infantil.
        @Query("with_genres")     genres: String? = null,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("movie/now_playing")
    suspend fun getNowPlaying(
        @Query("language") lang: String = "pt-BR",
        @Query("region")   region: String = "BR",
        @Query("page")     page: Int = 1,
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres")     genreId: Int? = null,
        @Query("language")        lang: String = "pt-BR",
        @Query("sort_by")         sort: String = "popularity.desc",
        @Query("page")            page: Int = 1,
        @Query("vote_count.gte")  voteCountGte: Int? = null,
        @Query("first_air_date_year") year: Int? = null,
        @Query("include_adult")   includeAdult: Boolean = false,
        // Região Brasil: só séries disponíveis em plataformas do BR.
        @Query("watch_region")    watchRegion: String = "BR",
        @Query("with_watch_monetization_types") monetization: String = "flatrate|free|ads|rent|buy",
        @Query("api_key")         apiKey: String,
        @Query("with_genres")     genres: String? = null,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("movie/{id}")
    suspend fun getMovieDetail(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("append_to_response") append: String = "release_dates,credits,recommendations,videos",
        @Query("api_key")  apiKey: String,
    ): TmdbMovieDto

    @GET("tv/{id}")
    suspend fun getSeriesDetail(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("append_to_response") append: String = "content_ratings,credits,recommendations,videos",
        @Query("api_key")  apiKey: String,
    ): TmdbSeriesDetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun getSeason(
        @Path("id")        id: Int,
        @Path("season")    season: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbSeasonDto

    @GET("search/multi")
    suspend fun search(
        @Query("query")         query: String,
        @Query("language")      lang: String = "pt-BR",
        @Query("page")          page: Int = 1,
        // Busca MOSTRA adulto (a home não); usuário pediu adulto só na pesquisa.
        @Query("include_adult") includeAdult: Boolean = true,
        @Query("api_key")       apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    // Acha o id do TMDB a partir do IMDB (catálogo de addon vem com "tt...").
    @GET("find/{id}")
    suspend fun findByImdb(
        @Path("id")              id: String,
        @Query("external_source") source: String = "imdb_id",
        @Query("api_key")        apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbFindResponse

    // Retorna imdb_id para filmes TMDB (os addons Stremio buscam por "tt")
    @GET("movie/{id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("id")       id: Int,
        @Query("api_key") apiKey: String,
    ): TmdbExternalIds

    // imdb_id para séries TMDB (necessário p/ addons Stremio que só aceitam "tt")
    @GET("tv/{id}/external_ids")
    suspend fun getSeriesExternalIds(
        @Path("id")       id: Int,
        @Query("api_key") apiKey: String,
    ): TmdbExternalIds

    // Filmografia do ator (filmes + séries em que atuou).
    @GET("person/{id}/combined_credits")
    suspend fun getPersonCredits(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbCreditsListDto

    // Logos do filme/série (TMDB tem logo em PT-BR). include_image_language pega PT+EN+neutro.
    @GET("movie/{id}/images")
    suspend fun getMovieImages(
        @Path("id")                       id: Int,
        @Query("include_image_language")  imgLang: String = "pt,en,null",
        @Query("api_key")                 apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbImagesDto

    // Recomendações de UM título. Alimentam a fileira "Porque você assistiu".
    @GET("movie/{id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("page")     page: Int = 1,
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("tv/{id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("page")     page: Int = 1,
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    /** Coleção/franquia completa (parts = todos os filmes). */
    @GET("collection/{id}")
    suspend fun getCollection(
        @Path("id")        id: Int,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbCollectionDto

    // ── Fontes do TMDB das coleções (formato do Nuvio) ──
    @GET("list/{id}")
    suspend fun getList(
        @Path("id")        id: Int,
        @Query("page")     page: Int = 1,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListDetailsDto

    @GET("company/{id}")
    suspend fun getCompany(@Path("id") id: Int, @Query("api_key") apiKey: String): TmdbCompanyDto

    @GET("network/{id}")
    suspend fun getNetwork(@Path("id") id: Int, @Query("api_key") apiKey: String): TmdbCompanyDto

    @GET("person/{id}")
    suspend fun getPerson(@Path("id") id: Int, @Query("language") lang: String = "pt-BR", @Query("api_key") apiKey: String): TmdbPersonDto

    @GET("search/company")
    suspend fun searchCompanies(@Query("query") query: String, @Query("api_key") apiKey: String): TmdbListResponse<TmdbCompanyDto>

    @GET("search/collection")
    suspend fun searchCollections(
        @Query("query")    query: String,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbCollectionRefDto>

    @GET("search/person")
    suspend fun searchPeople(
        @Query("query")    query: String,
        @Query("language") lang: String = "pt-BR",
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbPersonDto>

    @GET("search/keyword")
    suspend fun searchKeywords(@Query("query") query: String, @Query("api_key") apiKey: String): TmdbListResponse<TmdbNamedDto>

    @GET("watch/providers/{type}")
    suspend fun getWatchProviders(
        @Path("type")          type: String,
        @Query("watch_region") region: String,
        @Query("language")     lang: String = "pt-BR",
        @Query("api_key")      apiKey: String,
    ): TmdbProvidersDto

    /** Discover com os filtros montados em TmdbCollectionRules.discoverQuery. [type] é movie ou tv. */
    @GET("discover/{type}")
    suspend fun discoverRaw(
        @Path("type")           type: String,
        @QueryMap               query: Map<String, String>,
        @Query("language")      lang: String = "pt-BR",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("api_key")       apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    // ── Página "Descobrir" (filtros livres + ordenação) ──────────────────────
    // Endpoints separados dos discover* da home de propósito: aqui with_genres é STRING
    // (vários gêneros por vírgula = E) e entram classificação indicativa e nota mínima.
    @GET("discover/movie")
    suspend fun discoverMoviesFiltered(
        @Query("with_genres")          withGenres: String? = null,
        @Query("sort_by")              sort: String = "popularity.desc",
        @Query("page")                 page: Int = 1,
        @Query("vote_average.gte")     voteGte: Float? = null,
        @Query("vote_count.gte")       voteCountGte: Int? = 40,
        @Query("primary_release_year") year: Int? = null,
        @Query("certification_country") certCountry: String? = null,
        @Query("certification")        certification: String? = null,
        @Query("language")             lang: String = "pt-BR",
        @Query("include_adult")        includeAdult: Boolean = false,
        @Query("without_keywords")     withoutKeywords: String = "190370,159615,155477,2384,313843",
        @Query("watch_region")         watchRegion: String = "BR",
        @Query("region")               region: String = "BR",
        @Query("api_key")              apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    // discover/tv não aceita certification (o TMDB só tem isso pra filme).
    @GET("discover/tv")
    suspend fun discoverTvFiltered(
        @Query("with_genres")           withGenres: String? = null,
        @Query("sort_by")               sort: String = "popularity.desc",
        @Query("page")                  page: Int = 1,
        @Query("vote_average.gte")      voteGte: Float? = null,
        @Query("vote_count.gte")        voteCountGte: Int? = 40,
        @Query("first_air_date_year")   year: Int? = null,
        @Query("language")              lang: String = "pt-BR",
        @Query("include_adult")         includeAdult: Boolean = false,
        @Query("watch_region")          watchRegion: String = "BR",
        @Query("api_key")               apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    // ── Fileira "Últimos Trailers" ──
    // /videos com include_video_language pega o dublado/legendado BR e cai pro inglês.
    @GET("movie/{id}/videos")
    suspend fun getMovieVideos(
        @Path("id")                      id: Int,
        @Query("language")               lang: String = "pt-BR",
        @Query("include_video_language") vidLang: String = "pt,en,null",
        @Query("api_key")                apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbVideosDto

    @GET("tv/{id}/videos")
    suspend fun getTvVideos(
        @Path("id")                      id: Int,
        @Query("language")               lang: String = "pt-BR",
        @Query("include_video_language") vidLang: String = "pt,en,null",
        @Query("api_key")                apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbVideosDto

    @GET("tv/on_the_air")
    suspend fun getOnTheAir(
        @Query("language") lang: String = "pt-BR",
        @Query("page")     page: Int = 1,
        @Query("api_key")  apiKey: String,
    ): TmdbListResponse<TmdbMovieDto>

    @GET("tv/{id}/images")
    suspend fun getSeriesImages(
        @Path("id")                       id: Int,
        @Query("include_image_language")  imgLang: String = "pt,en,null",
        @Query("api_key")                 apiKey: String,
    ): com.aotv.app.data.remote.dto.TmdbImagesDto
}

data class TmdbExternalIds(
    @com.google.gson.annotations.SerializedName("imdb_id") val imdbId: String? = null,
)
