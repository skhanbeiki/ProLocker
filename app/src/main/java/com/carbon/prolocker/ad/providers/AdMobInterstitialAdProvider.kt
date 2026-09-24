package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import android.util.Log
import com.carbon.prolocker.ad.InterstitialAdProvider
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdMobInterstitialAdProvider(override val providerName: String = "admob") : InterstitialAdProvider {

    companion object {
        private const val TAG = "AdMobInterstitialAd"
        private var isInitialized = false
    }

    @Volatile
    private var cachedAd: InterstitialAd? = null
    @Volatile
    private var cachedZoneId: String? = null

    override fun resetShowingState() {
        Log.i(TAG, "Resetting AdMob cache state")
        cachedAd = null
        cachedZoneId = null
    }

    override fun initSdk(context: Context) {
        if (isInitialized) return
        try {
            com.google.android.gms.ads.MobileAds.initialize(context) { status ->
                Log.d(TAG, "SDK initialized, status=$status")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AdMob SDK", e)
        }
    }

    override fun preloadInterstitialAd(context: Context, zoneId: String) {
        if (zoneId.isBlank()) return
        initSdk(context)
        if (isInterstitialAdReady(zoneId)) return

        InterstitialAd.load(
            context,
            zoneId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cachedAd = ad
                    cachedZoneId = zoneId
                    Log.d(TAG, "AdMob interstitial preloaded successfully for zone: $zoneId")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "AdMob interstitial preload failed: ${error.message}")
                }
            }
        )
    }

    override fun isInterstitialAdReady(zoneId: String): Boolean {
        return cachedZoneId == zoneId && cachedAd != null
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        val readyAd = if (isInterstitialAdReady(zoneId)) cachedAd else null
        cachedAd = null
        cachedZoneId = null

        if (readyAd != null) {
            showAdMobAd(activity, readyAd, onClosed, onError)
            return
        }

        Log.d(TAG, "INTERSTITIAL_REQUEST_START zoneId=$zoneId provider=$providerName")

        InterstitialAd.load(
            activity,
            zoneId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "INTERSTITIAL_REQUEST_SUCCESS provider=$providerName")
                    showAdMobAd(activity, ad, onClosed, onError)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "INTERSTITIAL_REQUEST_FAILED provider=$providerName error=${error.message}")
                    onError(error.message ?: "Unknown AdMob error")
                }
            }
        )
    }

    private fun showAdMobAd(
        activity: Activity,
        ad: InterstitialAd,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "INTERSTITIAL_CLOSED provider=$providerName")
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "INTERSTITIAL_FAILED provider=$providerName error=${error.message}")
                onError(error.message ?: "AdMob interstitial show error")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "INTERSTITIAL_SHOWN provider=$providerName")
            }

            override fun onAdImpression() {
                Log.d(TAG, "INTERSTITIAL_IMPRESSION provider=$providerName")
            }

            override fun onAdClicked() {
                Log.d(TAG, "INTERSTITIAL_CLICKED provider=$providerName")
            }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Exception showing AdMob ad", e)
            onError(e.message ?: "Failed to show AdMob ad")
        }
    }
}
