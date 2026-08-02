package com.carbon.prolocker.feature.callblocker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallBlockerDao {

    // --- Blocked Rules / Numbers ---

    @Query("SELECT * FROM blocked_numbers ORDER BY createdAtMs DESC")
    fun getAllBlockedNumbersFlow(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers ORDER BY createdAtMs DESC")
    suspend fun getAllBlockedNumbersList(): List<BlockedNumberEntity>

    @Query("SELECT * FROM blocked_numbers WHERE isEnabled = 1")
    suspend fun getActiveBlockedNumbersList(): List<BlockedNumberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(entity: BlockedNumberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumbers(entities: List<BlockedNumberEntity>)

    @Update
    suspend fun updateBlockedNumber(entity: BlockedNumberEntity)

    @Delete
    suspend fun deleteBlockedNumber(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE id = :id")
    suspend fun deleteBlockedNumberById(id: Long)

    @Query("DELETE FROM blocked_numbers")
    suspend fun deleteAllBlockedNumbers()

    // --- Blocked Call Logs ---

    @Query("SELECT * FROM blocked_call_logs ORDER BY timestampMs DESC")
    fun getAllBlockedCallLogsFlow(): Flow<List<BlockedCallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedCallLog(entity: BlockedCallLogEntity): Long

    @Query("DELETE FROM blocked_call_logs WHERE id = :id")
    suspend fun deleteBlockedCallLogById(id: Long)

    @Query("DELETE FROM blocked_call_logs")
    suspend fun deleteAllBlockedCallLogs()

    @Query("SELECT COUNT(*) FROM blocked_call_logs")
    fun getBlockedCallCountFlow(): Flow<Int>
}
