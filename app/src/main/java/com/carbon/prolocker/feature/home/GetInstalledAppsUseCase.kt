package com.carbon.prolocker.feature.home

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.carbon.prolocker.BuildConfig
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
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        for (app in packages) {
            if (app.packageName == BuildConfig.APPLICATION_ID) continue
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val name = pm.getApplicationLabel(app).toString()
                val icon = pm.getApplicationIcon(app)
                apps.add(AppInfo(app.packageName, name, icon))
            }
        }
        
        apps.sortedBy { it.name.lowercase() }
    }
}
