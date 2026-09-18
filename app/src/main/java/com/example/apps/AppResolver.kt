package com.example.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.service.SigmaAccessibilityService
import kotlin.math.min

sealed class AppResolutionResult {
    data class Success(val app: AppItem) : AppResolutionResult()
    data class MultipleMatches(val candidates: List<AppItem>) : AppResolutionResult()
    data class NotFound(val query: String) : AppResolutionResult()
}

class AppResolver(
    private val context: Context,
    private val repository: InstalledAppRepository
) {

    companion object {
        private const val TAG = "AppResolver"
    }

    /**
     * Dynamically resolves app name by querying all installed launchable applications from PackageManager.
     * No hardcoded package maps.
     */
    fun resolveAndLaunch(appNameQuery: String): AppResolutionResult {
        val cleanQuery = sanitizeQuery(appNameQuery)
        if (cleanQuery.isBlank()) {
            return AppResolutionResult.NotFound(appNameQuery)
        }

        val installedApps = repository.getInstalledApps()

        // 1. Direct Exact Match on Label (Case-Insensitive)
        val exactMatch = installedApps.firstOrNull {
            it.label.trim().equals(cleanQuery, ignoreCase = true)
        }
        if (exactMatch != null && launchApp(exactMatch.packageName)) {
            return AppResolutionResult.Success(exactMatch)
        }

        // 2. Normalized alphanumeric match (ignores spaces, punctuation, e.g. "play store" == "playstore")
        val normalizedQuery = cleanQuery.replace(Regex("[^a-zA-Z0-9]"), "")
        val normalizedMatch = installedApps.firstOrNull {
            it.label.replace(Regex("[^a-zA-Z0-9]"), "").equals(normalizedQuery, ignoreCase = true)
        }
        if (normalizedMatch != null && launchApp(normalizedMatch.packageName)) {
            return AppResolutionResult.Success(normalizedMatch)
        }

        // 3. Package Name Exact or Suffix Match
        val packageMatch = installedApps.firstOrNull {
            it.packageName.equals(cleanQuery, ignoreCase = true) ||
            it.packageName.endsWith(".$cleanQuery", ignoreCase = true)
        }
        if (packageMatch != null && launchApp(packageMatch.packageName)) {
            return AppResolutionResult.Success(packageMatch)
        }

        // 4. Starts With Prefix Match on Label
        val startsWithMatches = installedApps.filter {
            it.label.trim().startsWith(cleanQuery, ignoreCase = true)
        }
        if (startsWithMatches.size == 1) {
            val app = startsWithMatches.first()
            if (launchApp(app.packageName)) {
                return AppResolutionResult.Success(app)
            }
        } else if (startsWithMatches.size > 1) {
            val first = startsWithMatches.first()
            if (launchApp(first.packageName)) {
                return AppResolutionResult.Success(first)
            }
        }

        // 5. Word-boundary / Substring Match on Label
        val containsMatches = installedApps.filter {
            it.label.contains(cleanQuery, ignoreCase = true) ||
            cleanQuery.contains(it.label, ignoreCase = true)
        }
        if (containsMatches.isNotEmpty()) {
            // Pick closest length match
            val bestContains = containsMatches.minByOrNull { Math.abs(it.label.length - cleanQuery.length) }
            if (bestContains != null && launchApp(bestContains.packageName)) {
                return AppResolutionResult.Success(bestContains)
            }
        }

        // 6. Fuzzy Match (Levenshtein Distance for Speech Recognition phonetics/typos)
        val bestFuzzy = installedApps.map { app ->
            val dist = levenshteinDistance(cleanQuery, app.label.lowercase())
            val threshold = when {
                cleanQuery.length <= 4 -> 1
                cleanQuery.length <= 7 -> 2
                else -> 3
            }
            Triple(app, dist, threshold)
        }.filter { it.second <= it.third }
         .minByOrNull { it.second }
         ?.first

        if (bestFuzzy != null && launchApp(bestFuzzy.packageName)) {
            return AppResolutionResult.Success(bestFuzzy)
        }

        // 7. System Generic Intent Fallbacks (Camera, Settings, Web Browser, Dialer, Contacts)
        val genericFallback = tryLaunchGenericIntent(cleanQuery)
        if (genericFallback != null) {
            return AppResolutionResult.Success(genericFallback)
        }

        Log.w(TAG, "No installed app matched dynamic query: '$cleanQuery' (raw: '$appNameQuery')")
        return AppResolutionResult.NotFound(appNameQuery)
    }

    private fun tryLaunchGenericIntent(query: String): AppItem? {
        try {
            when {
                query.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return AppItem(label = "Camera", packageName = "android.media.action.CAMERA", isSystemApp = true)
                    }
                }
                query.contains("setting") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return AppItem(label = "Settings", packageName = "com.android.settings", isSystemApp = true)
                    }
                }
                query.contains("browser") || query.contains("web") || query.contains("internet") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return AppItem(label = "Browser", packageName = "android.intent.action.VIEW", isSystemApp = true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generic intent launch failed: ${e.message}")
        }
        return null
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            if (intent != null) {
                context.startActivity(intent)
                Log.d(TAG, "Successfully triggered launch intent for: $packageName")
                true
            } else {
                Log.w(TAG, "No launcher intent returned by PackageManager for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName: ${e.message}", e)
            false
        }
    }

    /**
     * Verifies that the launched app is indeed in the foreground using AccessibilityService.
     */
    fun verifyAppForeground(expectedPackageName: String): Boolean {
        val current = SigmaAccessibilityService.currentPackage
        return if (current.isNotEmpty()) {
            current.equals(expectedPackageName, ignoreCase = true) || current.contains(expectedPackageName, ignoreCase = true)
        } else {
            true // If accessibility is not enabled, assume standard Intent launch success
        }
    }

    private fun sanitizeQuery(raw: String): String {
        return raw.lowercase()
            .replace("sigma", "")
            .replace("open", "")
            .replace("kholo", "")
            .replace("launch", "")
            .replace("start", "")
            .replace("chalao", "")
            .replace("play", "")
            .replace("app", "")
            .replace("application", "")
            .replace("please", "")
            .trim()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
