package com.carbon.prolocker.feature.hidefile.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.ui.components.EmptyState
import com.carbon.prolocker.core.ui.components.LoadingView
import com.carbon.prolocker.feature.hidefile.HideFileViewModel
import com.carbon.prolocker.feature.hidefile.data.HideItem
import com.carbon.prolocker.feature.hidefile.data.MediaStoreQueries
import com.carbon.prolocker.feature.hidefile.data.MediaThumbnails
import com.carbon.prolocker.feature.hidefile.data.PickedMediaItem
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerScreen(
    type: String,
    onBack: () -> Unit = {}
) {
    val viewModel: HideFileViewModel = koinViewModel()
    val context = LocalContext.current
    val hiddenItems by viewModel.items.collectAsState()
    val hiddenPaths = remember(hiddenItems) {
        buildSet {
            val storage = Environment.getExternalStorageDirectory()
            for (item in hiddenItems) {
                add(File(storage, item.path.trimStart('/') + "/" + item.name).absolutePath)
            }
        }
    }

    var media by remember { mutableStateOf<List<PickedMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(type) {
        loading = true
        val all = when (type) {
            HideItem.TYPE_VIDEO -> MediaStoreQueries.listVideos(context)
            HideItem.TYPE_AUDIO -> MediaStoreQueries.listAudio(context)
            else -> MediaStoreQueries.listImages(context)
        }
        media = all.filter { it.path !in hiddenPaths }
        selected = emptySet()
        loading = false
    }

    val title = stringResource(categoryTitleId(type))
    val hideSelectedLabel = stringResource(R.string.hide_files_hide_count, selected.size)
    val isAudio = type == HideItem.TYPE_AUDIO

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.hide_files_pick_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val picked = media.filter { it.id in selected }
                            viewModel.hide(picked, type)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProLockerPrimary
                        )
                    ) {
                        Text(hideSelectedLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> LoadingView()
                media.isEmpty() -> EmptyState(
                    title = stringResource(R.string.hide_files_picker_empty_title),
                    description = stringResource(R.string.hide_files_picker_empty_description),
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(media, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        val onClick = {
                            selected = if (isSelected) selected - item.id else selected + item.id
                        }
                        if (isAudio) {
                            AudioTile(
                                item = item,
                                selected = isSelected,
                                onClick = onClick
                            )
                        } else {
                            ImageOrVideoTile(
                                item = item,
                                isVideo = type == HideItem.TYPE_VIDEO,
                                selected = isSelected,
                                onClick = onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageOrVideoTile(
    item: PickedMediaItem,
    isVideo: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) {
        if (isVideo) {
            thumbnail = MediaThumbnails.videoThumbnail(context, item.id)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(3.dp, ProLockerPrimary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        when {
            isVideo && thumbnail != null -> Image(
                painter = rememberAsyncImagePainter(thumbnail!!),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            item.uri != null && !isVideo -> Image(
                painter = rememberAsyncImagePainter(item.uri),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Text(
                text = item.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isVideo && item.duration > 0) {
                Text(
                    text = formatDuration(item.duration),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        SelectionBadge(selected)
    }
}

@Composable
private fun AudioTile(
    item: PickedMediaItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var artPath by remember(item.albumId) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.albumId) {
        artPath = MediaThumbnails.albumArtPath(context, item.albumId)
    }

    Column(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(3.dp, ProLockerPrimary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val art = artPath
            if (art != null && File(art).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(art)),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
            SelectionBadge(selected)
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = item.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artist?.takeIf { it.isNotBlank() } ?: item.album?.takeIf { it.isNotBlank() }
                    ?: " ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.duration > 0) {
                Text(
                    text = formatDuration(item.duration),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SelectionBadge(selected: Boolean) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .align(Alignment.TopEnd)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) ProLockerPrimary else Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun categoryTitleId(type: String): Int = when (type) {
    HideItem.TYPE_VIDEO -> R.string.hide_files_videos
    HideItem.TYPE_AUDIO -> R.string.hide_files_music
    else -> R.string.hide_files_images
}

@Preview(showBackground = true)
@Composable
fun MediaPickerScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        MediaPickerScreen(type = HideItem.TYPE_IMAGE)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MediaPickerScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        MediaPickerScreen(type = HideItem.TYPE_IMAGE)
    }
}
