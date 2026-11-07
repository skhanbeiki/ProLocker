package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrash(crash: CrashEntity)

    // Bounded: each row carries a crash report body, and an unbounded read could overflow the
    // 2 MB CursorWindow (CursorWindowAllocationException) when many reports piled up offline.
    @Query("SELECT * FROM pending_crashes WHERE isSent = 0 ORDER BY createdAt ASC LIMIT 20")
    suspend fun getPendingCrashes(): List<CrashEntity>

    /** Keeps only the newest reports so the table cannot grow without limit while offline. */
    @Query("DELETE FROM pending_crashes WHERE id NOT IN (SELECT id FROM pending_crashes ORDER BY createdAt DESC LIMIT 50)")
    suspend fun trimOldCrashes()

    @Query("DELETE FROM pending_crashes WHERE id = :id")
    suspend fun deleteCrash(id: Long)

    @Query("UPDATE pending_crashes SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
}
