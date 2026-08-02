package com.carbon.prolocker.feature.hidefile.ui

import android.content.res.Configuration
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.ui.components.EmptyState
import com.carbon.prolocker.core.ui.components.LoadingView
import com.carbon.prolocker.feature.hidefile.HideFileViewModel
import com.carbon.prolocker.feature.hidefile.data.HideItem
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    onBack: () -> Unit = {}
) {
    val viewModel: HideFileViewModel = koinViewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hiddenItems by viewModel.items.collectAsState()
    val hiddenPaths = remember(hiddenItems) {
        buildSet {
            val storage = Environment.getExternalStorageDirectory()
            for (item in hiddenItems) {
                add(File(storage, item.path.trimStart('/') + "/" + item.name).absolutePath)
            }
        }
    }

    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    val entries = remember(currentDir) {
        currentDir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.filterNot { it.absolutePath in hiddenPaths }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
            ?: emptyList()
    }

    androidx.compose.runtime.LaunchedEffect(entries) {
        loading = false
    }

    val hideLabel = stringResource(R.string.hide_files_hide_count, selectedPaths.size)

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
                            stringResource(R.string.hide_files_files),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currentDir.path.removePrefix(Environment.getExternalStorageDirectory().path)
                                .ifEmpty { "/" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (selectedPaths.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.hidePaths(selectedPaths.toList(), HideItem.TYPE_FILE)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary)
                    ) {
                        Text(hideLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> LoadingView()
                entries.isEmpty() -> EmptyState(
                    title = stringResource(R.string.hide_files_picker_empty_title),
                    description = stringResource(R.string.hide_files_picker_empty_description),
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    if (currentDir.parentFile != null && currentDir.absolutePath !=
                        Environment.getExternalStorageDirectory().absolutePath
                    ) {
                        item(key = "up") {
                            FilePickerRow(
                                label = stringResource(R.string.hide_files_go_up),
                                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                isDirectory = true,
                                selected = false,
                                onClick = {
                                    selectedPaths = emptySet()
                                    currentDir = currentDir.parentFile!!
                                }
                            )
                        }
                    }
                    items(entries, key = { it.absolutePath }) { file ->
                        val isSelected = file.absolutePath in selectedPaths
                        FilePickerRow(
                            label = file.name,
                            icon = if (file.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                            isDirectory = file.isDirectory,
                            selected = isSelected,
                            onClick = {
                                if (file.isDirectory) {
                                    selectedPaths = emptySet()
                                    currentDir = file
                                } else {
                                    selectedPaths = if (isSelected) selectedPaths - file.absolutePath
                                    else selectedPaths + file.absolutePath
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickerRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDirectory: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, ProLockerPrimary, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDirectory) ProLockerPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isDirectory) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ProLockerPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FilePickerScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        FilePickerScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun FilePickerScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        FilePickerScreen()
    }
}
