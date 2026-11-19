package com.aotv.app.domain.model

data class EpisodeItem(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val stillUrl: String,
    val airDate: String = "",      // yyyy-MM-dd
    val runtimeMin: Int = 0,       // duração em minutos (0 = desconhecido)
    val rating: Double = 0.0,      // nota do episódio (resolvida — ver ratingSource)
    // Origem da nota resolvida (Lote 24 item 12, v1.34.0): "Trakt" ou
    // "média de 3 fontes". null = só o TMDB (vote_average, o de hoje).
    val ratingSource: String? = null,
)
