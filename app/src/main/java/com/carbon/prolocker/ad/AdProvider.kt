package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

interface AdProvider {
    val providerName: String
    fun initSdk(context: Context)
}

interface NativeAdProvider : AdProvider {
    fun loadNativeAd(
        context: Context,
        zoneId: String,
        container: ViewGroup,
        @LayoutRes layoutRes: Int,
        onRendered: (View) -> Unit,
        onError: (String) -> Unit
    )
}

interface InterstitialAdProvider : AdProvider {
    fun loadAndShowInterstitialAd(
        activity: Activity,
        zoneId: String,
        onClosed: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
}
