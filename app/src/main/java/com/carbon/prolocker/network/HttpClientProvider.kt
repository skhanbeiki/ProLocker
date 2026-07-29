package com.carbon.prolocker.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientProvider {
    fun provide(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("KtorClient", message)
                    }
                }
                level = LogLevel.ALL
                sanitizeHeader { header -> header == "Authorization" }
            }
            defaultRequest {
                url.takeFrom(NetworkConfig.BASE_URL)
                header("Accept", "application/json")
                header("Content-Type", "application/json")
                header("Authorization", "Token 87eb5523-97a2-4c2f-8c19-01005e2ae414")
                val market = when {
                    com.carbon.prolocker.core.config.MarketConfig.isBazaar -> "bazaar"
                    com.carbon.prolocker.core.config.MarketConfig.isMyket -> "myket"
                    else -> "googleplay"
                }
                header("Market", market)
            }
        }
    }
}
