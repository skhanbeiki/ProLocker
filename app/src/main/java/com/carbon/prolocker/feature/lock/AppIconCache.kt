package com.carbon.prolocker.feature.lock

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.carbon.prolocker.core.utils.toSafeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches the label and the 48px icon of locked apps so repeat locks render instantly and
 * the PackageManager work never runs on the main thread.
 */
object AppIconCache {

    class Entry(val label: String, val icon: ImageBitmap?)

    private val cache = LruCache<String, Entry>(32)

    fun peek(packageName: String): Entry? = synchronized(cache) { cache.get(packageName) }

    suspend fun load(context: Context, packageName: String): Entry? {
        peek(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val entry = Entry(
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo).toSafeBitmap(48)
                )
                synchronized(cache) { cache.put(packageName, entry) }
                entry
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Throwable) {
                null
            }
        }
    }
}
