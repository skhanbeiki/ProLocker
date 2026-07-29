package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class DeviceApi(private val client: HttpClient) {
    suspend fun registerDevice(): HttpResponse {
        // Placeholder for future use
        return client.post("${NetworkConfig.BASE_URL}/device/register") {
            // body
        }
    }
}
