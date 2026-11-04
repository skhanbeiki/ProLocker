package com.carbon.prolocker.feature.privacyauditor

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes

enum class RiskLevel {
    HIGH,
    MEDIUM,
    LOW
}

enum class PermissionCategory {
    CAMERA,
    MICROPHONE,
    LOCATION,
    SMS,
    CONTACTS,
    STORAGE,
    CALL_LOG,
    OTHER
}

data class PermissionDetail(
    val permissionName: String,
    @StringRes val titleRes: Int,
    val category: PermissionCategory,
    val isDangerous: Boolean
)

data class AppPermissionInfo(
    val packageName: String,
    val appName: String,
    val iconDrawable: Drawable?,
    val riskLevel: RiskLevel,
    val grantedPermissions: List<PermissionDetail>,
    val isSystemApp: Boolean
)

data class PrivacySummary(
    val totalApps: Int,
    val highRiskApps: Int,
    val mediumRiskApps: Int,
    val safeApps: Int,
    val healthScore: Int
)

enum class RiskFilter {
    ALL,
    HIGH_RISK,
    CAMERA_MIC,
    LOCATION,
    SMS_CONTACTS
}
