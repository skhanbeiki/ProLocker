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
                try {
                    val adView = AdiveryNativeAdView(context)
                    adView.setNativeAdLayout(layoutRes)
                    adView.setPlacementId(zoneId)

                    if (!validateLayoutIds(context, layoutRes)) {
                        return
                    }

                    adView.setListener(object : AdiveryAdListener() {
                        override fun onAdLoaded() {
                        }

                        override fun onAdShown() {
                            onRendered(adView)
                        }

                        override fun onAdClicked() {
                        }

                        override fun onAdClosed() {
                        }

                        override fun onError(reason: String) {
                            onError(reason)
                        }
                    })
                    container.removeAllViews()
                    container.addView(adView)
                    adView.loadAd()
                } catch (e: Exception) {
                    onError(e.message ?: "Failed to render Adivery native ad")
                }
            }

            override fun onAdShown() {
                Log.d(TAG, "ADS_RENDERED provider=$providerName zoneId=$zoneId")
            }

            override fun onAdClicked() {
                Log.d(TAG, "ADS_CLICKED provider=$providerName zoneId=$zoneId")
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
