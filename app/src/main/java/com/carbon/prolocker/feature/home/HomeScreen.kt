package com.carbon.prolocker.feature.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.ui.components.RecoverySetupDialog
import com.carbon.prolocker.core.ui.components.RecoverySetupIntroDialog
import com.carbon.prolocker.core.utils.toSafeBitmap
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToLockedApps: () -> Unit,
    onNavigateToMemoryOptimizer: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToPermissions: (String) -> Unit = {}
) {
    val isInspection = LocalInspectionMode.current
    val context = LocalContext.current
    val viewModel: HomeViewModel? = if (isInspection) null else koinViewModel()
    val apps by viewModel?.appsList?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val searchQuery by viewModel?.searchQuery?.collectAsState() ?: remember { mutableStateOf("") }
    val isLoading by viewModel?.isLoading?.collectAsState() ?: remember { mutableStateOf(false) }
    val totalLockedCount by viewModel?.totalLockedCount?.collectAsState(initial = 0)
        ?: remember { mutableStateOf(0) }
    val serviceStatus by viewModel?.serviceStatus?.collectAsState(initial = ServiceStatus.IDLE)
        ?: remember { mutableStateOf(ServiceStatus.IDLE) }

    val listState = rememberLazyListState()
    var isHeaderVisible by remember { mutableStateOf(true) }


    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    isHeaderVisible = false
                } else if (available.y > 10f) {
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val analyticsManager =
        org.koin.compose.koinInject<com.carbon.prolocker.core.analytics.AnalyticsManager>()
    LaunchedEffect(Unit) {
        if (!isInspection) {
            analyticsManager.trackScreenView("HomeScreen")
        }
    }

    LaunchedEffect(Unit) {
        if (!isInspection) {
            viewModel?.onPermissionsGranted()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        if (!isInspection) {
            viewModel?.showProtectionReenabled?.collect {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.protection_reenabled)
                )
            }
        }
    }

    var showRecoveryIntroDialog by remember { mutableStateOf(false) }
    var showRecoverySetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isInspection) {
            viewModel?.showRecoveryOnboarding?.collect {
                showRecoveryIntroDialog = true
            }
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
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.home_title),
                            style = com.carbon.prolocker.core.theme.AppTypography.titleLarge,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    val galleryViewModel =
                        if (isInspection) null else koinViewModel<com.carbon.prolocker.feature.gallery.BackgroundGalleryViewModel>()
                    val badgeCount by galleryViewModel?.newBadgeCount?.collectAsState()
                        ?: remember { mutableStateOf(0) }

                    LaunchedEffect(Unit) {
                        galleryViewModel?.checkNewBackgrounds()
                    }

                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        com.carbon.prolocker.core.ui.ToolbarLottieIcon(
                            animationRes = R.raw.background,
                            onClick = onNavigateToGallery
                        )
                        if (badgeCount > 0) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text(text = badgeCount.toString(),fontSize = 10.sp)
                            }
                        }
                    }
                    com.carbon.prolocker.core.ui.ToolbarLottieIcon(
                        animationRes = R.raw.trash_clean,
                        onClick = onNavigateToMemoryOptimizer,
                        modifier = Modifier.padding(end = 8.dp, start = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // System Status Card
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onNavigateToLockedApps() }
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    when (serviceStatus) {
                                        ServiceStatus.ACTIVE -> stringResource(R.string.service_active)
                                        ServiceStatus.IDLE -> stringResource(R.string.service_idle)
                                        ServiceStatus.STOPPED -> stringResource(R.string.service_stopped)
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 24.sp,
                                    style = com.carbon.prolocker.core.theme.AppTypography.titleMedium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = when (serviceStatus) {
                                        ServiceStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                        ServiceStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                apps.filter { it.isLocked }.take(3).forEach { app ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                CircleShape
                                            )
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.surface,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        app.icon?.let { icon ->
                                            val safeBitmap = icon.toSafeBitmap(128)
                                            if (safeBitmap != null) {
                                                Image(
                                                    bitmap = safeBitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } // closes AnimatedVisibility

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel?.updateSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.search_apps)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )


            // Apps List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (apps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_apps_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            end = 8.dp,
                            start = 8.dp,
                            top = 16.dp,
                            bottom = 160.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (app.isLocked) {
                                            viewModel?.toggleAppLock(app.packageName, true)
                                        } else {
                                            val hasPermissions =
                                                viewModel?.checkPermissionsAndLock(app.packageName)
                                                    ?: true
                                            if (!hasPermissions) {
                                                onNavigateToPermissions(app.packageName)
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        app.icon?.let { icon ->
                                            val safeBitmap = icon.toSafeBitmap(128)
                                            if (safeBitmap != null) {
                                                Image(
                                                    bitmap = safeBitmap,
                                                    contentDescription = "App Icon",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    Column {
                                        Text(
                                            app.name,
                                            style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                                        )
                                        Text(
                                            app.packageName,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 180.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (app.isLocked) {
                                            viewModel?.toggleAppLock(app.packageName, true)
                                        } else {
                                            val hasPermissions =
                                                viewModel?.checkPermissionsAndLock(app.packageName)
                                                    ?: true
                                            if (!hasPermissions) {
                                                onNavigateToPermissions(app.packageName)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (app.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (app.isLocked) stringResource(R.string.unlock) else stringResource(
                                            R.string.lock
                                        ),
                                        tint = if (app.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.5f
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

//            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    RecoverySetupIntroDialog(
        visible = showRecoveryIntroDialog,
        onConfigureNow = {
            showRecoveryIntroDialog = false
            showRecoverySetupDialog = true
        },
        onNotNow = {
            showRecoveryIntroDialog = false
            viewModel?.dismissRecoveryOnboarding()
        }
    )

    RecoverySetupDialog(
        visible = showRecoverySetupDialog,
        onDismiss = { showRecoverySetupDialog = false },
        onSave = { question, answer ->
            viewModel?.setupRecovery(question, answer)
            viewModel?.completeRecoverySetup()
            showRecoverySetupDialog = false
        }
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HomeScreen(
            onNavigateToLockedApps = {},
            onNavigateToMemoryOptimizer = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HomeScreen(
            onNavigateToLockedApps = {},
            onNavigateToMemoryOptimizer = {}
        )
    }
}

