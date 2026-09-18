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
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val clicked = service.clickByText(label)
        return if (clicked) {
            ExecutionResult.Success("Tapped element: $label")
        } else {
            ExecutionResult.Failure("Could not find or tap element: $label")
        }
    }

    fun tapCoordinates(x: Float, y: Float): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val tapped = service.tapCoordinates(x, y)
        return if (tapped) {
            ExecutionResult.Success("Tapped coordinates ($x, $y)")
        } else {
            ExecutionResult.Failure("Failed to dispatch tap gesture at ($x, $y)")
        }
    }

    fun typeText(text: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val typed = service.typeTextIntoFocused(text)
        return if (typed) {
            ExecutionResult.Success("Typed text: $text")
        } else {
            ExecutionResult.Failure("No editable text field found to type into.")
        }
    }

    fun scroll(forward: Boolean): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val scrolled = if (forward) service.scrollForward() else service.scrollBackward()
        return if (scrolled) {
            ExecutionResult.Success("Scrolled screen ${if (forward) "down" else "up"}.")
        } else {
            ExecutionResult.Failure("Failed to scroll screen.")
        }
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val swiped = service.performSwipeGesture(startX, startY, endX, endY, durationMs)
        return if (swiped) {
            ExecutionResult.Success("Dispatched swipe gesture.")
        } else {
            ExecutionResult.Failure("Failed to dispatch swipe gesture.")
        }
    }

    fun goHome(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

        val success = service.goHome()
        return if (success) ExecutionResult.Success("Navigated Home") else ExecutionResult.Failure("Failed to navigate Home")
    }

    fun goBack(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service inactive.")

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
            ExecutionResult.Failure("Failed to lock device. Ensure Accessibility Service is enabled and Android 9+.")
        }
    }
}

