package com.carbon.prolocker.feature.privatebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.carbon.prolocker.R
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import com.carbon.prolocker.feature.privatebrowser.data.BrowserDataCleaner
import com.carbon.prolocker.feature.privatebrowser.data.BrowserRepository
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserActions
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserBookmarksScreen
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserHistoryScreen
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserMainScreen
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserSettingsScreen
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserShortcutOfferSheet
import com.carbon.prolocker.feature.privatebrowser.ui.BrowserTabsScreen
import com.carbon.prolocker.feature.privatebrowser.ui.ClearingDataScreen
import com.carbon.prolocker.feature.privatebrowser.ui.ClearingRequest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.InAppInterstitial
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private enum class BrowserPage { BROWSER, TABS, BOOKMARKS, HISTORY, SETTINGS }

/**
 * The private browser. It is its own activity so it can be opened straight from a home-screen
 * shortcut; whenever the app is locked (shortcut, recents, process start) the app's entry lock
 * is shown first and the browser stays blank until it is passed.
 */
class PrivateBrowserActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN = "com.carbon.prolocker.action.OPEN_PRIVATE_BROWSER"
        const val EXTRA_URL = "private_browser_url"

        fun start(context: Context, url: String? = null) {
            val intent = Intent(context, PrivateBrowserActivity::class.java).apply {
                if (url != null) putExtra(EXTRA_URL, url)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val repository: BrowserRepository by inject()
    private val preferencesRepository: PreferencesRepository by inject()
    private val languageManager: LanguageManager by inject()
    private val adManager: AdManager by inject()

    private lateinit var controller: BrowserController

    private var authorized by mutableStateOf(false)
    private var page by mutableStateOf(BrowserPage.BROWSER)
    private var clearing by mutableStateOf<ClearingRequest?>(null)
    /** Set once the browser is leaving: nothing is drawn while the exit interstitial shows. */
    private var exiting by mutableStateOf(false)
    /** The one-time "add to home screen" sheet, shown on the first exit. */
    private var showShortcutOffer by mutableStateOf(false)
    /** The launcher's pin dialog is up after "add": no exit interstitial on top of it. */
    private var skipExitAd = false

    private var awaitingAuth = false
    private var sessionStarted = false
    private var cleanedUp = false
    private var pendingUrl: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val entryLockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        awaitingAuth = false
        if (result.resultCode == RESULT_OK) {
            AppEntryLockActivity.markAuthenticated()
            onAuthorized()
        } else {
            finish()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun attachBaseContext(newBase: Context) {
        val repo = try {
            org.koin.java.KoinJavaComponent.get<PreferencesRepository>(PreferencesRepository::class.java)
        } catch (_: Exception) {
            null
        }
        val effective = LanguageManager.resolveEffectiveLanguage(repo, newBase)
        super.attachBaseContext(LanguageManager.createLocalizedContextStatic(newBase, effective))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applySecureFlag(repository.settings.value.secureScreen)
        com.carbon.prolocker.core.ui.NewFeatureBadges.markSeen(this, com.carbon.prolocker.core.ui.NewFeatureBadges.PRIVATE_BROWSER)

        // A previous session that was killed before it could clean up is wiped now.
        if (repository.hasPendingCleanup) {
            BrowserDataCleaner.runAllNow(BrowserDataCleaner.exitSteps(repository.settings.value), this, repository)
            repository.hasPendingCleanup = false
        }

        controller = BrowserController(this, repository) { callback, params -> openFileChooser(callback, params) }
        pendingUrl = intent?.getStringExtra(EXTRA_URL)

        setContent {
            val prefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = prefs?.isDarkMode ?: true
            val language = languageManager.getEffectiveLanguageTag(prefs?.language)
            val layoutDirection = languageManager.getLayoutDirection(language)
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, language) {
                languageManager.createLocalizedContext(baseContext, language)
            }
            ProLockerTheme(useDarkTheme = isDarkMode) {
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalLayoutDirection provides layoutDirection
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .imePadding()
                    ) {
                        if (authorized && !exiting) BrowserContent()
                        if (showShortcutOffer) {
                            BrowserShortcutOfferSheet(
                                onAdd = { onShortcutOfferAnswered(add = true) },
                                onDismiss = { onShortcutOfferAnswered(add = false) }
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repository.settings.collect { settings ->
                applySecureFlag(settings.secureScreen)
                controller.applySettings(settings)
                if (sessionStarted && !cleanedUp) {
                    repository.hasPendingCleanup = BrowserDataCleaner.exitSteps(settings).isNotEmpty()
                }
            }
        }
        lifecycleScope.launch {
            snapshotFlow { controller.fullscreenView != null }.collect { fullscreen ->
                val insets = WindowCompat.getInsetsController(window, window.decorView)
                if (fullscreen) {
                    insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insets.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insets.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { url ->
            pendingUrl = url
            if (authorized) consumePendingUrl()
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
        if (awaitingAuth) return
        if (needsLock()) {
            if (TrustedReturnManager.consumeTrustedReturn()) {
                // Back from the file picker or share sheet we opened ourselves.
                AppEntryLockActivity.markAuthenticated()
                onAuthorized()
                return
            }
            authorized = false
            awaitingAuth = true
            entryLockLauncher.launch(Intent(this, AppEntryLockActivity::class.java))
        } else {
            onAuthorized()
        }
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing && sessionStarted && !cleanedUp) {
            // Closed without the cleanup screen (swiped away, lock cancelled...): wipe silently.
            val settings = repository.settings.value
            if (!settings.closeTabsOnExit) repository.saveTabs(controller.openUrls())
            BrowserDataCleaner.runAllNow(BrowserDataCleaner.exitSteps(settings), this, repository)
            repository.hasPendingCleanup = false
            cleanedUp = true
        }
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        controller.destroy()
        super.onDestroy()
    }

    private fun needsLock(): Boolean {
        if (!AppEntryLockActivity.requiresAuthentication()) return false
        val prefs = preferencesRepository.currentPreferences
        return prefs.onboardingCompleted && prefs.lockType != "NONE"
    }

    private fun onAuthorized() {
        if (!sessionStarted) {
            sessionStarted = true
            val settings = repository.settings.value
            val restored = if (!settings.closeTabsOnExit) repository.savedTabs() else emptyList()
            repository.clearSavedTabs()
            restored.forEach { controller.newTab(it) }
            if (controller.tabs.isEmpty()) controller.newTab()
            repository.hasPendingCleanup = BrowserDataCleaner.exitSteps(settings).isNotEmpty()
        }
        authorized = true
        consumePendingUrl()
        InAppInterstitial.preload(this, AdPlacement.INTERSTITIAL_BROWSER_EXIT)
    }

    private fun consumePendingUrl() {
        val url = pendingUrl ?: return
        pendingUrl = null
        val current = controller.currentTab
        if (current != null && current.showHome && !current.hasPage) {
            controller.open(url)
        } else {
            controller.newTab(url)
        }
        page = BrowserPage.BROWSER
    }

    private fun applySecureFlag(secure: Boolean) {
        // Screenshots stay blocked in release builds only, so debug builds can be captured for testing.
        if (secure && !com.carbon.prolocker.BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            TrustedReturnManager.startTrustedReturn()
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (_: Exception) {
            TrustedReturnManager.clear()
            fileCallback = null
            false
        }
    }

    /** Leaves the browser; shows the cleanup screen first when something has to be wiped. */
    private fun requestExit() {
        if (clearing != null || showShortcutOffer) return
        // First exit ever: offer the home-screen shortcut once, for more direct visits to the browser.
        if (!repository.shortcutOfferShown) {
            repository.shortcutOfferShown = true
            if (BrowserShortcuts.canPin(this) && !BrowserShortcuts.isBrowserPinned(this)) {
                showShortcutOffer = true
                return
            }
        }
        proceedExit()
    }

    private fun onShortcutOfferAnswered(add: Boolean) {
        if (!showShortcutOffer) return
        showShortcutOffer = false
        if (add) {
            BrowserShortcuts.pinBrowser(this)
            skipExitAd = true
        }
        proceedExit()
    }

    private fun proceedExit() {
        if (clearing != null) return
        val settings = repository.settings.value
        if (!settings.closeTabsOnExit) repository.saveTabs(controller.openUrls())
        val steps = BrowserDataCleaner.exitSteps(settings)
        if (steps.isEmpty()) {
            finishCleanly()
        } else {
            page = BrowserPage.BROWSER
            clearing = ClearingRequest(steps = steps, exitAfter = true)
        }
    }

    private fun finishCleanly() {
        cleanedUp = true
        repository.hasPendingCleanup = false
        exiting = true
        if (skipExitAd) {
            if (!isFinishing) finish()
            return
        }
        // The data is already wiped; the exit is a natural break for an (every N-th) interstitial.
        InAppInterstitial.maybeShow(this, AdPlacement.INTERSTITIAL_BROWSER_EXIT) {
            if (!isFinishing) finish()
        }
    }

    private fun onClearingFinished() {
        val request = clearing ?: return
        clearing = null
        if (request.exitAfter) {
            finishCleanly()
        } else {
            Toast.makeText(this, R.string.pb_clearing_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(url: String) {
        try {
            TrustedReturnManager.startTrustedReturn()
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            startActivity(Intent.createChooser(send, null))
        } catch (_: Exception) {
            TrustedReturnManager.clear()
        }
    }

    private fun copyLink(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.pb_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun BrowserContent() {
        val settings by repository.settings.collectAsState()
        val bookmarks by repository.bookmarks.collectAsState()
        val history by repository.history.collectAsState()

        val request = clearing
        if (request != null) {
            BackHandler(enabled = true) { }
            ClearingDataScreen(
                request = request,
                runStep = { step ->
                    if (step == com.carbon.prolocker.feature.privatebrowser.data.CleanupStep.TABS) controller.closeAllTabs()
                    BrowserDataCleaner.run(step, this@PrivateBrowserActivity, repository)
                },
                onFinished = { onClearingFinished() },
                ad = {
                    NativeAdContainer(
                        adManager = adManager,
                        placement = AdPlacement.BROWSER_CLEANUP_NATIVE,
                        adType = NativeAdType.TYPE_1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            )
            return
        }

        BackHandler(enabled = true) {
            when {
                controller.fullscreenView != null -> controller.exitFullscreen()
                page != BrowserPage.BROWSER -> page = BrowserPage.BROWSER
                !controller.goBack() -> requestExit()
            }
        }

        val openUrl: (String) -> Unit = { url ->
            controller.open(url)
            page = BrowserPage.BROWSER
        }

        when (page) {
            BrowserPage.BROWSER -> BrowserMainScreen(
                controller = controller,
                settings = settings,
                bookmarks = bookmarks,
                actions = remember {
                    BrowserActions(
                        onExit = { requestExit() },
                        onOpenTabs = { page = BrowserPage.TABS },
                        onOpenBookmarks = { page = BrowserPage.BOOKMARKS },
                        onOpenHistory = { page = BrowserPage.HISTORY },
                        onOpenSettings = { page = BrowserPage.SETTINGS },
                        onToggleBookmark = { tab ->
                            val added = repository.toggleBookmark(tab.url, tab.title)
                            Toast.makeText(
                                this@PrivateBrowserActivity,
                                if (added) R.string.pb_bookmark_added else R.string.pb_bookmark_removed,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onShare = { share(it) },
                        onCopyLink = { copyLink(it) },
                        onAddPageShortcut = { tab ->
                            BrowserShortcuts.pinPage(this@PrivateBrowserActivity, tab.url, tab.title)
                        },
                        onAddBrowserShortcut = { BrowserShortcuts.pinBrowser(this@PrivateBrowserActivity) },
                        homeAd = {
                            NativeAdContainer(
                                adManager = adManager,
                                placement = AdPlacement.BROWSER_HOME_NATIVE,
                                adType = NativeAdType.TYPE_1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    )
                }
            )

            BrowserPage.TABS -> BrowserTabsScreen(
                controller = controller,
                onBack = { page = BrowserPage.BROWSER }
            )

            BrowserPage.BOOKMARKS -> BrowserBookmarksScreen(
                bookmarks = bookmarks,
                onBack = { page = BrowserPage.BROWSER },
                onOpen = openUrl,
                onOpenInNewTab = { url ->
                    controller.open(url, inNewTab = true)
                    page = BrowserPage.BROWSER
                },
                onAddShortcut = { BrowserShortcuts.pinPage(this, it.url, it.title) },
                onDelete = { repository.removeBookmark(it.url) }
            )

            BrowserPage.HISTORY -> BrowserHistoryScreen(
                history = history,
                onBack = { page = BrowserPage.BROWSER },
                onOpen = openUrl,
                onDelete = { repository.removeHistory(it) },
                onClearAll = { repository.clearHistory() }
            )

            BrowserPage.SETTINGS -> BrowserSettingsScreen(
                settings = settings,
                onBack = { page = BrowserPage.BROWSER },
                onUpdate = { transform -> repository.updateSettings(transform) },
                onAddShortcut = { BrowserShortcuts.pinBrowser(this) },
                onClearNow = {
                    page = BrowserPage.BROWSER
                    clearing = ClearingRequest(steps = BrowserDataCleaner.manualSteps, exitAfter = false)
                }
            )
        }

        val fullscreen = controller.fullscreenView
        if (fullscreen != null) {
            key(fullscreen) {
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.BLACK) }
                    },
                    update = { container ->
                        if (container.getChildAt(0) !== fullscreen) {
                            container.removeAllViews()
                            (fullscreen.parent as? ViewGroup)?.removeView(fullscreen)
                            container.addView(
                                fullscreen,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }
}
