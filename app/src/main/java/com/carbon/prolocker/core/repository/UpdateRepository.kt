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
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<UpdateResponse>(response.bodyAsText())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UpdateRepository", "Failed to check update", e)
            null
        }
    }
}
