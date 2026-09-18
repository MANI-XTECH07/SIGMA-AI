package com.example.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.service.SigmaAccessibilityService

data class SemanticTargetMatch(
    val element: ScreenElement,
    val matchType: String,
    val confidence: Float,
    val explanation: String
)

class ScreenUnderstandingEngine {
    companion object {
        private const val TAG = "ScreenUnderstandingEngine"
    }

    /**
     * Performs an on-demand, non-continuous structured inspection of the active screen.
     */
    fun inspectScreen(service: SigmaAccessibilityService? = SigmaAccessibilityService.instance): ScreenSnapshot {
        if (service == null) {
            val pkg = ForegroundAppTracker.currentPackageName
            val act = ForegroundAppTracker.currentActivityName
            return ScreenSnapshot(
                packageName = pkg,
                activityName = act,
                summary = "Accessibility service not bound."
            )
        }
        return service.buildScreenSnapshot()
    }

    /**
     * Builds full AppContext representing current foreground application and visible UI.
     */
    fun getAppContext(service: SigmaAccessibilityService? = SigmaAccessibilityService.instance): AppContext {
        val snapshot = inspectScreen(service)
        val pkg = snapshot.packageName.ifEmpty { ForegroundAppTracker.currentPackageName }
        val act = snapshot.activityName.ifEmpty { ForegroundAppTracker.currentActivityName }
        val appName = when {
            pkg.contains("youtube", ignoreCase = true) -> "YouTube"
            pkg.contains("spotify", ignoreCase = true) -> "Spotify"
            pkg.contains("chrome", ignoreCase = true) -> "Chrome"
            pkg.contains("instagram", ignoreCase = true) -> "Instagram"
            pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            pkg.contains("maps", ignoreCase = true) -> "Google Maps"
            pkg.contains("vending", ignoreCase = true) -> "Play Store"
            pkg.contains("camera", ignoreCase = true) -> "Camera"
            pkg.contains("settings", ignoreCase = true) -> "Settings"
            pkg.contains("example") || pkg.contains("sigma") -> "SIGMA Assistant"
            else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }

        val availableActions = mutableListOf<String>()
        if (snapshot.editableFields.isNotEmpty()) availableActions.add("TYPE_TEXT")
        if (snapshot.clickableElements.isNotEmpty()) availableActions.add("TAP_ELEMENT")
        if (snapshot.scrollableContainers.isNotEmpty()) availableActions.add("SCROLL")
        if (snapshot.visibleTexts.any { it.contains("search", ignoreCase = true) } ||
            snapshot.clickableElements.any { it.displayLabel.contains("search", ignoreCase = true) }) {
            availableActions.add("SEARCH")
        }
        availableActions.add("NAVIGATION")

        return AppContext(
            currentPackage = pkg,
            currentActivity = act,
            appName = appName,
            screenState = snapshot,
            availableActions = availableActions
        )
    }

    /**
     * Produces a concise, natural language summary of the visible screen for voice readout.
     * Triggered by "Sigma, read this screen" or "what is on my screen".
     */
    fun generateConciseSummary(snapshot: ScreenSnapshot): String {
        if (snapshot.allElements.isEmpty()) {
            return "The screen is currently blank or inaccessible. Please verify Accessibility permissions."
        }

        val appName = when {
            snapshot.packageName.contains("youtube") -> "YouTube"
            snapshot.packageName.contains("spotify") -> "Spotify"
            snapshot.packageName.contains("chrome") -> "Chrome"
            snapshot.packageName.contains("instagram") -> "Instagram"
            snapshot.packageName.contains("whatsapp") -> "WhatsApp"
            snapshot.packageName.contains("maps") -> "Maps"
            snapshot.packageName.contains("settings") -> "Settings"
            snapshot.packageName.contains("example") || snapshot.packageName.contains("sigma") -> "SIGMA"
            else -> snapshot.packageName.substringAfterLast(".")
        }

        val visibleHeadlines = snapshot.visibleTexts
            .filter { it.length in 3..60 && !it.startsWith("http") }
            .take(4)

        val inputsCount = snapshot.editableFields.size
        val buttonsCount = snapshot.clickableElements.size

        val sb = StringBuilder()
        sb.append("You are currently in $appName. ")

        if (visibleHeadlines.isNotEmpty()) {
            sb.append("Visible content includes: ")
            sb.append(visibleHeadlines.joinToString(", ") { "\"$it\"" })
            sb.append(". ")
        }

        if (inputsCount > 0) {
            sb.append("There ${if (inputsCount == 1) "is an active input field" else "are $inputsCount input fields"} available. ")
        }

        if (snapshot.hasDialog) {
            sb.append("A prompt dialog is currently displayed. ")
        }

        return sb.toString().trim()
    }

