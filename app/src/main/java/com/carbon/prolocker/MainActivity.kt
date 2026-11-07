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
import com.carbon.prolocker.core.security.NotificationLaunchToken
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.core.security.TrustedInternalLaunchManager
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.core.service.LockSessionManager
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import com.carbon.prolocker.feature.lock.LockOverlayEvent
import com.carbon.prolocker.feature.lock.LockOverlayEvents
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import com.carbon.prolocker.worker.RemoteConfigWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val preferencesRepository: PreferencesRepository by inject()
    private val languageManager: LanguageManager by inject()
    private val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager by inject()
    private val sessionManager: LockSessionManager by inject()
    private val remoteConfigRepository: RemoteConfigRepository by inject()
    private val stealthModeManager: StealthModeManager by inject()
    private val adManager: AdManager by inject()
    private val rateAppManager: com.carbon.prolocker.core.rate.RateAppManager by inject()

    private var pendingNavType: String? = null
    private var trustedLaunchDestination: String? = null
    private var trustedInternalDestination: String? = null
    private var storedLockedPackage: String? = null
    private var isContentShown = false
    private var hasSetContent = false
    private var isNotificationReturn = false
    private var notificationLaunchVerified = false
    private var awaitingEntryUnlock = false
    private var overlayEventsJob: Job? = null

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

    override fun attachBaseContext(newBase: android.content.Context) {
        val repo = try {
            org.koin.java.KoinJavaComponent.get<PreferencesRepository>(PreferencesRepository::class.java)
        } catch (_: Exception) {
            null
        }
        val effective = LanguageManager.resolveEffectiveLanguage(repo, newBase)
        val localizedContext = LanguageManager.createLocalizedContextStatic(newBase, effective)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                rateAppManager.recordLaunch()
            }
        }

        requestedOrientation = try {
            // Phones stay portrait; tablets follow the device rotation (layouts adapt to landscape).
            if (resources.configuration.smallestScreenWidthDp >= 600) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (_: IllegalStateException) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        analyticsManager.trackAppOpen()

        pendingNavType = intent?.getStringExtra("navigation_type")
        trustedInternalDestination = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_OPEN_DESTINATION)
        storedLockedPackage = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_PACKAGE_NAME)
        isNotificationReturn = false
        verifyNotificationLaunch(intent)
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
            com.carbon.prolocker.worker.JunkCleanerNotificationManager.EXTRA_FROM_NOTIFICATION, false
        )
        verifyNotificationLaunch(intent)
        val navType = pendingNavType
        if (navType != null) {
            analyticsManager.trackNotificationOpened(navType)
        }
        // Force re-inflation only when a navigation is actually requested (deep link /
        // trusted internal destination). A plain re-launch (e.g. tapping the launcher
        // icon) must keep the current screen and back stack intact.
        val hasNavTarget = pendingNavType != null || trustedInternalDestination != null
        if (hasNavTarget && isContentShown) {
            isContentShown = false
        }
    }

    override fun onResume() {
        super.onResume()

        // One-shot: only the delivery of the intent that carried a valid token may skip the entry lock.
        val isFromNotification = notificationLaunchVerified
        notificationLaunchVerified = false

        val isRecoveryLaunch = intent?.component?.className ==
            "com.carbon.prolocker.launcher.RecoveryLauncherAlias"

        val prefs = preferencesRepository.currentPreferences
        val isStealthEnabled = prefs.isStealthModeEnabled
        val isOnboardingCompleted = prefs.onboardingCompleted && prefs.lockType != "NONE"

        if (isRecoveryLaunch || isStealthEnabled) {
            // Stealth is only lifted and the entry lock only released once the overlay reports a
            // successful unlock (see observeEntryLockOverlay).
            awaitingEntryUnlock = true
            observeEntryLockOverlay()
            if (com.carbon.prolocker.feature.lock.LockService.showingPackageName != packageName) {
                com.carbon.prolocker.feature.lock.LockService.start(this, packageName, forSettings = true)
            }
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
                // A recreated activity (e.g. config change while in system settings) has no content yet.
                if (!hasSetContent) showMainContent()
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
        storedLockedPackage?.let {
            sessionManager.disarmUnlock(it)
            storedLockedPackage = null
        }
        sessionManager.markAppBackgrounded(packageName)
    }

    /**
     * MainActivity is exported, so the notification extra alone proves nothing: the launch only skips
     * the entry lock when it carries the secret token issued with our own notification. The token is
     * checked once per delivered intent and then removed from it.
     */
    private fun verifyNotificationLaunch(launchIntent: Intent?) {
        notificationLaunchVerified = try {
            val token = launchIntent?.getStringExtra(NotificationLaunchToken.EXTRA_TOKEN)
            NotificationLaunchToken.consume(applicationContext, token)
        } catch (_: Exception) {
            false
        }
        try {
            launchIntent?.removeExtra(NotificationLaunchToken.EXTRA_TOKEN)
        } catch (_: Exception) {
        }
    }

    private fun observeEntryLockOverlay() {
        if (overlayEventsJob?.isActive == true) return
        overlayEventsJob = lifecycleScope.launch {
            LockOverlayEvents.events.collect { event ->
                if (event.packageName != packageName || !awaitingEntryUnlock) return@collect
                when (event) {
                    is LockOverlayEvent.Unlocked -> {
                        awaitingEntryUnlock = false
                        stealthModeManager.setStealthMode(false, this@MainActivity)
                        AppEntryLockActivity.markAuthenticated()
                        showMainContent()
                    }
                    is LockOverlayEvent.Cancelled -> {
                        awaitingEntryUnlock = false
                        finish()
                    }
                }
            }
        }
    }

    private fun showMainContent() {
        if (isContentShown) return
        isContentShown = true
        hasSetContent = true

        // Store locked package from LockService for re-locking on exit
        if (storedLockedPackage == null) {
            storedLockedPackage = intent?.getStringExtra(com.carbon.prolocker.feature.lock.LockService.EXTRA_PACKAGE_NAME)
        }

        enableEdgeToEdge()
        val prefs = preferencesRepository.currentPreferences
        val isOnboardingCompleted = prefs.onboardingCompleted && prefs.lockType != "NONE"

        // Snapshot deep-link state, then clear it so a re-inflated navigation (e.g. after the
        // activity is recreated) does not re-apply a stale destination.
        val deepLink = pendingNavType
        val trustedDest = trustedInternalDestination ?: trustedLaunchDestination
        pendingNavType = null
        trustedInternalDestination = null
        trustedLaunchDestination = null

        setContent {
            val currentPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = currentPrefs?.isDarkMode ?: true
            val rawLang = currentPrefs?.language
            val language = languageManager.getEffectiveLanguageTag(rawLang)
            val layoutDirection = if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val localizedContext = androidx.compose.runtime.remember(baseContext, language) {
                languageManager.createLocalizedContext(baseContext, language)
            }

            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalContext provides localizedContext,
                    androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity,
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            deepLinkType = deepLink,
                            isOnboardingCompleted = isOnboardingCompleted,
                            trustedLaunchDestination = trustedDest,
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
            adManager.requestConsentAndInit(this@MainActivity)

            // Preload lock screen ads so they're ready when LockService needs them
            try {
                val lockPlace = remoteConfigRepository.getLockScreenAdPlace()
                val topAdType = if (lockPlace == "topBanner") NativeAdType.TYPE_4 else NativeAdType.TYPE_2
                adManager.preloadNativeAd(this@MainActivity, AdPlacement.LOCKSCREEN_TOP, topAdType)
                adManager.preloadNativeAd(this@MainActivity, AdPlacement.LOCKSCREEN_BOTTOM, NativeAdType.TYPE_3)
            } catch (_: Exception) {
                // Ad preloading is best-effort
            }
        }
    }
}
