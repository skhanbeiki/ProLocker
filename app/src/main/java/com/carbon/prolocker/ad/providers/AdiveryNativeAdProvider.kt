package com.carbon.prolocker.ad.providers

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryNativeAdView
import com.adivery.sdk.AdiveryNativeCallback
import com.adivery.sdk.NativeAd
import com.carbon.prolocker.ad.AdUnitIds
import com.carbon.prolocker.ad.NativeAdProvider

class AdiveryNativeAdProvider(override val providerName: String = "adivery") : NativeAdProvider {

    companion object {
        private const val TAG = "AdiveryNativeAd"
        private var isInitialized = false
    }

    override fun initSdk(context: Context) {
        if (isInitialized) return
        try {
            Adivery.configure(
                context.applicationContext as android.app.Application,
                AdUnitIds.Adivery.APP_ID
            )
            isInitialized = true
        } catch (_e: Exception) {
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
        Adivery.requestNativeAd(context, zoneId, object : AdiveryNativeCallback() {
            override fun onAdLoaded(ad: NativeAd) {
                Log.i("AD_PROVIDER_DEBUG", "🎯 [ADIVERY] requestNativeAd onAdLoaded -> zoneId=$zoneId")
                try {
                    val adView = AdiveryNativeAdView(context)
                    adView.setNativeAdLayout(layoutRes)
                    adView.setPlacementId(zoneId)

                    if (!validateLayoutIds(context, layoutRes)) {
                        Log.e("AD_PROVIDER_DEBUG", "❌ [ADIVERY] validateLayoutIds failed for layoutRes=$layoutRes")
                        return
                    }

                    adView.setListener(object : AdiveryAdListener() {
                        override fun onAdLoaded() {
                            Log.i("AD_PROVIDER_DEBUG", "🎯 [ADIVERY] adViewListener.onAdLoaded -> zoneId=$zoneId")
                        }

                        override fun onAdShown() {
                            Log.i("AD_PROVIDER_DEBUG", "👀 [ADIVERY] onAdShown -> ADIVERY NATIVE AD IS VISIBLE ON SCREEN! (zoneId=$zoneId)")
                            onRendered(adView)
                        }

                        override fun onAdClicked() {
                            Log.i("AD_PROVIDER_DEBUG", "🖱️ [ADIVERY] onAdClicked (zoneId=$zoneId)")
                            try {
                                com.carbon.prolocker.feature.lock.LockService.dismiss(context)
                            } catch (_e: Exception) {}
                        }

                        override fun onAdClosed() {}

                        override fun onError(reason: String) {
                            Log.e("AD_PROVIDER_DEBUG", "❌ [ADIVERY] adViewListener onError: $reason | zoneId=$zoneId")
                            onError(reason)
                        }
                    })
                    container.removeAllViews()
                    container.addView(adView)
                    adView.loadAd()
                } catch (e: Exception) {
                    Log.e("AD_PROVIDER_DEBUG", "❌ [ADIVERY] Exception rendering native ad: ${e.message}")
                    onError(e.message ?: "Failed to render Adivery native ad")
                }
            }

            override fun onAdShown() {
                Log.i("AD_PROVIDER_DEBUG", "👀 [ADIVERY] AdiveryNativeCallback.onAdShown -> zoneId=$zoneId")
            }

            override fun onAdClicked() {
                Log.i("AD_PROVIDER_DEBUG", "🖱️ [ADIVERY] AdiveryNativeCallback.onAdClicked -> zoneId=$zoneId")
            }
        })
    }

    private fun validateLayoutIds(context: Context, @LayoutRes layoutRes: Int): Boolean {
        try {
            val view = LayoutInflater.from(context).inflate(layoutRes, null, false)

            val wrapper = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_wrapper)
            val headline = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_headline)
            val cta = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_call_to_action)

            if (wrapper == null || headline == null || cta == null) {
                return false
            }

            val _description = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_description)
            val _advertiser = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_advertiser)
            val _image = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_image)
            val _icon = view.findViewById<View>(com.carbon.prolocker.R.id.adivery_icon)

            return true
        } catch (_e: Exception) {
            return false
        }
    }
}
