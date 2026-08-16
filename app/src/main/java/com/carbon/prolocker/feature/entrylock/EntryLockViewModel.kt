package com.carbon.prolocker.feature.entrylock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.IntruderManager
import com.carbon.prolocker.core.utils.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EntryLockViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val intruderManager: IntruderManager,
    private val context: Context
) : ViewModel() {

    private val currentPrefs = preferencesRepository.currentPreferences

    private val _lockType = MutableStateFlow<String?>(currentPrefs.lockType)
    val lockType: StateFlow<String?> = _lockType.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(currentPrefs.vibrationEnabled)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _hidePatternPath = MutableStateFlow(currentPrefs.hidePatternPath)
    val hidePatternPath: StateFlow<Boolean> = _hidePatternPath.asStateFlow()

    private val _recoveryQuestion = MutableStateFlow<String?>(currentPrefs.securityQuestionHash.ifEmpty { null })
    val recoveryQuestion: StateFlow<String?> = _recoveryQuestion.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _threshold = MutableStateFlow(currentPrefs.failedAttemptsThreshold)
    val threshold: StateFlow<Int> = _threshold.asStateFlow()

    private val _fingerprintUnlockEnabled = MutableStateFlow(currentPrefs.fingerprintUnlockEnabled)
    val fingerprintUnlockEnabled: StateFlow<Boolean> = _fingerprintUnlockEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _lockType.value = prefs.lockType
                _vibrationEnabled.value = prefs.vibrationEnabled
                _hidePatternPath.value = prefs.hidePatternPath
                _recoveryQuestion.value = prefs.securityQuestionHash.ifEmpty { null }
                _threshold.value = prefs.failedAttemptsThreshold
                _fingerprintUnlockEnabled.value = prefs.fingerprintUnlockEnabled
            }
        }
    }

    fun onBiometricSuccess() {
        _unlocked.value = true
        _failedAttempts.value = 0
        intruderManager.stopAlarm()
    }

    fun verifyPattern(pattern: List<Int>) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val inputPattern = pattern.joinToString(",")
            val hashedInput = SecurityUtils.hashCredential(inputPattern, prefs.securitySalt)

            if (hashedInput == prefs.hashedCredential) {
                _isError.value = false
                _unlocked.value = true
                _failedAttempts.value = 0
                intruderManager.stopAlarm()
            } else {
                _isError.value = true
                _failedAttempts.value++
                intruderManager.handleFailedAttempt(context.packageName, "PATTERN", _failedAttempts.value)
            }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val hashedInput = SecurityUtils.hashCredential(pin, prefs.securitySalt)

            if (hashedInput == prefs.hashedCredential) {
                _isError.value = false
                _unlocked.value = true
                _failedAttempts.value = 0
                intruderManager.stopAlarm()
            } else {
                _isError.value = true
                _failedAttempts.value++
                intruderManager.handleFailedAttempt(context.packageName, "PIN", _failedAttempts.value)
            }
        }
    }

    fun verifyRecoveryAnswer(answer: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val hashedAnswer = SecurityUtils.hashCredential(answer, prefs.securitySalt)

            if (hashedAnswer == prefs.securityAnswerHash) {
                _unlocked.value = true
                _failedAttempts.value = 0
                intruderManager.stopAlarm()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun resetError() {
        _isError.value = false
    }
}