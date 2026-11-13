package com.aotv.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.aotv.app.domain.model.MediaType
import com.aotv.app.domain.model.MovieItem
import com.aotv.app.domain.model.SeriesItem

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

data class TmdbMovieDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = "",
    @SerializedName("poster_path")    val posterPath: String? = null,
    @SerializedName("backdrop_path")  val backdropPath: String? = null,
    @SerializedName("release_date")   val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average")   val voteAverage: Double = 0.0,
    @SerializedName("media_type")     val mediaType: String? = null,
    @SerializedName("genre_ids")      val genreIds: List<Int> = emptyList(),
    // Etiqueta do pôster (idioma original e Imperdível precisam de votos).
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("vote_count")     val voteCount: Int = 0,
    @SerializedName("origin_country") val originCountry: List<String>? = null,
    // Gêneros do detalhe (/movie/{id}). A lista traz genre_ids, o detalhe traz este.
    val genres: List<TmdbGenreDto> = emptyList(),
    val adult: Boolean = false,
    // Presente na resposta de /movie/{id} (detail); null nas respostas de lista.
    // Evita chamada separada a /movie/{id}/external_ids para resolver o IMDB ID.
    @SerializedName("imdb_id")        val imdbId: String? = null,
    // Título em idioma original (Lote 3, v1.11.0). Só vem em /movie/{id};
    // null nas respostas de lista. Igual ao originalName das séries.
    @SerializedName("original_title") val originalTitle: String? = null,
    // Orçamento e receita em dólares (TMDB, /movie/{id}). 0 = sem dado
    // (o TMDB usa 0 quando não sabe); nunca inventar valor a partir daqui.
    val budget: Long = 0,
    val revenue: Long = 0,
    // Duração em minutos. Só vem em /movie/{id}. Prova se a fonte é mesmo deste filme.
    val runtime: Int? = null,
    // Classificação indicativa via append_to_response=release_dates
    @SerializedName("release_dates")  val releaseDates: TmdbReleaseDatesDto? = null,
    // Elenco + relacionados via append_to_response=credits,recommendations
    @SerializedName("credits")          val credits: TmdbCreditsDto? = null,
    @SerializedName("recommendations")  val recommendations: TmdbListResponse<TmdbMovieDto>? = null,
    // Trailer via append_to_response=videos
    @SerializedName("videos")           val videos: TmdbVideosDto? = null,
    // Coleção/franquia do filme (Harry Potter, Velozes…). Só vem em /movie/{id}.
    @SerializedName("belongs_to_collection") val collection: TmdbCollectionRefDto? = null,
    // Função na equipe (combined_credits.crew). "Director" alimenta a fonte de diretor das coleções.
    val job: String? = null,
)

// ── Coleções (franquias) ──
/** Referência curta da coleção, embutida no detalhe do filme. */
data class TmdbCollectionRefDto(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("poster_path")   val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
)

/** Resposta de /collection/{id}. [parts] são os filmes da franquia. */
data class TmdbCollectionDto(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerializedName("poster_path")   val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    val parts: List<TmdbMovieDto> = emptyList(),
)

// ── Vídeos (trailers) ──
data class TmdbVideosDto(val results: List<TmdbVideoDto> = emptyList())
data class TmdbVideoDto(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val name: String = "",
    val official: Boolean = false,
    @SerializedName("iso_639_1") val lang: String = "",
)

/** Melhor trailer COMPLETO (key + nome), p/ a fileira "Últimos Trailers". null se não houver. */
fun TmdbVideosDto?.bestTrailer(): TmdbVideoDto? {
    val vids = this?.results?.filter { it.site.equals("YouTube", true) && it.key.isNotBlank() } ?: return null
    fun score(v: TmdbVideoDto): Int {
        var s = 0
        if (v.type.equals("Trailer", true)) s += 4 else if (v.type.equals("Teaser", true)) s += 2
        if (v.official) s += 2
        if (v.lang == "pt") s += 3
        return s
    }
    return vids.maxByOrNull { score(it) }
}

/** Melhor trailer YouTube (Trailer/Teaser oficial; PT-BR primeiro). "" se não houver. */
fun TmdbVideosDto?.bestTrailerKey(): String {
    val vids = this?.results?.filter { it.site.equals("YouTube", true) && it.key.isNotBlank() } ?: return ""
    if (vids.isEmpty()) return ""
    fun score(v: TmdbVideoDto): Int {
        var s = 0
        if (v.type.equals("Trailer", true)) s += 4 else if (v.type.equals("Teaser", true)) s += 2
        if (v.official) s += 2
        if (v.lang == "pt") s += 3
        return s
    }
    return vids.maxByOrNull { score(it) }?.key ?: ""
}

/** TODOS os trailers YouTube ordenados (melhor primeiro) — p/ tentar o próximo se um bloquear embed. */
fun TmdbVideosDto?.trailerKeys(): List<String> {
    val vids = this?.results?.filter { it.site.equals("YouTube", true) && it.key.isNotBlank() } ?: return emptyList()
    fun score(v: TmdbVideoDto): Int {
        var s = 0
        if (v.type.equals("Trailer", true)) s += 4 else if (v.type.equals("Teaser", true)) s += 2
        if (v.official) s += 2
        if (v.lang == "pt") s += 3
        return s
    }
    return vids.sortedByDescending { score(it) }.map { it.key }.distinct()
}

// ── Elenco (credits) ──
data class TmdbCreditsDto(val cast: List<TmdbCastDto> = emptyList())
data class TmdbCastDto(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerializedName("profile_path") val profilePath: String? = null,
)

