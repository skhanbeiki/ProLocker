package com.carbon.prolocker.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.domain.CheckNewBackgroundsUseCase
import com.carbon.prolocker.core.domain.GetBackgroundsUseCase
import com.carbon.prolocker.core.domain.ReportBackgroundDownloadUseCase
import com.carbon.prolocker.core.repository.GalleryException
import com.carbon.prolocker.network.model.BackgroundItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Success(val backgrounds: List<BackgroundItem>) : GalleryUiState
    data class Error(val messageResId: Int) : GalleryUiState
}

class BackgroundGalleryViewModel(
    private val getBackgroundsUseCase: GetBackgroundsUseCase,
    private val checkNewBackgroundsUseCase: CheckNewBackgroundsUseCase,
    private val reportBackgroundDownloadUseCase: ReportBackgroundDownloadUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _newBadgeCount = MutableStateFlow(0)
    val newBadgeCount: StateFlow<Int> = _newBadgeCount.asStateFlow()

    private val _selectedBackgroundUrl = MutableStateFlow<String?>(null)
    val selectedBackgroundUrl: StateFlow<String?> = _selectedBackgroundUrl.asStateFlow()

    private var currentCursor: String? = ""
    private var allBackgrounds: MutableList<BackgroundItem> = mutableListOf()

    init {
        loadSelectedBackground()
    }

    private fun loadSelectedBackground() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            _selectedBackgroundUrl.value = prefs.selectedBackgroundUrl.ifEmpty { null }
        }
    }

    fun loadInitialBackgrounds() {
        if (allBackgrounds.isNotEmpty()) return
        currentCursor = ""
        loadMoreBackgrounds()
    }

    fun loadMoreBackgrounds() {
        if (_isLoadingMore.value || currentCursor == null) return

        val isFirstPage = allBackgrounds.isEmpty()
        viewModelScope.launch {
            _isLoadingMore.value = true
            if (isFirstPage) {
                _uiState.value = GalleryUiState.Loading
            }
            try {
                val response = getBackgroundsUseCase(currentCursor!!)
                allBackgrounds.addAll(response.results)
                _uiState.value = GalleryUiState.Success(allBackgrounds.toList())
                currentCursor = extractCursor(response.next)

                if (allBackgrounds.isNotEmpty()) {
                    val highestId = allBackgrounds.maxOfOrNull { it.id } ?: 0
                    preferencesRepository.updatePreferences { it.copy(lastBackgroundId = highestId) }
                    _newBadgeCount.value = 0
                }
            } catch (e: GalleryException) {
                val messageResId = mapErrorToMessageResId(e)
                _uiState.value = GalleryUiState.Error(messageResId)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(com.carbon.prolocker.R.string.gallery_error_unexpected)
            }
            _isLoadingMore.value = false
        }
    }

    private fun mapErrorToMessageResId(e: GalleryException): Int {
        return when (e) {
            is GalleryException.NoNetwork -> com.carbon.prolocker.R.string.gallery_error_no_network
            is GalleryException.Timeout -> com.carbon.prolocker.R.string.gallery_error_timeout
            is GalleryException.ServerError -> com.carbon.prolocker.R.string.gallery_error_server
            is GalleryException.ClientError -> com.carbon.prolocker.R.string.gallery_error_client
            is GalleryException.Unexpected -> com.carbon.prolocker.R.string.gallery_error_unexpected
        }
    }

    private fun extractCursor(nextUrl: String?): String? {
        if (nextUrl == null) return null
        val uri = android.net.Uri.parse(nextUrl)
        return uri.getQueryParameter("cursor")
    }

    fun checkNewBackgrounds() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val newCount = checkNewBackgroundsUseCase(prefs.lastBackgroundId)
            _newBadgeCount.value = newCount
        }
    }

    fun setBackground(url: String, id: Int, packageName: String) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedBackgroundUrl = url) }
            _selectedBackgroundUrl.value = url
            reportBackgroundDownloadUseCase(id, packageName)
        }
    }

    fun removeBackground() {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedBackgroundUrl = "") }
            _selectedBackgroundUrl.value = null
        }
    }
}
