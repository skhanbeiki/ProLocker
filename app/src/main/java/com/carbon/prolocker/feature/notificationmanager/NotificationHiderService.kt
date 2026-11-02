package com.carbon.prolocker.feature.notificationmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.feature.notificationmanager.data.HiddenNotification
import com.carbon.prolocker.feature.notificationmanager.data.NotificationHiderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.UUID

/**
 * Takes the notifications of the apps the user picked out of the status bar and keeps them
 * inside the app, where they can only be read after the app lock. Only a content-free
 * "n hidden notifications" summary of our own is left in the status bar.
 */
class NotificationHiderService : NotificationListenerService() {

    private val repository: NotificationHiderRepository by inject()
    private val preferencesRepository: PreferencesRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.coroutineContext.cancelChildren()
        scope.launch {
            combine(repository.enabled, repository.hiddenApps) { enabled, apps -> enabled to apps }
                .distinctUntilChanged()
                .collect { (enabled, apps) ->
                    if (enabled && apps.isNotEmpty()) sweepActive(apps) else cancelSummary(this@NotificationHiderService)
                }
        }
        scope.launch {
            repository.unseenCount.collect { count ->
                if (count == 0) cancelSummary(this@NotificationHiderService)
            }
        }
    }

    override fun onListenerDisconnected() {
        scope.coroutineContext.cancelChildren()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!repository.enabled.value) return
        if (sbn.packageName == packageName) return
        if (!repository.isHidden(sbn.packageName)) return
        if (hide(sbn)) postSummary()
    }

    /** Moves the notifications already in the status bar when the feature or app list changes. */
    private fun sweepActive(apps: Set<String>) {
        val active = try {
            activeNotifications ?: return
        } catch (_: Exception) {
            return
        }
        var added = false
        for (sbn in active) {
            if (sbn.packageName == packageName || sbn.packageName !in apps) continue
            if (hide(sbn)) added = true
        }
        if (added) postSummary()
    }

    /** Stores [sbn] and removes it from the status bar. Returns true when something new was kept. */
    private fun hide(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val flags = notification.flags
        // Ongoing notifications (calls, music, foreground services) cannot be dismissed: leave them.
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false

        var added = false
        if (flags and Notification.FLAG_GROUP_SUMMARY == 0) {
            val content = extract(notification)
            if (content != null) {
                added = repository.add(
                    HiddenNotification(
                        id = UUID.randomUUID().toString(),
                        packageName = sbn.packageName,
                        title = content.first,
                        text = content.second,
                        postedAt = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis(),
                        sbnKey = sbn.key ?: ""
                    ),
                    notification.contentIntent
                )
            }
        }
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
        }
        return added
    }

    private fun extract(notification: Notification): Pair<String, String>? {
        val extras = notification.extras ?: return null
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val messaging = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }
        val last = messaging?.messages?.lastOrNull()
        if (last?.text != null) {
            text = last.text.toString()
            val sender = last.person?.name?.toString()
            val conversation = messaging?.conversationTitle?.toString()
            title = when {
                !conversation.isNullOrBlank() && !sender.isNullOrBlank() -> "$conversation: $sender"
                !conversation.isNullOrBlank() -> conversation
                !sender.isNullOrBlank() -> sender
                else -> title
            }
        }
        if (title.isBlank() && text.isBlank()) return null
        return title.trim() to text.trim()
    }

    private fun postSummary() {
        val count = repository.unseenCount.value
        if (count <= 0) return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val localized = localizedContext()
        createChannel(localized)
        val open = PendingIntent.getActivity(
            this,
            SUMMARY_ID,
            Intent(this, HiddenNotificationsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = localized.resources.getQuantityString(R.plurals.nm_summary_title, count, count)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifi)
            .setContentTitle(title)
            .setContentText(localized.getString(R.string.nm_summary_text))
            .setNumber(count)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            manager.notify(SUMMARY_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * A service does not get the in-app language the way activities do (they wrap their context
     * in attachBaseContext), so texts here came out in the system language. Resolve the app's
     * language for every notification, so a language change applies without restarting the service.
     */
    private fun localizedContext(): Context = try {
        val language = LanguageManager.resolveEffectiveLanguage(preferencesRepository, this)
        LanguageManager.createLocalizedContextStatic(this, language)
    } catch (_: Exception) {
        this
    }

    companion object {
        const val CHANNEL_ID = "hidden_notifications"
        const val SUMMARY_ID = 47_310

        fun cancelSummary(context: Context) {
            try {
                NotificationManagerCompat.from(context).cancel(SUMMARY_ID)
            } catch (_: Exception) {
            }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Creating an existing channel again only updates its name and description,
            // so the name in system settings follows the app language as well.
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.nm_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.nm_channel_desc)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }
}
