package com.aotv.app.domain.model

data class SeriesItem(
    val tmdbId: Int,
    val embedId: String,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val firstAirYear: String,
    val rating: Double,
    val numberOfSeasons: Int = 1,
    val genres: List<String> = emptyList(),
    val certification: String = "",
    // Data completa da 1ª exibição (yyyy-MM-dd) — p/ mostrar data de lançamento no detalhe.
    val firstAirDate: String = "",
    // Próximo episódio a ir ao ar (séries semanais). "" = nenhum agendado.
    val nextEpisodeDate: String = "",  // yyyy-MM-dd
    val nextEpisodeSE: String = "",    // ex "T2 E5"
    val status: String = "",           // "Returning Series" / "Ended" ...
)
