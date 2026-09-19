package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SigmaNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isClearable: Boolean
)

class SigmaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SigmaNotifService"

        private val _recentNotifications = MutableStateFlow<List<SigmaNotificationItem>>(emptyList())
        val recentNotifications: StateFlow<List<SigmaNotificationItem>> = _recentNotifications.asStateFlow()

        var isConnected: Boolean = false
            private set

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledListeners.contains(context.packageName)) {
                return true
            }
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat?.contains(context.packageName) == true
        }

        fun openNotificationSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "SIGMA Notification Listener Connected")
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.i(TAG, "SIGMA Notification Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        handleNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        _recentNotifications.value = current
    }

    private fun handleNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Privacy filter: never store OTPs, passwords, or authentication codes
        val lower = "$title $text".lowercase()
        if (lower.contains("otp") || lower.contains("passcode") || lower.contains("verification code") || lower.contains("password")) {
            Log.d(TAG, "Skipping sensitive notification from ${sbn.packageName}")
            return
        }

        if (title.isBlank() && text.isBlank()) return

        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val item = SigmaNotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            isClearable = sbn.isClearable
        )

        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.key == item.key }
        current.add(0, item)
        // Keep last 30
        if (current.size > 30) {
            _recentNotifications.value = current.take(30)
        } else {
            _recentNotifications.value = current
        }
    }

    private fun refreshActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                handleNotificationPosted(sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh active notifications: ${e.message}")
        }
    }
}
