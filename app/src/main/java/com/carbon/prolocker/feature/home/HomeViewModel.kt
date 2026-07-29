package com.carbon.prolocker.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.permissions.PermissionManager
import com.carbon.prolocker.core.security.RecoveryManager
import com.carbon.prolocker.core.service.ProtectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val lockedAppsRepository: LockedAppsRepository,
    private val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager,
    private val context: Context,
    private val protectionManager: ProtectionManager,
    private val preferencesRepository: PreferencesRepository,
    private val recoveryManager: RecoveryManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showProtectionReenabled = MutableSharedFlow<Unit>()
    val showProtectionReenabled = _showProtectionReenabled.asSharedFlow()

    private val _showRecoveryOnboarding = MutableSharedFlow<Unit>()
    val showRecoveryOnboarding = _showRecoveryOnboarding.asSharedFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedSearchQuery = _searchQuery.debounce(300L)
    
    val totalLockedCount: Flow<Int> = lockedAppsRepository.allLockedApps.map { list ->
        list.count { it.lockedState }
    }

    val serviceStatus: Flow<ServiceStatus> = combine(
        totalLockedCount,
        protectionManager.protectionEnabled
    ) { lockedCount, protectionEnabled ->
        when {
            !protectionEnabled -> ServiceStatus.STOPPED
            lockedCount > 0 -> ServiceStatus.ACTIVE
            else -> ServiceStatus.IDLE
        }
    }
    
    val appsList: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        lockedAppsRepository.allLockedApps,
        debouncedSearchQuery
    ) { apps, lockedApps, query ->
        val lockedPackageNames = lockedApps.filter { it.lockedState }.map { it.packageName }.toSet()
        val mappedApps = apps.map { app ->
            app.copy(isLocked = lockedPackageNames.contains(app.packageName))
        }
        if (query.isBlank()) {
            mappedApps
        } else {
            val lowerQuery = query.lowercase()
            mappedApps.filter {
                it.name.lowercase().contains(lowerQuery) || it.packageName.lowercase().contains(lowerQuery)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var pendingLockPackage: String? = null

    init {
        loadApps()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = getInstalledAppsUseCase()
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun checkPermissionsAndLock(packageName: String): Boolean {
        if (PermissionManager.hasAllRequiredPermissions(context)) {
            toggleAppLock(packageName, false)
            return true
        } else {
            pendingLockPackage = packageName
            return false
        }
    }

    fun onPermissionsGranted() {
        val pkg = pendingLockPackage
        pendingLockPackage = null
        if (pkg != null) {
            toggleAppLock(pkg, false)
        }
    }

    fun toggleAppLock(packageName: String, isLocked: Boolean) {
        viewModelScope.launch {
            if (!isLocked && !PermissionManager.hasAllRequiredPermissions(context)) {
                pendingLockPackage = packageName
                return@launch
            }
            if (isLocked) {
                lockedAppsRepository.removeLockedApp(packageName)
                analyticsManager.trackAppUnlocked(packageName)
            } else {
                val currentCount = totalLockedCount.first()
                lockedAppsRepository.addLockedApp(packageName)
                analyticsManager.trackAppLocked(packageName)
                if (protectionManager.reenableProtectionIfNeeded()) {
                    _showProtectionReenabled.emit(Unit)
                }
                if (currentCount == 0) {
                    checkAndTriggerRecoveryOnboarding()
                }
            }
            checkServiceState()
        }
    }

    private suspend fun checkAndTriggerRecoveryOnboarding() {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val hasRecovery = prefs.securityQuestionHash.isNotBlank()
        val onboardingDismissed = prefs.recoveryOnboardingDismissed
        if (!hasRecovery && !onboardingDismissed) {
            _showRecoveryOnboarding.emit(Unit)
        }
    }

    fun dismissRecoveryOnboarding() {
        viewModelScope.launch {
            preferencesRepository.updatePreferences {
                it.copy(recoveryOnboardingDismissed = true)
            }
        }
    }

    fun completeRecoverySetup() {
        viewModelScope.launch {
            preferencesRepository.updatePreferences {
                it.copy(recoveryOnboardingDismissed = true)
            }
        }
    }

    fun setupRecovery(question: String, answer: String) {
        viewModelScope.launch {
            recoveryManager.setupRecovery(question, answer)
        }
    }

    private fun checkServiceState() {
        protectionManager.startProtectionIfEnabled()
    }
}

enum class ServiceStatus {
    ACTIVE,
    IDLE,
    STOPPED
}
