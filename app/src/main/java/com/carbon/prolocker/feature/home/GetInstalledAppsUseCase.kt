package com.carbon.prolocker.feature.home

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.core.utils.toSafeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null,
    val isLocked: Boolean = false
)

class GetInstalledAppsUseCase(private val context: Context) {

    suspend operator fun invoke(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val apps = mutableListOf<AppInfo>()
        val packages = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Throwable) {
            emptyList()
        }

        for (app in packages) {
            if (app.packageName == BuildConfig.APPLICATION_ID) continue
            try {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val name = pm.getApplicationLabel(app).toString()
                    apps.add(AppInfo(app.packageName, name, loadSmallIcon(pm, app)))
                }
            } catch (_: Throwable) {
                // One broken package (or a vendor icon customizer failing) must not fail the whole list.
            }
        }

        apps.sortedBy { it.name.lowercase() }
    }

    /**
     * The list only shows icons at 44dp, so each icon is drawn once into a small bitmap and cached.
     * Keeping every app's full Drawable (often 1000px+ adaptive icons) ran low-memory phones out of heap.
     */
    private fun loadSmallIcon(pm: PackageManager, app: android.content.pm.ApplicationInfo): Drawable? {
        val key = app.packageName + ":" + (try { pm.getPackageInfo(app.packageName, 0).lastUpdateTime } catch (_: Throwable) { 0L })
        synchronized(iconCache) { iconCache.get(key) }?.let { return it }
        return try {
            val bitmap = pm.getApplicationIcon(app).toSafeBitmap(ICON_PX, ICON_PX) ?: return null
            BitmapDrawable(context.resources, bitmap).also { synchronized(iconCache) { iconCache.put(key, it) } }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        /** Matches the size the list converts icons to, so no further scaling happens. */
        const val ICON_PX = 128

        /** About 8 MB of 128px ARGB icons, shared across reloads of the list. */
        val iconCache = object : LruCache<String, Drawable>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Drawable): Int =
                (value as? BitmapDrawable)?.bitmap?.allocationByteCount ?: (ICON_PX * ICON_PX * 4)
        }
    }
}
