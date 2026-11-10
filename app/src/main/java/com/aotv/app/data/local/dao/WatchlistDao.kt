package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getAll(profileId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE profileId = :profileId AND tmdbId = :tmdbId AND type = :type)")
    fun isSaved(profileId: String, tmdbId: Int, type: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND tmdbId = :tmdbId AND type = :type")
    suspend fun remove(profileId: String, tmdbId: Int, type: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId")
    suspend fun dropProfile(profileId: String)
}
