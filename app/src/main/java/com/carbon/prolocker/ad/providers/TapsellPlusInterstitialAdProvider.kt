package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import com.carbon.prolocker.ad.InterstitialAdProvider
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState

class TapsellPlusInterstitialAdProvider(override val providerName: String = "tapsell") :
    InterstitialAdProvider {

    companion object {
        private var isInitialized = false
    }

    override fun initSdk(context: Context) {
        try {
            Tapsell.setInitializationListener {
                isInitialized = true
            }
        } catch (_e: Exception) {
        }
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        Tapsell.requestInterstitialAd(zoneId, object : RequestResultListener {
            override fun onSuccess(adId: String) {

                Tapsell.showInterstitialAd(adId, activity, object : AdStateListener.Interstitial {
                    override fun onAdImpression() {}

                    override fun onAdClosed(@NonNull adShowCompletionState: AdShowCompletionState) {
                        onClosed()
                    }

                    override fun onAdClicked() {}

                    override fun onAdFailed(@NonNull message: String) {}
                })

            }

            override fun onFailure(message: String) {

            }
        })
    }
}
