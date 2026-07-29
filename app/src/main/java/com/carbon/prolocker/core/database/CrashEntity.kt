package com.carbon.prolocker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_crashes")
data class CrashEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val issue: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val isSent: Boolean = false
)
