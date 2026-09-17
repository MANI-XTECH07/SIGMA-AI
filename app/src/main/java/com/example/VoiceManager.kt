package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onStateChange: ((VoiceState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "VoiceManager"
        private const val UTTERANCE_ID = "SIGMA_TTS_UTTERANCE"
    }

    enum class VoiceState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        ERROR
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    var isListening: Boolean = false
        private set
    var isSpeaking: Boolean = false
        private set

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.US)
                }
                textToSpeech?.setPitch(1.05f)
                textToSpeech?.setSpeechRate(1.0f)
                isTtsInitialized = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TTS, status: $status")
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                mainHandler.post {
                    onStateChange?.invoke(VoiceState.SPEAKING)
                }
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post {
                    onStateChange?.invoke(VoiceState.IDLE)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                mainHandler.post {
                    onStateChange?.invoke(VoiceState.IDLE)
                }
            }
        })
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    ) {
        mainHandler.post {
            stopSpeaking()

            if (!isRecognitionAvailable()) {
                onError("Speech recognition is not available on this device.")
                onStateChange?.invoke(VoiceState.ERROR)
                return@post
            }

            destroyRecognizer()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            onStateChange?.invoke(VoiceState.LISTENING)
                        }

                        override fun onBeginningOfSpeech() {
                            isListening = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            onRmsChanged?.invoke(rmsdB)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val errorMessage = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                                SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, please try again"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                                SpeechRecognizer.ERROR_SERVER -> "Server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                                else -> "Recognition error ($error)"
                            }
                            Log.w(TAG, "Speech recognition error: $errorMessage")
                            mainHandler.post {
                                onError(errorMessage)
                                onStateChange?.invoke(VoiceState.ERROR)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognizedText = matches?.firstOrNull()?.trim()
                            if (!recognizedText.isNullOrEmpty()) {
                                mainHandler.post {
                                    onFinalResult(recognizedText)
                                }
                            } else {
                                mainHandler.post {
                                    onError("Could not understand voice command.")
                                    onStateChange?.invoke(VoiceState.ERROR)
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim()
                            if (!text.isNullOrEmpty()) {
                                mainHandler.post {
                                    onPartialResult(text)
                                }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                isListening = false
                onError("Failed to start voice listener: ${e.localizedMessage}")
                onStateChange?.invoke(VoiceState.ERROR)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping listening", e)
            }
            isListening = false
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        mainHandler.post {
            if (textToSpeech == null || !isTtsInitialized) {
                Log.w(TAG, "TTS not ready yet")
                return@post
            }
            stopListening()
            isSpeaking = true
            onStateChange?.invoke(VoiceState.SPEAKING)

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
            }

            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                if (textToSpeech?.isSpeaking == true) {
                    textToSpeech?.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping TTS", e)
            }
            isSpeaking = false
            onStateChange?.invoke(VoiceState.IDLE)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
    }

    fun shutdown() {
        mainHandler.post {
            destroyRecognizer()
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down TTS", e)
            }
            isListening = false
            isSpeaking = false
        }
    }
}
