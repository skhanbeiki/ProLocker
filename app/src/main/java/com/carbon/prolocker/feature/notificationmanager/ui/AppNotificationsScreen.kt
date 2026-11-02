package com.carbon.prolocker.feature.notificationmanager.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.notificationmanager.NotificationManagerGradient
import com.carbon.prolocker.feature.notificationmanager.data.HiddenNotification

/** The kept notifications of one app, newest first. */
@Composable
fun AppNotificationsScreen(
    packageName: String,
    notifications: List<HiddenNotification>,
    onBack: () -> Unit,
    onOpen: (HiddenNotification) -> Unit,
    onDelete: (HiddenNotification) -> Unit,
    onClearApp: () -> Unit
) {
    val label = rememberAppLabel(packageName)
    val formatTime = rememberTimeFormatter()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NmTopBar(
                title = label,
                onBack = onBack,
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.nm_clear_all))
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notifications, key = { it.id }) { item ->
                NotificationCard(
                    item = item,
                    appLabel = label,
                    time = formatTime(item.postedAt),
                    onOpen = { onOpen(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.nm_clear_app_title, label)) },
            text = { Text(stringResource(R.string.nm_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearApp()
                }) { Text(stringResource(R.string.pb_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.pb_cancel)) }
            }
        )
    }
}

@Composable
private fun NotificationCard(
    item: HiddenNotification,
    appLabel: String,
    time: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    NmCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item.packageName, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$appLabel · $time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.nm_open),
                        tint = NotificationManagerGradient.first(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.pb_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Spacer(Modifier.height(2.dp))
            }
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
        }
    }
}
