package com.carbon.prolocker.core.security

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.carbon.prolocker.R
import com.carbon.prolocker.core.database.IntruderEventDao
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.service.AppMonitorService
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class IntruderManager(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val intruderEventDao: IntruderEventDao,
    private val cameraCaptureManager: CameraCaptureManager,
    private val eventLogManager: com.carbon.prolocker.core.security.EventLogManager
) {
    companion object {
        private const val TAG = "Moslemprolocker"
    }

    private var mediaPlayer: MediaPlayer? = null

    suspend fun handleFailedAttempt(packageName: String, lockType: String, failedAttempts: Int) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        if (failedAttempts == prefs.failedAttemptsThreshold) {
            eventLogManager.logEvent(
                "INTRUDER_DETECTED",
                packageName = packageName,
                details = "Reached max failed attempts ($failedAttempts)"
            )
        }
        if (failedAttempts >= prefs.failedAttemptsThreshold) {
            triggerIntruderActions(
                packageName,
                lockType,
                prefs.captureIntruderSelfie,
                prefs.triggerAlarm
            )
        }
    }

    private suspend fun triggerIntruderActions(
        packageName: String,
        lockType: String,
        captureSelfie: Boolean,
        triggerAlarm: Boolean
    ) {
        if (triggerAlarm) {
            if (mediaPlayer?.isPlaying != true) {
                eventLogManager.logEvent("ALARM_TRIGGERED", packageName = packageName)
            }
            playAlarm()
        }

        if (captureSelfie) {
            val photoFile = File(context.filesDir, "intruder_${UUID.randomUUID()}.jpg")
            Log.d(TAG, "INTRUDER_CAPTURE_REQUESTED pkg=$packageName lockType=$lockType file=${photoFile.name}")
            AppMonitorService.requestIntruderCapture(
                context = context,
                packageName = packageName,
                lockType = lockType,
                photoPath = photoFile.absolutePath
            )
        }
    }

    private fun playAlarm() {
        if (mediaPlayer?.isPlaying == true) return
        stopAlarm()
        try {
            mediaPlayer = MediaPlayer.create(
                context,
                R.raw.warring_alarm
            )?.apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false

                setOnCompletionListener {
                    stopAlarm()
                }

                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlarm() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
    }
}
