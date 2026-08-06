package com.carbon.prolocker.feature.privacyauditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrivacyAuditorViewModel(
    private val repository: PrivacyAuditorRepository
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    
    private val _filteredApps = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppPermissionInfo>> = _filteredApps.asStateFlow()

    private val _summary = MutableStateFlow(PrivacySummary(0, 0, 0, 0, 100))
    val summary: StateFlow<PrivacySummary> = _summary.asStateFlow()

    private val _activeFilter = MutableStateFlow(RiskFilter.ALL)
    val activeFilter: StateFlow<RiskFilter> = _activeFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAuditData()
    }

    fun loadAuditData() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = repository.getAppsPermissionAudit()
            _allApps.value = apps
            _summary.value = repository.calculateSummary(apps)
            applyFilter(_activeFilter.value)
            _isLoading.value = false
        }
    }

    fun setFilter(filter: RiskFilter) {
        _activeFilter.value = filter
        applyFilter(filter)
    }

    private fun applyFilter(filter: RiskFilter) {
        val list = _allApps.value
        _filteredApps.value = when (filter) {
            RiskFilter.ALL -> list
            RiskFilter.HIGH_RISK -> list.filter { it.riskLevel == RiskLevel.HIGH }
            RiskFilter.CAMERA_MIC -> list.filter { app ->
                app.grantedPermissions.any { it.category == PermissionCategory.CAMERA || it.category == PermissionCategory.MICROPHONE }
            }
            RiskFilter.LOCATION -> list.filter { app ->
                app.grantedPermissions.any { it.category == PermissionCategory.LOCATION }
            }
            RiskFilter.SMS_CONTACTS -> list.filter { app ->
                app.grantedPermissions.any { it.category == PermissionCategory.SMS || it.category == PermissionCategory.CONTACTS }
            }
        }
    }
}
