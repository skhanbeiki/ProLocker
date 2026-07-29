package com.carbon.prolocker.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FailedAttemptManager {
    data class State(
        val count: Int = 0,
        val lastAttemptTime: Long = 0L
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun recordFailedAttempt() {
        _state.update { 
            it.copy(
                count = it.count + 1,
                lastAttemptTime = System.currentTimeMillis()
            )
        }
    }

    fun reset() {
        _state.update { it.copy(count = 0) }
    }
}
