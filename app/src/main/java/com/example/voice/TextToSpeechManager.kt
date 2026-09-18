package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
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
        private const val UTTERANCE_PREFIX = "SIGMA_GEMINI_RESPONSE_"
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

    var pitch: Float = 0.90f // Calibrated deep, authoritative SIGMA AI tone
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    private var pendingSpeechOnReady: String? = null

    init {
        initializeTtsEngine()
    }

    private fun initializeTtsEngine() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureAudioAttributes()
                configureVoice()
                setupProgressListener()

                Log.d(TAG, "Android TextToSpeech engine initialized successfully.")
                
                // Flush pending speech if queued during startup
                pendingSpeechOnReady?.let { text ->
                    pendingSpeechOnReady = null
                    speak(text)
                }
            } else {
                Log.e(TAG, "Android TextToSpeech initialization failed with code: $status")
                isReady = false
            }
        }
    }

    private fun configureAudioAttributes() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set custom AudioAttributes: ${e.message}")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                mainHandler.post { onSpeakingStarted() }
                Log.d(TAG, "TTS started vocalizing: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post { onSpeakingFinished() }
                Log.d(TAG, "TTS finished vocalizing: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post { onSpeakingFinished() }
                Log.w(TAG, "TTS error on utterance: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                mainHandler.post { onSpeakingFinished() }
                Log.w(TAG, "TTS error code $errorCode on utterance: $utteranceId")
            }
        })
    }

    private fun configureVoice(locale: Locale = Locale.ENGLISH) {
        tts?.let { engine ->
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.US
            }
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            // Select natural male voice if available in system voices
            try {
                val voices = engine.voices
                if (!voices.isNullOrEmpty()) {
                    val maleVoice = voices.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.ROOT)
                        val voiceLoc = voice.locale
                        val matchesLang = voiceLoc.language.equals(locale.language, ignoreCase = true)
                        val isMale = name.contains("male") ||
                                name.contains("#male") ||
                                name.contains("-x-sfg") ||
                                name.contains("-x-iom") ||
                                name.contains("-x-cpc") ||
                                name.contains("-x-ahp") ||
                                name.contains("-x-rjs") ||
                                name.contains("en-us-x-sfg#male_1") ||
                                name.contains("en-in-x-cxx#male_1")
                        matchesLang && isMale && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.ROOT)
                        name.contains("male") && voice.locale.language.equals(locale.language, ignoreCase = true)
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals(locale.language, ignoreCase = true) && !voice.isNetworkConnectionRequired
                    }

                    if (maleVoice != null) {
                        engine.voice = maleVoice
                        Log.d(TAG, "Selected natural TTS voice: ${maleVoice.name}")
                    } else {
                        Log.d(TAG, "Applied calibrated voice parameters (Rate: $speechRate, Pitch: $pitch)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice selection fallback: ${e.message}")
            }
        }
    }

    /**
     * Vocalizes Gemini API generated response cleanly with Android TextToSpeech.
     */
    fun vocalizeGeminiResponse(rawAiResponse: String, flushQueue: Boolean = true) {
        if (rawAiResponse.isBlank()) return
        speak(rawAiResponse, flushQueue = flushQueue)
    }

    fun speak(text: String, flushQueue: Boolean = true) {
        if (text.isBlank()) return

        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready yet; queuing speech.")
            pendingSpeechOnReady = text
            return
        }

        val cleanText = formatTextForSpeech(text)
        if (cleanText.isBlank()) return

        // Auto-detect Hindi/Devanagari scripts
        val isHindi = containsDevanagari(cleanText)
        if (isHindi) {
            tts?.language = Locale.forLanguageTag("hi-IN")
        } else {
            tts?.language = Locale.ENGLISH
        }

        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "$UTTERANCE_PREFIX${System.currentTimeMillis()}")
        }

        tts?.speak(cleanText, queueMode, params, "$UTTERANCE_PREFIX${System.currentTimeMillis()}")
        Log.d(TAG, "Spoken via TTS: \"$cleanText\"")
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
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        isReady = false
    }

    /**
     * Cleans up markdown, links, symbols, code blocks, and formatting so Gemini's responses
     * sound natural and pleasant when vocalized by Android's TextToSpeech engine.
     */
    private fun formatTextForSpeech(raw: String): String {
        return raw.trim()
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            // Remove inline code
            .replace(Regex("`[^`]*`"), "")
            // Remove Markdown URLs [text](url) -> text
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            // Remove raw URLs
            .replace(Regex("https?://\\S+"), "")
            // Remove bold/italic markdown marks
            .replace("**", "")
            .replace("*", "")
            .replace("__", "")
            .replace("_", " ")
            .replace("~~", "")
            .replace("#", "")
            // Remove bullet points / list markers at beginnings of lines
            .replace(Regex("(?m)^\\s*[-*•]\\s+"), "")
            // Remove numbered list markers at beginnings of lines
            .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
            // Remove emojis & special symbolic characters that stutter speech
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            // Clean up multiple spaces and extra newlines into natural pauses
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsDevanagari(text: String): Boolean {
        for (char in text) {
            val block = Character.UnicodeBlock.of(char)
            if (block == Character.UnicodeBlock.DEVANAGARI) {
                return true
            }
        }
        return false
    }
}
