package de.jbecker.tractivewatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TractiveColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF002A3D),
    primaryContainer = Color(0xFF003349),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF00243A),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3F2FD),
    surface = Color(0xFF1A2026),
    onSurface = Color(0xFFE3F2FD),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF3E0000),
)

@Composable
fun TractiveWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TractiveColors, content = content)
}
