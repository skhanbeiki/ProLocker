package com.carbon.prolocker.core.database

import com.carbon.prolocker.BuildConfig
import kotlinx.coroutines.flow.Flow

class LockedAppsRepository(private val dao: LockedAppDao) {

    val allLockedApps: Flow<List<LockedAppEntity>> = dao.getAllLockedApps()

    suspend fun cleanupSelfPackage() {
        dao.deleteLockedApp(BuildConfig.APPLICATION_ID)
    }

    suspend fun getLockedApp(packageName: String): LockedAppEntity? {
        if (packageName == BuildConfig.APPLICATION_ID) return null
        return dao.getLockedApp(packageName)
    }

    suspend fun addLockedApp(packageName: String) {
        if (packageName == BuildConfig.APPLICATION_ID) return
        dao.insertLockedApp(LockedAppEntity(packageName = packageName, lockedState = true))
    }

    suspend fun removeLockedApp(packageName: String) {
        dao.deleteLockedApp(packageName)
    }

    suspend fun recordUnlockTime(packageName: String, time: Long) {
        dao.updateUnlockTime(packageName, time)
    }
}
