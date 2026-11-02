package com.carbon.prolocker.feature.notificationmanager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Helpers around the "notification access" special permission. */
object NotificationAccess {

    fun component(context: Context) = ComponentName(context, NotificationHiderService::class.java)

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system page where the user turns notification access on. */
    fun openSettings(context: Context): Boolean {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component(context).flattenToString()
                        )
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in intents) {
            try {
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /** Asks the system to (re)connect the listener, e.g. after the feature is switched on. */
    fun requestRebind(context: Context) {
        if (!isGranted(context)) return
        try {
            NotificationListenerService.requestRebind(component(context))
        } catch (_: Exception) {
        }
    }
}
