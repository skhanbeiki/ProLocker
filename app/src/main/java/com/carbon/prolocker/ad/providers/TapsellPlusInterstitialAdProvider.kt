package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import com.carbon.prolocker.ad.InterstitialAdProvider
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState

import android.os.Handler
import android.os.Looper
import android.util.Log

class TapsellPlusInterstitialAdProvider(override val providerName: String = "tapsell") :
    InterstitialAdProvider {

    companion object {
        private const val TAG = "TapsellInterstitial"
        private var isInitialized = false
    }

    @Volatile
    private var cachedAdId: String? = null
    @Volatile
    private var cachedZoneId: String? = null

    override fun initSdk(context: Context) {
        try {
            Tapsell.setInitializationListener {
                isInitialized = true
            }
        } catch (_e: Exception) {
        }
    }

    override fun resetShowingState() {
        Log.i(TAG, "Resetting Tapsell cache state")
        cachedAdId = null
        cachedZoneId = null
    }

    override fun preloadInterstitialAd(context: Context, zoneId: String) {
        if (zoneId.isBlank()) return
        val appContext = context.applicationContext
        initSdk(appContext)
        if (isInterstitialAdReady(zoneId)) return

        try {
            Tapsell.requestInterstitialAd(zoneId, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    cachedAdId = adId
                    cachedZoneId = zoneId
                    Log.i(TAG, "Tapsell interstitial ad preloaded: $adId")
                }

                override fun onFailure(message: String) {
                    Log.w(TAG, "Tapsell preload failed: $message")
                    cachedAdId = null
                    cachedZoneId = null
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading Tapsell ad", e)
        }
    }

    override fun isInterstitialAdReady(zoneId: String): Boolean {
        return cachedZoneId == zoneId && cachedAdId != null
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        val readyAdId = if (isInterstitialAdReady(zoneId)) cachedAdId else null
        cachedAdId = null
        cachedZoneId = null

        if (readyAdId != null) {
            showTapsellAd(activity, readyAdId, onClosed, onError)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var isCompleted = false

        val timeoutRunnable = Runnable {
            if (!isCompleted) {
                isCompleted = true
                Log.w(TAG, "Tapsell ad request timed out after 4s")
                onError("Tapsell ad request timeout")
            }
        }
        handler.postDelayed(timeoutRunnable, 4000)

        try {
            Tapsell.requestInterstitialAd(zoneId, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (isCompleted) return
                    isCompleted = true
                    handler.removeCallbacks(timeoutRunnable)
                    showTapsellAd(activity, adId, onClosed, onError)
                }

                override fun onFailure(message: String) {
                    if (isCompleted) return
                    isCompleted = true
                    handler.removeCallbacks(timeoutRunnable)
                    onError(message)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Tapsell ad", e)
            handler.removeCallbacks(timeoutRunnable)
            onError(e.message ?: "Failed to request Tapsell ad")
        }
    }

    private fun showTapsellAd(
        activity: Activity,
        adId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("Activity destroyed before showing Tapsell ad")
            return
        }
        try {
            Tapsell.showInterstitialAd(adId, activity, object : AdStateListener.Interstitial {
                override fun onAdImpression() {}

                override fun onAdClosed(@NonNull adShowCompletionState: AdShowCompletionState) {
                    onClosed()
                }

                override fun onAdClicked() {}

                override fun onAdFailed(@NonNull message: String) {
                    onError(message)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception showing Tapsell ad", e)
            onError(e.message ?: "Failed to show Tapsell ad")
        }
    }
}
