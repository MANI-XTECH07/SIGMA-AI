package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaNotificationListenerService
import com.example.service.SigmaVoiceService
import com.example.ui.components.PermissionCardStatus
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.components.SigmaPermissionCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaGlassBorder
import com.example.ui.theme.SigmaGlassBorderSubtle
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaNeonRedGlow
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaTextTertiary
import com.example.ui.theme.SigmaWhite

private enum class SetupCategory(val label: String) {
    ALL("ALL MATRIX"),
    CORE("CORE NEURAL"),
    AUTOMATION("AUTOMATION & VISION"),
    COMMUNICATIONS("COMMS & POWER")
}

@Composable
fun PermissionScreen(
    isOnboarding: Boolean = false,
    onCompleteOnboarding: () -> Unit = {},
    onRequestScreenCapture: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Trigger state used to re-evaluate system states upon returning from settings
    var checkTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic Permission Checks
    val hasAudio = remember(checkTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasNotif = remember(checkTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    val hasContacts = remember(checkTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val hasCall = remember(checkTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }
    val hasPhoneState = remember(checkTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    val hasSms = remember(checkTrigger) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
    val isAccessibilityActive = remember(checkTrigger) {
        SigmaAccessibilityService.isServiceRunning
    }
    val isNotificationAccessActive = remember(checkTrigger) {
        SigmaNotificationListenerService.isNotificationAccessGranted(context)
    }
    val isBackgroundActive = remember(checkTrigger) {
        SigmaVoiceService.isRunning
    }
    val isBatteryOptimizedIgnored = remember(checkTrigger) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    // Launchers
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkTrigger++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkTrigger++ }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkTrigger++ }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checkTrigger++ }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checkTrigger++ }

    // Quick Batch Launcher for Standard Android Runtime Dialogs
    val batchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkTrigger++
    }

    // Category filter state
    var selectedCategory by remember { mutableStateOf(SetupCategory.ALL) }

    // Progress calculations
    val coreChecks = listOf(hasAudio, hasNotif, hasContacts, isAccessibilityActive, isNotificationAccessActive)
    val coreGrantedCount = coreChecks.count { it }
    val totalCore = coreChecks.size

    val allChecks = listOf(
        hasAudio,
        hasAudio, // Speech recognition relies on audio
        hasNotif,
        hasContacts,
        hasCall && hasPhoneState,
        hasSms,
        isNotificationAccessActive,
        isAccessibilityActive,
        false, // Screen capture is on-demand
        isBatteryOptimizedIgnored
    )
    val totalGrantedCount = allChecks.count { it }
    val totalItems = allChecks.size

    val targetProgress = totalGrantedCount.toFloat() / totalItems.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )

    // Glowing pulse transition for AI Core
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val needsBatchStandard = !hasAudio || !hasNotif || !hasContacts || !hasCall || !hasPhoneState

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 16.dp,
                bottom = if (isOnboarding) 120.dp else 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. HERO HEADER: EXECUTIVE AI CORE BANNER
            // ==========================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF26050C),
                                    Color(0xFF130407),
                                    Color(0xFF09070A)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(SigmaNeonRed.copy(alpha = 0.6f), Color(0x11FF1744))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // AI Shield / Crest with Animated Halo
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pulsing Outer Ring
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                SigmaNeonRed.copy(alpha = pulseAlpha),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            // Inner Crest
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A0307))
                                    .border(2.dp, SigmaNeonRedBright, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SigmaWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Monospace System Sub-Label
                        Text(
                            text = "SIGMA AUTONOMOUS INTELLIGENCE // MATRIX v36",
                            color = SigmaNeonRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isOnboarding) "SYSTEM INITIALIZATION" else "PERMISSION CENTER",
                            color = SigmaWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isOnboarding) {
                                "Configure device capabilities to activate real-time wake-word, autonomous screen manipulation, and background intelligence."
                            } else {
                                "Real-time hardware status verification and security privilege management."
                            },
                            color = SigmaTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 2. READINESS DASHBOARD & BATCH ACTION
            // ==========================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF161622), Color(0xFF0F0F17))
                            )
                        )
                        .border(1.dp, SigmaGlassBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "SYSTEM READINESS GAUGE",
                                    color = SigmaWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (coreGrantedCount >= totalCore) {
                                        "CORE CAPABILITIES ONLINE"
                                    } else {
                                        "${totalCore - coreGrantedCount} CORE SUBSYSTEMS PENDING"
                                    },
                                    color = if (coreGrantedCount >= totalCore) SigmaStatusOnline else SigmaNeonRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Circular Progress & Percentage
                            Box(
                                modifier = Modifier.size(54.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = if (animatedProgress >= 0.8f) SigmaStatusOnline else SigmaNeonRed,
                                    trackColor = Color(0xFF261017),
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    color = SigmaWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Linear Breakdown bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (animatedProgress >= 0.8f) SigmaStatusOnline else SigmaNeonRed,
                            trackColor = Color(0xFF240A11)
                        )

                        // Quick Batch Action Button if standard permissions are pending
                        if (needsBatchStandard) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF380811), Color(0xFF1A0207))
                                        )
                                    )
                                    .border(1.dp, SigmaNeonRed.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val perms = mutableListOf(
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.CALL_PHONE,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        batchLauncher.launch(perms.toTypedArray())
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = SigmaNeonRedBright,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "QUICK AUTHORIZE RUNTIME PERMISSIONS",
                                                color = SigmaWhite,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "Mic, Notifications, Contacts, Phone in one sequence",
                                                color = SigmaTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = SigmaNeonRedBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. CATEGORY FILTER TABS
            // ==========================================
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SetupCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFF330910) else Color(0xFF14141B)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) SigmaNeonRed else SigmaGlassBorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category.label,
                                color = if (isSelected) SigmaWhite else SigmaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. PERMISSION CARDS (CATEGORIZED)
            // ==========================================

            // CATEGORY: CORE NEURAL INTERFACE
            if (selectedCategory == SetupCategory.ALL || selectedCategory == SetupCategory.CORE) {
                item {
                    SectionHeader("CATEGORY 01 // CORE NEURAL INTERFACE")
                }

                // 1. Microphone
                item {
                    SigmaPermissionCard(
                        title = "MICROPHONE SENSOR",
                        description = "Required for low-latency continuous wake-word (\"Hey Sigma\") and real-time audio commands.",
                        icon = Icons.Default.Mic,
                        status = if (hasAudio) PermissionCardStatus.GRANTED else PermissionCardStatus.REQUIRED,
                        customActionLabel = if (hasAudio) "Active" else "Grant Mic",
                        categoryTag = "CORE SENSOR",
                        technicalNote = "android.media.AudioRecord",
                        onActionClick = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }

                // 2. Speech Recognition
                item {
                    SigmaPermissionCard(
                        title = "SPEECH-TO-TEXT ENGINE",
                        description = "Translates streaming spoken words into structured JSON execution intents.",
                        icon = Icons.Default.RecordVoiceOver,
                        status = if (hasAudio) PermissionCardStatus.GRANTED else PermissionCardStatus.REQUIRED,
                        customActionLabel = if (hasAudio) "Active" else "Authorize Audio",
                        categoryTag = "SPEECH PIPELINE",
                        technicalNote = "On-Device Neural STT",
                        onActionClick = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }

                // 3. Notifications
                item {
                    SigmaPermissionCard(
                        title = "FOREGROUND NOTIFICATIONS",
                        description = "Maintains persistent status indicators, background service vitality, and real-time execution feedback.",
                        icon = Icons.Default.Notifications,
                        status = if (hasNotif) PermissionCardStatus.GRANTED else PermissionCardStatus.REQUIRED,
                        customActionLabel = if (hasNotif) "Active" else "Allow",
                        categoryTag = "SYSTEM STATUS",
                        technicalNote = "POST_NOTIFICATIONS",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }

            // CATEGORY: SYSTEM AUTOMATION & VISION
            if (selectedCategory == SetupCategory.ALL || selectedCategory == SetupCategory.AUTOMATION) {
                item {
                    SectionHeader("CATEGORY 02 // SYSTEM AUTOMATION & VISION")
                }

                // 4. Accessibility Automation
                item {
                    SigmaPermissionCard(
                        title = "ACCESSIBILITY AUTOMATION",
                        description = "Enables SIGMA to observe on-screen layout nodes, click targets, type text, scroll, and execute autonomous tasks.",
                        icon = Icons.Default.AccessibilityNew,
                        status = if (isAccessibilityActive) PermissionCardStatus.GRANTED else PermissionCardStatus.DISABLED,
                        customActionLabel = if (isAccessibilityActive) "Connected" else "Enable Service ↗",
                        categoryTag = "AUTONOMOUS AGENT",
                        technicalNote = "SigmaAccessibilityService",
                        onActionClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )
                }

                // 5. Notification Access Listener
                item {
                    SigmaPermissionCard(
                        title = "NOTIFICATION ACCESS LISTENER",
                        description = "Intercepts incoming alerts from messaging apps for proactive summarization, hands-free readouts, and quick actions.",
                        icon = Icons.Default.Notifications,
                        status = if (isNotificationAccessActive) PermissionCardStatus.GRANTED else PermissionCardStatus.DISABLED,
                        customActionLabel = if (isNotificationAccessActive) "Connected" else "Connect Listener ↗",
                        categoryTag = "ALERT INTERCEPTOR",
                        technicalNote = "SigmaNotificationListenerService",
                        onActionClick = {
                            context.startActivity(SigmaNotificationListenerService.openNotificationSettingsIntent())
                        }
                    )
                }

                // 6. MediaProjection Vision
                item {
                    SigmaPermissionCard(
                        title = "SCREEN ANALYSIS VISION",
                        description = "High-accuracy visual layout parsing via Gemini multimodal vision. Android security prompts consent whenever vision is requested.",
                        icon = Icons.AutoMirrored.Filled.ScreenShare,
                        status = PermissionCardStatus.OPTIONAL,
                        isOptional = true,
                        customActionLabel = "Test Consent Dialog",
                        categoryTag = "VISION LAYER",
                        technicalNote = "MediaProjectionManager",
                        onActionClick = onRequestScreenCapture
                    )
                }
            }

            // CATEGORY: COMMUNICATIONS & POWER
            if (selectedCategory == SetupCategory.ALL || selectedCategory == SetupCategory.COMMUNICATIONS) {
                item {
                    SectionHeader("CATEGORY 03 // COMMUNICATIONS & POWER")
                }

                // 7. Contacts
                item {
                    SigmaPermissionCard(
                        title = "DEVICE CONTACTS",
                        description = "Enables voice-driven queries like \"Call John\" or \"Text Mom\" by searching real device address books.",
                        icon = Icons.Default.Contacts,
                        status = if (hasContacts) PermissionCardStatus.GRANTED else PermissionCardStatus.REQUIRED,
                        customActionLabel = if (hasContacts) "Active" else "Grant Contacts",
                        categoryTag = "ADDRESS BOOK",
                        technicalNote = "READ_CONTACTS",
                        onActionClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    )
                }

                // 8. Phone & Call Safety
                item {
                    SigmaPermissionCard(
                        title = "PHONE CALLS & SAFETY INTERCEPT",
                        description = "Direct call placement and phone state monitoring to safely mute AI mic capture during active phone calls.",
                        icon = Icons.Default.Call,
                        status = if (hasCall && hasPhoneState) PermissionCardStatus.GRANTED else PermissionCardStatus.REQUIRED,
                        customActionLabel = if (hasCall && hasPhoneState) "Active" else "Grant Calls",
                        categoryTag = "TELEPHONY",
                        technicalNote = "CALL_PHONE & READ_PHONE_STATE",
                        onActionClick = {
                            phoneLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                        }
                    )
                }

                // 9. SMS Messaging
                item {
                    SigmaPermissionCard(
                        title = "SMS MESSAGING DISPATCH",
                        description = "Draft and transmit SMS messages via hands-free voice command with explicit confirmation safeguards.",
                        icon = Icons.Default.Sms,
                        status = if (hasSms) PermissionCardStatus.GRANTED else PermissionCardStatus.OPTIONAL,
                        isOptional = true,
                        customActionLabel = if (hasSms) "Active" else "Enable SMS",
                        categoryTag = "MESSAGING",
                        technicalNote = "SEND_SMS & READ_SMS",
                        onActionClick = {
                            smsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS))
                        }
                    )
                }

                // 10. Background Power Optimization
                item {
                    SigmaPermissionCard(
                        title = "BACKGROUND POWER UNRESTRICTED",
                        description = "Prevents Android OS Doze mode from killing the wake-word detector and background voice loop when screen is locked.",
                        icon = Icons.Default.BatteryChargingFull,
                        status = if (isBatteryOptimizedIgnored) PermissionCardStatus.GRANTED else PermissionCardStatus.OPTIONAL,
                        isOptional = true,
                        customActionLabel = if (isBatteryOptimizedIgnored) "Unrestricted" else "Configure ↗",
                        categoryTag = "POWER ENGINE",
                        technicalNote = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                        onActionClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        }
                    )
                }
            }

            // ==========================================
            // 5. SECURITY & PRIVACY COMMITMENT
            // ==========================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0D14))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF14141E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SigmaStatusOnline,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ZERO-CLOUD TELEMETRY GUARANTEE",
                                color = SigmaWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SIGMA executes processing on-device and communicates strictly with your selected AI models. Passwords, PINs, OTP codes, and authentication tokens are systematically excluded and never persisted.",
                                color = SigmaTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // System App Details Settings Option
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF12121A))
                        .border(1.dp, SigmaGlassBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = SigmaTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ANDROID SYSTEM APP SETTINGS",
                                    color = SigmaWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Access OS settings if a permission was permanently denied.",
                                    color = SigmaTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        SigmaButton(
                            text = "Open Settings",
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            isPrimary = false,
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. STICKY DOCKED BOTTOM ACTION BAR (ONBOARDING)
        // ==========================================
        if (isOnboarding) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                SigmaBlack.copy(alpha = 0.85f),
                                SigmaBlack,
                                SigmaBlack
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Primary Launch Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        SigmaNeonRedBright,
                                        Color(0xFFB71C1C)
                                    )
                                )
                            )
                            .border(1.dp, SigmaNeonRedBright, RoundedCornerShape(12.dp))
                            .clickable(onClick = onCompleteOnboarding),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (coreGrantedCount >= totalCore) {
                                    "LAUNCH SIGMA ASSISTANT"
                                } else {
                                    "PROCEED TO SIGMA"
                                },
                                color = SigmaWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = SigmaWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Configure Later Secondary Option
                    if (coreGrantedCount < totalCore) {
                        Text(
                            text = "Configure remaining subsystems later in Permission Center",
                            color = SigmaTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable(onClick = onCompleteOnboarding)
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(SigmaNeonRed)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = SigmaTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
    }
}
