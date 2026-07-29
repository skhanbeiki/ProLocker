package com.carbon.prolocker.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.feature.lock.LockService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import java.util.Locale

class AppMonitorService : Service() {
    companion object {
        private const val TAG = "Moslemprolocker"
        const val ACTION_CAPTURE_INTRUDER = "com.carbon.prolocker.action.CAPTURE_INTRUDER"
        const val EXTRA_INTRUDER_PACKAGE = "extra_intruder_package"
        const val EXTRA_INTRUDER_LOCK_TYPE = "extra_intruder_lock_type"
        const val EXTRA_INTRUDER_PHOTO_PATH = "extra_intruder_photo_path"

        fun requestIntruderCapture(
            context: Context,
            packageName: String,
            lockType: String,
            photoPath: String
        ) {
            Log.d(TAG, "INTRUDER_CAPTURE_SENT_TO_SERVICE pkg=$packageName lockType=$lockType path=$photoPath")
            val intent = Intent(context, AppMonitorService::class.java).apply {
                action = ACTION_CAPTURE_INTRUDER
                putExtra(EXTRA_INTRUDER_PACKAGE, packageName)
                putExtra(EXTRA_INTRUDER_LOCK_TYPE, lockType)
                putExtra(EXTRA_INTRUDER_PHOTO_PATH, photoPath)
            }
            context.startService(intent)
        }
    }

