package com.aotv.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Episódio visto, por perfil. */
@Entity(tableName = "watched_episodes", primaryKeys = ["profileId", "seriesTmdbId", "season", "episode"])
data class WatchedEpisodeEntity(
    @ColumnInfo(defaultValue = "main") val profileId: String = "main",
    val seriesTmdbId: Int,
    val season: Int,
    val episode: Int,
    val watchedAt: Long = System.currentTimeMillis(),
)
