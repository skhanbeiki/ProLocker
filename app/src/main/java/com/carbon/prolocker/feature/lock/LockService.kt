package com.carbon.prolocker.feature.lock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
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
import com.carbon.prolocker.feature.home.MemoryOptimizerScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class LockService : Service(), OnBackPressedDispatcherOwner {

    private val _backPressedDispatcher = OnBackPressedDispatcher()

    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = _backPressedDispatcher

    override val lifecycle: Lifecycle get() = serviceLifecycleOwner.lifecycle

    companion object {
        private const val TAG = "LockService"
        const val ACTION_SHOW = "com.carbon.prolocker.action.SHOW_LOCK"
        const val ACTION_DISMISS = "com.carbon.prolocker.action.DISMISS_LOCK"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_FOR_SETTINGS = "extra_for_settings"
        const val EXTRA_OPEN_DESTINATION = "extra_open_destination"

        @Volatile
        var isShowing: Boolean = false
            private set

        fun start(context: Context, packageName: String, forSettings: Boolean = false) {
            val intent = Intent(context, LockService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_FOR_SETTINGS, forSettings)
            }
            context.startService(intent)
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, LockService::class.java).apply {
                action = ACTION_DISMISS
            }
            context.startService(intent)
        }
    }

    private lateinit var overlayManager: OverlayWindowManager
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception in LockService", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + serviceExceptionHandler)
    private val serviceLifecycleOwner = ServiceLifecycleOwner()
    private var currentPackageName: String? = null
    private var currentForSettings: Boolean = false
    private val currentScreen = mutableStateOf<LockNavScreen>(LockNavScreen.Lock)

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
        overlayManager = OverlayWindowManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val forSettings = intent.getBooleanExtra(EXTRA_FOR_SETTINGS, false)
                showLockScreen(packageName, forSettings)
            }
            ACTION_DISMISS -> {
                dismissLockScreen()
            }
        }
        return START_NOT_STICKY
    }

    private fun showLockScreen(packageName: String, forSettings: Boolean) {
        if (isShowing) return

        currentPackageName = packageName
        currentForSettings = forSettings
        currentScreen.value = LockNavScreen.Lock

        serviceLifecycleOwner.start()
        serviceLifecycleOwner.resume()

        val lockScreenAdPlace = try {
            runBlocking {
                org.koin.java.KoinJavaComponent.get<com.carbon.prolocker.network.repository.RemoteConfigRepository>(
                    com.carbon.prolocker.network.repository.RemoteConfigRepository::class.java
                ).getLockScreenAdPlace()
            }
        } catch (_e: Exception) {
            "bottom"
        }

        val preloadedTopAd = adManager.consumeCachedNativeAdView(AdPlacement.LOCKSCREEN_TOP)
        val preloadedBottomAd = adManager.consumeCachedNativeAdView(AdPlacement.LOCKSCREEN_BOTTOM)

        val composeView = createComposeView(
            packageName = packageName,
            forSettings = forSettings,
            lockScreenAdPlace = lockScreenAdPlace,
            preloadedAdView = when (lockScreenAdPlace) {
                "top", "topBanner" -> preloadedTopAd
                "bottom" -> preloadedBottomAd
                else -> null
            }
        )

        try {
            overlayManager.show(
                composeView,
                serviceLifecycleOwner,
                serviceLifecycleOwner,
                serviceLifecycleOwner,
                onBackPressedDispatcher
            )
            isShowing = true
            eventLogManager.logEvent("LOCK_OVERLAY_SHOWN", packageName = packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            serviceLifecycleOwner.stop()
            isShowing = false
            currentPackageName = null
            stopSelf()
        }
    }

    private fun dismissLockScreen() {
        if (!isShowing) return
        serviceLifecycleOwner.pause()
        serviceLifecycleOwner.stop()
        overlayManager.dismiss()
        isShowing = false
        currentPackageName = null
        stopSelf()
    }

    private fun createComposeView(
        packageName: String,
        forSettings: Boolean,
        lockScreenAdPlace: String,
        preloadedAdView: android.view.View?
    ): ComposeView {
        val initialLang = languageManager.getEffectiveLanguageTag()
        val initialContext = languageManager.createLocalizedContext(this, initialLang)
        return ComposeView(initialContext).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnLifecycleDestroyed(serviceLifecycleOwner))
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
                    LocalOnBackPressedDispatcherOwner provides this@LockService
                ) {
                    ProLockerTheme(useDarkTheme = isDarkMode) {
                        val stateHolder = LockOverlayState(packageName, forSettings)
                        val unlocked by lockViewModel.unlocked.collectAsState()

                        LaunchedEffect(unlocked) {
                            if (unlocked) {
                                dismissLockScreen()
                            }
                        }

                        BackHandler {
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
                                        packageName = stateHolder.packageName.value,
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
                                             BiometricAuthActivity.start(this@LockService, stateHolder.packageName.value)
                                        },
                                        onPatternComplete = { lockViewModel.verifyPattern(it, packageName) },
                                        onPinComplete = { lockViewModel.verifyPin(it, packageName) },
                                        onErrorReset = { lockViewModel.resetError() },
                                        onVerifyRecoveryAnswer = { answer, onResult ->
                                            lockViewModel.verifyRecoveryAnswer(answer, onResult)
                                        },
                                        onRecoverySuccess = {
                                            lockViewModel.unlockForRecovery(packageName)
                                            dismissLockScreen()
                                        },
                                        onBackClick = {
                                            if (forSettings) {
                                                dismissLockScreen()
                                            } else {
                                                navigateToHome()
                                            }
                                        },
                                        onNavigateToBackgrounds = {
                                            currentScreen.value = LockNavScreen.Gallery
                                        },
                                        onNavigateToMemory = {
                                            currentScreen.value = LockNavScreen.MemoryOptimizer
                                        }
                                    )
                                }

                                is LockNavScreen.Gallery -> {
                                    BackgroundGalleryScreen(
                                        onBack = { currentScreen.value = LockNavScreen.Lock },
                                        onBackgroundClick = { url, id ->
                                            val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                            currentScreen.value = LockNavScreen.Preview(encodedUrl, id)
                                        }
                                    )
                                }

                                is LockNavScreen.Preview -> {
                                    val decodedUrl = URLDecoder.decode(targetScreen.url, "UTF-8")
                                    BackgroundPreviewScreen(
                                        url = decodedUrl,
                                        id = targetScreen.id,
                                        onBack = { currentScreen.value = LockNavScreen.Gallery }
                                    )
                                }

                                is LockNavScreen.MemoryOptimizer -> {
                                    MemoryOptimizerScreen(
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
            is LockNavScreen.Gallery, is LockNavScreen.MemoryOptimizer -> currentScreen.value = LockNavScreen.Lock
        }
    }

    override fun onDestroy() {
        serviceLifecycleOwner.destroy()
        serviceScope.cancel()
        overlayManager.dismiss()
        isShowing = false
        super.onDestroy()
    }
}
