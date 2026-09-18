package com.example.automation

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.device.LockController
import com.example.service.SigmaAccessibilityService

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
}

class ActionExecutor(
    private val lockController: LockController,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "ActionExecutor"
    }

    private val audioManager: AudioManager? by lazy {
        context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: com.example.SigmaApplication.instance?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // ==========================================
    // HYBRID TAP & CLICK ENGINE
    // ==========================================

    fun tapText(label: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR: tapText('$label')")
        val clicked = TargetingEngine.performHybridClick(label, service)
        return if (clicked) {
            ExecutionResult.Success("Tapped element: '$label'")
        } else {
            ExecutionResult.Failure("Target element '$label' was not found on the current screen.")
        }
    }

    fun doubleTap(label: String? = null, x: Float? = null, y: Float? = null): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        if (x != null && y != null) {
            val ok = service.doubleTapCoordinates(x, y)
            return if (ok) ExecutionResult.Success("Double tapped ($x, $y)") else ExecutionResult.Failure("Double tap failed")
        }

        if (label != null) {
            val target = TargetingEngine.findTarget(label, service)
            return when (target) {
                is TargetingEngine.TargetResult.NodeTarget -> {
                    val rect = android.graphics.Rect()
                    target.node.getBoundsInScreen(rect)
                    val ok = service.doubleTapCoordinates(rect.exactCenterX(), rect.exactCenterY())
                    if (ok) ExecutionResult.Success("Double tapped on '$label'") else ExecutionResult.Failure("Double tap failed")
                }
                is TargetingEngine.TargetResult.CoordinateTarget -> {
                    val ok = service.doubleTapCoordinates(target.x, target.y)
                    if (ok) ExecutionResult.Success("Double tapped on '$label'") else ExecutionResult.Failure("Double tap failed")
                }
                TargetingEngine.TargetResult.NotFound -> ExecutionResult.Failure("Target '$label' not found for double tap")
            }
        }

        return ExecutionResult.Failure("Missing double tap parameters")
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

    fun longPressText(text: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val target = TargetingEngine.findTarget(text, service)
        return when (target) {
            is TargetingEngine.TargetResult.NodeTarget -> {
                val rect = android.graphics.Rect()
                target.node.getBoundsInScreen(rect)
                val ok = service.longPressCoordinates(rect.exactCenterX(), rect.exactCenterY())
                if (ok) ExecutionResult.Success("Long pressed '$text'") else ExecutionResult.Failure("Long press failed")
            }
            is TargetingEngine.TargetResult.CoordinateTarget -> {
                val ok = service.longPressCoordinates(target.x, target.y)
                if (ok) ExecutionResult.Success("Long pressed '$text'") else ExecutionResult.Failure("Long press failed")
            }
            TargetingEngine.TargetResult.NotFound -> {
                val pressed = service.longPressByText(text)
                if (pressed) ExecutionResult.Success("Long pressed '$text'") else ExecutionResult.Failure("Target '$text' not found")
            }
        }
    }

    fun longPressCoordinates(x: Float, y: Float, durationMs: Long = 650L): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val pressed = service.longPressCoordinates(x, y, durationMs)
        return if (pressed) {
            ExecutionResult.Success("Long pressed at ($x, $y).")
        } else {
            ExecutionResult.Failure("Failed to long press at ($x, $y).")
        }
    }

    // ==========================================
    // TEXT INPUT ENGINE
    // ==========================================

    fun typeText(text: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR: typeText('$text')")
        val typed = service.typeTextIntoFocused(text)
        return if (typed) {
            ExecutionResult.Success("Typed text: \"$text\"")
        } else {
            ExecutionResult.Failure("No active or editable text field found to enter text into.")
        }
    }

    fun clearText(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR: clearText()")
        val cleared = service.clearFocusedOrNodeText()
        return if (cleared) {
            ExecutionResult.Success("Cleared text.")
        } else {
            ExecutionResult.Failure("Could not clear text field.")
        }
    }

    // ==========================================
    // GESTURES & SCROLLING ENGINE
    // ==========================================

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

    fun swipeDirection(direction: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val swiped = service.swipeDirection(direction)
        return if (swiped) {
            ExecutionResult.Success("Swiped $direction.")
        } else {
            ExecutionResult.Failure("Failed to swipe $direction.")
        }
    }

    fun drag(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 400L): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val dragged = service.dragCoordinates(startX, startY, endX, endY, durationMs)
        return if (dragged) {
            ExecutionResult.Success("Dragged from ($startX, $startY) to ($endX, $endY)")
        } else {
            ExecutionResult.Failure("Failed to drag.")
        }
    }

    // ==========================================
    // SYSTEM NAVIGATION & CONTROL
    // ==========================================

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

    fun openRecents(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.openRecents()
        return if (success) ExecutionResult.Success("Opened Recent Apps") else ExecutionResult.Failure("Failed to open Recent Apps")
    }

    fun closeCurrentApp(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.closeCurrentApp()
        return if (success) ExecutionResult.Success("Closed app.") else ExecutionResult.Failure("Failed to close app.")
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

    fun openNotifications(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.openNotifications()
        return if (success) ExecutionResult.Success("Opened notifications.") else ExecutionResult.Failure("Failed to open notifications.")
    }

    fun openQuickSettings(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        val success = service.openQuickSettings()
        return if (success) ExecutionResult.Success("Opened quick settings.") else ExecutionResult.Failure("Failed to open quick settings.")
    }

    // ==========================================
    // VOLUME & MEDIA CONTROL ENGINE
    // ==========================================

    fun setVolume(percent: Int): ExecutionResult {
        val am = audioManager ?: return ExecutionResult.Failure("AudioManager not available")
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = ((percent.coerceIn(0, 100) / 100f) * maxVol).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
        Log.i(TAG, "Volume set to $percent% (index: $targetVol/$maxVol)")
        return ExecutionResult.Success("Volume set to $percent%")
    }

    fun adjustVolume(up: Boolean): ExecutionResult {
        val am = audioManager ?: return ExecutionResult.Failure("AudioManager not available")
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return ExecutionResult.Success("Volume ${if (up) "raised" else "lowered"}")
    }

    fun setMute(mute: Boolean): ExecutionResult {
        val am = audioManager ?: return ExecutionResult.Failure("AudioManager not available")
        val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return ExecutionResult.Success(if (mute) "Muted" else "Unmuted")
    }

    fun sendMediaKey(keyCode: Int): ExecutionResult {
        val am = audioManager ?: return ExecutionResult.Failure("AudioManager not available")
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(eventDown)
        am.dispatchMediaKeyEvent(eventUp)
        Log.d(TAG, "Dispatched media key code: $keyCode")
        return ExecutionResult.Success("Dispatched media key: $keyCode")
    }

    fun mediaPlay(): ExecutionResult = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun mediaPause(): ExecutionResult = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun mediaNext(): ExecutionResult = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun mediaPrevious(): ExecutionResult = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    // ==========================================
    // SEARCH & MEDIA SPECIFIC AUTOMATION
    // ==========================================

    fun tapSearch(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR: tapSearch()")
        val clicked = service.findAndClickSearch()
        return if (clicked) {
            ExecutionResult.Success("Tapped search interface.")
        } else {
            ExecutionResult.Failure("Search field was not exposed by the current screen.")
        }
    }

    fun submitSearch(): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.d(TAG, "EXECUTOR: submitSearch()")
        val submitted = service.submitSearch()
        return if (submitted) {
            ExecutionResult.Success("Submitted search query.")
        } else {
            ExecutionResult.Failure("Search submit action could not be triggered.")
        }
    }

    fun selectResult(query: String): ExecutionResult {
        val service = SigmaAccessibilityService.instance
            ?: return ExecutionResult.Failure("Accessibility Service is not connected.")

        Log.i(TAG, "EXECUTOR: selectResult('$query')")
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

        Log.i(TAG, "EXECUTOR: playMedia()")
        if (service.verifyPlayback()) {
            return ExecutionResult.Success("Playback already active.")
        }

        val clicked = service.findAndClickPlayControl()
        return if (clicked) {
            ExecutionResult.Success("Triggered play control.")
        } else {
            if (service.verifyPlayback()) {
                ExecutionResult.Success("Playback verified.")
            } else {
                ExecutionResult.Failure("Play control could not be triggered.")
            }
        }
    }
}
