package com.carbon.prolocker.feature.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.ad.findActivity
import com.carbon.prolocker.core.config.MarketConfig
import com.carbon.prolocker.core.rate.RateAppDialog
import com.carbon.prolocker.core.rate.RateAppManager
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.core.theme.ProLockerSurfaceVariant
import com.carbon.prolocker.feature.account.AccountScreen
import com.carbon.prolocker.feature.home.HomeScreen
import com.carbon.prolocker.feature.tools.ToolsScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun MainScreen(
    initialTab: String? = null,
    onNavigateToLockedApps: () -> Unit = {},
    onNavigateToLockSetup: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToAppSettings: () -> Unit = {},
    onNavigateToMemoryOptimizer: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToPermissions: (String) -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToAboutUs: () -> Unit = {},
    onNavigateToHideFiles: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToCallBlocker: () -> Unit = {},
    viewModel: MainViewModel = koinViewModel()
) {
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    var showExitScreen by remember { mutableStateOf(false) }
    val adManager: AdManager = koinInject()
    val rateAppManager: RateAppManager = koinInject()
    val activity = LocalContext.current.findActivity()
    var showRateDialog by remember { mutableStateOf(false) }
    val rateDialogScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val shouldShow = rateAppManager.shouldShowDialog()
        if (shouldShow) showRateDialog = true
    }

    LaunchedEffect(activity) {
        activity?.let {
            adManager.preloadNativeAd(
                activity = it,
                placement = AdPlacement.EXIT_NATIVE,
                adType = NativeAdType.TYPE_6
            )
        }
    }

    val homeViewModel: com.carbon.prolocker.feature.home.HomeViewModel = koinViewModel()
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val selectedTab by viewModel.selectedTab.collectAsState()

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            viewModel.setSelectedTab(
                when (initialTab) {
                    "security" -> MainTab.TOOLS
                    "account" -> MainTab.ACCOUNT
                    else -> MainTab.TOOLS
                }
            )
        }
    }

    var adKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab) {
        adKey++
        Log.d("MainScreen", "HOME_AD_REQUEST_START tab=$selectedTab")
    }

    androidx.activity.compose.BackHandler(enabled = !showExitScreen) {
        if (selectedTab != MainTab.TOOLS) {
            viewModel.setSelectedTab(MainTab.TOOLS)
        } else {
            showExitScreen = true
        }
    }

    if (updateState?.displayType == "dialog") {
        AlertDialog(
            onDismissRequest = {
                if (updateState?.priority == "normal") {
                    viewModel.dismissUpdate()
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            title = {
                Text(
                    updateState?.title ?: "",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f).hashCode()
                val bgColor = MaterialTheme.colorScheme.surfaceVariant.hashCode()
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.TextView(ctx).apply {
                            text = HtmlCompat.fromHtml(
                                updateState?.description ?: "",
                                HtmlCompat.FROM_HTML_MODE_COMPACT
                            )
                            setTextColor(textColor)
                            setBackgroundColor(bgColor)
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { openMarket(context) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.update))
                }
            },
            dismissButton = {
                if (updateState?.priority == "normal") {
                    TextButton(onClick = { viewModel.dismissUpdate() }) {
                        Text(stringResource(R.string.close))
                    }
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = updateState?.priority == "normal",
                dismissOnClickOutside = updateState?.priority == "normal"
            )
        )
    }

    if (showRateDialog) {
        RateAppDialog(
            onRateClicked = {
                showRateDialog = false
                MarketConfig.rateApp(context)
                rateDialogScope.launch { rateAppManager.onRateClicked() }
            },
            onDontShowAgainClicked = {
                showRateDialog = false
                rateDialogScope.launch { rateAppManager.onDontShowAgainClicked() }
            },
            onDismiss = { showRateDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (updateState?.displayType == "notification") {
                        Surface(
                            color = ProLockerPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.new_version_available),
                                    modifier = Modifier.weight(1f),
                                    color = ProLockerPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = { openMarket(context) },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(stringResource(R.string.update))
                                }
                                if (updateState?.priority == "normal") {
                                    IconButton(onClick = { viewModel.dismissUpdate() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.close),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    key(adKey) {
                        val placement = when (selectedTab) {
                            MainTab.HOME -> AdPlacement.HOME_TAB_APPS
                            MainTab.TOOLS -> AdPlacement.HOME_TAB_THEMES
                            MainTab.ACCOUNT -> AdPlacement.HOME_TAB_SETTINGS
                        }
                        NativeAdContainer(
                            adManager = adManager,
                            placement = placement,
                            adType = NativeAdType.TYPE_1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == MainTab.TOOLS,
                            onClick = { viewModel.setSelectedTab(MainTab.TOOLS) },
                            icon = {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = if (selectedTab == MainTab.TOOLS)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.tools),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedTab == MainTab.TOOLS)
                                        FontWeight.SemiBold
                                    else
                                        FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = ProLockerSecondary.copy(alpha = 0.25f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.HOME,
                            onClick = { viewModel.setSelectedTab(MainTab.HOME) },
                            icon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (selectedTab == MainTab.HOME)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.home),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedTab == MainTab.HOME)
                                        FontWeight.SemiBold
                                    else
                                        FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = ProLockerSecondary.copy(alpha = 0.25f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.ACCOUNT,
                            onClick = { viewModel.setSelectedTab(MainTab.ACCOUNT) },
                            icon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = if (selectedTab == MainTab.ACCOUNT)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.account),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedTab == MainTab.ACCOUNT)
                                        FontWeight.SemiBold
                                    else
                                        FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = ProLockerSecondary.copy(alpha = 0.25f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            when (selectedTab) {
                MainTab.TOOLS -> ToolsScreen(
                    paddingValues = PaddingValues(0.dp),
                    onNavigateToSecurity = onNavigateToSecurity,
                    onNavigateToHideFiles = onNavigateToHideFiles,
                    onNavigateToBackup = onNavigateToBackup,
                    onNavigateToAppLock = { viewModel.setSelectedTab(MainTab.HOME) },
                    onNavigateToMemoryOptimizer = onNavigateToMemoryOptimizer,
                    onNavigateToCallBlocker = onNavigateToCallBlocker
                )

                MainTab.HOME -> HomeScreen(
                    paddingValues = PaddingValues(0.dp),
                    onNavigateToLockedApps = onNavigateToLockedApps,
                    onNavigateToGallery = onNavigateToGallery,
                    onNavigateToPermissions = onNavigateToPermissions,
                )


                MainTab.ACCOUNT -> AccountScreen(
                    paddingValues = PaddingValues(0.dp),
                    onNavigateToLockSetup = onNavigateToLockSetup,
                    onNavigateToAudit = onNavigateToAudit,
                    onNavigateToAppSettings = onNavigateToAppSettings,
                    onNavigateToAboutUs = onNavigateToAboutUs
                )
            }
        }

        if (showExitScreen) {
            val cachedExitAd = remember {
                adManager.consumeCachedNativeAdView(AdPlacement.EXIT_NATIVE)
            }
            ExitBottomSheet(
                adManager = adManager,
                preloadedAd = cachedExitAd,
                onDismiss = { showExitScreen = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitBottomSheet(
    adManager: AdManager,
    preloadedAd: android.view.View? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (preloadedAd != null) {
                NativeAdContainer(
                    preloadedAd = preloadedAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                NativeAdContainer(
                    adManager = adManager,
                    placement = AdPlacement.EXIT_NATIVE,
                    adType = NativeAdType.TYPE_6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .clickable(
                        onClick = {
                            (context as? android.app.Activity)?.finishAffinity()
                        }
                    )
                    .height(56.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ProLockerPrimary, ProLockerSecondary)
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.exit_title),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

fun openMarket(context: android.content.Context) {
    val packageName = context.packageName
    val uri = when (MarketConfig.marketType) {
        "bazaar" -> "bazaar://details?id=$packageName"
        "googleplay" -> "market://details?id=$packageName"
        else -> "myket://details?id=$packageName"
    }

    val fallbackUri = when (MarketConfig.marketType) {
        "bazaar" -> "https://cafebazaar.ir/app/$packageName"
        "googleplay" -> "https://play.google.com/store/apps/details?id=$packageName"
        else -> "https://myket.ir/app/$packageName"
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (_e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)))
        } catch (_e: Exception) {
        }
    }
}


enum class MainTab(val tabName: String) {
    HOME("home"), TOOLS("tools"), ACCOUNT("account");

    companion object {
        fun fromName(name: String?): MainTab = when (name) {
            "home" -> HOME
            "account" -> ACCOUNT
            else -> TOOLS
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        MainScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MainScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        MainScreen()
    }
}
