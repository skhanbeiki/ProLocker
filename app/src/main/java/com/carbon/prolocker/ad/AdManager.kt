package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.carbon.prolocker.core.config.MarketConfig
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.model.RemoteConfigResponse
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AdManager(
    private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val preferencesRepository: PreferencesRepository,
    private val nativeProviders: Map<String, NativeAdProvider>,
    private val interstitialProviders: Map<String, InterstitialAdProvider>
) {

    val configFlow = remoteConfigRepository.configFlow

    companion object {
        private const val TAG = "AdManager"
        private const val NATIVE_AD_TTL_MS = 5 * 60 * 1000L
    }

    @Volatile
    private var isShowingInterstitial = false

    private data class CachedNativeAd(
        val container: FrameLayout,
        val loadedAt: Long
    )

    private val preloadedAds = mutableMapOf<String, CachedNativeAd>()

    fun preloadNativeAd(
        activity: Activity,
        placement: String,
        adType: NativeAdType
    ) {
        if (!areAdsEnabled()) return

        val existing = preloadedAds[placement]
        if (existing != null && System.currentTimeMillis() - existing.loadedAt < NATIVE_AD_TTL_MS) {
            return
        }

        val providerName = getNativeProviderName(placement)
        val provider = nativeProviders[providerName]
        if (provider == null) {
            Log.e(TAG, "PRELOAD_FAILED placement=$placement provider=$providerName (unknown)")
            return
        }

        val unitId = getUnitId(providerName, placement)
        val layoutRes = NativeAdLayoutResolver.getLayout(providerName, adType)
        val cacheContainer = FrameLayout(activity)

        provider.loadNativeAd(
            context = activity,
            zoneId = unitId,
            container = cacheContainer,
            layoutRes = layoutRes,
            onRendered = {
                preloadedAds[placement] = CachedNativeAd(cacheContainer, System.currentTimeMillis())
            },
            onError = { error ->
                Log.e(TAG, "PRELOAD_FAILED placement=$placement error=$error")
            }
        )
    }

    fun consumeCachedNativeAdView(placement: String): View? {
        val cached = preloadedAds.remove(placement) ?: return null
        if (System.currentTimeMillis() - cached.loadedAt >= NATIVE_AD_TTL_MS) {
            return null
        }
        return cached.container
    }

    fun initSdk() {
        if (!areAdsEnabled()) {
            Log.d(TAG, "ADS_DISABLED limitInstallDisplayAdDays not reached")
            return
        }
        nativeProviders.values.forEach { it.initSdk(context) }
        interstitialProviders.values.forEach { it.initSdk(context) }
    }

    fun areAdsEnabled(): Boolean = runBlocking {
        try {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val config = getConfig()
            val limitDays = config.configs.limitInstallDisplayAdDays
            if (limitDays <= 0) return@runBlocking true

            if (prefs.remoteConfigInstallDate == 0L) {
                val installDate = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                preferencesRepository.updatePreferences { it.copy(remoteConfigInstallDate = installDate) }
                false
            } else {
                val daysSinceInstall = (System.currentTimeMillis() - prefs.remoteConfigInstallDate) / (1000 * 60 * 60 * 24)
                daysSinceInstall >= limitDays
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getConfig(): RemoteConfigResponse {
        return remoteConfigRepository.configFlow.first()
    }

    fun getNativeProviderName(placement: String): String = runBlocking {
        val config = getConfig()
        val isLockscreen = placement == AdPlacement.LOCKSCREEN_TOP || placement == AdPlacement.LOCKSCREEN_BOTTOM
        if (isLockscreen) {
            when {
                MarketConfig.isBazaar -> config.configs.nativeAdBazaarLockScreen
                MarketConfig.isMyket -> config.configs.nativeAdMyketLockScreen
                else -> config.configs.nativeAdGooglePlayLockScreen
            }
        } else {
            when {
                MarketConfig.isBazaar -> config.configs.displayAdTypeBazaarApp
                MarketConfig.isMyket -> config.configs.displayAdTypeMyketApp
                else -> config.configs.displayAdTypeGooglePlayApp
            }
        }.lowercase()
    }

    fun getInterstitialProviderName(): String = runBlocking {
        val config = getConfig()
        when {
            MarketConfig.isBazaar -> config.configs.displayAdTypeBazaarApp
            MarketConfig.isMyket -> config.configs.displayAdTypeMyketApp
            else -> config.configs.displayAdTypeGooglePlayApp
        }.lowercase()
    }

    fun getUnitId(providerName: String, placement: String): String {
        return when (providerName) {
            "tapsell" -> when (placement) {
                AdPlacement.HOME_TAB_APPS -> AdUnitIds.Tapsell.HOME_TAB_APPS
                AdPlacement.HOME_TAB_THEMES -> AdUnitIds.Tapsell.HOME_TAB_THEMES
                AdPlacement.HOME_TAB_SETTINGS -> AdUnitIds.Tapsell.HOME_TAB_SETTINGS
                AdPlacement.LOCKSCREEN_TOP -> AdUnitIds.Tapsell.LOCKSCREEN_TOP
                AdPlacement.LOCKSCREEN_BOTTOM -> AdUnitIds.Tapsell.LOCKSCREEN_BOTTOM
                AdPlacement.RAM_CLEANER_NATIVE -> AdUnitIds.Tapsell.RAM_CLEANER_NATIVE
                AdPlacement.BACKGROUND_LIST_NATIVE -> AdUnitIds.Tapsell.BACKGROUND_LIST_NATIVE
                AdPlacement.BACKGROUND_PREVIEW_NATIVE -> AdUnitIds.Tapsell.BACKGROUND_PREVIEW_NATIVE
                AdPlacement.EXIT_NATIVE -> AdUnitIds.Tapsell.EXIT_NATIVE
                AdPlacement.INTERSTITIAL_BACKGROUND -> AdUnitIds.Tapsell.INTERSTITIAL_BACKGROUND
                AdPlacement.INTERSTITIAL_RAM_CLEANER -> AdUnitIds.Tapsell.INTERSTITIAL_RAM_CLEANER
                else -> ""
            }
            "adivery" -> when (placement) {
                AdPlacement.HOME_TAB_APPS -> AdUnitIds.Adivery.HOME_TAB_APPS
                AdPlacement.HOME_TAB_THEMES -> AdUnitIds.Adivery.HOME_TAB_THEMES
                AdPlacement.HOME_TAB_SETTINGS -> AdUnitIds.Adivery.HOME_TAB_SETTINGS
                AdPlacement.LOCKSCREEN_TOP -> AdUnitIds.Adivery.LOCKSCREEN_TOP
                AdPlacement.LOCKSCREEN_BOTTOM -> AdUnitIds.Adivery.LOCKSCREEN_BOTTOM
                AdPlacement.RAM_CLEANER_NATIVE -> AdUnitIds.Adivery.RAM_CLEANER_NATIVE
                AdPlacement.BACKGROUND_LIST_NATIVE -> AdUnitIds.Adivery.BACKGROUND_LIST_NATIVE
                AdPlacement.BACKGROUND_PREVIEW_NATIVE -> AdUnitIds.Adivery.BACKGROUND_PREVIEW_NATIVE
                AdPlacement.EXIT_NATIVE -> AdUnitIds.Adivery.EXIT_NATIVE
                AdPlacement.INTERSTITIAL_BACKGROUND -> AdUnitIds.Adivery.INTERSTITIAL_BACKGROUND
                AdPlacement.INTERSTITIAL_RAM_CLEANER -> AdUnitIds.Adivery.INTERSTITIAL_RAM_CLEANER
                else -> ""
            }
            "admob" -> when (placement) {
                AdPlacement.HOME_TAB_APPS -> AdUnitIds.AdMob.HOME_TAB_APPS
                AdPlacement.HOME_TAB_THEMES -> AdUnitIds.AdMob.HOME_TAB_THEMES
                AdPlacement.HOME_TAB_SETTINGS -> AdUnitIds.AdMob.HOME_TAB_SETTINGS
                AdPlacement.LOCKSCREEN_TOP -> AdUnitIds.AdMob.LOCKSCREEN_TOP
                AdPlacement.LOCKSCREEN_BOTTOM -> AdUnitIds.AdMob.LOCKSCREEN_BOTTOM
                AdPlacement.RAM_CLEANER_NATIVE -> AdUnitIds.AdMob.RAM_CLEANER_NATIVE
                AdPlacement.BACKGROUND_LIST_NATIVE -> AdUnitIds.AdMob.BACKGROUND_LIST_NATIVE
                AdPlacement.BACKGROUND_PREVIEW_NATIVE -> AdUnitIds.AdMob.BACKGROUND_PREVIEW_NATIVE
                AdPlacement.EXIT_NATIVE -> AdUnitIds.AdMob.EXIT_NATIVE
                AdPlacement.INTERSTITIAL_BACKGROUND -> AdUnitIds.AdMob.INTERSTITIAL_BACKGROUND
                AdPlacement.INTERSTITIAL_RAM_CLEANER -> AdUnitIds.AdMob.INTERSTITIAL_RAM_CLEANER
                else -> ""
            }
            else -> ""
        }
    }

    fun loadNativeAd(
        activity: Context,
        placement: String,
        container: ViewGroup,
        @LayoutRes layoutRes: Int,
        onRendered: (View) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!areAdsEnabled()) {
            onError("Ads disabled")
            return
        }

        val providerName = getNativeProviderName(placement)
        val provider = nativeProviders[providerName]
        if (provider == null) {
            Log.e(TAG, "AD_PLACEMENT_SELECTED placement=$placement provider=$providerName (unknown)")
            onError("Unknown provider: $providerName")
            return
        }

        val unitId = getUnitId(providerName, placement)
        provider.loadNativeAd(
            activity, unitId, container, layoutRes, onRendered, onError
        )
    }

    fun showInterstitialAd(
        activity: Activity,
        placement: String = AdPlacement.INTERSTITIAL_BACKGROUND,
        onClosed: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isShowingInterstitial) {
            onError("Ad already showing")
            return
        }
        if (!areAdsEnabled()) {
            onError("Ads disabled")
            return
        }
        val providerName = getInterstitialProviderName()
        val provider = interstitialProviders[providerName]
        if (provider == null) {
            Log.e(TAG, "INTERSTITIAL_PLACEMENT_SELECTED placement=$placement provider=$providerName (unknown)")
            onError("Unknown provider: $providerName")
            return
        }

        isShowingInterstitial = true
        val unitId = getUnitId(providerName, placement)
        provider.loadAndShowInterstitialAd(
            activity,
            unitId,
            onClosed = {
                isShowingInterstitial = false
                onClosed()
            },
            onError = { error ->
                isShowingInterstitial = false
                onError(error)
            }
        )
    }

    fun destroyNativeAd(activity: Activity, providerName: String) {
    }
}
