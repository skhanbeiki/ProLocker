package com.carbon.prolocker.feature.gallery

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.database.DownloadedBackgroundEntity
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.theme.AppTypography
import com.carbon.prolocker.core.ui.components.EmptyState
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

sealed interface DownloadedGridItem {
    data object DefaultItem : DownloadedGridItem
    data class CustomItem(val entity: DownloadedBackgroundEntity) : DownloadedGridItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundGalleryScreen(
    onBack: () -> Unit,
    onBackgroundClick: (String, Int) -> Unit,
    viewModel: BackgroundGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val downloadedBackgrounds by viewModel.downloadedBackgrounds.collectAsState()
    val downloadedCount by viewModel.downloadedCount.collectAsState()
    val selectedBackgroundUrl by viewModel.selectedBackgroundUrl.collectAsState()

    val onlineGridState = rememberLazyGridState()
    val downloadedGridState = rememberLazyGridState()

    val adManager: AdManager = koinInject()
    val preferencesRepository: PreferencesRepository = koinInject()
    val remoteConfigRepository: RemoteConfigRepository = koinInject()
    val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.activity.compose.BackHandler {
        handleThemeInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitialBackgrounds()
        analyticsManager.trackScreenView("BackgroundGalleryScreen")
    }

    val onlineBackgrounds = when (val state = uiState) {
        is GalleryUiState.Success -> state.backgrounds
        else -> emptyList()
    }

    // Dynamic grid items for Tab 2 (Permanent Default Background + Downloaded items)
    val tab2Items = remember(downloadedBackgrounds, selectedBackgroundUrl) {
        val isDefaultActive = selectedBackgroundUrl.isNullOrEmpty()
        val list = mutableListOf<DownloadedGridItem>()
        if (isDefaultActive) {
            list.add(DownloadedGridItem.DefaultItem)
            downloadedBackgrounds.forEach { list.add(DownloadedGridItem.CustomItem(it)) }
        } else {
            if (downloadedBackgrounds.isNotEmpty()) {
                val first = downloadedBackgrounds.first()
                list.add(DownloadedGridItem.CustomItem(first))
                list.add(DownloadedGridItem.DefaultItem)
                downloadedBackgrounds.drop(1).forEach { list.add(DownloadedGridItem.CustomItem(it)) }
            } else {
                list.add(DownloadedGridItem.DefaultItem)
            }
        }
        list
    }

    LaunchedEffect(onlineGridState) {
        snapshotFlow { onlineGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null
                    && lastVisibleIndex >= onlineBackgrounds.size - 4
                    && !isLoadingMore
                    && uiState is GalleryUiState.Success
                    && selectedTab == 0
                ) {
                    viewModel.loadMoreBackgrounds()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.background_gallery),
                        style = AppTypography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        handleThemeInterstitial(
                            adManager,
                            preferencesRepository,
                            remoteConfigRepository,
                            coroutineScope,
                            context,
                            onBack
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented Custom Modern Tab Row
            GallerySegmentedTabs(
                selectedTab = selectedTab,
                downloadedCount = downloadedCount,
                onTabSelected = { viewModel.setSelectedTab(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content Area based on Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0) {
                    // Online Wallpapers Tab
                    when (val state = uiState) {
                        is GalleryUiState.Loading -> {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        is GalleryUiState.Error -> {
                            EmptyState(
                                title = stringResource(R.string.gallery_error_title),
                                description = stringResource(state.messageResId),
                                icon = Icons.Default.CloudOff,
                                actionText = stringResource(R.string.retry),
                                onAction = { viewModel.loadInitialBackgrounds() }
                            )
                        }

                        is GalleryUiState.Success -> {
                            if (state.backgrounds.isEmpty()) {
                                EmptyState(
                                    title = stringResource(R.string.gallery_error_title),
                                    description = stringResource(R.string.gallery_error_unexpected),
                                    icon = Icons.Default.CloudOff,
                                    actionText = stringResource(R.string.retry),
                                    onAction = { viewModel.loadInitialBackgrounds() }
                                )
                            } else {
                                LazyVerticalGrid(
                                    state = onlineGridState,
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = state.backgrounds,
                                        key = { _, bg -> bg.id }
                                    ) { _, bg ->
                                        val isActive = viewModel.isBackgroundActive(bg)
                                        val isDownloaded = viewModel.isItemDownloaded(bg.id)

                                        WallpaperGridCard(
                                            imageUrl = bg.photoThumb,
                                            title = bg.name,
                                            category = bg.category,
                                            downloadCount = bg.downloadCount,
                                            isActive = isActive,
                                            isDownloaded = isDownloaded,
                                            onClick = {
                                                val targetUrl = bg.photoGallery.ifEmpty { bg.photoThumb }
                                                onBackgroundClick(targetUrl, bg.id)
                                            }
                                        )
                                    }

                                    if (isLoadingMore) {
                                        item(span = { GridItemSpan(2) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 3.dp,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Downloaded Wallpapers Tab (includes permanent Default Background card)
                    LazyVerticalGrid(
                        state = downloadedGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = tab2Items,
                            key = { _, item ->
                                when (item) {
                                    is DownloadedGridItem.DefaultItem -> "default_bg"
                                    is DownloadedGridItem.CustomItem -> item.entity.id
                                }
                            }
                        ) { _, item ->
                            when (item) {
                                is DownloadedGridItem.DefaultItem -> {
                                    val isDefaultActive = selectedBackgroundUrl.isNullOrEmpty()
                                    DefaultBackgroundCard(
                                        isActive = isDefaultActive,
                                        onClick = {
                                            onBackgroundClick("default", 0)
                                        }
                                    )
                                }

                                is DownloadedGridItem.CustomItem -> {
                                    val downloaded = item.entity
                                    val cachedItem = viewModel.findBackgroundItem(downloaded.id)
                                    val bgItem = cachedItem ?: downloaded.toBackgroundItem()
                                    val isActive = viewModel.isBackgroundActive(bgItem)
                                    val imageModel = if (File(downloaded.localPath).exists()) {
                                        downloaded.localPath
                                    } else {
                                        downloaded.photoThumb
                                    }
                                    val effectiveDownloadCount = if (downloaded.downloadCount > 0) {
                                        downloaded.downloadCount
                                    } else {
                                        cachedItem?.downloadCount ?: 0
                                    }
                                    val effectiveTitle = downloaded.name.ifEmpty { cachedItem?.name ?: "" }
                                    val effectiveCategory = downloaded.category ?: cachedItem?.category

                                    WallpaperGridCard(
                                        imageUrl = imageModel,
                                        title = effectiveTitle,
                                        category = effectiveCategory,
                                        downloadCount = effectiveDownloadCount,
                                        isActive = isActive,
                                        isDownloaded = true,
                                        onClick = {
                                            val targetUrl = if (File(downloaded.localPath).exists()) {
                                                downloaded.localPath
                                            } else {
                                                downloaded.photoGallery.ifEmpty { downloaded.photoThumb }
                                            }
                                            onBackgroundClick(targetUrl, downloaded.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Native Ad Container
            NativeAdContainer(
                adManager = adManager,
                placement = AdPlacement.BACKGROUND_LIST_NATIVE,
                adType = NativeAdType.TYPE_1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
fun DefaultBackgroundCard(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Theme Default Dark Gradient Canvas for ProLocker
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF334155)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Dark Scrim Gradient at the Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Active Background Badge
            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.active_status),
                            style = AppTypography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom Information Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.default_background),
                    color = Color.White,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = stringResource(R.string.default_background_desc),
                    color = Color.White.copy(alpha = 0.75f),
                    style = AppTypography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun GallerySegmentedTabs(
    selectedTab: Int,
    downloadedCount: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 0: Wallpapers
            SegmentedTabItem(
                title = stringResource(R.string.tab_wallpapers),
                icon = Icons.Outlined.Palette,
                isSelected = selectedTab == 0,
                badgeCount = null,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Tab 1: Downloaded
            SegmentedTabItem(
                title = stringResource(R.string.tab_downloaded),
                icon = Icons.Outlined.DownloadDone,
                isSelected = selectedTab == 1,
                badgeCount = if (downloadedCount > 0) downloadedCount else null,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = AppTypography.labelLarge,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (badgeCount != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.height(20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "$badgeCount",
                            style = AppTypography.labelSmall,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperGridCard(
    imageUrl: String,
    title: String,
    category: String?,
    downloadCount: Int,
    isActive: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Wallpaper Thumbnail
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark Scrim Gradient at the Bottom for High Contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Top Status Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Downloaded badge indicator
                if (isDownloaded) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(26.dp))
                }

                // Active Background Badge
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2E7D32),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.active_status),
                                style = AppTypography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Information Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title.ifEmpty { stringResource(R.string.background_gallery) },
                    color = Color.White,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!category.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = category,
                                color = Color.White,
                                style = AppTypography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatNumber(downloadCount),
                            color = Color.White.copy(alpha = 0.9f),
                            style = AppTypography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun handleThemeInterstitial(
    adManager: AdManager,
    preferencesRepository: PreferencesRepository,
    remoteConfigRepository: RemoteConfigRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onBack: () -> Unit
) {
    val activity = context as? android.app.Activity ?: run {
        onBack()
        return
    }

    coroutineScope.launch {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val config = remoteConfigRepository.getConfig()
        val limit = config.configs.interstitialAdThemeStep

        if (limit > 0) {
            val currentCount = prefs.themeInterstitialCounter + 1
            if (currentCount >= limit) {
                preferencesRepository.updateThemeInterstitialCounter(false)
                adManager.showInterstitialAd(
                    activity = activity,
                    placement = AdPlacement.INTERSTITIAL_BACKGROUND,
                    onClosed = { onBack() },
                    onError = { _ -> onBack() }
                )
            } else {
                preferencesRepository.updateThemeInterstitialCounter(true)
                onBack()
            }
        } else {
            onBack()
        }
    }
}
