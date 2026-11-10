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
)

enum class MediaType { MOVIE, SERIES }
