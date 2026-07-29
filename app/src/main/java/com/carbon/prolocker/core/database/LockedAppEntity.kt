package com.carbon.prolocker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey val packageName: String,
    val lockedState: Boolean = true,
    val lastUnlockTime: Long = 0L
)
