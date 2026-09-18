package com.example.automation

data class AppContext(
    val currentPackage: String,
    val currentActivity: String,
    val appName: String,
    val screenState: ScreenSnapshot,
    val availableActions: List<String> = emptyList()
)
