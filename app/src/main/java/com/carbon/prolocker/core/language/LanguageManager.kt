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

    companion object {
        const val TAG = "MoslemProLocker"

        fun getAppLocaleTag(): String? {
            return try {
                val appLocales = AppCompatDelegate.getApplicationLocales()
                if (!appLocales.isEmpty) {
                    appLocales.get(0)?.language?.takeIf { it.isNotEmpty() }
                } else null
            } catch (_: Exception) {
                null
            }
        }

        fun resolveEffectiveLanguage(repo: PreferencesRepository?, newBase: Context? = null): String {
            val lang = try {
                repo?.currentPreferences?.language?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
            if (lang != null) return lang

            val appLocale = getAppLocaleTag()
            if (appLocale != null) return appLocale

            val baseLocale = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    newBase?.resources?.configuration?.locales?.get(0)?.language?.takeIf { it.isNotEmpty() }
                } else {
                    @Suppress("DEPRECATION")
                    newBase?.resources?.configuration?.locale?.language?.takeIf { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                null
            }
            if (baseLocale == "fa" || baseLocale == "en") {
                return baseLocale
            }

            return if (MarketConfig.isGooglePlay) "en" else "fa"
        }

        fun createLocalizedContextStatic(baseContext: Context, languageTag: String): Context {
            val locale = Locale(languageTag)
            Locale.setDefault(locale)

            val config = Configuration(baseContext.resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            config.setLayoutDirection(locale)

            val configContext = baseContext.createConfigurationContext(config)
            return LocalizedContextWrapper(baseContext, configContext)
        }
    }

    fun getEffectiveLanguageTag(rawLanguage: String? = null): String {
        if (!rawLanguage.isNullOrEmpty()) {
            return rawLanguage
        }
        val lang = try {
            preferencesRepository.currentPreferences.language.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        if (lang != null) {
            return lang
        }
        val appLocale = getAppLocaleTag()
        if (appLocale != null) {
            return appLocale
        }
        return if (MarketConfig.isGooglePlay) "en" else "fa"
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
        return createLocalizedContextStatic(baseContext, languageTag)
    }

    /**
     * Returns the current app language tag from preferences, falling back to flavor default.
     */
    fun getCurrentLanguageTag(): String {
        return getEffectiveLanguageTag()
    }
}

tailrec fun Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

