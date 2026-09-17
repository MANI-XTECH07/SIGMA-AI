package com.example.apps

import android.content.Context
import android.content.Intent
import android.util.Log

sealed class AppResolutionResult {
    data class Success(val app: AppItem) : AppResolutionResult()
    data class MultipleMatches(val candidates: List<AppItem>) : AppResolutionResult()
    object NotFound : AppResolutionResult()
}

class AppResolver(
    private val context: Context,
    private val repository: InstalledAppRepository
) {

    companion object {
        private const val TAG = "AppResolver"
    }

    /**
     * Resolves app name by query and launches it if a single clear match is found.
     */
    fun resolveAndLaunch(appNameQuery: String): AppResolutionResult {
        val cleanQuery = sanitizeQuery(appNameQuery)
        val installed = repository.getInstalledApps()

        // 1. Exact case-insensitive match
        val exactMatch = installed.firstOrNull { it.label.equals(cleanQuery, ignoreCase = true) }
        if (exactMatch != null) {
            launchApp(exactMatch.packageName)
            return AppResolutionResult.Success(exactMatch)
        }

        // 2. Starts with query
        val startsWithMatches = installed.filter { it.label.startsWith(cleanQuery, ignoreCase = true) }
        if (startsWithMatches.size == 1) {
            launchApp(startsWithMatches.first().packageName)
            return AppResolutionResult.Success(startsWithMatches.first())
        }

        // 3. Contains query
        val containsMatches = installed.filter {
            it.label.contains(cleanQuery, ignoreCase = true) ||
            cleanQuery.contains(it.label, ignoreCase = true)
        }

        return when {
            containsMatches.size == 1 -> {
                launchApp(containsMatches.first().packageName)
                AppResolutionResult.Success(containsMatches.first())
            }
            containsMatches.size > 1 -> {
                // If one of them starts with or matches closely, pick it
                val topMatch = containsMatches.first()
                launchApp(topMatch.packageName)
                AppResolutionResult.Success(topMatch)
            }
            else -> AppResolutionResult.NotFound
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            if (intent != null) {
                context.startActivity(intent)
                Log.d(TAG, "Launched app package: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent found for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName: ${e.message}")
            false
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
            .replace("app", "")
            .trim()
    }
}
