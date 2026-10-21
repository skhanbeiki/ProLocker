package com.carbon.prolocker.core.datastore

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The [UserPreferences] layout written by app versions before commit c14a18f, which inserted
 * `selectedTab` at position 3 and shifted every following field number.
 * Only used to recover settings from files that are still in that layout. Never change this class.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class LegacyUserPreferences(
    @ProtoNumber(1) val onboardingCompleted: Boolean = false,
    @ProtoNumber(2) val isDarkMode: Boolean = true,
    @ProtoNumber(3) val language: String = "fa",
    @ProtoNumber(4) val lockType: String = "NONE",
    @ProtoNumber(5) val hashedCredential: String = "",
    @ProtoNumber(6) val securitySalt: String = "",
    @ProtoNumber(7) val vibrationEnabled: Boolean = true,
    @ProtoNumber(8) val hidePatternPath: Boolean = false,
    @ProtoNumber(9) val failedAttemptsThreshold: Int = 3,
    @ProtoNumber(10) val autoStartEnabled: Boolean = true,
    @ProtoNumber(11) val captureIntruderSelfie: Boolean = false,
    @ProtoNumber(12) val triggerAlarm: Boolean = false,
    @ProtoNumber(13) val securityQuestionHash: String = "",
    @ProtoNumber(14) val securityAnswerHash: String = "",
    @ProtoNumber(15) val shortExitDurationSeconds: Int = 2,
    @ProtoNumber(16) val relockOnScreenOff: Boolean = true,
    @ProtoNumber(17) val lockScreenRotation: String = "SYSTEM",
    @ProtoNumber(18) val isStealthModeEnabled: Boolean = false,
    @ProtoNumber(19) val deviceUuid: String = "",
    @ProtoNumber(20) val selectedBackgroundUrl: String = "",
    @ProtoNumber(21) val lastBackgroundId: Int = 0,
    @ProtoNumber(22) val remoteConfigJson: String = "",
    @ProtoNumber(23) val remoteConfigInterval: Long = 360,
    @ProtoNumber(24) val lastRemoteConfigSync: Long = 0,
    @ProtoNumber(25) val remoteConfigInstallDate: Long = 0,
    @ProtoNumber(26) val themeInterstitialCounter: Int = 0,
    @ProtoNumber(27) val lastRamCleanerRunTime: Long = 0,
    @ProtoNumber(28) val lastRamCleanerNotificationTime: Long = 0,
    @ProtoNumber(29) val ramCleanerNotificationEnabled: Boolean = true,
    @ProtoNumber(30) val rateAppLaunchCount: Int = 0,
    @ProtoNumber(31) val rateDialogStage: Int = 0,
    @ProtoNumber(32) val userHasRated: Boolean = false,
    @ProtoNumber(33) val isProtectionEnabled: Boolean = true,
    @ProtoNumber(34) val recoveryOnboardingDismissed: Boolean = false,
    @ProtoNumber(35) val lastInstalledVersionCode: Int = 0
) {
    fun toCurrent(): UserPreferences = UserPreferences(
        onboardingCompleted = onboardingCompleted,
        isDarkMode = isDarkMode,
        language = language,
        lockType = lockType,
        hashedCredential = hashedCredential,
        securitySalt = securitySalt,
        vibrationEnabled = vibrationEnabled,
        hidePatternPath = hidePatternPath,
        failedAttemptsThreshold = failedAttemptsThreshold,
        autoStartEnabled = autoStartEnabled,
        captureIntruderSelfie = captureIntruderSelfie,
        triggerAlarm = triggerAlarm,
        securityQuestionHash = securityQuestionHash,
        securityAnswerHash = securityAnswerHash,
        shortExitDurationSeconds = shortExitDurationSeconds,
        relockOnScreenOff = relockOnScreenOff,
        lockScreenRotation = lockScreenRotation,
        isStealthModeEnabled = isStealthModeEnabled,
        deviceUuid = deviceUuid,
        selectedBackgroundUrl = selectedBackgroundUrl,
        lastBackgroundId = lastBackgroundId,
        remoteConfigJson = remoteConfigJson,
        remoteConfigInterval = remoteConfigInterval,
        lastRemoteConfigSync = lastRemoteConfigSync,
        remoteConfigInstallDate = remoteConfigInstallDate,
        themeInterstitialCounter = themeInterstitialCounter,
        lastRamCleanerRunTime = lastRamCleanerRunTime,
        lastRamCleanerNotificationTime = lastRamCleanerNotificationTime,
        ramCleanerNotificationEnabled = ramCleanerNotificationEnabled,
        rateAppLaunchCount = rateAppLaunchCount,
        rateDialogStage = rateDialogStage,
        userHasRated = userHasRated,
        isProtectionEnabled = isProtectionEnabled,
        recoveryOnboardingDismissed = recoveryOnboardingDismissed,
        lastInstalledVersionCode = lastInstalledVersionCode
    )
}
