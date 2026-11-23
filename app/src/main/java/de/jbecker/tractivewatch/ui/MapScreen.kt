package de.jbecker.tractivewatch.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.12: Karten-Screen (Wear Compose).
 *
 * Zeigt den OSM-Kartenausschnitt (geschützte [OsmMap]-Logik), Zoom-Buttons,
 * LED/Piep-Schnellbefehle mit Haptic, Genauigkeit und Aktionen
 * ("Befehle", "In OSM öffnen", "Beenden").
 */
@Composable
fun MapScreen(
    repo: Repo,
    index: Int,
    onOpenPet: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    val ui = TrackerUi
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScalingLazyListState()

    val pet = remember(index) { ui.pets.getOrNull(index) ?: ui.pets.firstOrNull() }
    val trackerId: String? = pet?.trackerId

    var zoom by remember { mutableStateOf(14) }

    LaunchedEffect(Unit) {
        delay(150)
        if (trackerId != null) {
            runCatching { withContext(Dispatchers.IO) { repo.client.posReport(trackerId) } }
                .onFailure { AppLog.record(context, "posReport: ${it.javaClass.simpleName}") }
        }
    }
    DisposableEffect(Unit) {
        repo.startChannel()
        onDispose { }
    }

    fun send(cmd: String) {
        val tid = trackerId ?: return
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

                val pos = ui.position[tid]
                if (pos != null) {
                    item {
                        OsmMap(pos = pos, zoomOverride = zoom)
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { zoom = (zoom - 1).coerceAtLeast(3) }, modifier = Modifier.weight(1f)) {
                            Text("−")
                        }
                        Text(
                            "Zoom $zoom",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(2f),
                        )
                        TextButton(onClick = { zoom = (zoom + 1).coerceAtMost(18) }, modifier = Modifier.weight(1f)) {
                            Text("+")
                        }
                    }
                }

                item {
                    CommandToggle("LED", checked = ui.led[tid] == true) {
                        ui.led[tid] = !(ui.led[tid] ?: false)
                        send("led")
                    }
                }
                item {
                    CommandToggle("Piep", checked = ui.buzzer[tid] == true) {
                        ui.buzzer[tid] = !(ui.buzzer[tid] ?: false)
                        send("buzzer")
                    }
                }

                if (pos != null) {
                    item {
                        Text(
                            "Genauigkeit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pos.accuracy != null) {
                        item {
                            Text(
                                "± ${pos.accuracy} m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (pos.accuracy == null) {
                        item {
                            Text(
                                "unbekannt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Button(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { repo.client.posReport(tid) } }
                                .onFailure { AppLog.record(context, "posReport: ${it.javaClass.simpleName}") }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aktualisieren")
                    }
                }

                item {
                    Button(onClick = { onOpenPet(index) }) {
                        Text("Befehle")
                    }
                }

                item {
                    Button(onClick = {
                        val uri = android.net.Uri.parse("https://www.openstreetmap.org")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }) {
                        Text("In OSM öffnen")
                    }
                }

                item {
                    TextButton(onClick = onFinished) { Text("Beenden") }
                }
            }
        }
    }
}
