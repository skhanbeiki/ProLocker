package com.carbon.prolocker.feature.lock

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface LockOverlayEvent {
    val packageName: String

    /** The user passed the lock (PIN, pattern, biometric or recovery). */
    data class Unlocked(override val packageName: String) : LockOverlayEvent

    /** The overlay was closed without unlocking (back, home, switched away, ad click...). */
    data class Cancelled(override val packageName: String) : LockOverlayEvent
}

object LockOverlayEvents {
    private val _events = MutableSharedFlow<LockOverlayEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<LockOverlayEvent> = _events.asSharedFlow()

    fun emit(event: LockOverlayEvent) {
        _events.tryEmit(event)
    }
}
