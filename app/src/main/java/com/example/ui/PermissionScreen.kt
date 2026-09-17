package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaVoiceService
import com.example.ui.components.PermissionCardStatus
import com.example.ui.components.SigmaPermissionCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaWhite

@Composable
fun PermissionScreen(
    onRequestAudio: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestCallPhone: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val isAccessibilityActive = SigmaAccessibilityService.isServiceRunning
    val isBackgroundActive = SigmaVoiceService.isRunning

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "PERMISSION CENTER",
            color = SigmaWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Live status & hardware authorization checks",
            color = SigmaNeonRed,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SigmaPermissionCard(
                title = "MICROPHONE",
                description = "Required for real-time speech recognition and live waveform feedback.",
                icon = Icons.Default.Mic,
                status = if (hasAudio) PermissionCardStatus.READY else PermissionCardStatus.REQUIRES_PERMISSION,
                onActionClick = onRequestAudio
            )

            SigmaPermissionCard(
                title = "ACCESSIBILITY SERVICE",
                description = "Required to inspect screen elements, execute taps, typing, scrolling, and device lock.",
                icon = Icons.Default.AccessibilityNew,
                status = if (isAccessibilityActive) PermissionCardStatus.READY else PermissionCardStatus.DISABLED,
                onActionClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            )

            SigmaPermissionCard(
                title = "SCREEN VISION",
                description = "Enables real visual screen capture analysis via MediaProjection.",
                icon = Icons.Default.ScreenShare,
                status = PermissionCardStatus.READY,
                onActionClick = onRequestScreenCapture
            )

            SigmaPermissionCard(
                title = "NOTIFICATIONS",
                description = "Allows SIGMA to show active foreground assistant controls in status bar.",
                icon = Icons.Default.Notifications,
                status = if (hasNotif) PermissionCardStatus.READY else PermissionCardStatus.REQUIRES_PERMISSION,
                onActionClick = onRequestNotifications
            )

            SigmaPermissionCard(
                title = "CONTACTS",
                description = "Search real device contacts for voice calling and messaging.",
                icon = Icons.Default.Contacts,
                status = if (hasContacts) PermissionCardStatus.READY else PermissionCardStatus.REQUIRES_PERMISSION,
                onActionClick = onRequestContacts
            )

            SigmaPermissionCard(
                title = "CALLING",
                description = "Initiate verified outgoing phone calls upon voice command.",
                icon = Icons.Default.Call,
                status = if (hasCall) PermissionCardStatus.READY else PermissionCardStatus.REQUIRES_PERMISSION,
                onActionClick = onRequestCallPhone
            )

            SigmaPermissionCard(
                title = "BACKGROUND ASSISTANT",
                description = "Keeps voice listener active in background for \"Hey Sigma\" wake commands.",
                icon = Icons.Default.VolumeUp,
                status = if (isBackgroundActive) PermissionCardStatus.READY else PermissionCardStatus.DISABLED,
                onActionClick = {
                    if (isBackgroundActive) {
                        SigmaVoiceService.stop(context)
                    } else {
                        SigmaVoiceService.start(context)
                    }
                }
            )
        }
    }
}
