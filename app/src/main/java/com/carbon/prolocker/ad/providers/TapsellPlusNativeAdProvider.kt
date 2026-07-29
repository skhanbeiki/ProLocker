package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.NativeAdProvider
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import ir.tapsell.mediation.ad.views.ntv.NativeAdView
import ir.tapsell.mediation.ad.views.ntv.NativeAdViewContainer

class TapsellPlusNativeAdProvider(override val providerName: String = "tapsell") : NativeAdProvider {

    companion object {
        private const val TAG = "TapsellPlusNativeAd"
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

    override fun loadNativeAd(
        context: Context,
        zoneId: String,
        container: ViewGroup,
        @LayoutRes layoutRes: Int,
        onRendered: (View) -> Unit,
        onError: (String) -> Unit
    ) {
        Tapsell.requestNativeAd(zoneId, object : RequestResultListener {
            override fun onSuccess(adId: String) {
                val activity = context as? Activity
                if (activity != null && activity.isDestroyed) return
                try {
                    val adView = renderNativeAd(context, adId, container, layoutRes)
                    onRendered(adView)
                } catch (e: Exception) {
                    Log.e(TAG, "renderNativeAd failed", e)
                }
            }

            override fun onFailure(message: String) {
                val activity = context as? Activity
                if (activity != null && activity.isDestroyed) return
                onError(message)
            }
        })
    }

    private fun renderNativeAd(
        context: Context,
        adId: String,
        container: ViewGroup,
        @LayoutRes layoutRes: Int
    ): View {
        container.removeAllViews()

        val adContainer = LayoutInflater.from(context)
            .inflate(layoutRes, container, false) as NativeAdViewContainer

        container.addView(adContainer)

        val logo = adContainer.findViewById<ImageView?>(R.id.tapsell_native_ad_logo)
        val title = adContainer.findViewById<TextView?>(R.id.tapsell_native_ad_title)
        val description = adContainer.findViewById<TextView?>(R.id.tapsell_native_ad_description)
        val cta = adContainer.findViewById<Button?>(R.id.tapsell_native_ad_cta)
        val media = adContainer.findViewById<FrameLayout?>(R.id.tapsell_native_ad_media)

        val builder = NativeAdView.Builder(adContainer)

        logo?.let { builder.withLogo(it) }
        title?.let { builder.withTitle(it) }
        description?.let { builder.withDescription(it) }
        cta?.let { builder.withCtaButton(it) }
        media?.let { builder.withMedia(it) }

        val nativeAdView = builder.build()

        val activity = context as? Activity
        if (activity == null) {
            Log.w(TAG, "showNativeAd skipped — no Activity available (context=${context::class.java.name})")
            return adContainer
        }

        Tapsell.showNativeAd(
            adId,
            nativeAdView,
            activity,
            object : AdStateListener.Native {
                override fun onAdImpression() {}
                override fun onAdClicked() {}
                override fun onAdClosed(completionState: AdShowCompletionState) {}
                override fun onAdFailed(message: String) {
                    Log.e(TAG, "showNativeAd failed: $message")
                }
            }
        )

        return adContainer
    }
}
