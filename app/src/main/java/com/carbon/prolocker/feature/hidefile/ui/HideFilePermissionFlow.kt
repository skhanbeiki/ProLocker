package com.carbon.prolocker.feature.hidefile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.hidefile.data.HideFileStorage

@Composable
fun StorageAccessDialog(
    category: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(R.string.hide_files_permission_dialog_media_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                stringResource(R.string.hide_files_permission_dialog_media_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.hide_files_allow_access))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

class StoragePermissionRequester(
    private val storage: HideFileStorage,
    private val pendingCategory: MutableState<String?>,
    private val runtimeLauncher: ActivityResultLauncher<Array<String>>
) {
    fun needsPermission(category: String? = null): Boolean {
        return !storage.hasStorageAccess()
    }

    fun request(category: String?, onImmediateGrant: () -> Unit) {
        pendingCategory.value = category
        if (!needsPermission(category)) {
            onImmediateGrant()
        } else {
            launchRuntimePermissions()
        }
    }

    private fun launchRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runtimeLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}

@Composable
fun rememberStoragePermissionRequester(
    onGranted: (String?) -> Unit,
    onDenied: () -> Unit
): StoragePermissionRequester {
    val context = LocalContext.current
    val storage = remember { HideFileStorage(context.applicationContext) }
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)
    val pendingCategory = remember { mutableStateOf<String?>(null) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val category = pendingCategory.value
        if (granted.values.any { it } || storage.hasStorageAccess()) {
            currentOnGranted(category)
        } else {
            currentOnDenied()
        }
    }

    return remember(storage) {
        StoragePermissionRequester(
            storage = storage,
            pendingCategory = pendingCategory,
            runtimeLauncher = runtimeLauncher
        )
    }
}
