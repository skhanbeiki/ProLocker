package com.carbon.prolocker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.carbon.prolocker.MainActivity
import com.carbon.prolocker.R
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.security.NotificationLaunchToken
import org.koin.java.KoinJavaComponent
import java.util.Locale
import kotlin.random.Random

class JunkCleanerNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "Moslemprolocker"
        const val CHANNEL_ID = "junk_cleaner_notification"
        const val NOTIFICATION_ID = 9002
        const val EXTRA_FROM_NOTIFICATION = "extra_from_notification"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val preferencesRepository: PreferencesRepository by KoinJavaComponent.inject(
        PreferencesRepository::class.java
    )

    private fun getLocalizedContext(): Context {
        val languageTag = try {
            preferencesRepository.currentPreferences.language
        } catch (e: Exception) {
            Log.e(TAG, "JunkCleanerNotificationManager: failed to read language preference", e)
            ""
        }
        val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val localizedContext = getLocalizedContext()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.junk_cleaner_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedContext.getString(R.string.junk_cleaner_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showNotification() {
        val localizedContext = getLocalizedContext()

        val (titleRes, bodyRes) = JUNK_MESSAGES[Random.nextInt(JUNK_MESSAGES.size)]
        val title = localizedContext.getString(titleRes)
        val body = localizedContext.getString(bodyRes)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigation_type", "memory")
            putExtra(EXTRA_FROM_NOTIFICATION, true)
            putExtra(NotificationLaunchToken.EXTRA_TOKEN, NotificationLaunchToken.issue(context))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recycle)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

private val JUNK_MESSAGES = listOf(
    R.string.junk_notif_title_1 to R.string.junk_notif_body_1,
    R.string.junk_notif_title_2 to R.string.junk_notif_body_2,
    R.string.junk_notif_title_3 to R.string.junk_notif_body_3
)
