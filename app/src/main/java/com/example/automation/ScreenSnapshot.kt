package com.example.automation

import android.graphics.Rect

data class ScreenElement(
    val text: String = "",
    val contentDescription: String = "",
    val viewIdResourceName: String = "",
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isChecked: Boolean = false,
    val isSelected: Boolean = false,
    val isFocused: Boolean = false,
    val bounds: Rect = Rect()
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewIdResourceName.isNotBlank() -> viewIdResourceName.substringAfter(":id/").substringAfter("/")
            else -> className.substringAfterLast(".")
        }

    val centerX: Float
        get() = bounds.exactCenterX()

    val centerY: Float
        get() = bounds.exactCenterY()
}

data class ScreenSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val activityName: String = "",
    val visibleTexts: List<String> = emptyList(),
    val clickableElements: List<ScreenElement> = emptyList(),
    val editableFields: List<ScreenElement> = emptyList(),
    val scrollableContainers: List<ScreenElement> = emptyList(),
    val allElements: List<ScreenElement> = emptyList(),
    val hasDialog: Boolean = false,
    val summary: String = ""
) {
    fun findElement(query: String): ScreenElement? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null

        // 1. Exact text or content description
        allElements.firstOrNull {
            it.text.equals(q, ignoreCase = true) || it.contentDescription.equals(q, ignoreCase = true)
        }?.let { return it }

        // 2. Partial text or content description
        allElements.firstOrNull {
            (it.text.isNotBlank() && it.text.contains(q, ignoreCase = true)) ||
            (it.contentDescription.isNotBlank() && it.contentDescription.contains(q, ignoreCase = true))
        }?.let { return it }

        // 3. Resource ID
        allElements.firstOrNull {
            it.viewIdResourceName.isNotBlank() && it.viewIdResourceName.contains(q, ignoreCase = true)
        }?.let { return it }

        return null
    }
}
