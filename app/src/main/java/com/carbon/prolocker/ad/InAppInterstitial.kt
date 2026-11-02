package com.carbon.prolocker.ad

import android.content.Context
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

/**
 * Interstitials for natural breaks in a flow (leaving the private browser, after picking apps to
 * hide...). They share the app's in-app counter, so the user sees one every
 * `interstitialAdInAppStep` breaks, never on every action.
 */
object InAppInterstitial {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun preload(context: Context, placement: String) {
        try {
            KoinJavaComponent.get<AdManager>(AdManager::class.java)
                .preloadInterstitialAd(context.applicationContext, placement)
        } catch (_: Exception) {
        }
    }

    /** Shows the ad when the counter is due. [onDone] always runs exactly once, after the ad if shown. */
    fun maybeShow(context: Context, placement: String, onDone: () -> Unit = {}) {
        var finished = false
        val finish: () -> Unit = {
            if (!finished) {
                finished = true
                onDone()
            }
        }
        scope.launch {
            try {
                val adManager = KoinJavaComponent.get<AdManager>(AdManager::class.java)
                val preferences = KoinJavaComponent.get<PreferencesRepository>(PreferencesRepository::class.java)
                val remoteConfig = KoinJavaComponent.get<RemoteConfigRepository>(RemoteConfigRepository::class.java)
                val step = remoteConfig.cachedConfig().configs.interstitialAdInAppStep
                if (step > 0 && adManager.areAdsEnabled()) {
                    val count = preferences.currentPreferences.inAppInterstitialCounter + 1
                    if (count >= step) {
                        preferences.updateInAppInterstitialCounter(false)
                        adManager.showInterstitialAd(
                            context = context,
                            placement = placement,
                            onClosed = finish,
                            onError = { finish() }
                        )
                        return@launch
                    } else {
                        preferences.updateInAppInterstitialCounter(true)
                    }
                }
            } catch (_: Exception) {
            }
            finish()
        }
    }
}
