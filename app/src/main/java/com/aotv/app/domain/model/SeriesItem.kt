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
    // Data completa da ÚLTIMA exibição (yyyy-MM-dd, /tv/{id}). "" = sem dado (o catálogo não traz).
    val lastAirDate: String = "",
    // Próximo episódio a ir ao ar (séries semanais). "" = nenhum agendado.
    val nextEpisodeDate: String = "",  // yyyy-MM-dd
    val nextEpisodeSE: String = "",    // ex "T2 E5"
    val status: String = "",           // "Returning Series" / "Ended" ...
)

/**
 * Intervalo de exibição, estilo Nuvio: "1998–2008" p/ série encerrada,
 * "1998–" p/ série em produção, "1998" sem dado de fim. Nunca inventa:
 * sem lastAirDate e sem status de retorno, só o início.
 */
fun SeriesItem.yearRange(): String = seriesYears(
    firstAirDate.take(4).ifBlank { firstAirYear }, lastAirDate, status,
)
