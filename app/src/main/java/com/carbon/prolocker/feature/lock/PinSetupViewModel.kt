package com.carbon.prolocker.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.utils.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PinSetupViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _step = MutableStateFlow(SetupStep.ENTER)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()
    
    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    private var firstPin: String? = null

    fun onNumberClicked(number: Int) {
        val currentPin = _enteredPin.value
        if (currentPin.length < 4) {
            _enteredPin.value = currentPin + number.toString()
            if (_enteredPin.value.length == 4) {
                processPinComplete()
            }
        }
    }
    
    fun onDeleteClicked() {
        val currentPin = _enteredPin.value
        if (currentPin.isNotEmpty()) {
            _enteredPin.value = currentPin.dropLast(1)
            _isError.value = false
        }
    }

    private fun processPinComplete() {
        viewModelScope.launch {
            if (_step.value == SetupStep.ENTER) {
                firstPin = _enteredPin.value
                _enteredPin.value = ""
                _step.value = SetupStep.CONFIRM
                _isError.value = false
            } else if (_step.value == SetupStep.CONFIRM) {
                val secondPin = _enteredPin.value
                if (firstPin == secondPin) {
                    _isError.value = false
                    val salt = SecurityUtils.generateSalt()
                    val hash = SecurityUtils.hashCredential(secondPin, salt)
                    preferencesRepository.saveLockCredential("PIN", hash, salt)
                    _step.value = SetupStep.SUCCESS
                } else {
                    _isError.value = true
                    _step.value = SetupStep.ENTER
                    firstPin = null
                    _enteredPin.value = ""
                }
            }
        }
    }

    enum class SetupStep {
        ENTER, CONFIRM, SUCCESS
    }
}
