package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager as AndroidMediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.router.ActionRouter
import com.example.router.RouterResult
import com.example.screen.MediaProjectionManager
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenCaptureManager
import com.example.service.SigmaVoiceService
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.example.ui.AutomationScreen
import com.example.ui.HistoryScreen
import com.example.ui.MainScreen
import com.example.ui.PermissionScreen
import com.example.ui.SettingsScreen
import com.example.ui.LockScreenView
import com.example.ui.SidebarDrawer
import com.example.ui.SplashScreen
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaDeepBlack
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sigmaRepository: SigmaRepository
    private lateinit var conversationDao: ConversationDao

    private lateinit var deviceController: DeviceController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SigmaDatabase.getInstance(this)
        sigmaRepository = SigmaRepository(db.sigmaDao())
        conversationDao = ConversationDao(db.sigmaDao())
        settingsRepository = SettingsRepository(this)

        deviceController = DeviceController(this)
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
            this,
            sigmaRepository,
            conversationDao,
            appResolver,
            deviceController,
            lockController,
            automationEngine,
            actionPlanner,
            visionService
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
            onTextRecognized = { text ->
                handleSpokenText(text)
            },
            onRmsChanged = { rms ->
                voiceSessionManager.updateRms(rms)
            },
            onError = { err ->
                voiceSessionManager.updateState(AssistantSessionState.ERROR)
            },
            onStateChange = { isListening ->
                if (isListening) {
                    voiceSessionManager.updateState(AssistantSessionState.LISTENING)
                }
            }
        )

        setContent {
            SigmaTheme {
                SigmaRootApp()
            }
        }
    }

    private var activeSpeechText = ""
    private var activeResponseText = ""

    private fun handleSpokenText(text: String) {
        val (isWake, command) = wakeWordManager.processSpokenText(text)
        val query = if (isWake && command.isNotEmpty()) command else text

        activeSpeechText = query
        voiceSessionManager.updateState(AssistantSessionState.THINKING)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val metrics = resources.displayMetrics
            val result = actionRouter.executeCommand(query, this@MainActivity, metrics)

            when (result) {
                is RouterResult.Spoken -> {
                    val speech = result.speechText.ifEmpty {
                        val aiReply = aiService.generateText(query).getOrDefault("Done.")
                        aiReply
                    }
                    activeResponseText = speech
                    voiceSessionManager.updateState(AssistantSessionState.SPEAKING)
                    textToSpeechManager.speak(speech)
                }
                is RouterResult.NeedConfirmation -> {
                    activeResponseText = result.description
                    textToSpeechManager.speak("Confirmation needed: ${result.description}")
                }
                is RouterResult.ContentPreview -> {
                    activeResponseText = result.text
                    textToSpeechManager.speak("Preview generated.")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SigmaRootApp() {
        var showSplash by remember { mutableStateOf(true) }
        var currentDestination by remember { mutableStateOf("HOME") }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val sessionState by voiceSessionManager.sessionState.collectAsState()
        val liveRms by voiceSessionManager.liveRms.collectAsState()
        val scope = rememberCoroutineScope()

        var spokenDisplay by remember { mutableStateOf("") }
        var responseDisplay by remember { mutableStateOf("Say \"Hey Sigma\" or tap the mic to begin.") }
        var pendingConfirmation by remember { mutableStateOf<RouterResult.NeedConfirmation?>(null) }

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
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionManager.handleActivityResult(result.resultCode, data)
                Toast.makeText(this, "Screen capture initialized.", Toast.LENGTH_SHORT).show()
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
                            state = sessionState,
                            rmsLevel = liveRms,
                            spokenText = spokenDisplay.ifEmpty { activeSpeechText },
                            responseText = responseDisplay.ifEmpty { activeResponseText },
                            isListening = sessionState == AssistantSessionState.LISTENING,
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
                                    if (sessionState == AssistantSessionState.LISTENING) {
                                        speechRecognitionManager.stopListening()
                                    } else {
                                        textToSpeechManager.stop()
                                        speechRecognitionManager.startListening()
                                    }
                                }
                            },
                            onSendCommand = { cmd ->
                                spokenDisplay = cmd
                                handleSpokenText(cmd)
                            },
                            onQuickActionClick = { action ->
                                when (action) {
                                    "APPS" -> {
                                        currentDestination = "AUTOMATION"
                                    }
                                    "CONTACTS" -> {
                                        val hasContacts = ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.READ_CONTACTS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!hasContacts) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                        } else {
                                            handleSpokenText("open contacts")
                                        }
                                    }
                                    "SCREEN" -> {
                                        val sysMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as AndroidMediaProjectionManager
                                        projectionLauncher.launch(sysMgr.createScreenCaptureIntent())
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
                        "SETTINGS" -> SettingsScreen(
                            settingsRepository = settingsRepository,
                            onBackgroundServiceToggled = { enabled ->
                                if (enabled) SigmaVoiceService.start(this@MainActivity)
                                else SigmaVoiceService.stop(this@MainActivity)
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
                        "LOCKSCREEN" -> LockScreenView(
                            onUnlockRequest = {
                                currentDestination = "HOME"
                            }
                        )
                        else -> MainScreen(
                            state = sessionState,
                            rmsLevel = liveRms,
                            spokenText = spokenDisplay.ifEmpty { activeSpeechText },
                            responseText = responseDisplay.ifEmpty { activeResponseText },
                            isListening = sessionState == AssistantSessionState.LISTENING,
                            animationQuality = settingsRepository.animationQuality,
                            reduceMotion = settingsRepository.isReduceMotion,
                            onMicClick = {
                                speechRecognitionManager.startListening()
                            },
                            onSendCommand = { cmd ->
                                handleSpokenText(cmd)
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
                                                textToSpeechManager.speak(res.speechText)
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
    }
}
