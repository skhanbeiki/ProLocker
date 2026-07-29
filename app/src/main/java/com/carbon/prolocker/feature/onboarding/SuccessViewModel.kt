package com.carbon.prolocker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import kotlinx.coroutines.launch

class SuccessViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.completeOnboarding()
            onSuccess()
        }
    }
}
