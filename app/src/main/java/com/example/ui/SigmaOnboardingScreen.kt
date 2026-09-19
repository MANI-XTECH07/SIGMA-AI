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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.SigmaAccessibilityService
import com.example.service.SigmaNotificationListenerService
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaGlassBorderSubtle
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaNeonRedGlow
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaTextTertiary
import com.example.ui.theme.SigmaWhite
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stages of the setup onboarding matrix.
 */
enum class OnboardingStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val codeTag: String
) {
    VOICE_CORE(
        stepNumber = 1,
        title = "Voice Core",
        subtitle = "Continuous wake-word detection and on-device neural speech",
        codeTag = "AUDIO_SYNAPSE"
    ),
    AUTOMATION(
        stepNumber = 2,
        title = "Accessibility",
        subtitle = "Universal UI node perception and screen manipulation",
        codeTag = "ACCESSIBILITY_AI"
    ),
    NOTIFICATIONS(
        stepNumber = 3,
        title = "Alerts & Status",
        subtitle = "Persistent foreground service and notification interceptor",
        codeTag = "ALERT_INTERCEPTOR"
    ),
    COMMUNICATIONS(
        stepNumber = 4,
        title = "Comms & Power",
        subtitle = "Device contacts, safe telephony intercept, and power vitality",
        codeTag = "PERIPHERAL_MATRIX"
    )
}

