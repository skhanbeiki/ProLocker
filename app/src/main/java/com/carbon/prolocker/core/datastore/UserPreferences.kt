package com.carbon.prolocker.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val isDarkMode: Boolean = true,
    val selectedTab: String = "tools",
    val language: String = "fa",
    val lockType: String = "NONE",
    val hashedCredential: String = "",
    val securitySalt: String = "",
    val vibrationEnabled: Boolean = true,
    val hidePatternPath: Boolean = false,
    val failedAttemptsThreshold: Int = 3,
    val autoStartEnabled: Boolean = true,
    val captureIntruderSelfie: Boolean = false,
    val triggerAlarm: Boolean = false,
    val securityQuestionHash: String = "",
    val securityAnswerHash: String = "",
    val shortExitDurationSeconds: Int = 2,
    val relockOnScreenOff: Boolean = true,
    val lockScreenRotation: String = "SYSTEM",
    val isStealthModeEnabled: Boolean = false,
    val deviceUuid: String = "",
    val selectedBackgroundUrl: String = "",
    val lastBackgroundId: Int = 0,
    val remoteConfigJson: String = "",
    val remoteConfigInterval: Long = 360,
    val lastRemoteConfigSync: Long = 0,
    val remoteConfigInstallDate: Long = 0,
    val themeInterstitialCounter: Int = 0,
    val lastRamCleanerRunTime: Long = 0,
    val lastRamCleanerNotificationTime: Long = 0,
    val ramCleanerNotificationEnabled: Boolean = true,
    val rateAppLaunchCount: Int = 0,
    val rateDialogStage: Int = 0,
    val userHasRated: Boolean = false,
    val isProtectionEnabled: Boolean = true,
    val recoveryOnboardingDismissed: Boolean = false,
    val lastInstalledVersionCode: Int = 0,
    val fingerprintUnlockEnabled: Boolean = false
)