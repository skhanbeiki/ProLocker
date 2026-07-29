package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.ApiEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

class UpdateApi(private val client: HttpClient) {
    suspend fun checkUpdate(
        androidVer: String,
        appVer: Int,
        elaanVer: Int,
        language: String,
        market: String
    ): HttpResponse {
        return client.get(ApiEndpoints.CHECK_UPDATE) {
            parameter("android_ver", androidVer)
            parameter("app_ver", appVer)
            parameter("elaan_ver", elaanVer)
            parameter("language", language)
            parameter("market", market)
        }
    }
}
