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
import com.example.voice.SpeechRecognitionManager
import com.example.voice.TextToSpeechManager
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
            instance?.startListeningInternal() ?: start(context)
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

    private var isPaused = false
    private var restartListeningRunnable: Runnable? = null

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
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
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
                // Resume listening loop after speaking
                if (!isPaused && _isServiceRunning.value) {
                    scheduleRestartListening(400)
                }
            }
        ).apply {
            speechRate = settingsRepository.speechRate
            pitch = settingsRepository.speechPitch
        }

        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        speechRecognitionManager?.destroy()
        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            onFinalTextRecognized = { text ->
                handleIncomingSpokenText(text)
            },
            onPartialTranscript = { partial ->
                _lastTranscript.value = partial
            },
            onRmsChanged = { rms ->
                _liveRms.value = rms
            },
            onError = { err ->
                Log.d(TAG, "SpeechRec error in background: $err")
                _isListening.value = false
                if (!isPaused && _isServiceRunning.value && !_isSpeaking.value) {
                    // Automatically restart continuous listening
                    scheduleRestartListening(500)
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
                startListeningInternal()
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

    fun startListeningInternal() {
        if (isPaused) return
        mainHandler.post {
            cancelPendingRestart()
            // If TTS is currently speaking and user speaks or requests listen, interrupt TTS immediately
            if (_isSpeaking.value) {
                textToSpeechManager.stop()
                _isSpeaking.value = false
            }
            try {
                speechRecognitionManager?.startListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                scheduleRestartListening(1000)
            }
        }
    }

    fun stopListeningInternal() {
        mainHandler.post {
            cancelPendingRestart()
            speechRecognitionManager?.stopListening()
            _isListening.value = false
        }
    }

    fun pauseListening() {
        isPaused = true
        stopListeningInternal()
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
        stopListeningInternal()
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
        speechRecognitionManager?.destroy()
        textToSpeechManager.shutdown()
        releaseWakeLock()
        serviceScope.cancel()
        Log.d(TAG, "SigmaVoiceService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
