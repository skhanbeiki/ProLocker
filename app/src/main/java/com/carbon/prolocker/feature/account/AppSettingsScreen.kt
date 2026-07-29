package com.carbon.prolocker.feature.account

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carbon.prolocker.R
import com.carbon.prolocker.core.permissions.PermissionManager
import com.carbon.prolocker.core.theme.AppTypography
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onNavigateToAudit: () -> Unit = {}
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: AccountViewModel? = if (isInspection) null else koinViewModel()
    val prefs by viewModel?.userPreferences?.collectAsState() ?: remember { mutableStateOf(com.carbon.prolocker.core.datastore.UserPreferences()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStealthWarningDialog by remember { mutableStateOf(false) }
    var showStealthRequiresLockDialog by remember { mutableStateOf(false) }
    var isAdminActive by remember {
        mutableStateOf(
            if (isInspection) false else com.carbon.prolocker.core.security.DeviceAdminManager(context).isAdminActive()
        )
    }
    var isAccessibilityActive by remember {
        mutableStateOf(
            if (isInspection) false else PermissionManager.isAccessibilityServiceEnabled(
                context,
                com.carbon.prolocker.core.service.AppMonitorAccessibilityService::class.java
            )
        )
    }
    var isStealthActive by remember {
        mutableStateOf(
            if (isInspection) false else PermissionManager.isLauncherComponentDisabled(context)
        )
    }

    val refreshProtectionStates = {
        if (!isInspection) {
            val deviceAdminManager = com.carbon.prolocker.core.security.DeviceAdminManager(context)
            isAdminActive = deviceAdminManager.isAdminActive()
            isAccessibilityActive = PermissionManager.isAccessibilityServiceEnabled(
                context,
                com.carbon.prolocker.core.service.AppMonitorAccessibilityService::class.java
            )
            isStealthActive = PermissionManager.isLauncherComponentDisabled(context)
        }
    }

    if (!isInspection) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshProtectionStates()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            refreshProtectionStates()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
    LaunchedEffect(prefs.isStealthModeEnabled) {
        if (!isInspection) {
            refreshProtectionStates()
        }
    }
    val canEnableStealth = prefs.lockType != "NONE" && prefs.hashedCredential.isNotEmpty()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings), style = AppTypography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.general_settings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AppTypography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ,
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThemeDialog = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.theme), style = AppTypography.bodyLarge)
                                // We map system, light, dark directly in text.
                                Text(if (prefs.isDarkMode) stringResource(R.string.dark) else stringResource(R.string.light), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.language), style = AppTypography.bodyLarge)
                                Text(if (prefs.language == "fa") stringResource(R.string.persian) else stringResource(R.string.english), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            
            item {
                Text(
                    text = stringResource(R.string.app_settings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AppTypography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.vibration_options), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.vibration_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = prefs.vibrationEnabled,
                                onCheckedChange = { viewModel?.toggleVibration(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.hide_pattern_path), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.hide_pattern_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = prefs.hidePatternPath,
                                onCheckedChange = { viewModel?.toggleHidePatternPath(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.failed_attempts_threshold), style = AppTypography.bodyLarge)
                                    val count = prefs.failedAttemptsThreshold
                                    Text(count.toString(), style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = prefs.failedAttemptsThreshold.toFloat(),
                                    onValueChange = { viewModel?.updateFailedAttemptsThreshold(it.toInt()) },
                                    valueRange = 1f..7f,
                                    steps = 5,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(stringResource(R.string.failed_attempts_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.short_exit_grace_period), style = AppTypography.bodyLarge)
                                    val duration = prefs.shortExitDurationSeconds
                                    Text(stringResource(R.string.duration_seconds, duration.toString()), style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = prefs.shortExitDurationSeconds.toFloat(),
                                    onValueChange = { viewModel?.updateShortExitDuration(it.toInt()) },
                                    valueRange = 0f..300f,
                                    steps = 29,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(stringResource(R.string.short_exit_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.lock_again_after_screen_off), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.lock_again_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = prefs.relockOnScreenOff,
                                onCheckedChange = { viewModel?.toggleRelockOnScreenOff(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.auto_start_after_reboot), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.auto_start_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = prefs.autoStartEnabled,
                                onCheckedChange = { viewModel?.toggleAutoStart(it) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.system_settings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AppTypography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val options = listOf("SYSTEM", "AUTO", "PORTRAIT", "LANDSCAPE")
                                    val nextRotation = options[(options.indexOf(prefs.lockScreenRotation) + 1) % options.size]
                                    viewModel?.updateLockScreenRotation(nextRotation)
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.lock_screen_rotation), style = AppTypography.bodyLarge)
                                val rotationStr = when(prefs.lockScreenRotation) {
                                    "SYSTEM" -> stringResource(R.string.rotation_system)
                                    "AUTO" -> stringResource(R.string.rotation_auto)
                                    "PORTRAIT" -> stringResource(R.string.rotation_portrait)
                                    "LANDSCAPE" -> stringResource(R.string.rotation_landscape)
                                    else -> prefs.lockScreenRotation
                                }
                                Text("${stringResource(R.string.current_colon)} $rotationStr", style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isInspection) {
                                        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val ignored = if (!isInspection) {
                                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                    pm.isIgnoringBatteryOptimizations(context.packageName)
                                } else { false }
                                Text(stringResource(R.string.battery_optimization), style = AppTypography.bodyLarge)
                                Text(if (ignored) stringResource(R.string.battery_ignored) else stringResource(R.string.battery_optimized), style = AppTypography.bodySmall, color = if (ignored) Color(0xFF00AA00) else MaterialTheme.colorScheme.error)
                            }
                            Icon(
                                imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item {
                Text(
                    text = stringResource(R.string.protection_settings),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AppTypography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, start = 8.dp)
                )
            }
            
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isInspection) {
                                        val deviceAdminManager = com.carbon.prolocker.core.security.DeviceAdminManager(context)
                                        if (!deviceAdminManager.isAdminActive()) {
                                            context.startActivity(deviceAdminManager.getActivationIntent())
                                        } else {
                                            deviceAdminManager.removeAdmin()
                                            refreshProtectionStates()
                                        }
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.prevent_easy_uninstall), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.prevent_uninstall_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAdminActive,
                                onCheckedChange = {
                                    if (!isInspection) {
                                        val deviceAdminManager = com.carbon.prolocker.core.security.DeviceAdminManager(context)
                                        if (!deviceAdminManager.isAdminActive()) {
                                            context.startActivity(deviceAdminManager.getActivationIntent())
                                        } else {
                                            deviceAdminManager.removeAdmin()
                                            refreshProtectionStates()
                                        }
                                    }
                                }
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.accessibility_mode), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.accessibility_mode_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAccessibilityActive,
                                onCheckedChange = {
                                    if (!isInspection) {
                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.stealth_mode), style = AppTypography.bodyLarge)
                                Text(stringResource(R.string.stealth_mode_desc), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isStealthActive,
                                onCheckedChange = { 
                                    if (it) {
                                        if (canEnableStealth) {
                                            showStealthWarningDialog = true
                                        } else {
                                            showStealthRequiresLockDialog = true
                                        }
                                    } else {
                                        viewModel?.toggleStealthMode(false, context)
                                        refreshProtectionStates()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAudit() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.security_audit), style = AppTypography.bodyLarge)
                        }
                        Icon(
                            imageVector = if (isRtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(R.string.theme), style = AppTypography.titleLarge) },
                text = {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel?.changeTheme(false); showThemeDialog = false }.padding(16.dp)) {
                            Text(stringResource(R.string.light), style = AppTypography.bodyLarge)
                        }
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel?.changeTheme(true); showThemeDialog = false }.padding(16.dp)) {
                            Text(stringResource(R.string.dark), style = AppTypography.bodyLarge)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.language), style = AppTypography.titleLarge) },
                text = {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel?.changeLanguage("en"); showLanguageDialog = false }.padding(16.dp)) {
                            Text(stringResource(R.string.english), style = AppTypography.bodyLarge)
                        }
                        Row(modifier = Modifier.fillMaxWidth().clickable { viewModel?.changeLanguage("fa"); showLanguageDialog = false }.padding(16.dp)) {
                            Text(stringResource(R.string.persian), style = AppTypography.bodyLarge)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showStealthWarningDialog) {
            AlertDialog(
                onDismissRequest = { showStealthWarningDialog = false },
                title = { Text(stringResource(R.string.stealth_warning_title), style = AppTypography.titleLarge) },
                text = { Text(stringResource(R.string.stealth_warning_message), style = AppTypography.bodyMedium) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showStealthWarningDialog = false
                            viewModel?.toggleStealthMode(true, context)
                            refreshProtectionStates()
                        }
                    ) {
                        Text(stringResource(R.string.enable))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStealthWarningDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showStealthRequiresLockDialog) {
            AlertDialog(
                onDismissRequest = { showStealthRequiresLockDialog = false },
                title = { Text(stringResource(R.string.stealth_requires_lock_title), style = AppTypography.titleLarge) },
                text = { Text(stringResource(R.string.stealth_requires_lock_message), style = AppTypography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { showStealthRequiresLockDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppSettingsScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        AppSettingsScreen(onBack = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AppSettingsScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        AppSettingsScreen(onBack = {})
    }
}

