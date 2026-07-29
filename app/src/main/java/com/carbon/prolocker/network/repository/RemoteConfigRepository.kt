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

    suspend fun getConfig(): RemoteConfigResponse = configFlow.first()

    suspend fun getLockScreenAdPlace(): String = getConfig().configs.nativeAdPlaceLockScreen

    suspend fun getLastSyncTime(): Long {
        return preferencesRepository.userPreferencesFlow.map { it.lastRemoteConfigSync }.first()
    }
}
