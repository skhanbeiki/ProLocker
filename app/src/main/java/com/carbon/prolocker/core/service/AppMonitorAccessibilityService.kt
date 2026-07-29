package com.carbon.prolocker.core.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject

class AppMonitorAccessibilityService : AccessibilityService() {

    private val lockedAppsRepository: LockedAppsRepository by inject()
    private val sessionManager: LockSessionManager by inject()
    private val preferencesRepository: PreferencesRepository by inject()
    private val hybridDetectionEngine: HybridDetectionEngine by inject()
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var lastForegroundPackage: String? = null
    private var lastEventTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        val prefs = try {
            kotlinx.coroutines.runBlocking {
                preferencesRepository.userPreferencesFlow.first()
            }
        } catch (_: Exception) {
            return
        }
        if (!prefs.isProtectionEnabled) return

        // Ignore common system packages and keyboards to prevent false backgrounding
        if (packageName == "com.android.systemui" || 
            packageName == "com.samsung.android.honeyboard" ||
            packageName.contains("inputmethod", ignoreCase = true) || 
            packageName.contains("keyboard", ignoreCase = true)) {
            return
        }

        val currentTime = System.currentTimeMillis()

        // Ignore internal navigation (same package)
        if (packageName == lastForegroundPackage) return

        // Debounce protection (500ms)
        if (currentTime - lastEventTime < 500) return

        lastEventTime = currentTime
        lastForegroundPackage = packageName

        hybridDetectionEngine.updateFromAccessibility(packageName)
    }

    override fun onInterrupt() {
    }
}
