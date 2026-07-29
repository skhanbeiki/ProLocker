package com.carbon.prolocker.core.language

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

class LanguageManager(private val preferencesRepository: PreferencesRepository) {

    private companion object {
        const val TAG = "Moslemprolocker"
    }

    fun setLanguage(languageTag: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun applyLanguage(languageTag: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /**
     * Creates a Context whose resources resolve strings in the currently selected app language.
     * Use this instead of the raw Service/Worker context when building notifications,
     * because Services and Workers may not have the AppCompat locale applied.
     */
    fun createLocalizedContext(baseContext: Context): Context {
        val languageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "createLocalizedContext: failed to read language preference", e)
            ""
        }

        val appLocales = LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()

        val config = Configuration(baseContext.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))

        val localizedContext = baseContext.createConfigurationContext(config)

        Log.d(TAG, "NOTIFICATION_LANGUAGE createLocalizedContext: languageTag=$languageTag, locale=$locale, base=${baseContext.resources.configuration.locales[0]}")

        return localizedContext
    }

    /**
     * Returns the current app language tag from preferences, falling back to system default.
     */
    fun getCurrentLanguageTag(): String {
        return try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLanguageTag: failed to read language preference", e)
            Locale.getDefault().language
        }
    }
}
