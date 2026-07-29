package com.carbon.prolocker.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.carbon.prolocker.core.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionsViewModel : ViewModel() {

    private val _usageState = MutableStateFlow(PermissionState.IDLE)
    val usageState: StateFlow<PermissionState> = _usageState.asStateFlow()

    private val _overlayState = MutableStateFlow(PermissionState.IDLE)
    val overlayState: StateFlow<PermissionState> = _overlayState.asStateFlow()

    private val _batteryState = MutableStateFlow(PermissionState.IDLE)
    val batteryState: StateFlow<PermissionState> = _batteryState.asStateFlow()

    fun checkPermissions(context: Context) {
        _usageState.value = if (PermissionManager.hasUsageAccess(context)) PermissionState.GRANTED else PermissionState.DENIED
        _overlayState.value = if (PermissionManager.hasOverlayPermission(context)) PermissionState.GRANTED else PermissionState.DENIED
        _batteryState.value = if (PermissionManager.isIgnoringBatteryOptimizations(context)) PermissionState.GRANTED else PermissionState.DENIED
    }
}
