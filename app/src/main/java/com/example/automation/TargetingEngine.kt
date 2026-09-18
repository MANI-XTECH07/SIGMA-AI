package com.example.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.service.SigmaAccessibilityService

object TargetingEngine {
    private const val TAG = "TargetingEngine"

    sealed class TargetResult {
        data class NodeTarget(val node: AccessibilityNodeInfo, val strategy: String) : TargetResult()
        data class CoordinateTarget(val x: Float, val y: Float, val strategy: String) : TargetResult()
        object NotFound : TargetResult()
    }

    /**
     * Executes hybrid UI target discovery according to strict priority:
     * 1. Accessibility text / contentDescription exact match
     * 2. Accessibility text / contentDescription partial match
     * 3. View resource ID match
     * 4. Clickable parent traversal
     * 5. Fallback coordinate calculation from snapshot or screen geometry
     */
    fun findTarget(query: String, service: SigmaAccessibilityService? = SigmaAccessibilityService.instance): TargetResult {
        if (service == null) return TargetResult.NotFound
        val rootNode = service.getActiveRoot() ?: return TargetResult.NotFound
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return TargetResult.NotFound

        // 1. Accessibility exact text / contentDescription
        var targetNode: AccessibilityNodeInfo? = null
        fun findExact(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            if (text == cleanQuery || desc == cleanQuery) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findExact(node.getChild(i))
                if (found != null) return found
            }
            return null
        }
        targetNode = findExact(rootNode)
        if (targetNode != null) {
            val clickable = service.findClickableNode(targetNode) ?: targetNode
            Log.d(TAG, "Target found via Priority 1 (Exact text/desc): '$query'")
            return TargetResult.NodeTarget(clickable, "EXACT_TEXT_DESC")
        }

        // 2. Accessibility partial text / contentDescription match
        fun findPartial(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            if ((text.isNotEmpty() && text.contains(cleanQuery)) || (desc.isNotEmpty() && desc.contains(cleanQuery))) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findPartial(node.getChild(i))
                if (found != null) return found
            }
            return null
        }
        targetNode = findPartial(rootNode)
        if (targetNode != null) {
            val clickable = service.findClickableNode(targetNode) ?: targetNode
            Log.d(TAG, "Target found via Priority 2 (Partial text/desc): '$query'")
            return TargetResult.NodeTarget(clickable, "PARTIAL_TEXT_DESC")
        }

        // 3. View Resource ID match
        fun findResourceId(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if (resId.isNotEmpty() && resId.contains(cleanQuery)) {
                return node
            }
            for (i in 0 until node.childCount) {
                val found = findResourceId(node.getChild(i))
                if (found != null) return found
            }
            return null
        }
        targetNode = findResourceId(rootNode)
        if (targetNode != null) {
            val clickable = service.findClickableNode(targetNode) ?: targetNode
            Log.d(TAG, "Target found via Priority 3 (Resource ID): '$query'")
            return TargetResult.NodeTarget(clickable, "RESOURCE_ID")
        }

        // 4. Check snapshot element coordinates
        val snapshot = service.buildScreenSnapshot()
        val element = snapshot.findElement(query)
        if (element != null && element.bounds.width() > 0 && element.bounds.height() > 0) {
            Log.d(TAG, "Target found via Priority 5 (Snapshot coordinate fallback): ($element.centerX, $element.centerY)")
            return TargetResult.CoordinateTarget(element.centerX, element.centerY, "SNAPSHOT_COORDINATE")
        }

        Log.w(TAG, "Target not found for query: '$query'")
        return TargetResult.NotFound
    }

    /**
     * Executes a hybrid click/tap on the given target using priority order:
     * Node performAction(ACTION_CLICK) -> Clickable parent -> Tap coordinate gesture
     */
    fun performHybridClick(query: String, service: SigmaAccessibilityService? = SigmaAccessibilityService.instance): Boolean {
        if (service == null) return false
        val target = findTarget(query, service)
        return when (target) {
            is TargetResult.NodeTarget -> {
                val clicked = service.clickNodeOrClickableParent(target.node)
                if (clicked) {
                    Log.d(TAG, "Hybrid click succeeded via ${target.strategy}")
                    true
                } else {
                    val rect = Rect()
                    target.node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        service.tapCoordinates(rect.exactCenterX(), rect.exactCenterY())
                    } else false
                }
            }
            is TargetResult.CoordinateTarget -> {
                service.tapCoordinates(target.x, target.y)
            }
            TargetResult.NotFound -> false
        }
    }
}
