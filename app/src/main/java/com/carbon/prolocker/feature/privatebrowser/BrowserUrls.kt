package com.carbon.prolocker.feature.privatebrowser

import android.net.Uri
import android.util.Patterns
import com.carbon.prolocker.feature.privatebrowser.data.SearchEngine

object BrowserUrls {

    /** Turns what the user typed into a URL: a web address as is, anything else as a search. */
    fun resolve(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return engine.homeUrl
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")) {
            return text
        }
        if (!text.contains(' ') && looksLikeAddress(text)) return "https://$text"
        return engine.searchUrl(text)
    }

    private fun looksLikeAddress(text: String): Boolean {
        val host = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.startsWith("localhost")) return true
        if (!host.contains('.') || host.endsWith('.') || host.startsWith('.')) return false
        return Patterns.WEB_URL.matcher(text).matches()
    }

    fun host(url: String): String = try {
        Uri.parse(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
    } catch (_: Exception) {
        url
    }

    /** Short form shown in the address bar while it is not being edited. */
    fun display(url: String): String {
        if (url.isBlank()) return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }
}
