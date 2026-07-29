package com.carbon.prolocker.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.utils.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PatternSetupViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _step = MutableStateFlow(SetupStep.ENTER)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private var firstPattern: String? = null

    fun onPatternEntered(pattern: List<Int>) {
        viewModelScope.launch {
            if (_step.value == SetupStep.ENTER) {
                firstPattern = pattern.joinToString(",")
                _step.value = SetupStep.CONFIRM
                _isError.value = false
            } else if (_step.value == SetupStep.CONFIRM) {
                val secondPattern = pattern.joinToString(",")
                if (firstPattern == secondPattern) {
                    _isError.value = false
                    val salt = SecurityUtils.generateSalt()
                    val hash = SecurityUtils.hashCredential(secondPattern, salt)
                    preferencesRepository.saveLockCredential("PATTERN", hash, salt)
                    _step.value = SetupStep.SUCCESS
                } else {
                    _isError.value = true
                    _step.value = SetupStep.ENTER
                    firstPattern = null
                }
            }
        }
    }
    
    fun resetError() {
        _isError.value = false
    }

    enum class SetupStep {
        ENTER, CONFIRM, SUCCESS
    }
}
