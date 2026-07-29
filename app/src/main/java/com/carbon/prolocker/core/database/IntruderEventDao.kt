package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderEventDao {
    @Query("SELECT * FROM intruder_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<IntruderEventEntity>>

    @Insert
    suspend fun insertEvent(event: IntruderEventEntity): Long

    @Delete
    suspend fun deleteEvent(event: IntruderEventEntity)

    @Query("DELETE FROM intruder_events")
    suspend fun deleteAllEvents()
}
