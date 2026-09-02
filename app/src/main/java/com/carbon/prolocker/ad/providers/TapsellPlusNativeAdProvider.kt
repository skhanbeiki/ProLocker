package com.carbon.prolocker.ad.providers

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.carbon.prolocker.ProLockerApplication
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.NativeAdProvider
import com.carbon.prolocker.core.language.findActivity
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import ir.tapsell.mediation.ad.views.ntv.NativeAdView
import ir.tapsell.mediation.ad.views.ntv.NativeAdViewContainer

private const val TAG = "TapsellNativeAd"
private val mainHandler = Handler(Looper.getMainLooper())

class TapsellPlusNativeAdProvider(override val providerName: String = "tapsell") : NativeAdProvider {

    companion object {
        private var isInitialized = false
    }

    override fun initSdk(context: Context) {
        try {
            Tapsell.setInitializationListener {
                isInitialized = true
                Log.i("AD_PROVIDER_DEBUG", "✅ [TAPSELL] SDK initialized")
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
        Log.i("AD_PROVIDER_DEBUG", "📡 [TAPSELL NATIVE] Requesting ad for zoneId=$zoneId (context=${context::class.java.simpleName})")

        Tapsell.requestNativeAd(zoneId, object : RequestResultListener {
            override fun onSuccess(adId: String) {
                Log.i("AD_PROVIDER_DEBUG", "✅ [TAPSELL NATIVE] requestNativeAd onSuccess -> adId=$adId | zoneId=$zoneId")

                // Always resolve Activity fresh at render time — this is critical for the
                // LockScreen case where context is a Service and the Activity is tracked
                // globally via ProLockerApplication.currentActivity.
                val act: Activity? = (context as? Activity)?.takeIf { !it.isDestroyed && !it.isFinishing }
                    ?: context.findActivity()?.takeIf { !it.isDestroyed && !it.isFinishing }
                    ?: ProLockerApplication.currentActivity

                if (act == null) {
                    Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL NATIVE] RENDER ERROR: No live Activity found (zoneId=$zoneId). Will retry on main thread.")
                    // Retry once on the main thread — the activity may appear just after the callback fires
                    mainHandler.postDelayed({
                        val retryAct = ProLockerApplication.currentActivity
                        if (retryAct != null) {
                            Log.i("AD_PROVIDER_DEBUG", "🔄 [TAPSELL NATIVE] Retry: Activity found -> ${retryAct.javaClass.simpleName}")
                            try {
                                val adView = renderNativeAd(context, retryAct, adId, container, layoutRes)
                                onRendered(adView)
                            } catch (e: Exception) {
                                Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL NATIVE] Retry render failed: ${e.message}", e)
                                onError(e.message ?: "renderNativeAd failed on retry")
                            }
                        } else {
                            Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL NATIVE] Retry failed: still no Activity (zoneId=$zoneId)")
                            onError("No active Activity for Tapsell native ad")
                        }
                    }, 200)
                    return
                }

                try {
                    val adView = renderNativeAd(context, act, adId, container, layoutRes)
                    Log.i("AD_PROVIDER_DEBUG", "🎉 [TAPSELL NATIVE] Native ad rendered for zoneId=$zoneId activity=${act.javaClass.simpleName}")
                    onRendered(adView)
                } catch (e: Exception) {
                    Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL NATIVE] Exception during renderNativeAd for zoneId=$zoneId: ${e.message}", e)
                    onError(e.message ?: "renderNativeAd failed")
                }
            }

            override fun onFailure(message: String) {
                Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL NATIVE] requestNativeAd onFailure -> Tapsell Error: '$message' | zoneId=$zoneId")
                onError(message)
            }
        })
    }

    private fun renderNativeAd(
        context: Context,
        activity: Activity,
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

        Tapsell.showNativeAd(
            adId,
            nativeAdView,
            activity,
            object : AdStateListener.Native {
                override fun onAdImpression() {
                    Log.i("AD_PROVIDER_DEBUG", "👀 [TAPSELL] onAdImpression -> AD IS VISIBLE ON SCREEN! (adId=$adId)")
                }

                override fun onAdClicked() {
                    Log.i("AD_PROVIDER_DEBUG", "🖱️ [TAPSELL] onAdClicked (adId=$adId)")
                    try {
                        com.carbon.prolocker.feature.lock.LockService.dismiss(context)
                    } catch (_e: Exception) {}
                }

                override fun onAdClosed(completionState: AdShowCompletionState) {}

                override fun onAdFailed(message: String) {
                    Log.e("AD_PROVIDER_DEBUG", "❌ [TAPSELL] showNativeAd onAdFailed: $message (adId=$adId)")
                }
            }
        )

        return adContainer
    }
}
