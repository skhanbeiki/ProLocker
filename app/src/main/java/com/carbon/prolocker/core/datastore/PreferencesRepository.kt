package com.carbon.prolocker.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

class PreferencesRepository(private val dataStore: DataStore<UserPreferences>) {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val userPreferencesFlow: StateFlow<UserPreferences> = dataStore.data
        .catch { e ->
            Log.e("PreferencesRepository", "DataStore read error", e)
            emit(UserPreferences())
        }
        .stateIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            initialValue = runCatching { runBlocking(Dispatchers.IO) { dataStore.data.first() } }.getOrDefault(UserPreferences())
        )

    val currentPreferences: UserPreferences
        get() = userPreferencesFlow.value

    suspend fun completeOnboarding() {
        runCatching { dataStore.updateData { it.copy(onboardingCompleted = true) } }
    }

    suspend fun saveLockCredential(lockType: String, hashedCredential: String, salt: String) {
        runCatching {
            dataStore.updateData {
                it.copy(
                    lockType = lockType,
                    hashedCredential = hashedCredential,
                    securitySalt = salt
                )
            }
        }
    }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        runCatching { dataStore.updateData(transform) }
    }

    suspend fun updateThemeInterstitialCounter(increment: Boolean) {
        runCatching {
            dataStore.updateData { prefs ->
                if (increment) {
                    prefs.copy(themeInterstitialCounter = prefs.themeInterstitialCounter + 1)
                } else {
                    prefs.copy(themeInterstitialCounter = 0)
                }
            }
        }
    }

    /**
     * Safely reads current preferences, returning defaults on failure.
     */
    suspend fun safeFirst(): UserPreferences {
        return try {
            userPreferencesFlow.first()
        } catch (e: Exception) {
            Log.e("PreferencesRepository", "Failed to read preferences", e)
            UserPreferences()
        }
    }
}
