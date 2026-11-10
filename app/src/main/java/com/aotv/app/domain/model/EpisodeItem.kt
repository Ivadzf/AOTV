package com.aotv.app.domain.model

data class EpisodeItem(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val stillUrl: String,
    val airDate: String = "",      // yyyy-MM-dd
    val runtimeMin: Int = 0,       // duração em minutos (0 = desconhecido)
    val rating: Double = 0.0,      // nota TMDB do episódio
)
