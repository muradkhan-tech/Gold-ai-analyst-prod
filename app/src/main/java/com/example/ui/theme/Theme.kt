package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GoldDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1E1300),
    primaryContainer = Color(0xFF452B00),
    onPrimaryContainer = GoldLight,
    secondary = GoldAccent,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF3F3004),
    onSecondaryContainer = GoldLight,
    tertiary = BullishGreen,
    onTertiary = Color(0xFF003822),
    background = DarkTerminalBackground,
    onBackground = DarkTextPrimary,
    surface = DarkTerminalSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkTerminalSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkTerminalOutline,
    error = BearishRed,
    onError = Color.White
)

val GoldLightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261900),
    secondary = GoldPrimary,
    onSecondary = Color.White,
    tertiary = BullishGreen,
    onTertiary = Color.White,
    background = LightTerminalBackground,
    onBackground = LightTextPrimary,
    surface = LightTerminalSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightTerminalSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightTerminalOutline,
    error = BearishRed,
    onError = Color.White
)

@Composable
fun GoldAiAnalystTheme(
    darkTheme: Boolean = true, // Default to sleek financial dark mode terminal
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GoldDarkColorScheme else GoldLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
