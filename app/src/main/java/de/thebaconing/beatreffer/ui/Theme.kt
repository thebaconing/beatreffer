package de.thebaconing.beatreffer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF4B942)
val Navy = Color(0xFF1F2A44)
val Good = Color(0xFF3DBE6E)
val Warn = Color(0xFFE8A33D)
val Bad = Color(0xFFE5534B)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Navy,
    secondary = Color(0xFF8FA6D9),
    background = Color(0xFF121722),
    surface = Color(0xFF121722),
    surfaceVariant = Color(0xFF232B3D),
    surfaceContainer = Color(0xFF1B2232),
    surfaceContainerHigh = Color(0xFF232B3D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB57A00),
    onPrimary = Color.White,
    secondary = Navy,
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFF7F5F0),
    surfaceVariant = Color(0xFFE9E4D8),
    surfaceContainer = Color(0xFFEFEBE2),
    surfaceContainerHigh = Color(0xFFE9E4D8),
)

@Composable
fun TaktTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
