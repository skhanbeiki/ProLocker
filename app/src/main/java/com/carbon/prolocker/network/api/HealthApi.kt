package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class HealthApi(private val client: HttpClient) {
    suspend fun checkHealth(): HttpResponse {
        // Placeholder for future use
        return client.get("${NetworkConfig.BASE_URL}/health")
    }
}
