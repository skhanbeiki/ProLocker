package com.carbon.prolocker.core.service

import com.carbon.prolocker.core.datastore.PreferencesRepository

// Called from the main thread (lock UI, MainActivity, screen receiver) and from the IO monitor loop,
// so every access to the state map is synchronized.
class LockSessionManager(private val preferencesRepository: PreferencesRepository) {

    // Map of packageName to the timestamp it was last exited / verified unlocked.
    // If a package is actively unlocked but not yet exited, its timestamp could be 0L or current unlock time.
    private val unlockedAppsState = mutableMapOf<String, Long>()

    @Synchronized
    fun unlockApp(packageName: String) {
        unlockedAppsState[packageName] = 0L // 0 means actively unlocked and currently foreground
    }

    @Synchronized
    fun lockAll() {
        unlockedAppsState.clear()
    }

    @Synchronized
    fun isAppUnlocked(packageName: String): Boolean {
        if (!unlockedAppsState.containsKey(packageName)) return false

        val exitTimestamp = unlockedAppsState[packageName] ?: 0L
        if (exitTimestamp == 0L) return true // App was unlocked and hasn't been backgrounded

        val shortExitDurationMs = preferencesRepository.currentPreferences.shortExitDurationSeconds * 1000L

        if (System.currentTimeMillis() - exitTimestamp <= shortExitDurationMs) {
            // Still within the grace period
            unlockedAppsState[packageName] = 0L // Reset back to active
            return true
        } else {
            // Grace period expired
            unlockedAppsState.remove(packageName)
            return false
        }
    }

    @Synchronized
    fun markAppBackgrounded(packageName: String, atMillis: Long = System.currentTimeMillis()) {
        if (unlockedAppsState.containsKey(packageName)) {
            unlockedAppsState[packageName] = atMillis
        }
    }

    @Synchronized
    fun removeUnlockedApp(packageName: String) {
        unlockedAppsState.remove(packageName)
    }

    @Synchronized
    fun disarmUnlock(packageName: String) {
        unlockedAppsState.remove(packageName)
    }
}

