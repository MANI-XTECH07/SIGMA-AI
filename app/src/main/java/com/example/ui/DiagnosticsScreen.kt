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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.screen.ScreenAnalysisManager
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaScreenCaptureService
import com.example.service.SigmaVoiceService
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaStatusError
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite
import com.example.voice.TextToSpeechManager

@Composable
fun DiagnosticsScreen(
    ttsManager: TextToSpeechManager?,
    lastCommand: String,
    currentAction: String,
    lastError: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenAnalysisManager = remember { ScreenAnalysisManager() }

    var refreshTrigger by remember { mutableStateOf(0) }
    var screenSummary by remember { mutableStateOf("Analyzing...") }
    var isAccessibilityRunning by remember { mutableStateOf(false) }
    var currentForegroundPackage by remember { mutableStateOf("") }
    var hasMicPermission by remember { mutableStateOf(false) }
    var isVoiceServiceRunning by remember { mutableStateOf(false) }
    var isScreenCaptureRunning by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        isAccessibilityRunning = SigmaAccessibilityService.isServiceRunning
        currentForegroundPackage = SigmaAccessibilityService.currentPackage.ifEmpty { "com.aistudio.sigma" }
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
                    text = "HARDWARE & SYSTEM DIAGNOSTICS",
                    color = SigmaWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Live kernel & subsystem verification",
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

        // Subsystems Status Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticItemRow(
                label = "ACCESSIBILITY SERVICE",
                status = if (isAccessibilityRunning) "CONNECTED" else "DISCONNECTED",
                isOk = isAccessibilityRunning,
                detail = if (isAccessibilityRunning) "Active Window Inspection & Gesture Dispatch Enabled" else "Disabled in Android Settings",
                actionLabel = if (!isAccessibilityRunning) "Enable" else null,
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
                detail = if (isVoiceServiceRunning) "Persistent Foreground Assistant Active" else "Assistant Service Inactive",
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
                detail = if (hasMicPermission) "RECORD_AUDIO Permission Available" else "Microphone access blocked",
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
                status = if (isTtsReady) "READY" else "ERROR",
                isOk = isTtsReady,
                detail = if (isTtsReady) "Calibrated Male Voice Synthesis Online" else "TTS Engine initializing or unavailable",
                actionLabel = "Test Voice",
                onAction = {
                    ttsManager?.speak("SIGMA diagnostics test passed. Systems fully operational.")
                }
            )

            DiagnosticItemRow(
                label = "SCREEN CAPTURE (VISION)",
                status = if (isScreenCaptureRunning) "ACTIVE" else "INACTIVE",
                isOk = isScreenCaptureRunning,
                detail = if (isScreenCaptureRunning) "MediaProjection VirtualDisplay Online" else "MediaProjection Standby",
                actionLabel = null,
                onAction = {}
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
                MetricRow(label = "Current Package", value = currentForegroundPackage)
                MetricRow(label = "Last Command", value = lastCommand.ifEmpty { "None recorded" })
                MetricRow(label = "Current Action", value = currentAction.ifEmpty { "Idle" })
                MetricRow(label = "Last Error", value = lastError.ifEmpty { "None" }, isError = lastError.isNotEmpty() && lastError != "None")
                MetricRow(label = "Detected Screen", value = screenSummary)
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
