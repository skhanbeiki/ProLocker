package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrash(crash: CrashEntity)

    @Query("SELECT * FROM pending_crashes WHERE isSent = 0 ORDER BY createdAt ASC")
    suspend fun getPendingCrashes(): List<CrashEntity>

    @Query("DELETE FROM pending_crashes WHERE id = :id")
    suspend fun deleteCrash(id: Long)

    @Query("UPDATE pending_crashes SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
}
