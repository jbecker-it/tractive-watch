package de.jbecker.tractivewatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * v1.12: Wear-M3-Farbpalette (dunkel, für Pixel Watch 40/45 mm).
 *
 * [ColorScheme] in wear-m3 1.6.x ist eine public class mit Defaults — wir
 * konstruieren über den No-Arg-Konstruktor und [ColorScheme.copy] (beide im
 * wear-m3-Quelljar verifiziert). Kein Phone-Material3.
 */
private val wearColorScheme = ColorScheme().copy(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF003447),
    primaryContainer = Color(0xFF004965),
    onPrimaryContainer = Color(0xFFCDE7FF),
    secondary = Color(0xFF6DD5A8),
    onSecondary = Color(0xFF003826),
    secondaryContainer = Color(0xFF004F38),
    onSecondaryContainer = Color(0xFFB7F8D8),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3E2600),
    tertiaryContainer = Color(0xFF523200),
    onTertiaryContainer = Color(0xFFFFDDB0),
    surfaceContainerLow = Color(0xFF11171C),
    surfaceContainer = Color(0xFF1A2127),
    surfaceContainerHigh = Color(0xFF252D33),
    onSurface = Color(0xFFE7F1F7),
    onSurfaceVariant = Color(0xFFB9C7D2),
    outline = Color(0xFF8195A6),
    outlineVariant = Color(0xFF455766),
    background = Color(0xFF0A0F14),
    onBackground = Color(0xFFE7F1F7),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * App-Theme. Stellt die Wear-M3-Palette bereit; die Wear-Standard-Typografie
 * (für die runde Watch optimiert) wird von [MaterialTheme] verwendet.
 */
@Composable
fun TractiveWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = wearColorScheme,
        content = content,
    )
}
