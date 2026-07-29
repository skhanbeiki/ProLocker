package com.carbon.prolocker.core.service

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class WatchdogReceiver : BroadcastReceiver() {

    private val lockedAppsRepository: LockedAppsRepository by inject(LockedAppsRepository::class.java)
    private val preferencesRepository: PreferencesRepository by inject(PreferencesRepository::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                if (!prefs.autoStartEnabled) return@launch
                if (!prefs.isProtectionEnabled) return@launch

                val lockedApps = lockedAppsRepository.allLockedApps.first()
                if (lockedApps.none { it.lockedState }) return@launch

                if (!isServiceRunning(context, AppMonitorService::class.java)) {
                    val serviceIntent = Intent(context, AppMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
