package com.carbon.prolocker

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.navigation.AppNavigation
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.core.security.TrustedInternalLaunchManager
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.core.service.LockSessionManager
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import com.carbon.prolocker.worker.RemoteConfigWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val preferencesRepository: PreferencesRepository by inject()
    private val languageManager: LanguageManager by inject()
    private val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager by inject()
    private val sessionManager: LockSessionManager by inject()
    private val remoteConfigRepository: RemoteConfigRepository by inject()
    private val stealthModeManager: StealthModeManager by inject()
    private val adManager: AdManager by inject()

    private var pendingNavType: String? = null
    private var trustedLaunchDestination: String? = null
    private var trustedInternalDestination: String? = null
    private var storedLockedPackage: String? = null
    private var isContentShown = false
    private var isNotificationReturn = false

    private val entryLockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            AppEntryLockActivity.markAuthenticated()
            showMainContent()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        requestedOrientation = try {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (_: IllegalStateException) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        analyticsManager.trackAppOpen()

        pendingNavType = intent?.getStringExtra("navigation_type")
        trustedInternalDestination = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_OPEN_DESTINATION)
        storedLockedPackage = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_PACKAGE_NAME)
        isNotificationReturn = false
        val navType = pendingNavType
        if (navType != null) {
            analyticsManager.trackNotificationOpened(navType)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavType = intent.getStringExtra("navigation_type")
        trustedInternalDestination = intent.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_OPEN_DESTINATION)
        storedLockedPackage = intent.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_PACKAGE_NAME)
        isNotificationReturn = intent.getBooleanExtra(
            com.carbon.prolocker.worker.RamCleanerNotificationManager.EXTRA_FROM_NOTIFICATION, false
        )
        val navType = pendingNavType
        if (navType != null) {
            analyticsManager.trackNotificationOpened(navType)
        }
        // If content is already shown, re-trigger navigation via recomposition
        if (isContentShown) {
            isContentShown = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isContentShown) return

        // Handle trusted internal navigation from LockService
        if (trustedInternalDestination != null) {
            AppEntryLockActivity.markAuthenticated()
            showMainContent()
            return
        }

        val isFromNotification = intent?.getBooleanExtra(
            com.carbon.prolocker.worker.RamCleanerNotificationManager.EXTRA_FROM_NOTIFICATION, false
        ) == true

        val isRecoveryLaunch = intent?.component?.className ==
            "com.carbon.prolocker.launcher.RecoveryLauncherAlias"

        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        val isStealthEnabled = prefs.isStealthModeEnabled
        val isOnboardingCompleted = prefs.onboardingCompleted && prefs.lockType != "NONE"

        if (isRecoveryLaunch || isStealthEnabled) {
            // Do state cleanup that recoveryUnlockLauncher used to handle on RESULT_OK
            lifecycleScope.launch {
                stealthModeManager.setStealthMode(false, this@MainActivity)
                AppEntryLockActivity.markAuthenticated()
            }
            // Show lock screen via LockService (dismisses itself on unlock)
            com.carbon.prolocker.feature.lock.LockService.start(this, packageName, forSettings = true)
            return
        }

        if (isFromNotification) {
            trustedLaunchDestination = pendingNavType
            AppEntryLockActivity.markAuthenticated()
            showMainContent()
            return
        }

        if (isOnboardingCompleted && AppEntryLockActivity.requiresAuthentication()) {
            val trustedDest = TrustedInternalLaunchManager.consume()
            if (trustedDest != null) {
                trustedLaunchDestination = trustedDest
                AppEntryLockActivity.markAuthenticated()
                showMainContent()
                return
            }
            if (TrustedReturnManager.consumeTrustedReturn()) {
                AppEntryLockActivity.markAuthenticated()
                isContentShown = true
                return
            }
            val lockIntent = Intent(this, AppEntryLockActivity::class.java)
            entryLockLauncher.launch(lockIntent)
            return
        }

        AppEntryLockActivity.markAuthenticated()
        showMainContent()
    }

    override fun onStop() {
        super.onStop()
        isContentShown = false
        storedLockedPackage?.let {
            sessionManager.disarmUnlock(it)
            storedLockedPackage = null
        }
        sessionManager.markAppBackgrounded(packageName)
    }

    private fun showMainContent() {
        if (isContentShown) return
        isContentShown = true

        // Store locked package from LockService for re-locking on exit
        if (storedLockedPackage == null) {
            storedLockedPackage = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_PACKAGE_NAME)
        }

        enableEdgeToEdge()
        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        val isOnboardingCompleted = prefs.onboardingCompleted && prefs.lockType != "NONE"

        // Apply locale BEFORE setContent so string resources resolve in the correct language
        languageManager.applyLanguage(prefs.language)

        setContent {
            val currentPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = currentPrefs?.isDarkMode ?: false
            val language = currentPrefs?.language ?: "fa"
            val layoutDirection = if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            deepLinkType = pendingNavType,
                            isOnboardingCompleted = isOnboardingCompleted,
                            trustedLaunchDestination = trustedInternalDestination ?: trustedLaunchDestination,
                            isStandaloneExit = !isNotificationReturn
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            try {
                remoteConfigRepository.syncConfig()
            } catch (_: Exception) {
                // Ignore sync errors during startup
            }

            val currentPrefs = preferencesRepository.userPreferencesFlow.first()
            RemoteConfigWorker.schedule(applicationContext, currentPrefs.remoteConfigInterval)
            stealthModeManager.repairLauncherState(this@MainActivity)

            // Preload lock screen ads so they're ready when LockService needs them
            try {
                adManager.preloadNativeAd(this@MainActivity, AdPlacement.LOCKSCREEN_TOP, NativeAdType.TYPE_2)
                adManager.preloadNativeAd(this@MainActivity, AdPlacement.LOCKSCREEN_BOTTOM, NativeAdType.TYPE_3)
            } catch (_: Exception) {
                // Ad preloading is best-effort
            }
        }
    }
}
