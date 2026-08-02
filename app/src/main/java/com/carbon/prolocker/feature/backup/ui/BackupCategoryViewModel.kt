package com.carbon.prolocker.feature.backup.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.backup.data.BackupRepository
import com.carbon.prolocker.feature.backup.model.BackupCategory
import com.carbon.prolocker.feature.backup.model.BackupFileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupCategoryUiState(
    val category: BackupCategory = BackupCategory.CONTACTS,
    val selectedTab: Int = 0, // 0 = Backup, 1 = Restore
    val totalAvailableItems: Int = 0,
    val lastBackupDateJalali: String = "—",
    val isBackingUp: Boolean = false,
    val backupProgress: Float = 0f,
    val isRestoring: Boolean = false,
    val restoreProgress: Float = 0f,
    val backupFiles: List<BackupFileInfo> = emptyList(),
    val isLoadingFiles: Boolean = true,
    val userMessage: String? = null,
    val selectedFileForSheet: BackupFileInfo? = null
)

class BackupCategoryViewModel(
    private val context: Context,
    private val repository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupCategoryUiState())
    val uiState: StateFlow<BackupCategoryUiState> = _uiState.asStateFlow()

    fun initCategory(category: BackupCategory) {
        _uiState.value = _uiState.value.copy(category = category)
        loadData()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
        if (tabIndex == 1) {
            loadBackupFiles()
        }
    }

    fun loadData() {
        val category = _uiState.value.category
        viewModelScope.launch {
            val count = repository.getCategoryItemCount(category)
            _uiState.value = _uiState.value.copy(totalAvailableItems = count)
            loadBackupFiles()
        }
    }

    fun loadBackupFiles() {
        val category = _uiState.value.category
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            val files = repository.getBackupFiles(category)
            val lastDate = if (files.isNotEmpty()) {
                repository.formatJalaliDate(files.first().lastModifiedMs)
            } else {
                "—"
            }
            _uiState.value = _uiState.value.copy(
                backupFiles = files,
                lastBackupDateJalali = lastDate,
                isLoadingFiles = false
            )
        }
    }

    fun startBackup() {
        val category = _uiState.value.category
        if (_uiState.value.isBackingUp) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBackingUp = true,
                backupProgress = 0f,
                userMessage = null
            )

            try {
                when (category) {
                    BackupCategory.CONTACTS -> repository.backupContacts { progress ->
                        _uiState.value = _uiState.value.copy(backupProgress = progress)
                    }
                    BackupCategory.CALL_LOGS -> repository.backupCallLogs { progress ->
                        _uiState.value = _uiState.value.copy(backupProgress = progress)
                    }
                    BackupCategory.SMS -> repository.backupSms { progress ->
                        _uiState.value = _uiState.value.copy(backupProgress = progress)
                    }
                    BackupCategory.APPLICATIONS -> ""
                }

                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    backupProgress = 1f,
                    userMessage = context.getString(R.string.backup_msg_success)
                )
                loadBackupFiles()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBackingUp = false,
                    backupProgress = 0f,
                    userMessage = context.getString(R.string.backup_msg_failed, e.localizedMessage ?: "")
                )
            }
        }
    }

    fun restoreFile(fileInfo: BackupFileInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                selectedFileForSheet = null,
                userMessage = null
            )
            try {
                val restoredCount = when (fileInfo.category) {
                    BackupCategory.CONTACTS -> repository.restoreContacts(fileInfo)
                    BackupCategory.CALL_LOGS -> repository.restoreCallLogs(fileInfo)
                    BackupCategory.SMS -> repository.restoreSms(fileInfo)
                    BackupCategory.APPLICATIONS -> 0
                }
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    userMessage = context.getString(R.string.backup_msg_restored_count, restoredCount)
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    userMessage = context.getString(R.string.backup_msg_restore_failed, e.localizedMessage ?: "")
                )
            }
        }
    }

    fun shareFile(fileInfo: BackupFileInfo) {
        _uiState.value = _uiState.value.copy(selectedFileForSheet = null)
        repository.shareBackupFile(fileInfo)
    }

    fun deleteFile(fileInfo: BackupFileInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedFileForSheet = null)
            val success = repository.deleteBackupFile(fileInfo)
            if (success) {
                _uiState.value = _uiState.value.copy(userMessage = context.getString(R.string.backup_msg_deleted))
                loadBackupFiles()
            } else {
                _uiState.value = _uiState.value.copy(userMessage = context.getString(R.string.backup_msg_delete_failed))
            }
        }
    }

    fun selectFileForSheet(fileInfo: BackupFileInfo?) {
        _uiState.value = _uiState.value.copy(selectedFileForSheet = fileInfo)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
