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

        // Common well-known package aliases across Android OEMs (Samsung, Pixel, Xiaomi, OnePlus, Motorola, etc.)
        private val WELL_KNOWN_PACKAGES = mapOf(
            "music" to listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.google.android.youtube",
                "com.sec.android.app.music",
                "com.apple.android.music",
                "com.miui.player",
                "com.android.music",
                "com.amazon.mp3",
                "com.jio.media.jiobeats",
                "com.bsbportal.music",
                "com.soundcloud.android",
                "in.startv.hotstar"
            ),
            "music player" to listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.google.android.youtube",
                "com.sec.android.app.music",
                "com.apple.android.music",
                "com.miui.player",
                "com.android.music"
            ),
            "music_player" to listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.google.android.youtube",
                "com.sec.android.app.music",
                "com.apple.android.music",
                "com.miui.player",
                "com.android.music"
            ),
            "songs" to listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.google.android.youtube"
            ),
            "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
            "spotify" to listOf("com.spotify.music"),
            "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
            "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.opera.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser"),
            "web_browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.opera.browser", "com.microsoft.emmx"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "instagram" to listOf("com.instagram.android"),
            "maps" to listOf("com.google.android.apps.maps"),
            "play store" to listOf("com.android.vending"),
            "playstore" to listOf("com.android.vending"),
            "app store" to listOf("com.android.vending"),
            "gmail" to listOf("com.google.android.gm"),
            "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"),
            "photo_gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera"),
            "settings" to listOf("com.android.settings", "com.google.android.settings"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.miui.calculator"),
            "clock" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.coloros.alarm"),
            "calendar" to listOf("com.google.android.calendar", "com.samsung.android.calendar"),
            "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            "contacts" to listOf("com.google.android.contacts", "com.samsung.android.app.contacts"),
            "files" to listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.android.documentsui", "com.mi.android.globalFileexplorer"),
            "file_manager" to listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.android.documentsui", "com.mi.android.globalFileexplorer"),
            "drive" to listOf("com.google.android.apps.docs"),
            "recorder" to listOf("com.google.android.apps.recorder", "com.sec.android.app.voicenote"),
            "voice_recorder" to listOf("com.google.android.apps.recorder", "com.sec.android.app.voicenote")
        )
    }

    /**
     * Dynamically resolves app name by querying all installed launchable applications from PackageManager.
     */
    fun resolveAndLaunch(appNameQuery: String): AppResolutionResult {
        val cleanQuery = sanitizeQuery(appNameQuery)
        if (cleanQuery.isBlank()) {
            return AppResolutionResult.NotFound(appNameQuery)
        }

        val installedApps = repository.getInstalledApps()
        Log.d(TAG, "Resolving '$cleanQuery' (raw: '$appNameQuery') across ${installedApps.size} installed apps...")

        // 1. Direct Alias Check for popular and generic category names
        val aliasList = WELL_KNOWN_PACKAGES[cleanQuery] ?: WELL_KNOWN_PACKAGES[cleanQuery.replace(" ", "_")] ?: WELL_KNOWN_PACKAGES[cleanQuery.replace("_", " ")]
        if (aliasList != null) {
            for (pkg in aliasList) {
                val found = installedApps.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
                if (found != null && launchApp(found.packageName)) {
                    Log.d(TAG, "Launched via known alias: ${found.label} (${found.packageName})")
                    return AppResolutionResult.Success(found)
                }
                // Try direct package launch via PackageManager
                if (launchApp(pkg)) {
                    val directItem = AppItem(label = cleanQuery.replaceFirstChar { it.uppercase() }, packageName = pkg, isSystemApp = true)
                    return AppResolutionResult.Success(directItem)
                }
            }
        }

        // 2. Direct Exact Match on Label (Case-Insensitive)
        val exactMatch = installedApps.firstOrNull {
            it.label.trim().equals(cleanQuery, ignoreCase = true)
        }
        if (exactMatch != null && launchApp(exactMatch.packageName)) {
            Log.d(TAG, "Launched exact match: ${exactMatch.label}")
            return AppResolutionResult.Success(exactMatch)
        }

        // 3. Normalized alphanumeric match (ignores spaces, punctuation, e.g. "play store" == "playstore")
        val normalizedQuery = cleanQuery.replace(Regex("[^a-zA-Z0-9]"), "")
        val normalizedMatch = installedApps.firstOrNull {
            it.label.replace(Regex("[^a-zA-Z0-9]"), "").equals(normalizedQuery, ignoreCase = true)
        }
        if (normalizedMatch != null && launchApp(normalizedMatch.packageName)) {
            Log.d(TAG, "Launched normalized match: ${normalizedMatch.label}")
            return AppResolutionResult.Success(normalizedMatch)
        }

        // 4. Package Name Exact or Suffix Match
        val packageMatch = installedApps.firstOrNull {
            it.packageName.equals(cleanQuery, ignoreCase = true) ||
            it.packageName.endsWith(".$cleanQuery", ignoreCase = true)
        }
        if (packageMatch != null && launchApp(packageMatch.packageName)) {
            Log.d(TAG, "Launched package match: ${packageMatch.packageName}")
            return AppResolutionResult.Success(packageMatch)
        }

        // 5. Category-Specific Semantic Keyword Match in installed apps (e.g. "music" -> any app with "music" or "audio" or "player")
        if (cleanQuery.contains("music") || cleanQuery.contains("song") || cleanQuery.contains("audio")) {
            val musicApp = installedApps.firstOrNull {
                it.label.contains("music", ignoreCase = true) ||
                it.packageName.contains("music", ignoreCase = true) ||
                it.packageName.contains("spotify", ignoreCase = true) ||
                it.packageName.contains("audio", ignoreCase = true)
            }
            if (musicApp != null && launchApp(musicApp.packageName)) {
                Log.d(TAG, "Launched semantic music app: ${musicApp.label} (${musicApp.packageName})")
                return AppResolutionResult.Success(musicApp)
            }
        }

        // 6. Starts With Prefix Match on Label
        val startsWithMatches = installedApps.filter {
            it.label.trim().startsWith(cleanQuery, ignoreCase = true)
        }
        if (startsWithMatches.isNotEmpty()) {
            val app = startsWithMatches.first()
            if (launchApp(app.packageName)) {
                Log.d(TAG, "Launched prefix match: ${app.label}")
                return AppResolutionResult.Success(app)
            }
        }

        // 7. Word-boundary / Substring Match on Label
        val containsMatches = installedApps.filter {
            it.label.contains(cleanQuery, ignoreCase = true) ||
            cleanQuery.contains(it.label, ignoreCase = true)
        }
        if (containsMatches.isNotEmpty()) {
            val bestContains = containsMatches.minByOrNull { Math.abs(it.label.length - cleanQuery.length) }
            if (bestContains != null && launchApp(bestContains.packageName)) {
                Log.d(TAG, "Launched substring match: ${bestContains.label}")
                return AppResolutionResult.Success(bestContains)
            }
        }

        // 8. Fuzzy Match (Levenshtein Distance for Speech Recognition phonetics/typos)
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
            Log.d(TAG, "Launched fuzzy match: ${bestFuzzy.label}")
            return AppResolutionResult.Success(bestFuzzy)
        }

        // 9. System Generic Intent Fallbacks (Music, Camera, Settings, Web Browser)
        val genericFallback = tryLaunchGenericIntent(cleanQuery)
        if (genericFallback != null) {
            return AppResolutionResult.Success(genericFallback)
        }

        Log.w(TAG, "TARGET_PACKAGE NOT FOUND: '$cleanQuery' (raw: '$appNameQuery')")
        return AppResolutionResult.NotFound(appNameQuery)
    }

    private fun tryLaunchGenericIntent(query: String): AppItem? {
        try {
            when {
                query.contains("music") || query.contains("song") || query.contains("audio") || query.contains("player") -> {
                    // 1. Try Category App Music selector
                    val musicIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (musicIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(musicIntent)
                        Log.i(TAG, "Launched Music via CATEGORY_APP_MUSIC")
                        return AppItem(label = "Music Player", packageName = "android.intent.category.APP_MUSIC", isSystemApp = true)
                    }

                    // 2. Try MediaStore music player intent
                    @Suppress("DEPRECATION")
                    val mediaStoreIntent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    if (mediaStoreIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mediaStoreIntent)
                        Log.i(TAG, "Launched Music via INTENT_ACTION_MUSIC_PLAYER")
                        return AppItem(label = "Music Player", packageName = "android.media.action.MUSIC_PLAYER", isSystemApp = true)
                    }

                    // 3. Fallback: Try YouTube or Spotify directly
                    for (pkg in listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.youtube")) {
                        if (launchApp(pkg)) {
                            return AppItem(label = "Music Player", packageName = pkg, isSystemApp = true)
                        }
                    }
                }
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
                Log.i(TAG, "TARGET_PACKAGE LAUNCHED: $packageName")
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
            true
        }
    }

    private fun sanitizeQuery(raw: String): String {
        return raw.lowercase()
            .replace("_", " ") // converts music_player -> music player
            .replace("-", " ")
            .replace("hey sigma", "")
            .replace("sigma", "")
            .replace("open ", "")
            .replace("kholo", "")
            .replace("launch ", "")
            .replace("start ", "")
            .replace("chalao", "")
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
