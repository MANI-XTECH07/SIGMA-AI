package com.example.automation

import android.util.Log

data class ConversationTurn(
    val timestamp: Long = System.currentTimeMillis(),
    val rawCommand: String,
    val intent: String,
    val targetApp: String? = null,
    val query: String? = null,
    val selectedItem: String? = null,
    val executionSuccess: Boolean = true
)

object CommandContext {
    private const val TAG = "CommandContext"
    private const val MAX_HISTORY = 5

    private val history = mutableListOf<ConversationTurn>()

    @Volatile var lastSearchQuery: String? = null
    @Volatile var lastAppOpened: String? = null
    @Volatile var lastSelectedItem: String? = null
    @Volatile var lastObservedResults: List<String> = emptyList()

    @Synchronized
    fun recordTurn(
        rawCommand: String,
        intent: String,
        targetApp: String? = null,
        query: String? = null,
        selectedItem: String? = null,
        success: Boolean = true
    ) {
        if (targetApp != null) lastAppOpened = targetApp
        if (query != null) lastSearchQuery = query
        if (selectedItem != null) lastSelectedItem = selectedItem

        val turn = ConversationTurn(
            rawCommand = rawCommand,
            intent = intent,
            targetApp = targetApp,
            query = query,
            selectedItem = selectedItem,
            executionSuccess = success
        )

        history.add(turn)
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }

        Log.d(TAG, "Recorded command context: app=$lastAppOpened query=$lastSearchQuery selected=$lastSelectedItem (history size: ${history.size})")
    }

    @Synchronized
    fun getRecentHistory(): List<ConversationTurn> {
        return history.toList()
    }

    /**
     * Resolves anaphoric expressions (e.g., "first one", "play it", "ye wala", "isko chalao", "aur scroll karo")
     * using stored short-term context.
     */
    fun resolveContextualCommand(rawCommand: String): ContextualResolution {
        val lower = rawCommand.lowercase().trim()

        // 1. "First one", "First result", "Pehla wala", "Pahilo wala"
        if (lower.contains("first one") || lower.contains("first result") || lower.contains("1st one") ||
            lower.contains("pehla wala") || lower.contains("pehli video") || lower.contains("pahilo wala") ||
            lower.contains("first wala") || lower.contains("1st result")
        ) {
            val query = lastSearchQuery ?: "Trending"
            return ContextualResolution.SelectFirstResult(query = query, app = lastAppOpened ?: "YouTube")
        }

        // 2. "Play it", "Isko play karo", "Ye chalao", "Bajao isko"
        if (lower == "play it" || lower == "play" || lower == "chalao isko" || lower == "isko play karo" ||
            lower == "ye chalao" || lower == "bajaide" || lower == "baja" || lower == "start it"
        ) {
            val query = lastSelectedItem ?: lastSearchQuery ?: ""
            return ContextualResolution.PlayActive(query = query, app = lastAppOpened ?: "YouTube")
        }

        // 3. "More scroll", "Aur scroll karo", "Thoda aur scroll karo", "Thap scroll"
        if (lower.contains("aur scroll") || lower.contains("more scroll") || lower.contains("thoda aur") ||
            lower.contains("ali tala sar") || lower.contains("thap scroll")
        ) {
            val isUp = lower.contains("up") || lower.contains("upar") || lower.contains("mathi")
            return ContextualResolution.ScrollMore(forward = !isUp)
        }

        // 4. "Open it", "Isko kholo", "Khol islai"
        if (lower.contains("open it") || lower.contains("isko kholo") || lower.contains("khol islai") ||
            lower.contains("open this")
        ) {
            val query = lastSelectedItem ?: lastSearchQuery ?: ""
            return ContextualResolution.OpenItem(query = query)
        }

        return ContextualResolution.None
    }

    @Synchronized
    fun clear() {
        history.clear()
        lastSearchQuery = null
        lastAppOpened = null
        lastSelectedItem = null
        lastObservedResults = emptyList()
    }
}

sealed class ContextualResolution {
    data class SelectFirstResult(val query: String, val app: String) : ContextualResolution()
    data class PlayActive(val query: String, val app: String) : ContextualResolution()
    data class ScrollMore(val forward: Boolean) : ContextualResolution()
    data class OpenItem(val query: String) : ContextualResolution()
    object None : ContextualResolution()
}
