package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.SigmaAccessibilityService
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaStatusError
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

@Composable
fun AutomationScreen(
    onInspectScreen: () -> Unit,
    onExecuteAction: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAccessibilityActive = SigmaAccessibilityService.isServiceRunning

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "AUTOMATION ENGINE",
            color = SigmaWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Beast Loop (Observe • Plan • Act • Verify)",
            color = SigmaNeonRed,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Service Status Card
        SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isAccessibilityActive) SigmaStatusOnline else SigmaStatusError,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isAccessibilityActive) "Accessibility Connected" else "Service Inactive",
                            color = SigmaWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAccessibilityActive) "Ready for in-app automation" else "Enable SIGMA in Settings",
                            color = SigmaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                if (!isAccessibilityActive) {
                    SigmaButton(
                        text = "Enable",
                        onClick = onOpenAccessibilitySettings
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DIRECT AUTOMATION CONTROLS",
            color = SigmaWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Screen Inspection & Vision",
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Inspects visible UI hierarchy, clickable bounds, and screen text without fabrication.",
                        color = SigmaTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SigmaButton(
                        text = "Inspect Active Screen",
                        icon = Icons.Default.ScreenSearchDesktop,
                        onClick = onInspectScreen
                    )
                }
            }

            SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "System Navigation Gestures",
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SigmaButton(
                            text = "Home",
                            icon = Icons.Default.Home,
                            onClick = { onExecuteAction("HOME") },
                            modifier = Modifier.weight(1f)
                        )
                        SigmaButton(
                            text = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { onExecuteAction("BACK") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Scroll & Device Controls",
                        color = SigmaWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SigmaButton(
                            text = "Scroll Down",
                            icon = Icons.Default.ArrowDownward,
                            onClick = { onExecuteAction("SCROLL_DOWN") },
                            modifier = Modifier.weight(1f)
                        )
                        SigmaButton(
                            text = "Scroll Up",
                            icon = Icons.Default.ArrowUpward,
                            onClick = { onExecuteAction("SCROLL_UP") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SigmaButton(
                        text = "Lock Device",
                        icon = Icons.Default.Lock,
                        onClick = { onExecuteAction("LOCK_PHONE") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
