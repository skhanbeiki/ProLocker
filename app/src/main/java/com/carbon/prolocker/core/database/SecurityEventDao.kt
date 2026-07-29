package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {
    @Insert
    suspend fun insert(event: SecurityEventEntity)

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT 1000")
    fun getRecentEvents(): Flow<List<SecurityEventEntity>>

    @Query("DELETE FROM security_events WHERE id NOT IN (SELECT id FROM security_events ORDER BY timestamp DESC LIMIT 1000)")
    suspend fun trimOldEvents()
    
    @Query("SELECT COUNT(*) FROM security_events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM security_events")
    suspend fun deleteAllEvents()
}
