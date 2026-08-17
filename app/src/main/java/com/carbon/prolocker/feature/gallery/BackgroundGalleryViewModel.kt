package com.carbon.prolocker.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.database.DownloadedBackgroundDao
import com.carbon.prolocker.core.database.DownloadedBackgroundEntity
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.domain.CheckNewBackgroundsUseCase
import com.carbon.prolocker.core.domain.GetBackgroundsUseCase
import com.carbon.prolocker.core.repository.BackgroundDownloadManager
import com.carbon.prolocker.core.repository.BackgroundRepository
import com.carbon.prolocker.core.repository.GalleryException
import com.carbon.prolocker.network.model.BackgroundItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Success(val backgrounds: List<BackgroundItem>) : GalleryUiState
    data class Error(val messageResId: Int) : GalleryUiState
}

class BackgroundGalleryViewModel(
    private val getBackgroundsUseCase: GetBackgroundsUseCase,
    private val checkNewBackgroundsUseCase: CheckNewBackgroundsUseCase,
    private val downloadManager: BackgroundDownloadManager,
    private val backgroundRepository: BackgroundRepository,
    private val downloadedBackgroundDao: DownloadedBackgroundDao,
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

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingIds: StateFlow<Set<Int>> = _downloadingIds.asStateFlow()

    val downloadedBackgrounds: StateFlow<List<DownloadedBackgroundEntity>> =
        combine(
            downloadManager.downloadedBackgroundsFlow,
            _selectedBackgroundUrl
        ) { list, selectedUrl ->
            list.sortedWith(
                compareByDescending<DownloadedBackgroundEntity> { entity ->
                    if (selectedUrl.isNullOrEmpty()) false
                    else selectedUrl == entity.localPath ||
                            selectedUrl.endsWith("bg_${entity.id}.jpg") ||
                            selectedUrl.contains("/bg_${entity.id}.") ||
                            selectedUrl == entity.photoGallery ||
                            selectedUrl == entity.photoThumb2x ||
                            selectedUrl == entity.photoThumb
                }.thenByDescending { it.downloadedAt }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val downloadedCount: StateFlow<Int> =
        downloadManager.downloadedCountFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    private var currentCursor: String? = ""
    private var allBackgrounds: MutableList<BackgroundItem> = mutableListOf()

    init {
        observeSelectedBackground()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    private fun observeSelectedBackground() {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _selectedBackgroundUrl.value = prefs.selectedBackgroundUrl.ifEmpty { null }
            }
        }
    }

    fun loadInitialBackgrounds() {
        if (allBackgrounds.isNotEmpty()) return
        currentCursor = ""
        loadMoreBackgrounds()
    }

    fun refreshBackgrounds() {
        allBackgrounds.clear()
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

                    // Backfill any downloaded items that have downloadCount == 0 or empty name
                    syncDownloadedMetadata()
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

    private fun syncDownloadedMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                allBackgrounds.forEach { item ->
                    val downloaded = downloadedBackgroundDao.getById(item.id)
                    if (downloaded != null && (downloaded.downloadCount == 0 || downloaded.name.isEmpty())) {
                        downloadedBackgroundDao.insert(
                            downloaded.copy(
                                downloadCount = if (item.downloadCount > 0) item.downloadCount else downloaded.downloadCount,
                                name = if (downloaded.name.isEmpty()) item.name else downloaded.name,
                                category = downloaded.category ?: item.category
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
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

    fun downloadBackground(
        item: BackgroundItem,
        packageName: String,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        if (_downloadingIds.value.contains(item.id)) return
        viewModelScope.launch {
            _downloadingIds.value = _downloadingIds.value + item.id
            val fullItem = findBackgroundItem(item.id) ?: item
            val result = downloadManager.downloadBackground(fullItem, packageName)
            _downloadingIds.value = _downloadingIds.value - item.id
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun isItemDownloaded(id: Int): Boolean {
        return downloadedBackgrounds.value.any { it.id == id }
    }

    fun isBackgroundActive(item: BackgroundItem): Boolean {
        val selected = _selectedBackgroundUrl.value ?: return false
        if (selected.isEmpty()) return false
        val downloaded = downloadedBackgrounds.value.find { it.id == item.id }
        return selected == item.photoGallery ||
                selected == item.photoThumb ||
                selected == item.photoThumb2x ||
                (downloaded != null && selected == downloaded.localPath) ||
                selected.endsWith("bg_${item.id}.jpg") ||
                selected.contains("/bg_${item.id}.")
    }

    fun setBackground(item: BackgroundItem, packageName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val fullItem = findBackgroundItem(item.id) ?: item
            // Check if already downloaded locally
            val downloaded = downloadedBackgrounds.value.find { it.id == item.id }
                ?: downloadManager.getDownloadedById(item.id)

            val pathToSave = if (downloaded != null) {
                downloaded.localPath
            } else {
                // Download in background
                val result = downloadManager.downloadBackground(fullItem, packageName)
                if (result.isSuccess) {
                    result.getOrNull()?.localPath ?: fullItem.photoThumb2x.ifEmpty { fullItem.photoGallery }
                } else {
                    fullItem.photoThumb2x.ifEmpty { fullItem.photoGallery }
                }
            }

            preferencesRepository.updatePreferences { it.copy(selectedBackgroundUrl = pathToSave) }
            _selectedBackgroundUrl.value = pathToSave
            onDone()
        }
    }

    fun deleteDownloadedBackground(id: Int) {
        viewModelScope.launch {
            val downloaded = downloadedBackgrounds.value.find { it.id == id }
            if (downloaded != null && _selectedBackgroundUrl.value == downloaded.localPath) {
                removeBackground()
            }
            downloadManager.deleteDownloadedBackground(id)
        }
    }

    fun removeBackground() {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(selectedBackgroundUrl = "") }
            _selectedBackgroundUrl.value = null
        }
    }

    fun findBackgroundItem(id: Int, fallbackUrl: String = ""): BackgroundItem? {
        val fromOnline = allBackgrounds.find { it.id == id }
        if (fromOnline != null) return fromOnline

        val fromRepo = backgroundRepository.getCachedBackground(id)
        if (fromRepo != null) return fromRepo

        val fromDownloaded = downloadedBackgrounds.value.find { it.id == id }
        if (fromDownloaded != null) return fromDownloaded.toBackgroundItem()

        if (fallbackUrl.isNotEmpty()) {
            return BackgroundItem(
                id = id,
                name = "",
                image = com.carbon.prolocker.network.model.BackgroundImage(
                    file = com.carbon.prolocker.network.model.BackgroundFile(
                        photoGallery = fallbackUrl,
                        photoThumb = fallbackUrl
                    )
                )
            )
        }
        return null
    }
}
