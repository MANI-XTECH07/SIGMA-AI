package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SigmaDarkColorScheme = darkColorScheme(
    primary = SigmaNeonRed,
    onPrimary = SigmaWhite,
    primaryContainer = SigmaRedDark,
    onPrimaryContainer = SigmaWhite,
    secondary = SigmaNeonRedBright,
    onSecondary = SigmaWhite,
    background = SigmaBlack,
    onBackground = SigmaWhite,
    surface = SigmaDeepBlack,
    onSurface = SigmaWhite,
    surfaceVariant = SigmaSurfaceBlack,
    onSurfaceVariant = SigmaTextSecondary,
    outline = SigmaGlassBorder,
    error = SigmaStatusError,
    onError = Color.White
)

@Composable
fun SigmaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SigmaDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SigmaTheme(content = content)
}
