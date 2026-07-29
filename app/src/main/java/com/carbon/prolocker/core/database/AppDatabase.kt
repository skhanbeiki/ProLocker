package com.carbon.prolocker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LockedAppEntity::class, IntruderEventEntity::class, SecurityEventEntity::class, CrashEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun intruderEventDao(): IntruderEventDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun crashDao(): CrashDao
}