    private val hybridDetectionEngine: HybridDetectionEngine by inject()
    private val sessionManager: LockSessionManager by inject()
    private val lockedAppsRepository: LockedAppsRepository by inject()
    private val preferencesRepository: PreferencesRepository by inject()
    private val permissionHealthMonitor: com.carbon.prolocker.core.permissions.PermissionHealthMonitor by inject()
    private val eventLogManager: com.carbon.prolocker.core.security.EventLogManager by inject()
    private val foregroundAppDetector: ForegroundAppDetector by inject()
    private val cameraCaptureManager: com.carbon.prolocker.core.security.CameraCaptureManager by inject()
    private val intruderEventDao: com.carbon.prolocker.core.database.IntruderEventDao by inject()
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception in AppMonitorService", throwable)
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + serviceExceptionHandler)
    private var isMonitoring = false
    private var lastForegroundApp: String? = null
    private var currentlyLockingApp: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                try {
                    runBlocking {
                        val prefs = org.koin.java.KoinJavaComponent.inject<PreferencesRepository>(
                            PreferencesRepository::class.java
                        ).value.userPreferencesFlow.first()
                        if (prefs.relockOnScreenOff) {
                            sessionManager.lockAll()
                            currentlyLockingApp = null
                            LockService.dismiss(this@AppMonitorService)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun getLocalizedContext(): Context {
        val languageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLocalizedContext: failed to read language preference", e)
            ""
        }
        val appLocales = LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()
        val config = Configuration(resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localizedContext = createConfigurationContext(config)

        Log.d(TAG, "NOTIFICATION_LANGUAGE AppMonitorService: languageTag=$languageTag, locale=$locale")

        return localizedContext
    }

    override fun onCreate() {
        super.onCreate()
        val localizedContext = getLocalizedContext()
        val channelId = "prolocker_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                localizedContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedContext.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val fgServiceType = if (hasCameraPermission) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        androidx.core.app.ServiceCompat.startForeground(
            this,
            1001,
            createNotification(),
            fgServiceType
        )
        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        if (!prefs.isProtectionEnabled) {
            stopSelf()
            return
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
        ServiceWatchdog.startWatchdog(this)
        permissionHealthMonitor.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CAPTURE_INTRUDER) {
            val packageName = intent.getStringExtra(EXTRA_INTRUDER_PACKAGE) ?: return START_NOT_STICKY
            val lockType = intent.getStringExtra(EXTRA_INTRUDER_LOCK_TYPE) ?: return START_NOT_STICKY
            val photoPath = intent.getStringExtra(EXTRA_INTRUDER_PHOTO_PATH) ?: return START_NOT_STICKY
            Log.d(TAG, "INTRUDER_CAPTURE_RECEIVED_IN_SERVICE pkg=$packageName lockType=$lockType path=$photoPath")
            handleIntruderCapture(packageName, lockType, photoPath)
            return START_NOT_STICKY
        }

        if (!isMonitoring) {
            isMonitoring = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive && isMonitoring) {
                hybridDetectionEngine.pollUsageStats()
                val currentApp = hybridDetectionEngine.currentForegroundApp.value

                // پکیج خودمان را کلاً از چرخه‌ی قفل خارج می‌کنیم.
                // قفل ProLocker فقط از طریق MainActivity چک می‌شود.
                if (currentApp == packageName) {
                    delay(250L)
                    continue
                }

                if (currentApp != null && currentApp != lastForegroundApp) {
                    if (lastForegroundApp != null) {
                        sessionManager.markAppBackgrounded(lastForegroundApp!!)
                    }
                    lastForegroundApp = currentApp
                    currentlyLockingApp = null
                }

                if (currentApp != null) {
                    val lockedApps = lockedAppsRepository.allLockedApps.first()
                    val isLocked = lockedApps.any { it.packageName == currentApp && it.lockedState }
                    if (isLocked && !sessionManager.isAppUnlocked(currentApp)) {
                        // Relaunch if: different package OR lock screen is no longer visible
                        if (currentlyLockingApp != currentApp || !LockService.isShowing) {
                            launchLockScreen(currentApp)
                            currentlyLockingApp = currentApp
                            delay(500L)
                        }
                    } else if (!isLocked || sessionManager.isAppUnlocked(currentApp)) {
                        if (currentlyLockingApp == currentApp) {
                            currentlyLockingApp = null
                        }
                    }
                }

                delay(if (currentApp == null) 500L else 250L)
            }
        }
    }

    private fun launchLockScreen(packageName: String) {
        eventLogManager.logEvent("LOCK_TRIGGERED", packageName = packageName)
        foregroundAppDetector.lockServiceLaunchedAt = System.currentTimeMillis()
        LockService.start(this, packageName)
    }

    private fun handleIntruderCapture(packageName: String, lockType: String, photoPath: String) {
        serviceScope.launch {
            try {
                val photoFile = java.io.File(photoPath)
                Log.d(TAG, "INTRUDER_CAPTURE_START pkg=$packageName file=${photoFile.name}")

                val success = cameraCaptureManager.captureSelfie(photoFile)
                Log.d(TAG, "INTRUDER_CAPTURE_RESULT success=$success file.exists()=${photoFile.exists()} file.length()=${photoFile.length()}")

                if (success && photoFile.exists() && photoFile.length() > 0) {
                    val entity = com.carbon.prolocker.core.database.IntruderEventEntity(
                        photoPath = photoFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        targetAppPackage = packageName,
                        lockType = lockType
                    )
                    intruderEventDao.insertEvent(entity)
                    Log.d(TAG, "INTRUDER_DB_RECORD_CREATED id=${entity.id} pkg=$packageName")
                } else {
                    Log.e(TAG, "INTRUDER_CAPTURE_FAILED or file empty — no DB record created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "INTRUDER_CAPTURE_EXCEPTION pkg=$packageName", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val localizedContext = getLocalizedContext()
        val channelId = "prolocker_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                localizedContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedContext.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val stealthEnabled = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().isStealthModeEnabled
            }
        } catch (_: Exception) {
            false
        }
        val targetComponent = if (stealthEnabled) {
            android.content.ComponentName(
                packageName,
                StealthModeManager.RECOVERY_ALIAS_CLASS_NAME
            )
        } else {
            android.content.ComponentName(
                packageName,
                StealthModeManager.APP_ALIAS_CLASS_NAME
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            2001,
            Intent().setComponent(targetComponent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appLanguageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (_: Exception) {
            Locale.getDefault().language
        }

        Log.d(TAG, "NOTIFICATION_LANGUAGE AppMonitorService.createNotification: appLanguage=$appLanguageTag, currentLocale=${Locale.getDefault()}, contextLocale=${localizedContext.resources.configuration.locales[0]}")

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(localizedContext.getString(R.string.prolocker_is_active))
            .setContentText(localizedContext.getString(R.string.protecting_your_apps))
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        serviceScope.cancel()
        unregisterReceiver(screenReceiver)
        permissionHealthMonitor.stopMonitoring()
    }
}
