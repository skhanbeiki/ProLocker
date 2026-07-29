package com.carbon.prolocker.core.rate

import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first

class RateAppManager(private val preferencesRepository: PreferencesRepository) {

    enum class DialogStage(val value: Int) {
        NOT_SHOWN(0),
        FIRST_SHOWN(1),
        DISMISSED_FOREVER(2),
        RATED_FOREVER(3);

        companion object {
            fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: NOT_SHOWN
        }
    }

    suspend fun recordLaunch(): Boolean {
        preferencesRepository.updatePreferences {
            it.copy(rateAppLaunchCount = it.rateAppLaunchCount + 1)
        }
        return shouldShowDialog()
    }

    suspend fun shouldShowDialog(): Boolean {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val stage = DialogStage.fromValue(prefs.rateDialogStage)

        if (stage == DialogStage.RATED_FOREVER || stage == DialogStage.DISMISSED_FOREVER) {
            return false
        }

        return when (stage) {
            DialogStage.NOT_SHOWN -> prefs.rateAppLaunchCount >= LAUNCHES_BEFORE_FIRST_PROMPT
            DialogStage.FIRST_SHOWN -> prefs.rateAppLaunchCount >= LAUNCHES_BEFORE_SECOND_PROMPT
            else -> false
        }
    }

    suspend fun onRateClicked() {
        preferencesRepository.updatePreferences {
            it.copy(rateDialogStage = DialogStage.RATED_FOREVER.value, userHasRated = true)
        }
    }

    suspend fun onDontShowAgainClicked() {
        preferencesRepository.updatePreferences { prefs ->
            val stage = DialogStage.fromValue(prefs.rateDialogStage)
            val nextStage = when (stage) {
                DialogStage.NOT_SHOWN -> DialogStage.FIRST_SHOWN
                DialogStage.FIRST_SHOWN -> DialogStage.DISMISSED_FOREVER
                else -> stage
            }
            prefs.copy(rateDialogStage = nextStage.value)
        }
    }

    suspend fun onDialogDismissed() {
        // no-op: only explicit button presses change state
    }

    companion object {
        private const val LAUNCHES_BEFORE_FIRST_PROMPT = 3
        private const val LAUNCHES_BEFORE_SECOND_PROMPT = 8
    }
}
