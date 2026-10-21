package com.carbon.prolocker.network.repository

import android.content.Context
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.api.ConfigApi
import com.carbon.prolocker.network.model.RemoteConfigResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RemoteConfigRepository(
    private val configApi: ConfigApi,
    private val preferencesRepository: PreferencesRepository,
    private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }

    val configFlow: Flow<RemoteConfigResponse> = preferencesRepository.userPreferencesFlow.map { prefs ->
        if (prefs.remoteConfigJson.isNotEmpty()) {
            try {
                json.decodeFromString<RemoteConfigResponse>(prefs.remoteConfigJson)
            } catch (e: Exception) {
                RemoteConfigResponse.DEFAULT
            }
        } else {
            RemoteConfigResponse.DEFAULT
        }
    }

    suspend fun syncConfig(): Result<RemoteConfigResponse> {
        val result = configApi.getRemoteConfig()
        result.onSuccess { response ->
            saveConfigLocally(response)
        }
        return result
    }

    private suspend fun saveConfigLocally(config: RemoteConfigResponse) {
        val jsonString = try {
            json.encodeToString(config)
        } catch (e: Exception) {
            ""
        }
        preferencesRepository.updatePreferences { prefs ->
            prefs.copy(
                remoteConfigJson = jsonString,
                remoteConfigInterval = config.interval,
                lastRemoteConfigSync = System.currentTimeMillis()
            )
        }
    }

    // (json string, parsed) of the last cachedConfig() call; swapped as one immutable pair.
    @Volatile
    private var cachedParse: Pair<String, RemoteConfigResponse>? = null

    /**
     * Non-suspending read of the stored config for hot paths (e.g. showing the lock screen).
     * Parsing is memoised by the stored JSON string; same fallbacks as [configFlow].
     */
    fun cachedConfig(): RemoteConfigResponse {
        val raw = preferencesRepository.currentPreferences.remoteConfigJson
        if (raw.isEmpty()) return RemoteConfigResponse.DEFAULT
        cachedParse?.let { if (it.first == raw) return it.second }
        val parsed = try {
            json.decodeFromString<RemoteConfigResponse>(raw)
        } catch (e: Exception) {
            RemoteConfigResponse.DEFAULT
        }
        cachedParse = raw to parsed
        return parsed
    }

    fun cachedLockScreenAdPlace(): String = cachedConfig().configs.nativeAdPlaceLockScreen

    suspend fun getConfig(): RemoteConfigResponse = configFlow.first()

    suspend fun getLockScreenAdPlace(): String = getConfig().configs.nativeAdPlaceLockScreen

    suspend fun getSuggestionButtonConfig(): com.carbon.prolocker.network.model.SuggestionButtonConfig? =
        getConfig().getEffectiveSuggestionButton()

    suspend fun getLastSyncTime(): Long {
        return preferencesRepository.userPreferencesFlow.map { it.lastRemoteConfigSync }.first()
    }
}
