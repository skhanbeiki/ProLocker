package com.carbon.prolocker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // LOCK_TRIGGERED, UNLOCK_SUCCESS, UNLOCK_FAILED, INTRUDER_DETECTED, ALARM_TRIGGERED, RECOVERY_USED, PERMISSION_REVOKED
    val packageName: String? = null, // The app involved, if applicable
    val details: String? = null // Additional info (e.g., unlock method)
)
