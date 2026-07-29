package com.carbon.prolocker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RamCleanerNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val preferencesRepository: PreferencesRepository by inject()
    private val remoteConfigRepository: RemoteConfigRepository by inject()

    override suspend fun doWork(): Result {

        val prefs = preferencesRepository.userPreferencesFlow.first()

        if (!prefs.ramCleanerNotificationEnabled) {
            return Result.success()
        }

        val configHours = try {
            remoteConfigRepository.getConfig().configs.ramCleanerNotifyTimes
        } catch (_e: Exception) {
            emptyList()
        }
        if (configHours.isEmpty()) {
            return Result.success()
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (currentHour !in configHours) {
            return Result.success()
        }

        val now = System.currentTimeMillis()

        if (now - prefs.lastRamCleanerRunTime < TWENTY_FOUR_HOURS) {
            return Result.success()
        }

        if (isSameDay(prefs.lastRamCleanerNotificationTime, now)) {
            return Result.success()
        }

        preferencesRepository.updatePreferences {
            it.copy(lastRamCleanerNotificationTime = now)
        }

        val manager = RamCleanerNotificationManager(applicationContext)
        manager.createNotificationChannel()
        manager.showNotification()

        return Result.success()
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        if (timestamp1 == 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val WORK_NAME = "RamCleanerNotificationWork"
        private const val TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000L

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<RamCleanerNotificationWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
