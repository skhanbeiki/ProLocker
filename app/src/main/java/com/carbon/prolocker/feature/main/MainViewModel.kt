package com.carbon.prolocker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.domain.CheckUpdateUseCase
import com.carbon.prolocker.network.model.UpdateResponse
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(
    private val checkUpdateUseCase: CheckUpdateUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateResponse?>(null)
    val updateState: StateFlow<UpdateResponse?> = _updateState.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.TOOLS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private var userHasManuallyChangedTab = false

    init {
        restoreSelectedTab()
        observeRemoteConfigTab()
        checkUpdate()
    }

    private fun restoreSelectedTab() {
        viewModelScope.launch {
            try {
                val config = remoteConfigRepository.getConfig()
                val effectiveTab = config.getEffectiveDefaultHomeTab()
                if (!userHasManuallyChangedTab) {
                    _selectedTab.value = if (effectiveTab.equals("applocker", ignoreCase = true)) {
                        MainTab.HOME
                    } else {
                        MainTab.TOOLS
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun observeRemoteConfigTab() {
        viewModelScope.launch {
            remoteConfigRepository.configFlow.collectLatest { config ->
                if (!userHasManuallyChangedTab) {
                    val effectiveTab = config.getEffectiveDefaultHomeTab()
                    _selectedTab.value = if (effectiveTab.equals("applocker", ignoreCase = true)) {
                        MainTab.HOME
                    } else {
                        MainTab.TOOLS
                    }
                }
            }
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
        userHasManuallyChangedTab = true
        _selectedTab.value = tab
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedTab = tab.name) }
        }
    }
}
