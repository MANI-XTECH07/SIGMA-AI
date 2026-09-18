package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

class SigmaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SigmaAccessibility"
        @Volatile
        var instance: SigmaAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        var currentPackage: String = ""
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "SigmaAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                it.packageName?.let { pkg ->
                    currentPackage = pkg.toString()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SigmaAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "SigmaAccessibilityService destroyed")
    }

    fun extractScreenText(): List<String> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val textList = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            // Safety rule: Skip sensitive / password fields
            if (node.isPassword) {
                return
            }

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrEmpty() && !textList.contains(text)) {
                textList.add(text)
            } else if (!desc.isNullOrEmpty() && !textList.contains(desc)) {
                textList.add(desc)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        return textList
    }

    fun clickByText(targetText: String, ignoreCase: Boolean = true): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val matchedNodes = rootNode.findAccessibilityNodeInfosByText(targetText)

        for (node in matchedNodes) {
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }

            // Check parent hierarchy
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
                parent = parent.parent
            }
        }

        // Try fuzzy traversal if direct search failed
        return fuzzyFindAndClick(rootNode, targetText)
    }

    private fun fuzzyFindAndClick(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val targetLower = target.lowercase()

        if ((text.contains(targetLower) || desc.contains(targetLower)) && !node.isPassword) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            if (fuzzyFindAndClick(node.getChild(i), target)) {
                return true
            }
        }
        return false
    }

    fun findEditableAndSetText(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // 1. Try focused element
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        // 2. Search for any editable node in hierarchy
        fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable && !node.isPassword) return node
            for (i in 0 until node.childCount) {
                val res = findEditable(node.getChild(i))
                if (res != null) return res
            }
            return null
        }

        val editable = findEditable(rootNode)
        if (editable != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    fun typeTextIntoFocused(textToType: String): Boolean {
        return findEditableAndSetText(textToType)
    }

    fun performSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun tapCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun scrollForward(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun lockDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun takeScreenshotCompat(onComplete: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = Executor { command -> command.run() }
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )?.copy(Bitmap.Config.ARGB_8888, false)
                            screenshot.hardwareBuffer.close()
                            mainHandler.post { onComplete(bitmap) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy hardware buffer: ${e.message}")
                            mainHandler.post { onComplete(null) }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Accessibility takeScreenshot failed with code: $errorCode")
                        mainHandler.post { onComplete(null) }
                    }
                }
            )
        } else {
            onComplete(null)
        }
    }
}

