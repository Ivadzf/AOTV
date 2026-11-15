package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedEpisodeDao {
    @Query("SELECT * FROM watched_episodes WHERE profileId = :profileId AND seriesTmdbId = :seriesTmdbId ORDER BY season, episode")
    fun getForSeries(profileId: String, seriesTmdbId: Int): Flow<List<WatchedEpisodeEntity>>

    /** Todos os episódios vistos do perfil, de uma vez (p/ o backup). */
    @Query("SELECT * FROM watched_episodes WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: String): List<WatchedEpisodeEntity>

    /** Ids das séries com pelo menos um episódio visto (p/ a prateleira "Ainda não assistidos"). */
    @Query("SELECT DISTINCT seriesTmdbId FROM watched_episodes WHERE profileId = :profileId")
    fun watchedSeriesIds(profileId: String): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_episodes WHERE profileId = :profileId AND seriesTmdbId = :seriesTmdbId AND season = :season AND episode = :episode)")
    suspend fun isWatched(profileId: String, seriesTmdbId: Int, season: Int, episode: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(entity: WatchedEpisodeEntity)

    @Query("DELETE FROM watched_episodes WHERE profileId = :profileId AND seriesTmdbId = :seriesTmdbId AND season = :season AND episode = :episode")
    suspend fun unmark(profileId: String, seriesTmdbId: Int, season: Int, episode: Int)

    @Query("DELETE FROM watched_episodes WHERE profileId = :profileId")
    suspend fun dropProfile(profileId: String)
}
