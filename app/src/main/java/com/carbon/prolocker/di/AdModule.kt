package com.carbon.prolocker.di

import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.InterstitialAdProvider
import com.carbon.prolocker.ad.NativeAdProvider
import com.carbon.prolocker.ad.providers.AdMobInterstitialAdProvider
import com.carbon.prolocker.ad.providers.AdMobNativeAdProvider
import com.carbon.prolocker.ad.providers.AdiveryInterstitialAdProvider
import com.carbon.prolocker.ad.providers.AdiveryNativeAdProvider
import com.carbon.prolocker.ad.providers.TapsellPlusInterstitialAdProvider
import com.carbon.prolocker.ad.providers.TapsellPlusNativeAdProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val adModule = module {
    single {
        val nativeProviders: Map<String, NativeAdProvider> = mapOf(
            "tapsell" to TapsellPlusNativeAdProvider("tapsell"),
            "adivery" to AdiveryNativeAdProvider("adivery"),
            "admob" to AdMobNativeAdProvider("admob")
        )

        val interstitialProviders: Map<String, InterstitialAdProvider> = mapOf(
            "tapsell" to TapsellPlusInterstitialAdProvider("tapsell"),
            "adivery" to AdiveryInterstitialAdProvider("adivery"),
            "admob" to AdMobInterstitialAdProvider("admob")
        )

        AdManager(
            context = androidContext(),
            remoteConfigRepository = get(),
            preferencesRepository = get(),
            nativeProviders = nativeProviders,
            interstitialProviders = interstitialProviders
        )
    }
}
