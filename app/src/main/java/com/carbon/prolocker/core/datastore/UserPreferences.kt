package com.carbon.prolocker.core.datastore

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Stored as ProtoBuf. Field numbers are part of the on-disk format: never remove or reorder
// properties, and give every new property the next free number.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserPreferences(
    @ProtoNumber(1) val onboardingCompleted: Boolean = false,
    @ProtoNumber(2) val isDarkMode: Boolean = true,
    @ProtoNumber(3) val selectedTab: String = "tools",
    @ProtoNumber(4) val language: String = if (com.carbon.prolocker.core.config.MarketConfig.isGooglePlay) "en" else "fa",
    @ProtoNumber(5) val lockType: String = "NONE",
    @ProtoNumber(6) val hashedCredential: String = "",
    @ProtoNumber(7) val securitySalt: String = "",
    @ProtoNumber(8) val vibrationEnabled: Boolean = true,
    @ProtoNumber(9) val hidePatternPath: Boolean = false,
    @ProtoNumber(10) val failedAttemptsThreshold: Int = 3,
    @ProtoNumber(11) val autoStartEnabled: Boolean = true,
    @ProtoNumber(12) val captureIntruderSelfie: Boolean = false,
    @ProtoNumber(13) val triggerAlarm: Boolean = false,
    @ProtoNumber(14) val securityQuestionHash: String = "",
    @ProtoNumber(15) val securityAnswerHash: String = "",
    @ProtoNumber(16) val shortExitDurationSeconds: Int = 2,
    @ProtoNumber(17) val relockOnScreenOff: Boolean = true,
    @ProtoNumber(18) val lockScreenRotation: String = "SYSTEM",
    @ProtoNumber(19) val isStealthModeEnabled: Boolean = false,
    @ProtoNumber(20) val deviceUuid: String = "",
    @ProtoNumber(21) val selectedBackgroundUrl: String = "",
    @ProtoNumber(22) val lastBackgroundId: Int = 0,
    @ProtoNumber(23) val remoteConfigJson: String = "",
    @ProtoNumber(24) val remoteConfigInterval: Long = 360,
    @ProtoNumber(25) val lastRemoteConfigSync: Long = 0,
    @ProtoNumber(26) val remoteConfigInstallDate: Long = 0,
    @ProtoNumber(27) val themeInterstitialCounter: Int = 0,
    @ProtoNumber(28) val lastRamCleanerRunTime: Long = 0,
    @ProtoNumber(29) val lastRamCleanerNotificationTime: Long = 0,
    @ProtoNumber(30) val ramCleanerNotificationEnabled: Boolean = true,
    @ProtoNumber(31) val rateAppLaunchCount: Int = 0,
    @ProtoNumber(32) val rateDialogStage: Int = 0,
    @ProtoNumber(33) val userHasRated: Boolean = false,
    @ProtoNumber(34) val isProtectionEnabled: Boolean = true,
    @ProtoNumber(35) val recoveryOnboardingDismissed: Boolean = false,
    @ProtoNumber(36) val lastInstalledVersionCode: Int = 0,
    @ProtoNumber(37) val fingerprintUnlockEnabled: Boolean = false,
    @ProtoNumber(38) val ramCleanerInterstitialCounter: Int = 0,
    @ProtoNumber(39) val inAppInterstitialCounter: Int = 0,
    @ProtoNumber(40) val suggestionButtonDisplayCounter: Int = 0,
    @ProtoNumber(41) val suggestionButtonLastClickedDate: String = ""
)
