package com.carbon.prolocker.feature.hidefile.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.feature.hidefile.HideFileViewModel
import com.carbon.prolocker.feature.hidefile.data.HideItem
import com.carbon.prolocker.feature.tools.FeatureCard
import com.carbon.prolocker.feature.tools.ToolsFeatureItem
import org.koin.androidx.compose.koinViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.triggerExitInterstitialAd
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import org.koin.compose.koinInject

private data class HideCategory(
    val type: String,
    val title: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideFilesScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onBack: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {}
) {
    val viewModel: HideFileViewModel = koinViewModel()
    val adManager: AdManager = koinInject()
    val preferencesRepository: PreferencesRepository = koinInject()
    val remoteConfigRepository: RemoteConfigRepository = koinInject()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val handleExit = {
        triggerExitInterstitialAd(
            context = context,
            coroutineScope = scope,
            preferencesRepository = preferencesRepository,
            remoteConfigRepository = remoteConfigRepository,
            adManager = adManager,
            placement = AdPlacement.INTERSTITIAL_HIDE_FILES,
            onBack = onBack
        )
    }

    BackHandler { handleExit() }

    val counts by viewModel.counts.collectAsState()

    var pendingCategoryType by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val requester = rememberStoragePermissionRequester(
        onGranted = { category ->
            val cat = category ?: pendingCategoryType
            if (cat != null) onOpenCategory(cat)
        },
        onDenied = { }
    )

    val categories = listOf(
        HideCategory(
            type = HideItem.TYPE_IMAGE,
            title = stringResource(R.string.hide_files_images),
            icon = Icons.Outlined.Image,
            gradient = listOf(Color(0xFF7B61FF), Color(0xFF5B6CFF))
        ),
        HideCategory(
            type = HideItem.TYPE_VIDEO,
            title = stringResource(R.string.hide_files_videos),
            icon = Icons.Outlined.Movie,
            gradient = listOf(Color(0xFF00D1B2), Color(0xFF00A896))
        ),
        HideCategory(
            type = HideItem.TYPE_AUDIO,
            title = stringResource(R.string.hide_files_music),
            icon = Icons.Outlined.MusicNote,
            gradient = listOf(Color(0xFFF472B6), Color(0xFFEC4899))
        ),
        HideCategory(
            type = HideItem.TYPE_FILE,
            title = stringResource(R.string.hide_files_files),
            icon = Icons.Outlined.InsertDriveFile,
            gradient = listOf(Color(0xFF4F8CFF), Color(0xFF2563EB))
        )
    )

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "hideFilesAlpha"
    )

    Scaffold(
        modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = handleExit) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(ProLockerPrimary, ProLockerSecondary)
                                    ),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.hide_files_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.hide_files_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories, key = { it.type }) { category ->
                    val count = counts[category.type] ?: 0
                    val subtitle = if (count == 0) {
                        stringResource(R.string.hide_files_no_items)
                    } else {
                        stringResource(R.string.hide_files_item_count, count)
                    }
                    FeatureCard(
                        item = ToolsFeatureItem(
                            title = category.title,
                            subtitle = subtitle,
                            icon = category.icon,
                            gradient = category.gradient,
                            available = true
                        ),
                        onClick = {
                            if (requester.needsPermission(category.type)) {
                                pendingCategoryType = category.type
                                showPermissionDialog = true
                            } else {
                                onOpenCategory(category.type)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                    )
                }
            }
        }
    }

    if (showPermissionDialog) {
        StorageAccessDialog(
            category = pendingCategoryType,
            onConfirm = {
                showPermissionDialog = false
                requester.request(pendingCategoryType) {
                    pendingCategoryType?.let(onOpenCategory)
                }
            },
            onDismiss = { showPermissionDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HideFilesScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HideFilesScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HideFilesScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HideFilesScreen()
    }
}
