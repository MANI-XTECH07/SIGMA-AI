package com.example.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
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

        private val KNOWN_APP_PACKAGES = mapOf(
            "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
            "chrome" to listOf("com.android.chrome", "org.chromium.chrome"),
            "browser" to listOf("com.android.chrome", "com.google.android.browser"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "spotify" to listOf("com.spotify.music", "com.spotify.lite"),
            "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
            "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
            "gmail" to listOf("com.google.android.gm"),
            "mail" to listOf("com.google.android.gm", "com.android.email"),
            "maps" to listOf("com.google.android.apps.maps"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera"),
            "settings" to listOf("com.android.settings"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"),
            "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.android.gallery3d"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.android.gallery3d"),
            "contacts" to listOf("com.google.android.contacts", "com.android.contacts", "com.samsung.android.contacts"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
            "play store" to listOf("com.android.vending"),
            "playstore" to listOf("com.android.vending"),
            "drive" to listOf("com.google.android.apps.docs"),
            "files" to listOf("com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui")
        )
    }

    /**
     * Resolves app name by query and launches it if a single clear match is found.
     */
    fun resolveAndLaunch(appNameQuery: String): AppResolutionResult {
        val cleanQuery = sanitizeQuery(appNameQuery)
        val installed = repository.getInstalledApps()

        // 1. Check known packages first for high-confidence launcher
        val candidatePackages = KNOWN_APP_PACKAGES[cleanQuery] ?: KNOWN_APP_PACKAGES.entries.firstOrNull {
            cleanQuery.contains(it.key) || it.key.contains(cleanQuery)
        }?.value

        if (candidatePackages != null) {
            for (pkg in candidatePackages) {
                val foundApp = installed.firstOrNull { it.packageName == pkg }
                if (foundApp != null && launchApp(pkg)) {
                    return AppResolutionResult.Success(foundApp)
                }
                // Try direct system package manager launch
                if (launchApp(pkg)) {
                    val fallbackItem = AppItem(label = cleanQuery.replaceFirstChar { it.uppercase() }, packageName = pkg)
                    return AppResolutionResult.Success(fallbackItem)
                }
            }
        }

        // 2. Exact case-insensitive match on label
        val exactMatch = installed.firstOrNull { it.label.equals(cleanQuery, ignoreCase = true) }
        if (exactMatch != null && launchApp(exactMatch.packageName)) {
            return AppResolutionResult.Success(exactMatch)
        }

        // 3. Starts with query
        val startsWithMatches = installed.filter { it.label.startsWith(cleanQuery, ignoreCase = true) }
        if (startsWithMatches.isNotEmpty()) {
            val app = startsWithMatches.first()
            if (launchApp(app.packageName)) {
                return AppResolutionResult.Success(app)
            }
        }

        // 4. Contains query
        val containsMatches = installed.filter {
            it.label.contains(cleanQuery, ignoreCase = true) ||
            cleanQuery.contains(it.label, ignoreCase = true)
        }

        if (containsMatches.isNotEmpty()) {
            val app = containsMatches.first()
            if (launchApp(app.packageName)) {
                return AppResolutionResult.Success(app)
            }
        }

        // 5. System Intent fallbacks for generic intents (Camera, Settings, Browser)
        val genericFallback = tryLaunchGenericIntent(cleanQuery)
        if (genericFallback != null) {
            return AppResolutionResult.Success(genericFallback)
        }

        return AppResolutionResult.NotFound
    }

    private fun tryLaunchGenericIntent(cleanQuery: String): AppItem? {
        try {
            when {
                cleanQuery.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return AppItem(label = "Camera", packageName = "android.media.action.CAMERA")
                }
                cleanQuery.contains("setting") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return AppItem(label = "Settings", packageName = "com.android.settings")
                }
                cleanQuery.contains("browser") || cleanQuery.contains("web") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return AppItem(label = "Browser", packageName = "android.intent.action.VIEW")
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
            .replace("play", "")
            .replace("app", "")
            .replace("application", "")
            .trim()
    }
}

