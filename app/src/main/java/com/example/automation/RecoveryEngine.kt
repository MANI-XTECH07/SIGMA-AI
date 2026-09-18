package com.example.automation

import android.util.Log
import kotlinx.coroutines.delay

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
            Log.d(TAG, "SIGMA_AUTOMATION: Recovery attempt #$attempt...")
            delay(500L)
            onFallback()
            delay(400L)
            val success = targetAction()
            if (success) {
                Log.d(TAG, "SIGMA_AUTOMATION: Recovery succeeded on attempt #$attempt")
                return true
            }
            delay(400L)
        }

        Log.w(TAG, "SIGMA_AUTOMATION: Recovery failed after $MAX_RETRIES attempts.")
        return false
    }

    /**
     * Specialized recovery for Result Selection and Play execution (Section 9):
     * 1. re-observe screen
     * 2. locate the result container again
     * 3. locate its play control
     * 4. try parent clickable node
     * 5. try opening the result first
     * 6. observe player screen
     * 7. click Play
     * 8. verify playback
     */
    suspend fun recoverResultSelectionAndPlay(query: String): Boolean {
        val service = com.example.service.SigmaAccessibilityService.instance ?: return false
        Log.i(TAG, "SIGMA_AUTOMATION: Starting Section 9 recovery for query='$query'...")

        for (attempt in 1..MAX_RETRIES) {
            Log.d(TAG, "SIGMA_AUTOMATION: Result/Play Recovery attempt #$attempt")
            delay(500L)

            // 1. Re-observe screen
            val snapshot = service.observeScreen()
            Log.d(TAG, "SIGMA_AUTOMATION: Recovery observed ${snapshot.size} nodes.")

            // 2. Locate the result container again
            val resultNode = service.findMatchingResult(query)
            if (resultNode != null) {
                // 3. Locate its play control
                val childPlay = service.findNodeByContentDescription("play") ?: service.findNodeByText("play")
                if (childPlay != null && service.clickNodeOrClickableParent(childPlay)) {
                    delay(800L)
                    if (service.verifyPlayback()) {
                        Log.i(TAG, "SIGMA_AUTOMATION: Recovery succeeded via child play control click.")
                        return true
                    }
                }

                // 4. Try parent clickable node / opening the result first
                val clicked = service.clickNodeOrClickableParent(resultNode)
                if (clicked) {
                    delay(1200L) // Wait for player view transition

                    // 6. Observe player screen & click play if needed
                    service.findAndClickPlayControl()
                    delay(600L)

                    // 8. Verify playback
                    if (service.verifyPlayback()) {
                        Log.i(TAG, "SIGMA_AUTOMATION: Recovery succeeded: Player active and playback verified.")
                        return true
                    }
                }
            } else {
                // If container wasn't visible, try scrolling forward once and re-locating
                Log.d(TAG, "SIGMA_AUTOMATION: Recovery scrolling forward...")
                service.performScroll(forward = true)
                delay(800L)
            }
        }

        Log.w(TAG, "SIGMA_AUTOMATION: Result/Play Recovery failed after $MAX_RETRIES attempts.")
        return false
    }
}
