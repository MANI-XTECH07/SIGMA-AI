package com.example.ai

import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.util.Log
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenCaptureManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class VisionService(
    private val screenCaptureManager: ScreenCaptureManager,
    private val screenAnalysisManager: ScreenAnalysisManager,
    private val aiProvider: AiProvider
) {

    companion object {
        private const val TAG = "VisionService"
    }

    suspend fun captureAndExplain(
        metrics: DisplayMetrics,
        userQuestion: String = "Explain what is visible on this screen clearly and concisely."
    ): String {
        // 1. First inspect live accessibility UI hierarchy
        val uiHierarchy = screenAnalysisManager.analyzeCurrentScreen()

        // 2. Try real bitmap capture if MediaProjection is active
        val bitmap: Bitmap? = suspendCancellableCoroutine { continuation ->
            screenCaptureManager.captureScreen(metrics) { captured ->
                continuation.resume(captured)
            }
        }

        if (bitmap != null) {
            val result = aiProvider.analyzeImage(bitmap, userQuestion)
            if (result.isSuccess) {
                return result.getOrNull() ?: "Analyzed screen successfully."
            }
        }

        // Fallback to real UI hierarchy text analysis if screenshot capture isn't active
        if (uiHierarchy.visibleTexts.isNotEmpty()) {
            val prompt = "User asks: $userQuestion\nLive screen elements: ${uiHierarchy.visibleTexts.joinToString(", ")}"
            val textAnalysis = aiProvider.generateText(prompt, "Summarize what is on the Android screen based on the extracted visible text.")
            return textAnalysis.getOrDefault(uiHierarchy.summary)
        }

        return "I could not capture the screen. Please ensure Screen Capture or Accessibility permission is enabled."
    }
}
