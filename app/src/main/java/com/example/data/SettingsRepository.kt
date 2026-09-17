package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class AnimationQuality {
    LOW,
    MEDIUM,
    HIGH
}

class SettingsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "sigma_settings_prefs"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_BACKGROUND_SERVICE = "background_service_enabled"
        private const val KEY_ANIMATION_QUALITY = "animation_quality"
        private const val KEY_GLOW_INTENSITY = "glow_intensity"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_CONFIRM_SENSITIVE = "confirm_sensitive_actions"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.05f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var speechPitch: Float
        get() = prefs.getFloat(KEY_SPEECH_PITCH, 0.95f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_PITCH, value).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var isBackgroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE, value).apply()

    var animationQuality: AnimationQuality
        get() {
            val name = prefs.getString(KEY_ANIMATION_QUALITY, AnimationQuality.HIGH.name)
            return try {
                AnimationQuality.valueOf(name ?: AnimationQuality.HIGH.name)
            } catch (e: Exception) {
                AnimationQuality.HIGH
            }
        }
        set(value) = prefs.edit().putString(KEY_ANIMATION_QUALITY, value.name).apply()

    var glowIntensity: Float
        get() = prefs.getFloat(KEY_GLOW_INTENSITY, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_GLOW_INTENSITY, value).apply()

    var isReduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()

    var isConfirmSensitiveActions: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_SENSITIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_SENSITIVE, value).apply()

    var customApiKey: String
        get() = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_API_KEY, value).apply()
}
