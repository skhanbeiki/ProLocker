package com.carbon.prolocker.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProtectionManager(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val lockedAppsRepository: LockedAppsRepository,
    private val sessionManager: LockSessionManager
) {

    private val _protectionEnabled = MutableStateFlow(true)
    val protectionEnabled = _protectionEnabled.asStateFlow()

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.IO + scopeExceptionHandler)

    init {
        scope.launch {
            preferencesRepository.userPreferencesFlow.first().let { prefs ->
                _protectionEnabled.value = prefs.isProtectionEnabled
            }
        }
    }

    fun isEnabled(): Boolean = _protectionEnabled.value

    fun enableProtection() {
        scope.launch {
            preferencesRepository.updatePreferences { it.copy(isProtectionEnabled = true) }
            _protectionEnabled.value = true
            startServiceIfLockedAppsExist()
        }
    }

    fun disableProtection() {
        scope.launch {
            preferencesRepository.updatePreferences { it.copy(isProtectionEnabled = false) }
            _protectionEnabled.value = false
            stopMonitoring()
        }
    }

    fun reenableProtectionIfNeeded(): Boolean {
        if (_protectionEnabled.value) return false
        enableProtection()
        return true
    }

    fun startProtectionIfEnabled() {
        scope.launch {
            if (!_protectionEnabled.value) return@launch
            val lockedApps = lockedAppsRepository.allLockedApps.first()
            if (lockedApps.any { it.lockedState }) {
                val serviceIntent = Intent(context, AppMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                stopMonitoring()
            }
        }
    }

    private fun startServiceIfLockedAppsExist() {
        scope.launch {
            val lockedApps = lockedAppsRepository.allLockedApps.first()
            if (lockedApps.any { it.lockedState }) {
                val serviceIntent = Intent(context, AppMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    private fun stopMonitoring() {
        val serviceIntent = Intent(context, AppMonitorService::class.java)
        context.stopService(serviceIntent)
        cancelWatchdog()
        sessionManager.lockAll()
    }

    private fun cancelWatchdog() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
