package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import android.util.Log
import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.ad.AdUnitIds
import com.carbon.prolocker.ad.InterstitialAdProvider
import com.hasin.magnetad.AdConfig
import com.hasin.magnetad.AdError
import com.hasin.magnetad.AdListener
import com.hasin.magnetad.AdLoadResult
import com.hasin.magnetad.MagnetAdManager

class MagnetInterstitialAdProvider(override val providerName: String = "magnet") : InterstitialAdProvider {

    companion object {
        private const val TAG = "MagnetInterstitial"
        @Volatile
        private var isInitialized = false
    }

    @Volatile
    private var isShowingAd = false

    override fun initSdk(context: Context) {
        if (isInitialized) return
        val appId = AdUnitIds.Magnet.APP_ID
        if (appId.isBlank()) {
            Log.w(TAG, "Magnet APP_ID is empty. Skipping initSdk.")
            return
        }
        try {
            val app = context.applicationContext as? android.app.Application ?: return
            MagnetAdManager.initialize(
                application = app,
                config = AdConfig(
                    appId = appId,
                    debugMode = BuildConfig.DEBUG,
                    videoVolume = 1f
                ),
                onInitComplete = { success ->
                    isInitialized = success
                    Log.i(TAG, "MagnetAdManager init complete. success=$success")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "MagnetAdManager initialization error", e)
        }
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isShowingAd) {
            Log.w(TAG, "Ad is already showing or loading.")
            onError("Ad already showing")
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing or destroyed.")
            onError("Activity unavailable")
            return
        }

        if (zoneId.isBlank()) {
            Log.w(TAG, "Magnet placementId (zoneId) is empty.")
            onError("Zone ID empty")
            return
        }

        if (!MagnetAdManager.isInitialized()) {
            initSdk(activity)
            if (!MagnetAdManager.isInitialized()) {
                Log.w(TAG, "Magnet SDK not initialized.")
                onError("Magnet SDK not initialized")
                return
            }
        }

        isShowingAd = true

        try {
            val interstitialAd = MagnetAdManager.getInterstitialAd(placementId = zoneId)
            var isCallbackDone = false

            fun finishWithClosed() {
                if (!isCallbackDone) {
                    isCallbackDone = true
                    isShowingAd = false
                    try {
                        interstitialAd.destroy()
                    } catch (_: Exception) {}
                    onClosed()
                }
            }

            fun finishWithError(err: String) {
                if (!isCallbackDone) {
                    isCallbackDone = true
                    isShowingAd = false
                    try {
                        interstitialAd.destroy()
                    } catch (_: Exception) {}
                    onError(err)
                }
            }

            interstitialAd.setAdListener(object : AdListener() {
                override fun onAdShown() {
                    Log.i(TAG, "Magnet interstitial ad shown: $zoneId")
                }

                override fun onAdClicked() {
                    Log.i(TAG, "Magnet interstitial ad clicked: $zoneId")
                }

                override fun onAdDismissed() {
                    Log.i(TAG, "Magnet interstitial ad dismissed: $zoneId")
                    finishWithClosed()
                }

                override fun onAdFailedToShow(error: AdError) {
                    Log.e(TAG, "Magnet interstitial ad failed to show: ${error.code} - ${error.message}")
                    finishWithError(error.message)
                }
            })

            interstitialAd.requestAd(activity) { result ->
                when (result) {
                    is AdLoadResult.Success -> {
                        if (activity.isFinishing || activity.isDestroyed) {
                            finishWithError("Activity destroyed before show")
                            return@requestAd
                        }
                        Log.i(TAG, "Magnet interstitial ad loaded successfully. Showing adId: ${result.adId}")
                        interstitialAd.show(activity, result.adId)
                    }
                    is AdLoadResult.Failure -> {
                        Log.e(TAG, "Magnet interstitial ad load failed: ${result.error.code} - ${result.error.message}")
                        finishWithError(result.error.message)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Magnet interstitial ad", e)
            isShowingAd = false
            onError(e.message ?: "Unknown Magnet error")
        }
    }
}
