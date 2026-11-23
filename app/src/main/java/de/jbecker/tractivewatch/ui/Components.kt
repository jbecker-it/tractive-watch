package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * v1.12: Geteilte Wear-UI-Bausteine (nur Wear Compose, kein Phone-Material3).
 */

/** Kleiner Verbindungs-Status-Punkt (grün = live, grau = getrennt/verbindet). */
@Composable
fun StatusDot(live: Boolean) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(if (live) Color(0xFF34C759) else Color(0xFF8E8E93)),
    )
}

/**
 * LED / Piep / Live-Umschalter mit Haptic-Feedback (Wear [TextToggleButton]).
 *
 * @param label Beschriftung (z. B. "LED", "Piep", "Live")
 * @param checked aktueller Zustand
 * @param onToggle wird mit dem NEUEN Zustand aufgerufen; ruft den Befehl beim Tracker ab
 */
@Composable
fun CommandToggle(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    TextToggleButton(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle(it)
        },
    ) {
        Text(label)
    }
}
