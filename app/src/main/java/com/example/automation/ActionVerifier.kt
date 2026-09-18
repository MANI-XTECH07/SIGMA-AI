package com.example.automation

import android.util.Log
import com.example.screen.ScreenAnalysisResult
import kotlinx.coroutines.delay

class ActionVerifier(private val screenObserver: ScreenObserver) {

    companion object {
        private const val TAG = "ActionVerifier"
    }

    /**
     * Verifies that the screen state changed after an action.
     */
    suspend fun verifyChange(
        beforeState: ScreenAnalysisResult,
        timeoutMs: Long = 2000L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(250L)
            val current = screenObserver.observeCurrentScreen()
            if (current.visibleTexts != beforeState.visibleTexts ||
                current.clickableElements.size != beforeState.clickableElements.size
            ) {
                Log.d(TAG, "Screen change verified successfully.")
                return true
            }
        }
        Log.w(TAG, "No verified UI change detected after action.")
        return false
    }

    /**
     * Verifies that a specific text is now visible on screen.
     */
    suspend fun verifyElementPresent(targetText: String, timeoutMs: Long = 3000L): Boolean {
        return screenObserver.waitForElement(targetText, timeoutMs = timeoutMs)
    }

    /**
     * Verifies that media playback is actively occurring on screen (Section 8).
     */
    suspend fun verifyPlayback(timeoutMs: Long = 3500L, intervalMs: Long = 400L): Boolean {
        val service = com.example.service.SigmaAccessibilityService.instance
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val verified = service?.verifyPlayback() == true
            if (verified) {
                Log.i(TAG, "SIGMA_AUTOMATION: Playback verified successfully.")
                return true
            }
            delay(intervalMs)
        }
        Log.w(TAG, "SIGMA_AUTOMATION: Playback verification timed out after ${timeoutMs}ms.")
        return false
    }

    /**
     * Verifies that search results have appeared on screen.
     */
    suspend fun verifyResultsDetected(query: String = "", timeoutMs: Long = 3000L, intervalMs: Long = 350L): Boolean {
        val service = com.example.service.SigmaAccessibilityService.instance
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (service != null) {
                val match = if (query.isNotBlank()) service.findMatchingResult(query) else null
                if (match != null) return true
                val nodes = service.dumpScreenNodes()
                val hasResults = nodes.any { it.bounds.height() in 80..1000 && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
                if (hasResults) return true
            }
            delay(intervalMs)
        }
        return false
    }
}
