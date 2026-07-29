package com.carbon.prolocker.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

class PreferencesRepository(private val dataStore: DataStore<UserPreferences>) {

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { e ->
            Log.e("PreferencesRepository", "DataStore read error", e)
            emit(UserPreferences())
        }

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
