package com.carbon.prolocker.core.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HybridDetectionEngine(
    private val foregroundAppDetector: ForegroundAppDetector,
    private val context: Context
) {
    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp = _currentForegroundApp.asStateFlow()

    private var lastAccessibilityUpdate: Long = 0
    var isLockVerificationActive: Boolean = false

    fun updateFromAccessibility(packageName: String) {
        if (isLockVerificationActive && packageName == "com.carbon.prolocker") return
        lastAccessibilityUpdate = System.currentTimeMillis()
        _currentForegroundApp.value = packageName
    }

    fun pollUsageStats(): String? {
        // If we received an accessibility event within the last 30 seconds,
        // rely on that directly as it is much faster and accurate.
        if (System.currentTimeMillis() - lastAccessibilityUpdate < 30000) {
            return _currentForegroundApp.value
        }

        val app = foregroundAppDetector.getForegroundApp()
        if (isLockVerificationActive && app == "com.carbon.prolocker") {
            return _currentForegroundApp.value
        }
        
        if (app != null) {
            _currentForegroundApp.value = app
        }
        return app
    }
}
