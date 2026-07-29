package com.carbon.prolocker.core.security

import com.carbon.prolocker.core.security.TrustedInternalLaunchManager.arm
import com.carbon.prolocker.core.security.TrustedInternalLaunchManager.consume
import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot, in-memory token for trusted internal launches.
 *
 * Any internal component (notification worker, deep link handler, shortcut, etc.)
 * calls [arm] before triggering a launch. The entry-point Activity calls [consume]
 * during its resume path — if the token is still valid the lock screen is skipped
 * once and the token is immediately cleared.
 *
 * Security properties:
 * - Token lives only in process memory → cannot be forged externally.
 * - Consumed on first read → one-shot.
 * - Expires after a short window → no stale bypass.
 * - Never persisted → does not survive process death.
 */
object TrustedInternalLaunchManager {

    private data class Token(
        val destination: String,
        val expiresAt: Long
    )

    private val token = AtomicReference<Token?>(null)

    private const val DEFAULT_TTL_MS = 30_000L

    /**
     * Arm a trusted launch toward [destination].
     *
     * @param destination Routing key understood by the navigation layer
     *                    (e.g. "memory", "backgrounds").
     * @param ttlMs       How long the token stays valid. Defaults to 30 s.
     */
    fun arm(destination: String, ttlMs: Long = DEFAULT_TTL_MS) {
        token.set(Token(destination, System.currentTimeMillis() + ttlMs))
    }

    /**
     * Consume the token if it is still valid.
     *
     * @return The destination string if the token was armed and not yet expired,
     *         or `null` if there is no valid token (callers should show the lock screen).
     */
    fun consume(): String? {
        val now = System.currentTimeMillis()
        val t = token.getAndSet(null) ?: return null
        return if (now <= t.expiresAt) t.destination else null
    }

    /**
     * Check whether a valid token exists without consuming it.
     */
    fun isArmed(): Boolean {
        val t = token.get() ?: return false
        return System.currentTimeMillis() <= t.expiresAt
    }

    /**
     * Explicitly clear any pending token.
     */
    fun clear() {
        token.set(null)
    }
}
