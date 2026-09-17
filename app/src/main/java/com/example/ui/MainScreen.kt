package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AnimationQuality
import com.example.ui.components.SigmaStatusPill
import com.example.ui.components.SigmaWaveform
import com.example.ui.theme.SigmaDeepBlack
import com.example.ui.theme.SigmaGlassBorder
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaRedDark
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaWhite
import com.example.voice.AssistantSessionState

@Composable
fun MainScreen(
    state: AssistantSessionState,
    rmsLevel: Float,
    spokenText: String,
    responseText: String,
    isListening: Boolean,
    animationQuality: AnimationQuality,
    reduceMotion: Boolean,
    onMicClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    onQuickActionClick: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF040406), Color(0xFF0C0709), Color(0xFF050507))
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP HEADER: BRANDING & STATUS PILL & SETTINGS GEAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SIGM",
                        color = SigmaWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "A",
                        color = SigmaNeonRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Text(
                    text = "AI ASSISTANT",
                    color = Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SigmaStatusPill(state = state)
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // CENTER: ANIMATED CYBER DAEMON ORB WITH CONCENTRIC ENERGY RINGS
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SigmaOrb(
                state = state,
                rmsLevel = rmsLevel,
                size = 250.dp,
                quality = animationQuality,
                reduceMotion = reduceMotion
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Context Prompt or Spoken Response
            val statusDisplay = when {
                spokenText.isNotEmpty() -> "\"$spokenText\""
                responseText.isNotEmpty() -> responseText
                state == AssistantSessionState.LISTENING -> "Listening... Speak now"
                state == AssistantSessionState.THINKING -> "Analyzing your request..."
                state == AssistantSessionState.SPEAKING -> "Here's what I found..."
                state == AssistantSessionState.EXECUTING -> "Performing action..."
                state == AssistantSessionState.SUCCESS -> "Action completed successfully!"
                state == AssistantSessionState.ERROR -> "Couldn't complete request. Try again."
                else -> "How can I help you today?"
            }

            Text(
                text = statusDisplay,
                color = when (state) {
                    AssistantSessionState.LISTENING -> SigmaNeonRedBright
                    AssistantSessionState.THINKING -> Color(0xFF2979FF)
                    AssistantSessionState.SUCCESS -> Color(0xFF00E676)
                    AssistantSessionState.ERROR -> Color(0xFFFF1744)
                    else -> SigmaWhite
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Dynamic Audio Waveform during voice activity
            if (isListening || state == AssistantSessionState.SPEAKING || state == AssistantSessionState.THINKING) {
                Spacer(modifier = Modifier.height(10.dp))
                SigmaWaveform(
                    rmsLevel = rmsLevel,
                    isListening = isListening,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // 4 QUICK ACTION GLASS PILLS (Apps, Contacts, Screen, More)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MockupQuickActionButton(
                label = "Apps",
                icon = Icons.Default.Apps,
                onClick = { onQuickActionClick("APPS") }
            )
            MockupQuickActionButton(
                label = "Contacts",
                icon = Icons.Default.PermContactCalendar,
                onClick = { onQuickActionClick("CONTACTS") }
            )
            MockupQuickActionButton(
                label = "Screen",
                icon = Icons.Default.PhoneAndroid,
                onClick = { onQuickActionClick("SCREEN") }
            )
            MockupQuickActionButton(
                label = "More",
                icon = Icons.Default.MoreHoriz,
                onClick = { onQuickActionClick("MORE") }
            )
        }

        // BOTTOM BAR INPUT CAPSULE WITH CENTER MIC AND SEND
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF14080B))
                .border(1.5.dp, SigmaNeonRed.copy(alpha = 0.65f), RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Text Input field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            "Type a message...",
                            color = Color(0xFF757575),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = SigmaWhite,
                        unfocusedTextColor = SigmaWhite,
                        cursorColor = SigmaNeonRed,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                onSendCommand(textInput.trim())
                                textInput = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                // Center Glowing Mic Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isListening) listOf(SigmaNeonRedBright, SigmaRedDark)
                                else listOf(SigmaNeonRed, Color(0xFF8B0021))
                            )
                        )
                        .border(
                            1.5.dp,
                            if (isListening) SigmaWhite else SigmaNeonRedBright,
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = SigmaWhite),
                            onClick = onMicClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop" else "Listen",
                        tint = SigmaWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send Icon
                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendCommand(textInput.trim())
                            textInput = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = SigmaNeonRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MockupQuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF13090C))
                .border(1.dp, SigmaNeonRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SigmaWhite,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
