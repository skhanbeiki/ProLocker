package com.carbon.prolocker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.domain.CheckUpdateUseCase
import com.carbon.prolocker.network.model.UpdateResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val checkUpdateUseCase: CheckUpdateUseCase) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateResponse?>(null)
    val updateState: StateFlow<UpdateResponse?> = _updateState.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    init {
        checkUpdate()
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
    }
}
