package com.carbon.prolocker.core.database

import android.content.Context
import android.os.Build
import com.carbon.prolocker.core.config.MarketConfig
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.api.CrashApi
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class CrashRepository(
    private val crashDao: CrashDao,
    private val preferencesRepository: PreferencesRepository,
    private val crashApi: CrashApi,
    private val context: Context
) {

    companion object {
        private const val MAX_SUMMARY_LENGTH = 2000
        private const val MAX_STACK_FRAMES = 15
    }

    private fun generateCrashSummary(throwable: Throwable): String {
        val exceptionName = throwable.javaClass.name
        val message = throwable.message ?: "No message"

        val header = "$exceptionName: $message\n"

        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString("\n") { frame ->
                "\tat ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
            }

        val summary = "$header\n$frames"

        return if (summary.length > MAX_SUMMARY_LENGTH) {
            summary.take(MAX_SUMMARY_LENGTH - "...[TRUNCATED]".length) + "...[TRUNCATED]"
        } else {
            summary
        }
    }

    private fun sanitizeField(value: String, maxLength: Int = 200): String {
        return if (value.length > maxLength) value.take(maxLength) else value
    }

    suspend fun getDeviceUuid(): String {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        var deviceUuid = prefs.deviceUuid
        if (deviceUuid.isBlank()) {
            deviceUuid = UUID.randomUUID().toString()
            preferencesRepository.updatePreferences { it.copy(deviceUuid = deviceUuid) }
        }
        return deviceUuid
    }

    suspend fun saveCrash(throwable: Throwable, issue: String = "Unhandled Exception") {
        try {
            val uuid = UUID.randomUUID().toString()
            val fullStackTrace = throwable.stackTraceToString()
            val crashSummary = generateCrashSummary(throwable)

            val appVersionCode = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toString()
                } else {
                    pInfo.versionCode.toString()
                }
            } catch (_e: Exception) {
                "Unknown"
            }
            val appVersionName = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "Unknown"
            } catch (_e: Exception) {
                "Unknown"
            }
            val language = java.util.Locale.getDefault().language

            val market = when(MarketConfig.marketType) {
                "bazaar" -> "bazaar"
                "googleplay" -> "googleplay"
                else -> "myket"
            }

            val request = CrashReportRequest(
                uuid = uuid,
                brand = sanitizeField(Build.BRAND),
                model = sanitizeField(Build.MODEL),
                market = sanitizeField(market),
                language = sanitizeField(language),
                androidVer = sanitizeField(Build.VERSION.RELEASE),
                appVerCode = sanitizeField(appVersionCode),
                appVerName = sanitizeField(appVersionName),
                event = "error",
                issue = sanitizeField(issue, 200),
                body = crashSummary
            )

            val bodyJson = Json.encodeToString(request)

            crashDao.insertCrash(
                CrashEntity(
                    uuid = uuid,
                    issue = issue,
                    body = bodyJson
                )
            )
        } catch (_e: Exception) {
            // Do not crash the app while handling a crash
        }
    }

    suspend fun getPendingCrashes(): List<CrashEntity> {
        return try {
            crashDao.getPendingCrashes()
        } catch (_e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadCrash(crash: CrashEntity): Boolean {
        return try {
            val market = when(MarketConfig.marketType) {
                "bazaar" -> "bazaar"
                "googleplay" -> "googleplay"
                else -> "myket"
            }

            val response = crashApi.uploadCrash(crash.body)
            if (response.status.value in 200..299) {
                crashDao.deleteCrash(crash.id)
                true
            } else {
                crashDao.incrementRetryCount(crash.id)
                false
            }
        } catch (_e: Exception) {
            crashDao.incrementRetryCount(crash.id)
            false
        }
    }
}
