package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text

/**
 * Geteilte Wear-UI-Bausteine (nur Wear Compose, kein Phone-Material3).
 */

/**
 * Standard-Screen: ScreenScaffold (TimeText, ScrollIndicator, Crown-Scrolling)
 * + ScalingLazyColumn mit korrektem Rand-Padding für das runde Display.
 */
@Composable
fun WearList(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { contentPadding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/** Zentrierte Überschrift oben in einer Liste. */
@Composable
fun Header(text: String) {
    ListHeader {
        Text(
            text,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Kleiner zentrierter Hinweis-/Fehlertext. */
@Composable
fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

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
 * LED / Ton / Live als volle Wear-[SwitchButton]-Zeile mit Haptic-Feedback.
 *
 * [onToggle] schickt den Befehl an den Tracker; der Schalterzustand kommt
 * ausschließlich aus [checked] (UI-State wird erst bei Erfolg geändert).
 */
@Composable
fun CommandSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    secondary: String? = null,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SwitchButton(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        secondaryLabel = secondary?.let { { Text(it) } },
        label = { Text(label) },
    )
}
