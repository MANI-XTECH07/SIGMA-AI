package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaWhite
import com.example.voice.AssistantSessionState

@Composable
fun SigmaStatusPill(
    state: AssistantSessionState,
    modifier: Modifier = Modifier
) {
    val (symbol, statusText, statusColor) = when (state) {
        AssistantSessionState.ONLINE -> Triple("•", "IDLE", Color(0xFF00E676))
        AssistantSessionState.LISTENING -> Triple("+", "LISTENING", SigmaNeonRedBright)
        AssistantSessionState.THINKING -> Triple("+", "THINKING", Color(0xFF2979FF))
        AssistantSessionState.EXECUTING -> Triple("◎", "EXECUTING", SigmaNeonRed)
        AssistantSessionState.SPEAKING -> Triple("★", "SPEAKING", Color(0xFFFF0055))
        AssistantSessionState.SUCCESS -> Triple("✓", "SUCCESS", Color(0xFF00E676))
        AssistantSessionState.ERROR -> Triple("⊗", "ERROR", Color(0xFFFF1744))
        AssistantSessionState.SLEEP -> Triple("•", "STANDBY", Color.Gray)
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, label = "StatusPillColor")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14070A).copy(alpha = 0.85f))
            .border(1.dp, animatedColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = symbol,
                color = animatedColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = SigmaWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}
