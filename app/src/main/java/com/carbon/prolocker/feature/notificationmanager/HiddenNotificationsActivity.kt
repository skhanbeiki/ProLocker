package com.carbon.prolocker.feature.notificationmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import org.koin.android.ext.android.inject

/**
 * Opened from the "n hidden notifications" summary. Shows the app lock first, then the
 * notification hider, so the kept notifications are never readable without unlocking.
 */
class HiddenNotificationsActivity : ComponentActivity() {

    private val preferencesRepository: PreferencesRepository by inject()
    private val languageManager: LanguageManager by inject()

    private var authorized by mutableStateOf(false)
    private var awaitingAuth = false

    private val entryLockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        awaitingAuth = false
        if (result.resultCode == RESULT_OK) {
            AppEntryLockActivity.markAuthenticated()
            authorized = true
        } else {
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val repo = try {
            org.koin.java.KoinJavaComponent.get<PreferencesRepository>(PreferencesRepository::class.java)
        } catch (_: Exception) {
            null
        }
        val effective = LanguageManager.resolveEffectiveLanguage(repo, newBase)
        super.attachBaseContext(LanguageManager.createLocalizedContextStatic(newBase, effective))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Screenshots stay blocked in release builds only, so debug builds can be captured for testing.
        if (!com.carbon.prolocker.BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            val prefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = prefs?.isDarkMode ?: true
            val language = languageManager.getEffectiveLanguageTag(prefs?.language)
            val layoutDirection = languageManager.getLayoutDirection(language)
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, language) {
                languageManager.createLocalizedContext(baseContext, language)
            }
            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (authorized) {
                            NotificationManagerScreen(onBack = { finish() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingAuth) return
        if (needsLock()) {
            if (TrustedReturnManager.consumeTrustedReturn()) {
                // Back from the notification access settings we opened ourselves.
                AppEntryLockActivity.markAuthenticated()
                authorized = true
                return
            }
            authorized = false
            awaitingAuth = true
            entryLockLauncher.launch(Intent(this, AppEntryLockActivity::class.java))
        } else {
            authorized = true
        }
    }

    private fun needsLock(): Boolean {
        if (!AppEntryLockActivity.requiresAuthentication()) return false
        val prefs = preferencesRepository.currentPreferences
        return prefs.onboardingCompleted && prefs.lockType != "NONE"
    }
}
