package com.carbon.prolocker.feature.privatebrowser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.data.BrowserRepository
import com.carbon.prolocker.feature.privatebrowser.data.BrowserSettings

/** One browser tab. A tab starts on the start page and gets its WebView on the first load. */
class BrowserTab(val id: Long) {
    var webView: WebView? = null
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var showHome by mutableStateOf(true)

    val hasPage: Boolean get() = webView != null && url.isNotBlank()
}

/**
 * Owns the tabs and their WebViews. It lives exactly as long as [PrivateBrowserActivity]
 * because a WebView must not outlive the activity it was created with.
 */
class BrowserController(
    private val activity: ComponentActivity,
    private val repository: BrowserRepository,
    private val fileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean
) {
    val tabs = mutableStateListOf<BrowserTab>()

    var currentTabId by mutableLongStateOf(-1L)
        private set

    var desktopMode by mutableStateOf(false)
        private set

    var fullscreenView by mutableStateOf<View?>(null)
        private set

    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var nextId = 1L

    val currentTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == currentTabId } ?: tabs.lastOrNull()

    fun newTab(url: String? = null): BrowserTab {
        val tab = BrowserTab(nextId++)
        tabs.add(tab)
        currentTabId = tab.id
        if (!url.isNullOrBlank()) load(tab, url)
        return tab
    }

    fun selectTab(tab: BrowserTab) {
        currentTabId = tab.id
    }

    fun closeTab(tab: BrowserTab) {
        tab.webView?.let { destroyWebView(it) }
        tab.webView = null
        tabs.remove(tab)
        if (tabs.isEmpty()) {
            newTab()
        } else if (currentTabId == tab.id) {
            currentTabId = tabs.last().id
        }
    }

    fun closeAllTabs() {
        tabs.forEach { tab ->
            tab.webView?.let { destroyWebView(it) }
            tab.webView = null
        }
        tabs.clear()
        newTab()
    }

    /** Address bar / search box input. */
    fun submit(input: String) {
        val tab = currentTab ?: newTab()
        load(tab, BrowserUrls.resolve(input, repository.settings.value.searchEngine))
    }

    fun open(url: String, inNewTab: Boolean = false) {
        if (inNewTab) {
            newTab(url)
        } else {
            load(currentTab ?: newTab(), url)
        }
    }

    private fun load(tab: BrowserTab, url: String) {
        val webView = tab.webView ?: createWebView(tab).also { tab.webView = it }
        tab.showHome = false
        tab.url = url
        webView.loadUrl(url, extraHeaders())
    }

    fun reload() {
        currentTab?.webView?.reload()
    }

    fun stopLoading() {
        currentTab?.webView?.stopLoading()
    }

    fun goHome() {
        currentTab?.showHome = true
    }

    fun canGoBack(tab: BrowserTab): Boolean = !tab.showHome

    fun canGoForward(tab: BrowserTab): Boolean =
        if (tab.showHome) tab.hasPage else tab.canGoForward

    /** Returns false when there is nothing left to go back to (the caller then exits). */
    fun goBack(): Boolean {
        if (fullscreenView != null) {
            exitFullscreen()
            return true
        }
        val tab = currentTab ?: return false
        if (tab.showHome) return false
        val webView = tab.webView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            tab.showHome = true
        }
        return true
    }

    fun goForward() {
        val tab = currentTab ?: return
        if (tab.showHome) {
            if (tab.hasPage) tab.showHome = false
            return
        }
        tab.webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun toggleDesktopMode() {
        desktopMode = !desktopMode
        tabs.forEach { tab ->
            tab.webView?.settings?.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else null
        }
        currentTab?.takeIf { !it.showHome }?.webView?.reload()
    }

    fun applySettings(settings: BrowserSettings) {
        tabs.forEach { tab -> tab.webView?.let { applySettings(it, settings) } }
    }

    fun exitFullscreen() {
        val callback = fullscreenCallback
        fullscreenView = null
        fullscreenCallback = null
        callback?.onCustomViewHidden()
    }

    /** URLs of the open pages, used when tabs are kept between sessions. */
    fun openUrls(): List<String> = tabs
        .filter { !it.showHome && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
        .map { it.url }

    fun onPause() {
        tabs.forEach { it.webView?.onPause() }
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        tabs.forEach { it.webView?.onResume() }
    }

    fun destroy() {
        if (fullscreenView != null) exitFullscreen()
        tabs.forEach { tab ->
            tab.webView?.let { destroyWebView(it) }
            tab.webView = null
        }
        tabs.clear()
    }

    private fun destroyWebView(webView: WebView) {
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        } catch (_: Exception) {
        }
    }

    private fun extraHeaders(): Map<String, String> =
        if (repository.settings.value.doNotTrack) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(webView: WebView, settings: BrowserSettings) {
        webView.settings.javaScriptEnabled = settings.javaScriptEnabled
        webView.settings.javaScriptCanOpenWindowsAutomatically = !settings.blockPopups
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !settings.blockThirdPartyCookies)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // "Save passwords" hands the page's login forms to the system autofill / password manager.
            webView.importantForAutofill = if (settings.savePasswords) {
                View.IMPORTANT_FOR_AUTOFILL_AUTO
            } else {
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        }
    }

    private fun createWebView(tab: BrowserTab): WebView {
        val settings = repository.settings.value
        val webView = WebView(activity)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        with(webView.settings) {
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (desktopMode) userAgentString = DESKTOP_USER_AGENT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        applySettings(webView, settings)
        webView.webViewClient = TabWebViewClient(tab)
        webView.webChromeClient = TabChromeClient(tab)
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(activity, R.string.pb_download_not_supported, Toast.LENGTH_SHORT).show()
        }
        return webView
    }

    private fun syncNavigation(tab: BrowserTab, webView: WebView) {
        tab.canGoBack = webView.canGoBack()
        tab.canGoForward = webView.canGoForward()
    }

    private fun openExternal(webView: WebView, url: String) {
        try {
            val intent = if (url.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            try {
                activity.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (!fallback.isNullOrBlank() && fallback.startsWith("http")) {
                    webView.loadUrl(fallback)
                } else {
                    Toast.makeText(activity, R.string.pb_no_app_to_open, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.pb_no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class TabWebViewClient(private val tab: BrowserTab) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return when (uri.scheme?.lowercase()) {
                "http", "https", "about", "data", "blob", "javascript" -> false
                else -> {
                    if (view != null) openExternal(view, uri.toString())
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url != null) tab.url = url
            tab.isLoading = true
            tab.favicon = favicon
            view?.let { syncNavigation(tab, it) }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            tab.isLoading = false
            tab.progress = 100
            if (url != null) tab.url = url
            view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
            view?.let { syncNavigation(tab, it) }
            if (url != null) repository.addHistory(url, tab.title)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (url != null) tab.url = url
            view?.let { syncNavigation(tab, it) }
        }
    }

    private inner class TabChromeClient(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            tab.progress = newProgress
            tab.isLoading = newProgress < 100
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            tab.favicon = icon
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null) return
            if (fullscreenView != null) {
                callback?.onCustomViewHidden()
                return
            }
            fullscreenView = view
            fullscreenCallback = callback
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            if (repository.settings.value.blockPopups && !isUserGesture) return false
            val newTab = BrowserTab(nextId++)
            val webView = createWebView(newTab)
            newTab.webView = webView
            newTab.showHome = false
            tabs.add(newTab)
            currentTabId = newTab.id
            transport.webView = webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            tabs.firstOrNull { it.webView === window }?.let { closeTab(it) }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            if (filePathCallback == null || fileChooserParams == null) return false
            return fileChooser(filePathCallback, fileChooserParams)
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            callback?.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
        }
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
