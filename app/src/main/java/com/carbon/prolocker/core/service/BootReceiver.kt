package com.carbon.prolocker.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class BootReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "Moslemprolocker"
    }

    private val lockedAppsRepository: LockedAppsRepository by inject(LockedAppsRepository::class.java)
    private val preferencesRepository: PreferencesRepository by inject(PreferencesRepository::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if service should start")
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Error in BootReceiver", throwable)
            }
            CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
                try {
                    val prefs = preferencesRepository.userPreferencesFlow.first()
                    if (!prefs.autoStartEnabled) {
                        Log.d(TAG, "Auto-start disabled, skipping")
                        return@launch
                    }
                    if (!prefs.isProtectionEnabled) {
                        Log.d(TAG, "Protection disabled, skipping")
                        return@launch
                    }

                    val lockedApps = lockedAppsRepository.allLockedApps.first()
                    if (lockedApps.any { it.lockedState }) {
                        Log.d(TAG, "Locked apps found, starting AppMonitorService")
                        val serviceIntent = Intent(context, AppMonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        Log.d(TAG, "AppMonitorService start requested from boot")
                    } else {
                        Log.d(TAG, "No locked apps, skipping service start")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service from boot", e)
                }
            }
        }
    }
}