@Composable
fun SigmaOnboardingScreen(
    onCompleteOnboarding: () -> Unit,
    onRequestScreenCapture: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Trigger state to re-evaluate system states upon returning from Android settings
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

    // Current Step State (0 to 3)
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = OnboardingStep.values()[currentStepIndex]

    // Step completion calculations
    val step1Complete = hasAudio
    val step2Complete = isAccessibilityActive
    val step3Complete = hasNotif && isNotificationAccessActive
    val step4Complete = hasContacts && (hasCall && hasPhoneState)

    val stepCompletionList = listOf(step1Complete, step2Complete, step3Complete, step4Complete)
    val completedStepsCount = stepCompletionList.count { it }
    val totalSteps = stepCompletionList.size

    val animatedOverallProgress by animateFloatAsState(
        targetValue = completedStepsCount.toFloat() / totalSteps.toFloat(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "overallProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
    ) {
        // Ambient cyberpunk glow in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Neon red glow in top-center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF1744).copy(alpha = 0.12f),
                        Color(0xFF8B0000).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                    radius = size.width * 0.7f
                )
            )
            // Subtle cyan glow in bottom-right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================
                // 1. ANIMATED CYBERPUNK ORB HEADER
                // ==========================================
                item {
                    CyberpunkOrbHeader(
                        currentStep = currentStep,
                        overallProgress = animatedOverallProgress
                    )
                }

                // ==========================================
                // 2. PROGRESS INDICATOR STEP-BAR
                // ==========================================
                item {
                    CyberpunkStepBar(
                        steps = OnboardingStep.values(),
                        currentStepIndex = currentStepIndex,
                        stepCompletion = stepCompletionList,
                        onStepClick = { index -> currentStepIndex = index }
                    )
                }

                // ==========================================
                // 3. STEP CONTENT CARDS (ANIMATED TRANSITION)
                // ==========================================
                item {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState.stepNumber > initialState.stepNumber) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut()
                                )
                            }
                        },
                        label = "stepCardTransition"
                    ) { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Step Header Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "// PHASE 0${step.stepNumber}: ${step.codeTag}",
                                        color = SigmaNeonRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.2.sp
                                    )
                                    Text(
                                        text = step.title.uppercase(),
                                        color = SigmaWhite,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                val isStepDone = stepCompletionList[step.stepNumber - 1]
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isStepDone) Color(0xFF072714) else Color(0xFF26050C))
                                        .border(
                                            1.dp,
                                            if (isStepDone) SigmaStatusOnline else SigmaNeonRed,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isStepDone) "✓ READY" else "PENDING",
                                        color = if (isStepDone) SigmaStatusOnline else SigmaNeonRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Text(
                                text = step.subtitle,
                                color = SigmaTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Step-Specific Material3 Glass Cards
                            when (step) {
                                OnboardingStep.VOICE_CORE -> {
                                    // 1. Microphone
                                    CyberpunkPermissionCardM3(
                                        title = "Microphone Sensor",
                                        description = "Enables real-time low-latency wake-word recognition (\"Hey Sigma\") and streaming voice command capture.",
                                        icon = Icons.Default.Mic,
                                        iconTint = SigmaNeonRedBright,
                                        isGranted = hasAudio,
                                        technicalTag = "android.media.AudioRecord",
                                        actionLabel = if (hasAudio) "Granted" else "Grant Mic",
                                        onActionClick = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                                    )

                                    // 2. Speech Recognition STT
                                    CyberpunkPermissionCardM3(
                                        title = "Neural Speech-to-Text",
                                        description = "Transcribes live spoken audio into structured command intents directly on device.",
                                        icon = Icons.Default.RecordVoiceOver,
                                        iconTint = Color(0xFF00E5FF),
                                        isGranted = hasAudio,
                                        technicalTag = "On-Device Speech Recognizer",
                                        actionLabel = if (hasAudio) "Active" else "Authorize Audio",
                                        onActionClick = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                                    )
                                }

                                OnboardingStep.AUTOMATION -> {
                                    // 1. Accessibility Automation
                                    CyberpunkPermissionCardM3(
                                        title = "Accessibility Automation",
                                        description = "Inspects screen hierarchy nodes, clicks UI targets, types input, scrolls, and recovers across installed apps.",
                                        icon = Icons.Default.AccessibilityNew,
                                        iconTint = SigmaNeonRed,
                                        isGranted = isAccessibilityActive,
                                        technicalTag = "SigmaAccessibilityService",
                                        actionLabel = if (isAccessibilityActive) "Connected" else "Open Settings ↗",
                                        onActionClick = {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        }
                                    )

                                    // 2. Multimodal Screen Capture
                                    CyberpunkPermissionCardM3(
                                        title = "Multimodal Screen Vision",
                                        description = "Captures high-resolution frame context for Gemini AI visual layout understanding when voice-prompted.",
                                        icon = Icons.AutoMirrored.Filled.ScreenShare,
                                        iconTint = Color(0xFF00E5FF),
                                        isGranted = false,
                                        isOptional = true,
                                        technicalTag = "MediaProjection Consent",
                                        actionLabel = "Test Consent Dialog",
                                        onActionClick = onRequestScreenCapture
                                    )
                                }

                                OnboardingStep.NOTIFICATIONS -> {
                                    // 1. Notifications
                                    CyberpunkPermissionCardM3(
                                        title = "Foreground Notifications",
                                        description = "Displays persistent status indicators, execution progress toasts, and prevents background service termination.",
                                        icon = Icons.Default.Notifications,
                                        iconTint = SigmaNeonRedBright,
                                        isGranted = hasNotif,
                                        technicalTag = "POST_NOTIFICATIONS",
                                        actionLabel = if (hasNotif) "Active" else "Allow Alerts",
                                        onActionClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                    )

                                    // 2. Notification Listener
                                    CyberpunkPermissionCardM3(
                                        title = "Notification Interceptor",
                                        description = "Monitors alerts from messaging apps for hands-free audio briefings and rapid voice replies.",
                                        icon = Icons.Default.NotificationsActive,
                                        iconTint = Color(0xFFFFB300),
                                        isGranted = isNotificationAccessActive,
                                        technicalTag = "SigmaNotificationListenerService",
                                        actionLabel = if (isNotificationAccessActive) "Connected" else "Open Listener Settings ↗",
                                        onActionClick = {
                                            context.startActivity(SigmaNotificationListenerService.openNotificationSettingsIntent())
                                        }
                                    )
                                }

                                OnboardingStep.COMMUNICATIONS -> {
                                    // 1. Contacts
                                    CyberpunkPermissionCardM3(
                                        title = "Device Contacts",
                                        description = "Resolves address book names for hands-free calling and messaging commands (\"Call John\").",
                                        icon = Icons.Default.Contacts,
                                        iconTint = Color(0xFF00E5FF),
                                        isGranted = hasContacts,
                                        technicalTag = "READ_CONTACTS",
                                        actionLabel = if (hasContacts) "Granted" else "Grant Contacts",
                                        onActionClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
                                    )

                                    // 2. Phone Calls & Safety
                                    CyberpunkPermissionCardM3(
                                        title = "Phone & Call Intercept",
                                        description = "Places direct calls and pauses mic listening during cellular/VoIP calls without disrupting conversations.",
                                        icon = Icons.Default.Call,
                                        iconTint = SigmaNeonRed,
                                        isGranted = hasCall && hasPhoneState,
                                        technicalTag = "CALL_PHONE & READ_PHONE_STATE",
                                        actionLabel = if (hasCall && hasPhoneState) "Active" else "Grant Telephony",
                                        onActionClick = {
                                            phoneLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                                        }
                                    )

                                    // 3. Background Battery Optimization
                                    CyberpunkPermissionCardM3(
                                        title = "Unrestricted Battery Power",
                                        description = "Exempts SIGMA from Android OS Doze battery limits so wake-word listening survives screen-off periods.",
                                        icon = Icons.Default.BatteryChargingFull,
                                        iconTint = SigmaStatusOnline,
                                        isGranted = isBatteryOptimizedIgnored,
                                        isOptional = true,
                                        technicalTag = "IGNORE_BATTERY_OPTIMIZATION",
                                        actionLabel = if (isBatteryOptimizedIgnored) "Unrestricted" else "Configure ↗",
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
                        }
                    }
                }

                // Privacy assurance note
                item {
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = Color(0xFF0B0B12).copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SigmaStatusOnline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SECURE LOCAL RUNTIME GUARANTEE",
                                    color = SigmaWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "All microphone and accessibility telemetry stays on your device. Passwords and credentials are never captured or sent.",
                                    color = SigmaTextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. DOCKED STICKY BOTTOM ACTION BAR
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                SigmaBlack.copy(alpha = 0.92f),
                                SigmaBlack,
                                SigmaBlack
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button (if step > 0)
                    if (currentStepIndex > 0) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161622))
                                .border(1.dp, SigmaGlassBorderSubtx, RoundedCornerShape(12.dp))
                                .clickable { currentStepIndex-- }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = SigmaTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PREV",
                                    color = SigmaTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    } else {
                        // Skip option on first screen
                        Text(
                            text = "Skip to Home",
                            color = SigmaTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable(onClick = onCompleteOnboarding)
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }

                    // Next / Launch Primary Action
                    val isFinalStep = currentStepIndex == OnboardingStep.values().lastIndex
                    val isCoreReady = hasAudio && isAccessibilityActive && hasNotif

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (isFinalStep || isCoreReady) {
                                        listOf(SigmaNeonRedBright, Color(0xFFB71C1C))
                                    } else {
                                        listOf(Color(0xFF380811), Color(0xFF1E0207))
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                if (isFinalStep || isCoreReady) SigmaNeonRedBright else SigmaNeonRed.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isFinalStep) {
                                    onCompleteOnboarding()
                                } else {
                                    currentStepIndex++
                                }
                            }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isFinalStep) "ENTER SIGMA" else "NEXT STEP",
                                color = SigmaWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = SigmaWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SigmaGlassBorderSubtx = Color(0x28FFFFFF)

