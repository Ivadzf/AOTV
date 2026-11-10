package com.aotv.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Continuar assistindo de cada perfil. */
@Entity(tableName = "movie_history", primaryKeys = ["profileId", "tmdbId", "type"])
data class MovieHistoryEntity(
    @ColumnInfo(defaultValue = "main") val profileId: String = "main",
    val tmdbId: Int,
    val type: String, // MOVIE | SERIES
    val title: String,
    val posterUrl: String,
    val lastWatchedAt: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastEpisode: String = "", // ex "T1:E3" p/ serie
    val lastSourceUrl: String = "", // última fonte usada (p/ retomar na mesma)
    val lastAddon: String = "", // addon da última fonte (p/ próximo episódio herdar sem perguntar)
)
