package com.example.voice

import android.content.Context
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

    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set
    var isSpeaking: Boolean = false
        private set

    var speechRate: Float = 1.05f
    var pitch: Float = 0.95f // Slightly deeper for natural male tone

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        onSpeakingStarted()
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        onSpeakingFinished()
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        onSpeakingFinished()
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
                val maleVoice = voices?.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    (name.contains("male") || name.contains("en-us-x-sfg") || name.contains("en-in-x-cpc")) &&
                            !voice.isNetworkConnectionRequired
                }
                if (maleVoice != null) {
                    engine.voice = maleVoice
                    Log.d(TAG, "Selected male voice: ${maleVoice.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not inspect voices: ${e.message}")
            }
        }
    }

    fun speak(text: String, flushQueue: Boolean = true) {
        if (!isReady || text.isBlank()) return

        // Short crisp response enforcement
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
            onSpeakingFinished()
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

    /**
     * Ensures SIGMA speaks crisp, executive voice feedback without narrating internal logs.
     */
    private fun formatCrispResponse(raw: String): String {
        return raw.trim()
            .replace("**", "")
            .replace("*", "")
            .take(180)
    }
}
