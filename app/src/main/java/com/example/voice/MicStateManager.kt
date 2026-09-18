package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MicState {
    MIC_IDLE,
    MIC_WAKE_LISTENING,
    MIC_COMMAND_LISTENING,
    MIC_BUSY,
    MIC_SUSPENDED,
    MIC_ERROR
}

class MicStateManager {

    private val _currentState = MutableStateFlow(MicState.MIC_IDLE)
    val currentState: StateFlow<MicState> = _currentState.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private var retryCount = 0

    fun transitionTo(newState: MicState, errorDetail: String = "") {
        if (newState == MicState.MIC_ERROR) {
            _lastError.value = errorDetail
            retryCount++
        } else if (newState == MicState.MIC_WAKE_LISTENING || newState == MicState.MIC_COMMAND_LISTENING) {
            retryCount = 0
            _lastError.value = ""
        }
        _currentState.value = newState
    }

    fun getBackoffDelayMs(): Long {
        // Exponential backoff: 1s, 2s, 4s, up to 15s max
        val delay = (1000L * (1 shl retryCount.coerceIn(0, 4)))
        return delay.coerceIn(1000L, 15000L)
    }

    val state: MicState
        get() = _currentState.value
}
