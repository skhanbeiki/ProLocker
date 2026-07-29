package com.carbon.prolocker.feature.security

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.carbon.prolocker.R
import com.carbon.prolocker.core.database.IntruderEventEntity
import org.koin.androidx.compose.koinViewModel
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToGallery: () -> Unit = {},
    onNavigateToPhotoDetail: (Long) -> Unit = {}
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: SecurityViewModel? = if (isInspection) null else koinViewModel()
    val prefs by viewModel?.userPreferences?.collectAsState()
        ?: remember { mutableStateOf(com.carbon.prolocker.core.datastore.UserPreferences()) }
    val events by viewModel?.intruderEvents?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    if (!isInspection) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel?.toggleCaptureSelfie(true)
        } else {
            // User denied permission, do not toggle
        }
    }

    val lockEvents by viewModel?.lockHistoryEvents?.collectAsState() ?: remember {
        mutableStateOf(
            emptyList()
        )
    }
    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteAllPhotosDialog by remember { mutableStateOf(false) }
    var showDeleteSinglePhotoDialog by remember { mutableStateOf<IntruderEventEntity?>(null) }
    var showDeleteAllHistoryDialog by remember { mutableStateOf(false) }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var isSettingsVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    isSettingsVisible = false
                } else if (available.y > 10f) {
                    isSettingsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val analyticsManager =
        org.koin.compose.koinInject<com.carbon.prolocker.core.analytics.AnalyticsManager>()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!isInspection) {
            analyticsManager.trackScreenView("SecurityScreen")
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.security_center),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleLarge,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            AnimatedVisibility(
                visible = isSettingsVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.intruder_settings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        style = com.carbon.prolocker.core.theme.AppTypography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ),
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
                                    Text(
                                        stringResource(R.string.capture_intruder_selfie),
                                        style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                                    )
                                    Text(
                                        stringResource(R.string.capture_intruder_selfie_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = prefs.captureIntruderSelfie,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.CAMERA
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                viewModel?.toggleCaptureSelfie(true)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        } else {
                                            viewModel?.toggleCaptureSelfie(false)
                                        }
                                    }
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.trigger_alarm),
                                        style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                                    )
                                    Text(
                                        stringResource(R.string.trigger_alarm_desc),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = prefs.triggerAlarm,
                                    onCheckedChange = { viewModel?.toggleAlarm(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }


            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.intruders),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleMedium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.lock_history),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleMedium
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_intruders_detected),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDeleteAllPhotosDialog = true }) {
                            Text(
                                stringResource(R.string.clear_all),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp, bottom = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(events, key = { it.id }) { event ->
                            Log.d(
                                "INTRUDER_LOAD",
                                "Loading event id=${event.id}, path=${event.photoPath}, exists=${
                                    java.io.File(event.photoPath).exists()
                                }"
                            )
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { onNavigateToPhotoDetail(event.id) }
                            ) {
                                if (event.photoPath.isNotEmpty()) {
                                    Image(
//                                    painter = rememberAsyncImagePainter(
//                                        request = ImageRequest.Builder(LocalContext.current)
//                                            .data(java.io.File(event.photoPath))
//                                            .build(),
//                                        key = event.id
//                                    ),
                                        painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(java.io.File(event.photoPath))
                                                .crossfade(true)
                                                .build()
                                        ),
                                        contentDescription = "Intruder photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(4.dp)
                                ) {
                                    val persianDate = PersianDate(event.timestamp)
                                    val dateText =
                                        PersianDateFormat("Y/m/d H:i").format(persianDate)
                                    Text(
                                        text = dateText,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }

                                IconButton(
                                    onClick = { showDeleteSinglePhotoDialog = event },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                if (lockEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_security_events),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDeleteAllHistoryDialog = true }) {
                            Text(
                                stringResource(R.string.delete_all_history),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(lockEvents.size) { index ->
                            val event = lockEvents[index]
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val persianDate = PersianDate(event.timestamp)
                                        val dateText =
                                            PersianDateFormat("Y/m/d H:i").format(persianDate)
                                        Text(
                                            stringResource(getEventTypeStringId(event.eventType)),
                                            style = com.carbon.prolocker.core.theme.AppTypography.titleMedium,
                                            fontSize = 14.sp
                                        )
                                        if (event.packageName != null) {
                                            Text(
                                                stringResource(
                                                    R.string.app_prefix,
                                                    event.packageName
                                                ),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (event.details != null) {
                                            Text(
                                                stringResource(
                                                    R.string.details_prefix,
                                                    event.details
                                                ),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = dateText,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.6f
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete All Intruder Photos Confirmation Dialog
    if (showDeleteAllPhotosDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllPhotosDialog = false },
            title = { Text(stringResource(R.string.delete_all_intruder_photos_title)) },
            text = { Text(stringResource(R.string.delete_all_intruder_photos_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllPhotosDialog = false
                        viewModel?.clearAllIntruderEvents()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllPhotosDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Single Intruder Photo Confirmation Dialog
    if (showDeleteSinglePhotoDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteSinglePhotoDialog = null },
            title = { Text(stringResource(R.string.delete_photo_title)) },
            text = { Text(stringResource(R.string.delete_photo_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        val event: IntruderEventEntity? = showDeleteSinglePhotoDialog
                        showDeleteSinglePhotoDialog = null
                        event?.let { viewModel?.deleteIntruderEvent(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSinglePhotoDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete All Lock History Confirmation Dialog
    if (showDeleteAllHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllHistoryDialog = false },
            title = { Text(stringResource(R.string.delete_lock_history_title)) },
            text = { Text(stringResource(R.string.delete_lock_history_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllHistoryDialog = false
                        viewModel?.clearAllLockHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun getEventTypeStringId(eventType: String): Int {
    return when (eventType) {
        "LOCK_TRIGGERED" -> R.string.event_locked
        "UNLOCK_SUCCESS" -> R.string.event_unlock_success
        "UNLOCK_FAILED" -> R.string.event_unlock_failed
        "INTRUDER_DETECTED" -> R.string.event_intruder_detected
        "ALARM_TRIGGERED" -> R.string.event_alarm_triggered
        "RECOVERY_USED" -> R.string.event_recovery_used
        "PERMISSION_REVOKED" -> R.string.event_permission_revoked
        else -> R.string.event_unknown
    }
}

@Preview(showBackground = true)
@Composable
fun SecurityScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SecurityScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SecurityScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        SecurityScreen()
    }
}

