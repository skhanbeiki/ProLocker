package com.carbon.prolocker.feature.callblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_call_logs")
data class BlockedCallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val callerName: String? = null,
    val matchedRule: String,
    val timestampMs: Long = System.currentTimeMillis()
)
