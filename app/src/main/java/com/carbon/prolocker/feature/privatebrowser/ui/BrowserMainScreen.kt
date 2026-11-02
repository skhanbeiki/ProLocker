package com.carbon.prolocker.feature.privatebrowser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.BrowserController
import com.carbon.prolocker.feature.privatebrowser.BrowserTab
import com.carbon.prolocker.feature.privatebrowser.BrowserUrls
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient
import com.carbon.prolocker.feature.privatebrowser.data.BrowserBookmark
import com.carbon.prolocker.feature.privatebrowser.data.BrowserSettings

private data class QuickLink(val name: String, val url: String, val color: Color)

private val quickLinks = listOf(
    QuickLink("Google", "https://www.google.com", Color(0xFF4285F4)),
    QuickLink("YouTube", "https://m.youtube.com", Color(0xFFE62117)),
    QuickLink("Instagram", "https://www.instagram.com", Color(0xFFD62976)),
    QuickLink("Wikipedia", "https://www.wikipedia.org", Color(0xFF5F6368)),
    QuickLink("Facebook", "https://m.facebook.com", Color(0xFF1877F2)),
    QuickLink("X", "https://x.com", Color(0xFF1F2937)),
    QuickLink("Reddit", "https://www.reddit.com", Color(0xFFFF4500)),
    QuickLink("DuckDuckGo", "https://duckduckgo.com", Color(0xFFDE5833))
)

/** Actions of the main browser screen, wired up by the activity. */
class BrowserActions(
    val onExit: () -> Unit,
    val onOpenTabs: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onToggleBookmark: (BrowserTab) -> Unit,
    val onShare: (String) -> Unit,
    val onCopyLink: (String) -> Unit,
    val onAddPageShortcut: (BrowserTab) -> Unit,
    val onAddBrowserShortcut: () -> Unit,
    /** Native ad slot on the new-tab page (never over web content). */
    val homeAd: (@Composable () -> Unit)? = null
)

@Composable
fun BrowserMainScreen(
    controller: BrowserController,
    settings: BrowserSettings,
    bookmarks: List<BrowserBookmark>,
    actions: BrowserActions
) {
    val tab = controller.currentTab ?: return
    val isBookmarked = !tab.showHome && bookmarks.any { it.url == tab.url }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BrowserTopBar(
                tab = tab,
                controller = controller,
                isBookmarked = isBookmarked,
                actions = actions
            )
        },
        bottomBar = {
            BrowserBottomBar(tab = tab, controller = controller, actions = actions)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tab.showHome) {
                BrowserHomeContent(
                    settings = settings,
                    bookmarks = bookmarks,
                    onOpen = { controller.open(it) },
                    onOpenBookmarks = actions.onOpenBookmarks,
                    onOpenSettings = actions.onOpenSettings,
                    homeAd = actions.homeAd
                )
            } else {
                key(tab.id) { WebViewHost(tab) }
            }
        }
    }
}

