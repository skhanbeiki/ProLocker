package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener
import com.carbon.prolocker.ad.AdUnitIds
import com.carbon.prolocker.ad.InterstitialAdProvider

class AdiveryInterstitialAdProvider(override val providerName: String = "adivery") :
    InterstitialAdProvider {

    companion object {
        private var isInitialized = false
    }

    @Volatile
    private var isShowingAd = false

    private var currentListener: AdiveryListener? = null

    override fun initSdk(context: Context) {
        if (isInitialized) return
        try {
            Adivery.configure(
                context.applicationContext as android.app.Application,
                AdUnitIds.Adivery.APP_ID
            )
            isInitialized = true
        } catch (_: Exception) {
        }
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isShowingAd) {
            return
        }

        currentListener?.let { Adivery.removeGlobalListener(it) }

        isShowingAd = true

        val listener = object : AdiveryListener() {
            override fun onInterstitialAdLoaded(placementId: String) {
                if (placementId != zoneId) return@onInterstitialAdLoaded
                if (!isShowingAd) return@onInterstitialAdLoaded
                if (Adivery.isLoaded(placementId)) {
                    Adivery.showAd(placementId)
                }
            }

            override fun onInterstitialAdShown(placementId: String) {
            }

            override fun onInterstitialAdClicked(placementId: String) {
            }

            override fun onInterstitialAdClosed(placementId: String) {
                if (placementId != zoneId) return@onInterstitialAdClosed
                isShowingAd = false
                currentListener?.let { Adivery.removeGlobalListener(it) }
                currentListener = null
                onClosed()
            }
        }

        currentListener = listener
        Adivery.addGlobalListener(listener)
        Adivery.prepareInterstitialAd(activity, zoneId)
    }
}
