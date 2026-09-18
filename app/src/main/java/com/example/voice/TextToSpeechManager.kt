package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TextToSpeechManager(
    private val context: Context,
    private val onSpeakingStarted: () -> Unit = {},
    private val onSpeakingFinished: () -> Unit = {}
) {

    companion object {
        private const val TAG = "TextToSpeechManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set
    var isSpeaking: Boolean = false
        private set

    var speechRate: Float = 1.05f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 0.85f // Calibrated for deep, masculine SIGMA tone
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        mainHandler.post { onSpeakingStarted() }
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        mainHandler.post { onSpeakingFinished() }
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        mainHandler.post { onSpeakingFinished() }
                    }
                })
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with code: $status")
            }
        }
    }

    private fun configureVoice() {
        tts?.let { engine ->
            engine.language = Locale.ENGLISH
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            // Select natural male voice if available in system voices
            try {
                val voices = engine.voices
                if (voices != null && voices.isNotEmpty()) {
                    // Look for known male identifiers across Google TTS, Samsung TTS, and AOSP
                    val maleVoice = voices.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.ROOT)
                        val locale = voice.locale
                        val isEnglish = locale.language.startsWith("en") || locale.language.startsWith("hi")
                        val isMale = name.contains("male") ||
                                name.contains("#male") ||
                                name.contains("-x-sfg") ||
                                name.contains("-x-iom") ||
                                name.contains("-x-cpc") ||
                                name.contains("-x-ahp") ||
                                name.contains("-x-rjs") ||
                                name.contains("en-us-x-sfg#male_1") ||
                                name.contains("en-in-x-cxx#male_1")
                        isEnglish && isMale && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.ROOT)
                        name.contains("male")
                    }

                    if (maleVoice != null) {
                        engine.voice = maleVoice
                        Log.d(TAG, "Selected male voice: ${maleVoice.name}")
                    } else {
                        Log.d(TAG, "No explicit male voice identifier found, applying deep pitch calibration ($pitch)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not inspect voices: ${e.message}")
            }
        }
    }

    fun speak(text: String, flushQueue: Boolean = true) {
        if (!isReady || text.isBlank()) return

        val cleanText = formatCrispResponse(text)
        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(cleanText, queueMode, null, "SIGMA_UTTERANCE_${System.currentTimeMillis()}")
    }

    fun stop() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
            isSpeaking = false
            mainHandler.post { onSpeakingFinished() }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun formatCrispResponse(raw: String): String {
        return raw.trim()
            .replace("**", "")
            .replace("*", "")
            .replace("`", "")
            .replace("#", "")
            .take(240)
    }
}

