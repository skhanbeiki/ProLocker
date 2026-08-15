package com.carbon.prolocker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.datastore.UserPreferences
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.security.RecoveryManager
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.core.service.ProtectionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recoveryManager: RecoveryManager,
    private val stealthModeManager: StealthModeManager,
    private val languageManager: LanguageManager,
    private val protectionManager: ProtectionManager
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, UserPreferences())

    val protectionEnabled: StateFlow<Boolean> = protectionManager.protectionEnabled
        .stateIn(viewModelScope, SharingStarted.Lazily, true)
        
    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(vibrationEnabled = enabled) }
        }
    }

    fun toggleHidePatternPath(hide: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(hidePatternPath = hide) }
        }
    }

    fun updateFailedAttemptsThreshold(threshold: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(failedAttemptsThreshold = threshold) }
        }
    }

    fun updateShortExitDuration(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(shortExitDurationSeconds = seconds) }
        }
    }

    fun toggleRelockOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(relockOnScreenOff = enabled) }
        }
    }

    fun updateLockScreenRotation(rotation: String) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(lockScreenRotation = rotation) }
        }
    }

    fun toggleAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(autoStartEnabled = enabled) }
        }
    }

    fun toggleStealthMode(enabled: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (enabled && (prefs.lockType == "NONE" || prefs.hashedCredential.isEmpty())) {
                return@launch
            }
            stealthModeManager.setStealthMode(enabled, context)
        }
    }

    fun changeTheme(isDarkMode: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(isDarkMode = isDarkMode) }
        }
    }

    fun changeLanguage(language: String) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(language = language) }
            languageManager.setLanguage(language)
        }
    }

    fun setupRecovery(question: String, answer: String) {
        viewModelScope.launch {
            recoveryManager.setupRecovery(question, answer)
        }
    }

    fun toggleFingerprintUnlock(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(fingerprintUnlockEnabled = enabled) }
        }
    }

    fun enableProtection() {
        protectionManager.enableProtection()
    }

    fun disableProtection() {
        protectionManager.disableProtection()
    }
}
