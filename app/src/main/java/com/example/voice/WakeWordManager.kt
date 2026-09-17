package com.example.voice

import java.util.Locale

class WakeWordManager {

    companion object {
        val WAKE_PHRASES = listOf("hey sigma", "ok sigma", "sigma")
    }

    /**
     * Checks if the text contains the wake word.
     * Returns a Pair(isWakeWordTriggered, extractedCommand).
     */
    fun processSpokenText(spokenText: String): Pair<Boolean, String> {
        val lower = spokenText.trim().lowercase(Locale.ROOT)

        for (wake in WAKE_PHRASES) {
            if (lower.startsWith(wake)) {
                val command = lower.removePrefix(wake).trim()
                return Pair(true, command)
            } else if (lower.contains(wake)) {
                val index = lower.indexOf(wake)
                val command = lower.substring(index + wake.length).trim()
                return Pair(true, command)
            }
        }

        // Direct command execution if user is in active conversation
        return Pair(false, spokenText.trim())
    }
}
