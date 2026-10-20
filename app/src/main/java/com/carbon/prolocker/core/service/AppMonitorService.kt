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
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.R
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.feature.lock.LockService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import java.util.Locale

class AppMonitorService : Service() {
    companion object {
        private const val TAG = "Moslemprolocker"
        const val ACTION_CAPTURE_INTRUDER = "com.carbon.prolocker.action.CAPTURE_INTRUDER"
        const val EXTRA_INTRUDER_PACKAGE = "extra_intruder_package"
        const val EXTRA_INTRUDER_LOCK_TYPE = "extra_intruder_lock_type"
        const val EXTRA_INTRUDER_PHOTO_PATH = "extra_intruder_photo_path"

        private const val PERF_TAG = "LockPerf"
        private const val POLL_ACTIVE_MS = 150L
        private const val POLL_IDLE_MS = 500L
        private const val SCREEN_WAKE_CHECK_MS = 60_000L
        private const val TRANSIENT_MAX_MS = 5 * 60 * 1000L

        // System UI that briefly covers the app the user is in (permission dialogs, share sheet,
        // file picker...). Coming back from these must not count as leaving the app.
        private val TRANSIENT_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.providers.media.module",
            "com.android.providers.media.module",
            "com.android.providers.media",
            "com.google.android.gms"
        )

