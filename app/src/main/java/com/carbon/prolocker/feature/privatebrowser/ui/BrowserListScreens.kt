package com.carbon.prolocker.feature.privatebrowser.ui

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.carbon.prolocker.core.ui.adaptiveGridHorizontalArrangement
import com.carbon.prolocker.core.ui.adaptiveStaggeredCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.BrowserController
import com.carbon.prolocker.feature.privatebrowser.BrowserTab
import com.carbon.prolocker.feature.privatebrowser.BrowserUrls
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient
import com.carbon.prolocker.feature.privatebrowser.data.BrowserBookmark
import com.carbon.prolocker.feature.privatebrowser.data.BrowserHistoryEntry
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// region Tabs

@Composable
fun BrowserTabsScreen(controller: BrowserController, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrowserPageTopBar(
                title = stringResource(R.string.pb_tabs_count, controller.tabs.size),
                onBack = onBack,
                actions = {
                    TextButton(onClick = { controller.closeAllTabs(); onBack() }) {
                        Text(stringResource(R.string.pb_close_all_tabs), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { controller.newTab(); onBack() },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.pb_new_tab)) },
                containerColor = PrivateBrowserGradient.first(),
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = adaptiveStaggeredCells(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = adaptiveGridHorizontalArrangement()
        ) {
            items(controller.tabs, key = { it.id }) { tab ->
Column(modifier = Modifier.fillMaxWidth()) {
                TabCard(
                    tab = tab,
                    selected = tab.id == controller.currentTab?.id,
                    onClick = { controller.selectTab(tab); onBack() },
                    onClose = { controller.closeTab(tab) }
                )
            }
}
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val isHome = tab.showHome || tab.url.isBlank()
    val title = if (isHome) stringResource(R.string.pb_new_tab) else tab.title.ifBlank { BrowserUrls.host(tab.url) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.75f else 0.4f),
        border = if (selected) BorderStroke(1.5.dp, PrivateBrowserGradient.first()) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isHome) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(PrivateBrowserGradient.first().copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Tab, contentDescription = null, tint = PrivateBrowserGradient.first())
                }
            } else {
                SiteAvatar(title = title, url = tab.url, size = 42.dp, favicon = tab.favicon)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isHome) {
                    Text(
                        text = BrowserUrls.display(tab.url),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pb_close_tab))
            }
        }
    }
}

// endregion

// region Bookmarks

@Composable
fun BrowserBookmarksScreen(
    bookmarks: List<BrowserBookmark>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onAddShortcut: (BrowserBookmark) -> Unit,
    onDelete: (BrowserBookmark) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrowserPageTopBar(title = stringResource(R.string.pb_bookmarks), onBack = onBack) }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            BrowserEmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = stringResource(R.string.pb_empty_bookmarks),
                description = stringResource(R.string.pb_empty_bookmarks_desc),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = adaptiveStaggeredCells(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = adaptiveGridHorizontalArrangement()
) {
                items(bookmarks, key = { it.url }) { bookmark ->
Column(modifier = Modifier.fillMaxWidth()) {
                    var menu by remember { mutableStateOf(false) }
                    SiteRow(
                        title = bookmark.title,
                        url = bookmark.url,
                        onClick = { onOpen(bookmark.url) },
                        trailing = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.pb_menu))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.pb_open_in_new_tab)) },
                                        leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                                        onClick = { menu = false; onOpenInNewTab(bookmark.url) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.pb_add_page_shortcut)) },
                                        leadingIcon = { Icon(Icons.Outlined.AddToHomeScreen, contentDescription = null) },
                                        onClick = { menu = false; onAddShortcut(bookmark) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.pb_delete), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menu = false; onDelete(bookmark) }
                                    )
                                }
                            }
                        }
                    )
                }
}
            }
        }
    }
}

// endregion

// region History

@Composable
fun BrowserHistoryScreen(
    history: List<BrowserHistoryEntry>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (BrowserHistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(history, query) {
        val q = query.trim()
        if (q.isEmpty()) history else history.filter {
            it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true)
        }
    }
    val todayLabel = stringResource(R.string.pb_today)
    val yesterdayLabel = stringResource(R.string.pb_yesterday)
    val grouped = remember(filtered, todayLabel, yesterdayLabel) {
        filtered.groupBy { dayLabel(it.visitedAt, todayLabel, yesterdayLabel) }
    }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrowserPageTopBar(
                title = stringResource(R.string.pb_history),
                onBack = onBack,
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.pb_clear_history))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (history.isEmpty()) {
                BrowserEmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.pb_empty_history),
                    description = stringResource(R.string.pb_empty_history_desc)
                )
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pb_search_history)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = null) }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrivateBrowserGradient.first(),
                    cursorColor = PrivateBrowserGradient.first()
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyVerticalStaggeredGrid(
                columns = adaptiveStaggeredCells(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = adaptiveGridHorizontalArrangement()
) {
                grouped.forEach { (day, entries) ->
                    item(key = "header_$day", span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrivateBrowserGradient.first(),
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
}
                    items(entries, key = { "h_${it.visitedAt}_${it.url}" }) { entry ->
Column(modifier = Modifier.fillMaxWidth()) {
                        SiteRow(
                            title = entry.title,
                            url = entry.url,
                            subtitlePrefix = timeFormat.format(Date(entry.visitedAt)),
                            onClick = { onOpen(entry.url) },
                            trailing = {
                                IconButton(onClick = { onDelete(entry) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.pb_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
}
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.pb_clear_history)) },
            text = { Text(stringResource(R.string.pb_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text(stringResource(R.string.pb_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.pb_cancel)) }
            }
        )
    }
}

private fun dayLabel(timestamp: Long, today: String, yesterday: String): String {
    val day = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(day, now)) return today
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(day, now)) return yesterday
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

// endregion

@Composable
private fun SiteRow(
    title: String,
    url: String,
    onClick: () -> Unit,
    subtitlePrefix: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SiteAvatar(title = title, url = url, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val host = BrowserUrls.display(url)
            Text(
                text = if (subtitlePrefix != null) "$subtitlePrefix · $host" else host,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}
