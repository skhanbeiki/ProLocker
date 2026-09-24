package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener
import com.carbon.prolocker.ad.AdUnitIds
import com.carbon.prolocker.ad.InterstitialAdProvider

import android.os.Handler
import android.os.Looper
import android.util.Log

class AdiveryInterstitialAdProvider(override val providerName: String = "adivery") :
    InterstitialAdProvider {

    companion object {
        private const val TAG = "AdiveryInterstitialAd"
        private var isInitialized = false
    }

    @Volatile
    private var isShowingAd = false

    private var currentListener: AdiveryListener? = null
    private val handler = Handler(Looper.getMainLooper())

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

    override fun resetShowingState() {
        Log.i(TAG, "Resetting Adivery isShowingAd state")
        isShowingAd = false
        currentListener?.let { Adivery.removeGlobalListener(it) }
        currentListener = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun preloadInterstitialAd(context: Context, zoneId: String) {
        if (zoneId.isBlank()) return
        val appContext = context.applicationContext
        initSdk(appContext)
        try {
            if (!Adivery.isLoaded(zoneId)) {
                Adivery.prepareInterstitialAd(appContext, zoneId)
            }
        } catch (_: Exception) {
        }
    }

    override fun isInterstitialAdReady(zoneId: String): Boolean {
        if (zoneId.isBlank()) return false
        return try {
            Adivery.isLoaded(zoneId)
        } catch (_: Exception) {
            false
        }
    }

    override fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (zoneId.isBlank()) {
            onError("Zone ID empty")
            return
        }

        currentListener?.let { Adivery.removeGlobalListener(it) }
        handler.removeCallbacksAndMessages(null)

        if (Adivery.isLoaded(zoneId)) {
            isShowingAd = true
            val listener = object : AdiveryListener() {
                override fun onInterstitialAdClosed(placementId: String) {
                    if (placementId != zoneId) return
                    isShowingAd = false
                    currentListener?.let { Adivery.removeGlobalListener(it) }
                    currentListener = null
                    onClosed()
                }
            }
            currentListener = listener
            Adivery.addGlobalListener(listener)
            try {
                Adivery.showAd(zoneId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show loaded Adivery ad", e)
                isShowingAd = false
                currentListener?.let { Adivery.removeGlobalListener(it) }
                currentListener = null
                onError(e.message ?: "Failed to show Adivery ad")
            }
            return
        }

        isShowingAd = true
        var isCompleted = false

        val timeoutRunnable = Runnable {
            if (!isCompleted) {
                isCompleted = true
                Log.w(TAG, "Adivery ad preparation timed out after 4s")
                isShowingAd = false
                currentListener?.let { Adivery.removeGlobalListener(it) }
                currentListener = null
                onError("Adivery ad load timeout")
            }
        }
        handler.postDelayed(timeoutRunnable, 4000)

        val listener = object : AdiveryListener() {
            override fun onInterstitialAdLoaded(placementId: String) {
                if (placementId != zoneId || isCompleted) return
                handler.removeCallbacks(timeoutRunnable)
                if (activity.isFinishing || activity.isDestroyed) {
                    isCompleted = true
                    isShowingAd = false
                    currentListener?.let { Adivery.removeGlobalListener(it) }
                    currentListener = null
                    onError("Activity destroyed before showing ad")
                    return
                }
                try {
                    if (Adivery.isLoaded(placementId)) {
                        Adivery.showAd(placementId)
                    } else {
                        isCompleted = true
                        isShowingAd = false
                        currentListener?.let { Adivery.removeGlobalListener(it) }
                        currentListener = null
                        onError("Adivery ad not ready after load")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing newly loaded Adivery ad", e)
                    isCompleted = true
                    isShowingAd = false
                    currentListener?.let { Adivery.removeGlobalListener(it) }
                    currentListener = null
                    onError(e.message ?: "Failed to show Adivery ad")
                }
            }

            override fun onInterstitialAdClosed(placementId: String) {
                if (placementId != zoneId) return
                if (!isCompleted) {
                    isCompleted = true
                    handler.removeCallbacks(timeoutRunnable)
                    isShowingAd = false
                    currentListener?.let { Adivery.removeGlobalListener(it) }
                    currentListener = null
                    onClosed()
                }
            }
        }

        currentListener = listener
        Adivery.addGlobalListener(listener)
        try {
            Adivery.prepareInterstitialAd(activity.applicationContext, zoneId)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing Adivery interstitial ad", e)
            handler.removeCallbacks(timeoutRunnable)
            isShowingAd = false
            currentListener?.let { Adivery.removeGlobalListener(it) }
            currentListener = null
            onError(e.message ?: "Error preparing Adivery ad")
        }
    }
}
