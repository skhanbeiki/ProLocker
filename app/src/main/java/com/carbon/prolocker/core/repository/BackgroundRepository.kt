package com.carbon.prolocker.core.repository

import android.util.Log
import com.carbon.prolocker.network.api.BackgroundApi
import com.carbon.prolocker.network.model.BackgroundResponse
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class GalleryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoNetwork(cause: Throwable? = null) : GalleryException("No network connection", cause)
    class Timeout(cause: Throwable? = null) : GalleryException("Request timed out", cause)
    class ServerError(val statusCode: Int, message: String? = null) : GalleryException(message ?: "Server error $statusCode")
    class ClientError(val statusCode: Int, message: String? = null) : GalleryException(message ?: "Client error $statusCode")
    class Unexpected(cause: Throwable? = null) : GalleryException("Unexpected error", cause)
}

class BackgroundRepository(private val backgroundApi: BackgroundApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getBackgrounds(cursor: String): BackgroundResponse {
        try {
            val response = backgroundApi.getBackgrounds(cursor, 20)
            if (response.status.value in 200..299) {
                val parsed = json.decodeFromString<BackgroundResponse>(response.bodyAsText())
                Log.d("BackgroundGallery", "Repository count: ${parsed.results.size}")
                return parsed
            }
            val errorBody = try { response.bodyAsText() } catch (_: Exception) { null }
            Log.e("BackgroundRepository", "HTTP ${response.status.value}: $errorBody")
            when (response.status.value) {
                in 400..499 -> throw GalleryException.ClientError(response.status.value, errorBody)
                in 500..599 -> throw GalleryException.ServerError(response.status.value, errorBody)
                else -> throw GalleryException.Unexpected()
            }
        } catch (e: GalleryException) {
            throw e
        } catch (e: UnknownHostException) {
            Log.e("BackgroundRepository", "No network", e)
            throw GalleryException.NoNetwork(e)
        } catch (e: SocketTimeoutException) {
            Log.e("BackgroundRepository", "Timeout", e)
            throw GalleryException.Timeout(e)
        } catch (e: IOException) {
            Log.e("BackgroundRepository", "IO error", e)
            throw GalleryException.NoNetwork(e)
        } catch (e: ResponseException) {
            Log.e("BackgroundRepository", "Response error", e)
            val code = e.response.status.value
            when (code) {
                in 400..499 -> throw GalleryException.ClientError(code)
                in 500..599 -> throw GalleryException.ServerError(code)
                else -> throw GalleryException.Unexpected(e)
            }
        } catch (e: Exception) {
            Log.e("BackgroundRepository", "Unexpected error", e)
            throw GalleryException.Unexpected(e)
        }
    }

    suspend fun reportDownload(id: Int, packageName: String): Boolean {
        return try {
            val response = backgroundApi.reportDownload(id, packageName)
            response.status.value in 200..299
        } catch (_e: Exception) {
            false
        }
    }

    suspend fun checkNew(lastId: Int): Int {
        return try {
            val response = backgroundApi.checkNew(lastId)
            if (response.status.value in 200..299) {
                response.bodyAsText().toIntOrNull() ?: 0
            } else {
                0
            }
        } catch (_e: Exception) {
            0
        }
    }
}
