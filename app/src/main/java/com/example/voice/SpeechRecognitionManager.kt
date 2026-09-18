package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(
    private val context: Context,
    private val onFinalTextRecognized: (String) -> Unit,
    private val onPartialTranscript: (String) -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onStateChange: (Boolean) -> Unit = {}
) {

    companion object {
        private const val TAG = "SpeechRecManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                    Log.d(TAG, "SpeechRecognizer initialized successfully")
                } else {
                    Log.e(TAG, "Speech recognition is not available on this device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                this@SpeechRecognitionManager.onStateChange(true)
                Log.d(TAG, "SpeechRecognizer ready for speech")
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                this@SpeechRecognitionManager.onStateChange(true)
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                this@SpeechRecognitionManager.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                this@SpeechRecognitionManager.onStateChange(false)
                Log.d(TAG, "End of speech")
            }

            override fun onError(error: Int) {
                isListening = false
                this@SpeechRecognitionManager.onStateChange(false)
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection error"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, resetting..."
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    else -> "Speech recognition error ($error)"
                }
                Log.w(TAG, "Speech recognition error: $errorMsg ($error)")
                this@SpeechRecognitionManager.onError(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                this@SpeechRecognitionManager.onStateChange(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.trim() ?: ""
                if (spokenText.isNotEmpty()) {
                    Log.d(TAG, "Final Recognized: $spokenText")
                    this@SpeechRecognitionManager.onFinalTextRecognized(spokenText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Live streaming feedback for UI ONLY - NEVER execute from partial results
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    this@SpeechRecognitionManager.onPartialTranscript(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(languageLocale: String = "en-IN") {
        mainHandler.post {
            if (isListening) return@post

            if (speechRecognizer == null) {
                initRecognizer()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening: ${e.message}")
                isListening = false
                onStateChange(false)
                onError("Could not start microphone listener")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listening: ${e.message}")
            }
            isListening = false
            onStateChange(false)
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            isListening = false
            onStateChange(false)
        }
    }
}

