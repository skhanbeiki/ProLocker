package com.carbon.prolocker.core.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.carbon.prolocker.feature.home.LockedAppsScreen
import com.carbon.prolocker.feature.home.MemoryOptimizerScreen
import com.carbon.prolocker.feature.lock.LockTypeSelectionScreen
import com.carbon.prolocker.feature.lock.PatternSetupScreen
import com.carbon.prolocker.feature.lock.PinSetupScreen
import com.carbon.prolocker.feature.onboarding.PermissionsScreen
import com.carbon.prolocker.feature.onboarding.SuccessScreen
import com.carbon.prolocker.feature.onboarding.WelcomeScreen

import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import com.carbon.prolocker.core.analytics.AnalyticsManager
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    deepLinkType: String? = null,
    isOnboardingCompleted: Boolean,
    trustedLaunchDestination: String? = null,
    isStandaloneExit: Boolean = false
) {
    val navController = rememberNavController()
    val analyticsManager: AnalyticsManager = koinInject()
    val context = LocalContext.current

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: return@OnDestinationChangedListener
            val cleanScreenName = route.substringAfterLast(".").substringBefore("?")
            if (cleanScreenName.isNotBlank()) {
                analyticsManager.trackScreenView(cleanScreenName)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    val startDest = if (isOnboardingCompleted) HomeRoute() else WelcomeRoute

    val effectiveDeepLink = trustedLaunchDestination ?: deepLinkType

    LaunchedEffect(effectiveDeepLink) {
        if (effectiveDeepLink != null) {
            when (effectiveDeepLink) {
                "home" -> navController.navigate(HomeRoute()) { popUpTo(startDest) { inclusive = true } }
                "backgrounds" -> {
                    if (trustedLaunchDestination != null) {
                        navController.navigate(BackgroundGalleryRoute)
                    } else {
                        navController.navigate(HomeRoute()) { popUpTo(startDest) { inclusive = true } }
                        navController.navigate(BackgroundGalleryRoute)
                    }
                }
                "security" -> {
                    navController.navigate(HomeRoute("security")) { popUpTo(startDest) { inclusive = true } }
                }
                "update" -> {
                    navController.navigate(HomeRoute("update")) { popUpTo(startDest) { inclusive = true } }
                }
                "memory" -> {
                    if (trustedLaunchDestination != null) {
                        navController.navigate(MemoryOptimizerRoute)
                    } else {
                        navController.navigate(HomeRoute()) { popUpTo(startDest) { inclusive = true } }
                        navController.navigate(MemoryOptimizerRoute)
                    }
                }
                else -> navController.navigate(HomeRoute()) { popUpTo(startDest) { inclusive = true } }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(LockTypeSelectionRoute)
                }
            )
        }
        composable<LockTypeSelectionRoute> {
            LockTypeSelectionScreen(
                onSelectPattern = { navController.navigate(PatternSetupRoute) },
                onSelectPin = { navController.navigate(PinSetupRoute) }
            )
        }
        composable<PatternSetupRoute> {
            PatternSetupScreen(
                onSetupComplete = {
                    navController.navigate(SuccessRoute)
                }
            )
        }
        composable<PinSetupRoute> {
            PinSetupScreen(
                onSetupComplete = {
                    navController.navigate(SuccessRoute)
                }
            )
        }
        composable<PermissionsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PermissionsRoute>()
            PermissionsScreen(
                pendingPackage = route.pendingPackage,
                onPermissionsGranted = {
                    navController.popBackStack()
                }
            )
        }
        composable<SuccessRoute> {
            SuccessScreen(
                onGoHome = {
                    navController.navigate(HomeRoute()) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HomeRoute>()
            com.carbon.prolocker.feature.main.MainScreen(
                initialTab = route.tab,
                onNavigateToLockedApps = {
                    navController.navigate(LockedAppsRoute)
                },
                onNavigateToLockSetup = {
                    navController.navigate(LockTypeSelectionRoute)
                },
                onNavigateToAudit = {
                    navController.navigate(PrivacyAuditorRoute)
                },
                onNavigateToAppSettings = {
                    navController.navigate(AppSettingsRoute)
                },
                onNavigateToMemoryOptimizer = {
                    navController.navigate(MemoryOptimizerRoute)
                },
                onNavigateToGallery = {
                    navController.navigate(BackgroundGalleryRoute)
                },
                onNavigateToPermissions = { pkg ->
                    navController.navigate(PermissionsRoute(pendingPackage = pkg))
                },
                onNavigateToSecurity = {
                    navController.navigate(SecurityRoute)
                },
                onNavigateToHideFiles = {
                    navController.navigate(HideFilesRoute)
                },
                onNavigateToAboutUs = {
                    navController.navigate(AboutUsRoute)
                },
                onNavigateToBackup = {
                    navController.navigate(BackupHomeRoute)
                },
                onNavigateToCallBlocker = {
                    navController.navigate(CallBlockerRoute)
                }
            )
        }
        composable<CallBlockerRoute> {
            com.carbon.prolocker.feature.callblocker.ui.CallBlockerHomeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable<BackupHomeRoute> {
            com.carbon.prolocker.feature.backup.ui.BackupHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenCategory = { category ->
                    if (category == com.carbon.prolocker.feature.backup.model.BackupCategory.APPLICATIONS) {
                        navController.navigate(BackupAppsRoute)
                    } else {
                        navController.navigate(BackupDetailRoute(category.name))
                    }
                }
            )
        }
        composable<BackupDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BackupDetailRoute>()
            val cat = com.carbon.prolocker.feature.backup.model.BackupCategory.valueOf(route.categoryName)
            com.carbon.prolocker.feature.backup.ui.BackupCategoryDetailScreen(
                category = cat,
                onBack = { navController.popBackStack() }
            )
        }
        composable<BackupAppsRoute> { backStackEntry ->
            val viewModel: com.carbon.prolocker.feature.backup.ui.BackupAppsViewModel = org.koin.androidx.compose.koinViewModel(viewModelStoreOwner = backStackEntry)
            com.carbon.prolocker.feature.backup.ui.BackupAppsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProgress = { navController.navigate(BackupAppsProgressRoute) },
                viewModel = viewModel
            )
        }
        composable<BackupAppsProgressRoute> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<BackupAppsRoute>()
            }
            val viewModel: com.carbon.prolocker.feature.backup.ui.BackupAppsViewModel = org.koin.androidx.compose.koinViewModel(viewModelStoreOwner = parentEntry)
            com.carbon.prolocker.feature.backup.ui.BackupAppsProgressScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable<HideFilesRoute> {
            com.carbon.prolocker.feature.hidefile.ui.HideFilesScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenCategory = { category ->
                    navController.navigate(HiddenItemsRoute(category))
                }
            )
        }
        composable<HiddenItemsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HiddenItemsRoute>()
            val viewModel: com.carbon.prolocker.feature.hidefile.HideFileViewModel = org.koin.androidx.compose.koinViewModel(viewModelStoreOwner = backStackEntry)
            com.carbon.prolocker.feature.hidefile.ui.HiddenItemsScreen(
                type = route.category,
                onBack = {
                    navController.popBackStack()
                },
                onOpenPicker = { category ->
                    navController.navigate(MediaPickerRoute(category))
                },
                viewModel = viewModel
            )
        }
        composable<MediaPickerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<MediaPickerRoute>()
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<HiddenItemsRoute>()
            }
            val viewModel: com.carbon.prolocker.feature.hidefile.HideFileViewModel = org.koin.androidx.compose.koinViewModel(viewModelStoreOwner = parentEntry)
            if (route.category == com.carbon.prolocker.feature.hidefile.data.HideItem.TYPE_FILE) {
                com.carbon.prolocker.feature.hidefile.ui.FilePickerScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    viewModel = viewModel
                )
            } else {
                com.carbon.prolocker.feature.hidefile.ui.MediaPickerScreen(
                    type = route.category,
                    onBack = {
                        navController.popBackStack()
                    },
                    viewModel = viewModel
                )
            }
        }
        composable<IntruderPhotoDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<IntruderPhotoDetailRoute>()
            com.carbon.prolocker.feature.security.IntruderPhotoDetailScreen(
                eventId = route.eventId,
                onBack = { navController.popBackStack() }
            )
        }
        composable<SecurityRoute> {
            com.carbon.prolocker.feature.security.SecurityScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToGallery = {
                    navController.navigate(BackgroundGalleryRoute)
                },
                onNavigateToPhotoDetail = { eventId ->
                    navController.navigate(IntruderPhotoDetailRoute(eventId = eventId))
                }
            )
        }
        composable<LockedAppsRoute> {
            LockedAppsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<MemoryOptimizerRoute> {
            val shouldFinishAffinity = isStandaloneExit && trustedLaunchDestination == "memory"
            MemoryOptimizerScreen(
                onBack = {
                    if (shouldFinishAffinity) {
                        (context as? Activity)?.finishAffinity()
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
        composable<BackgroundGalleryRoute> {
            val isStandalone = trustedLaunchDestination == "backgrounds"
            com.carbon.prolocker.feature.gallery.BackgroundGalleryScreen(
                onBack = {
                    if (isStandalone) {
                        (context as? Activity)?.finishAffinity()
                    } else {
                        navController.popBackStack()
                    }
                },
                onBackgroundClick = { url, id ->
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    navController.navigate(BackgroundPreviewRoute(encodedUrl, id))
                }
            )
        }
        composable<BackgroundPreviewRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BackgroundPreviewRoute>()
            val decodedUrl = java.net.URLDecoder.decode(route.url, "UTF-8")
            com.carbon.prolocker.feature.gallery.BackgroundPreviewScreen(
                url = decodedUrl,
                id = route.id,
                onBack = { navController.popBackStack() }
            )
        }
        composable<SecurityAuditRoute> {
            com.carbon.prolocker.feature.account.SecurityAuditScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AppSettingsRoute> {
            com.carbon.prolocker.feature.account.AppSettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToAudit = {
                    navController.navigate(SecurityAuditRoute)
                }
            )
        }
        composable<AboutUsRoute> {
            com.carbon.prolocker.feature.account.AboutUsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<PrivacyAuditorRoute> {
            com.carbon.prolocker.feature.privacyauditor.PrivacyAuditorScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
