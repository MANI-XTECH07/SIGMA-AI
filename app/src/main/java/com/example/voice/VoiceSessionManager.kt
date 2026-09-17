package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantSessionState {
    ONLINE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    SUCCESS,
    ERROR,
    SLEEP
}

class VoiceSessionManager {

    private val _sessionState = MutableStateFlow(AssistantSessionState.ONLINE)
    val sessionState: StateFlow<AssistantSessionState> = _sessionState.asStateFlow()

    private val _liveRms = MutableStateFlow(0f)
    val liveRms: StateFlow<Float> = _liveRms.asStateFlow()

    fun updateState(newState: AssistantSessionState) {
        _sessionState.value = newState
    }

    fun updateRms(rms: Float) {
        _liveRms.value = rms
    }
}
