package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.AnimationQuality
import com.example.ui.components.SigmaGlow
import com.example.ui.theme.SigmaNeonRed
import com.example.ui.theme.SigmaNeonRedBright
import com.example.ui.theme.SigmaRedDark
import com.example.ui.theme.SigmaStatusError
import com.example.ui.theme.SigmaStatusExecuting
import com.example.ui.theme.SigmaStatusOnline
import com.example.ui.theme.SigmaStatusThinking
import com.example.ui.theme.SigmaWhite
import com.example.voice.AssistantSessionState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SigmaOrb(
    state: AssistantSessionState,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    quality: AnimationQuality = AnimationQuality.HIGH,
    reduceMotion: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SigmaOrbStateTransitions")

    // Breathing pulse for idle & continuous float
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbBreathing"
    )

    // Vertical hover float
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbHover"
    )

    // Rotation angle for energy rings and swirling flame wisps
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 8000 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRingRotation"
    )

    // Fast speech syllable pulse
    val speechPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SpeechMouthPulse"
    )

    // Eye glow shimmer
    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EyeGlowShimmer"
    )

    // Determine theme colors based on state matching mockup
    val (primaryColor, outerRingColor) = when (state) {
        AssistantSessionState.ONLINE -> SigmaNeonRed to SigmaNeonRed
        AssistantSessionState.LISTENING -> SigmaNeonRedBright to SigmaNeonRedBright
        AssistantSessionState.THINKING -> SigmaNeonRed to Color(0xFF2979FF) // Cyan/Blue thinking halo
        AssistantSessionState.EXECUTING -> SigmaNeonRedBright to SigmaNeonRed
        AssistantSessionState.SPEAKING -> Color(0xFFFF0055) to SigmaNeonRed
        AssistantSessionState.SUCCESS -> Color(0xFF00E676) to Color(0xFF00E676) // Emerald green
        AssistantSessionState.ERROR -> Color(0xFFFF1744) to Color(0xFFFF5252) // Alert red
        AssistantSessionState.SLEEP -> Color(0xFF888888) to Color(0xFF444444)
    }

    val dynamicAudioMultiplier = if (state == AssistantSessionState.LISTENING) {
        (1f + (rmsLevel.coerceAtLeast(0f) / 10f) * 0.35f)
    } else {
        breathing
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft atmospheric glow behind the orb
        if (quality != AnimationQuality.LOW) {
            SigmaGlow(
                radius = size * 1.4f,
                color = primaryColor,
                alpha = if (state == AssistantSessionState.LISTENING) 0.60f else 0.38f
            )
        }

        Canvas(modifier = Modifier.size(size)) {
            val canvasW = size.toPx()
            val canvasH = size.toPx()
            val orbCenterY = canvasH * 0.44f + (if (reduceMotion) 0f else floatOffset)
            val orbCenterX = canvasW * 0.5f
            val orbRadius = (canvasW * 0.28f) * dynamicAudioMultiplier

            // ==========================================
            // 1. FLOOR HOLOGRAPHIC ENERGY DAIS (Perspective Rings)
            // ==========================================
            val floorY = canvasH * 0.82f
            val maxFloorWidth = canvasW * 0.72f
            val floorHeight = canvasH * 0.18f

            // Outer Floor Ring
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(orbCenterX, floorY),
                    radius = maxFloorWidth / 2f
                ),
                topLeft = Offset(orbCenterX - maxFloorWidth / 2f, floorY - floorHeight / 2f),
                size = Size(maxFloorWidth, floorHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // Middle Floor Ring with dashed ticks
            val midFloorWidth = maxFloorWidth * 0.72f
            val midFloorHeight = floorHeight * 0.72f
            drawOval(
                color = primaryColor.copy(alpha = 0.65f),
                topLeft = Offset(orbCenterX - midFloorWidth / 2f, floorY - midFloorHeight / 2f),
                size = Size(midFloorWidth, midFloorHeight),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Inner Brightest Floor Core Ring
            val innerFloorWidth = maxFloorWidth * 0.45f
            val innerFloorHeight = floorHeight * 0.45f
            drawOval(
                color = primaryColor.copy(alpha = 0.85f),
                topLeft = Offset(orbCenterX - innerFloorWidth / 2f, floorY - innerFloorHeight / 2f),
                size = Size(innerFloorWidth, innerFloorHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // Floor energy light pillars / beams
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.35f))
                ),
                start = Offset(orbCenterX, orbCenterY + orbRadius),
                end = Offset(orbCenterX, floorY),
                strokeWidth = 3.dp.toPx()
            )

            // ==========================================
            // 2. SWIRLING ENERGY FLAMES & ARCS (Mockup Corona)
            // ==========================================
            val arcAngles = listOf(0f, 72f, 144f, 216f, 288f)
            arcAngles.forEachIndexed { i, angleOffset ->
                val curAngle = rotationAngle * (if (i % 2 == 0) 1f else -0.8f) + angleOffset
                val arcRadius = orbRadius * (1.18f + (i * 0.05f))
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            outerRingColor.copy(alpha = 0.75f),
                            SigmaWhite.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    ),
                    startAngle = curAngle,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(orbCenterX - arcRadius, orbCenterY - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = (2.5f + i * 0.5f).dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // ==========================================
            // 3. THE SPHERICAL CORE (Pitch Black Gloss Orb)
            // ==========================================
            // Base shadow
            drawCircle(
                color = Color(0xFF040406),
                radius = orbRadius,
                center = Offset(orbCenterX, orbCenterY)
            )

            // Volumetric Sphere Shading with Red Rim Reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF16161C),
                        Color(0xFF09090C),
                        Color(0xFF000000),
                        primaryColor.copy(alpha = 0.85f),
                        primaryColor
                    ),
                    center = Offset(orbCenterX - orbRadius * 0.25f, orbCenterY - orbRadius * 0.25f),
                    radius = orbRadius * 1.05f
                ),
                radius = orbRadius,
                center = Offset(orbCenterX, orbCenterY)
            )

            // Top-left Specular Gloss Highlight
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SigmaWhite.copy(alpha = 0.55f),
                        SigmaWhite.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    start = Offset(orbCenterX - orbRadius * 0.55f, orbCenterY - orbRadius * 0.55f),
                    end = Offset(orbCenterX - orbRadius * 0.2f, orbCenterY - orbRadius * 0.2f)
                ),
                topLeft = Offset(orbCenterX - orbRadius * 0.58f, orbCenterY - orbRadius * 0.62f),
                size = Size(orbRadius * 0.55f, orbRadius * 0.32f)
            )

            // ==========================================
            // 4. THE CYBER EYE PAIR & MOUTH (Signature Expression)
            // ==========================================
            val eyeDistX = orbRadius * 0.36f
            val eyeCenterY = orbCenterY - orbRadius * 0.04f
            val eyeWidth = orbRadius * 0.30f
            val eyeHeight = orbRadius * 0.16f

            when (state) {
                // SUCCESS STATE: Happy curved cyber arcs (^ ^)
                AssistantSessionState.SUCCESS -> {
                    val leftPath = Path().apply {
                        moveTo(orbCenterX - eyeDistX - eyeWidth / 2f, eyeCenterY + eyeHeight / 3f)
                        quadraticTo(
                            orbCenterX - eyeDistX, eyeCenterY - eyeHeight,
                            orbCenterX - eyeDistX + eyeWidth / 2f, eyeCenterY + eyeHeight / 3f
                        )
                    }
                    val rightPath = Path().apply {
                        moveTo(orbCenterX + eyeDistX - eyeWidth / 2f, eyeCenterY + eyeHeight / 3f)
                        quadraticTo(
                            orbCenterX + eyeDistX, eyeCenterY - eyeHeight,
                            orbCenterX + eyeDistX + eyeWidth / 2f, eyeCenterY + eyeHeight / 3f
                        )
                    }
                    drawPath(leftPath, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(rightPath, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                    // Gentle smiling mouth
                    val mouthPath = Path().apply {
                        moveTo(orbCenterX - orbRadius * 0.14f, orbCenterY + orbRadius * 0.28f)
                        quadraticTo(
                            orbCenterX, orbCenterY + orbRadius * 0.38f,
                            orbCenterX + orbRadius * 0.14f, orbCenterY + orbRadius * 0.28f
                        )
                    }
                    drawPath(mouthPath, primaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }

                // ERROR STATE: Furrowed warning angled cyber eyes (> <)
                AssistantSessionState.ERROR -> {
                    val leftPath = Path().apply {
                        moveTo(orbCenterX - eyeDistX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f)
                        lineTo(orbCenterX - eyeDistX + eyeWidth / 2f, eyeCenterY + eyeHeight / 2f)
                        lineTo(orbCenterX - eyeDistX - eyeWidth / 2f, eyeCenterY + eyeHeight * 0.8f)
                    }
                    val rightPath = Path().apply {
                        moveTo(orbCenterX + eyeDistX + eyeWidth / 2f, eyeCenterY - eyeHeight / 2f)
                        lineTo(orbCenterX + eyeDistX - eyeWidth / 2f, eyeCenterY + eyeHeight / 2f)
                        lineTo(orbCenterX + eyeDistX + eyeWidth / 2f, eyeCenterY + eyeHeight * 0.8f)
                    }
                    drawPath(leftPath, primaryColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(rightPath, primaryColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

                    // Small straight alert mouth
                    drawLine(
                        color = primaryColor,
                        start = Offset(orbCenterX - orbRadius * 0.12f, orbCenterY + orbRadius * 0.32f),
                        end = Offset(orbCenterX + orbRadius * 0.12f, orbCenterY + orbRadius * 0.32f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // STANDARD / LISTENING / THINKING / SPEAKING: Sleek Feline Cyber Eyes
                else -> {
                    // Left Eye (Almond cyber shape angled inwards)
                    val leftEyePath = Path().apply {
                        moveTo(orbCenterX - eyeDistX - eyeWidth * 0.6f, eyeCenterY - eyeHeight * 0.2f)
                        quadraticTo(
                            orbCenterX - eyeDistX, eyeCenterY - eyeHeight * 1.1f * eyeGlow,
                            orbCenterX - eyeDistX + eyeWidth * 0.5f, eyeCenterY + eyeHeight * 0.3f
                        )
                        quadraticTo(
                            orbCenterX - eyeDistX, eyeCenterY + eyeHeight * 0.7f,
                            orbCenterX - eyeDistX - eyeWidth * 0.6f, eyeCenterY - eyeHeight * 0.2f
                        )
                        close()
                    }

                    // Right Eye (Mirrored almond cyber shape)
                    val rightEyePath = Path().apply {
                        moveTo(orbCenterX + eyeDistX + eyeWidth * 0.6f, eyeCenterY - eyeHeight * 0.2f)
                        quadraticTo(
                            orbCenterX + eyeDistX, eyeCenterY - eyeHeight * 1.1f * eyeGlow,
                            orbCenterX + eyeDistX - eyeWidth * 0.5f, eyeCenterY + eyeHeight * 0.3f
                        )
                        quadraticTo(
                            orbCenterX + eyeDistX, eyeCenterY + eyeHeight * 0.7f,
                            orbCenterX + eyeDistX + eyeWidth * 0.6f, eyeCenterY - eyeHeight * 0.2f
                        )
                        close()
                    }

                    // Draw glowing eye interiors
                    drawPath(
                        leftEyePath,
                        brush = Brush.radialGradient(
                            colors = listOf(SigmaWhite, primaryColor),
                            center = Offset(orbCenterX - eyeDistX, eyeCenterY),
                            radius = eyeWidth
                        )
                    )
                    drawPath(
                        rightEyePath,
                        brush = Brush.radialGradient(
                            colors = listOf(SigmaWhite, primaryColor),
                            center = Offset(orbCenterX + eyeDistX, eyeCenterY),
                            radius = eyeWidth
                        )
                    )

                    // Mouth representation
                    val mouthY = orbCenterY + orbRadius * 0.26f
                    if (state == AssistantSessionState.SPEAKING) {
                        // Dynamic speech mouth oval
                        val dynamicMouthH = (orbRadius * 0.18f * speechPulse).coerceAtLeast(3.dp.toPx())
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(SigmaWhite, primaryColor),
                                center = Offset(orbCenterX, mouthY),
                                radius = orbRadius * 0.12f
                            ),
                            topLeft = Offset(orbCenterX - orbRadius * 0.12f, mouthY - dynamicMouthH / 2f),
                            size = Size(orbRadius * 0.24f, dynamicMouthH)
                        )
                    } else if (state == AssistantSessionState.LISTENING) {
                        // Small attentive triangle mouth
                        val mouthPath = Path().apply {
                            moveTo(orbCenterX - orbRadius * 0.08f, mouthY - orbRadius * 0.04f)
                            lineTo(orbCenterX + orbRadius * 0.08f, mouthY - orbRadius * 0.04f)
                            lineTo(orbCenterX, mouthY + orbRadius * 0.06f)
                            close()
                        }
                        drawPath(mouthPath, primaryColor)
                    } else {
                        // Sleek minimal cyber mouth slit in IDLE / THINKING
                        drawLine(
                            color = primaryColor.copy(alpha = 0.85f),
                            start = Offset(orbCenterX - orbRadius * 0.10f, mouthY),
                            end = Offset(orbCenterX + orbRadius * 0.10f, mouthY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // ==========================================
            // 5. FLOATING PARTICLES & SPARKS (Cyberpunk High Quality)
            // ==========================================
            if (quality == AnimationQuality.HIGH && !reduceMotion) {
                val particleCount = 12
                for (i in 0 until particleCount) {
                    val angleRad = Math.toRadians((i * (360.0 / particleCount) + rotationAngle * 0.9).toDouble())
                    val pDist = orbRadius * (1.25f + 0.15f * sin((i * 1.5).toDouble()).toFloat())
                    val px = orbCenterX + (pDist * cos(angleRad)).toFloat()
                    val py = orbCenterY + (pDist * sin(angleRad)).toFloat()

                    drawCircle(
                        color = if (i % 3 == 0) SigmaWhite.copy(alpha = 0.9f) else primaryColor.copy(alpha = 0.8f),
                        radius = (1.5f + (i % 2) * 1.5f).dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
