package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    /** Todos os downloads de uma vez (p/ achar restos de arquivos — Lote 18 item 4). */
    @Query("SELECT * FROM downloads")
    suspend fun getAllOnce(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'downloading'")
    suspend fun getDownloading(): List<DownloadEntity>

    /** Na fila aguardando Wi-Fi ("Só Wi-Fi" ligado, Lote 5, v1.14.0). */
    @Query("SELECT * FROM downloads WHERE status = 'queued' ORDER BY createdAt ASC")
    suspend fun getQueued(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, localUri = :localUri WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, localUri: String)

    /** Troca o id do DownloadManager e o status (tirar da fila "queued" p/ "downloading"). */
    @Query("UPDATE downloads SET downloadId = :downloadId, status = :status WHERE id = :id")
    suspend fun updateDownload(id: String, downloadId: Long, status: String)

    @Delete
    suspend fun delete(entity: DownloadEntity)
}
