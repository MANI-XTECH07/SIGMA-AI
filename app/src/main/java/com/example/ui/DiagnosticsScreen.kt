package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.AiService
import com.example.apps.AppResolutionResult
import com.example.apps.AppResolver
import com.example.apps.InstalledAppRepository
import com.example.data.SettingsRepository
import com.example.screen.ScreenAnalysisManager
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaScreenCaptureService
import com.example.service.SigmaVoiceService
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaStatusError
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticTestResult(
    val testName: String,
    val isRunning: Boolean = false,
    val status: String = "NOT_TESTED",
    val isPass: Boolean? = null,
    val details: String = ""
)

@Composable
fun DiagnosticsScreen(
    ttsManager: TextToSpeechManager?,
    lastCommand: String,
    currentAction: String,
    lastError: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenAnalysisManager = remember { ScreenAnalysisManager() }
    val installedAppRepository = remember { InstalledAppRepository(context) }
    val appResolver = remember { AppResolver(context, installedAppRepository) }
    val settingsRepository = remember { SettingsRepository(context) }
    val aiService = remember { AiService(settingsRepository.customApiKey) }

    var refreshTrigger by remember { mutableStateOf(0) }
    var screenSummary by remember { mutableStateOf("Analyzing...") }
    var isAccessibilityRunning by remember { mutableStateOf(false) }
    var currentForegroundPackage by remember { mutableStateOf("") }
    var hasMicPermission by remember { mutableStateOf(false) }
    var isVoiceServiceRunning by remember { mutableStateOf(false) }
    var isScreenCaptureRunning by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Test states
    var testAppLaunchResult by remember { mutableStateOf(DiagnosticTestResult("App Launch")) }
    var testAccessibilityResult by remember { mutableStateOf(DiagnosticTestResult("Accessibility Binding")) }
    var testNodeDumpResult by remember { mutableStateOf(DiagnosticTestResult("Screen Node Dump")) }
    var testTapResult by remember { mutableStateOf(DiagnosticTestResult("Tap Gesture Dispatch")) }
    var testTextInputResult by remember { mutableStateOf(DiagnosticTestResult("Text Input (ACTION_SET_TEXT)")) }
    var testScrollResult by remember { mutableStateOf(DiagnosticTestResult("Scroll Action")) }
    var testTtsResult by remember { mutableStateOf(DiagnosticTestResult("TTS Audio Output")) }
    var testGeminiResult by remember { mutableStateOf(DiagnosticTestResult("Gemini AI API")) }

    LaunchedEffect(refreshTrigger) {
        isAccessibilityRunning = SigmaAccessibilityService.isServiceRunning
        currentForegroundPackage = SigmaAccessibilityService.currentPackage.ifEmpty { context.packageName }
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isVoiceServiceRunning = SigmaVoiceService.isRunning
        isScreenCaptureRunning = SigmaScreenCaptureService.isServiceRunning
        isTtsReady = ttsManager?.isReady ?: false

        val analysis = screenAnalysisManager.analyzeCurrentScreen()
        screenSummary = analysis.summary
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DIAGNOSTICS & RUNTIME SUITE",
                    color = SigmaWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Real physical device subsystem verification",
                    color = SigmaNeonRed,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = SigmaNeonRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subsystems Status
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticItemRow(
                label = "ACCESSIBILITY SERVICE",
                status = if (isAccessibilityRunning) "CONNECTED" else "DISCONNECTED",
                isOk = isAccessibilityRunning,
                detail = if (isAccessibilityRunning) "Active Window Inspection & Gesture Dispatch Bound" else "Service is not enabled in Android Accessibility Settings",
                actionLabel = if (!isAccessibilityRunning) "Open Settings" else null,
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            )

            DiagnosticItemRow(
                label = "VOICE SERVICE",
                status = if (isVoiceServiceRunning) "RUNNING" else "STOPPED",
                isOk = isVoiceServiceRunning,
                detail = if (isVoiceServiceRunning) "Foreground voice service active in background" else "Assistant service stopped",
                actionLabel = if (isVoiceServiceRunning) "Stop" else "Start",
                onAction = {
                    if (isVoiceServiceRunning) {
                        SigmaVoiceService.stop(context)
                    } else {
                        SigmaVoiceService.start(context)
                    }
                    refreshTrigger++
                }
            )

            DiagnosticItemRow(
                label = "MICROPHONE PERMISSION",
                status = if (hasMicPermission) "GRANTED" else "DENIED",
                isOk = hasMicPermission,
                detail = if (hasMicPermission) "RECORD_AUDIO permission active" else "Microphone access blocked",
                actionLabel = if (!hasMicPermission) "Settings" else null,
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            )

            DiagnosticItemRow(
                label = "TTS SPEECH ENGINE",
                status = if (isTtsReady) "READY" else "NOT READY",
                isOk = isTtsReady,
                detail = if (isTtsReady) "Android TextToSpeech engine initialized" else "TTS engine initializing or unavailable",
                actionLabel = "Test Voice",
                onAction = {
                    ttsManager?.speak("SIGMA diagnostics test passed. Subsystems operational.")
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "REAL RUNTIME COMPONENT TESTS",
            color = SigmaWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Component Test Rows
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // 1. Accessibility Service Verification
            RuntimeTestCard(
                result = testAccessibilityResult,
                onRunTest = {
                    val service = SigmaAccessibilityService.instance
                    if (service != null) {
                        val root = service.rootInActiveWindow
                        val hasRoot = root != null
                        testAccessibilityResult = DiagnosticTestResult(
                            testName = "Accessibility Binding",
                            status = "PASS",
                            isPass = true,
                            details = "Bound by Android OS. Active root window: ${if (hasRoot) "Accessible (${root?.childCount} children)" else "No active window"}"
                        )
                    } else {
                        testAccessibilityResult = DiagnosticTestResult(
                            testName = "Accessibility Binding",
                            status = "FAIL",
                            isPass = false,
                            details = "Service instance is null. Enable SIGMA in Settings > Accessibility."
                        )
                    }
                }
            )

            // 2. Node Dump
            RuntimeTestCard(
                result = testNodeDumpResult,
                onRunTest = {
                    val service = SigmaAccessibilityService.instance
                    if (service != null) {
                        val nodes = service.dumpScreenNodes()
                        testNodeDumpResult = DiagnosticTestResult(
                            testName = "Screen Node Dump",
                            status = if (nodes.isNotEmpty()) "PASS" else "FAIL",
                            isPass = nodes.isNotEmpty(),
                            details = "Dumped ${nodes.size} real nodes from root window. Clickables: ${nodes.count { it.isClickable }}, Editables: ${nodes.count { it.isEditable }}"
                        )
                    } else {
                        testNodeDumpResult = DiagnosticTestResult(
                            testName = "Screen Node Dump",
                            status = "FAIL",
                            isPass = false,
                            details = "Accessibility Service is not connected."
                        )
                    }
                }
            )

            // 3. App Launch (Dynamic PackageManager Test)
            RuntimeTestCard(
                result = testAppLaunchResult,
                onRunTest = {
                    scope.launch {
                        testAppLaunchResult = testAppLaunchResult.copy(isRunning = true)
                        val installed = withContext(Dispatchers.IO) { installedAppRepository.getInstalledApps() }
                        val res = appResolver.resolveAndLaunch("settings")
                        delay(1200L)
                        val isSuccess = res is AppResolutionResult.Success
                        testAppLaunchResult = DiagnosticTestResult(
                            testName = "App Launch",
                            isRunning = false,
                            status = if (isSuccess) "PASS" else "FAIL",
                            isPass = isSuccess,
                            details = if (isSuccess) "Successfully queried ${installed.size} apps and launched Settings." else "Failed to resolve or launch app."
                        )
                    }
                }
            )

            // 4. Tap Gesture Dispatch
            RuntimeTestCard(
                result = testTapResult,
                onRunTest = {
                    val service = SigmaAccessibilityService.instance
                    if (service != null) {
                        val success = service.tapCoordinates(500f, 500f)
                        testTapResult = DiagnosticTestResult(
                            testName = "Tap Gesture Dispatch",
                            status = if (success) "PASS" else "FAIL",
                            isPass = success,
                            details = if (success) "GestureDescription stroke dispatched at (500, 500)." else "Accessibility gesture dispatch returned false."
                        )
                    } else {
                        testTapResult = DiagnosticTestResult(
                            testName = "Tap Gesture Dispatch",
                            status = "FAIL",
                            isPass = false,
                            details = "Accessibility Service is not connected."
                        )
                    }
                }
            )

            // 5. Text Input Action
            RuntimeTestCard(
                result = testTextInputResult,
                onRunTest = {
                    val service = SigmaAccessibilityService.instance
                    if (service != null) {
                        val success = service.findEditableAndSetText("SIGMA Test")
                        testTextInputResult = DiagnosticTestResult(
                            testName = "Text Input (ACTION_SET_TEXT)",
                            status = if (success) "PASS" else "FAIL",
                            isPass = success,
                            details = if (success) "Successfully applied ACTION_SET_TEXT to editable field." else "No active editable EditText found in current window."
                        )
                    } else {
                        testTextInputResult = DiagnosticTestResult(
                            testName = "Text Input (ACTION_SET_TEXT)",
                            status = "FAIL",
                            isPass = false,
                            details = "Accessibility Service is not connected."
                        )
                    }
                }
            )

            // 6. Scroll Action
            RuntimeTestCard(
                result = testScrollResult,
                onRunTest = {
                    val service = SigmaAccessibilityService.instance
                    if (service != null) {
                        val success = service.scrollForward()
                        testScrollResult = DiagnosticTestResult(
                            testName = "Scroll Action",
                            status = if (success) "PASS" else "FAIL",
                            isPass = success,
                            details = if (success) "ACTION_SCROLL_FORWARD executed on active window." else "Active window did not report scrollable container."
                        )
                    } else {
                        testScrollResult = DiagnosticTestResult(
                            testName = "Scroll Action",
                            status = "FAIL",
                            isPass = false,
                            details = "Accessibility Service is not connected."
                        )
                    }
                }
            )

            // 7. TTS Vocalization
            RuntimeTestCard(
                result = testTtsResult,
                onRunTest = {
                    if (ttsManager != null && ttsManager.isReady) {
                        ttsManager.speak("SIGMA runtime vocalization test.")
                        testTtsResult = DiagnosticTestResult(
                            testName = "TTS Audio Output",
                            status = "PASS",
                            isPass = true,
                            details = "Dispatched utterance to Android TextToSpeech engine."
                        )
                    } else {
                        testTtsResult = DiagnosticTestResult(
                            testName = "TTS Audio Output",
                            status = "FAIL",
                            isPass = false,
                            details = "TTS engine is not ready or failed to initialize."
                        )
                    }
                }
            )

            // 8. Gemini AI API
            RuntimeTestCard(
                result = testGeminiResult,
                onRunTest = {
                    scope.launch {
                        testGeminiResult = testGeminiResult.copy(isRunning = true)
                        val res = withContext(Dispatchers.IO) { aiService.generateText("Say Hello in one word.") }
                        val text = res.getOrNull()
                        val success = !text.isNullOrBlank()
                        testGeminiResult = DiagnosticTestResult(
                            testName = "Gemini AI API",
                            isRunning = false,
                            status = if (success) "PASS" else "FAIL",
                            isPass = success,
                            details = if (success) "Received AI response: \"${text?.trim()}\"" else "Gemini API failed: ${res.exceptionOrNull()?.message}"
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "LIVE EXECUTION METRICS",
            color = SigmaWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow(
                    label = "Accessibility Service",
                    value = if (isAccessibilityRunning) "CONNECTED" else "DISCONNECTED",
                    isError = !isAccessibilityRunning
                )
                MetricRow(
                    label = "Current Package",
                    value = com.example.automation.AutomationDiagnostics.currentPackage.ifEmpty { currentForegroundPackage.ifEmpty { "None" } }
                )
                MetricRow(
                    label = "Current Screen",
                    value = com.example.automation.AutomationDiagnostics.currentScreenSummary.ifEmpty { screenSummary }
                )
                MetricRow(
                    label = "Last Action",
                    value = com.example.automation.AutomationDiagnostics.lastAction
                )
                MetricRow(
                    label = "Last Action Result",
                    value = com.example.automation.AutomationDiagnostics.lastActionResult,
                    isError = com.example.automation.AutomationDiagnostics.lastActionResult.startsWith("FAILED")
                )
                MetricRow(
                    label = "Current Automation State",
                    value = com.example.automation.AutomationDiagnostics.currentState.name,
                    isError = com.example.automation.AutomationDiagnostics.currentState == com.example.automation.AutomationState.FAILED
                )
                MetricRow(
                    label = "Playback Verification",
                    value = if (com.example.automation.AutomationDiagnostics.isPlaybackVerified) "TRUE" else "FALSE"
                )
                MetricRow(
                    label = "Recovery Attempts",
                    value = com.example.automation.AutomationDiagnostics.recoveryAttempts.toString()
                )
                MetricRow(
                    label = "Last Command",
                    value = com.example.automation.AutomationDiagnostics.lastCommand.ifEmpty { lastCommand.ifEmpty { "None recorded" } }
                )
                if (lastError.isNotEmpty() && lastError != "None") {
                    MetricRow(label = "Last Error", value = lastError, isError = true)
                }
            }
        }
    }
}

@Composable
private fun RuntimeTestCard(
    result: DiagnosticTestResult,
    onRunTest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF14080D))
            .border(
                1.dp,
                when (result.isPass) {
                    true -> SigmaStatusOnline.copy(alpha = 0.5f)
                    false -> SigmaStatusError.copy(alpha = 0.5f)
                    null -> Color(0xFF2B1019)
                },
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.testName,
                        color = SigmaWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${result.status}]",
                        color = when (result.isPass) {
                            true -> SigmaStatusOnline
                            false -> SigmaStatusError
                            null -> Color.Gray
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (result.details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = result.details,
                        color = SigmaTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (result.isRunning) {
                CircularProgressIndicator(
                    color = SigmaNeonRedBright,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onRunTest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C0B14)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Run", color = SigmaWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItemRow(
    label: String,
    status: String,
    isOk: Boolean,
    detail: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14080D))
            .border(
                1.dp,
                if (isOk) SigmaStatusOnline.copy(alpha = 0.35f) else SigmaStatusError.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isOk) SigmaStatusOnline else SigmaStatusError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = SigmaWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[$status]",
                        color = if (isOk) SigmaStatusOnline else SigmaStatusError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    color = SigmaTextSecondary,
                    fontSize = 11.sp
                )
            }

            if (actionLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                SigmaButton(
                    text = actionLabel,
                    onClick = onAction
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, isError: Boolean = false) {
    Column {
        Text(
            text = label.uppercase(),
            color = SigmaNeonRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            color = if (isError) SigmaStatusError else SigmaWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