        fun requestIntruderCapture(
            context: Context,
            packageName: String,
            lockType: String,
            photoPath: String
        ) {
            try {
                val intent = Intent(context, AppMonitorService::class.java).apply {
                    action = ACTION_CAPTURE_INTRUDER
                    putExtra(EXTRA_INTRUDER_PACKAGE, packageName)
                    putExtra(EXTRA_INTRUDER_LOCK_TYPE, lockType)
                    putExtra(EXTRA_INTRUDER_PHOTO_PATH, photoPath)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startService with capture intent", e)
            }
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
    private var monitorJob: Job? = null
    private var lockedAppsJob: Job? = null

    // Packages with lockedState == true, kept in sync with the database by a single collector.
    @Volatile
    private var lockedPackages: Set<String> = emptySet()
    private val lockedAppsReady = CompletableDeferred<Unit>()

    @Volatile
    private var lastForegroundApp: String? = null

    @Volatile
    private var currentlyLockingApp: String? = null

    // Start of the current stretch on a transient system screen, 0 when not on one.
    @Volatile
    private var transientSince: Long = 0L

    @Volatile
    private var isScreenReceiverRegistered = false
    private val screenInteractive = MutableStateFlow(true)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    try {
                        if (preferencesRepository.currentPreferences.relockOnScreenOff) {
                            sessionManager.lockAll()
                            currentlyLockingApp = null
                            LockService.dismiss(this@AppMonitorService)
                        }
                    } catch (_: Exception) {
                    } finally {
                        // Flipped last: the loop then runs one more pass that re-arms the overlay
                        // for the locked app, and only then waits for SCREEN_ON.
                        screenInteractive.value = false
                    }
                }
                Intent.ACTION_SCREEN_ON -> screenInteractive.value = true
            }
        }
    }

    private fun getLocalizedContext(): Context {
        val languageTag = try {
            preferencesRepository.currentPreferences.language
        } catch (e: Exception) {
            Log.e(TAG, "getLocalizedContext: failed to read language preference", e)
            ""
        }
        val appLocales = LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) {
            appLocales[0] ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }

        val config = Configuration(resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }

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
        val notification = createNotification()
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val fgServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (hasCameraPermission) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            type
        } else {
            0
        }

        try {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                1001,
                notification,
                fgServiceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground with type in AppMonitorService, fallback to SPECIAL_USE", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    androidx.core.app.ServiceCompat.startForeground(
                        this,
                        1001,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(1001, notification)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Critical: Failed to startForeground in AppMonitorService", e2)
            }
        }
        val prefs = preferencesRepository.currentPreferences
        if (prefs.isProtectionEnabled) {
            if (!isScreenReceiverRegistered) {
                try {
                    val filter = IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_SCREEN_ON)
                    }
                    registerReceiver(screenReceiver, filter)
                    isScreenReceiverRegistered = true
                    // Read after registering, both run on the main thread, so no event is missed.
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    screenInteractive.value = powerManager.isInteractive
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register screenReceiver", e)
                }
            }
            ServiceWatchdog.startWatchdog(this)
            permissionHealthMonitor.startMonitoring()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = preferencesRepository.currentPreferences
        if (!prefs.isProtectionEnabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Also covers a service that was (re)created by a capture request.
        ensureMonitoring()

        if (intent?.action == ACTION_CAPTURE_INTRUDER) {
            val packageName = intent.getStringExtra(EXTRA_INTRUDER_PACKAGE) ?: return START_STICKY
            val lockType = intent.getStringExtra(EXTRA_INTRUDER_LOCK_TYPE) ?: "UNKNOWN"
            val photoPath = intent.getStringExtra(EXTRA_INTRUDER_PHOTO_PATH) ?: return START_STICKY
            handleIntruderCapture(packageName, lockType, photoPath)
            // The last start command decides whether the system restarts a killed service;
            // START_NOT_STICKY here would silently drop the protection after the first capture.
            return START_STICKY
        }

        return START_STICKY
    }

    private fun ensureMonitoring() {
        if (lockedAppsJob?.isActive != true) startLockedAppsCollector()
        if (monitorJob?.isActive != true) startMonitoring()
    }

    private fun startLockedAppsCollector() {
        lockedAppsJob = serviceScope.launch {
            while (isActive) {
                try {
                    lockedAppsRepository.allLockedApps.collect { apps ->
                        lockedPackages = apps.asSequence()
                            .filter { it.lockedState }
                            .map { it.packageName }
                            .toSet()
                        lockedAppsReady.complete(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Locked apps collection failed, retrying", e)
                }
                delay(1_000L)
            }
        }
    }

    private fun isTransient(packageName: String): Boolean =
        packageName in TRANSIENT_PACKAGES && packageName !in lockedPackages

    private fun startMonitoring() {
        monitorJob = serviceScope.launch {
            lockedAppsReady.await()
            var screenOffPassDone = false
            var consecutiveErrors = 0
            while (isActive) {
                try {
                    if (screenOffPassDone) {
                        awaitScreenOn()
                        screenOffPassDone = false
                    }
                    // While the screen is off the loop still runs one full pass (the receiver has
                    // just re-locked everything, so this re-arms the overlay), then it waits.
                    val screenOffAtStart = isScreenReceiverRegistered && !screenInteractive.value
                    val pauseMs = pollOnce()
                    screenOffPassDone = screenOffAtStart
                    consecutiveErrors = 0
                    delay(pauseMs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveErrors++
                    if (consecutiveErrors == 1 || consecutiveErrors % 60 == 0) {
                        Log.e(TAG, "Monitor loop iteration failed (x$consecutiveErrors), continuing", e)
                    }
                    delay(POLL_IDLE_MS)
                }
            }
        }
    }

    private suspend fun awaitScreenOn() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        while (!screenInteractive.value) {
            val woke = withTimeoutOrNull(SCREEN_WAKE_CHECK_MS) { screenInteractive.first { it } }
            // Safety net in case a SCREEN_ON broadcast was missed.
            if (woke == null && powerManager.isInteractive) screenInteractive.value = true
        }
    }

    /** Runs one detection pass and returns how long to wait before the next one. */
    private suspend fun pollOnce(): Long {
        hybridDetectionEngine.pollUsageStats()
        val currentApp = hybridDetectionEngine.currentForegroundApp.value

        // پکیج خودمان را کلاً از چرخه‌ی قفل خارج می‌کنیم.
        // قفل ProLocker فقط از طریق MainActivity چک می‌شود.
        if (currentApp == packageName) return POLL_ACTIVE_MS

        // اگر تبلیغ در حال نمایش است، لاک اسکرین نباید ظاهر شود
        if (LockService.isHidingForAd) {
            val adHost = LockService.adHostPackage
            if (currentApp != null && adHost != null && currentApp != adHost && !isTransient(currentApp)) {
                // The user left the ad (Home/Recents) and opened another app.
                LockService.clearAdHiding()
            } else {
                return POLL_ACTIVE_MS
            }
        }

        if (currentApp == null) return POLL_IDLE_MS

        val now = System.currentTimeMillis()
        if (isTransient(currentApp)) {
            if (transientSince == 0L) transientSince = now
            return POLL_ACTIVE_MS
        }

        val previousApp = lastForegroundApp
        val leftTransientAt = transientSince
        transientSince = 0L
        // Back from a transient screen: the session continues if it is the same app and it was
        // not too long, otherwise the app counts as backgrounded since the transient screen began.
        var markedBackgrounded = false
        if (leftTransientAt != 0L && previousApp != null &&
            (currentApp != previousApp || now - leftTransientAt > TRANSIENT_MAX_MS)
        ) {
            sessionManager.markAppBackgrounded(previousApp, leftTransientAt)
            markedBackgrounded = true
        }

        if (currentApp != previousApp) {
            if (previousApp != null && !markedBackgrounded) {
                sessionManager.markAppBackgrounded(previousApp)
            }
            lastForegroundApp = currentApp
            currentlyLockingApp = null
        }

        val isLocked = currentApp in lockedPackages
        val showingPackage = LockService.showingPackageName
        // The overlay shows another app than the one in front (e.g. the user pressed Home).
        // The overlay of our own package belongs to MainActivity and is never touched here.
        val overlayIsStale = LockService.isShowing && showingPackage != null &&
            showingPackage != packageName && showingPackage != currentApp

        if (isLocked && !sessionManager.isAppUnlocked(currentApp)) {
            // Relaunch if: different package OR lock screen is no longer visible
            if (!LockService.isHidingForAd &&
                (currentlyLockingApp != currentApp || !LockService.isShowing || overlayIsStale)
            ) {
                launchLockScreen(currentApp)
                currentlyLockingApp = currentApp
                delay(500L)
            }
        } else {
            if (currentlyLockingApp == currentApp) {
                currentlyLockingApp = null
            }
            if (overlayIsStale) {
                LockService.dismiss(this)
                currentlyLockingApp = null
            }
        }
        return POLL_ACTIVE_MS
    }

    private fun launchLockScreen(packageName: String) {
        if (BuildConfig.DEBUG) {
            Log.d(
                PERF_TAG,
                "detect pkg=$packageName sinceResumeMs=${System.currentTimeMillis() - foregroundAppDetector.lastForegroundEventTime}"
            )
        }
        eventLogManager.logEvent("LOCK_TRIGGERED", packageName = packageName)
        foregroundAppDetector.lockServiceLaunchedAt = System.currentTimeMillis()
        LockService.start(this, packageName)
    }

    private fun handleIntruderCapture(packageName: String, lockType: String, photoPath: String) {
        serviceScope.launch {
            try {
                val hasCameraPermission = ContextCompat.checkSelfPermission(
                    this@AppMonitorService,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasCameraPermission) {
                    Log.w(TAG, "Cannot capture intruder: CAMERA permission not granted")
                    return@launch
                }

                // Background camera access needs the camera foreground service type (API 30+).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val cameraType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        } else {
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        }
                        androidx.core.app.ServiceCompat.startForeground(
                            this@AppMonitorService,
                            1001,
                            createNotification(),
                            cameraType
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to elevate foreground service type for camera", e)
                    }
                }

                val photoFile = java.io.File(photoPath)
                photoFile.parentFile?.mkdirs()

                val success = cameraCaptureManager.captureSelfie(photoFile)
                if (success && photoFile.exists() && photoFile.length() > 0) {
                    val entity = com.carbon.prolocker.core.database.IntruderEventEntity(
                        photoPath = photoFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        targetAppPackage = packageName,
                        lockType = lockType
                    )
                    intruderEventDao.insertEvent(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during intruder capture", e)
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
            preferencesRepository.currentPreferences.isStealthModeEnabled
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

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(localizedContext.getString(R.string.prolocker_is_active))
            .setContentText(localizedContext.getString(R.string.protecting_your_apps))
            .setSmallIcon(R.drawable.ic_notifi)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob = null
        lockedAppsJob = null
        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            } finally {
                isScreenReceiverRegistered = false
            }
        }
        serviceScope.cancel()
        permissionHealthMonitor.stopMonitoring()
    }
}
