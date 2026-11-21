package com.aotv.app.domain.model

data class MovieItem(
    val tmdbId: Int,
    val embedId: String,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val year: String,
    val rating: Double,
    val genres: List<String> = emptyList(),
    val type: MediaType = MediaType.MOVIE,
    // Classificação indicativa (ex.: "L", "10", "12", "14", "16", "18"). Vazio = sem dado.
    val certification: String = "",
    // Data completa de lançamento (yyyy-MM-dd). Também vem nas listas, para a etiqueta Novo e Em breve.
    val releaseDate: String = "",
    // Para a etiqueta e a faixa do pôster (idioma original, votos da nota, gênero e país).
    val genreIds: List<Int> = emptyList(),
    val originalLanguage: String = "",
    val voteCount: Int = 0,
    val originCountry: List<String> = emptyList(),
    // Fim da exibição p/ séries (yyyy-MM-dd, só vem do /tv/{id} via toMovieItem).
    // "" no catálogo: as listas do TMDB não devolvem last_air_date nem status.
    val lastAirDate: String = "",
    // Status TMDB da série ("Returning Series" / "Ended" …). "" no catálogo e em filmes.
    val seriesStatus: String = "",
)

/**
 * Intervalo de exibição p/ série ("1998–2008" encerrada, "1998–" em produção,
 * "1998" sem dado de fim). Filme = o ano. "" sem dado nenhum. Nunca inventa data.
 */
internal fun seriesYears(startYear: String, lastAirDate: String, status: String): String {
    val start = startYear.take(4)
    if (start.isBlank()) return ""
    val end = lastAirDate.take(4)
    if (end.isNotBlank()) return if (end == start) start else "$start–$end"
    return if (status == "Returning Series" || status == "In Production") "$start–" else start
}

/** Ano (filme) ou intervalo de exibição (série) p/ mostrar embaixo do pôster. */
fun MovieItem.yearRange(): String =
    if (type == MediaType.SERIES) seriesYears(year, lastAirDate, seriesStatus) else year

enum class MediaType { MOVIE, SERIES }
