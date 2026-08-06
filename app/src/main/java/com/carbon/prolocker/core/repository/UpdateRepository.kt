package com.carbon.prolocker.core.repository

import android.os.Build
import android.util.Log
import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.core.config.MarketConfig
import com.carbon.prolocker.network.api.UpdateApi
import com.carbon.prolocker.network.model.UpdateResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class UpdateRepository(private val updateApi: UpdateApi) {
    suspend fun checkUpdate(): UpdateResponse? {
        return try {
            val language = java.util.Locale.getDefault().language
            val market = when(MarketConfig.marketType) {
                "bazaar" -> "bazaar"
                "googleplay" -> "googleplay"
                else -> "myket"
            }
            
            val response = updateApi.checkUpdate(
                androidVer = Build.VERSION.RELEASE,
                appVer = BuildConfig.VERSION_CODE,
                elaanVer = 3,
                language = language,
                market = market
            )

            if (response.status.value in 200..299) {
                val body = response.bodyAsText().trim()
                if (body.isEmpty() || body == "null") {
                    null
                } else {
                    try {
                        val json = Json { ignoreUnknownKeys = true }
                        json.decodeFromString<UpdateResponse>(body)
                    } catch (e: Exception) {
                        Log.d("UpdateRepository", "No valid update JSON payload: $body")
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("UpdateRepository", "Check update failed: ${e.message}")
            null
        }
    }
}
