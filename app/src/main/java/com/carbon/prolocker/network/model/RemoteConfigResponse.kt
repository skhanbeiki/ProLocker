package com.carbon.prolocker.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfigResponse(
    @SerialName("interval") val interval: Long = 360,
    @SerialName("configs") val configs: RemoteConfigs = RemoteConfigs(),
    @SerialName("defaultHomeTab") val defaultHomeTab: String = "tools",
    @SerialName("surveyDisplay") val surveyDisplay: Int? = null
) {
    companion object {
        val DEFAULT = RemoteConfigResponse(
            defaultHomeTab = "tools",
            configs = RemoteConfigs(
                defaultHomeTab = "tools",
                displayAdTypeMyketApp = "adivery",
                displayAdTypeBazaarApp = "adivery",
                displayAdTypeGooglePlayApp = "adivery",
                nativeAdBazaarLockScreen = "adivery",
                nativeAdMyketLockScreen = "adivery",
                nativeAdGooglePlayLockScreen = "adivery",
                nativeAdPlaceLockScreen = "topBanner",
                interstitialAdThemeStep = 3,
                interstitialAdRamCleanerStep = 3,
                nativeAdRamCleanerPage = true,
                limitInstallDisplayAdDays = 3,
                ramCleanerNotifyTimes = listOf(22),
                surveyDisplay = 2
            )
        )
    }

    fun getEffectiveDefaultHomeTab(): String {
        return if (defaultHomeTab.isNotBlank() && defaultHomeTab != "tools") {
            defaultHomeTab
        } else {
            configs.defaultHomeTab
        }
    }

    fun getEffectiveSurveyDisplay(): Int = surveyDisplay ?: configs.surveyDisplay
}

@Serializable
data class RemoteConfigs(
    @SerialName("defaultHomeTab") val defaultHomeTab: String = "tools",
    @SerialName("RamCleanerNotifyTimes") val ramCleanerNotifyTimes: List<Int> = emptyList(),
    @SerialName("displayAdTypeMyketApp") val displayAdTypeMyketApp: String = "adivery",
    @SerialName("displayAdTypeBazaarApp") val displayAdTypeBazaarApp: String = "adivery",
    @SerialName("displayAdTypeGooglePLayApp") val displayAdTypeGooglePlayApp: String = "adivery",
    @SerialName("nativeAdBazaarLockScreen") val nativeAdBazaarLockScreen: String = "adivery",
    @SerialName("nativeAdMyketLockScreen") val nativeAdMyketLockScreen: String = "adivery",
    @SerialName("nativeAdGooglePLayLockScreen") val nativeAdGooglePlayLockScreen: String = "adivery",
    @SerialName("nativeAdPlaceLockScreen") val nativeAdPlaceLockScreen: String = "topBanner",
    @SerialName("nativeAdRamCleanerPage") val nativeAdRamCleanerPage: Boolean = true,
    @SerialName("interstitialAdThemeStep") val interstitialAdThemeStep: Int = 3,
    @SerialName("interstitialAdRamCleanerStep") val interstitialAdRamCleanerStep: Int = 3,
    @SerialName("limitInstallDisplayAdDays") val limitInstallDisplayAdDays: Int = 3,
    @SerialName("surveyDisplay") val surveyDisplay: Int = 2
)
