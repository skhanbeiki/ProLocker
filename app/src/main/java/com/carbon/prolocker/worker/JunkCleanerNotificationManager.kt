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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
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

        val appLanguageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Locale.getDefault().language
        }
        val isPersian = appLanguageTag == "fa"

        val (title, body) = if (isPersian) getRandomPersianText() else getRandomEnglishText()

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

    private fun getRandomPersianText(): Pair<String, String> {
        val messages = listOf(
            Pair(
                "زمان پاک‌سازی فایل‌های زائد است",
                "با پاک‌سازی فایل‌های موقت و کش، فضای ذخیره‌سازی گوشی را آزاد کنید."
            ),
            Pair(
                "حافظه گوشی پر شده؟",
                "فایل‌های زائد و فایل‌های نصبی قدیمی را حذف کنید تا فضای بیشتری داشته باشید."
            ),
            Pair(
                "فضای ذخیره‌سازی دستگاه را آزاد کنید",
                "فایل‌های موقت را پاک کنید تا فضای بیشتری برای عکس‌ها و برنامه‌هایتان داشته باشید."
            )
        )
        return messages[Random.nextInt(messages.size)]
    }

    private fun getRandomEnglishText(): Pair<String, String> {
        val messages = listOf(
            Pair(
                "Time to clean junk files",
                "Clean temporary and cache files to free up valuable storage space."
            ),
            Pair(
                "Running out of storage?",
                "Remove obsolete APKs and junk files to make room for what matters."
            ),
            Pair(
                "Free up storage space",
                "Clean junk and residual files with one quick tap."
            )
        )
        return messages[Random.nextInt(messages.size)]
    }
}
