package com.carbon.prolocker.feature.lock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.IntruderManager
import com.carbon.prolocker.core.security.RecoveryManager
import com.carbon.prolocker.core.service.FailedAttemptManager
import com.carbon.prolocker.core.service.LockSessionManager
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.core.utils.VibrationManager
import com.carbon.prolocker.feature.gallery.BackgroundGalleryScreen
import com.carbon.prolocker.feature.gallery.BackgroundPreviewScreen
import com.carbon.prolocker.feature.junkcleaner.JunkCleanerScreen
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class LockService : Service(), OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {

    private val _backPressedDispatcher = OnBackPressedDispatcher()

    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = _backPressedDispatcher

    private val _activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            try {
                val intent = contract.createIntent(this@LockService, input)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@LockService.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch intent from LockService", e)
            }
        }
    }

    override val activityResultRegistry: ActivityResultRegistry get() = _activityResultRegistry

    override val lifecycle: Lifecycle get() = serviceLifecycleOwner.lifecycle

    companion object {
        private const val TAG = "LockService"
        private const val PERF_TAG = "LockPerf"
        const val ACTION_SHOW = "com.carbon.prolocker.action.SHOW_LOCK"
        const val ACTION_DISMISS = "com.carbon.prolocker.action.DISMISS_LOCK"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_FOR_SETTINGS = "extra_for_settings"
        const val EXTRA_OPEN_DESTINATION = "extra_open_destination"

        // The service now outlives each unlock; stop it only after this long without a lock screen.
        private const val IDLE_STOP_DELAY_MS = 10 * 60 * 1000L

        // The ad activity should open within this window after prepareForAd().
        private const val AD_PREPARE_WINDOW_MS = 4_000L

        // Upper bound for a hidden-for-ad overlay, in case the ad activity never reports back.
        private const val AD_ACTIVE_WINDOW_MS = 120_000L

        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        var isShowing: Boolean = false
            private set

        /** Package the overlay currently shows, null when it is not showing. */
        @Volatile
        var showingPackageName: String? = null
            private set

        @Volatile
        private var adPreparedAt: Long = 0L

        @Volatile
        private var adActiveSince: Long = 0L

        /** Package that was locked when the current ad flow started. */
        @Volatile
        var adHostPackage: String? = null
            private set

        val isHidingForAd: Boolean
            get() {
                val now = SystemClock.elapsedRealtime()
                val activeSince = adActiveSince
                if (activeSince > 0L) return now - activeSince < AD_ACTIVE_WINDOW_MS
                val preparedAt = adPreparedAt
                return preparedAt > 0L && now - preparedAt < AD_PREPARE_WINDOW_MS
            }

        @Volatile
        var instance: LockService? = null
            private set

        @Volatile
        var lastLockedPackageName: String? = null
            private set

        @Volatile
        private var showRequestedAt: Long = 0L

        private fun runOnMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post { block() }
            }
        }

        private fun resetAdState() {
            adPreparedAt = 0L
            adActiveSince = 0L
            adHostPackage = null
        }

        fun prepareForAd() {
            Log.i(TAG, "prepareForAd")
            adHostPackage = instance?.currentPackageName ?: lastLockedPackageName
            adActiveSince = 0L
            adPreparedAt = SystemClock.elapsedRealtime()
        }

        fun hideOverlayForAd() {
            adActiveSince = SystemClock.elapsedRealtime()
            if (adHostPackage == null) {
                adHostPackage = instance?.currentPackageName ?: lastLockedPackageName
            }
            val s = instance ?: return
            runOnMain {
                try {
                    s.overlayManager.dismiss()
                    isShowing = false
                    showingPackageName = null
                    Log.i(TAG, "hideOverlayForAd: overlay dismissed, isShowing=false, isHidingForAd=true")
                } catch (e: Exception) {
                    Log.e(TAG, "Error dismissing overlay for ad", e)
                }
            }
        }

        /** Drops the ad-hiding state without re-showing the overlay. */
        fun clearAdHiding() {
            resetAdState()
            val s = instance ?: return
            runOnMain {
                if (!isShowing) s.releaseWhenIdle()
            }
        }

        fun restoreLockScreen(context: Context? = null) {
            resetAdState()
            val s = instance
            val pkg = s?.currentPackageName ?: lastLockedPackageName
            val forSettings = s?.currentForSettings ?: false
            Log.i(TAG, "restoreLockScreen: pkg=$pkg, hasInstance=${s != null}")
            if (s != null && !s.isStopping && pkg != null) {
                runOnMain {
                    try {
                        if (s.isStopping) {
                            if (context != null) sendShowIntent(context, pkg, forSettings)
                        } else {
                            s.currentScreen.value = LockNavScreen.Lock
                            if (!isShowing) {
                                s.showLockScreen(pkg, forSettings)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring lockscreen after ad", e)
                    }
                }
            } else if (context != null && pkg != null) {
                start(context, pkg, forSettings)
            }
        }

        fun start(context: Context, packageName: String, forSettings: Boolean = false) {
            if (BuildConfig.DEBUG) {
                showRequestedAt = SystemClock.uptimeMillis()
                Log.d(PERF_TAG, "show requested pkg=$packageName")
            }
            val s = instance
            if (s != null && !s.isStopping) {
                // Service is alive: skip the startService IPC and run on the main thread.
                runOnMain {
                    try {
                        if (s.isStopping) {
                            sendShowIntent(context, packageName, forSettings)
                        } else {
                            s.showLockScreen(packageName, forSettings)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing lock screen", e)
                    }
                }
                return
            }
            sendShowIntent(context, packageName, forSettings)
        }

        fun dismiss(context: Context) {
            val s = instance
            if (s != null && !s.isStopping) {
                runOnMain {
                    try {
                        if (!s.isStopping) s.dismissLockScreen()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dismissing lock screen", e)
                    }
                }
                return
            }
            // A start may still be in flight; the intent keeps show/dismiss ordered.
            try {
                context.startService(
                    Intent(context, LockService::class.java).apply { action = ACTION_DISMISS }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send dismiss to LockService", e)
            }
        }

        private fun sendShowIntent(context: Context, packageName: String, forSettings: Boolean) {
            val intent = Intent(context, LockService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_FOR_SETTINGS, forSettings)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LockService", e)
            }
        }
    }

    private lateinit var overlayManager: OverlayWindowManager
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception in LockService", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + serviceExceptionHandler)
    private val serviceLifecycleOwner = ServiceLifecycleOwner()

    // Read from other threads by the companion (ad flow), written on the main thread.
    @Volatile
    private var currentPackageName: String? = null

    @Volatile
    private var currentForSettings: Boolean = false

    // True once stopSelf has been requested or onDestroy ran; new requests must go through startService.
    @Volatile
    private var isStopping: Boolean = false
    private var lastStartId: Int = 0
    private var recoveryCompleted: Boolean = false
    private val currentScreen = mutableStateOf<LockNavScreen>(LockNavScreen.Lock)

    // Package the composable reads; changing it switches the locked app without re-adding the window.
    private val overlayState = LockOverlayState()

    private val idleStopRunnable = Runnable {
        if (!isShowing && !isHidingForAd && stopSelfResult(lastStartId)) {
            isStopping = true
        }
    }

    // Koin-injected dependencies
    private val sessionManager: LockSessionManager by org.koin.java.KoinJavaComponent.inject(
        LockSessionManager::class.java
    )
    private val eventLogManager: EventLogManager by org.koin.java.KoinJavaComponent.inject(
        EventLogManager::class.java
    )
    private val adManager: AdManager by org.koin.java.KoinJavaComponent.inject(
        AdManager::class.java
    )
    private val preferencesRepository: PreferencesRepository by org.koin.java.KoinJavaComponent.inject(
        PreferencesRepository::class.java
    )
    private val languageManager: LanguageManager by org.koin.java.KoinJavaComponent.inject(
        LanguageManager::class.java
    )
    private val failedAttemptManager: FailedAttemptManager by org.koin.java.KoinJavaComponent.inject(
        FailedAttemptManager::class.java
    )
    private val intruderManager: IntruderManager by org.koin.java.KoinJavaComponent.inject(
        IntruderManager::class.java
    )
    private val recoveryManager: RecoveryManager by org.koin.java.KoinJavaComponent.inject(
        RecoveryManager::class.java
    )
    private val vibrationManager: VibrationManager by org.koin.java.KoinJavaComponent.inject(
        VibrationManager::class.java
    )
    private val remoteConfigRepository: RemoteConfigRepository by org.koin.java.KoinJavaComponent.inject(
        RemoteConfigRepository::class.java
    )

    private val lockViewModel: LockViewModel by lazy {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LockViewModel(
                    preferencesRepository = preferencesRepository,
                    sessionManager = sessionManager,
                    failedAttemptManager = failedAttemptManager,
                    intruderManager = intruderManager,
                    recoveryManager = recoveryManager,
                    eventLogManager = eventLogManager,
                    vibrationManager = vibrationManager
                ) as T
            }
        }
        ViewModelProvider(serviceLifecycleOwner.viewModelStore, factory)[LockViewModel::class.java]
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        overlayManager = OverlayWindowManager(this)
        // A service that was started for something other than a show must not linger forever.
        scheduleIdleStop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        isStopping = false
        try {
            when (intent?.action) {
                ACTION_SHOW -> {
                    val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                    if (packageName == null) {
                        Log.w(TAG, "ACTION_SHOW without package name")
                    } else {
                        showLockScreen(packageName, intent.getBooleanExtra(EXTRA_FOR_SETTINGS, false))
                    }
                }
                ACTION_DISMISS -> {
                    dismissLockScreen()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand failed", e)
        }
        return START_NOT_STICKY
    }

    private fun scheduleIdleStop() {
        mainHandler.removeCallbacks(idleStopRunnable)
        mainHandler.postDelayed(idleStopRunnable, IDLE_STOP_DELAY_MS)
    }

    private fun cancelIdleStop() {
        mainHandler.removeCallbacks(idleStopRunnable)
    }

    /** No window and nothing pending: park the lifecycle and arm the idle stop. */
    private fun releaseWhenIdle() {
        serviceLifecycleOwner.pause()
        serviceLifecycleOwner.stop()
        scheduleIdleStop()
    }

    private fun perfLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(PERF_TAG, "$message +${SystemClock.uptimeMillis() - showRequestedAt}ms")
        }
    }

    /** Points the shared state (VM, navigation, composable package) at a new lock session. */
    private fun beginSession(packageName: String, forSettings: Boolean) {
        currentPackageName = packageName
        lastLockedPackageName = packageName
        currentForSettings = forSettings
        recoveryCompleted = false
        // A stale "unlocked" entry would make dismiss look like a successful unlock (own package in stealth mode).
        if (forSettings) sessionManager.removeUnlockedApp(packageName)
        // The ViewModel outlives the overlay now, so a previous unlock must not leak into this one.
        lockViewModel.resetForNewSession()
        currentScreen.value = LockNavScreen.Lock
        overlayState.packageName.value = packageName
    }

    private fun showLockScreen(packageName: String, forSettings: Boolean) {
        if (isStopping) return
        if (isHidingForAd) {
            Log.d(TAG, "showLockScreen ignored: isHidingForAd")
            return
        }

        if (isShowing) {
            val shown = showingPackageName
            if (shown == packageName) return
            if (shown != null && !currentForSettings) {
                beginSession(packageName, forSettings)
                showingPackageName = packageName
                if (BuildConfig.DEBUG) perfLog("overlay switched to $packageName")
                eventLogManager.logEvent("LOCK_OVERLAY_SHOWN", packageName = packageName)
            } else {
                Log.d(TAG, "showLockScreen ignored: already showing $shown")
            }
            return
        }

        cancelIdleStop()
        beginSession(packageName, forSettings)

        serviceLifecycleOwner.start()
        serviceLifecycleOwner.resume()

        val placeholderColor =
            if (preferencesRepository.currentPreferences.selectedBackgroundUrl.isEmpty()) {
                0xFF377AFF.toInt()
            } else {
                android.graphics.Color.BLACK
            }

        val onRootFirstDraw: (() -> Unit)? =
            if (BuildConfig.DEBUG) ({ perfLog("root first draw") }) else null
        val onContentFirstDraw: (() -> Unit)? =
            if (BuildConfig.DEBUG) ({ perfLog("compose view first draw") }) else null

        val added = try {
            overlayManager.show(
                composeViewFactory = { createComposeView() },
                lifecycleOwner = serviceLifecycleOwner,
                savedStateRegistryOwner = serviceLifecycleOwner,
                viewModelStoreOwner = serviceLifecycleOwner,
                onBackPressedDispatcher = onBackPressedDispatcher,
                placeholderColor = placeholderColor,
                onContentFailed = { dismissLockScreen() },
                onRootFirstDraw = onRootFirstDraw,
                onContentFirstDraw = onContentFirstDraw
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            false
        }
        if (BuildConfig.DEBUG) perfLog("window added=$added")

        if (added) {
            isShowing = true
            showingPackageName = packageName
            eventLogManager.logEvent("LOCK_OVERLAY_SHOWN", packageName = packageName)
        } else {
            isShowing = false
            showingPackageName = null
            currentPackageName = null
            releaseWhenIdle()
        }
    }

    private fun dismissLockScreen() {
        if (!isShowing) {
            // Nothing to close; make sure a service that was started only to dismiss still ends.
            if (!isHidingForAd) scheduleIdleStop()
            return
        }
        val pkg = currentPackageName ?: showingPackageName
        // Decide before the state is cleared. isAppUnlocked covers biometric unlocks, which
        // go through BiometricAuthActivity and never touch the ViewModel.
        val unlocked = pkg != null &&
                (lockViewModel.unlocked.value || recoveryCompleted || sessionManager.isAppUnlocked(pkg))

        // The ViewModel is no longer cleared with the service, so silence the intruder alarm here.
        lockViewModel.stopAlarm()

        isShowing = false
        showingPackageName = null
        currentPackageName = null
        serviceLifecycleOwner.pause()
        serviceLifecycleOwner.stop()
        overlayManager.dismiss()
        scheduleIdleStop()

        if (pkg != null) {
            LockOverlayEvents.emit(
                if (unlocked) LockOverlayEvent.Unlocked(pkg) else LockOverlayEvent.Cancelled(pkg)
            )
        }
    }

    private fun createComposeView(): ComposeView {
        val lockScreenAdPlace = try {
            remoteConfigRepository.cachedLockScreenAdPlace()
        } catch (_e: Exception) {
            "bottom"
        }

        val preloadedTopAd = adManager.consumeCachedNativeAdView(AdPlacement.LOCKSCREEN_TOP)
        val preloadedBottomAd = adManager.consumeCachedNativeAdView(AdPlacement.LOCKSCREEN_BOTTOM)
        val preloadedAdView: android.view.View? = when (lockScreenAdPlace) {
            "top", "topBanner" -> preloadedTopAd
            "bottom" -> preloadedBottomAd
            else -> null
        }

        val initialLang = languageManager.getEffectiveLanguageTag()
        val initialContext = languageManager.createLocalizedContext(this, initialLang)
        // Default composition strategy: disposed when the view leaves the window. The service
        // lifecycle is not destroyed between locks anymore, so a lifecycle-bound strategy would leak.
        return ComposeView(initialContext).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setContent {
                val currentPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
                val isDarkMode = currentPrefs?.isDarkMode ?: true
                val language = languageManager.getEffectiveLanguageTag(currentPrefs?.language)
                val layoutDirection = languageManager.getLayoutDirection(language)
                val baseContext = LocalContext.current
                val localizedContext = androidx.compose.runtime.remember(baseContext, language) {
                    languageManager.createLocalizedContext(baseContext, language)
                }

                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration,
                    LocalLayoutDirection provides layoutDirection,
                    LocalViewModelStoreOwner provides serviceLifecycleOwner,
                    LocalOnBackPressedDispatcherOwner provides this@LockService,
                    LocalActivityResultRegistryOwner provides this@LockService
                ) {
                    ProLockerTheme(useDarkTheme = isDarkMode) {
                        val lockedPackage by overlayState.packageName
                        val unlocked by lockViewModel.unlocked.collectAsState()

                        LaunchedEffect(unlocked) {
                            if (unlocked) {
                                dismissLockScreen()
                            }
                        }

                        BackHandler(enabled = currentScreen.value is LockNavScreen.Lock || currentScreen.value is LockNavScreen.Preview) {
                            navigateBack()
                        }

                        val screen by currentScreen

                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            contentKey = { it::class }
                        ) { targetScreen ->
                            when (targetScreen) {
                                is LockNavScreen.Lock -> {
                                    LockScreenContent(
                                        packageName = lockedPackage,
                                        lockType = lockViewModel.lockType.collectAsState().value,
                                        selectedBackgroundUrl = lockViewModel.selectedBackgroundUrl.collectAsState().value,
                                        isError = lockViewModel.isError.collectAsState().value,
                                        failedAttemptsCount = lockViewModel.failedAttempts.collectAsState().value.count,
                                        threshold = lockViewModel.threshold.collectAsState().value,
                                        recoveryQuestion = lockViewModel.recoveryQuestion.collectAsState().value,
                                        hidePatternPath = lockViewModel.hidePatternPath.collectAsState().value,
                                        vibrationEnabled = lockViewModel.vibrationEnabled.collectAsState().value,
                                        lockScreenAdPlace = lockScreenAdPlace,
                                        adManager = adManager,
                                        preloadedAdView = preloadedAdView,
                                        fingerprintUnlockEnabled = lockViewModel.fingerprintUnlockEnabled.collectAsState().value,
                                        onFingerprintClick = {
                                            BiometricAuthActivity.start(this@LockService, overlayState.packageName.value)
                                        },
                                        onPatternComplete = { lockViewModel.verifyPattern(it, overlayState.packageName.value) },
                                        onPinComplete = { lockViewModel.verifyPin(it, overlayState.packageName.value) },
                                        onErrorReset = { lockViewModel.resetError() },
                                        onVerifyRecoveryAnswer = { answer, onResult ->
                                            lockViewModel.verifyRecoveryAnswer(answer, onResult)
                                        },
                                        onRecoverySuccess = {
                                            lockViewModel.unlockForRecovery(overlayState.packageName.value)
                                            recoveryCompleted = true
                                            dismissLockScreen()
                                        },
                                        onBackClick = {
                                            if (currentForSettings) {
                                                dismissLockScreen()
                                            } else {
                                                navigateToHome()
                                            }
                                        },
                                        onNavigateToBackgrounds = {
                                            currentScreen.value = LockNavScreen.Gallery
                                        },
                                        onNavigateToMemory = {
                                            currentScreen.value = LockNavScreen.JunkCleaner
                                        }
                                    )
                                }

                                 is LockNavScreen.Gallery -> {
                                    BackgroundGalleryScreen(
                                        isExternal = true,
                                        onBack = { currentScreen.value = LockNavScreen.Lock },
                                        onBackgroundClick = { url, id, fromDownloaded ->
                                            val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                            currentScreen.value = LockNavScreen.Preview(encodedUrl, id, fromDownloaded)
                                        }
                                    )
                                }

                                is LockNavScreen.Preview -> {
                                    val decodedUrl = URLDecoder.decode(targetScreen.url, "UTF-8")
                                    BackgroundPreviewScreen(
                                        url = decodedUrl,
                                        id = targetScreen.id,
                                        fromDownloaded = targetScreen.fromDownloaded,
                                        onBack = { currentScreen.value = LockNavScreen.Gallery }
                                    )
                                }

                                is LockNavScreen.JunkCleaner -> {
                                    JunkCleanerScreen(
                                        isExternal = true,
                                        onBack = { currentScreen.value = LockNavScreen.Lock }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        dismissLockScreen()
    }

    private fun navigateBack() {
        when (currentScreen.value) {
            is LockNavScreen.Lock -> {
                if (currentForSettings) dismissLockScreen() else navigateToHome()
            }
            is LockNavScreen.Preview -> currentScreen.value = LockNavScreen.Gallery
            is LockNavScreen.Gallery, is LockNavScreen.JunkCleaner -> currentScreen.value = LockNavScreen.Lock
        }
    }

    override fun onDestroy() {
        isStopping = true
        cancelIdleStop()
        if (instance === this) {
            instance = null
        }
        val pkg = currentPackageName
        val wasShowing = isShowing
        overlayManager.dismiss()
        isShowing = false
        showingPackageName = null
        currentPackageName = null
        serviceLifecycleOwner.destroy()
        serviceScope.cancel()
        // Someone may be waiting for this overlay (e.g. MainActivity in stealth mode).
        if (wasShowing && pkg != null) {
            LockOverlayEvents.emit(LockOverlayEvent.Cancelled(pkg))
        }
        super.onDestroy()
    }
}
