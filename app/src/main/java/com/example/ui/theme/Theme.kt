package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GoldDarkColorScheme = darkColorScheme(
    primary = NavyBrandLight,
    onPrimary = Color.White,
    primaryContainer = NavyBrand,
    onPrimaryContainer = Color.White,
    secondary = GoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3F3004),
    onSecondaryContainer = GoldLight,
    tertiary = MintAccent,
    onTertiary = Color.White,
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
    primary = NavyBrand,
    onPrimary = Color.White,
    primaryContainer = LightTerminalSurfaceVariant,
    onPrimaryContainer = NavyBrand,
    secondary = MintAccent,
    onSecondary = Color.White,
    secondaryContainer = MintAccentBg,
    onSecondaryContainer = MintAccent,
    tertiary = GoldAccent,
    onTertiary = Color.White,
    background = LightTerminalBackground, // #F8FAFC
    onBackground = LightTextPrimary, // #0F172A
    surface = LightTerminalSurface, // #FFFFFF
    onSurface = LightTextPrimary,
    surfaceVariant = LightTerminalSurfaceVariant, // #F1F5F9
    onSurfaceVariant = LightTextSecondary, // #475569
    outline = LightTerminalOutline, // #E2E8F0
    error = BearishRed,
    onError = Color.White
)

@Composable
fun GoldAiAnalystTheme(
    darkTheme: Boolean = false, // Default to soft, bright institutional light theme (#F8FAFC)
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GoldDarkColorScheme else GoldLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
