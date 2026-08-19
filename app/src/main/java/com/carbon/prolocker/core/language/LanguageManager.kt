package com.carbon.prolocker.core.language

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.core.config.MarketConfig
import com.carbon.prolocker.core.datastore.PreferencesRepository
import java.util.Locale

class LocalizedContextWrapper(
    base: Context,
    private val localizedContext: Context
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedContext.resources
    override fun getAssets(): AssetManager = localizedContext.assets
}

class LanguageManager(private val preferencesRepository: PreferencesRepository) {

    private companion object {
        const val TAG = "Moslemprolocker"
    }

    fun getEffectiveLanguageTag(rawLanguage: String? = null): String {
        if (!rawLanguage.isNullOrEmpty()) {
            return rawLanguage
        }
        val lang = try {
            preferencesRepository.currentPreferences.language
        } catch (_: Exception) {
            null
        }
        return if (!lang.isNullOrEmpty()) {
            lang
        } else {
            if (MarketConfig.isGooglePlay) "en" else "fa"
        }
    }

    fun getLayoutDirection(languageTag: String): LayoutDirection {
        return if (languageTag == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    fun setLanguage(languageTag: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun applyLanguage(languageTag: String) {
        val effective = getEffectiveLanguageTag(languageTag)
        val appLocale = LocaleListCompat.forLanguageTags(effective)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /**
     * Creates a ContextWrapper preserving the base Activity/Service context while resolving resources in the selected language.
     */
    fun createLocalizedContext(baseContext: Context, overrideLanguage: String? = null): Context {
        val languageTag = getEffectiveLanguageTag(overrideLanguage)
        val locale = Locale(languageTag)
        Locale.setDefault(locale)

        val config = Configuration(baseContext.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        config.setLayoutDirection(locale)

        val configContext = baseContext.createConfigurationContext(config)
        return LocalizedContextWrapper(baseContext, configContext)
    }

    /**
     * Returns the current app language tag from preferences, falling back to flavor default.
     */
    fun getCurrentLanguageTag(): String {
        return getEffectiveLanguageTag()
    }
}
