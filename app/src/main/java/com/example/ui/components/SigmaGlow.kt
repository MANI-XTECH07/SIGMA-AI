package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SigmaNeonRed

@Composable
fun SigmaGlow(
    modifier: Modifier = Modifier,
    radius: Dp = 120.dp,
    color: Color = SigmaNeonRed,
    alpha: Float = 0.35f
) {
    Box(
        modifier = modifier
            .size(radius)
            .blur(32.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}