@Composable
private fun WebViewHost(tab: BrowserTab) {
    val webView = tab.webView ?: return
    AndroidView(
        factory = { context -> FrameLayout(context) },
        update = { container ->
            if (container.getChildAt(0) !== webView) {
                container.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BrowserTopBar(
    tab: BrowserTab,
    controller: BrowserController,
    isBookmarked: Boolean,
    actions: BrowserActions
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = actions.onExit) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pb_exit))
                }
                AddressField(
                    tab = tab,
                    onSubmit = { controller.submit(it) },
                    onReload = { controller.reload() },
                    onStop = { controller.stopLoading() },
                    modifier = Modifier.weight(1f)
                )
                if (!tab.showHome) {
                    IconButton(onClick = { actions.onToggleBookmark(tab) }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (isBookmarked) R.string.pb_remove_bookmark else R.string.pb_add_bookmark
                            ),
                            tint = if (isBookmarked) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.pb_menu))
                    }
                    BrowserMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        tab = tab,
                        controller = controller,
                        isBookmarked = isBookmarked,
                        actions = actions
                    )
                }
            }
            if (!tab.showHome && tab.isLoading) {
                LinearProgressIndicator(
                    progress = { (tab.progress.coerceIn(5, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = PrivateBrowserGradient.first(),
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun AddressField(
    tab: BrowserTab,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val pageUrl = if (tab.showHome) "" else tab.url
    var focused by remember { mutableStateOf(false) }
    var value by remember(tab.id) { mutableStateOf(TextFieldValue(BrowserUrls.display(pageUrl))) }

    LaunchedEffect(pageUrl) {
        if (!focused) value = TextFieldValue(BrowserUrls.display(pageUrl))
    }

    val borderColor = if (focused) PrivateBrowserGradient.first() else Color.Transparent
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(23.dp))
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val secure = !tab.showHome && pageUrl.startsWith("https://")
        Icon(
            imageVector = if (secure && !focused) Icons.Filled.Lock else Icons.Filled.Search,
            contentDescription = null,
            tint = if (secure && !focused) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.pb_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content
                ),
                cursorBrush = SolidColor(PrivateBrowserGradient.first()),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val text = value.text
                        focusManager.clearFocus()
                        if (text.isNotBlank()) onSubmit(text)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused && !focused) {
                            value = TextFieldValue(pageUrl, TextRange(0, pageUrl.length))
                        } else if (!state.isFocused && focused) {
                            value = TextFieldValue(BrowserUrls.display(pageUrl))
                        }
                        focused = state.isFocused
                    }
            )
        }
        when {
            focused && value.text.isNotEmpty() -> IconButton(onClick = { value = TextFieldValue("") }) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            !tab.showHome && !focused -> IconButton(onClick = { if (tab.isLoading) onStop() else onReload() }) {
                Icon(
                    imageVector = if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = stringResource(if (tab.isLoading) R.string.pb_stop else R.string.pb_reload),
                    modifier = Modifier.size(20.dp)
                )
            }

            else -> Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
private fun BrowserMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tab: BrowserTab,
    controller: BrowserController,
    isBookmarked: Boolean,
    actions: BrowserActions
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(Icons.Outlined.Add, stringResource(R.string.pb_new_tab)) {
            onDismiss(); controller.newTab()
        }
        MenuItem(Icons.Outlined.BookmarkBorder, stringResource(R.string.pb_bookmarks)) {
            onDismiss(); actions.onOpenBookmarks()
        }
        MenuItem(Icons.Outlined.History, stringResource(R.string.pb_history)) {
            onDismiss(); actions.onOpenHistory()
        }
        if (!tab.showHome) {
            HorizontalDivider()
            MenuItem(
                if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                stringResource(if (isBookmarked) R.string.pb_remove_bookmark else R.string.pb_add_bookmark)
            ) { onDismiss(); actions.onToggleBookmark(tab) }
            MenuItem(Icons.Outlined.Share, stringResource(R.string.pb_share)) {
                onDismiss(); actions.onShare(tab.url)
            }
            MenuItem(Icons.Outlined.ContentCopy, stringResource(R.string.pb_copy_link)) {
                onDismiss(); actions.onCopyLink(tab.url)
            }
            MenuItem(Icons.Outlined.AddToHomeScreen, stringResource(R.string.pb_add_page_shortcut)) {
                onDismiss(); actions.onAddPageShortcut(tab)
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pb_desktop_site)) },
                leadingIcon = { Icon(Icons.Outlined.DesktopWindows, contentDescription = null) },
                trailingIcon = {
                    if (controller.desktopMode) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = PrivateBrowserGradient.first())
                    }
                },
                onClick = { onDismiss(); controller.toggleDesktopMode() }
            )
        }
        HorizontalDivider()
        MenuItem(Icons.Outlined.AddToHomeScreen, stringResource(R.string.pb_add_browser_shortcut)) {
            onDismiss(); actions.onAddBrowserShortcut()
        }
        MenuItem(Icons.Outlined.Settings, stringResource(R.string.pb_settings)) {
            onDismiss(); actions.onOpenSettings()
        }
        MenuItem(
            Icons.Outlined.DeleteSweep,
            stringResource(R.string.pb_clear_and_exit),
            tint = MaterialTheme.colorScheme.error
        ) { onDismiss(); actions.onExit() }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}

@Composable
private fun BrowserBottomBar(
    tab: BrowserTab,
    controller: BrowserController,
    actions: BrowserActions
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.goBack() }, enabled = controller.canGoBack(tab)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.pb_back),
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = { controller.goForward() }, enabled = controller.canGoForward(tab)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.pb_forward),
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = { controller.goHome() }) {
                Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.pb_home))
            }
            IconButton(onClick = actions.onOpenTabs) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val count = controller.tabs.size
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(onClick = actions.onExit) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(R.string.pb_clear_and_exit),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BrowserHomeContent(
    settings: BrowserSettings,
    bookmarks: List<BrowserBookmark>,
    onOpen: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    homeAd: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(Brush.linearGradient(PrivateBrowserGradient), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.private_browser),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pb_search_with, settings.searchEngine.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PrivacyNoteCard(settings = settings, onClick = onOpenSettings)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.pb_quick_access))
        }
        items(quickLinks, key = { "quick_" + it.url }) { link ->
            SiteTile(title = link.name, url = link.url, color = link.color, onClick = { onOpen(link.url) })
        }

        if (homeAd != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "home_ad") {
                homeAd()
            }
        }

        if (bookmarks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(
                    title = stringResource(R.string.pb_bookmarks),
                    action = stringResource(R.string.pb_see_all),
                    onAction = onOpenBookmarks
                )
            }
            items(bookmarks.take(8), key = { "bm_" + it.url }) { bookmark ->
                SiteTile(title = bookmark.title, url = bookmark.url, color = null, onClick = { onOpen(bookmark.url) })
            }
        }
    }
}

@Composable
private fun PrivacyNoteCard(settings: BrowserSettings, onClick: () -> Unit) {
    val active = settings.clearHistoryOnExit || settings.clearCookiesOnExit || settings.clearCacheOnExit
    val accent = if (active) Color(0xFF10B981) else Color(0xFFF59E0B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(if (active) R.string.pb_private_note else R.string.pb_private_note_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = PrivateBrowserGradient.first(),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SiteTile(title: String, url: String, color: Color?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SiteAvatar(title = title, url = url, size = 52.dp, color = color)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
