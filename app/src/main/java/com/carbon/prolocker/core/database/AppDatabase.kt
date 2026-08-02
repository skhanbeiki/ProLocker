package com.carbon.prolocker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.carbon.prolocker.feature.callblocker.data.BlockedCallLogEntity
import com.carbon.prolocker.feature.callblocker.data.BlockedNumberEntity
import com.carbon.prolocker.feature.callblocker.data.CallBlockerDao

@Database(
    entities = [
        LockedAppEntity::class,
        IntruderEventEntity::class,
        SecurityEventEntity::class,
        CrashEntity::class,
        BlockedNumberEntity::class,
        BlockedCallLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun intruderEventDao(): IntruderEventDao
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun crashDao(): CrashDao
    abstract fun callBlockerDao(): CallBlockerDao
}
