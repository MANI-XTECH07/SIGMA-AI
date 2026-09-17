package com.example.automation

import android.util.Log

class RecoveryEngine(
    private val actionExecutor: ActionExecutor,
    private val screenObserver: ScreenObserver
) {

    companion object {
        private const val TAG = "RecoveryEngine"
        private const val MAX_RETRIES = 2
    }

    suspend fun attemptRecovery(targetAction: suspend () -> Boolean, onFallback: suspend () -> Unit): Boolean {
        for (attempt in 1..MAX_RETRIES) {
            Log.d(TAG, "Recovery attempt #$attempt...")
            val success = targetAction()
            if (success) {
                Log.d(TAG, "Recovery succeeded on attempt #$attempt")
                return true
            }
            // Fallback: try navigating back once or small scroll
            actionExecutor.scroll(forward = true)
        }

        Log.w(TAG, "Recovery failed after $MAX_RETRIES attempts. Executing fallback.")
        onFallback()
        return false
    }
}
