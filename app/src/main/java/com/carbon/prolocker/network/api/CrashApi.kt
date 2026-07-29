package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.ApiEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class CrashApi(private val client: HttpClient) {
    suspend fun uploadCrash(requestBody: String): HttpResponse {
        return client.post(ApiEndpoints.CRASH_REPORT) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }
}
