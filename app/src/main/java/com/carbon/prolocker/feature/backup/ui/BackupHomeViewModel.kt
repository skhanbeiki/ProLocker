package com.carbon.prolocker.feature.backup.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.feature.backup.data.BackupPreferences
import com.carbon.prolocker.feature.backup.data.BackupRepository
import com.carbon.prolocker.feature.backup.model.BackupCategory
import com.carbon.prolocker.feature.backup.model.BackupItemCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class BackupHomeUiState(
    val counts: Map<BackupCategory, Int> = emptyMap(),
    val locationPath: String = "",
    val isLoadingCounts: Boolean = true
)

class BackupHomeViewModel(
    private val repository: BackupRepository,
    private val preferences: BackupPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupHomeUiState())
    val uiState: StateFlow<BackupHomeUiState> = _uiState.asStateFlow()

    init {
        observeLocationPath()
        loadItemCounts()
    }

    private fun observeLocationPath() {
        viewModelScope.launch {
            repository.backupDisplayPathFlow.collectLatest { path ->
                _uiState.value = _uiState.value.copy(locationPath = path)
            }
        }
    }

    fun loadItemCounts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCounts = true)
            val map = mutableMapOf<BackupCategory, Int>()
            for (cat in BackupCategory.entries) {
                map[cat] = repository.getCategoryItemCount(cat)
            }
            _uiState.value = _uiState.value.copy(
                counts = map,
                isLoadingCounts = false
            )
        }
    }

    fun updateBackupLocation(uri: Uri) {
        viewModelScope.launch {
            preferences.saveBackupLocation(uri)
        }
    }
}
