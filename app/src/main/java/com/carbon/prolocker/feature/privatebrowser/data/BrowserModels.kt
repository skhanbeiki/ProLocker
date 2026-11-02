package com.carbon.prolocker.feature.privatebrowser.data

import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Serializable
data class BrowserBookmark(
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

enum class SearchEngine(
    val id: String,
    val displayName: String,
    private val searchTemplate: String,
    val homeUrl: String
) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com");

    fun searchUrl(query: String): String =
        searchTemplate.replace("%s", URLEncoder.encode(query, "UTF-8"))

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/** Every switch is on by default, as requested for the private browser. */
data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val savePasswords: Boolean = true,
    val clearCacheOnExit: Boolean = true,
    val clearCookiesOnExit: Boolean = true,
    val clearHistoryOnExit: Boolean = true,
    val clearSiteDataOnExit: Boolean = true,
    val closeTabsOnExit: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val blockPopups: Boolean = true,
    val doNotTrack: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val secureScreen: Boolean = true
)