    /**
     * Resolves target UI elements dynamically from semantic intent (e.g. "search button", "settings", "blue button").
     * Uses prioritized multi-stage matching:
     * 1. Exact text/desc
     * 2. Semantic synonym matching (e.g., 'search' -> lens, magnifying glass, find, query, 'settings' -> gear, config)
     * 3. Partial substring matching
     * 4. Resource ID match
     * 5. Fallback report with reason
     */
    fun findSemanticTarget(rawQuery: String, snapshot: ScreenSnapshot): SemanticTargetMatch? {
        val q = rawQuery.lowercase().trim()
        if (q.isEmpty() || snapshot.allElements.isEmpty()) return null

        // Synonyms dictionary for universal semantic targeting
        val synonyms = when {
            q.contains("search") || q.contains("khoj") || q.contains("lens") ->
                listOf("search", "find", "explore", "query", "magnify", "btn_search", "action_search", "search_edit_text", "search_box")
            q.contains("settings") || q.contains("setting") ->
                listOf("settings", "setting", "preference", "preferences", "config", "options")
            q.contains("back") || q.contains("piche") || q.contains("pachadi") ->
                listOf("back", "navigate up", "arrow_back", "btn_back", "close", "cancel")
            q.contains("close") || q.contains("band") ->
                listOf("close", "dismiss", "cancel", "clear", "exit")
            q.contains("menu") || q.contains("more") ->
                listOf("more options", "menu", "overflow", "drawer", "hamburger")
            q.contains("send") || q.contains("bhejo") ->
                listOf("send", "submit", "post", "done", "confirm")
            q.contains("play") || q.contains("chalao") ->
                listOf("play", "resume", "start", "video", "track")
            else -> listOf(q)
        }

        // 1. Exact match on text or contentDescription
        for (syn in synonyms) {
            val match = snapshot.allElements.firstOrNull {
                it.text.equals(syn, ignoreCase = true) || it.contentDescription.equals(syn, ignoreCase = true)
            }
            if (match != null) {
                return SemanticTargetMatch(match, "EXACT_TEXT_MATCH", 1.0f, "Found exact matching element for '$syn'")
            }
        }

        // 2. Resource ID match
        for (syn in synonyms) {
            val match = snapshot.allElements.firstOrNull {
                it.viewIdResourceName.isNotBlank() && it.viewIdResourceName.contains(syn, ignoreCase = true)
            }
            if (match != null) {
                return SemanticTargetMatch(match, "RESOURCE_ID_MATCH", 0.9f, "Found element with matching resource ID: ${match.viewIdResourceName}")
            }
        }

        // 3. Partial substring match
        for (syn in synonyms) {
            val match = snapshot.allElements.firstOrNull {
                (it.text.isNotBlank() && it.text.contains(syn, ignoreCase = true)) ||
                (it.contentDescription.isNotBlank() && it.contentDescription.contains(syn, ignoreCase = true))
            }
            if (match != null) {
                return SemanticTargetMatch(match, "PARTIAL_TEXT_MATCH", 0.8f, "Found element containing '$syn'")
            }
        }

        // 4. Clickable candidates matching query words
        val words = q.split(" ").filter { it.length > 2 && it != "button" && it != "the" && it != "tap" && it != "click" }
        for (word in words) {
            val match = snapshot.clickableElements.firstOrNull {
                it.displayLabel.contains(word, ignoreCase = true) || it.viewIdResourceName.contains(word, ignoreCase = true)
            }
            if (match != null) {
                return SemanticTargetMatch(match, "CLICKABLE_KEYWORD_MATCH", 0.7f, "Found clickable element with keyword '$word'")
            }
        }

        return null
    }

    /**
     * Resolves ordinal and semantic queries like "first result", "second one", "the video about Naruto", "open that one".
     */
    fun resolveSmartResult(query: String, snapshot: ScreenSnapshot): ScreenElement? {
        val lower = query.lowercase().trim()

        // Filter valid content cards/items (elements with non-trivial height, containing text or clickable)
        val candidates = snapshot.allElements.filter {
            it.bounds.height() in 60..1200 &&
            it.bounds.width() > 100 &&
            (it.isClickable || it.text.isNotBlank() || it.contentDescription.isNotBlank())
        }.sortedBy { it.bounds.top }

        if (candidates.isEmpty()) return null

        // 1. Ordinal index matching: "first", "1st", "second", "2nd", "third", "3rd", "last"
        val targetIndex = when {
            lower.contains("first") || lower.contains("1st") || lower.contains("pehla") || lower.contains("pahilo") -> 0
            lower.contains("second") || lower.contains("2nd") || lower.contains("dusra") || lower.contains("dosro") -> 1
            lower.contains("third") || lower.contains("3rd") || lower.contains("teesra") || lower.contains("tesro") -> 2
            lower.contains("fourth") || lower.contains("4th") || lower.contains("chautha") -> 3
            lower.contains("last") || lower.contains("aakhri") || lower.contains("antim") -> candidates.lastIndex
            else -> null
        }

        if (targetIndex != null && targetIndex in candidates.indices) {
            Log.d(TAG, "Resolved smart result via index $targetIndex out of ${candidates.size} candidates.")
            return candidates[targetIndex]
        }

        // 2. Keyword/Title matching: "the video about Naruto", "open result for X"
        val searchKeywords = lower.replace("the video with title", "")
            .replace("the video about", "")
            .replace("open the result about", "")
            .replace("play the one about", "")
            .replace("play that one", "")
            .replace("open that one", "")
            .replace("result about", "")
            .replace("video about", "")
            .trim()

        if (searchKeywords.isNotBlank() && searchKeywords != "it" && searchKeywords != "that" && searchKeywords != "this") {
            val matchingCandidate = candidates.firstOrNull {
                it.text.contains(searchKeywords, ignoreCase = true) ||
                it.contentDescription.contains(searchKeywords, ignoreCase = true)
            }
            if (matchingCandidate != null) {
                Log.d(TAG, "Resolved smart result via keyword matching: '$searchKeywords'")
                return matchingCandidate
            }
        }

        // 3. Fallback: return the topmost primary candidate item
        return candidates.firstOrNull()
    }
}
