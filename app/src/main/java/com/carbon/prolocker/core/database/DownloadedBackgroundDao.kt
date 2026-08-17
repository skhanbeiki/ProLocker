package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedBackgroundDao {
    @Query("SELECT * FROM downloaded_backgrounds ORDER BY downloadedAt DESC")
    fun getAllFlow(): Flow<List<DownloadedBackgroundEntity>>

    @Query("SELECT * FROM downloaded_backgrounds ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadedBackgroundEntity>

    @Query("SELECT * FROM downloaded_backgrounds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): DownloadedBackgroundEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_backgrounds WHERE id = :id)")
    fun isDownloadedFlow(id: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_backgrounds WHERE id = :id)")
    suspend fun isDownloaded(id: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(background: DownloadedBackgroundEntity)

    @Query("DELETE FROM downloaded_backgrounds WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM downloaded_backgrounds")
    fun getCountFlow(): Flow<Int>
}
