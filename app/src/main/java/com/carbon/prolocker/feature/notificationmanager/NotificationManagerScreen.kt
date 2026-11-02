package com.carbon.prolocker.feature.notificationmanager

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carbon.prolocker.R
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.feature.notificationmanager.data.HiddenNotification
import com.carbon.prolocker.feature.notificationmanager.data.NotificationHiderRepository
import com.carbon.prolocker.feature.notificationmanager.ui.AccessGuideDialog
import com.carbon.prolocker.feature.notificationmanager.ui.AppNotificationsScreen
import com.carbon.prolocker.feature.notificationmanager.ui.ManageAppsScreen
import com.carbon.prolocker.feature.notificationmanager.ui.NotificationHomeScreen
import com.carbon.prolocker.feature.notificationmanager.ui.SelectAppsScreen
import org.koin.compose.koinInject
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.InAppInterstitial
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

val NotificationManagerGradient = listOf(Color(0xFFDB2777), Color(0xFFF472B6))

private enum class NmPage { HOME, SELECT, MANAGE, APP }

/**
 * Notification hider. The screens are already behind the app lock (in-app navigation or
 * [HiddenNotificationsActivity]), so the kept notifications are only readable after unlocking.
 */
@Composable
fun NotificationManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository: NotificationHiderRepository = koinInject()
    val adManager: AdManager = koinInject()
    val enabled by repository.enabled.collectAsState()
    val hiddenApps by repository.hiddenApps.collectAsState()
    val notifications by repository.notifications.collectAsState()
    val unseen by repository.unseenCount.collectAsState()

    var page by rememberSaveable { mutableStateOf(NmPage.HOME) }
    var currentApp by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }

    // Everything is being read now: drop the "n hidden notifications" summary.
    LaunchedEffect(unseen) {
        if (unseen > 0) repository.markSeen()
        NotificationHiderService.cancelSummary(context)
    }

    fun turnOn() {
        if (hiddenApps.isEmpty()) {
            page = NmPage.SELECT
        } else {
            repository.setEnabled(true)
            NotificationAccess.requestRebind(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = NotificationAccess.isGranted(context)
                if (hasAccess && pendingEnable) {
                    pendingEnable = false
                    turnOn()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { InAppInterstitial.preload(context, AdPlacement.INTERSTITIAL_NOTIFICATIONS) }

    // Leaving one app's notifications is a natural break for an (every N-th) interstitial.
    val backFromApp: () -> Unit = {
        page = NmPage.HOME
        InAppInterstitial.maybeShow(context, AdPlacement.INTERSTITIAL_NOTIFICATIONS)
    }

    BackHandler(enabled = page != NmPage.HOME) {
        if (page == NmPage.APP) backFromApp() else page = NmPage.HOME
    }

    val summaries = remember(notifications) { NotificationHiderRepository.summarize(notifications) }

    when (page) {
        NmPage.HOME -> NotificationHomeScreen(
            enabled = enabled,
            hasAccess = hasAccess,
            hiddenApps = hiddenApps,
            summaries = summaries,
            onBack = onBack,
            onToggle = { on ->
                if (!on) {
                    pendingEnable = false
                    repository.setEnabled(false)
                } else if (!NotificationAccess.isGranted(context)) {
                    pendingEnable = true
                    showGuide = true
                } else {
                    turnOn()
                }
            },
            onGrantAccess = {
                pendingEnable = true
                showGuide = true
            },
            onManageApps = { page = NmPage.MANAGE },
            onOpenApp = {
                currentApp = it
                page = NmPage.APP
            },
            onClearAll = { repository.clearAll() },
            ad = {
                NativeAdContainer(
                    adManager = adManager,
                    placement = AdPlacement.NOTIFICATIONS_NATIVE,
                    adType = NativeAdType.TYPE_1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        )

        NmPage.SELECT -> SelectAppsScreen(
            initialSelection = hiddenApps,
            onBack = { page = NmPage.HOME },
            onConfirm = { selection ->
                repository.setHiddenApps(selection)
                repository.setEnabled(true)
                NotificationAccess.requestRebind(context)
                page = NmPage.HOME
                InAppInterstitial.maybeShow(context, AdPlacement.INTERSTITIAL_NOTIFICATIONS)
            }
        )

        NmPage.MANAGE -> ManageAppsScreen(
            hiddenApps = hiddenApps,
            onBack = { page = NmPage.HOME },
            onHide = { repository.hideApp(it) },
            onShow = { repository.showApp(it) }
        )

        NmPage.APP -> {
            val pkg = currentApp
            val items = remember(notifications, pkg) { notifications.filter { it.packageName == pkg } }
            LaunchedEffect(pkg, items.isEmpty()) {
                if (pkg == null || items.isEmpty()) page = NmPage.HOME
            }
            if (pkg != null) {
                AppNotificationsScreen(
                    packageName = pkg,
                    notifications = items,
                    onBack = backFromApp,
                    onOpen = { openNotification(context, repository, it) },
                    onDelete = { repository.delete(it.id) },
                    onClearApp = { repository.clearApp(pkg) },
                    onMarkRead = { repository.markAppRead(pkg) }
                )
            }
        }
    }

    if (showGuide) {
        AccessGuideDialog(
            appName = stringResource(R.string.app_name),
            onDismiss = {
                showGuide = false
                pendingEnable = false
            },
            onOpenSettings = {
                showGuide = false
                TrustedReturnManager.startTrustedReturn()
                if (!NotificationAccess.openSettings(context)) {
                    TrustedReturnManager.clear()
                    pendingEnable = false
                    Toast.makeText(context, R.string.nm_settings_not_found, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/** Opens the notification the way the original app intended, or just launches the app. */
private fun openNotification(context: Context, repository: NotificationHiderRepository, item: HiddenNotification) {
    val pendingIntent = repository.contentIntent(item.id)
    if (pendingIntent != null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            return
        } catch (_: Exception) {
        }
    }
    val launch = context.packageManager.getLaunchIntentForPackage(item.packageName)
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, R.string.nm_cannot_open, Toast.LENGTH_SHORT).show()
}
