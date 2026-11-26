package com.aotv.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.aotv.app.domain.model.EpisodeItem
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.domain.model.SeriesItem

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

data class TmdbSeriesDetailDto(
    val id: Int,
    val name: String,
    @SerializedName("original_name")     val originalName: String? = null,
    val overview: String? = "",
    @SerializedName("poster_path")       val posterPath: String? = null,
    @SerializedName("backdrop_path")     val backdropPath: String? = null,
    @SerializedName("first_air_date")    val firstAirDate: String? = null,
    @SerializedName("vote_average")      val voteAverage: Double = 0.0,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 1,
    val genres: List<TmdbGenreDto> = emptyList(),
    val adult: Boolean = false,
    // Classificação indicativa via append_to_response=content_ratings
    @SerializedName("content_ratings") val contentRatings: TmdbContentRatingsDto? = null,
    // Elenco + relacionados via append_to_response=credits,recommendations
    @SerializedName("credits")         val credits: TmdbCreditsDto? = null,
    @SerializedName("recommendations") val recommendations: TmdbListResponse<TmdbMovieDto>? = null,
    @SerializedName("videos")          val videos: TmdbVideosDto? = null,
    // Próximo episódio a ir ao ar (séries semanais/em produção) + status.
    @SerializedName("next_episode_to_air") val nextEpisodeToAir: TmdbNextEpisodeDto? = null,
    @SerializedName("last_episode_to_air") val lastEpisodeToAir: TmdbNextEpisodeDto? = null,
    @SerializedName("status")              val status: String? = null,
    @SerializedName("in_production")       val inProduction: Boolean = false,
    // Última exibição (p/ o intervalo de anos "1998–2008" no detalhe). O /tv/{id} já devolve;
    // as listas (/tv/popular etc.) NÃO — por isso o catálogo só tem o ano inicial.
    @SerializedName("last_air_date")       val lastAirDate: String? = null,
    // Estúdios de produção (folha Sobre, v1.21.0). Só vem em /tv/{id}.
    @SerializedName("production_companies") val productionCompanies: List<TmdbCompanyDto> = emptyList(),
    // Temporadas com pôster (Lote 2 item 9e — toggle Pôsteres/Texto). Só vem em /tv/{id}.
    @SerializedName("seasons") val seasons: List<TmdbSeasonDto> = emptyList(),
)

data class TmdbNextEpisodeDto(
    @SerializedName("air_date")       val airDate: String? = null,
    @SerializedName("season_number")  val season: Int = 0,
    @SerializedName("episode_number") val episode: Int = 0,
)

data class TmdbContentRatingsDto(val results: List<TmdbContentRatingDto> = emptyList())
data class TmdbContentRatingDto(
    @SerializedName("iso_3166_1") val country: String = "",
    val rating: String = "",
)

fun TmdbContentRatingsDto?.certBR(): String {
    if (this == null) return ""
    fun byC(c: String) = results.firstOrNull { it.country == c }?.rating?.takeIf { it.isNotBlank() } ?: ""
    return byC("BR").ifBlank { byC("US") }
}

fun TmdbSeriesDetailDto.toDomain(embedId: String = id.toString()) = SeriesItem(
    tmdbId          = id,
    embedId         = embedId,
    title           = name,
    overview        = overview ?: "",
    posterUrl       = if (posterPath != null) "$TMDB_IMAGE_BASE$posterPath" else "",
    backdropUrl     = if (backdropPath != null) "$TMDB_BACKDROP_BASE$backdropPath" else "",
    firstAirYear    = (firstAirDate ?: "").take(4),
    rating          = voteAverage,
    numberOfSeasons = numberOfSeasons,
    genres          = genres.map { it.name },
    certification   = contentRatings.certBR(),
    firstAirDate    = firstAirDate ?: "",
    lastAirDate     = lastAirDate ?: "",
    nextEpisodeDate = nextEpisodeToAir?.airDate ?: "",
    nextEpisodeSE   = nextEpisodeToAir?.let { if (it.season > 0) "T${it.season} E${it.episode}" else "" } ?: "",
    status          = status ?: "",
)

data class TmdbSeasonDto(
    @SerializedName("season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
    // Pôster da temporada (só vem em /tv/{id}; o detalhe /season/{n} não traz).
    @SerializedName("poster_path") val posterPath: String? = null,
)

data class TmdbEpisodeDto(
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number")  val seasonNumber: Int,
    val name: String = "",
    val overview: String = "",
    @SerializedName("still_path")     val stillPath: String? = null,
    @SerializedName("air_date")       val airDate: String? = null,
    @SerializedName("runtime")        val runtime: Int? = null,
    @SerializedName("vote_average")   val voteAverage: Double = 0.0,
)

fun TmdbEpisodeDto.toDomain() = EpisodeItem(
    episodeNumber = episodeNumber,
    seasonNumber  = seasonNumber,
    name          = name,
    overview      = overview,
    stillUrl      = if (stillPath != null) "https://image.tmdb.org/t/p/w300$stillPath" else "",
    airDate       = airDate ?: "",
    runtimeMin    = runtime ?: 0,
    rating        = voteAverage,
)

/**
 * Série do detalhe (/tv/{id}) como MovieItem — p/ as listas do MDBList no
 * Descobrir (Lote 21, v1.31.0), que resolvem cada item por id no TMDB.
 */
fun TmdbSeriesDetailDto.toMovieItem(embedId: String = id.toString()) = MovieItem(
    tmdbId      = id,
    embedId     = embedId,
    title       = name,
    overview    = overview ?: "",
    posterUrl   = if (posterPath != null) "$TMDB_IMAGE_BASE$posterPath" else "",
    backdropUrl = if (backdropPath != null) "$TMDB_BACKDROP_BASE$backdropPath" else "",
    year        = (firstAirDate ?: "").take(4),
    rating      = voteAverage,
    type        = MediaType.SERIES,
    releaseDate = firstAirDate ?: "",
    // Intervalo de anos no pôster/detalhe (só o detalhe traz last_air_date + status).
    lastAirDate = lastAirDate ?: "",
    seriesStatus = status ?: "",
)
