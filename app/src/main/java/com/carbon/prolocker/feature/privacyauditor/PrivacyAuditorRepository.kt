package com.carbon.prolocker.feature.privacyauditor

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivacyAuditorRepository(private val context: Context) {

    suspend fun getAppsPermissionAudit(): List<AppPermissionInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val myPackageName = context.packageName

        installedPackages
            .filter { it.packageName != myPackageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                val iconDrawable = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
                val permissionsDetails = requestedPermissions.mapNotNull { perm ->
                    mapPermissionDetail(perm)
                }

                val riskLevel = when {
                    permissionsDetails.any { it.category == PermissionCategory.CAMERA || it.category == PermissionCategory.MICROPHONE || it.category == PermissionCategory.SMS || it.category == PermissionCategory.LOCATION } -> RiskLevel.HIGH
                    permissionsDetails.any { it.category == PermissionCategory.CONTACTS || it.category == PermissionCategory.STORAGE || it.category == PermissionCategory.CALL_LOG } -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }

                AppPermissionInfo(
                    packageName = packageInfo.packageName,
                    appName = appName,
                    iconDrawable = iconDrawable,
                    riskLevel = riskLevel,
                    grantedPermissions = permissionsDetails,
                    isSystemApp = isSystem
                )
            }
            .sortedWith(compareBy<AppPermissionInfo> { it.riskLevel.ordinal }.thenBy { it.appName.lowercase() })
    }

    private fun mapPermissionDetail(permissionName: String): PermissionDetail? {
        return when (permissionName) {
            Manifest.permission.CAMERA -> PermissionDetail(permissionName, "دوربین", PermissionCategory.CAMERA, true)
            Manifest.permission.RECORD_AUDIO -> PermissionDetail(permissionName, "میکروفون", PermissionCategory.MICROPHONE, true)
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> PermissionDetail(permissionName, "موقعیت مکانی", PermissionCategory.LOCATION, true)
            Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS -> PermissionDetail(permissionName, "پیامک‌ها", PermissionCategory.SMS, true)
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS -> PermissionDetail(permissionName, "مخاطبین", PermissionCategory.CONTACTS, true)
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG -> PermissionDetail(permissionName, "لیست تماس", PermissionCategory.CALL_LOG, true)
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE -> PermissionDetail(permissionName, "حافظه", PermissionCategory.STORAGE, true)
            else -> null
        }
    }

    fun calculateSummary(apps: List<AppPermissionInfo>): PrivacySummary {
        val total = apps.size
        val highRisk = apps.count { it.riskLevel == RiskLevel.HIGH }
        val mediumRisk = apps.count { it.riskLevel == RiskLevel.MEDIUM }
        val safe = apps.count { it.riskLevel == RiskLevel.LOW }
        val score = if (total > 0) {
            val penalty = (highRisk * 12) + (mediumRisk * 4)
            (100 - penalty).coerceIn(15, 100)
        } else 100

        return PrivacySummary(
            totalApps = total,
            highRiskApps = highRisk,
            mediumRiskApps = mediumRisk,
            safeApps = safe,
            healthScore = score
        )
    }
}
