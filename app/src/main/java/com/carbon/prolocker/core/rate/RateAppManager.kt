package com.carbon.prolocker.core.rate

import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository

class RateAppManager(
    private val preferencesRepository: PreferencesRepository,
    private val remoteConfigRepository: RemoteConfigRepository
) {

    enum class DialogStage(val value: Int) {
        NOT_SHOWN(0),
        LATER_ONCE(1),
        DISMISSED_FOREVER(2),
        RATED_FOREVER(3);

        companion object {
            fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: NOT_SHOWN
        }
    }

    suspend fun recordLaunch() {
        preferencesRepository.updatePreferences {
            it.copy(rateAppLaunchCount = it.rateAppLaunchCount + 1)
        }
    }

    suspend fun shouldShowSurveyOnExit(): Boolean {
        val config = remoteConfigRepository.getConfig()
        if (config.getEffectiveSurveyDisplay() != 2) {
            return false
        }

        val prefs = preferencesRepository.userPreferencesFlow.value
        if (prefs.userHasRated) {
            return false
        }

        // On first launch, user sees regular exit dialog. On launch >= 2, user sees survey dialog.
        if (prefs.rateAppLaunchCount < 2) {
            return false
        }

        val stage = DialogStage.fromValue(prefs.rateDialogStage)
        return when (stage) {
            DialogStage.NOT_SHOWN, DialogStage.LATER_ONCE -> true
            DialogStage.DISMISSED_FOREVER, DialogStage.RATED_FOREVER -> false
        }
    }

    suspend fun onRateClicked() {
        preferencesRepository.updatePreferences {
            it.copy(
                rateDialogStage = DialogStage.RATED_FOREVER.value,
                userHasRated = true
            )
        }
    }

    suspend fun onLaterClicked() {
        preferencesRepository.updatePreferences { prefs ->
            val stage = DialogStage.fromValue(prefs.rateDialogStage)
            val nextStage = when (stage) {
                DialogStage.NOT_SHOWN -> DialogStage.LATER_ONCE
                DialogStage.LATER_ONCE -> DialogStage.DISMISSED_FOREVER
                else -> DialogStage.DISMISSED_FOREVER
            }
            prefs.copy(rateDialogStage = nextStage.value)
        }
    }
}
