package com.aotv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Um download de filme/episódio (.mp4 direto via DownloadManager do sistema). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,         // "tmdbId:type:lastEpisode"
    val tmdbId: Int,
    val type: String,                   // MOVIE | SERIES
    val title: String,
    val posterUrl: String,
    val sourceUrl: String,
    val localUri: String = "",          // file:// quando concluído
    val lastEpisode: String = "",       // "T1:E3" p/ série
    val downloadId: Long = 0,           // id do DownloadManager
    val status: String = "downloading", // downloading | done | failed
    val createdAt: Long = 0,
)
