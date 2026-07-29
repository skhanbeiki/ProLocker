package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.ApiEndpoints
import com.carbon.prolocker.network.model.RemoteConfigResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class ConfigApi(private val client: HttpClient) {
    suspend fun getRemoteConfig(): Result<RemoteConfigResponse> = runCatching {
        client.get(ApiEndpoints.REMOTE_CONFIG).body()
    }
}
