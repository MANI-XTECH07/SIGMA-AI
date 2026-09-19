package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SigmaGlassBorder
import com.example.ui.theme.SigmaGlassBorderSubtle
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaSurfaceBlack
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaTextTertiary
import com.example.ui.theme.SigmaWhite

enum class PermissionCardStatus {
    GRANTED,
    REQUIRED,
    OPTIONAL,
    DISABLED,
    OPEN_SETTINGS,
    NOT_SUPPORTED
}

@Composable
fun SigmaPermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    status: PermissionCardStatus,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    customActionLabel: String? = null,
    isOptional: Boolean = false,
    categoryTag: String? = null,
    technicalNote: String? = null
) {
    val isGranted = status == PermissionCardStatus.GRANTED

    val (statusColor, badgeBg, badgeText) = when (status) {
        PermissionCardStatus.GRANTED -> Triple(SigmaStatusOnline, Color(0xFF042614), "ONLINE")
        PermissionCardStatus.REQUIRED -> Triple(SigmaNeonRed, Color(0xFF2E080E), "REQUIRED")
        PermissionCardStatus.OPTIONAL -> Triple(Color(0xFF00E5FF), Color(0xFF051C24), "OPTIONAL")
        PermissionCardStatus.DISABLED -> Triple(SigmaNeonRed, Color(0xFF2E080E), if (isOptional) "OPTIONAL" else "REQUIRED")
        PermissionCardStatus.OPEN_SETTINGS -> Triple(Color(0xFFFFB300), Color(0xFF261A02), "CONFIGURE")
        PermissionCardStatus.NOT_SUPPORTED -> Triple(SigmaTextSecondary, Color(0xFF1B1B1F), "UNSUPPORTED")
    }

    val iconBoxBg = when {
        isGranted -> Brush.linearGradient(listOf(Color(0xFF0A3319), Color(0xFF02170B)))
        isOptional -> Brush.linearGradient(listOf(Color(0xFF042C38), Color(0xFF01141A)))
        else -> Brush.linearGradient(listOf(Color(0xFF380811), Color(0xFF1A0206)))
    }

    val cardBorder = if (isGranted) {
        SigmaGlassBorderSubtle
    } else {
        statusColor.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14141B).copy(alpha = 0.95f),
                        Color(0xFF0E0E14).copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Futuristic Icon Container
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBoxBg)
                            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) SigmaStatusOnline else statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = SigmaWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )

                            if (categoryTag != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• $categoryTag",
                                    color = SigmaTextTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeBg)
                                    .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = badgeText,
                                        color = statusColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                            }

                            if (technicalNote != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = technicalNote,
                                    color = SigmaTextTertiary,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Action Button / Status Badge
                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF062312))
                            .border(1.dp, SigmaStatusOnline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                                    listOf(
                                        if (isOptional) Color(0xFF00838F) else SigmaNeonRedBright,
                                        if (isOptional) Color(0xFF004D40) else Color(0xFF8B0000)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                (if (isOptional) Color(0xFF00E5FF) else SigmaNeonRed).copy(alpha = 0.8f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onActionClick)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = customActionLabel ?: if (isOptional) "Enable" else "Grant",
                                color = SigmaWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = SigmaWhite,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = SigmaTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

