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
}
