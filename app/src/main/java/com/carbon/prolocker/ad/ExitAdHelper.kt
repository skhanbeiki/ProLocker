package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun triggerExitInterstitialAd(
    context: Context,
    coroutineScope: CoroutineScope,
    preferencesRepository: PreferencesRepository,
    remoteConfigRepository: RemoteConfigRepository,
    adManager: AdManager,
    placement: String,
    onBack: () -> Unit
) {
    val activity = context as? Activity ?: run {
        onBack()
        return
    }

    coroutineScope.launch {
        try {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val config = remoteConfigRepository.getConfig()
            val limit = config.configs.interstitialAdThemeStep

            if (limit > 0) {
                val currentCount = prefs.themeInterstitialCounter + 1
                if (currentCount >= limit) {
                    preferencesRepository.updateThemeInterstitialCounter(false)
                    adManager.showInterstitialAd(
                        activity = activity,
                        placement = placement,
                        onClosed = { onBack() },
                        onError = { onBack() }
                    )
                } else {
                    preferencesRepository.updateThemeInterstitialCounter(true)
                    onBack()
                }
            } else {
                onBack()
            }
        } catch (e: Exception) {
            onBack()
        }
    }
}
