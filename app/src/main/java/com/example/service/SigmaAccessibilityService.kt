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
        Log.d(TAG, "SigmaAccessibilityService connected successfully")
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
            if (node.isPassword) return

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

    /**
     * Finds and clicks a node matching targetText by exact text, contentDescription, or partial match,
     * walking up the parent tree if the node itself isn't marked isClickable.
     */
    fun clickByText(targetText: String, ignoreCase: Boolean = true): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // 1. Direct system match
        val matchedNodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in matchedNodes) {
            if (performClickHierarchy(node)) return true
        }

        // 2. Recursive fuzzy match
        return fuzzyFindAndClick(rootNode, targetText)
    }

    private fun performClickHierarchy(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            current = current.parent
        }
        // If node has bounds, try gesture tap fallback
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                return tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
        }
        return false
    }

    private fun fuzzyFindAndClick(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val targetLower = target.lowercase()

        if ((text.contains(targetLower) || desc.contains(targetLower)) && !node.isPassword) {
            if (performClickHierarchy(node)) return true
        }

        for (i in 0 until node.childCount) {
            if (fuzzyFindAndClick(node.getChild(i), target)) {
                return true
            }
        }
        return false
    }

    /**
     * Dynamically finds search icon, search button, or search box in the active window.
     */
    fun findAndClickSearch(): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        val searchKeywords = listOf("search", "search here", "search youtube", "search google", "type to search", "khoj", "find")

        // 1. Find by view ID containing search
        fun findByViewId(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("search") || viewId.contains("menu_search") || viewId.contains("search_button")) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findByViewId(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val searchIdNode = findByViewId(rootNode)
        if (searchIdNode != null && performClickHierarchy(searchIdNode)) {
            Log.d(TAG, "Clicked search node by viewId")
            return true
        }

        // 2. Find by text or content description
        for (keyword in searchKeywords) {
            if (clickByText(keyword)) {
                Log.d(TAG, "Clicked search node by keyword: $keyword")
                return true
            }
        }

        // 3. Find any editable EditText
        val editable = findEditableNode(rootNode)
        if (editable != null) {
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return performClickHierarchy(editable)
        }

        return false
    }

    /**
     * Sets text on the currently focused or first available editable field.
     */
    fun findEditableAndSetText(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // 1. Focused element
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focusedNode != null && (focusedNode.isEditable || focusedNode.className?.toString()?.contains("EditText") == true)) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        // 2. First editable in hierarchy
        val editable = findEditableNode(rootNode)
        if (editable != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if ((node.isEditable || node.className?.toString()?.contains("EditText") == true) && !node.isPassword) {
            return node
        }
        for (i in 0 until node.childCount) {
            val res = findEditableNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    fun typeTextIntoFocused(textToType: String): Boolean {
        return findEditableAndSetText(textToType)
    }

    /**
     * Locates the first matching search result or video card and clicks it.
     */
    fun findAndClickResult(query: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // Try direct click by words in query
        val words = query.split(" ").filter { it.length > 2 }
        for (word in words) {
            if (clickByText(word)) return true
        }

        // Try clicking first substantial clickable item in list
        fun findFirstContentCard(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val hasContent = (text.length > 5 || desc.length > 5) && !text.equals("Search", true) && !desc.equals("Search", true)
            if (hasContent && (node.isClickable || node.parent?.isClickable == true)) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findFirstContentCard(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val card = findFirstContentCard(rootNode)
        if (card != null) {
            return performClickHierarchy(card)
        }

        return false
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
