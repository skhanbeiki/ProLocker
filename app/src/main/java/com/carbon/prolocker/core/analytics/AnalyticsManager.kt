package com.carbon.prolocker.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsManager(context: Context) {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun trackAppOpen() {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
    }

    fun trackScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun trackToolClick(toolName: String) {
        val bundle = Bundle().apply {
            putString("tool_name", toolName)
        }
        firebaseAnalytics.logEvent("tool_click", bundle)
    }

    fun trackHideFilesAction(action: String, category: String, count: Int) {
        val bundle = Bundle().apply {
            putString("action", action)
            putString("category", category)
            putInt("count", count)
        }
        firebaseAnalytics.logEvent("hide_files_action", bundle)
    }

    fun trackBackupAction(category: String, count: Int) {
        val bundle = Bundle().apply {
            putString("category", category)
            putInt("count", count)
        }
        firebaseAnalytics.logEvent("backup_action", bundle)
    }

    fun trackMemoryOptimized(freedMb: Int) {
        val bundle = Bundle().apply {
            putInt("freed_mb", freedMb)
        }
        firebaseAnalytics.logEvent("memory_optimized", bundle)
    }

    fun trackCallBlockerAction(action: String) {
        val bundle = Bundle().apply {
            putString("action", action)
        }
        firebaseAnalytics.logEvent("call_blocker_action", bundle)
    }

    fun trackPrivacyAuditAction() {
        firebaseAnalytics.logEvent("privacy_audit_performed", null)
    }

    fun trackBackgroundSelected(backgroundId: Int) {
        val bundle = Bundle().apply {
            putInt("background_id", backgroundId)
        }
        firebaseAnalytics.logEvent("background_selected", bundle)
    }

    fun trackAppLocked(packageName: String) {
        val bundle = Bundle().apply {
            putString("package_name", packageName)
        }
        firebaseAnalytics.logEvent("app_locked", bundle)
    }

    fun trackAppUnlocked(packageName: String) {
        val bundle = Bundle().apply {
            putString("package_name", packageName)
        }
        firebaseAnalytics.logEvent("app_unlocked", bundle)
    }

    fun trackNotificationOpened(type: String) {
        val bundle = Bundle().apply {
            putString("notification_type", type)
        }
        firebaseAnalytics.logEvent("notification_opened", bundle)
    }

    fun trackNotificationReceived() {
        firebaseAnalytics.logEvent("notification_received", null)
    }
}
