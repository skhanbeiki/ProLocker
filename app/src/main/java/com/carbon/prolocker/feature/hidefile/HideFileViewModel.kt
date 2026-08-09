package com.carbon.prolocker.feature.hidefile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.feature.hidefile.data.HideFileRepository
import com.carbon.prolocker.feature.hidefile.data.HideItem
import com.carbon.prolocker.feature.hidefile.data.PickedMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HideOperationResult(
    val isRestore: Boolean,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class HideFileViewModel(private val repository: HideFileRepository) : ViewModel() {

    val items: StateFlow<List<HideItem>> = repository.items

    val counts: StateFlow<Map<String, Int>> = repository.items
        .map { list -> list.groupingBy { it.type }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _operationResult = MutableStateFlow<HideOperationResult?>(null)
    val operationResult: StateFlow<HideOperationResult?> = _operationResult.asStateFlow()

    fun itemsFor(type: String): List<HideItem> = repository.itemsFor(type)

    fun hasStorageAccess(): Boolean = repository.hasStorageAccess()

    fun needsAllFilesAccess(): Boolean = repository.needsAllFilesAccess()

    fun hide(selected: List<PickedMediaItem>, type: String) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.hide(selected.map { it.path to type })
            _operationResult.value = HideOperationResult(isRestore = false, count = selected.size)
        }
    }

    fun hidePaths(selected: List<String>, type: String) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.hide(selected.map { it to type })
            _operationResult.value = HideOperationResult(isRestore = false, count = selected.size)
        }
    }

    fun unhide(item: HideItem) {
        viewModelScope.launch {
            repository.unhide(item)
            _operationResult.value = HideOperationResult(isRestore = true, count = 1)
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    fun delete(item: HideItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun open(item: HideItem) = repository.open(item)

    fun share(item: HideItem) = repository.share(item)
}
