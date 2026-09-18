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
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.Executor

data class NodeDumpInfo(
    val packageName: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val viewIdResourceName: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isEnabled: Boolean,
    val isVisibleToUser: Boolean,
    val bounds: Rect
)

class SigmaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SigmaAccessibility"

        @Volatile
        var instance: SigmaAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        @Volatile
        var currentPackage: String = ""
            private set

        @Volatile
        var lastEventTime: Long = 0
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "ACCESSIBILITY_CONNECTED: SigmaAccessibilityService successfully bound by Android OS")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            lastEventTime = System.currentTimeMillis()
            it.packageName?.let { pkg ->
                currentPackage = pkg.toString()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SigmaAccessibilityService interrupted by system")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "ACCESSIBILITY_DISCONNECTED: SigmaAccessibilityService destroyed")
    }

    /**
     * Obtains the real active root window node.
     * Checks rootInActiveWindow first, and falls back across interactive windows (TYPE_APPLICATION)
     * so it never fails during window transitions or splash screens.
     */
    fun getActiveRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (root != null) return root

        try {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                val appWindow = winList.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root != null }
                if (appWindow?.root != null) return appWindow.root
                for (w in winList) {
                    val r = w.root
                    if (r != null) return r
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error obtaining active root window: ${e.message}")
        }
        return null
    }

    /**
     * Dumps all real visible accessibility nodes from active window for diagnostics.
     */
    fun dumpScreenNodes(): List<NodeDumpInfo> {
        val rootNode = getActiveRoot() ?: return emptyList()
        val list = mutableListOf<NodeDumpInfo>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || node.isPassword) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val info = NodeDumpInfo(
                packageName = node.packageName?.toString() ?: "",
                className = node.className?.toString() ?: "",
                text = node.text?.toString()?.trim() ?: "",
                contentDescription = node.contentDescription?.toString()?.trim() ?: "",
                viewIdResourceName = node.viewIdResourceName ?: "",
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable,
                isEnabled = node.isEnabled,
                isVisibleToUser = node.isVisibleToUser,
                bounds = bounds
            )
            list.add(info)

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        return list
    }

    fun extractScreenText(): List<String> {
        val rootNode = getActiveRoot() ?: return emptyList()
        val textList = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || node.isPassword) return

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
     * walking up the parent tree if the node itself isn't marked isClickable, and falling back to physical tap.
     */
    fun clickByText(targetText: String, ignoreCase: Boolean = true): Boolean {
        val rootNode = getActiveRoot() ?: return false

        // 1. Direct system match by text
        val matchedNodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in matchedNodes) {
            if (performClickHierarchy(node)) {
                Log.d(TAG, "NODE_CLICKED by system text match: '$targetText'")
                return true
            }
        }

        // 2. Recursive fuzzy match across all nodes (text, desc, viewId)
        val clicked = fuzzyFindAndClick(rootNode, targetText)
        if (clicked) {
            Log.d(TAG, "NODE_CLICKED by fuzzy match: '$targetText'")
        }
        return clicked
    }

    fun performClickHierarchy(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            current = current.parent
        }

        // Fallback: Dispatch physical tap gesture to the node's center screen coordinates
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                Log.d(TAG, "Fallback tapping center coordinates: ($cx, $cy)")
                return tapCoordinates(cx, cy)
            }
        }
        return false
    }

    private fun fuzzyFindAndClick(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null || node.isPassword) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val targetLower = target.lowercase().trim()

        // Match content description or text first
        if (desc.contains(targetLower) || text.contains(targetLower)) {
            if (performClickHierarchy(node)) return true
        }

        // Match viewId only if it's an interactive widget (not a full-screen layout container)
        if (viewId.contains(targetLower)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() in 1..800 && bounds.height() in 1..800) {
                if (performClickHierarchy(node)) return true
            }
        }

        for (i in 0 until node.childCount) {
            if (fuzzyFindAndClick(node.getChild(i), target)) {
                return true
            }
        }
        return false
    }

    /**
     * Dynamically locates and triggers the search icon, button, or input field on the screen.
     * Evaluates candidates based on content description, view ID, and geometry, with coordinate fallback.
     */
    fun findAndClickSearch(): Boolean {
        val rootNode = getActiveRoot() ?: return false

        // 1. Check if an editable search field is ALREADY open and visible
        val activeEditable = findEditableNode(rootNode)
        if (activeEditable != null && activeEditable.isVisibleToUser) {
            val rect = Rect()
            activeEditable.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                activeEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (performClickHierarchy(activeEditable)) {
                    Log.d(TAG, "NODE_CLICKED already visible editable search field")
                    return true
                }
            }
        }

        // 2. Score and rank all potential search candidate nodes
        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()

        fun scoreNode(node: AccessibilityNodeInfo?): Int {
            if (node == null || node.isPassword) return 0
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase()?.trim() ?: ""
            val cls = node.className?.toString() ?: ""

            var score = 0

            // Exact match on contentDescription (standard across YouTube, Spotify, etc.)
            if (desc == "search" || desc == "search youtube" || desc == "search google" || desc == "search here") {
                score += 100
            } else if (desc.contains("search") || desc.contains("khoj")) {
                score += 70
            }

            if (text == "search" || text == "search youtube" || text == "search here") {
                score += 90
            } else if (text.contains("search") || text.contains("khoj")) {
                score += 60
            }

            // High-confidence view IDs
            if (viewId.contains("menu_item_search") || viewId.contains("search_button") ||
                viewId.contains("action_search") || viewId.contains("search_edit_text") ||
                viewId.contains("search_bar")
            ) {
                score += 85
            } else if (viewId.contains("search") && !viewId.contains("container") && !viewId.contains("layout")) {
                score += 40
            }

            if (node.isClickable) score += 20
            if (cls.contains("Button") || cls.contains("ImageView") || cls.contains("ImageButton")) score += 15
            if (node.isEditable) score += 30

            // Heavy penalty for full-screen layout wrappers
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 900 && bounds.height() > 1200) {
                score -= 150
            }

            return score
        }

        fun collectCandidates(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val s = scoreNode(node)
            if (s >= 35) {
                candidates.add(node to s)
            }
            for (i in 0 until node.childCount) {
                collectCandidates(node.getChild(i))
            }
        }

        collectCandidates(rootNode)

        val sorted = candidates.sortedByDescending { it.second }
        for ((cand, score) in sorted) {
            Log.d(TAG, "Testing search candidate score=$score desc='${cand.contentDescription}' text='${cand.text}' id='${cand.viewIdResourceName}'")
            if (performClickHierarchy(cand)) {
                Log.d(TAG, "NODE_CLICKED search candidate with score $score: ${cand.viewIdResourceName}")
                return true
            }
        }

        // 3. Fallback: Standard top action-bar search location (YouTube / OEM apps: ~85% width, ~6% height)
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()
        if (screenW > 0 && screenH > 0) {
            val fallbackX = screenW * 0.85f
            val fallbackY = screenH * 0.06f
            Log.d(TAG, "Fallback tapping top action bar search at ($fallbackX, $fallbackY)")
            if (tapCoordinates(fallbackX, fallbackY)) {
                return true
            }
        }

        return false
    }

    /**
     * Sets text on the currently focused or first available editable field and verifies insertion.
     */
    fun findEditableAndSetText(textToType: String): Boolean {
        val rootNode = getActiveRoot() ?: return false

        // 1. Focused element
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focusedNode != null && (focusedNode.isEditable || focusedNode.className?.toString()?.contains("EditText") == true)) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val resultSet = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (resultSet) {
                Log.d(TAG, "TEXT_ENTERED successfully into focused node: \"$textToType\"")
                return true
            }
        }

        // 2. First editable in hierarchy
        val editable = findEditableNode(rootNode)
        if (editable != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val resultSet = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (resultSet) {
                Log.d(TAG, "TEXT_ENTERED successfully into editable node: \"$textToType\"")
                return true
            }
        }

        Log.w(TAG, "Failed to enter text: No editable node accepted ACTION_SET_TEXT")
        return false
    }

    /**
     * Submits a search query via ACTION_IME_ENTER, dropdown search suggestions, search button, or keyboard Enter tap.
     */
    fun submitSearch(): Boolean {
        val rootNode = getActiveRoot() ?: return false

        // 1. Try IME action on focused node if available (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val imeClicked = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                if (imeClicked) {
                    Log.d(TAG, "Submitted search via ACTION_IME_ENTER on focused node")
                    return true
                }
            }
        }

        // 2. Try clicking first suggestion item in drop-down list if present (YouTube instant suggestions)
        fun findFirstSuggestion(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val cls = node.className?.toString() ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            val isSuggestion = viewId.contains("suggest") || viewId.contains("search_typeahead") ||
                    viewId.contains("item") || cls.contains("TextView") || cls.contains("ViewGroup")

            if (isSuggestion && (text.length > 2 || desc.length > 2) && (node.isClickable || node.parent?.isClickable == true)) {
                if (!text.equals("Home", true) && !text.equals("Explore", true) && !text.equals("Library", true)) {
                    val b = Rect()
                    node.getBoundsInScreen(b)
                    if (b.top > 120 && b.height() > 30) {
                        return node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val found = findFirstSuggestion(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val suggestion = findFirstSuggestion(rootNode)
        if (suggestion != null && performClickHierarchy(suggestion)) {
            Log.d(TAG, "NODE_CLICKED search suggestion dropdown item: ${suggestion.text ?: suggestion.contentDescription}")
            return true
        }

        // 3. Look for explicit search submit button
        val submitKeywords = listOf("search", "submit", "go", "enter", "done", "khojo")
        for (keyword in submitKeywords) {
            val matched = rootNode.findAccessibilityNodeInfosByText(keyword)
            for (node in matched) {
                if (node.isClickable || node.parent?.isClickable == true) {
                    if (performClickHierarchy(node)) {
                        Log.d(TAG, "NODE_CLICKED search submit button: '$keyword'")
                        return true
                    }
                }
            }
        }

        // 4. Fallback: Tap bottom-right on-screen keyboard Enter/Search key (~90% width, ~94% height)
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()
        if (screenW > 0 && screenH > 0) {
            val enterX = screenW * 0.90f
            val enterY = screenH * 0.94f
            Log.d(TAG, "Fallback search submit: tapping IME Enter key at ($enterX, $enterY)")
            if (tapCoordinates(enterX, enterY)) {
                return true
            }
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

    // =========================================================================
    // SECTION 14 REUSABLE ACCESSIBILITY PIPELINE FUNCTIONS
    // =========================================================================

    /**
     * Finds the nearest clickable node at or above this node in the hierarchy.
     */
    fun findClickableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    /**
     * Finds a node in active root matching text.
     */
    fun findNodeByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val rootNode = getActiveRoot() ?: return null
        val target = text.trim()
        if (target.isEmpty()) return null

        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val nodeText = node.text?.toString()?.trim() ?: ""
            val matches = if (exact) {
                nodeText.equals(target, ignoreCase = true)
            } else {
                nodeText.contains(target, ignoreCase = true)
            }
            if (matches) return node

            for (i in 0 until node.childCount) {
                val found = search(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        return search(rootNode)
    }

    /**
     * Finds a node in active root matching contentDescription.
     */
    fun findNodeByContentDescription(desc: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val rootNode = getActiveRoot() ?: return null
        val target = desc.trim()
        if (target.isEmpty()) return null

        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
            val matches = if (exact) {
                nodeDesc.equals(target, ignoreCase = true)
            } else {
                nodeDesc.contains(target, ignoreCase = true)
            }
            if (matches) return node

            for (i in 0 until node.childCount) {
                val found = search(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        return search(rootNode)
    }

    /**
     * Clicks the given node, or walks up its parent tree to click the first clickable container.
     */
    fun clickNodeOrClickableParent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val clickable = findClickableNode(node)
        if (clickable != null) {
            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "SIGMA_AUTOMATION: Successfully clicked clickable node: cls=${clickable.className} desc='${clickable.contentDescription}' text='${clickable.text}'")
                return true
            }
        }

        // Fallback: If node itself can receive ACTION_CLICK
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Sets text into an editable node using ACTION_SET_TEXT.
     */
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        val target = if (node != null && (node.isEditable || node.className?.toString()?.contains("EditText") == true)) {
            node
        } else {
            val root = getActiveRoot() ?: return false
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        } ?: return false

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "SIGMA_AUTOMATION: setText('$text') success=$success")
        return success
    }

    /**
     * Scrolls the current screen forward or backward using real accessibility actions.
     */
    fun performScroll(forward: Boolean = true): Boolean {
        val rootNode = getActiveRoot() ?: return false

        fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val res = findScrollable(node.getChild(i))
                if (res != null) return res
            }
            return null
        }

        val scrollable = findScrollable(rootNode) ?: rootNode
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrolled = scrollable.performAction(action)
        Log.d(TAG, "SIGMA_AUTOMATION: performScroll(forward=$forward) result=$scrolled")
        return scrolled
    }

    /**
     * Actively observes the current screen returning a list of visible node metadata.
     */
    fun observeScreen(): List<NodeDumpInfo> {
        return dumpScreenNodes()
    }

    /**
     * Locates the best matching search result container on the active screen.
     * Evaluates visible title matches, query tokens, and result card bounds without fixed coordinates.
     */
    fun findMatchingResult(query: String): AccessibilityNodeInfo? {
        val rootNode = getActiveRoot() ?: return null

        val stopWords = setOf("play", "video", "song", "audio", "first", "result", "the", "and", "it", "search", "youtube", "for", "music")
        val tokens = query.split(" ", "-", "_")
            .map { it.trim().lowercase() }
            .filter { it.length > 2 && !stopWords.contains(it) }

        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        val displayH = resources.displayMetrics.heightPixels

        fun evaluate(node: AccessibilityNodeInfo?) {
            if (node == null || node.isPassword) return
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val cls = node.className?.toString() ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Skip top action bar or bottom nav bar
            if (bounds.top < 120 || bounds.bottom > displayH * 0.94f || bounds.height() < 25) {
                for (i in 0 until node.childCount) evaluate(node.getChild(i))
                return
            }

            var score = 0

            // Near-exact full query match
            val cleanQuery = query.lowercase().trim()
            if (cleanQuery.length > 3 && (text.contains(cleanQuery) || desc.contains(cleanQuery))) {
                score += 150
            }

            // Word tokens
            for (token in tokens) {
                if (text.contains(token)) score += 40
                if (desc.contains(token)) score += 40
            }

            // Play / Media cues
            val mediaCues = listOf("views", "duration", "channel", "play", "video", "min", "sec", "artist", "album", "track")
            for (cue in mediaCues) {
                if (desc.contains(cue) || text.contains(cue)) score += 15
            }

            // Result containers often have a substantial height and width
            if (bounds.width() > 300 && bounds.height() > 80) {
                score += 20
            }

            // Interactive
            if (node.isClickable || node.parent?.isClickable == true) {
                score += 25
            }

            if (score >= 35) {
                candidates.add(node to score)
            }

            for (i in 0 until node.childCount) {
                evaluate(node.getChild(i))
            }
        }

        evaluate(rootNode)

        if (candidates.isNotEmpty()) {
            val bestCandidate = candidates.maxByOrNull { it.second }?.first
            Log.d(TAG, "SIGMA_AUTOMATION: Best matching result candidate found: text='${bestCandidate?.text}' desc='${bestCandidate?.contentDescription}'")
            return bestCandidate
        }

        // If no token match, locate the first playable result card in the body
        fun findFirstCard(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.top in 150..(displayH * 0.75).toInt() && bounds.height() in 90..1000 && bounds.width() > 350) {
                val hasClickable = node.isClickable || node.parent?.isClickable == true
                val hasContent = (node.text?.isNotBlank() == true || node.contentDescription?.isNotBlank() == true)
                if (hasClickable && hasContent) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                val found = findFirstCard(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val firstCard = findFirstCard(rootNode)
        if (firstCard != null) {
            Log.d(TAG, "SIGMA_AUTOMATION: Falling back to first visible content card on screen: text='${firstCard.text}' desc='${firstCard.contentDescription}'")
            return firstCard
        }

        return null
    }

    /**
     * Locates the first matching search result or video card and performs a real accessibility click.
     * Never uses hardcoded screen coordinates.
     */
    fun findAndClickResult(query: String): Boolean {
        val rootNode = getActiveRoot() ?: return false
        Log.i(TAG, "SIGMA_AUTOMATION: findAndClickResult(query='$query')")

        // 1. Try finding matching result on current screen
        var targetNode = findMatchingResult(query)

        // 2. If not found on initial screen, perform one safe forward scroll and search again (Section 12)
        if (targetNode == null) {
            Log.d(TAG, "SIGMA_AUTOMATION: No matching result in initial view, attempting single forward scroll...")
            if (performScroll(forward = true)) {
                Thread.sleep(800L) // UI settling time
                targetNode = findMatchingResult(query)
            }
        }

        if (targetNode == null) {
            Log.w(TAG, "SIGMA_AUTOMATION: findAndClickResult failed: No matching result container located.")
            return false
        }

        // 3. Perform click hierarchy on the result node
        // Prefer child play button if present inside the container
        val childPlayButton = findPlayControlInNode(targetNode)
        if (childPlayButton != null && clickNodeOrClickableParent(childPlayButton)) {
            Log.i(TAG, "SIGMA_AUTOMATION: CLICK_RESULT = true (via child play button: desc='${childPlayButton.contentDescription}')")
            return true
        }

        val clicked = clickNodeOrClickableParent(targetNode)
        Log.i(TAG, "SIGMA_AUTOMATION: CLICK_RESULT = $clicked (node: desc='${targetNode.contentDescription}' text='${targetNode.text}')")
        return clicked
    }

    /**
     * Detects play controls on screen using contentDescription, text, or widget structures (Section 6).
     */
    fun findAndClickPlayControl(): Boolean {
        val rootNode = getActiveRoot() ?: return false

        val playKeywords = listOf("play", "play video", "play song", "start", "listen", "resume", "watch", "open video")

        fun searchPlay(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || node.isPassword) return null
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val cls = node.className?.toString() ?: ""

            for (kw in playKeywords) {
                if (desc == kw || desc.startsWith("$kw ") || text == kw || text.startsWith("$kw ")) {
                    val clickable = findClickableNode(node) ?: node
                    return clickable
                }
            }

            for (i in 0 until node.childCount) {
                val found = searchPlay(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val playNode = searchPlay(rootNode)
        if (playNode != null) {
            val clicked = clickNodeOrClickableParent(playNode)
            Log.i(TAG, "SIGMA_AUTOMATION: PLAY_CONTROL_FOUND = true, PLAY_CLICK = $clicked (desc='${playNode.contentDescription}')")
            return clicked
        }

        Log.d(TAG, "SIGMA_AUTOMATION: No explicit play control button found on active screen.")
        return false
    }

    private fun findPlayControlInNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || node.isPassword) return null
        val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        val playKeywords = listOf("play", "play video", "play song", "watch")

        for (kw in playKeywords) {
            if (desc.contains(kw) || text.contains(kw)) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val found = findPlayControlInNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Verifies whether playback actually started using real UI evidence (Section 8):
     * - Pause button appears ("Pause", "Pause video", "Pause song")
     * - Player screen / video container appeared
     * - Timeline / seekbar / progress indicators active
     */
    fun verifyPlayback(): Boolean {
        val rootNode = getActiveRoot() ?: return false

        var hasPauseButton = false
        var hasPlayerView = false
        var hasTimelineControl = false

        fun scan(node: AccessibilityNodeInfo?) {
            if (node == null || node.isPassword) return
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val cls = node.className?.toString() ?: ""

            // Universal playback active indicator: "Pause" button is present!
            if (desc.contains("pause") || text.contains("pause")) {
                hasPauseButton = true
            }

            // Player container indicators
            if (viewId.contains("player") || viewId.contains("watch_") || viewId.contains("video_surface") ||
                cls.contains("PlayerView") || cls.contains("VideoView") || viewId.contains("exo_")
            ) {
                hasPlayerView = true
            }

            // Timeline / Seekbar indicators
            if (viewId.contains("time_bar") || viewId.contains("progress") || cls.contains("SeekBar") ||
                desc.contains("elapsed") || desc.contains("seek") || text.matches(Regex("""\d+:\d+.*"""))
            ) {
                hasTimelineControl = true
            }

            for (i in 0 until node.childCount) {
                scan(node.getChild(i))
            }
        }

        scan(rootNode)

        val verified = hasPauseButton || (hasPlayerView && hasTimelineControl) || hasPlayerView
        Log.i(TAG, "SIGMA_AUTOMATION: PLAYBACK_VERIFIED = $verified (pauseBtn=$hasPauseButton, playerView=$hasPlayerView, timeline=$hasTimelineControl)")
        return verified
    }

    fun performSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Swipe gesture failed: ${e.message}")
                false
            }
        }
        return false
    }

    /**
     * Dispatches a non-empty tap gesture stroke to (x, y).
     * Adds lineTo(x, y + 1f) so StrokeDescription never receives an empty path.
     */
    fun tapCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return try {
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x, y + 1f)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, 80)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val result = dispatchGesture(gesture, null, null)
                Log.d(TAG, "GESTURE_DISPATCH tap at ($x, $y) returned $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch tap gesture at ($x, $y): ${e.message}", e)
                false
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val rootNode = getActiveRoot() ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(): Boolean {
        val rootNode = getActiveRoot() ?: return false
        return rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    fun lockDevice(): Boolean = lockScreen()

    fun takeScreenshotCompat(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = Executor { command -> mainHandler.post(command) }
            captureScreenshot(executor, callback)
        } else {
            callback(null)
        }
    }

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun captureScreenshot(executor: Executor, callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            callback(copy)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process screenshot bitmap: ${e.message}")
                            callback(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "AccessibilityService takeScreenshot failed with code: $errorCode")
                        callback(null)
                    }
                }
            )
        } else {
            callback(null)
        }
    }
}