/**
 * Animated Futuristic Cyberpunk Orb Header with rotating plasma rings,
 * orbital energy particles, and pulsing neural core.
 */
@Composable
fun CyberpunkOrbHeader(
    currentStep: OnboardingStep,
    overallProgress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    // Core breathing pulsation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Outer plasma ring rotation
    val ringRotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation1"
    )

    // Inner reverse ring rotation
    val ringRotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation2"
    )

    // Orbital particles angle
    val particleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleAngle"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F0F18).copy(alpha = 0.85f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    SigmaNeonRed.copy(alpha = 0.65f),
                    Color(0x18FF1744)
                )
            )
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Cyberpunk Orb Canvas
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.width * 0.30f

                    // 1. Ambient Outer Halo Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SigmaNeonRedBright.copy(alpha = 0.35f),
                                Color(0xFFFF0055).copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.48f
                        )
                    )

                    // 2. Outer Dashed Ring (Clockwise)
                    drawCircle(
                        color = SigmaNeonRed.copy(alpha = 0.5f),
                        radius = size.width * 0.44f,
                        center = center,
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), ringRotation1 * 2f),
                            cap = StrokeCap.Round
                        )
                    )

                    // 3. Middle Segmented Ring (Counter-Clockwise)
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                        radius = size.width * 0.38f,
                        center = center,
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(35f, 25f), ringRotation2 * 1.5f)
                        )
                    )

                    // 4. Pulsing Plasma Core
                    val coreRadius = baseRadius * pulseScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                SigmaNeonRedBright,
                                Color(0xFFB71C1C),
                                Color(0xFF1E0207)
                            ),
                            center = center,
                            radius = coreRadius
                        ),
                        radius = coreRadius,
                        center = center
                    )

                    // 5. Orbital Energy Particle Nodes
                    val orbitRadius = size.width * 0.41f
                    for (i in 0..2) {
                        val angle = particleAngle + (i * (2 * Math.PI / 3f)).toFloat()
                        val px = center.x + orbitRadius * cos(angle)
                        val py = center.y + orbitRadius * sin(angle)
                        drawCircle(
                            color = if (i == 0) Color(0xFF00E5FF) else SigmaNeonRedBright,
                            radius = 4f,
                            center = Offset(px, py)
                        )
                    }
                }

                // Centered Hologram Glyph
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = SigmaWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subsystem Status Tag
            Text(
                text = "SIGMA NEURAL CORE // v36 MATRIX",
                color = SigmaNeonRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.6.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AUTONOMOUS INITIALIZATION",
                color = SigmaWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Authorize sensory pipelines and automation engines to awaken SIGMA's real-time assistance capabilities.",
                color = SigmaTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/**
 * Cyberpunk Step Bar showing connected progress with step circles and glow lines.
 */
@Composable
fun CyberpunkStepBar(
    steps: Array<OnboardingStep>,
    currentStepIndex: Int,
    stepCompletion: List<Boolean>,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF11111B).copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, SigmaGlassBorderSubtx),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STEP ${currentStepIndex + 1} OF ${steps.size}",
                    color = SigmaNeonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                val completedCount = stepCompletion.count { it }
                Text(
                    text = "$completedCount/${steps.size} STEPS CONFIGURED",
                    color = if (completedCount == steps.size) SigmaStatusOnline else SigmaTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step Node Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, step ->
                    val isSelected = index == currentStepIndex
                    val isCompleted = stepCompletion[index]

                    // Step Node
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> Color(0xFF093118)
                                    isSelected -> Color(0xFF380811)
                                    else -> Color(0xFF14141E)
                                }
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = when {
                                    isCompleted -> SigmaStatusOnline
                                    isSelected -> SigmaNeonRedBright
                                    else -> Color(0x33FFFFFF)
                                },
                                shape = CircleShape
                            )
                            .clickable { onStepClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SigmaStatusOnline,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = if (isSelected) SigmaWhite else SigmaTextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Connector Line (if not last)
                    if (index < steps.lastIndex) {
                        val nextStepCompleted = stepCompletion[index]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .padding(horizontal = 4.dp)
                                .background(
                                    if (nextStepCompleted) SigmaStatusOnline.copy(alpha = 0.7f)
                                    else Color(0x22FFFFFF)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current Step Sub-Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                steps.forEachIndexed { index, step ->
                    val isSelected = index == currentStepIndex
                    Text(
                        text = step.title.split(" ").first(),
                        color = if (isSelected) SigmaWhite else SigmaTextTertiary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(60.dp)
                            .clickable { onStepClick(index) }
                    )
                }
            }
        }
    }
}

