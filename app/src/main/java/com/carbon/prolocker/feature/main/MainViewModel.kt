package com.carbon.prolocker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.domain.CheckUpdateUseCase
import com.carbon.prolocker.network.model.UpdateResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val checkUpdateUseCase: CheckUpdateUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateResponse?>(null)
    val updateState: StateFlow<UpdateResponse?> = _updateState.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.TOOLS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    init {
        restoreSelectedTab()
        checkUpdate()
    }

    private fun restoreSelectedTab() {
        viewModelScope.launch {
            val tabName = preferencesRepository.safeFirst().selectedTab
            _selectedTab.value = MainTab.fromName(tabName)
        }
    }

    private fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = checkUpdateUseCase()
        }
    }

    fun dismissUpdate() {
        _updateState.value = null
    }

    fun setSelectedTab(tab: MainTab) {
        _selectedTab.value = tab
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedTab = tab.name) }
        }
    }
}
