package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.MovieHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Linha de histórico p/ as estatísticas (Lote 23, v1.33.0): só o que a agregação precisa. */
data class HistoryStatsRow(
    val lastWatchedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)

@Dao
interface MovieHistoryDao {
    // Lote 23 (v1.33.0, JOTV 1.0.22 §4.2): o histórico passou de 30 p/
    // 250 títulos — alimenta o Continuar assistindo, a Minha Lista e as
    // estatísticas. Filme terminado (≥90%) sai do Continuar (ver
    // resolveHistoryItem) e continua nos salvos com o selo Assistido.
    @Query("SELECT * FROM movie_history WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT 250")
    fun getAll(profileId: String): Flow<List<MovieHistoryEntity>>

    /** Linhas p/ as estatísticas do Lote 23 (agregação em StatsRecap). */
    @Query("SELECT lastWatchedAt, positionMs, durationMs FROM movie_history WHERE profileId = :profileId")
    suspend fun statsRows(profileId: String): List<HistoryStatsRow>

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
