package com.carbon.prolocker.core.security

import java.util.concurrent.atomic.AtomicLong

object TrustedReturnManager {

    private val expirationTimestamp = AtomicLong(0L)

    fun startTrustedReturn(durationMillis: Long = 30_000L) {
        expirationTimestamp.set(System.currentTimeMillis() + durationMillis)
    }

    fun consumeTrustedReturn(): Boolean {
        val now = System.currentTimeMillis()
        val expiration = expirationTimestamp.get()
        if (now <= expiration && expiration > 0) {
            expirationTimestamp.set(0L)
            return true
        }
        expirationTimestamp.set(0L)
        return false
    }

    fun clear() {
        expirationTimestamp.set(0L)
    }
}
