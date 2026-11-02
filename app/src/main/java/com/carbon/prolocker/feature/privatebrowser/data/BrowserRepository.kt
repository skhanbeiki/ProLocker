package com.carbon.prolocker.feature.privatebrowser.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Bookmarks, history, saved tabs and settings of the private browser.
 * Everything lives in the app's private storage (files + SharedPreferences).
 */
class BrowserRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(appContext.filesDir, "private_browser").apply { mkdirs() }
    private val bookmarksFile = File(dir, "bookmarks.json")
    private val historyFile = File(dir, "history.json")
    private val tabsFile = File(dir, "tabs.json")
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _bookmarks = MutableStateFlow(readBookmarks())
    val bookmarks: StateFlow<List<BrowserBookmark>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<BrowserHistoryEntry>> = _history.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()

    // region bookmarks
    fun isBookmarked(url: String): Boolean = url.isNotBlank() && _bookmarks.value.any { it.url == url }

    /** Returns true when the page is bookmarked after the call. */
    fun toggleBookmark(url: String, title: String): Boolean {
        return if (isBookmarked(url)) {
            removeBookmark(url)
            false
        } else {
            addBookmark(url, title)
            true
        }
    }

    fun addBookmark(url: String, title: String) {
        if (url.isBlank()) return
        _bookmarks.update { list ->
            listOf(BrowserBookmark(url = url, title = title.ifBlank { url })) + list.filterNot { it.url == url }
        }
        persistBookmarks()
    }

    fun removeBookmark(url: String) {
        _bookmarks.update { list -> list.filterNot { it.url == url } }
        persistBookmarks()
    }
    // endregion

    // region history
    fun addHistory(url: String, title: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        _history.update { list ->
            val entry = BrowserHistoryEntry(url = url, title = title.ifBlank { url })
            val rest = if (list.firstOrNull()?.url == url) list.drop(1) else list
            (listOf(entry) + rest).take(MAX_HISTORY)
        }
        persistHistory()
    }

    fun removeHistory(entry: BrowserHistoryEntry) {
        _history.update { list -> list.filterNot { it.url == entry.url && it.visitedAt == entry.visitedAt } }
        persistHistory()
    }

    /** Synchronous so the file is gone even if the process dies right after. */
    fun clearHistory() {
        _history.value = emptyList()
        try {
            historyFile.writeText("[]")
        } catch (_: Exception) {
        }
    }
    // endregion

    // region tabs
    fun savedTabs(): List<String> = try {
        if (tabsFile.exists()) json.decodeFromString<List<String>>(tabsFile.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun saveTabs(urls: List<String>) {
        try {
            if (urls.isEmpty()) tabsFile.delete() else tabsFile.writeText(json.encodeToString(urls))
        } catch (_: Exception) {
        }
    }

    fun clearSavedTabs() {
        try {
            tabsFile.delete()
        } catch (_: Exception) {
        }
    }
    // endregion

    // region settings
    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_ENGINE, updated.searchEngine.id)
            .putBoolean(KEY_SAVE_PASSWORDS, updated.savePasswords)
            .putBoolean(KEY_CLEAR_CACHE, updated.clearCacheOnExit)
            .putBoolean(KEY_CLEAR_COOKIES, updated.clearCookiesOnExit)
            .putBoolean(KEY_CLEAR_HISTORY, updated.clearHistoryOnExit)
            .putBoolean(KEY_CLEAR_SITE_DATA, updated.clearSiteDataOnExit)
            .putBoolean(KEY_CLOSE_TABS, updated.closeTabsOnExit)
            .putBoolean(KEY_THIRD_PARTY, updated.blockThirdPartyCookies)
            .putBoolean(KEY_POPUPS, updated.blockPopups)
            .putBoolean(KEY_DNT, updated.doNotTrack)
            .putBoolean(KEY_JS, updated.javaScriptEnabled)
            .putBoolean(KEY_SECURE, updated.secureScreen)
            .apply()
    }

    private fun loadSettings(): BrowserSettings {
        val d = BrowserSettings()
        return BrowserSettings(
            searchEngine = SearchEngine.fromId(prefs.getString(KEY_ENGINE, d.searchEngine.id)),
            savePasswords = prefs.getBoolean(KEY_SAVE_PASSWORDS, d.savePasswords),
            clearCacheOnExit = prefs.getBoolean(KEY_CLEAR_CACHE, d.clearCacheOnExit),
            clearCookiesOnExit = prefs.getBoolean(KEY_CLEAR_COOKIES, d.clearCookiesOnExit),
            clearHistoryOnExit = prefs.getBoolean(KEY_CLEAR_HISTORY, d.clearHistoryOnExit),
            clearSiteDataOnExit = prefs.getBoolean(KEY_CLEAR_SITE_DATA, d.clearSiteDataOnExit),
            closeTabsOnExit = prefs.getBoolean(KEY_CLOSE_TABS, d.closeTabsOnExit),
            blockThirdPartyCookies = prefs.getBoolean(KEY_THIRD_PARTY, d.blockThirdPartyCookies),
            blockPopups = prefs.getBoolean(KEY_POPUPS, d.blockPopups),
            doNotTrack = prefs.getBoolean(KEY_DNT, d.doNotTrack),
            javaScriptEnabled = prefs.getBoolean(KEY_JS, d.javaScriptEnabled),
            secureScreen = prefs.getBoolean(KEY_SECURE, d.secureScreen)
        )
    }
    // endregion

    /** Set while a browsing session that must be wiped on exit is open; survives a killed process. */
    var hasPendingCleanup: Boolean
        get() = prefs.getBoolean(KEY_PENDING_CLEANUP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PENDING_CLEANUP, value).commit()
        }

    private fun readBookmarks(): List<BrowserBookmark> = try {
        if (bookmarksFile.exists()) json.decodeFromString<List<BrowserBookmark>>(bookmarksFile.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun readHistory(): List<BrowserHistoryEntry> = try {
        if (historyFile.exists()) json.decodeFromString<List<BrowserHistoryEntry>>(historyFile.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    // Each write stores the latest snapshot, on a single serial thread.
    private fun persistBookmarks() {
        ioScope.launch { writeSafely(bookmarksFile, json.encodeToString(_bookmarks.value)) }
    }

    private fun persistHistory() {
        ioScope.launch { writeSafely(historyFile, json.encodeToString(_history.value)) }
    }

    private fun writeSafely(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREFS_NAME = "private_browser_settings"
        private const val MAX_HISTORY = 500
        private const val KEY_ENGINE = "search_engine"
        private const val KEY_SAVE_PASSWORDS = "save_passwords"
        private const val KEY_CLEAR_CACHE = "clear_cache_on_exit"
        private const val KEY_CLEAR_COOKIES = "clear_cookies_on_exit"
        private const val KEY_CLEAR_HISTORY = "clear_history_on_exit"
        private const val KEY_CLEAR_SITE_DATA = "clear_site_data_on_exit"
        private const val KEY_CLOSE_TABS = "close_tabs_on_exit"
        private const val KEY_THIRD_PARTY = "block_third_party_cookies"
        private const val KEY_POPUPS = "block_popups"
        private const val KEY_DNT = "do_not_track"
        private const val KEY_JS = "javascript"
        private const val KEY_SECURE = "secure_screen"
        private const val KEY_PENDING_CLEANUP = "pending_cleanup"
    }
}
