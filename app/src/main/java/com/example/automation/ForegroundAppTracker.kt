package com.example.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundAppTracker {
    private const val TAG = "ForegroundAppTracker"

    private val _currentPackage = MutableStateFlow("")
    val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

    private val _currentActivity = MutableStateFlow("")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

    @Volatile
    var lastTransitionTime: Long = System.currentTimeMillis()
        private set

    fun updateForeground(pkg: String, activity: String = "") {
        if (pkg.isBlank()) return
        val changed = _currentPackage.value != pkg || (_currentActivity.value != activity && activity.isNotBlank())
        _currentPackage.value = pkg
        if (activity.isNotBlank()) {
            _currentActivity.value = activity
        }
        if (changed) {
            lastTransitionTime = System.currentTimeMillis()
            Log.d(TAG, "Foreground updated: pkg=$pkg activity=${_currentActivity.value}")
        }
    }

    val currentPackageName: String
        get() = _currentPackage.value

    val currentActivityName: String
        get() = _currentActivity.value
}
