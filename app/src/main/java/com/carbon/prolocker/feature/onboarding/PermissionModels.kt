package com.carbon.prolocker.feature.onboarding

import androidx.compose.ui.graphics.vector.ImageVector

enum class PermissionType {
    USAGE_ACCESS,
    OVERLAY,
    BATTERY
}

enum class PermissionState {
    IDLE, GRANTED, DENIED, CHECKING
}

data class PermissionSlideModel(
    val type: PermissionType,
    val title: String,
    val description: String,
    val reason: String,
    val icon: ImageVector,
    val isRequired: Boolean
)
