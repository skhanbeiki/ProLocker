package com.carbon.prolocker.feature.account

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.permissions.PermissionManager
import com.carbon.prolocker.core.permissions.PermissionManager.isAccessibilityServiceEnabled
import com.carbon.prolocker.core.security.DeviceAdminManager
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.SecurityScoreManager
import com.carbon.prolocker.core.service.AppMonitorAccessibilityService
import com.carbon.prolocker.core.theme.AppTypography
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.core.theme.ProLockerTertiary
import com.carbon.prolocker.core.ui.components.AppToolbar
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(onBack: () -> Unit) {
    val isInspection = LocalInspectionMode.current
    val context = LocalContext.current
    val deviceAdminManager = if (isInspection) null else DeviceAdminManager(context)
    val scoreManager = if (isInspection) null else koinInject<SecurityScoreManager>()
    val eventLogManager = if (isInspection) null else koinInject<EventLogManager>()

    var score by remember { mutableStateOf(0) }
    var events by remember { mutableStateOf<List<com.carbon.prolocker.core.database.SecurityEventEntity>>(emptyList()) }

    if (!isInspection) {
        LaunchedEffect(Unit) {
            score = scoreManager?.calculateScore() ?: 0
            eventLogManager?.getRecentEvents()?.collect { events = it }
        }
    }

    val usageStats = if (isInspection) true else PermissionManager.hasUsageAccess(context)
    val overlay = if (isInspection) true else PermissionManager.hasOverlayPermission(context)
    val batteryOpt = if (isInspection) true else PermissionManager.isIgnoringBatteryOptimizations(context)
    val admin = if (isInspection) false else deviceAdminManager?.isAdminActive() ?: false
    val accessibility = if (isInspection) false else isAccessibilityServiceEnabled(context, AppMonitorAccessibilityService::class.java)
    val service = true

    Scaffold(
        topBar = {
            AppToolbar(
                title = stringResource(R.string.security_audit),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ProLockerPrimary.copy(alpha = 0.25f),
                                        ProLockerSecondary.copy(alpha = 0.12f)
                                    )
                                )
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            Text(
                                stringResource(R.string.security_score),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = AppTypography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.score_format, score.toString()),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (score >= 90) stringResource(R.string.excellent_protection)
                                else if (score >= 70) stringResource(R.string.good_protection)
                                else stringResource(R.string.needs_attention),
                                color = if (score >= 90) ProLockerTertiary
                                       else if (score >= 70) ProLockerPrimary
                                       else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.protection_status),
                    style = MaterialTheme.typography.titleMedium,
                    color = ProLockerPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            item { AuditItem(stringResource(R.string.audit_usage_access), stringResource(R.string.audit_usage_access_desc), usageStats) }
            item { AuditItem(stringResource(R.string.audit_draw_over_apps), stringResource(R.string.audit_draw_over_apps_desc), overlay) }
            item { AuditItem(stringResource(R.string.audit_battery_opt), stringResource(R.string.audit_battery_opt_desc), batteryOpt) }
            item { AuditItem(stringResource(R.string.audit_device_admin), stringResource(R.string.audit_device_admin_desc), admin, isWarningMode = true) }
            item { AuditItem(stringResource(R.string.audit_accessibility_mode), stringResource(R.string.audit_accessibility_mode_desc), accessibility, isWarningMode = true) }
            item { AuditItem(stringResource(R.string.audit_background_service), stringResource(R.string.audit_background_service_desc), service) }

            item {
                Text(
                    text = stringResource(R.string.event_log),
                    style = MaterialTheme.typography.titleMedium,
                    color = ProLockerPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_events_recorded),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(events, key = { it.id }) { event ->
                    EventLogItem(event)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun EventLogItem(event: com.carbon.prolocker.core.database.SecurityEventEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                Text(event.eventType, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                if (event.packageName != null) {
                    Text(
                        stringResource(R.string.app_prefix, event.packageName),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.details != null) {
                    Text(
                        stringResource(R.string.details_prefix, event.details),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    sdf.format(Date(event.timestamp)),
                    style = AppTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun AuditItem(title: String, description: String, isOk: Boolean, isWarningMode: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (isOk) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.healthy), tint = ProLockerTertiary, modifier = Modifier.size(24.dp))
            } else {
                val tint = if (isWarningMode) ProLockerSecondary else MaterialTheme.colorScheme.error
                Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.missing_or_warning), tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecurityAuditScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SecurityAuditScreen(onBack = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SecurityAuditScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SecurityAuditScreen(onBack = {})
    }
}