/**
 * Material3 Cyberpunk Glassmorphic Card for each permission.
 */
@Composable
fun CyberpunkPermissionCardM3(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    isGranted: Boolean,
    technicalTag: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOptional: Boolean = false
) {
    val cardBorder = if (isGranted) {
        SigmaStatusOnline.copy(alpha = 0.35f)
    } else {
        if (isOptional) Color(0xFF00E5FF).copy(alpha = 0.3f) else SigmaNeonRed.copy(alpha = 0.45f)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF13131F).copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, cardBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                    // Glass Icon Container
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    if (isGranted) {
                                        listOf(Color(0xFF072B15), Color(0xFF02160A))
                                    } else {
                                        listOf(Color(0xFF330910), Color(0xFF140205))
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                if (isGranted) SigmaStatusOnline.copy(alpha = 0.5f) else iconTint.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) SigmaStatusOnline else iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            color = SigmaWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isGranted) Color(0xFF052110) else Color(0xFF26050C))
                                    .border(
                                        1.dp,
                                        if (isGranted) SigmaStatusOnline.copy(alpha = 0.6f) else SigmaNeonRed.copy(alpha = 0.6f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isGranted) "ONLINE" else if (isOptional) "OPTIONAL" else "REQUIRED",
                                    color = if (isGranted) SigmaStatusOnline else if (isOptional) Color(0xFF00E5FF) else SigmaNeonRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = technicalTag,
                                color = SigmaTextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Action Pill
                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF052110))
                            .border(1.dp, SigmaStatusOnline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SigmaStatusOnline,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE",
                                color = SigmaStatusOnline,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (isOptional) {
                                        listOf(Color(0xFF00838F), Color(0xFF004D40))
                                    } else {
                                        listOf(SigmaNeonRedBright, Color(0xFF8B0000))
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                if (isOptional) Color(0xFF00E5FF).copy(alpha = 0.7f) else SigmaNeonRed.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onActionClick)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionLabel,
                            color = SigmaWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                color = SigmaTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
