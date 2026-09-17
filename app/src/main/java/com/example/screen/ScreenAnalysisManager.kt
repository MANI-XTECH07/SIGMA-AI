package com.example.screen

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.service.SigmaAccessibilityService

data class UiElementInfo(
    val text: String,
    val contentDescription: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect
)

data class ScreenAnalysisResult(
    val visibleTexts: List<String>,
    val clickableElements: List<UiElementInfo>,
    val editableFields: List<UiElementInfo>,
    val hasActiveDialog: Boolean,
    val summary: String
)

class ScreenAnalysisManager {

    companion object {
        private const val TAG = "ScreenAnalysisMgr"
    }

    /**
     * Inspects the live accessibility window hierarchy to extract real UI elements.
     * Never fabricates screen content.
     */
    fun analyzeCurrentScreen(): ScreenAnalysisResult {
        val service = SigmaAccessibilityService.instance
        if (service == null) {
            return ScreenAnalysisResult(
                visibleTexts = emptyList(),
                clickableElements = emptyList(),
                editableFields = emptyList(),
                hasActiveDialog = false,
                summary = "Accessibility Service is not enabled. Please enable it in Settings to read screen content."
            )
        }

        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            return ScreenAnalysisResult(
                visibleTexts = emptyList(),
                clickableElements = emptyList(),
                editableFields = emptyList(),
                hasActiveDialog = false,
                summary = "No active window detected."
            )
        }

        val visibleTexts = mutableListOf<String>()
        val clickableElements = mutableListOf<UiElementInfo>()
        val editableFields = mutableListOf<UiElementInfo>()
        var hasDialog = false

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isPassword) return // Safety: never read passwords

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val cls = node.className?.toString() ?: ""

            if (cls.contains("Dialog", ignoreCase = true) || cls.contains("AlertDialog", ignoreCase = true)) {
                hasDialog = true
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val element = UiElementInfo(
                text = text,
                contentDescription = desc,
                className = cls,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable,
                bounds = bounds
            )

            if (text.isNotEmpty() && !visibleTexts.contains(text)) {
                visibleTexts.add(text)
            } else if (desc.isNotEmpty() && !visibleTexts.contains(desc)) {
                visibleTexts.add(desc)
            }

            if (node.isClickable) {
                clickableElements.add(element)
            }

            if (node.isEditable) {
                editableFields.add(element)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)

        val summary = buildString {
            append("Screen contains ${visibleTexts.size} text elements, ")
            append("${clickableElements.size} clickable controls, ")
            append("and ${editableFields.size} input fields.")
            if (visibleTexts.isNotEmpty()) {
                append(" Visible items include: ")
                append(visibleTexts.take(5).joinToString(", "))
            }
        }

        return ScreenAnalysisResult(
            visibleTexts = visibleTexts,
            clickableElements = clickableElements,
            editableFields = editableFields,
            hasActiveDialog = hasDialog,
            summary = summary
        )
    }
}
