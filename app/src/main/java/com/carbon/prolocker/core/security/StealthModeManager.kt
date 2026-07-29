package com.carbon.prolocker.core.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first

class StealthModeManager(private val preferencesRepository: PreferencesRepository) {
    companion object {
        const val APP_ALIAS_CLASS_NAME = "com.carbon.prolocker.launcher.AppLauncherAlias"
        const val RECOVERY_ALIAS_CLASS_NAME = "com.carbon.prolocker.launcher.RecoveryLauncherAlias"
    }

    suspend fun isStealthModeEnabled(): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        return prefs.isStealthModeEnabled
    }

    suspend fun setStealthMode(enabled: Boolean, context: Context) {
        applyLauncherState(context, enabled)
        preferencesRepository.updatePreferences {
            it.copy(isStealthModeEnabled = enabled)
        }
    }

    suspend fun repairLauncherState(context: Context) {
        applyLauncherState(context, isStealthModeEnabled())
    }

    private fun applyLauncherState(context: Context, stealthEnabled: Boolean) {
        val packageManager = context.packageManager
        val appAlias = ComponentName(context, APP_ALIAS_CLASS_NAME)
        val recoveryAlias = ComponentName(context, RECOVERY_ALIAS_CLASS_NAME)

        if (stealthEnabled) {
            setComponentEnabledSetting(packageManager, recoveryAlias, true)
            setComponentEnabledSetting(packageManager, appAlias, false)
        } else {
            setComponentEnabledSetting(packageManager, appAlias, true)
            setComponentEnabledSetting(packageManager, recoveryAlias, false)
        }
    }

    private fun setComponentEnabledSetting(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean
    ) {
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(componentName) != desiredState) {
            packageManager.setComponentEnabledSetting(
                componentName,
                desiredState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
