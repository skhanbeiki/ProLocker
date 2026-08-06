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
import com.carbon.prolocker.core.security.TrustedInternalLaunchManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import java.util.Locale
import kotlin.random.Random

class RamCleanerNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "Moslemprolocker"
        const val CHANNEL_ID = "ram_cleaner_notification"
        const val NOTIFICATION_ID = 9002
        const val EXTRA_FROM_NOTIFICATION = "extra_from_notification"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val preferencesRepository: PreferencesRepository by KoinJavaComponent.inject(
        PreferencesRepository::class.java
    )

    /**
     * Returns a Context whose resources resolve strings in the currently selected app language.
     */
    private fun getLocalizedContext(): Context {
        val languageTag = try {
            runBlocking {
                preferencesRepository.userPreferencesFlow.first().language
            }
        } catch (e: Exception) {
            Log.e(TAG, "RamCleanerNotificationManager: failed to read language preference", e)
            ""
        }
        val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(languageTag)
        val locale = if (!appLocales.isEmpty) appLocales[0] else Locale.getDefault()
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocales(android.os.LocaleList(locale))
        val localizedContext = context.createConfigurationContext(config)

        Log.d(TAG, "NOTIFICATION_LANGUAGE RamCleanerNotificationManager: languageTag=$languageTag, locale=$locale")

        return localizedContext
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val localizedContext = getLocalizedContext()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.ram_cleaner_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedContext.getString(R.string.ram_cleaner_channel_desc)
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

        Log.d(TAG, "NOTIFICATION_LANGUAGE RamCleanerNotificationManager: appLanguage=$appLanguageTag, isPersian=$isPersian, currentLocale=${Locale.getDefault()}")

        val (title, body) = if (isPersian) getRandomPersianText() else getRandomEnglishText()

        Log.d(TAG, "NOTIFICATION_LANGUAGE RamCleanerNotificationManager: title=$title, body=$body, contextLocale=${localizedContext.resources.configuration.locales[0]}")

        TrustedInternalLaunchManager.arm("memory")

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigation_type", "memory")
            putExtra(EXTRA_FROM_NOTIFICATION, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
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
                "حافظه گوشی شما نیاز به بهینه‌سازی دارد",
                "با یک لمس حافظه اضافی را آزاد کنید و عملکرد گوشی را بهبود دهید."
            ),
            Pair(
                "گوشی کند شده؟",
                "همین حالا حافظه را آزاد کنید و سرعت گوشی را افزایش دهید."
            ),
            Pair(
                "زمان پاکسازی حافظه فرا رسیده است",
                "برنامه‌های اضافی را ببندید و فضای بیشتری آزاد کنید."
            ),
            Pair(
                "عملکرد بهتر فقط با یک کلیک",
                "حافظه دستگاه را بهینه کنید و اجرای برنامه‌ها را سریع‌تر کنید."
            ),
            Pair(
                "بهینه‌سازی حافظه را فراموش نکنید",
                "برای افزایش سرعت گوشی، حافظه را همین حالا پاکسازی کنید."
            ),
            Pair(
                "گوشی شما آماده یک نفس تازه است",
                "با پاکسازی حافظه، عملکرد دستگاه را بهبود دهید."
            )
        )
        return messages[Random.nextInt(messages.size)]
    }

    private fun getRandomEnglishText(): Pair<String, String> {
        val messages = listOf(
            Pair(
                "Your phone memory can be optimized",
                "Free memory and improve performance with one tap."
            ),
            Pair(
                "Is your phone getting slower?",
                "Clean memory now and boost device performance."
            ),
            Pair(
                "Time for a memory cleanup",
                "Close unnecessary apps and free valuable memory."
            ),
            Pair(
                "Boost performance instantly",
                "Optimize memory and enjoy a smoother experience."
            ),
            Pair(
                "Don't forget memory optimization",
                "Clean memory now to keep your phone running fast."
            ),
            Pair(
                "Give your phone a fresh start",
                "Free memory and improve responsiveness."
            )
        )
        return messages[Random.nextInt(messages.size)]
    }
}
