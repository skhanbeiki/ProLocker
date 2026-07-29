package com.carbon.prolocker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class RemoteConfigWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val remoteConfigRepository: RemoteConfigRepository by inject()

    override suspend fun doWork(): Result {
        Log.d(TAG, "Syncing remote config...")
        val oldInterval = remoteConfigRepository.configFlow.first().interval
        
        val result = remoteConfigRepository.syncConfig()
        
        return result.fold(
            onSuccess = { response ->
                Log.d(TAG, "Config synced successfully. Interval: ${response.interval}m")
                // Check if interval changed
                if (response.interval != oldInterval) {
                    Log.d(TAG, "Interval changed. Rescheduling worker.")
                    schedule(applicationContext, response.interval)
                }
                Result.success()
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to sync config", error)
                Result.retry()
            }
        )
    }

    companion object {
        private const val TAG = "RemoteConfigWorker"
        const val WORK_NAME = "RemoteConfigSyncWork"

        fun schedule(context: Context, intervalMinutes: Long) {
            val safeInterval = maxOf(15L, intervalMinutes) // WorkManager min is 15 minutes
            val workRequest = PeriodicWorkRequestBuilder<RemoteConfigWorker>(
                safeInterval, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Replace if already exists to apply new interval
                workRequest
            )
            Log.d(TAG, "Scheduled RemoteConfigWorker with interval: \${safeInterval}m")
        }
    }
}
