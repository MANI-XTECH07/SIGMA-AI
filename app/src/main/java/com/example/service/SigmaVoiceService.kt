package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.ActionPlanner
import com.example.ai.AiService
import com.example.apps.AppResolver
import com.example.apps.InstalledAppRepository
import com.example.automation.ActionExecutor
import com.example.automation.ActionVerifier
import com.example.automation.AutomationEngine
import com.example.automation.RecoveryEngine
import com.example.automation.ScreenObserver
import com.example.data.ConversationDao
import com.example.data.SettingsRepository
import com.example.data.db.SigmaDatabase
import com.example.data.db.SigmaRepository
import com.example.device.DeviceController
import com.example.device.LockController
import com.example.device.TorchController
import com.example.router.ActionRouter
import com.example.router.RouterResult
import com.example.screen.ScreenAnalysisManager
import com.example.voice.AssistantSessionState
import com.example.voice.CallStateMonitor
import com.example.voice.MicState
import com.example.voice.MicStateManager
import com.example.voice.SpeechRecognitionManager
import com.example.voice.TextToSpeechManager
import com.example.voice.WakeAudioDetector
import com.example.voice.WakeWordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SigmaVoiceService : Service() {

    companion object {
        private const val TAG = "SigmaVoiceService"
        private const val CHANNEL_ID = "sigma_voice_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_STOP = "com.example.service.action.STOP"
        const val ACTION_PAUSE = "com.example.service.action.PAUSE"
        const val ACTION_RESUME = "com.example.service.action.RESUME"
        const val ACTION_TRIGGER_LISTEN = "com.example.service.action.TRIGGER_LISTEN"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        val isRunning: Boolean
            get() = _isServiceRunning.value

        private val _isListening = MutableStateFlow(false)
        val isListeningFlow: StateFlow<Boolean> = _isListening.asStateFlow()

        private val _isSpeaking = MutableStateFlow(false)
        val isSpeakingFlow: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        private val _sessionState = MutableStateFlow(AssistantSessionState.ONLINE)
        val sessionStateFlow: StateFlow<AssistantSessionState> = _sessionState.asStateFlow()

        private val _liveRms = MutableStateFlow(0f)
        val liveRmsFlow: StateFlow<Float> = _liveRms.asStateFlow()

        private val _lastTranscript = MutableStateFlow("")
        val lastTranscriptFlow: StateFlow<String> = _lastTranscript.asStateFlow()

        private val _lastResponse = MutableStateFlow("")
        val lastResponseFlow: StateFlow<String> = _lastResponse.asStateFlow()

        private val _activeCommand = MutableStateFlow("")
        val activeCommandFlow: StateFlow<String> = _activeCommand.asStateFlow()

        private val micStateManager = MicStateManager()
        val micStateFlow: StateFlow<MicState> = micStateManager.currentState

        var instance: SigmaVoiceService? = null
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, SigmaVoiceService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service intent: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SigmaVoiceService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service intent: ${e.message}", e)
            }
        }

        fun triggerListen(context: Context) {
            instance?.triggerCommandListening() ?: start(context)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sigmaRepository: SigmaRepository
    private lateinit var conversationDao: ConversationDao

    private lateinit var deviceController: DeviceController
    private lateinit var torchController: TorchController
    private lateinit var lockController: LockController
    private lateinit var installedAppRepository: InstalledAppRepository
    private lateinit var appResolver: AppResolver

    private lateinit var screenAnalysisManager: ScreenAnalysisManager
    private lateinit var screenObserver: ScreenObserver
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var actionVerifier: ActionVerifier
    private lateinit var recoveryEngine: RecoveryEngine
    private lateinit var automationEngine: AutomationEngine

    private lateinit var aiService: AiService
    private lateinit var actionPlanner: ActionPlanner
    private lateinit var actionRouter: ActionRouter

    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var textToSpeechManager: TextToSpeechManager
    private var speechRecognitionManager: SpeechRecognitionManager? = null
    private var wakeAudioDetector: WakeAudioDetector? = null
    private var callStateMonitor: CallStateMonitor? = null

    private var isPaused = false
    private var restartListeningRunnable: Runnable? = null
    private var commandListenTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        acquireWakeLock()
        initDependencies()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Sigma::BackgroundVoiceLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire partial wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
    }

    private fun initDependencies() {
        val db = SigmaDatabase.getInstance(this)
        sigmaRepository = SigmaRepository(db.sigmaDao())
        conversationDao = ConversationDao(db.sigmaDao())
        settingsRepository = SettingsRepository(this)

        deviceController = DeviceController(this)
        torchController = TorchController(this)
        lockController = LockController(this)
        installedAppRepository = InstalledAppRepository(this)
        appResolver = AppResolver(this, installedAppRepository)

        screenAnalysisManager = ScreenAnalysisManager()
        screenObserver = ScreenObserver(screenAnalysisManager)
        actionExecutor = ActionExecutor(lockController)
        actionVerifier = ActionVerifier(screenObserver)
        recoveryEngine = RecoveryEngine(actionExecutor, screenObserver)
        automationEngine = AutomationEngine(
            appResolver,
            actionExecutor,
            screenObserver,
            actionVerifier,
            recoveryEngine,
            deviceController
        )

        aiService = AiService(settingsRepository.customApiKey)
        actionPlanner = ActionPlanner(aiService)

        actionRouter = ActionRouter(
            context = this,
            appResolver = appResolver,
            deviceController = deviceController,
            torchController = torchController,
            screenAnalysisManager = screenAnalysisManager,
            actionPlanner = actionPlanner,
            automationEngine = automationEngine,
            aiProvider = aiService,
            repository = sigmaRepository,
            conversationDao = conversationDao
        )

        wakeWordManager = WakeWordManager()

        textToSpeechManager = TextToSpeechManager(
            context = this,
            onSpeakingStarted = {
                _isSpeaking.value = true
                _sessionState.value = AssistantSessionState.SPEAKING
            },
            onSpeakingFinished = {
                _isSpeaking.value = false
                _sessionState.value = AssistantSessionState.ONLINE
                // Resume non-intrusive wake listening loop after speaking
                if (!isPaused && _isServiceRunning.value) {
                    scheduleRestartListening(300)
                }
            }
        ).apply {
            speechRate = settingsRepository.speechRate
            pitch = settingsRepository.speechPitch
        }

        initSpeechRecognizer()
        initWakeAudioDetector()
        initCallStateMonitor()
    }

    private fun initCallStateMonitor() {
        callStateMonitor = CallStateMonitor(this) { inCall ->
            if (inCall) {
                Log.i(TAG, "Call or VoIP active. Suspending microphone listening to protect call audio.")
                micStateManager.transitionTo(MicState.MIC_SUSPENDED)
                stopAllMicCapture()
                _sessionState.value = AssistantSessionState.SLEEP
            } else {
                Log.i(TAG, "Call ended. Resuming background wake listening.")
                micStateManager.transitionTo(MicState.MIC_IDLE)
                if (!isPaused && _isServiceRunning.value) {
                    scheduleRestartListening(500)
                }
            }
        }
        callStateMonitor?.start()
    }

    private fun initWakeAudioDetector() {
        wakeAudioDetector?.stopListening()
        wakeAudioDetector = WakeAudioDetector(
            context = this,
            onWakeWordDetected = {
                Log.i(TAG, "WAKE_WORD_TRIGGERED: Transitioning from wake detection to command capture.")
                triggerCommandListening()
            },
            onVoiceActivityDetected = {
                // Voice energy detected
            },
            onRmsLevelChanged = { rms ->
                _liveRms.value = rms
            },
            onError = { err ->
                Log.w(TAG, "WakeAudioDetector error: $err")
                micStateManager.transitionTo(MicState.MIC_ERROR, err)
                if (!isPaused && _isServiceRunning.value && !_isSpeaking.value) {
                    scheduleRestartListening(micStateManager.getBackoffDelayMs())
                }
            }
        )
    }

    private fun initSpeechRecognizer() {
        speechRecognitionManager?.destroy()
        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            onFinalTextRecognized = { text ->
                cancelCommandTimeout()
                handleIncomingSpokenText(text)
            },
            onPartialTranscript = { partial ->
                _lastTranscript.value = partial
            },
            onRmsChanged = { rms ->
                _liveRms.value = rms
            },
            onError = { err ->
                Log.d(TAG, "SpeechRec error/timeout: $err")
                cancelCommandTimeout()
                _isListening.value = false
                // If speech recognizer failed or timed out, revert to non-intrusive wake listener
                if (!isPaused && _isServiceRunning.value && !_isSpeaking.value) {
                    scheduleRestartListening(400)
                }
            },
            onStateChange = { listening ->
                _isListening.value = listening
                if (listening) {
                    _sessionState.value = AssistantSessionState.LISTENING
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseListening()
                updateNotification()
            }
            ACTION_RESUME -> {
                resumeListening()
                updateNotification()
            }
            ACTION_TRIGGER_LISTEN -> {
                triggerCommandListening()
            }
            else -> {
                startForegroundWithNotification()
                if (!isPaused) {
                    startListeningInternal()
                }
            }
        }
        return START_STICKY
    }

    /**
     * Starts the non-intrusive background wake detector.
     * YouTube, Spotify, and media playback remain completely unaffected.
     */
    fun startListeningInternal() {
        if (isPaused) return
        if (callStateMonitor?.isCallOrVoipActive() == true) {
            micStateManager.transitionTo(MicState.MIC_SUSPENDED)
            return
        }

        mainHandler.post {
            cancelPendingRestart()
            if (_isSpeaking.value) return@post

            speechRecognitionManager?.stopListening()
            _isListening.value = false

            val started = wakeAudioDetector?.startListening() ?: false
            if (started) {
                micStateManager.transitionTo(MicState.MIC_WAKE_LISTENING)
                _sessionState.value = AssistantSessionState.ONLINE
                Log.d(TAG, "Non-intrusive WakeAudioDetector active in background.")
            }
        }
    }

    /**
     * Triggered either via acoustic wake word or user action button.
     * Transitions from passive wake detection to active speech recognition.
     */
    fun triggerCommandListening() {
        if (isPaused) return
        if (callStateMonitor?.isCallOrVoipActive() == true) {
            micStateManager.transitionTo(MicState.MIC_SUSPENDED)
            return
        }

        mainHandler.post {
            cancelPendingRestart()
            // Stop TTS immediately if active
            if (_isSpeaking.value) {
                textToSpeechManager.stop()
                _isSpeaking.value = false
            }

            // Stop passive AudioRecord before starting SpeechRecognizer
            wakeAudioDetector?.stopListening()

            micStateManager.transitionTo(MicState.MIC_COMMAND_LISTENING)
            _sessionState.value = AssistantSessionState.LISTENING
            _isListening.value = true

            try {
                speechRecognitionManager?.startListening()
                // Set safety timeout of 7 seconds for command listening
                startCommandTimeout(7000L)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                scheduleRestartListening(1000)
            }
        }
    }

    private fun startCommandTimeout(timeoutMs: Long) {
        cancelCommandTimeout()
        commandListenTimeoutRunnable = Runnable {
            Log.d(TAG, "Command listening timed out, resuming passive wake listener")
            speechRecognitionManager?.stopListening()
            _isListening.value = false
            if (!isPaused && _isServiceRunning.value && !_isSpeaking.value) {
                startListeningInternal()
            }
        }
        mainHandler.postDelayed(commandListenTimeoutRunnable!!, timeoutMs)
    }

    private fun cancelCommandTimeout() {
        commandListenTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            commandListenTimeoutRunnable = null
        }
    }

    fun stopListeningInternal() {
        mainHandler.post {
            cancelPendingRestart()
            cancelCommandTimeout()
            stopAllMicCapture()
        }
    }

    private fun stopAllMicCapture() {
        wakeAudioDetector?.stopListening()
        speechRecognitionManager?.stopListening()
        _isListening.value = false
    }

    fun pauseListening() {
        isPaused = true
        stopListeningInternal()
        micStateManager.transitionTo(MicState.MIC_IDLE)
        _sessionState.value = AssistantSessionState.SLEEP
    }

    fun resumeListening() {
        isPaused = false
        _sessionState.value = AssistantSessionState.ONLINE
        startListeningInternal()
    }

    private fun scheduleRestartListening(delayMs: Long) {
        cancelPendingRestart()
        restartListeningRunnable = Runnable {
            if (!isPaused && _isServiceRunning.value && !_isSpeaking.value) {
                startListeningInternal()
            }
        }
        mainHandler.postDelayed(restartListeningRunnable!!, delayMs)
    }

    private fun cancelPendingRestart() {
        restartListeningRunnable?.let {
            mainHandler.removeCallbacks(it)
            restartListeningRunnable = null
        }
    }

    fun handleIncomingSpokenText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            scheduleRestartListening(300)
            return
        }

        _lastTranscript.value = trimmed

        // Interrupt any lingering TTS
        if (_isSpeaking.value) {
            textToSpeechManager.stop()
            _isSpeaking.value = false
        }

        val (isWake, command) = wakeWordManager.processSpokenText(trimmed)
        val query = if (isWake && command.isNotEmpty()) command else trimmed

        _activeCommand.value = query
        _sessionState.value = AssistantSessionState.THINKING

        serviceScope.launch {
            try {
                // Keep TTS settings synchronized
                textToSpeechManager.speechRate = settingsRepository.speechRate
                textToSpeechManager.pitch = settingsRepository.speechPitch

                val result = actionRouter.routeUserSpeech(query)
                when (result) {
                    is RouterResult.Spoken -> {
                        val speech = if (result.text.isNotEmpty()) {
                            result.text
                        } else {
                            val aiReplyResult = aiService.generateText(query)
                            aiReplyResult.getOrDefault("Command processed.")
                        }

                        _lastResponse.value = speech
                        _sessionState.value = AssistantSessionState.SPEAKING
                        textToSpeechManager.speak(speech)
                    }
                    is RouterResult.NeedConfirmation -> {
                        val confirmPrompt = "Please confirm: ${result.description}"
                        _lastResponse.value = confirmPrompt
                        _sessionState.value = AssistantSessionState.SPEAKING
                        textToSpeechManager.speak(confirmPrompt)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action in background: ${e.message}", e)
                val errMsg = "Action encountered an error: ${e.localizedMessage ?: "Unknown"}"
                _lastResponse.value = errMsg
                _sessionState.value = AssistantSessionState.ERROR
                textToSpeechManager.speak(errMsg)
            }
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMic = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                var started = false
                if (hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                        started = true
                    } catch (se: SecurityException) {
                        Log.w(TAG, "Microphone FGS type rejected, trying DATA_SYNC fallback: ${se.message}")
                    }
                }

                if (!started) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isServiceRunning.value = true
            _sessionState.value = AssistantSessionState.ONLINE
            Log.d(TAG, "SigmaVoiceService successfully running in foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            _isServiceRunning.value = false
            stopSelf()
        }
    }

    private fun updateNotification() {
        if (!_isServiceRunning.value) return
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, SigmaVoiceService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this,
                2,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            )
        } else {
            val pauseIntent = Intent(this, SigmaVoiceService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                3,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        }

        val stopIntent = Intent(this, SigmaVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (isPaused) {
            "Paused • Tap Resume to listen for \"Sigma\""
        } else {
            "Active • Listening hands-free for \"Hey Sigma\""
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIGMA Voice Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForegroundService() {
        _isServiceRunning.value = false
        _isListening.value = false
        _isSpeaking.value = false
        cancelPendingRestart()
        cancelCommandTimeout()
        stopAllMicCapture()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        Log.d(TAG, "SigmaVoiceService stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SIGMA Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SIGMA ready for hands-free voice commands in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        cancelPendingRestart()
        cancelCommandTimeout()
        callStateMonitor?.stop()
        wakeAudioDetector?.stopListening()
        speechRecognitionManager?.destroy()
        textToSpeechManager.shutdown()
        releaseWakeLock()
        serviceScope.cancel()
        Log.d(TAG, "SigmaVoiceService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
