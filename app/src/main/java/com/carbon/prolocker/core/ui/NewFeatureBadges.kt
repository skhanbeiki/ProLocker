package com.carbon.prolocker.core.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "New" badges on recently added sections. A badge stays until the user opens that section once,
 * then it is gone for good (persisted in SharedPreferences).
 */
object NewFeatureBadges {
    const val PRIVATE_BROWSER = "private_browser"
    const val NOTIFICATION_MANAGER = "notification_manager"

    private const val PREFS = "new_feature_badges"
    private const val SEEN_PREFIX = "seen_"

    private val seen = MutableStateFlow<Set<String>?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Sections the user has already opened. */
    fun seen(context: Context): StateFlow<Set<String>?> {
        if (seen.value == null) {
            val p = prefs(context)
            seen.value = listOf(PRIVATE_BROWSER, NOTIFICATION_MANAGER)
                .filter { p.getBoolean(SEEN_PREFIX + it, false) }
                .toSet()
        }
        return seen.asStateFlow()
    }

    /** Call when the user enters the section; removes its badge permanently. */
    fun markSeen(context: Context, feature: String) {
        val current = seen(context).value.orEmpty()
        if (feature in current) return
        prefs(context).edit().putBoolean(SEEN_PREFIX + feature, true).apply()
        seen.value = current + feature
    }
}
