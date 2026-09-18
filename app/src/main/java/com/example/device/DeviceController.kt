package com.example.device

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

class DeviceController(private val context: Context) {

    companion object {
        private const val TAG = "DeviceController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun adjustVolume(increase: Boolean): Boolean {
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust volume: ${e.message}")
            false
        }
    }

    fun muteVolume(): Boolean {
        return try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute volume: ${e.message}")
            false
        }
    }

    fun unmuteVolume(): Boolean {
        return try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unmute volume: ${e.message}")
            false
        }
    }

    fun dispatchMediaKeyEvent(keyCode: Int): Boolean {
        return try {
            val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(eventDown)
            audioManager?.dispatchMediaKeyEvent(eventUp)
            Log.d(TAG, "Dispatched media key event: $keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch media key event: ${e.message}")
            false
        }
    }

    fun setVolumePercentage(percent: Int): Boolean {
        return try {
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
            val target = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume percentage: ${e.message}")
            false
        }
    }

    fun findContactPhoneNumber(query: String): String? {
        val matches = searchContacts(query)
        return matches.firstOrNull()?.phoneNumber
    }

    fun openWifiSettings(): Boolean {
        return openIntent(Settings.ACTION_WIFI_SETTINGS)
    }

    fun openBluetoothSettings(): Boolean {
        return openIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    fun openDisplaySettings(): Boolean {
        return openIntent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    fun openAccessibilitySettings(): Boolean {
        return openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun openNotificationSettings(): Boolean {
        return openIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    fun searchContacts(query: String): List<ContactMatch> {
        val matches = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: ""
                    val number = it.getString(numberIndex) ?: ""
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        matches.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Contacts permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}")
        }

        return matches
    }

    fun initiateCall(phoneNumber: String, directCall: Boolean = false): Boolean {
        return try {
            val action = if (directCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call: ${e.message}")
            false
        }
    }

    fun sendSms(phoneNumber: String, messageText: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber")).apply {
                putExtra("sms_body", messageText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS composer: ${e.message}")
            false
        }
    }

    private fun openIntent(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings intent: $action, ${e.message}")
            false
        }
    }
}
