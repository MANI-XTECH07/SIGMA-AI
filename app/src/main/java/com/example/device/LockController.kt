package com.example.device

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.service.SigmaAccessibilityService

class LockController(private val context: Context) {

    companion object {
        private const val TAG = "LockController"
    }

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun getDeviceState(): DeviceState {
        val isScreenOn = powerManager?.isInteractive ?: true
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        val isDeviceSecure = keyguardManager?.isDeviceSecure ?: false

        val status = when {
            isLocked && isDeviceSecure -> DeviceStatus.AUTHENTICATION_REQUIRED
            isLocked -> DeviceStatus.DEVICE_LOCKED
            isScreenOn -> DeviceStatus.DEVICE_UNLOCKED
            else -> DeviceStatus.DEVICE_AWAKE
        }

        return DeviceState(
            status = status,
            isScreenOn = isScreenOn,
            isKeyguardLocked = isLocked,
            isDeviceSecure = isDeviceSecure
        )
    }

    /**
     * Real device lock using AccessibilityService's GLOBAL_ACTION_LOCK_SCREEN (Android 9 / API 28+).
     * Never simulates a fake lock screen.
     */
    fun lockPhone(): Boolean {
        val service = SigmaAccessibilityService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "Device lock action executed via accessibility: $locked")
            return locked
        } else {
            Log.w(TAG, "Cannot lock phone: Accessibility service inactive or API < 28")
            return false
        }
    }

    /**
     * Secure Android unlock flow.
     * Wakes the device and prompts Android's native Keyguard authentication (PIN/Pattern/Biometrics).
     * Strictly complies with security mandates: Never bypasses credentials or simulates unlock.
     */
    fun promptSecureUnlock(
        activity: Activity,
        onUnlocked: () -> Unit,
        onCancelled: () -> Unit
    ) {
        // 1. Wake screen if off
        wakeScreen()

        // 2. Request Android Native Keyguard Dismissal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(
                activity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        Log.d(TAG, "Android native unlock succeeded")
                        onUnlocked()
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                        Log.d(TAG, "Android native unlock cancelled by user")
                        onCancelled()
                    }

                    override fun onDismissError() {
                        super.onDismissError()
                        Log.e(TAG, "Android native keyguard dismiss error")
                        onCancelled()
                    }
                }
            )
        } else {
            // Older API fallback
            if (keyguardManager?.isKeyguardLocked == false) {
                onUnlocked()
            } else {
                onCancelled()
            }
        }
    }

    private fun wakeScreen() {
        try {
            powerManager?.let { pm ->
                if (!pm.isInteractive) {
                    @Suppress("DEPRECATION")
                    val wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "SIGMA:WakeLock"
                    )
                    wakeLock.acquire(3000L)
                    wakeLock.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }
}
