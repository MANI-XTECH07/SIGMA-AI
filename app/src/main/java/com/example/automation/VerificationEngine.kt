package com.example.automation

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.service.SigmaAccessibilityService
import kotlinx.coroutines.delay

enum class ActionResultStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    NOT_SUPPORTED,
    CANCELLED
}

data class ActionResult(
    val status: ActionResultStatus,
    val message: String,
    val latencyMs: Long = 0L,
    val target: String = "",
    val details: String = ""
) {
    val isSuccess: Boolean
        get() = status == ActionResultStatus.SUCCESS
}

class VerificationEngine(
    private val context: Context,
    private val screenObserver: ScreenObserver
) {
    companion object {
        private const val TAG = "VerificationEngine"
    }

    /**
     * Conditionally waits until a boolean condition is satisfied or timeout expires.
     */
    suspend fun waitForCondition(
        timeoutMs: Long = 3000L,
        intervalMs: Long = 200L,
        conditionName: String = "Condition",
        check: suspend () -> Boolean
    ): ActionResult {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (check()) {
                val latency = System.currentTimeMillis() - startTime
                Log.d(TAG, "VERIFIED: $conditionName in ${latency}ms")
                return ActionResult(ActionResultStatus.SUCCESS, "$conditionName verified", latency)
            }
            delay(intervalMs)
        }
        val latency = System.currentTimeMillis() - startTime
        Log.w(TAG, "TIMEOUT: $conditionName after ${latency}ms")
        return ActionResult(ActionResultStatus.TIMEOUT, "$conditionName timed out", latency)
    }

    /**
     * Verifies that the foreground package transitioned to expectedPkg.
     */
    suspend fun verifyPackageTransition(expectedPkg: String, timeoutMs: Long = 4000L): ActionResult {
        val startTime = System.currentTimeMillis()
        val result = waitForCondition(timeoutMs, 200L, "Package transition to $expectedPkg") {
            val current = SigmaAccessibilityService.currentPackage
            current.contains(expectedPkg, ignoreCase = true)
        }
        return result
    }

    /**
     * Verifies that UI elements changed from the snapshot taken before the action.
     */
    suspend fun verifyUiChange(beforeSnapshot: ScreenSnapshot, timeoutMs: Long = 2500L): ActionResult {
        val startTime = System.currentTimeMillis()
        val result = waitForCondition(timeoutMs, 250L, "UI change") {
            val service = SigmaAccessibilityService.instance ?: return@waitForCondition false
            val current = service.buildScreenSnapshot()
            current.visibleTexts != beforeSnapshot.visibleTexts ||
                    current.clickableElements.size != beforeSnapshot.clickableElements.size ||
                    current.packageName != beforeSnapshot.packageName
        }
        return result
    }

    /**
     * Verifies target element appears on screen.
     */
    suspend fun verifyElementPresent(targetText: String, timeoutMs: Long = 3500L): ActionResult {
        val startTime = System.currentTimeMillis()
        val result = waitForCondition(timeoutMs, 250L, "Element present '$targetText'") {
            val service = SigmaAccessibilityService.instance ?: return@waitForCondition false
            val snapshot = service.buildScreenSnapshot()
            snapshot.findElement(targetText) != null ||
                    snapshot.visibleTexts.any { it.contains(targetText, ignoreCase = true) }
        }
        return result
    }

    /**
     * Verifies that active media playback is occurring.
     */
    suspend fun verifyPlayback(timeoutMs: Long = 3500L): ActionResult {
        val startTime = System.currentTimeMillis()
        val result = waitForCondition(timeoutMs, 300L, "Media playback") {
            val service = SigmaAccessibilityService.instance
            service?.verifyPlayback() == true
        }
        return result
    }

    /**
     * Verifies search results detected on screen.
     */
    suspend fun verifyResultsDetected(query: String = "", timeoutMs: Long = 3000L): ActionResult {
        val startTime = System.currentTimeMillis()
        val result = waitForCondition(timeoutMs, 300L, "Search results for '$query'") {
            val service = SigmaAccessibilityService.instance ?: return@waitForCondition false
            if (query.isNotBlank() && service.findMatchingResult(query) != null) return@waitForCondition true
            val nodes = service.dumpScreenNodes()
            nodes.any { it.bounds.height() in 80..1000 && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
        }
        return result
    }

    /**
     * Verifies device volume adjusted to expected level.
     */
    fun verifyVolume(expectedPercent: Int): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(ActionResultStatus.NOT_SUPPORTED, "AudioManager not available")
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = if (maxVol > 0) ((currentVol.toFloat() / maxVol) * 100).toInt() else 0

        // Allow ±10% tolerance due to discrete step rounding
        val matches = Math.abs(currentPercent - expectedPercent) <= 12
        return if (matches) {
            ActionResult(ActionResultStatus.SUCCESS, "Volume verified at $currentPercent%")
        } else {
            ActionResult(ActionResultStatus.FAILED, "Volume is at $currentPercent%, expected ~$expectedPercent%")
        }
    }
}
