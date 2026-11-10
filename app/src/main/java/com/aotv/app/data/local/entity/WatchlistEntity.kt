package com.aotv.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Item da Minha Lista. [profileId] separa a lista de cada perfil ("main" é o principal). */
@Entity(tableName = "watchlist", primaryKeys = ["profileId", "tmdbId", "type"])
data class WatchlistEntity(
    @ColumnInfo(defaultValue = "main") val profileId: String = "main",
    val tmdbId: Int,
    val type: String, // MOVIE | SERIES
    val title: String,
    val posterUrl: String,
    val year: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)
