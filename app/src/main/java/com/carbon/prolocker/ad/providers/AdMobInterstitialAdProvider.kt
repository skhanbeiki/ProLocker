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
        private const val MAX_CACHE_AGE_MS = 50 * 60 * 1000L
    }

    @Volatile
    private var cachedAd: InterstitialAd? = null
    @Volatile
    private var cachedZoneId: String? = null
    @Volatile
    private var cachedAt: Long = 0L
    @Volatile
    private var isPreloading = false

    // A preloaded ad stays valid across shows (until it expires), so nothing is dropped here.
    override fun resetShowingState() {}

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
        if (isInterstitialAdReady(zoneId) || isPreloading) return
        isPreloading = true

        InterstitialAd.load(
            context,
            zoneId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isPreloading = false
                    cachedAd = ad
                    cachedZoneId = zoneId
                    cachedAt = System.currentTimeMillis()
                    Log.d(TAG, "AdMob interstitial preloaded successfully for zone: $zoneId")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isPreloading = false
                    Log.w(TAG, "AdMob interstitial preload failed: ${error.message}")
                }
            }
        )
    }

    override fun isInterstitialAdReady(zoneId: String): Boolean {
        // AdMob interstitials expire after an hour; showing an expired one fails silently.
        if (cachedAd != null && System.currentTimeMillis() - cachedAt > MAX_CACHE_AGE_MS) {
            cachedAd = null
            cachedZoneId = null
        }
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

        // Without a timeout a slow load would pop the ad up seconds later, over another screen.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var isCompleted = false
        val timeoutRunnable = Runnable {
            if (!isCompleted) {
                isCompleted = true
                Log.w(TAG, "AdMob interstitial request timed out after 4s")
                onError("AdMob ad request timeout")
            }
        }
        handler.postDelayed(timeoutRunnable, 4000)

        InterstitialAd.load(
            activity,
            zoneId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (isCompleted) {
                        // Too late to show now: keep it for the next opportunity.
                        cachedAd = ad
                        cachedZoneId = zoneId
                        cachedAt = System.currentTimeMillis()
                        return
                    }
                    isCompleted = true
                    handler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "INTERSTITIAL_REQUEST_SUCCESS provider=$providerName")
                    if (activity.isFinishing || activity.isDestroyed) {
                        cachedAd = ad
                        cachedZoneId = zoneId
                        cachedAt = System.currentTimeMillis()
                        onError("Activity destroyed before showing AdMob ad")
                        return
                    }
                    showAdMobAd(activity, ad, onClosed, onError)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (isCompleted) return
                    isCompleted = true
                    handler.removeCallbacks(timeoutRunnable)
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
