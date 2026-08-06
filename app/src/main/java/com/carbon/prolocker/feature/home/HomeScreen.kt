package com.carbon.prolocker.feature.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.core.theme.ProLockerTertiary
import com.carbon.prolocker.core.ui.components.RecoverySetupDialog
import com.carbon.prolocker.core.ui.components.RecoverySetupIntroDialog
import com.carbon.prolocker.core.utils.toSafeBitmap
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToLockedApps: () -> Unit,
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
                                    Brush.linearGradient(
                                        colors = listOf(ProLockerPrimary, ProLockerSecondary)
                                    ),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
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

                    Box(modifier = Modifier.padding(horizontal = 12.dp).size(40.dp), contentAlignment = Alignment.Center) {
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
                                Text(text = badgeCount.toString(), fontSize = 10.sp)
                            }
                        }
                    }
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Protection Card
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ProLockerPrimary.copy(alpha = 0.25f),
                                    ProLockerSecondary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            ),
                            RoundedCornerShape(24.dp)
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
                                Text(
                                    text = stringResource(R.string.protection_status),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    when (serviceStatus) {
                                        ServiceStatus.ACTIVE -> stringResource(R.string.service_active)
                                        ServiceStatus.IDLE -> stringResource(R.string.service_idle)
                                        ServiceStatus.STOPPED -> stringResource(R.string.service_stopped)
                                    },
                                    color = when (serviceStatus) {
                                        ServiceStatus.ACTIVE -> ProLockerTertiary
                                        ServiceStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.error
                                    },
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = when (serviceStatus) {
                                        ServiceStatus.ACTIVE -> ProLockerTertiary
                                        ServiceStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.apps_protected, totalLockedCount),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                apps.filter { it.isLocked }.take(3).forEach { app ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
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
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel?.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        stringResource(R.string.search_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = ProLockerPrimary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )

            // Apps Section Header
            Text(
                text = "Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            // Apps List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            } else if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_apps_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 160.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(20.dp)
                                )
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
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(14.dp)
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
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            app.packageName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 180.dp),
                                            letterSpacing = 0.2.sp
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
                                    },
                                    modifier = Modifier
                                        .background(
                                            if (app.isLocked)
                                                ProLockerPrimary.copy(alpha = 0.15f)
                                            else
                                                MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (app.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (app.isLocked) stringResource(R.string.unlock) else stringResource(
                                            R.string.lock
                                        ),
                                        tint = if (app.isLocked) ProLockerPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f
                                        ),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
            onNavigateToLockedApps = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        HomeScreen(
            onNavigateToLockedApps = {}
        )
    }
}

