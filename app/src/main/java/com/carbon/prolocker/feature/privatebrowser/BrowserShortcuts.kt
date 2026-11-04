package com.carbon.prolocker.feature.privatebrowser

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.carbon.prolocker.R

/**
 * Pinned home-screen shortcuts. They open [PrivateBrowserActivity], which always asks for the
 * app lock first when the app is not already unlocked.
 */
object BrowserShortcuts {

    private const val BROWSER_SHORTCUT_ID = "private_browser"

    /** Whether the launcher can pin shortcuts at all. */
    fun canPin(context: Context): Boolean = try {
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    } catch (_: Exception) {
        false
    }

    /** True when the browser shortcut is already on the home screen (best effort). */
    fun isBrowserPinned(context: Context): Boolean = try {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == BROWSER_SHORTCUT_ID }
    } catch (_: Exception) {
        false
    }

    fun pinBrowser(context: Context) {
        pin(
            context = context,
            id = BROWSER_SHORTCUT_ID,
            label = context.getString(R.string.private_browser),
            url = null
        )
    }

    fun pinPage(context: Context, url: String, title: String) {
        if (url.isBlank()) return
        pin(
            context = context,
            id = "private_browser_page_" + url.hashCode(),
            label = title.ifBlank { BrowserUrls.host(url) },
            url = url
        )
    }

    private fun pin(context: Context, id: String, label: String, url: String?) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.pb_shortcut_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(context, PrivateBrowserActivity::class.java).apply {
            action = PrivateBrowserActivity.ACTION_OPEN
            if (url != null) putExtra(PrivateBrowserActivity.EXTRA_URL, url)
        }
        val shortLabel = if (label.length > 24) label.take(23) + "…" else label
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_shortcut_private_browser)
            ?.toBitmap(192, 192)
            ?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val info = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(intent)
            .build()
        val requested = try {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        } catch (_: Exception) {
            false
        }
        Toast.makeText(
            context,
            if (requested) R.string.pb_shortcut_requested else R.string.pb_shortcut_not_supported,
            Toast.LENGTH_SHORT
        ).show()
    }
}
