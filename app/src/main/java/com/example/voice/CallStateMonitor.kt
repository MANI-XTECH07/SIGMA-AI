package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Monitors telephony and VoIP audio state to ensure SIGMA never disrupts phone or VoIP calls.
 */
class CallStateMonitor(
    private val context: Context,
    private val onCallStateChanged: (isInCall: Boolean) -> Unit
) {

    companion object {
        private const val TAG = "CallStateMonitor"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    var isInCall: Boolean = false
        private set

    fun start() {
        checkCurrentCallStatus()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Telephony state permission not available: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering CallStateMonitor: ${e.message}")
        }
    }

    fun isCallOrVoipActive(): Boolean {
        // Check Telephony call state
        val isTelephonyInCall = try {
            telephonyManager?.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (e: Exception) {
            false
        }

        // Check AudioManager VoIP communication mode (WhatsApp, Telegram, Meet, Zoom, etc.)
        val isVoipActive = audioManager?.let {
            it.mode == AudioManager.MODE_IN_CALL ||
            it.mode == AudioManager.MODE_IN_COMMUNICATION ||
            it.mode == AudioManager.MODE_CALL_SCREENING
        } ?: false

        return isTelephonyInCall || isVoipActive
    }

    private fun handleCallState(state: Int) {
        val active = state != TelephonyManager.CALL_STATE_IDLE || isCallOrVoipActive()
        if (isInCall != active) {
            isInCall = active
            Log.i(TAG, "CALL_STATE_CHANGED: isInCall = $isInCall")
            onCallStateChanged(isInCall)
        }
    }

    fun checkCurrentCallStatus() {
        val active = isCallOrVoipActive()
        if (isInCall != active) {
            isInCall = active
            onCallStateChanged(isInCall)
        }
    }

    fun stop() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering CallStateMonitor: ${e.message}")
        }
    }
}
