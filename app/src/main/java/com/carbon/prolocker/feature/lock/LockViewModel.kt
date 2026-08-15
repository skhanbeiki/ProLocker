package com.carbon.prolocker.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.IntruderManager
import com.carbon.prolocker.core.security.RecoveryManager
import com.carbon.prolocker.core.service.FailedAttemptManager
import com.carbon.prolocker.core.service.LockSessionManager
import com.carbon.prolocker.core.utils.SecurityUtils
import com.carbon.prolocker.core.utils.VibrationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LockViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: LockSessionManager,
    private val failedAttemptManager: FailedAttemptManager,
    private val intruderManager: IntruderManager,
    private val recoveryManager: RecoveryManager,
    private val eventLogManager: EventLogManager,
    private val vibrationManager: VibrationManager
) : ViewModel() {

    private val currentPrefs = preferencesRepository.currentPreferences

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()
    
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    
    private val _lockType = MutableStateFlow<String?>(currentPrefs.lockType)
    val lockType: StateFlow<String?> = _lockType.asStateFlow()

    private val _threshold = MutableStateFlow(currentPrefs.failedAttemptsThreshold)
    val threshold: StateFlow<Int> = _threshold.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(currentPrefs.vibrationEnabled)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _hidePatternPath = MutableStateFlow(currentPrefs.hidePatternPath)
    val hidePatternPath: StateFlow<Boolean> = _hidePatternPath.asStateFlow()

    private val _recoveryQuestion = MutableStateFlow<String?>(currentPrefs.securityQuestionHash.ifEmpty { null })
    val recoveryQuestion: StateFlow<String?> = _recoveryQuestion.asStateFlow()

    private val _lockScreenRotation = MutableStateFlow(currentPrefs.lockScreenRotation)
    val lockScreenRotation: StateFlow<String> = _lockScreenRotation.asStateFlow()

    private val _selectedBackgroundUrl = MutableStateFlow<String?>(currentPrefs.selectedBackgroundUrl.ifEmpty { null })
    val selectedBackgroundUrl: StateFlow<String?> = _selectedBackgroundUrl.asStateFlow()

    private val _fingerprintUnlockEnabled = MutableStateFlow(currentPrefs.fingerprintUnlockEnabled)
    val fingerprintUnlockEnabled: StateFlow<Boolean> = _fingerprintUnlockEnabled.asStateFlow()

    val failedAttempts = failedAttemptManager.state

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _lockType.value = prefs.lockType
                _threshold.value = prefs.failedAttemptsThreshold
                _vibrationEnabled.value = prefs.vibrationEnabled
                _hidePatternPath.value = prefs.hidePatternPath
                _recoveryQuestion.value = prefs.securityQuestionHash.ifEmpty { null }
                _lockScreenRotation.value = prefs.lockScreenRotation
                _selectedBackgroundUrl.value = prefs.selectedBackgroundUrl.ifEmpty { null }
                _fingerprintUnlockEnabled.value = prefs.fingerprintUnlockEnabled
            }
        }
    }

    fun verifyRecoveryAnswer(answer: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Smart Recovery Validation implicitly checks this below, but we can log
            if (recoveryManager.validateAnswer(answer)) {
                eventLogManager.logEvent("RECOVERY_USED", details = "App access restored via recovery")
                recoveryManager.clearSecurityData()
                failedAttemptManager.reset()
                intruderManager.stopAlarm()
                onResult(true)
            } else {
                eventLogManager.logEvent("UNLOCK_FAILED", details = "Failed recovery attempt")
                onResult(false)
            }
        }
    }

    fun verifyPattern(pattern: List<Int>, packageName: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val inputPattern = pattern.joinToString(",")
            val hashedInput = SecurityUtils.hashCredential(inputPattern, prefs.securitySalt)
            
            if (hashedInput == prefs.hashedCredential) {
                _isError.value = false
                eventLogManager.logEvent("UNLOCK_SUCCESS", packageName = packageName, details = "Unlocked via Pattern")
                failedAttemptManager.reset()
                intruderManager.stopAlarm()
                sessionManager.unlockApp(packageName)
                _unlocked.value = true
            } else {
                _isError.value = true
                if (_vibrationEnabled.value) {
                    vibrationManager.vibrateError()
                }
                eventLogManager.logEvent("UNLOCK_FAILED", packageName = packageName, details = "Failed pattern attempt")
                failedAttemptManager.recordFailedAttempt()
                intruderManager.handleFailedAttempt(packageName, "PATTERN", failedAttemptManager.state.value.count)
            }
        }
    }

    fun verifyPin(pin: String, packageName: String) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val hashedInput = SecurityUtils.hashCredential(pin, prefs.securitySalt)
            
            if (hashedInput == prefs.hashedCredential) {
                _isError.value = false
                eventLogManager.logEvent("UNLOCK_SUCCESS", packageName = packageName, details = "Unlocked via PIN")
                failedAttemptManager.reset()
                intruderManager.stopAlarm()
                sessionManager.unlockApp(packageName)
                _unlocked.value = true
            } else {
                _isError.value = true
                if (_vibrationEnabled.value) {
                    vibrationManager.vibrateError()
                }
                eventLogManager.logEvent("UNLOCK_FAILED", packageName = packageName, details = "Failed PIN attempt")
                failedAttemptManager.recordFailedAttempt()
                intruderManager.handleFailedAttempt(packageName, "PIN", failedAttemptManager.state.value.count)
            }
        }
    }
    
    fun unlockForRecovery(packageName: String) {
        sessionManager.unlockApp(packageName)
    }

    fun onBiometricSuccess(packageName: String) {
        _isError.value = false
        eventLogManager.logEvent("UNLOCK_SUCCESS", packageName = packageName, details = "Unlocked via Biometric")
        failedAttemptManager.reset()
        intruderManager.stopAlarm()
        sessionManager.unlockApp(packageName)
        _unlocked.value = true
    }

    fun resetError() {
        _isError.value = false
    }

    fun stopAlarm() {
        intruderManager.stopAlarm()
    }

    override fun onCleared() {
        super.onCleared()
        intruderManager.stopAlarm()
    }
}
