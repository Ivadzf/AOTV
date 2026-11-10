package com.aotv.app.data.local.dao

import androidx.room.*
import com.aotv.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'downloading'")
    suspend fun getDownloading(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, localUri = :localUri WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, localUri: String)

    @Delete
    suspend fun delete(entity: DownloadEntity)
}
