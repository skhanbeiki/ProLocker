package com.carbon.prolocker.feature.backup.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

enum class BackupCategory {
    CONTACTS,
    CALL_LOGS,
    APPLICATIONS,
    SMS
}

data class BackupItemCount(
    val category: BackupCategory,
    val count: Int
)

data class BackupFileInfo(
    val fileName: String,
    val category: BackupCategory,
    val filePath: String,
    val uriString: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

@Serializable
data class ContactBackupData(
    val displayName: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList()
)

@Serializable
data class CallLogBackupData(
    val number: String,
    val name: String? = null,
    val type: Int,
    val dateMs: Long,
    val durationSec: Long
)

@Serializable
data class SmsBackupData(
    val address: String,
    val body: String,
    val dateMs: Long,
    val type: Int,
    val read: Int
)

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val iconDrawable: Drawable? = null,
    val version: String = "",
    val apkSizeBytes: Long = 0L,
    val isSelected: Boolean = false
)

enum class AppBackupStatus {
    WAITING,
    BACKING_UP,
    COMPLETED,
    FAILED
}

data class AppBackupProgressItem(
    val appName: String,
    val packageName: String,
    val status: AppBackupStatus = AppBackupStatus.WAITING,
    val iconDrawable: Drawable? = null
)
