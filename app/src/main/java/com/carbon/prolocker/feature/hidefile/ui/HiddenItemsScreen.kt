package com.carbon.prolocker.feature.hidefile.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerError
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.ui.components.EmptyState
import com.carbon.prolocker.feature.hidefile.HideFileViewModel
import com.carbon.prolocker.feature.hidefile.data.HideItem
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenItemsScreen(
    type: String,
    onBack: () -> Unit = {},
    onOpenPicker: (String) -> Unit = {}
) {
    val viewModel: HideFileViewModel = koinViewModel()
    val allItems by viewModel.items.collectAsState()
    val items = remember(allItems, type) { allItems.filter { it.type == type } }

    var selectedItem by remember { mutableStateOf<HideItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf<HideItem?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val requester = rememberStoragePermissionRequester(
        onGranted = { category ->
            if (category == null || category == type) onOpenPicker(type)
        },
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.hide_files_permission_denied),
                    actionLabel = context.getString(R.string.hide_files_retry),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    showPermissionDialog = true
                }
            }
        }
    )

    val isMedia = type == HideItem.TYPE_IMAGE || type == HideItem.TYPE_VIDEO

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
                    Text(
                        stringResource(categoryTitleId(type)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (requester.needsPermission(type)) {
                        showPermissionDialog = true
                    } else {
                        onOpenPicker(type)
                    }
                },
                containerColor = ProLockerPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.hide_files_add)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.hide_files_empty_title),
                    description = stringResource(R.string.hide_files_empty_description),
                    icon = if (isMedia) Icons.Outlined.InsertDriveFile else Icons.Outlined.InsertDriveFile,
                    modifier = Modifier.weight(1f)
                )
            } else if (isMedia) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(20.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items, key = { it.name }) { item ->
                        HiddenMediaThumb(item, onClick = { selectedItem = item })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.name }) { item ->
                        HiddenFileRow(item, onClick = { selectedItem = item })
                    }
                }
            }
        }
    }

    if (selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            val item = selectedItem!!
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HiddenSheetPreview(item)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SheetActionRow(Icons.Outlined.OpenInNew, stringResource(R.string.hide_files_open)) {
                    viewModel.open(item)
                    selectedItem = null
                }
                SheetActionRow(Icons.Outlined.Share, stringResource(R.string.share)) {
                    viewModel.share(item)
                    selectedItem = null
                }
                SheetActionRow(Icons.Outlined.Visibility, stringResource(R.string.hide_files_unhide)) {
                    viewModel.unhide(item)
                    selectedItem = null
                }
                SheetActionRow(Icons.Outlined.Delete, stringResource(R.string.delete), tint = ProLockerError) {
                    showDeleteDialog = item
                    selectedItem = null
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            title = { Text(stringResource(R.string.hide_files_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.hide_files_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        val item = showDeleteDialog
                        showDeleteDialog = null
                        item?.let { viewModel.delete(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProLockerError),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPermissionDialog) {
        StorageAccessDialog(
            category = type,
            onConfirm = {
                showPermissionDialog = false
                requester.request(type) { onOpenPicker(type) }
            },
            onDismiss = { showPermissionDialog = false }
        )
    }
}

@Composable
private fun HiddenMediaThumb(item: HideItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        val file = File(
            Environment.getExternalStorageDirectory(),
            "${com.carbon.prolocker.feature.hidefile.data.HideFileStorage.HIDE_FILE_DIR}/.${item.name}"
        )
        if (file.exists()) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HiddenFileRow(item: HideItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProLockerPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.type == HideItem.TYPE_AUDIO) Icons.Outlined.MusicNote
                    else Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = ProLockerPrimary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${item.size} · ${item.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HiddenSheetPreview(item: HideItem) {
    val file = File(
        Environment.getExternalStorageDirectory(),
        "${com.carbon.prolocker.feature.hidefile.data.HideFileStorage.HIDE_FILE_DIR}/.${item.name}"
    )
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val art = if (item.type == HideItem.TYPE_AUDIO && item.image != null) {
            item.image!!.takeIf { it.isNotEmpty() }?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    null
                }
            }
        } else null
        if (art != null) {
            Image(
                painter = rememberAsyncImagePainter(art),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (file.exists() && item.type != HideItem.TYPE_AUDIO) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (item.type == HideItem.TYPE_AUDIO) Icons.Outlined.MusicNote
                else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

private fun categoryTitleId(type: String): Int = when (type) {
    HideItem.TYPE_IMAGE -> R.string.hide_files_images
    HideItem.TYPE_VIDEO -> R.string.hide_files_videos
    HideItem.TYPE_AUDIO -> R.string.hide_files_music
    else -> R.string.hide_files_files
}

@Preview(showBackground = true)
@Composable
fun HiddenItemsScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HiddenItemsScreen(type = HideItem.TYPE_IMAGE)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HiddenItemsScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HiddenItemsScreen(type = HideItem.TYPE_IMAGE)
    }
}
