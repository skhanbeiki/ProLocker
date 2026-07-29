package com.carbon.prolocker.ad.providers

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.carbon.prolocker.ad.NativeAdProvider
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

class AdMobNativeAdProvider(override val providerName: String = "admob") : NativeAdProvider {

    companion object {
        private const val TAG = "AdMobNativeAd"
        private var isInitialized = false
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

    override fun loadNativeAd(
        context: Context,
        zoneId: String,
        container: ViewGroup,
        @LayoutRes layoutRes: Int,
        onRendered: (View) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "ADS_REQUEST_START zoneId=$zoneId provider=$providerName layoutRes=$layoutRes")

        val adLoader = AdLoader.Builder(context, zoneId)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "ADS_REQUEST_SUCCESS provider=$providerName")
                val adView = renderNativeAd(context, nativeAd, container, layoutRes)
                onRendered(adView)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "ADS_REQUEST_FAILED provider=$providerName error=${error.message}")
                    onError(error.message)
                }
                override fun onAdImpression() {
                    Log.d(TAG, "ADS_IMPRESSION provider=$providerName")
                }
                override fun onAdClicked() {
                    Log.d(TAG, "ADS_CLICKED provider=$providerName")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun renderNativeAd(context: Context, nativeAd: NativeAd, container: ViewGroup, @LayoutRes layoutRes: Int): NativeAdView {
        val adView = LayoutInflater.from(context).inflate(layoutRes, container, false) as NativeAdView

//        adView.headlineView = adView.findViewById(R.id.tvAdTitle)
//        adView.callToActionView = adView.findViewById(R.id.btnAdAction)
//        adView.iconView = adView.findViewById(R.id.ivAdIcon)

        adView.setNativeAd(nativeAd)
        container.removeAllViews()
        container.addView(adView)

        return adView
    }
}
