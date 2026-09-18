package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.ActionPlanner
import com.example.ai.AiService
import com.example.ai.VisionService
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
import com.example.screen.MediaProjectionManager
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenCaptureManager
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaVoiceService
import com.example.ui.AppControlScreen
import com.example.ui.AutomationScreen
import com.example.ui.ContactsScreen
import com.example.ui.DiagnosticsScreen
import com.example.ui.HistoryScreen
import com.example.ui.LockScreenView
import com.example.ui.MainScreen
import com.example.ui.PermissionScreen
import com.example.ui.ScreenAnalyzerScreen
import com.example.ui.SettingsScreen
import com.example.ui.SidebarDrawer
import com.example.ui.SplashScreen
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTheme
import com.example.ui.theme.SigmaWhite
import com.example.voice.AssistantSessionState
import com.example.voice.SpeechRecognitionManager
import com.example.voice.TextToSpeechManager
import com.example.voice.VoiceSessionManager
import com.example.voice.WakeWordManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sigmaRepository: SigmaRepository
    private lateinit var conversationDao: ConversationDao

    private lateinit var deviceController: DeviceController
    private lateinit var torchController: TorchController
    private lateinit var lockController: LockController
    private lateinit var installedAppRepository: InstalledAppRepository
    private lateinit var appResolver: AppResolver

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var screenAnalysisManager: ScreenAnalysisManager

    private lateinit var screenObserver: ScreenObserver
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var actionVerifier: ActionVerifier
    private lateinit var recoveryEngine: RecoveryEngine
    private lateinit var automationEngine: AutomationEngine

    private lateinit var aiService: AiService
    private lateinit var visionService: VisionService
    private lateinit var actionPlanner: ActionPlanner

    private lateinit var actionRouter: ActionRouter

    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var textToSpeechManager: TextToSpeechManager
    private lateinit var wakeWordManager: WakeWordManager
    private val voiceSessionManager = VoiceSessionManager()

    private var lastSpokenCommand: String = ""
    private var currentExecutingAction: String = "Idle"
    private var lastRecordedError: String = "None"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SigmaDatabase.getInstance(this)
        sigmaRepository = SigmaRepository(db.sigmaDao())
        conversationDao = ConversationDao(db.sigmaDao())
        settingsRepository = SettingsRepository(this)

        deviceController = DeviceController(this)
        torchController = TorchController(this)
        lockController = LockController(this)
        installedAppRepository = InstalledAppRepository(this)
        appResolver = AppResolver(this, installedAppRepository)

        projectionManager = MediaProjectionManager(this)
        screenCaptureManager = ScreenCaptureManager(projectionManager)
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
        visionService = VisionService(screenCaptureManager, screenAnalysisManager, aiService)
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
            onSpeakingStarted = { voiceSessionManager.updateState(AssistantSessionState.SPEAKING) },
            onSpeakingFinished = { voiceSessionManager.updateState(AssistantSessionState.ONLINE) }
        ).apply {
            speechRate = settingsRepository.speechRate
            pitch = settingsRepository.speechPitch
        }

        speechRecognitionManager = SpeechRecognitionManager(
            context = this,
            onFinalTextRecognized = { text ->
                handleSpokenText(text)
            },
            onRmsChanged = { rms ->
                voiceSessionManager.updateRms(rms)
            },
            onError = { err ->
                lastRecordedError = err
                voiceSessionManager.updateState(AssistantSessionState.ERROR)
            },
            onStateChange = { isListening ->
                if (isListening) {
                    voiceSessionManager.updateState(AssistantSessionState.LISTENING)
                }
            }
        )

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPerm && settingsRepository.backgroundAssistantEnabled) {
            SigmaVoiceService.start(this)
        }

        setContent {
            SigmaTheme {
                SigmaRootApp()
            }
        }
    }

    private var activeSpeechText by mutableStateOf("")
    private var activeResponseText by mutableStateOf("")

    private fun handleSpokenText(text: String) {
        val (isWake, command) = wakeWordManager.processSpokenText(text)
        val query = if (isWake && command.isNotEmpty()) command else text

        activeSpeechText = query
        lastSpokenCommand = query
        currentExecutingAction = "Processing command: $query"
        voiceSessionManager.updateState(AssistantSessionState.THINKING)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                // Ensure speech rate and pitch are updated from repository
                textToSpeechManager.speechRate = settingsRepository.speechRate
                textToSpeechManager.pitch = settingsRepository.speechPitch

                val result = actionRouter.routeUserSpeech(query)

                when (result) {
                    is RouterResult.Spoken -> {
                        val speech = if (result.text.isNotEmpty()) {
                            result.text
                        } else {
                            val aiReplyResult = aiService.generateText(query)
                            aiReplyResult.getOrDefault("I have processed your request.")
                        }
                        activeResponseText = speech
                        currentExecutingAction = result.actionType ?: "Spoken Response"
                        voiceSessionManager.updateState(AssistantSessionState.SPEAKING)
                        textToSpeechManager.vocalizeGeminiResponse(speech)
                    }
                    is RouterResult.NeedConfirmation -> {
                        activeResponseText = result.description
                        currentExecutingAction = "Awaiting Confirmation: ${result.actionType}"
                        textToSpeechManager.speak("Confirmation needed: ${result.description}")
                    }
                }
            } catch (e: Exception) {
                lastRecordedError = e.message ?: "Unknown execution exception"
                currentExecutingAction = "Error during execution"
                activeResponseText = "An error occurred while processing the command."
                voiceSessionManager.updateState(AssistantSessionState.ERROR)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SigmaRootApp() {
        var showSplash by remember { mutableStateOf(true) }
        var currentDestination by remember { mutableStateOf("HOME") }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        var spokenDisplay by remember { mutableStateOf("") }
        var responseDisplay by remember { mutableStateOf("Say \"Hey Sigma\" or tap the mic to begin.") }
        var pendingConfirmation by remember { mutableStateOf<RouterResult.NeedConfirmation?>(null) }

        val sessionState by voiceSessionManager.sessionState.collectAsState()
        val liveRms by voiceSessionManager.liveRms.collectAsState()

        val serviceRunning by SigmaVoiceService.isServiceRunning.collectAsState()
        val serviceSessionState by SigmaVoiceService.sessionStateFlow.collectAsState()
        val serviceLiveRms by SigmaVoiceService.liveRmsFlow.collectAsState()
        val serviceLastTranscript by SigmaVoiceService.lastTranscriptFlow.collectAsState()
        val serviceLastResponse by SigmaVoiceService.lastResponseFlow.collectAsState()

        val effectiveSessionState = if (serviceRunning) serviceSessionState else sessionState
        val effectiveLiveRms = if (serviceRunning) serviceLiveRms else liveRms
        val effectiveTranscript = if (serviceRunning && serviceLastTranscript.isNotEmpty()) serviceLastTranscript else spokenDisplay.ifEmpty { activeSpeechText }
        val effectiveResponse = if (serviceRunning && serviceLastResponse.isNotEmpty()) serviceLastResponse else responseDisplay.ifEmpty { activeResponseText }

        val scope = rememberCoroutineScope()

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            if (perms[Manifest.permission.RECORD_AUDIO] == true) {
                speechRecognitionManager.startListening()
            }
        }

        val projectionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val granted = projectionManager.handleActivityResult(result.resultCode, result.data)
            if (granted) {
                Toast.makeText(this, "Screen capture active.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen sharing permission cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

        if (showSplash) {
            SplashScreen(
                onInitializationFinished = { showSplash = false }
            )
            return
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFF070406),
                    modifier = Modifier.width(300.dp)
                ) {
                    SidebarDrawer(
                        selectedRoute = currentDestination,
                        onNavigate = { route ->
                            currentDestination = route
                        },
                        onCloseDrawer = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                bottomBar = {
                    if (currentDestination != "LOCKSCREEN") {
                        NavigationBar(
                            containerColor = Color(0xFF0A0507),
                            tonalElevation = 8.dp
                        ) {
                            val bottomTabs = listOf(
                                Triple("HOME", "Home", Icons.Default.Home),
                                Triple("HISTORY", "History", Icons.Default.History),
                                Triple("DIAGNOSTICS", "Diagnostics", Icons.Default.SmartToy),
                                Triple("SETTINGS", "Settings", Icons.Default.Settings)
                            )

                            for ((dest, label, icon) in bottomTabs) {
                                val isSelected = currentDestination == dest
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentDestination = dest },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SigmaWhite,
                                        selectedTextColor = SigmaNeonRed,
                                        indicatorColor = Color(0xFF3B0B14),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (currentDestination) {
                        "HOME" -> MainScreen(
                            state = effectiveSessionState,
                            rmsLevel = effectiveLiveRms,
                            spokenText = effectiveTranscript,
                            responseText = effectiveResponse,
                            isListening = effectiveSessionState == AssistantSessionState.LISTENING,
                            animationQuality = settingsRepository.animationQuality,
                            reduceMotion = settingsRepository.isReduceMotion,
                            onMicClick = {
                                val hasAudio = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasAudio) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else {
                                    if (serviceRunning) {
                                        if (effectiveSessionState == AssistantSessionState.LISTENING) {
                                            SigmaVoiceService.instance?.stopListeningInternal()
                                        } else {
                                            SigmaVoiceService.triggerListen(this@MainActivity)
                                        }
                                    } else {
                                        if (sessionState == AssistantSessionState.LISTENING) {
                                            speechRecognitionManager.stopListening()
                                        } else {
                                            textToSpeechManager.stop()
                                            speechRecognitionManager.startListening()
                                        }
                                    }
                                }
                            },
                            onSendCommand = { cmd ->
                                spokenDisplay = cmd
                                if (serviceRunning) {
                                    SigmaVoiceService.instance?.handleIncomingSpokenText(cmd)
                                } else {
                                    handleSpokenText(cmd)
                                }
                            },
                            onQuickActionClick = { action ->
                                when (action) {
                                    "APPS" -> {
                                        currentDestination = "APPS"
                                    }
                                    "CONTACTS" -> {
                                        val hasContacts = ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.READ_CONTACTS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!hasContacts) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                        } else {
                                            currentDestination = "CONTACTS"
                                        }
                                    }
                                    "SCREEN" -> {
                                        currentDestination = "SCREEN"
                                    }
                                    "MORE" -> {
                                        scope.launch { drawerState.open() }
                                    }
                                }
                            },
                            onOpenSettings = {
                                currentDestination = "SETTINGS"
                            }
                        )
                        "HISTORY" -> HistoryScreen(
                            conversationDao = conversationDao,
                            onClearHistory = { scope.launch { conversationDao.clearHistory() } }
                        )
                        "DIAGNOSTICS" -> DiagnosticsScreen(
                            ttsManager = textToSpeechManager,
                            lastCommand = lastSpokenCommand,
                            currentAction = currentExecutingAction,
                            lastError = lastRecordedError
                        )
                        "SETTINGS" -> SettingsScreen(
                            settingsRepository = settingsRepository,
                            onBackgroundServiceToggled = { enabled ->
                                if (enabled) SigmaVoiceService.start(this@MainActivity)
                                else SigmaVoiceService.stop(this@MainActivity)
                            },
                            onTestVoice = { rate, pitch ->
                                textToSpeechManager.speechRate = rate
                                textToSpeechManager.pitch = pitch
                                textToSpeechManager.vocalizeGeminiResponse("SIGMA AI voice synthesizer calibrated and online.")
                            },
                            onNavigateBack = { currentDestination = "HOME" }
                        )
                        "AUTOMATION" -> AutomationScreen(
                            onInspectScreen = {
                                val info = screenAnalysisManager.analyzeCurrentScreen()
                                activeResponseText = info.summary
                                textToSpeechManager.speak(info.summary)
                            },
                            onExecuteAction = { act ->
                                scope.launch {
                                    when (act) {
                                        "HOME" -> actionExecutor.goHome()
                                        "BACK" -> actionExecutor.goBack()
                                        "SCROLL_DOWN" -> actionExecutor.scroll(true)
                                        "SCROLL_UP" -> actionExecutor.scroll(false)
                                        "LOCK_PHONE" -> {
                                             currentDestination = "LOCKSCREEN"
                                             actionExecutor.lockDevice()
                                        }
                                    }
                                }
                            },
                            onOpenAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        )
                        "SCREEN" -> ScreenAnalyzerScreen(
                            screenAnalysisManager = screenAnalysisManager,
                            onStartMediaProjection = {
                                val captureIntent = projectionManager.createScreenCaptureIntent()
                                if (captureIntent != null) {
                                    projectionLauncher.launch(captureIntent)
                                } else {
                                    Toast.makeText(this@MainActivity, "Screen capture unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAskAiAboutScreen = { summaryText ->
                                handleSpokenText("Analyze screen: $summaryText")
                            },
                            onNavigateBack = { currentDestination = "HOME" }
                        )
                        "CONTACTS" -> ContactsScreen(
                            deviceController = deviceController,
                            onCallContact = { phone ->
                                val hasCall = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CALL_PHONE
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasCall) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                } else {
                                    deviceController.initiateCall(phone, directCall = false)
                                }
                            },
                            onSmsContact = { phone ->
                                deviceController.sendSms(phone, "")
                            },
                            onNavigateBack = { currentDestination = "HOME" }
                        )
                        "APPS" -> AppControlScreen(
                            appRepository = installedAppRepository,
                            onLaunchApp = { pkg ->
                                appResolver.launchApp(pkg)
                            },
                            onNavigateBack = { currentDestination = "HOME" }
                        )
                        "LOCKSCREEN" -> LockScreenView(
                            onUnlockRequest = {
                                currentDestination = "HOME"
                            }
                        )
                        else -> MainScreen(
                            state = effectiveSessionState,
                            rmsLevel = effectiveLiveRms,
                            spokenText = effectiveTranscript,
                            responseText = effectiveResponse,
                            isListening = effectiveSessionState == AssistantSessionState.LISTENING,
                            animationQuality = settingsRepository.animationQuality,
                            reduceMotion = settingsRepository.isReduceMotion,
                            onMicClick = {
                                if (serviceRunning) {
                                    if (effectiveSessionState == AssistantSessionState.LISTENING) {
                                        SigmaVoiceService.instance?.stopListeningInternal()
                                    } else {
                                        SigmaVoiceService.triggerListen(this@MainActivity)
                                    }
                                } else {
                                    speechRecognitionManager.startListening()
                                }
                            },
                            onSendCommand = { cmd ->
                                if (serviceRunning) {
                                    SigmaVoiceService.instance?.handleIncomingSpokenText(cmd)
                                } else {
                                    handleSpokenText(cmd)
                                }
                            },
                            onQuickActionClick = { act ->
                                scope.launch { drawerState.open() }
                            }
                        )
                    }

                    // Confirmation Dialog for high-risk actions
                    pendingConfirmation?.let { conf ->
                        AlertDialog(
                            onDismissRequest = { pendingConfirmation = null },
                            title = { Text(conf.title, color = SigmaWhite, fontWeight = FontWeight.Bold) },
                            text = { Text(conf.description, color = SigmaWhite.copy(alpha = 0.8f)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val act = conf
                                        pendingConfirmation = null
                                        scope.launch {
                                            val res = act.onConfirm()
                                            if (res is RouterResult.Spoken) {
                                                textToSpeechManager.speak(res.text)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SigmaNeonRedBright)
                                ) {
                                    Text("Execute", color = SigmaWhite)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingConfirmation = null }) {
                                    Text("Cancel", color = Color.Gray)
                                }
                            },
                            containerColor = SigmaSurfaceBlack
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognitionManager.destroy()
        textToSpeechManager.shutdown()
        projectionManager.release()
    }
}
