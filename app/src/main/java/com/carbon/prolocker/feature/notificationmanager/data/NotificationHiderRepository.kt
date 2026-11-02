package com.carbon.prolocker.feature.notificationmanager.data

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A notification that was taken out of the status bar and kept inside the app. */
@Serializable
data class HiddenNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val sbnKey: String = ""
)

/** One row of the main screen: an app and its kept notifications. */
data class HiddenAppSummary(
    val packageName: String,
    val count: Int,
    val lastTime: Long,
    val lastTitle: String
)

/**
 * Settings and storage of the notification hider. Everything lives in the app's private
 * storage (shared preferences + a JSON file), so it is wiped with the app and never leaves it.
 */
class NotificationHiderRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(appContext.filesDir, "notification_hider").apply { mkdirs() }
    private val file = File(dir, "notifications.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _hiddenApps = MutableStateFlow(prefs.getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    private val _notifications = MutableStateFlow(load())
    val notifications: StateFlow<List<HiddenNotification>> = _notifications.asStateFlow()

    private val _unseenCount = MutableStateFlow(prefs.getInt(KEY_UNSEEN, 0))
    val unseenCount: StateFlow<Int> = _unseenCount.asStateFlow()

    /** Tap actions of notifications captured while this process is alive (they cannot be saved). */
    private val contentIntents = ConcurrentHashMap<String, PendingIntent>()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setHiddenApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_APPS, HashSet(packages)).apply()
        _hiddenApps.value = packages.toSet()
    }

    fun hideApp(packageName: String) = setHiddenApps(_hiddenApps.value + packageName)

    fun showApp(packageName: String) = setHiddenApps(_hiddenApps.value - packageName)

    fun isHidden(packageName: String): Boolean = packageName in _hiddenApps.value

    /** Adds a notification. Returns false when it was an identical repost of one already kept. */
    fun add(notification: HiddenNotification, contentIntent: PendingIntent?): Boolean {
        synchronized(lock) {
            val current = _notifications.value
            val duplicate = current.any {
                it.packageName == notification.packageName &&
                    it.sbnKey == notification.sbnKey &&
                    it.title == notification.title &&
                    it.text == notification.text
            }
            if (duplicate) return false
            val updated = (listOf(notification) + current).take(MAX_NOTIFICATIONS)
            val keptIds = updated.mapTo(HashSet()) { it.id }
            contentIntents.keys.retainAll(keptIds)
            _notifications.value = updated
            _unseenCount.value = _unseenCount.value + 1
            prefs.edit().putInt(KEY_UNSEEN, _unseenCount.value).apply()
        }
        contentIntent?.let { contentIntents[notification.id] = it }
        persist()
        return true
    }

    fun contentIntent(id: String): PendingIntent? = contentIntents[id]

    fun delete(id: String) {
        synchronized(lock) { _notifications.value = _notifications.value.filterNot { it.id == id } }
        contentIntents.remove(id)
        persist()
    }

    fun clearApp(packageName: String) {
        synchronized(lock) {
            val (removed, kept) = _notifications.value.partition { it.packageName == packageName }
            removed.forEach { contentIntents.remove(it.id) }
            _notifications.value = kept
        }
        persist()
    }

    fun clearAll() {
        synchronized(lock) { _notifications.value = emptyList() }
        contentIntents.clear()
        persist()
    }

    fun markSeen() {
        if (_unseenCount.value == 0) return
        _unseenCount.value = 0
        prefs.edit().putInt(KEY_UNSEEN, 0).apply()
    }

    private fun load(): List<HiddenNotification> = try {
        if (file.exists()) json.decodeFromString<List<HiddenNotification>>(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist() {
        val snapshot = _notifications.value
        ioScope.launch {
            try {
                val tmp = File(dir, "notifications.json.tmp")
                tmp.writeText(json.encodeToString(snapshot))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val PREFS = "notification_hider"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_APPS = "hidden_apps"
        private const val KEY_UNSEEN = "unseen_count"
        private const val MAX_NOTIFICATIONS = 1000

        fun summarize(list: List<HiddenNotification>): List<HiddenAppSummary> =
            list.groupBy { it.packageName }
                .map { (pkg, items) ->
                    val latest = items.maxBy { it.postedAt }
                    HiddenAppSummary(pkg, items.size, latest.postedAt, latest.title.ifBlank { latest.text })
                }
                .sortedByDescending { it.lastTime }
    }
}
