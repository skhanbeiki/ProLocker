package com.carbon.prolocker.feature.notificationmanager.ui

import com.carbon.prolocker.core.ui.AdaptiveCardGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.carbon.prolocker.core.ui.adaptiveGridHorizontalArrangement
import com.carbon.prolocker.core.ui.adaptiveStaggeredCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import com.carbon.prolocker.core.ui.Adaptive
import com.carbon.prolocker.core.ui.centeredMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.notificationmanager.NotificationManagerGradient
import com.carbon.prolocker.feature.notificationmanager.data.HiddenAppSummary

@Composable
fun NotificationHomeScreen(
    enabled: Boolean,
    hasAccess: Boolean,
    hiddenApps: Set<String>,
    summaries: List<HiddenAppSummary>,
    /** True once apps were ever chosen (see NotificationHiderRepository.wasSetUp). */
    wasSetUp: Boolean = false,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onGrantAccess: () -> Unit,
    onManageApps: () -> Unit,
    onSelectApps: () -> Unit = {},
    onOpenApp: (String) -> Unit,
    onClearAll: () -> Unit,
    /** Native ad slot between the hidden-apps row and the notification list. */
    ad: (@Composable () -> Unit)? = null
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val formatTime = rememberTimeFormatter()
    // Before any app was ever picked there is only the intro and one "turn on" button.
    // Once apps were chosen (or notifications are kept) the intro never comes back, even when
    // every app has been un-hidden: the saved notifications must stay reachable.
    val setUp = hiddenApps.isNotEmpty() || wasSetUp || summaries.isNotEmpty()

    Scaffold(
        topBar = {
            NmTopBar(
                title = stringResource(R.string.nm_title),
                onBack = onBack,
                actions = {
                    if (summaries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.nm_clear_all))
                        }
                    }
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.nm_help))
                    }
                }
            )
        },
        bottomBar = {
            // While the feature is off, the call to action stays pinned to the bottom of the screen.
            if (!setUp) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { onToggle(true) },
                        modifier = Modifier
                            .fillMaxWidth().centeredMaxWidth(Adaptive.ButtonMaxWidth)
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NotificationManagerGradient.first())
                    ) {
                        Text(stringResource(R.string.nm_turn_on), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = adaptiveStaggeredCells(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = adaptiveGridHorizontalArrangement()
        ) {
            if (!setUp) {
                item(span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) { IntroSection() }
}
            } else {
                item {
Column(modifier = Modifier.fillMaxWidth()) { ToggleCard(enabled = enabled, onToggle = onToggle) }
}
                if (enabled && !hasAccess) {
                    item {
Column(modifier = Modifier.fillMaxWidth()) { AccessWarningCard(onGrantAccess) }
}
                }
                item {
Column(modifier = Modifier.fillMaxWidth()) {
                    if (hiddenApps.isEmpty()) {
                        SelectAppsCard(onClick = onSelectApps)
                    } else {
                        HiddenAppsRow(hiddenApps = hiddenApps, onClick = onManageApps)
                    }
                }
}
                if (ad != null) {
                    item(key = "native_ad") {
Column(modifier = Modifier.fillMaxWidth()) { ad() }
}
                }
                item(span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.nm_notifications),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
}
                if (summaries.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) {
                        NmCard {
                            NmEmptyState(
                                icon = Icons.Outlined.MarkChatUnread,
                                title = stringResource(R.string.nm_empty_title),
                                description = stringResource(R.string.nm_empty_desc)
                            )
                        }
                    }
}
                } else {
                    items(summaries, key = { it.packageName }) { summary ->
Column(modifier = Modifier.fillMaxWidth()) {
                        AppSummaryRow(summary = summary, time = formatTime(summary.lastTime), onClick = { onOpenApp(summary.packageName) })
                    }
}
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            icon = { GradientIconBox(Icons.Outlined.NotificationsOff) },
            title = { Text(stringResource(R.string.nm_help_title)) },
            text = { Text(stringResource(R.string.nm_help_text)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.nm_got_it)) }
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.nm_clear_all)) },
            text = { Text(stringResource(R.string.nm_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text(stringResource(R.string.pb_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.pb_cancel)) }
            }
        )
    }
}

@Composable
private fun ToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientIconBox(Icons.Outlined.VisibilityOff)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nm_hide_previews),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.nm_hide_previews_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NotificationManagerGradient.first()
                )
            )
        }
    }
}

