package com.carbon.prolocker.feature.privatebrowser.ui

import com.carbon.prolocker.core.ui.centeredMaxWidth
import com.carbon.prolocker.core.ui.Adaptive
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.carbon.prolocker.core.ui.adaptiveGridHorizontalArrangement
import com.carbon.prolocker.core.ui.adaptiveStaggeredCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient
import com.carbon.prolocker.feature.privatebrowser.data.BrowserSettings
import com.carbon.prolocker.feature.privatebrowser.data.SearchEngine

@Composable
fun BrowserSettingsScreen(
    settings: BrowserSettings,
    onBack: () -> Unit,
    onUpdate: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onAddShortcut: () -> Unit,
    onClearNow: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrowserPageTopBar(title = stringResource(R.string.pb_settings_title), onBack = onBack) }
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = adaptiveStaggeredCells(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            horizontalArrangement = adaptiveGridHorizontalArrangement()
) {
            item {
Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCard {
                    SearchEngineRow(settings.searchEngine) { engine -> onUpdate { it.copy(searchEngine = engine) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Key, stringResource(R.string.pb_setting_save_passwords),
                        stringResource(R.string.pb_setting_save_passwords_desc), settings.savePasswords
                    ) { v -> onUpdate { it.copy(savePasswords = v) } }
                }
            }
}

            item {
Column(modifier = Modifier.fillMaxWidth()) { SectionHeader(stringResource(R.string.pb_settings_clear_on_exit), stringResource(R.string.pb_settings_clear_on_exit_desc)) 
                SettingsCard {
                    SwitchRow(Icons.Outlined.DeleteSweep, stringResource(R.string.pb_setting_clear_cache), null,
                        settings.clearCacheOnExit) { v -> onUpdate { it.copy(clearCacheOnExit = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Cookie, stringResource(R.string.pb_setting_clear_cookies), null,
                        settings.clearCookiesOnExit) { v -> onUpdate { it.copy(clearCookiesOnExit = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.History, stringResource(R.string.pb_setting_clear_history), null,
                        settings.clearHistoryOnExit) { v -> onUpdate { it.copy(clearHistoryOnExit = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Storage, stringResource(R.string.pb_setting_clear_site_data), null,
                        settings.clearSiteDataOnExit) { v -> onUpdate { it.copy(clearSiteDataOnExit = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Tab, stringResource(R.string.pb_setting_close_tabs),
                        stringResource(R.string.pb_setting_close_tabs_desc),
                        settings.closeTabsOnExit) { v -> onUpdate { it.copy(closeTabsOnExit = v) } }
                }
            }
}

            item {
Column(modifier = Modifier.fillMaxWidth()) { SectionHeader(stringResource(R.string.pb_settings_privacy), null) 
                SettingsCard {
                    SwitchRow(Icons.Outlined.Layers, stringResource(R.string.pb_setting_third_party), null,
                        settings.blockThirdPartyCookies) { v -> onUpdate { it.copy(blockThirdPartyCookies = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Block, stringResource(R.string.pb_setting_popups), null,
                        settings.blockPopups) { v -> onUpdate { it.copy(blockPopups = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.VisibilityOff, stringResource(R.string.pb_setting_dnt), null,
                        settings.doNotTrack) { v -> onUpdate { it.copy(doNotTrack = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Code, stringResource(R.string.pb_setting_js),
                        stringResource(R.string.pb_setting_js_desc),
                        settings.javaScriptEnabled) { v -> onUpdate { it.copy(javaScriptEnabled = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.ScreenLockPortrait, stringResource(R.string.pb_setting_secure),
                        stringResource(R.string.pb_setting_secure_desc),
                        settings.secureScreen) { v -> onUpdate { it.copy(secureScreen = v) } }
                }
            }
}

            item {
Column(modifier = Modifier.fillMaxWidth()) { SectionHeader(stringResource(R.string.pb_settings_shortcut), null) 
                SettingsCard {
                    ActionRow(
                        icon = Icons.Outlined.AddToHomeScreen,
                        title = stringResource(R.string.pb_add_browser_shortcut),
                        subtitle = stringResource(R.string.pb_settings_shortcut_desc),
                        onClick = onAddShortcut
                    )
                }
            }
}

            item(span = StaggeredGridItemSpan.FullLine) {
Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onClearNow,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth().centeredMaxWidth(Adaptive.ButtonMaxWidth)
                        .height(52.dp)
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pb_clear_now), fontWeight = FontWeight.SemiBold)
                }
            }
}
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PrivateBrowserGradient.first()
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(PrivateBrowserGradient.first().copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = PrivateBrowserGradient.first(), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrivateBrowserGradient.first()
            )
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchEngineRow(selected: SearchEngine, onSelect: (SearchEngine) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowIcon(Icons.Outlined.Search)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pb_setting_search_engine),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    selected.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = PrivateBrowserGradient.first()
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = PrivateBrowserGradient.first())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchEngine.entries.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine.displayName) },
                    trailingIcon = {
                        if (engine == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = PrivateBrowserGradient.first())
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(engine)
                    }
                )
            }
        }
    }
}
