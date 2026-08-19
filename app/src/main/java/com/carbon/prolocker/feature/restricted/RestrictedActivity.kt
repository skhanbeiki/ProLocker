package com.carbon.prolocker.feature.restricted

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.navigation.RestrictedModeManager
import com.carbon.prolocker.core.navigation.RestrictedNavigation
import com.carbon.prolocker.core.theme.ProLockerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class RestrictedActivity : ComponentActivity() {

    private val preferencesRepository: PreferencesRepository by inject()
    private val languageManager: LanguageManager by inject()

    override fun attachBaseContext(newBase: android.content.Context) {
        val repo = try {
            org.koin.java.KoinJavaComponent.get<com.carbon.prolocker.core.datastore.PreferencesRepository>(com.carbon.prolocker.core.datastore.PreferencesRepository::class.java)
        } catch (_: Exception) {
            null
        }
        val lang = try {
            repo?.currentPreferences?.language
        } catch (_: Exception) {
            null
        }
        val effective = if (!lang.isNullOrEmpty()) lang else if (com.carbon.prolocker.core.config.MarketConfig.isGooglePlay) "en" else "fa"
        val localizedContext = com.carbon.prolocker.core.language.LanguageManager.createLocalizedContextStatic(newBase, effective)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: "backgrounds"
        RestrictedModeManager.enterRestrictedMode(destination)

        enableEdgeToEdge()

        setContent {
            val currentPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = currentPrefs?.isDarkMode ?: true
            val language = languageManager.getEffectiveLanguageTag(currentPrefs?.language)
            val layoutDirection = languageManager.getLayoutDirection(language)
            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val localizedContext = androidx.compose.runtime.remember(baseContext, language) {
                languageManager.createLocalizedContext(baseContext, language)
            }

            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalContext provides localizedContext,
                    androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides this@RestrictedActivity,
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        RestrictedNavigation(destination = destination)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            RestrictedModeManager.exitRestrictedMode()
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "extra_restricted_destination"
    }
}
