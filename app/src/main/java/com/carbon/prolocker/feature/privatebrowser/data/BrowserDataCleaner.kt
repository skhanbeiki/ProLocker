package com.carbon.prolocker.feature.privatebrowser.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class CleanupStep { HISTORY, COOKIES, CACHE, SITE_DATA, TABS }

/** Wipes browser data. All functions touch WebView APIs and must run on the main thread. */
object BrowserDataCleaner {

    fun exitSteps(settings: BrowserSettings): List<CleanupStep> = buildList {
        if (settings.clearHistoryOnExit) add(CleanupStep.HISTORY)
        if (settings.clearCookiesOnExit) add(CleanupStep.COOKIES)
        if (settings.clearCacheOnExit) add(CleanupStep.CACHE)
        if (settings.clearSiteDataOnExit) add(CleanupStep.SITE_DATA)
        if (settings.closeTabsOnExit) add(CleanupStep.TABS)
    }

    val manualSteps: List<CleanupStep> =
        listOf(CleanupStep.HISTORY, CleanupStep.COOKIES, CleanupStep.CACHE, CleanupStep.SITE_DATA)

    suspend fun run(step: CleanupStep, context: Context, repository: BrowserRepository) {
        if (step == CleanupStep.COOKIES) {
            withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    CookieManager.getInstance().removeAllCookies { _ ->
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            CookieManager.getInstance().flush()
        } else {
            runNow(step, context, repository)
        }
    }

    /** Silent, synchronous variant used when the browser closes without the cleanup screen. */
    fun runAllNow(steps: List<CleanupStep>, context: Context, repository: BrowserRepository) {
        steps.forEach { step ->
            try {
                runNow(step, context, repository)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun runNow(step: CleanupStep, context: Context, repository: BrowserRepository) {
        when (step) {
            CleanupStep.HISTORY -> {
                repository.clearHistory()
                try {
                    val db = WebViewDatabase.getInstance(context)
                    db.clearFormData()
                    db.clearHttpAuthUsernamePassword()
                } catch (_: Exception) {
                }
            }

            CleanupStep.COOKIES -> {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }

            CleanupStep.CACHE -> {
                try {
                    val webView = WebView(context.applicationContext)
                    webView.clearCache(true)
                    webView.clearFormData()
                    webView.destroy()
                } catch (_: Exception) {
                }
            }

            CleanupStep.SITE_DATA -> {
                WebStorage.getInstance().deleteAllData()
                GeolocationPermissions.getInstance().clearAll()
            }

            CleanupStep.TABS -> repository.clearSavedTabs()
        }
    }
}
