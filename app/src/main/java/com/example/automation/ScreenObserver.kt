package com.example.automation

import android.util.Log
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenAnalysisResult
import kotlinx.coroutines.delay

class ScreenObserver(private val screenAnalysisManager: ScreenAnalysisManager) {

    companion object {
        private const val TAG = "ScreenObserver"
    }

    fun observeCurrentScreen(): ScreenAnalysisResult {
        return screenAnalysisManager.analyzeCurrentScreen()
    }

    /**
     * Waits until an element matching the text or query appears on screen, with a timeout.
     */
    suspend fun waitForElement(
        targetText: String,
        timeoutMs: Long = 4000L,
        intervalMs: Long = 300L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val analysis = observeCurrentScreen()
            val found = analysis.visibleTexts.any { it.contains(targetText, ignoreCase = true) } ||
                    analysis.clickableElements.any {
                        it.text.contains(targetText, ignoreCase = true) ||
                                it.contentDescription.contains(targetText, ignoreCase = true)
                    }
            if (found) {
                Log.d(TAG, "Observed target element '$targetText' on screen.")
                return true
            }
            delay(intervalMs)
        }
        Log.w(TAG, "Timed out waiting for element '$targetText'.")
        return false
    }
}
