package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaRedDark
import kotlin.math.max

@Composable
fun SigmaWaveform(
    rmsLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barsCount: Int = 18,
    waveformHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    // Convert real dB amplitude to normalized scale
    val normalizedRms = if (isListening) {
        max(0.15f, (rmsLevel + 2f) / 12f).coerceIn(0.15f, 1f)
    } else {
        0.08f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(waveformHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barsCount) {
            // Harmonic wave multiplier
            val distanceFactor = 1f - kotlin.math.abs((i - barsCount / 2f) / (barsCount / 2f))
            val targetHeight = (waveformHeight.value * (normalizedRms * distanceFactor.coerceAtLeast(0.2f))).coerceIn(4f, waveformHeight.value * 0.95f)

            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 80),
                label = "WaveformBarHeight"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SigmaNeonRedBright,
                                SigmaNeonRed,
                                SigmaRedDark
                            )
                        )
                    )
            )
        }
    }
}
