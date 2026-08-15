package com.carbon.prolocker

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.core.database.CrashRepository
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.rate.RateAppManager
import com.carbon.prolocker.core.service.AppMonitorService
import com.carbon.prolocker.di.appModule
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import com.carbon.prolocker.worker.RamCleanerNotificationWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ProLockerApplication : Application(), Application.ActivityLifecycleCallbacks {

    private companion object {
        const val TAG = "Moslemprolocker"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "Unhandled coroutine exception", throwable)
    }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    private val crashRepository: CrashRepository by inject()
    private val adManager: AdManager by inject()
    private val lockedAppsRepository: LockedAppsRepository by inject()
    private val rateAppManager: RateAppManager by inject()
    private val preferencesRepository: PreferencesRepository by inject()

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        startKoin {
            androidLogger()
            androidContext(this@ProLockerApplication)
            modules(appModule, com.carbon.prolocker.network.networkModule)
        }

        setupCrashHandler()
        uploadPendingCrashes()
        RamCleanerNotificationWorker.schedule(this)

        applicationScope.launch {
            adManager.initSdk()
        }

        applicationScope.launch {
            lockedAppsRepository.cleanupSelfPackage()
        }

        detectUpdateAndRecoverService()
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (startedActivityCount == 1) {
            AppEntryLockActivity.markNeedsAuthentication()
            verifyServiceOnForeground()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * Detect if the app was just updated. If so, force-restart the monitoring service
     * to ensure a clean state. This prevents the issue where the service appears to be
     * running after an update but is actually dead.
     */
    private fun detectUpdateAndRecoverService() {
        applicationScope.launch {
            try {
                val currentVersionCode = try {
                    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get current version code", e)
                    return@launch
                }

                val prefs = preferencesRepository.userPreferencesFlow.first()
                val storedVersionCode = prefs.lastInstalledVersionCode

                Log.d(TAG, "Previous version: $storedVersionCode, Current version: $currentVersionCode")

                if (storedVersionCode == 0) {
                    // First install, save version and check if service needs to start
                    preferencesRepository.updatePreferences {
                        it.copy(lastInstalledVersionCode = currentVersionCode)
                    }
                    Log.d(TAG, "First install detected, saved version code: $currentVersionCode")
                    ensureServiceRunningIfNeeded()
                    return@launch
                }

                if (storedVersionCode != currentVersionCode) {
                    Log.d(TAG, "Update detected: $storedVersionCode -> $currentVersionCode")

                    // Save new version code
                    preferencesRepository.updatePreferences {
                        it.copy(lastInstalledVersionCode = currentVersionCode)
                    }

                    // Perform clean restart of monitoring system
                    restartMonitoringService()
                } else {
                    // Same version, just verify service is alive
                    ensureServiceRunningIfNeeded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during update detection", e)
            }
        }
    }

    /**
     * Force-restart the monitoring service after an update.
     * Stops any existing service, clears stale state, and starts fresh.
     */
    private suspend fun restartMonitoringService() {
        try {
            Log.d(TAG, "Stopping AppMonitorService")
            val stopIntent = Intent(this, AppMonitorService::class.java)
            stopService(stopIntent)

            Log.d(TAG, "Clearing service state")
            // Small delay to ensure the service is fully stopped
            kotlinx.coroutines.delay(500)

            Log.d(TAG, "Restarting AppMonitorService")
            startMonitoringServiceIfNeeded()

            Log.d(TAG, "Recovery completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart monitoring service", e)
        }
    }

    /**
     * Verify the service is running and start it if needed.
     * Called on every foreground transition to ensure self-healing.
     */
    private fun verifyServiceOnForeground() {
        applicationScope.launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                if (!prefs.isProtectionEnabled) return@launch

                val lockedApps = lockedAppsRepository.allLockedApps.first()
                if (lockedApps.any { it.lockedState }) {
                    Log.d(TAG, "Foreground check: locked apps exist, verifying service")
                    startMonitoringServiceIfNeeded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during foreground service verification", e)
            }
        }
    }

    /**
     * Ensure the monitoring service is running if there are locked apps and protection is enabled.
     */
    private fun ensureServiceRunningIfNeeded() {
        applicationScope.launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                if (!prefs.isProtectionEnabled) {
                    Log.d(TAG, "Protection is disabled, skipping service start")
                    return@launch
                }

                val lockedApps = lockedAppsRepository.allLockedApps.first()
                if (lockedApps.any { it.lockedState }) {
                    Log.d(TAG, "Locked apps found, starting monitoring service")
                    startMonitoringServiceIfNeeded()
                } else {
                    Log.d(TAG, "No locked apps, skipping service start")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ensuring service is running", e)
            }
        }
    }

    /**
     * Start the AppMonitorService as a foreground service.
     */
    private fun startMonitoringServiceIfNeeded() {
        try {
            val serviceIntent = Intent(this, AppMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "AppMonitorService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AppMonitorService", e)
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                runBlocking {
                    crashRepository.saveCrash(throwable)
                }
            } catch (_e: Exception) {
                // Ignore failure during crash handling
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun uploadPendingCrashes() {
        applicationScope.launch {
            try {
                val pendingCrashes = crashRepository.getPendingCrashes()
                for (crash in pendingCrashes) {
                    crashRepository.uploadCrash(crash)
                }
            } catch (_e: Exception) {
                // Ignore failure during upload
            }
        }
    }
}
