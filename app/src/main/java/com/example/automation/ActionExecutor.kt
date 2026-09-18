package com.example.automation

import android.os.Build
import android.util.Log
import com.example.device.LockController
import com.example.service.SigmaAccessibilityService

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val reason: String) : ExecutionResult()
}

class ActionExecutor(
    private val lockController: LockController
) {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    fun tapText(label: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR_STARTED: tapText('$label')")
        val clicked = service.clickByText(label)
        return if (clicked) {
            ExecutionResult.Success("Tapped element: '$label'")
        } else {
            ExecutionResult.Failure("Target element '$label' was not found on the current screen.")
        }
    }

    fun tapSearch(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR_STARTED: tapSearch()")
        val clicked = service.findAndClickSearch()
        return if (clicked) {
            ExecutionResult.Success("Tapped search interface.")
        } else {
            ExecutionResult.Failure("Search field was not exposed by the current screen.")
        }
    }

    fun typeText(text: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR_STARTED: typeText('$text')")
        val typed = service.typeTextIntoFocused(text)
        return if (typed) {
            ExecutionResult.Success("Typed text: \"$text\"")
        } else {
            ExecutionResult.Failure("No active or editable text field found to enter text into.")
        }
    }

    fun submitSearch(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR_STARTED: submitSearch()")
        val submitted = service.submitSearch()
        return if (submitted) {
            ExecutionResult.Success("Submitted search query.")
        } else {
            ExecutionResult.Failure("Search submit action could not be triggered.")
        }
    }

    fun tapResult(query: String): ExecutionResult = selectResult(query)

    fun selectResult(query: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.i(TAG, "SIGMA_AUTOMATION: ActionExecutor.selectResult('$query')")
        val clicked = service.findAndClickResult(query)
        return if (clicked) {
            ExecutionResult.Success("Selected result for '$query'")
        } else {
            ExecutionResult.Failure("Matching result for '$query' could not be found.")
        }
    }

    fun playMedia(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.i(TAG, "SIGMA_AUTOMATION: ActionExecutor.playMedia()")
        // Check if playback has already initiated from result click
        if (service.verifyPlayback()) {
            return ExecutionResult.Success("Playback already active.")
        }

        // Otherwise attempt to locate and trigger explicit play control
        val clicked = service.findAndClickPlayControl()
        return if (clicked) {
            ExecutionResult.Success("Triggered play control.")
        } else {
            // In many media apps (YouTube), clicking the card automatically plays it.
            // If play control wasn't needed because player is already loading, treat as success if player or verifyPlayback succeeds
            if (service.verifyPlayback()) {
                ExecutionResult.Success("Playback verified.")
            } else {
                ExecutionResult.Failure("Play control could not be triggered.")
            }
        }
    }

    fun verifyPlayback(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.i(TAG, "SIGMA_AUTOMATION: ActionExecutor.verifyPlayback()")
        val verified = service.verifyPlayback()
        return if (verified) {
            ExecutionResult.Success("Playback verified.")
        } else {
            ExecutionResult.Failure("Playback could not be verified.")
        }
    }

    fun observeResults(query: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val matching = service.findMatchingResult(query)
        return if (matching != null) {
            ExecutionResult.Success("Results observed for query '$query'")
        } else {
            // Check if any results/cards are present
            val nodes = service.dumpScreenNodes()
            val hasResults = nodes.any { it.bounds.height() in 80..1000 && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
            if (hasResults) {
                ExecutionResult.Success("Results present on screen.")
            } else {
                ExecutionResult.Failure("No search results observed on screen.")
            }
        }
    }

    fun tapCoordinates(x: Float, y: Float): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val tapped = service.tapCoordinates(x, y)
        return if (tapped) {
            ExecutionResult.Success("Tapped coordinates ($x, $y)")
        } else {
            ExecutionResult.Failure("Failed to dispatch tap gesture at ($x, $y)")
        }
    }

    fun scroll(forward: Boolean): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val scrolled = if (forward) service.scrollForward() else service.scrollBackward()
        return if (scrolled) {
            ExecutionResult.Success("Scrolled screen ${if (forward) "down" else "up"}.")
        } else {
            ExecutionResult.Failure("Failed to scroll screen.")
        }
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val swiped = service.performSwipeGesture(startX, startY, endX, endY, durationMs)
        return if (swiped) {
            ExecutionResult.Success("Dispatched swipe gesture.")
        } else {
            ExecutionResult.Failure("Failed to dispatch swipe gesture.")
        }
    }

    fun goHome(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.goHome()
        return if (success) ExecutionResult.Success("Navigated Home") else ExecutionResult.Failure("Failed to navigate Home")
    }

    fun goBack(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.goBack()
        return if (success) ExecutionResult.Success("Navigated Back") else ExecutionResult.Failure("Failed to navigate Back")
    }

    fun lockDevice(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
        if (service != null && service.lockDevice()) {
            return ExecutionResult.Success("Device locked successfully via Accessibility.")
        }

        val locked = lockController.lockPhone()
        return if (locked) {
            ExecutionResult.Success("Device locked successfully.")
        } else {
            ExecutionResult.Failure("Failed to lock device. Ensure Accessibility Service is enabled in Settings.")
        }
    }
}
