package com.carbon.prolocker.feature.gallery

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.carbon.prolocker.core.theme.AppTypography
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPreviewScreen(
    url: String,
    id: Int,
    onBack: () -> Unit,
    viewModel: BackgroundGalleryViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val packageName = context.packageName
    val adManager: AdManager = koinInject()
    val analyticsManager: com.carbon.prolocker.core.analytics.AnalyticsManager = koinInject()

    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val isDownloading = downloadingIds.contains(id)

    val item = viewModel.findBackgroundItem(id, url)
    val isDownloaded = viewModel.isItemDownloaded(id)
    val isActive = item != null && viewModel.isBackgroundActive(item)

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Resolve preview image path (local path if downloaded, else url)
    val previewImageModel = remember(url, isDownloaded) {
        val downloaded = viewModel.downloadedBackgrounds.value.find { it.id == id }
        if (downloaded != null && File(downloaded.localPath).exists()) {
            downloaded.localPath
        } else {
            url
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_download),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_wallpaper_confirm),
                    style = AppTypography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteDownloadedBackground(id)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item?.name?.ifEmpty { stringResource(R.string.preview) } ?: stringResource(R.string.preview),
                        style = AppTypography.titleLarge,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isDownloaded) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete_download),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Phone Frame Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(0.56f)
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = previewImageModel,
                        contentDescription = item?.name ?: "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Active badge if currently selected
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2E7D32),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
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

                // Native Ad
                Spacer(modifier = Modifier.height(14.dp))
                NativeAdContainer(
                    adManager = adManager,
                    placement = AdPlacement.BACKGROUND_PREVIEW_NATIVE,
                    adType = NativeAdType.TYPE_5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Bottom Sticky Action Buttons Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Download Button
                    if (!isDownloaded) {
                        OutlinedButton(
                            onClick = {
                                if (item != null && !isDownloading) {
                                    viewModel.downloadBackground(
                                        item = item,
                                        packageName = packageName,
                                        onSuccess = {
                                            analyticsManager.trackBackgroundSelected(id)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.wallpaper_download_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            },
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.downloading),
                                    style = AppTypography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.download_wallpaper),
                                    style = AppTypography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Downloaded badge button
                        FilledTonalButton(
                            onClick = { },
                            enabled = false,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.downloaded),
                                style = AppTypography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. Set / Remove Background Button
                    if (isActive) {
                        Button(
                            onClick = {
                                viewModel.removeBackground()
                                onBack()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.remove_background),
                                style = AppTypography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (item != null) {
                                    viewModel.setBackground(item, packageName) {
                                        analyticsManager.trackBackgroundSelected(id)
                                        onBack()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Wallpaper,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.set_as_lock_background),
                                style = AppTypography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
