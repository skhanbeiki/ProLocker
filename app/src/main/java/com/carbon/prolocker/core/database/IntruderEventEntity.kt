package com.carbon.prolocker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_events")
data class IntruderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val timestamp: Long,
    val targetAppPackage: String,
    val lockType: String
)
