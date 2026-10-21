package com.carbon.prolocker.core.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Secret, per-notification token that lets a tap on one of our own notifications skip the entry lock.
 *
 * MainActivity is exported, so a plain boolean extra can be forged by any app. The token is generated
 * when the notification is posted, travels inside the (immutable) PendingIntent and is only valid once.
 */
object NotificationLaunchToken {

    const val EXTRA_TOKEN = "extra_notification_launch_token"

    private const val PREFS_NAME = "notification_launch_token"
    private const val KEY_TOKEN = "token"
    private const val KEY_ISSUED_AT = "issued_at"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val TOKEN_BYTES = 16

    private val secureRandom = SecureRandom()

    @Synchronized
    fun issue(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { secureRandom.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_ISSUED_AT, System.currentTimeMillis())
            .commit()
        return token
    }

    @Synchronized
    fun consume(context: Context, token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_TOKEN, null) ?: return false
        val matches = MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )
        if (!matches) return false

        val issuedAt = prefs.getLong(KEY_ISSUED_AT, 0L)
        prefs.edit().clear().apply()

        val age = System.currentTimeMillis() - issuedAt
        return age in 0 until MAX_AGE_MS
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
