package com.carbon.prolocker.core.security

import android.content.Context
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.permissions.PermissionManager
import com.carbon.prolocker.core.permissions.PermissionManager.isAccessibilityServiceEnabled
import com.carbon.prolocker.core.service.AppMonitorAccessibilityService
import kotlinx.coroutines.flow.first

class SecurityScoreManager(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val deviceAdminManager: DeviceAdminManager
) {
    suspend fun calculateScore(): Int {
        var score = 0
        val prefs = preferencesRepository.userPreferencesFlow.first()
        
        // Base points
        if (PermissionManager.hasUsageAccess(context)) score += 20
        if (PermissionManager.hasOverlayPermission(context)) score += 20
        if (PermissionManager.isIgnoringBatteryOptimizations(context)) score += 10
        
        // Optional security features
        if (isAccessibilityServiceEnabled(context, AppMonitorAccessibilityService::class.java)) score += 10
        if (deviceAdminManager.isAdminActive()) score += 15
        
        // Intruder & Recovery
        if (prefs.captureIntruderSelfie) score += 15
        if (prefs.securityQuestionHash.isNotEmpty()) score += 10

        return Math.min(100, score)
    }
}
