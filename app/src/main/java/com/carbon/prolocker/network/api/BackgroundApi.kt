package com.carbon.prolocker.network.api

import com.carbon.prolocker.network.ApiEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class BackgroundApi(private val client: HttpClient) {
    suspend fun getBackgrounds(cursor: String, pageSize: Int): HttpResponse {
        return client.get(ApiEndpoints.BACKGROUNDS) {
            if (cursor.isNotEmpty()) {
                parameter("cursor", cursor)
            }
            parameter("page_size", pageSize)
        }
    }

    suspend fun reportDownload(id: Int, packageName: String): HttpResponse {
        return client.post("${ApiEndpoints.BACKGROUND_DOWNLOAD}$id/$packageName/")
    }

    suspend fun checkNew(lastId: Int): HttpResponse {
        return client.get("${ApiEndpoints.BACKGROUND_CHECK}$lastId/")
    }
}
