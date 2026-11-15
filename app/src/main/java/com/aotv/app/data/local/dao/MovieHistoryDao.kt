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

    /**
     * Tipos armazenados de um título (v1.21.0): o card do Continuar monta o
     * tipo a partir do MovieItem (só MOVIE/SERIES), mas a linha pode ser
     * "LIVE" (canal ao vivo) — remover pelo tipo do card não apagava nada.
     * Quem remove resolve o tipo aqui, na linha, em vez de confiar no card.
     */
    @Query("SELECT type FROM movie_history WHERE profileId = :profileId AND tmdbId = :tmdbId")
    suspend fun typesFor(profileId: String, tmdbId: Int): List<String>

    /** Apaga TODAS as linhas do título, qualquer que seja o tipo. */
    @Query("DELETE FROM movie_history WHERE profileId = :profileId AND tmdbId = :tmdbId")
    suspend fun deleteAllTypes(profileId: String, tmdbId: Int)

    @Query("DELETE FROM movie_history WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}
