package com.carbon.prolocker.ad

import androidx.annotation.LayoutRes
import com.carbon.prolocker.R

enum class NativeAdType {
    TYPE_1, TYPE_2, TYPE_3, TYPE_4, TYPE_5, TYPE_6
}

object NativeAdLayoutResolver {

    private val adiveryLayouts = mapOf(
        NativeAdType.TYPE_1 to R.layout.ads_native_adivery_type1,
        NativeAdType.TYPE_2 to R.layout.ads_native_adivery_type2,
        NativeAdType.TYPE_3 to R.layout.ads_native_adivery_type3,
        NativeAdType.TYPE_4 to R.layout.ads_native_adivery_type4,
        NativeAdType.TYPE_5 to R.layout.ads_native_adivery_type5,
        NativeAdType.TYPE_6 to R.layout.ads_native_adivery_type6
    )

    private val tapsellLayouts = mapOf(
        NativeAdType.TYPE_1 to R.layout.ads_native_tapsell_type1,
        NativeAdType.TYPE_2 to R.layout.ads_native_tapsell_type2,
        NativeAdType.TYPE_3 to R.layout.ads_native_tapsell_type3,
        NativeAdType.TYPE_4 to R.layout.ads_native_tapsell_type4,
        NativeAdType.TYPE_5 to R.layout.ads_native_tapsell_type5,
        NativeAdType.TYPE_6 to R.layout.ads_native_tapsell_type6
    )

    @LayoutRes
    fun getLayout(adManager: AdManager, placement: String, type: NativeAdType): Int {
        val provider = adManager.getNativeProviderName(placement)
        return getLayout(provider, type)
    }

    @LayoutRes
    fun getLayout(providerName: String, type: NativeAdType): Int {
        return when (providerName) {
            "tapsell" -> tapsellLayouts[type] ?: adiveryLayouts[type]
                ?: error("No layout found for type=$type")

            else -> adiveryLayouts[type] ?: error("No layout found for type=$type")
        }
    }
}