fun TmdbCastDto.toCastMember() = com.aotv.app.domain.model.CastMember(
    id = id,
    name = name,
    character = character,
    photoUrl = if (profilePath != null) "$TMDB_IMAGE_BASE$profilePath" else "",
)

data class TmdbReleaseDatesDto(val results: List<TmdbReleaseCountryDto> = emptyList())
data class TmdbReleaseCountryDto(
    @SerializedName("iso_3166_1") val country: String = "",
    @SerializedName("release_dates") val dates: List<TmdbReleaseEntryDto> = emptyList(),
)
data class TmdbReleaseEntryDto(val certification: String = "")

/** Classificação indicativa BR (fallback US). Vazio se não houver. */
fun TmdbReleaseDatesDto?.certBR(): String {
    if (this == null) return ""
    fun fromCountry(c: String) = results.firstOrNull { it.country == c }
        ?.dates?.map { it.certification }?.firstOrNull { it.isNotBlank() } ?: ""
    return fromCountry("BR").ifBlank { fromCountry("US") }
}

fun TmdbMovieDto.toMovieItem(embedId: String = id.toString()) = MovieItem(
    tmdbId   = id,
    embedId  = embedId,
    title    = title ?: name ?: "",
    overview = overview ?: "",
    posterUrl   = if (posterPath != null) "$TMDB_IMAGE_BASE$posterPath" else "",
    backdropUrl = if (backdropPath != null) "$TMDB_BACKDROP_BASE$backdropPath" else "",
    year     = (releaseDate ?: firstAirDate ?: "").take(4),
    rating   = voteAverage,
    type     = if (name != null || mediaType == "tv") MediaType.SERIES else MediaType.MOVIE,
    certification = releaseDates.certBR(),
    releaseDate = (releaseDate ?: firstAirDate ?: ""),
    genreIds = genreIdsOf(this),
    originalLanguage = originalLanguage ?: "",
    voteCount = voteCount,
    originCountry = originCountry ?: emptyList(),
)

/**
 * Gêneros da lista (genre_ids) ou do detalhe (genres). O Gson monta o DTO sem passar pelo
 * construtor, então a lista ausente no JSON chega nula mesmo com tipo não nulo.
 */
private fun genreIdsOf(d: TmdbMovieDto): List<Int> {
    val ids: List<Int>? = d.genreIds
    if (!ids.isNullOrEmpty()) return ids
    val full: List<TmdbGenreDto>? = d.genres
    return full.orEmpty().map { it.id }
}

fun TmdbMovieDto.toSeriesItem(embedId: String = id.toString()) = SeriesItem(
    tmdbId       = id,
    embedId      = embedId,
    title        = name ?: title ?: "",
    overview     = overview ?: "",
    posterUrl    = if (posterPath != null) "$TMDB_IMAGE_BASE$posterPath" else "",
    backdropUrl  = if (backdropPath != null) "$TMDB_BACKDROP_BASE$backdropPath" else "",
    firstAirYear = (firstAirDate ?: "").take(4),
    rating       = voteAverage,
)

/** Resposta de /find. Só o id interessa, para trocar "tt..." pelo id do TMDB. */
data class TmdbFindResponse(
    @SerializedName("movie_results") val movieResults: List<TmdbMovieDto> = emptyList(),
    @SerializedName("tv_results")    val tvResults: List<TmdbMovieDto> = emptyList(),
)

data class TmdbListResponse<T>(
    val results: List<T> = emptyList(),
    val page: Int = 1,
    @SerializedName("total_pages")   val totalPages: Int = 1,
    @SerializedName("total_results") val totalResults: Int = 0,
)

// Filmografia (combined_credits). cast = títulos em que atuou, crew = em que trabalhou na equipe.
data class TmdbCreditsListDto(val cast: List<TmdbMovieDto> = emptyList(), val crew: List<TmdbMovieDto> = emptyList())

// ── Logos (imagens) — TMDB tem logo em PT-BR (melhor que metahub) ──
data class TmdbImagesDto(val logos: List<TmdbLogoDto> = emptyList())
data class TmdbLogoDto(
    @SerializedName("file_path") val filePath: String = "",
    @SerializedName("iso_639_1") val lang: String? = null,
    // TMDB NÃO separa pt-BR de pt-PT no iso_639_1 (os dois vêm como "pt"). Quem separa é o
    // iso_3166_1 ("BR" ou "PT"). Sem ler esse campo o app pegava o logo de Portugal, que
    // costuma ter nota mais alta (ex "Sem Volta a Casa" no lugar de "Sem Volta Para Casa").
    @SerializedName("iso_3166_1") val region: String? = null,
)

/**
 * Melhor logo pro público BR. Ordem: pt-BR, pt sem região, inglês, sem idioma, pt-PT por último.
 * Portugal fica no fim de propósito. O título traduzido lá é diferente do daqui.
 */
fun TmdbImagesDto?.bestLogoUrl(): String {
    val logos = this?.logos?.filter { it.filePath.isNotBlank() } ?: return ""
    if (logos.isEmpty()) return ""
    val rank = { l: TmdbLogoDto ->
        val pt = l.lang == "pt" || l.lang == "pt-BR"
        val region = l.region?.uppercase()
        when {
            pt && (region == "BR" || l.lang == "pt-BR") -> 0
            pt && region.isNullOrBlank()                -> 1
            l.lang == "en"                              -> 2
            l.lang == null                              -> 3
            pt                                          -> 4 // sobrou pt-PT
            else                                        -> 5
        }
    }
    val best = logos.minByOrNull(rank) ?: return ""
    return "https://image.tmdb.org/t/p/w342${best.filePath}"
}

data class TmdbGenreDto(val id: Int, val name: String)
data class TmdbGenreListResponse(val genres: List<TmdbGenreDto>)
