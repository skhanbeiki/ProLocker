package com.carbon.prolocker.feature.notificationmanager.ui

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.carbon.prolocker.core.ui.adaptiveGridHorizontalArrangement
import com.carbon.prolocker.core.ui.adaptiveStaggeredCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import com.carbon.prolocker.core.ui.Adaptive
import com.carbon.prolocker.core.ui.centeredMaxWidth
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.notificationmanager.NotificationManagerGradient

@Composable
private fun rememberLaunchableApps(): List<PickableApp>? {
    val context = LocalContext.current
    val apps by produceState<List<PickableApp>?>(null) {
        value = NmAppInfoCache.launchableApps(context)
    }
    return apps
}

private fun List<PickableApp>.search(query: String): List<PickableApp> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
}

/** First step after access is granted: pick the apps whose notifications are hidden. */
@Composable
fun SelectAppsScreen(
    initialSelection: Set<String>,
    onBack: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val apps = rememberLaunchableApps()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(initialSelection.toList()) }
    val selectedSet = remember(selected) { selected.toSet() }
    val visible = remember(apps, query) { apps?.search(query).orEmpty() }
    val allSelected = visible.isNotEmpty() && visible.all { it.packageName in selectedSet }

    Scaffold(
        topBar = { NmTopBar(title = stringResource(R.string.nm_select_apps), onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onConfirm(selectedSet) },
                    enabled = selectedSet.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth().centeredMaxWidth(Adaptive.ButtonMaxWidth)
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NotificationManagerGradient.first())
                ) {
                    Text(
                        text = stringResource(R.string.nm_hide_count, selectedSet.size),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NmSearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (apps == null) {
                LoadingBox()
                return@Column
            }
            if (visible.isEmpty()) {
                NmEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.nm_no_results),
                    description = stringResource(R.string.nm_no_results_desc)
                )
                return@Column
            }
            LazyVerticalStaggeredGrid(
                columns = adaptiveStaggeredCells(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = adaptiveGridHorizontalArrangement()
            ) {
                item(key = "__all__", span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) {
                    PickRow(
                        leading = { GradientIconBox(Icons.Outlined.Apps, size = 42.dp) },
                        title = stringResource(R.string.nm_all_apps),
                        subtitle = stringResource(R.string.nm_selected_count, selectedSet.size),
                        trailing = { CircleCheck(checked = allSelected) },
                        onClick = {
                            val names = visible.map { it.packageName }
                            selected = if (allSelected) selected - names.toSet() else (selected + names).distinct()
                        }
                    )
                }
}
                items(visible, key = { it.packageName }) { app ->
Column(modifier = Modifier.fillMaxWidth()) {
                    val checked = app.packageName in selectedSet
                    PickRow(
                        leading = { AppIcon(app.packageName, size = 42.dp) },
                        title = app.label,
                        subtitle = null,
                        trailing = { CircleCheck(checked = checked) },
                        onClick = {
                            selected = if (checked) selected - app.packageName else selected + app.packageName
                        }
                    )
                }
}
            }
        }
    }
}

/** Two tabs (not hidden / hidden) to move apps in and out of the hidden list. */
@Composable
fun ManageAppsScreen(
    hiddenApps: Set<String>,
    onBack: () -> Unit,
    onHide: (String) -> Unit,
    onShow: (String) -> Unit
) {
    val apps = rememberLaunchableApps()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(1) }
    val visibleApps = remember(apps, hiddenApps) { apps?.filter { it.packageName !in hiddenApps }.orEmpty() }
    val hiddenList = remember(apps, hiddenApps) {
        val known = apps?.filter { it.packageName in hiddenApps }.orEmpty()
        // Hidden apps without a launcher icon still need to be removable.
        val knownNames = known.map { it.packageName }.toSet()
        known + hiddenApps.filter { it !in knownNames }.map { PickableApp(it, it) }
    }
    val list = remember(tab, visibleApps, hiddenList, query) {
        (if (tab == 0) visibleApps else hiddenList).search(query)
    }

    Scaffold(
        topBar = { NmTopBar(title = stringResource(R.string.nm_manage_apps), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SegmentedTabs(
                labels = listOf(
                    stringResource(R.string.nm_tab_not_hidden, visibleApps.size),
                    stringResource(R.string.nm_tab_hidden, hiddenList.size)
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            NmSearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (apps == null) {
                LoadingBox()
                return@Column
            }
            if (list.isEmpty()) {
                NmEmptyState(
                    icon = if (query.isBlank()) Icons.Outlined.Apps else Icons.Outlined.SearchOff,
                    title = stringResource(if (query.isBlank()) R.string.nm_no_apps_here else R.string.nm_no_results),
                    description = stringResource(
                        if (query.isBlank()) (if (tab == 1) R.string.nm_no_hidden_desc else R.string.nm_all_hidden_desc)
                        else R.string.nm_no_results_desc
                    )
                )
                return@Column
            }
            LazyVerticalStaggeredGrid(
                columns = adaptiveStaggeredCells(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = adaptiveGridHorizontalArrangement()
            ) {
                items(list, key = { it.packageName }) { app ->
Column(modifier = Modifier.fillMaxWidth()) {
                    val hidden = tab == 1
                    PickRow(
                        leading = { AppIcon(app.packageName, size = 42.dp) },
                        title = if (app.label == app.packageName) rememberAppLabel(app.packageName) else app.label,
                        subtitle = null,
                        trailing = {
                            IconButton(onClick = { if (hidden) onShow(app.packageName) else onHide(app.packageName) }) {
                                Icon(
                                    imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(if (hidden) R.string.nm_unhide else R.string.nm_hide),
                                    tint = if (hidden) NotificationManagerGradient.first() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { if (hidden) onShow(app.packageName) else onHide(app.packageName) }
                    )
                }
}
            }
        }
    }
}

@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (active) Modifier.background(Brush.linearGradient(NotificationManagerGradient))
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (index == 1) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    NmCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NotificationManagerGradient.first())
    }
}
