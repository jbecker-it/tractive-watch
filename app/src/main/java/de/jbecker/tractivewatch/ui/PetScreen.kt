package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.12: Pet-Screen (Wear Compose) — Befehle für einen einzelnen Tracker.
 *
 * LED / Piep / Live-Umschalter mit Haptic-Feedback ([CommandToggle]),
 * Akkustand, letzte Position, "Karte" und "Beenden".
 */
@Composable
fun PetScreen(
    repo: Repo,
    index: Int,
    onOpenMap: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    val ui = TrackerUi
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScalingLazyListState()

    val pet = remember(index) { ui.pets.getOrNull(index) ?: ui.pets.firstOrNull() }
    val trackerId: String? = pet?.trackerId

    LaunchedEffect(trackerId) {
        if (trackerId != null) {
            runCatching { withContext(Dispatchers.IO) { repo.client.posReport(trackerId) } }
                .onFailure { AppLog.record(context, "posReport: ${it.javaClass.simpleName}") }
        }
    }
    DisposableEffect(Unit) {
        repo.startChannel()
        onDispose { }
    }

    fun sendCommand(cmd: String, tid: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.toggle(tid, cmd) } }
                .onFailure { AppLog.record(context, "toggle($tid,$cmd): ${it.javaClass.simpleName}") }
        }
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        ScalingLazyColumn(state = scrollState, contentPadding = contentPadding) {
            if (pet == null) {
                item {
                    Text("Kein Tracker gefunden", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    TextButton(onClick = onFinished) { Text("Zurück") }
                }
            } else {
                val tid = pet.trackerId
                item {
                    Text(pet.name, style = MaterialTheme.typography.titleLarge)
                }

                if (ui.battery[tid] != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Akkustand",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${ui.battery[tid]}%", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (ui.position[tid] != null) {
                    item {
                        Text(
                            "Letzte Position",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val t = ui.position[tid]?.time?.let { fmtTime(it) }
                    if (t != null) {
                        item {
                            Text(
                                t,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    CommandToggle("LED", checked = ui.led[tid] == true) { sendCommand("led", tid) }
                }
                item {
                    CommandToggle("Piep", checked = ui.buzzer[tid] == true) { sendCommand("buzzer", tid) }
                }
                item {
                    CommandToggle("Live", checked = ui.live[tid] == true) { sendCommand("live", tid) }
                }

                item {
                    Button(onClick = { onOpenMap(index) }) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Karte")
                    }
                }

                item {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    if (ui.live[tid] == true) repo.toggle(tid, "live")
                                }
                            }.onFailure { AppLog.record(context, "beenden: live-aus: ${it.javaClass.simpleName}") }
                        }
                        onFinished()
                    }) {
                        Text("Beenden")
                    }
                }
            }
        }
    }
}
