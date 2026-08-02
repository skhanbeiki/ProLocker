package com.carbon.prolocker.feature.backup.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.backup.data.BackupRepository
import com.carbon.prolocker.feature.backup.model.AppBackupProgressItem
import com.carbon.prolocker.feature.backup.model.AppBackupStatus
import com.carbon.prolocker.feature.backup.model.BackupCategory
import com.carbon.prolocker.feature.backup.model.BackupFileInfo
import com.carbon.prolocker.feature.backup.model.InstalledAppItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupAppsUiState(
    val selectedTab: Int = 0, // 0 = Backup, 1 = Restore
    val searchQuery: String = "",
    val installedApps: List<InstalledAppItem> = emptyList(),
    val filteredApps: List<InstalledAppItem> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isAllSelected: Boolean = false,
    val selectedCount: Int = 0,
    // Progress screen state
    val isProgressRunning: Boolean = false,
    val progressItems: List<AppBackupProgressItem> = emptyList(),
    val completedCount: Int = 0,
    val remainingCount: Int = 0,
    val overallProgressFraction: Float = 0f,
    val isBackupFinished: Boolean = false,
    // Restore tab state
    val backupApkFiles: List<BackupFileInfo> = emptyList(),
    val isLoadingApks: Boolean = true,
    val selectedFileForSheet: BackupFileInfo? = null,
    val userMessage: String? = null
)

class BackupAppsViewModel(
    private val context: Context,
    private val repository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupAppsUiState())
    val uiState: StateFlow<BackupAppsUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
        loadBackupApks()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
        if (tabIndex == 1) {
            loadBackupApks()
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = repository.getInstalledApps()
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false
            )
            applySearchQuery(_uiState.value.searchQuery)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchQuery(query)
    }

    private fun applySearchQuery(query: String) {
        val all = _uiState.value.installedApps
        val filtered = if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        val selectedCount = all.count { it.isSelected }
        val isAllSelected = all.isNotEmpty() && all.all { it.isSelected }
        _uiState.value = _uiState.value.copy(
            filteredApps = filtered,
            selectedCount = selectedCount,
            isAllSelected = isAllSelected
        )
    }

    fun toggleAppSelection(packageName: String) {
        val updated = _uiState.value.installedApps.map { app ->
            if (app.packageName == packageName) {
                app.copy(isSelected = !app.isSelected)
            } else {
                app
            }
        }
        _uiState.value = _uiState.value.copy(installedApps = updated)
        applySearchQuery(_uiState.value.searchQuery)
    }

    fun toggleSelectAll() {
        val newTarget = !_uiState.value.isAllSelected
        val updated = _uiState.value.installedApps.map { app ->
            app.copy(isSelected = newTarget)
        }
        _uiState.value = _uiState.value.copy(installedApps = updated)
        applySearchQuery(_uiState.value.searchQuery)
    }

    fun getSelectedPackageNames(): List<String> {
        return _uiState.value.installedApps.filter { it.isSelected }.map { it.packageName }
    }

    fun startAppsBackupProcess() {
        val selected = _uiState.value.installedApps.filter { it.isSelected }
        if (selected.isEmpty()) return

        val progressList = selected.map { app ->
            AppBackupProgressItem(
                appName = app.appName,
                packageName = app.packageName,
                status = AppBackupStatus.WAITING,
                iconDrawable = app.iconDrawable
            )
        }

        _uiState.value = _uiState.value.copy(
            isProgressRunning = true,
            isBackupFinished = false,
            progressItems = progressList,
            completedCount = 0,
            remainingCount = selected.size,
            overallProgressFraction = 0f
        )

        viewModelScope.launch {
            var completed = 0
            val total = selected.size

            val mutableItems = progressList.toMutableList()
            for (index in selected.indices) {
                val app = selected[index]
                mutableItems[index] = mutableItems[index].copy(status = AppBackupStatus.BACKING_UP)
                _uiState.value = _uiState.value.copy(progressItems = mutableItems.toList())

                val success = repository.backupSingleApp(app.packageName) { _ -> }

                val finalStatus = if (success) AppBackupStatus.COMPLETED else AppBackupStatus.FAILED
                mutableItems[index] = mutableItems[index].copy(status = finalStatus)
                completed++

                val fraction = completed.toFloat() / total
                _uiState.value = _uiState.value.copy(
                    progressItems = mutableItems.toList(),
                    completedCount = completed,
                    remainingCount = total - completed,
                    overallProgressFraction = fraction
                )
            }

            _uiState.value = _uiState.value.copy(
                isProgressRunning = false,
                isBackupFinished = true
            )
            loadBackupApks()
        }
    }

    fun resetBackupProgressState() {
        _uiState.value = _uiState.value.copy(
            isProgressRunning = false,
            isBackupFinished = false,
            progressItems = emptyList(),
            overallProgressFraction = 0f
        )
    }

    fun loadBackupApks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApks = true)
            val apks = repository.getBackupFiles(BackupCategory.APPLICATIONS)
            _uiState.value = _uiState.value.copy(
                backupApkFiles = apks,
                isLoadingApks = false
            )
        }
    }

    fun installApk(fileInfo: BackupFileInfo) {
        _uiState.value = _uiState.value.copy(selectedFileForSheet = null)
        repository.installApk(fileInfo)
    }

    fun shareApk(fileInfo: BackupFileInfo) {
        _uiState.value = _uiState.value.copy(selectedFileForSheet = null)
        repository.shareBackupFile(fileInfo)
    }

    fun deleteApk(fileInfo: BackupFileInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedFileForSheet = null)
            val success = repository.deleteBackupFile(fileInfo)
            if (success) {
                _uiState.value = _uiState.value.copy(userMessage = context.getString(R.string.backup_msg_apk_deleted))
                loadBackupApks()
            } else {
                _uiState.value = _uiState.value.copy(userMessage = context.getString(R.string.backup_msg_apk_delete_failed))
            }
        }
    }

    fun selectFileForSheet(fileInfo: BackupFileInfo?) {
        _uiState.value = _uiState.value.copy(selectedFileForSheet = fileInfo)
    }

    fun formatJalaliDate(timestampMs: Long): String {
        return repository.formatJalaliDate(timestampMs)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
