package com.carbon.prolocker.feature.callblocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BlockRuleType {
    EXACT,         // Exact phone number match
    STARTS_WITH,    // Number starts with prefix
    ENDS_WITH,      // Number ends with suffix
    CONTAINS        // Number contains substring
}

enum class BlockSourceCategory {
    MANUAL,
    CONTACT,
    CALL_LOG,
    PATTERN
}

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numberOrPattern: String,
    val displayName: String,
    val ruleType: BlockRuleType = BlockRuleType.EXACT,
    val sourceCategory: BlockSourceCategory = BlockSourceCategory.MANUAL,
    val isEnabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
