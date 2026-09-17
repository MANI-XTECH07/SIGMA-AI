package com.example.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
)

class InstalledAppRepository(private val context: Context) {

    companion object {
        private const val TAG = "InstalledAppRepo"
    }

    private var cachedApps: List<AppItem>? = null

    /**
     * Resolves all installed applications using Android's PackageManager.
     * Never hardcoded, fully dynamic.
     */
    fun getInstalledApps(forceRefresh: Boolean = false): List<AppItem> {
        if (!forceRefresh && cachedApps != null) {
            return cachedApps!!
        }

        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        val apps = mutableListOf<AppItem>()

        for (resolveInfo in resolveInfos) {
            try {
                val pkgName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val icon = resolveInfo.loadIcon(packageManager)

                if (label.isNotEmpty() && !apps.any { it.packageName == pkgName }) {
                    apps.add(
                        AppItem(
                            label = label,
                            packageName = pkgName,
                            icon = icon,
                            isSystemApp = isSystem
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving app: ${e.message}")
            }
        }

        apps.sortBy { it.label.lowercase() }
        cachedApps = apps
        Log.d(TAG, "Resolved ${apps.size} installed launchable applications dynamically.")
        return apps
    }
}
