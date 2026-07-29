package com.carbon.prolocker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps")
    fun getAllLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    suspend fun getLockedApp(packageName: String): LockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockedApp(lockedApp: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun deleteLockedApp(packageName: String)
    
    @Query("UPDATE locked_apps SET lockedState = :isLocked WHERE packageName = :packageName")
    suspend fun updateLockState(packageName: String, isLocked: Boolean)

    @Query("UPDATE locked_apps SET lastUnlockTime = :time WHERE packageName = :packageName")
    suspend fun updateUnlockTime(packageName: String, time: Long)
}
