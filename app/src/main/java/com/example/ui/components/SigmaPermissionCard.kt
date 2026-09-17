package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SigmaGlassBorder
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaTextSecondary
import com.example.ui.theme.SigmaWhite

enum class PermissionCardStatus {
    READY,
    REQUIRES_PERMISSION,
    DISABLED,
    NOT_SUPPORTED
}

@Composable
fun SigmaPermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    status: PermissionCardStatus,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (status) {
        PermissionCardStatus.READY -> "READY" to SigmaStatusOnline
        PermissionCardStatus.REQUIRES_PERMISSION -> "GRANT" to SigmaNeonRed
        PermissionCardStatus.DISABLED -> "DISABLED" to SigmaNeonRed
        PermissionCardStatus.NOT_SUPPORTED -> "UNAVAILABLE" to SigmaTextSecondary
    }

    SigmaGlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (status == PermissionCardStatus.READY) SigmaGlassBorder else SigmaNeonRed.copy(alpha = 0.5f)
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (status == PermissionCardStatus.READY) SigmaStatusOnline else SigmaNeonRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = SigmaWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (status == PermissionCardStatus.READY) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = description,
                        color = SigmaTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            SigmaButton(
                text = statusLabel,
                onClick = onActionClick,
                isPrimary = status != PermissionCardStatus.READY,
                modifier = Modifier.height(38.dp)
            )
        }
    }
}
