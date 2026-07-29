package com.carbon.prolocker.core.permissions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.EventLogManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.util.Locale

class PermissionHealthMonitor(
    private val context: Context,
    private val eventLogManager: EventLogManager
) {
    private companion object {
        const val TAG = "Moslemprolocker"
    }

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.IO + scopeExceptionHandler)
    private var isMonitoring = false
    private val preferencesRepository: PreferencesRepository by inject(PreferencesRepository::class.java)


    // Track previously active permissions
    private var wasUsageAccessGranted = true
    private var wasOverlayGranted = true
    private var wasCameraGranted = true

    private fun getLocalizedContext(): Context {
        val languageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "PermissionHealthMonitor: failed to read language preference", e)
            ""
        }
        val appLocales = LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()
        val config = Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localizedContext = context.createConfigurationContext(config)

        Log.d(TAG, "NOTIFICATION_LANGUAGE PermissionHealthMonitor: languageTag=$languageTag, locale=$locale")

        return localizedContext
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        wasUsageAccessGranted = PermissionManager.hasUsageAccess(context)
        wasOverlayGranted = PermissionManager.hasOverlayPermission(context)
        wasCameraGranted = PermissionManager.hasCameraPermission(context)

        scope.launch {
            while (isActive && isMonitoring) {
                delay(60_000) // Check every minute
                checkPermissions()
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    private fun checkPermissions() {
        val hasUsageAccess = PermissionManager.hasUsageAccess(context)
        val hasOverlay = PermissionManager.hasOverlayPermission(context)
        val hasCamera = PermissionManager.hasCameraPermission(context)

        if (wasUsageAccessGranted && !hasUsageAccess) {
            val localizedContext = getLocalizedContext()
            logAndNotify(localizedContext.getString(R.string.usage_access_revoked), localizedContext.getString(R.string.usage_access_revoked_desc))
        }
        if (wasOverlayGranted && !hasOverlay) {
            val localizedContext = getLocalizedContext()
            logAndNotify(localizedContext.getString(R.string.overlay_revoked), localizedContext.getString(R.string.overlay_revoked_desc))
        }
        if (wasCameraGranted && !hasCamera) {
            val localizedContext = getLocalizedContext()
            logAndNotify(localizedContext.getString(R.string.camera_revoked), localizedContext.getString(R.string.camera_revoked_desc))
        }

        wasUsageAccessGranted = hasUsageAccess
        wasOverlayGranted = hasOverlay
        wasCameraGranted = hasCamera
    }

    private fun logAndNotify(title: String, message: String) {
        eventLogManager.logEvent("PERMISSION_REVOKED", details = title)

        val localizedContext = getLocalizedContext()
        val channelId = "prolocker_alert_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                localizedContext.getString(R.string.security_alerts_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val appLanguageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (_e: Exception) {
            Locale.getDefault().language
        }

        Log.d(TAG, "NOTIFICATION_LANGUAGE PermissionHealthMonitor: appLanguage=$appLanguageTag, currentLocale=${Locale.getDefault()}, notificationTitle=$title, notificationMessage=$message, contextLocale=${localizedContext.resources.configuration.locales[0]}")

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(localizedContext.getString(R.string.prolocker_security_alert))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedContext.getString(R.string.revoked_message_format, title, message)))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }
}
