package com.example.automation

import android.util.Log
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenAnalysisResult
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.delay

class ScreenObserver(private val screenAnalysisManager: ScreenAnalysisManager) {

    companion object {
        private const val TAG = "ScreenObserver"
    }

    /**
     * Builds and returns a fresh, lightweight ScreenSnapshot of the visible screen.
     */
    fun takeSnapshot(): ScreenSnapshot {
        val service = SigmaAccessibilityService.instance
        return if (service != null) {
            service.buildScreenSnapshot()
        } else {
            val analysis = screenAnalysisManager.analyzeCurrentScreen()
            ScreenSnapshot(
                visibleTexts = analysis.visibleTexts,
                summary = analysis.summary,
                hasDialog = analysis.hasActiveDialog
            )
        }
    }

    fun observeCurrentScreen(): ScreenAnalysisResult {
        return screenAnalysisManager.analyzeCurrentScreen()
    }

    /**
     * Waits until an element matching the text appears on screen, with configurable interval and timeout.
     */
    suspend fun waitForElement(
        targetText: String,
        timeoutMs: Long = 3500L,
        intervalMs: Long = 250L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val snapshot = takeSnapshot()
            val found = snapshot.findElement(targetText) != null ||
                    snapshot.visibleTexts.any { it.contains(targetText, ignoreCase = true) }
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
