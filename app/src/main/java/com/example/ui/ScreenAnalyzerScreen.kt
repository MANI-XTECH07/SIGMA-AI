package com.example.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.screen.ScreenAnalysisManager
import com.example.screen.ScreenAnalysisResult
import com.example.service.SigmaAccessibilityService
import com.example.ui.components.SigmaButton
import com.example.ui.components.SigmaGlassCard
import com.example.ui.theme.SigmaBlack
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

@Composable
fun ScreenAnalyzerScreen(
    screenAnalysisManager: ScreenAnalysisManager,
    onStartMediaProjection: () -> Unit,
    onAskAiAboutScreen: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var screenAnalysis by remember { mutableStateOf<ScreenAnalysisResult?>(null) }
    val currentPackage = SigmaAccessibilityService.currentPackage

    fun refreshAnalysis() {
        screenAnalysis = screenAnalysisManager.analyzeCurrentScreen()
    }

    LaunchedEffect(Unit) {
        refreshAnalysis()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SigmaBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SigmaWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SCREEN ANALYZER",
                        color = SigmaWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Accessibility tree & OCR vision",
                        color = SigmaNeonRed,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = { refreshAnalysis() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = SigmaNeonRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Screen Capture Card
        SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Live Screen Capture Service",
                    color = SigmaWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enable MediaProjection screen streaming to allow Gemini Vision to analyze live app visuals and diagrams.",
                    color = SigmaTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SigmaButton(
                    text = "Authorize Screen Capture",
                    icon = Icons.Default.CameraAlt,
                    onClick = onStartMediaProjection,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Current Package & Node Metrics
        val analysis = screenAnalysis
        if (analysis != null) {
            SigmaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Package:", color = SigmaTextSecondary, fontSize = 12.sp)
                        Text(
                            text = currentPackage.ifEmpty { "com.example (SIGMA)" },
                            color = SigmaNeonRedBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Visible Text Elements:", color = SigmaTextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${analysis.visibleTexts.size} texts",
                            color = SigmaWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Clickable Controls:", color = SigmaTextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${analysis.clickableElements.size} interactive",
                            color = SigmaWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Input Fields:", color = SigmaTextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${analysis.editableFields.size} fields",
                            color = SigmaWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Screen Hierarchy Summary
            Text(
                text = "ACCESSIBILITY TREE SUMMARY",
                color = SigmaWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SigmaSurfaceBlack)
                    .border(1.dp, SigmaNeonRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = analysis.summary,
                    color = SigmaWhite,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SigmaButton(
                text = "Summarize Screen with Gemini",
                icon = Icons.Default.SmartToy,
                onClick = { onAskAiAboutScreen(analysis.summary) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Analyzing screen layout...",
                    color = SigmaTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
