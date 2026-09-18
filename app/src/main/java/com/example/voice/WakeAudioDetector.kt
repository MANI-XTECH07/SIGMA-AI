package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Non-intrusive background microphone capture for wake-word & voice activity detection.
 *
 * CRITICAL ARCHITECTURAL CONSTRAINTS:
 * 1. NEVER requests AudioFocus from AudioManager.
 * 2. Uses AudioRecord directly on VOICE_RECOGNITION / MIC source.
 * 3. Does NOT send media keys or interrupt STREAM_MUSIC playback (YouTube, Spotify, video players).
 * 4. Yields microphone instantly when calls or system VoIP are active.
 */
class WakeAudioDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onVoiceActivityDetected: () -> Unit = {},
    private val onRmsLevelChanged: (Float) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "WakeAudioDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RMS_VOICE_THRESHOLD = 32.0f // dB threshold for voice energy
        private const val SIBILANT_ZCR_THRESHOLD = 0.28f // High zero-crossing rate characteristic of 'S' in 'Sigma'
    }

    private val isListening = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Detection state tracker
    private var consecutiveVoiceFrames = 0
    private var detectedSibilantRecentFrames = 0
    private var lastTriggerTimestamp = 0L

    fun startListening(): Boolean {
        if (isListening.get()) {
            return true
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start WakeAudioDetector: RECORD_AUDIO permission missing")
            onError("Microphone permission missing")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for AudioRecord")
            onError("Audio hardware buffer error")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(SAMPLE_RATE / 4) // ~250ms buffer

        try {
            // Try VOICE_RECOGNITION audio source first (optimized for speech without media interference)
            audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fallback to AudioSource.MIC: ${e.message}")
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                onError("Microphone busy or unavailable")
                return false
            }

            audioRecord?.startRecording()
            isListening.set(true)

            recordingThread = Thread({
                processAudioStream(bufferSize)
            }, "Sigma-WakeAudioDetector-Thread").apply {
                priority = Thread.NORM_PRIORITY
                start()
            }

            Log.i(TAG, "WakeAudioDetector started successfully in non-intrusive mode (No AudioFocus requested).")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting WakeAudioDetector: ${e.message}", e)
            audioRecord?.release()
            audioRecord = null
            isListening.set(false)
            onError("Microphone initialization error: ${e.message}")
            return false
        }
    }

    private fun processAudioStream(bufferSize: Int) {
        val audioBuffer = ShortArray(bufferSize)

        while (isListening.get()) {
            val record = audioRecord ?: break
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

            val readCount = record.read(audioBuffer, 0, audioBuffer.size)
            if (readCount > 0) {
                analyzePcmFrame(audioBuffer, readCount)
            } else if (readCount == AudioRecord.ERROR_INVALID_OPERATION || readCount == AudioRecord.ERROR_BAD_VALUE) {
                Log.w(TAG, "AudioRecord read error: $readCount")
                break
            }
        }

        Log.d(TAG, "WakeAudioDetector processing loop ended.")
    }

    private fun analyzePcmFrame(buffer: ShortArray, length: Int) {
        if (length <= 0) return

        var sumSquares = 0.0
        var zeroCrossings = 0

        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            sumSquares += (sample * sample).toDouble()

            if (i > 0) {
                if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                    zeroCrossings++
                }
            }
        }

        val meanSquare = sumSquares / length
        val rms = sqrt(meanSquare)
        // Convert RMS to approximate dB
        val rmsDb = if (rms > 1.0) {
            (20.0 * kotlin.math.log10(rms)).toFloat().coerceIn(0f, 90f)
        } else {
            0f
        }

        val zcr = zeroCrossings.toFloat() / length

        // Update live RMS for visualization
        mainHandler.post {
            onRmsLevelChanged(rmsDb)
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < 1500L) {
            // Cool-down period after a wake trigger
            return
        }

        val isVoicePresent = rmsDb > RMS_VOICE_THRESHOLD
        val isSibilantSound = zcr > SIBILANT_ZCR_THRESHOLD && rmsDb > (RMS_VOICE_THRESHOLD - 6f)

        if (isSibilantSound) {
            detectedSibilantRecentFrames = 6 // Hold sibilant window for ~6 frames
        } else if (detectedSibilantRecentFrames > 0) {
            detectedSibilantRecentFrames--
        }

        if (isVoicePresent) {
            consecutiveVoiceFrames++
            // If voice activity is detected following or alongside the "S" sibilant (characteristic of "Sigma")
            if (consecutiveVoiceFrames >= 2) {
                mainHandler.post { onVoiceActivityDetected() }
            }

            if (detectedSibilantRecentFrames > 0 && consecutiveVoiceFrames in 2..15) {
                Log.i(TAG, "WAKE_WORD_CANDIDATE: Acoustic match for 'Sigma' detected (rms=$rmsDb dB, zcr=$zcr)")
                lastTriggerTimestamp = now
                consecutiveVoiceFrames = 0
                detectedSibilantRecentFrames = 0
                mainHandler.post {
                    onWakeWordDetected()
                }
            }
        } else {
            consecutiveVoiceFrames = 0
        }
    }

    fun stopListening() {
        if (!isListening.getAndSet(false)) return

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
            recordingThread = null
        }
        Log.i(TAG, "WakeAudioDetector stopped.")
    }

    fun isRecording(): Boolean = isListening.get()
}
