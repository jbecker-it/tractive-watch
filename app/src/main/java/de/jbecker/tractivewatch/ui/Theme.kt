package de.jbecker.tractivewatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v1.11 Design-Refresh: moderne Material-3-Dunkel-Palette (Tractive-Blau als
 * Akzent) + Wear-optimierte Typografie. Alle Farben als Tonal-Paare, damit
 * Button-/Card-/Text-Kombinationen automatisch gut lesbar sind.
 */
private val TractiveColors = darkColorScheme(
    primary = Color(0xFF8AD8FF),
    onPrimary = Color(0xFF00283D),
    primaryContainer = Color(0xFF0A3D5C),
    onPrimaryContainer = Color(0xFFC9E9FF),
    secondary = Color(0xFFA6C6DA),
    onSecondary = Color(0xFF152A37),
    secondaryContainer = Color(0xFF12303F),
    onSecondaryContainer = Color(0xFFD2E8F7),
    tertiary = Color(0xFFB9BDE8),
    onTertiary = Color(0xFF24274D),
    background = Color(0xFF0A0F14),
    onBackground = Color(0xFFE3EAEF),
    surface = Color(0xFF10161C),
    onSurface = Color(0xFFE3EAEF),
    surfaceVariant = Color(0xFF1B2530),
    onSurfaceVariant = Color(0xFFB9C7D2),
    surfaceContainer = Color(0xFF182028),
    outline = Color(0xFF4A5A68),
    outlineVariant = Color(0xFF2A3642),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Wear-optimierte Typografie: 10-14sp statt der 16-28sp-Phone-Defaults,
 * damit alles in den runden Sicherheitsbereich passt (>= 10sp = Wear-Minimum).
 */
private val TractiveTypography = Typography(
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp),
)

@Composable
fun TractiveWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TractiveColors,
        typography = TractiveTypography,
        content = content,
    )
}
