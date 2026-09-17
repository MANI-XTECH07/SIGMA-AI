package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AnimationQuality
import com.example.data.SettingsRepository
import com.example.ui.components.SigmaButton
import com.example.ui.theme.SigmaGlassBorder
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaRedDark
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBackgroundServiceToggled: (Boolean) -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var speechRate by remember { mutableFloatStateOf(settingsRepository.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(settingsRepository.speechPitch) }
    var wakeWordEnabled by remember { mutableStateOf(settingsRepository.isWakeWordEnabled) }
    var backgroundServiceEnabled by remember { mutableStateOf(settingsRepository.isBackgroundServiceEnabled) }
    var confirmSensitive by remember { mutableStateOf(settingsRepository.isConfirmSensitiveActions) }
    var reduceMotion by remember { mutableStateOf(settingsRepository.isReduceMotion) }
    var customApiKey by remember { mutableStateOf(settingsRepository.customApiKey) }
    var selectedQuality by remember { mutableStateOf(settingsRepository.animationQuality) }

    var expandedSection by remember { mutableStateOf<String?>("VOICE") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SigmaWhite
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Settings",
                color = SigmaWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PROFILE HEADER CARD (Mockup matching)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13080B))
                .border(1.5.dp, SigmaNeonRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, SigmaNeonRed, RoundedCornerShape(14.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sigma_app_icon),
                            contentDescription = "SIGMA Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "SIGMA",
                            color = SigmaWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "AI Assistant",
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp
                        )
                    }
                }

                // Premium Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF28070F))
                        .border(1.dp, SigmaNeonRed, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Premium",
                        color = SigmaNeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. VOICE & SPEECH
        MockupSettingsCategory(
            title = "Voice & Speech",
            subtitle = "Voice, TTS, Wake Word",
            icon = Icons.Default.Mic,
            isExpanded = expandedSection == "VOICE",
            onToggle = { expandedSection = if (expandedSection == "VOICE") null else "VOICE" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Speech Speed (${String.format("%.2f", speechRate)}x)",
                    color = SigmaWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        settingsRepository.speechRate = it
                    },
                    valueRange = 0.8f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = SigmaNeonRed,
                        activeTrackColor = SigmaNeonRed
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Speech Pitch (${String.format("%.2f", speechPitch)})",
                    color = SigmaWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = speechPitch,
                    onValueChange = {
                        speechPitch = it
                        settingsRepository.speechPitch = it
                    },
                    valueRange = 0.7f..1.3f,
                    colors = SliderDefaults.colors(
                        thumbColor = SigmaNeonRed,
                        activeTrackColor = SigmaNeonRed
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Wake Word (\"Hey Sigma\")", color = SigmaWhite, fontSize = 13.sp)
                        Text(text = "Continuous wake word listener", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = {
                            wakeWordEnabled = it
                            settingsRepository.isWakeWordEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SigmaWhite,
                            checkedTrackColor = SigmaNeonRed
                        )
                    )
                }
            }
        }

        // 2. AI & MODEL
        MockupSettingsCategory(
            title = "AI & Model",
            subtitle = "Gemini, Context, Memory",
            icon = Icons.Default.Psychology,
            isExpanded = expandedSection == "AI",
            onToggle = { expandedSection = if (expandedSection == "AI") null else "AI" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Custom Gemini API Key",
                    color = SigmaWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Optional override for server-side key",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customApiKey,
                    onValueChange = {
                        customApiKey = it
                        settingsRepository.customApiKey = it
                    },
                    placeholder = { Text("AIzaSy...", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SigmaNeonRed,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = SigmaWhite,
                        unfocusedTextColor = SigmaWhite
                    )
                )
            }
        }

        // 3. PERMISSIONS
        MockupSettingsCategory(
            title = "Permissions",
            subtitle = "Accessibility, Microphone, Overlay",
            icon = Icons.Default.Security,
            isExpanded = expandedSection == "PERMISSIONS",
            onToggle = { expandedSection = if (expandedSection == "PERMISSIONS") null else "PERMISSIONS" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Sensitive Action Verification", color = SigmaWhite, fontSize = 13.sp)
                        Text(text = "Confirmation before calls and messages", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = confirmSensitive,
                        onCheckedChange = {
                            confirmSensitive = it
                            settingsRepository.isConfirmSensitiveActions = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SigmaWhite,
                            checkedTrackColor = SigmaNeonRed
                        )
                    )
                }
            }
        }

        // 4. APPEARANCE
        MockupSettingsCategory(
            title = "Appearance",
            subtitle = "Theme, Animation, Font",
            icon = Icons.Default.Palette,
            isExpanded = expandedSection == "APPEARANCE",
            onToggle = { expandedSection = if (expandedSection == "APPEARANCE") null else "APPEARANCE" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Reduce Motion", color = SigmaWhite, fontSize = 13.sp)
                        Text(text = "Disables intense particle spins", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = reduceMotion,
                        onCheckedChange = {
                            reduceMotion = it
                            settingsRepository.isReduceMotion = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SigmaWhite,
                            checkedTrackColor = SigmaNeonRed
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Animation Quality", color = SigmaWhite, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(AnimationQuality.LOW, AnimationQuality.MEDIUM, AnimationQuality.HIGH).forEach { q ->
                        SigmaButton(
                            text = q.name,
                            onClick = {
                                selectedQuality = q
                                settingsRepository.animationQuality = q
                            },
                            isPrimary = selectedQuality == q,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. BACKGROUND & LOCK SCREEN
        MockupSettingsCategory(
            title = "Background & Lock Screen",
            subtitle = "Always On, Notifications",
            icon = Icons.Default.LockClock,
            isExpanded = expandedSection == "BACKGROUND",
            onToggle = { expandedSection = if (expandedSection == "BACKGROUND") null else "BACKGROUND" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Background Wake Service", color = SigmaWhite, fontSize = 13.sp)
                        Text(text = "Listens when app is in background", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = backgroundServiceEnabled,
                        onCheckedChange = {
                            backgroundServiceEnabled = it
                            settingsRepository.isBackgroundServiceEnabled = it
                            onBackgroundServiceToggled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SigmaWhite,
                            checkedTrackColor = SigmaNeonRed
                        )
                    )
                }
            }
        }

        // 6. DEVICE CONTROL
        MockupSettingsCategory(
            title = "Device Control",
            subtitle = "Apps, System, Automation",
            icon = Icons.Default.Devices,
            isExpanded = expandedSection == "DEVICES",
            onToggle = { expandedSection = if (expandedSection == "DEVICES") null else "DEVICES" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Screen lock, volume controls, and app resolution enabled.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // 7. WIDGET
        MockupSettingsCategory(
            title = "Widget",
            subtitle = "Home Screen Widget",
            icon = Icons.Default.Widgets,
            isExpanded = expandedSection == "WIDGET",
            onToggle = { expandedSection = if (expandedSection == "WIDGET") null else "WIDGET" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Add SIGMA AI Assistant 4x2 quick orb widget to your Android home screen.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // 8. ADVANCED
        MockupSettingsCategory(
            title = "Advanced",
            subtitle = "Performance, Debug, About",
            icon = Icons.Default.SettingsApplications,
            isExpanded = expandedSection == "ADVANCED",
            onToggle = { expandedSection = if (expandedSection == "ADVANCED") null else "ADVANCED" }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "SIGMA Version: 2.4.0-CYBERPUNK", color = SigmaNeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "Kotlin • Jetpack Compose • Gemini 3.8", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MockupSettingsCategory(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F080A))
            .border(1.dp, Color(0xFF240D13), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF22080E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = SigmaNeonRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Expand",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0506))
            ) {
                content()
            }
        }
    }
}
