package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.MovieHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieHistoryDao {
    @Query("SELECT * FROM movie_history WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT 30")
    fun getAll(profileId: String): Flow<List<MovieHistoryEntity>>

    @Query("SELECT * FROM movie_history WHERE profileId = :profileId AND tmdbId = :tmdbId AND type = :type LIMIT 1")
    suspend fun get(profileId: String, tmdbId: Int, type: String): MovieHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MovieHistoryEntity)

    @Query("DELETE FROM movie_history WHERE profileId = :profileId AND tmdbId = :tmdbId AND type = :type")
    suspend fun delete(profileId: String, tmdbId: Int, type: String)

    @Query("DELETE FROM movie_history WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}
