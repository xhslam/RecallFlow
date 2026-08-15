package com.recallflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WarmWhite = Color(0xFFFCFBF8)
val SurfaceWarm = Color(0xFFF4F2ED)
val Ink = Color(0xFF20201E)
val InkMuted = Color(0xFF6E6C67)
val Sage = Color(0xFF47685A)
val SageLight = Color(0xFFDCE9E1)
val Amber = Color(0xFF8B651C)
val AmberLight = Color(0xFFF5E9CD)
val Brick = Color(0xFFA94B42)
val BrickLight = Color(0xFFF7DEDA)
val OutlineSoft = Color(0xFFD9D6CF)

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = WarmWhite,
    primaryContainer = SageLight,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = WarmWhite,
    secondaryContainer = AmberLight,
    error = Brick,
    errorContainer = BrickLight,
    background = WarmWhite,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    surfaceVariant = SurfaceWarm,
    outline = OutlineSoft
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAED2BD),
    onPrimary = Color(0xFF173C2B),
    primaryContainer = Color(0xFF305543),
    onPrimaryContainer = Color(0xFFE0F1E5),
    secondary = Color(0xFFECC984),
    onSecondary = Color(0xFF4B3510),
    error = Color(0xFFFFB4AA),
    background = Color(0xFF1B1C1A),
    onBackground = Color(0xFFE4E3DE),
    surface = Color(0xFF1B1C1A),
    onSurface = Color(0xFFE4E3DE),
    surfaceVariant = Color(0xFF30312D)
)

@Composable
fun RecallFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = RecallFlowTypography, content = content)
}