@Composable
private fun AccessWarningCard(onGrantAccess: () -> Unit) {
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGrantAccess)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.nm_access_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onGrantAccess) { Text(stringResource(R.string.nm_grant)) }
        }
    }
}

@Composable
private fun HiddenAppsRow(hiddenApps: Set<String>, onClick: () -> Unit) {
    val shown = remember(hiddenApps) { hiddenApps.sorted().take(5) }
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nm_hidden_apps_count, hiddenApps.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nm_no_apps_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box {
                        shown.forEachIndexed { index, pkg ->
                            AppIcon(
                                packageName = pkg,
                                size = 34.dp,
                                modifier = Modifier
                                    .offset(x = (index * 26).dp)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            )
                        }
                        if (hiddenApps.size > shown.size) {
                            Box(
                                modifier = Modifier
                                    .offset(x = (shown.size * 26).dp)
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${hiddenApps.size - shown.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.nm_manage_apps),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown instead of the hidden-apps row when no app is hidden: leads to the app picker. */
@Composable
private fun SelectAppsCard(onClick: () -> Unit) {
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientIconBox(Icons.Outlined.Apps)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nm_select_apps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.nm_select_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppSummaryRow(summary: HiddenAppSummary, time: String, onClick: () -> Unit) {
    val label = rememberAppLabel(summary.packageName)
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(summary.packageName, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = summary.lastTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Unread only: the badge drops (and disappears) once that app's list has been read.
            if (summary.unread > 0) {
                Spacer(Modifier.width(10.dp))
                CountBadge(summary.unread)
            }
        }
    }
}

/** Shown while the feature is off: how it works, in three small illustrations. */
@Composable
private fun IntroSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.nm_how_it_works),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
        // Phones: the cards stack as before. Tablets: they share the width in columns.
        AdaptiveCardGrid(phoneArrangement = Arrangement.spacedBy(12.dp)) {
        IntroCard(
            title = stringResource(R.string.nm_intro_1_title),
            text = stringResource(R.string.nm_intro_1_text)
        ) { LockIllustration() }
        IntroCard(
            title = stringResource(R.string.nm_intro_2_title),
            text = stringResource(R.string.nm_intro_2_text)
        ) { AppListIllustration() }
        IntroCard(
            title = stringResource(R.string.nm_intro_3_title),
            text = stringResource(R.string.nm_intro_3_text)
        ) { StatusBarIllustration() }
        }
    }
}

@Composable
private fun IntroCard(title: String, text: String, illustration: @Composable () -> Unit) {
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(NotificationManagerGradient.map { it.copy(alpha = 0.14f) })
                    ),
                contentAlignment = Alignment.Center
            ) { illustration() }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LockIllustration() {
    val accent = NotificationManagerGradient.first()
    Box(contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    repeat(3) { col ->
                        val on = (row == 0) || (col == 2) || (row == col)
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (on) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset(x = 30.dp, y = 28.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(NotificationManagerGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AppListIllustration() {
    val colors = listOf(Color(0xFF25D366), Color(0xFF29A9EB), Color(0xFFE1306C))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
        colors.forEachIndexed { index, color ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    SkeletonBar(36.dp, 5.dp)
                    SkeletonBar(24.dp, 4.dp, alpha = 0.7f)
                }
                CircleCheck(checked = index != 1, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatusBarIllustration() {
    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(NotificationManagerGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
            }
            Spacer(Modifier.width(5.dp))
            SkeletonBar(34.dp, 5.dp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = NotificationManagerGradient.first(),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SkeletonBar(50.dp, 5.dp)
                SkeletonBar(30.dp, 4.dp, alpha = 0.7f)
            }
        }
    }
}

/** Step-by-step guide shown before sending the user to the notification access settings. */
@Composable
fun AccessGuideDialog(appName: String, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.nm_access_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.nm_access_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                // Mock of the system list with our row highlighted.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(vertical = 6.dp)
                ) {
                    GuideRow(placeholder = true)
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, NotificationManagerGradient.first(), RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GradientIconBox(Icons.Outlined.Lock, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = true,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NotificationManagerGradient.first()
                            )
                        )
                    }
                    GuideRow(placeholder = true)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.nm_access_find, appName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NotificationManagerGradient.first())
            ) {
                Text(stringResource(R.string.nm_go_to_settings), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pb_cancel)) }
        }
    )
}

@Composable
private fun GuideRow(placeholder: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (placeholder) 0.12f else 0.2f))
        )
        Spacer(Modifier.width(10.dp))
        SkeletonBar(90.dp, 8.dp)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier.size(18.dp)
        )
    }
}
