package com.carbon.prolocker.feature.restricted

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: "backgrounds"
        RestrictedModeManager.enterRestrictedMode(destination)

        val prefs = runBlocking { preferencesRepository.userPreferencesFlow.first() }
        val isDarkMode = prefs.isDarkMode
        val language = prefs.language
        val layoutDirection = if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

        enableEdgeToEdge()

        setContent {
            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        RestrictedNavigation(destination = destination)
                    }
                }
            }
        }

        languageManager.applyLanguage(language)
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
