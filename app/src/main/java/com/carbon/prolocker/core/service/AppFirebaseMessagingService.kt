package com.carbon.prolocker.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.os.LocaleListCompat
import com.carbon.prolocker.MainActivity
import com.carbon.prolocker.R
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.util.Locale

class AppFirebaseMessagingService : FirebaseMessagingService() {

    private companion object {
        const val TAG = "Moslemprolocker"
    }

    private val preferencesRepository: PreferencesRepository by inject(
        PreferencesRepository::class.java
    )

    private fun getLocalizedContext(): Context {
        val languageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "AppFirebaseMessagingService: failed to read language preference", e)
            ""
        }
        val appLocales = LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()
        val config = Configuration(resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localizedContext = createConfigurationContext(config)

        Log.d(
            TAG,
            "NOTIFICATION_LANGUAGE AppFirebaseMessagingService: languageTag=$languageTag, locale=$locale"
        )

        return localizedContext
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "FCM Token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FCM", "Message received from: ${message.from}")

        val localizedContext = getLocalizedContext()
        val title = message.notification?.title ?: message.data["title"]
        ?: localizedContext.getString(R.string.home_title)
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "home"

        Log.d("FCM", "Type: $type")

        val analyticsManager = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.carbon.prolocker.core.analytics.AnalyticsManager>()
        analyticsManager.trackNotificationReceived()

        sendNotification(title, body, type)
    }

    private fun sendNotification(title: String, body: String, type: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigation_type", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val localizedContext = getLocalizedContext()
        val channelId = "general"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                localizedContext.getString(R.string.general_notifications_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val appLanguageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (_: Exception) {
            Locale.getDefault().language
        }

        Log.d(
            TAG,
            "NOTIFICATION_LANGUAGE AppFirebaseMessagingService: appLanguage=$appLanguageTag, currentLocale=${Locale.getDefault()}, notificationTitle=$title, notificationBody=$body, contextLocale=${localizedContext.resources.configuration.locales[0]}"
        )

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
