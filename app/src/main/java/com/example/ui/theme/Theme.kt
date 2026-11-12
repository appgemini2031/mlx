package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF371B66),
    onSecondaryContainer = NeonPurple,
    tertiary = EmeraldGreen,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = Color(0xFFE6EDF8),
    surface = DarkSurface,
    onSurface = Color(0xFFE6EDF8),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0BDD4),
    outline = DarkCardBorder,
    error = CyberPink,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00758A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5FF),
    onPrimaryContainer = Color(0xFF002026),
    secondary = Color(0xFF651FFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF22005D),
    tertiary = Color(0xFF00894B),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF131824),
    surface = LightSurface,
    onSurface = Color(0xFF131824),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF434E62),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFD50000),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek dark cyber aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
