package com.carbon.prolocker.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.ui.components.EmptyState
import com.carbon.prolocker.network.model.BackgroundItem
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundGalleryScreen(
    onBack: () -> Unit,
    onBackgroundClick: (String, Int) -> Unit,
    viewModel: BackgroundGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val selectedUrl by viewModel.selectedBackgroundUrl.collectAsState()
    val gridState = rememberLazyGridState()
    val adManager: AdManager = koinInject()
    val preferencesRepository: PreferencesRepository = koinInject()
    val remoteConfigRepository: RemoteConfigRepository = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.activity.compose.BackHandler {
        handleThemeInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
    }

    val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager = koinInject()
    LaunchedEffect(Unit) {
        viewModel.loadInitialBackgrounds()
        analyticsManager.trackScreenView("BackgroundGalleryScreen")
    }

    val backgrounds = when (val state = uiState) {
        is GalleryUiState.Success -> state.backgrounds
        else -> emptyList()
    }

    LaunchedEffect(backgrounds.size) {
        android.util.Log.d("BackgroundGallery", "LazyColumn count: ${backgrounds.size}")
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null
                    && lastVisibleIndex >= backgrounds.size - 4
                    && !isLoadingMore
                    && uiState is GalleryUiState.Success
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
                        style = MaterialTheme.typography.titleLarge,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is GalleryUiState.Loading -> {
                        CircularProgressIndicator()
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
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(state.backgrounds) { _, bg ->
                                    BackgroundCard(
                                        bg = bg,
                                        isSelected = selectedUrl == bg.photoGallery,
                                        onClick = { onBackgroundClick(bg.photoGallery, bg.id) }
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
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                    onClosed = {
                        onBack()
                    },
                    onError = { _ ->
                        onBack()
                    }
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

@Composable
fun BackgroundCard(bg: BackgroundItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .then(
                if (isSelected) Modifier.border(3.dp, ProLockerPrimary, RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = bg.photoThumb,
                contentDescription = bg.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ProLockerPrimary.copy(alpha = 0.25f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = ProLockerPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bg.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${bg.downloadCount}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun BackgroundCardPreview() {
    BackgroundCard(
        bg = BackgroundItem(id = 1, name = "Sample Photo", downloadCount = 1050),
        isSelected = true,
        onClick = {}
    )
}
