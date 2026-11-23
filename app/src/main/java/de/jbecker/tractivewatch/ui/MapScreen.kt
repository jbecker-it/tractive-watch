package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtAccuracy
import de.jbecker.tractivewatch.fmtTime
import de.jbecker.tractivewatch.friendlyMessage
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Karten-Screen (Wear Compose): OSM-Ausschnitt, Zoom, LED/Ton-Schnellbefehle,
 * Genauigkeit, Aktualisieren und Link zur OSM-Webkarte.
 */
@Composable
fun MapScreen(
    repo: Repo,
    index: Int,
    onFinished: () -> Unit,
) {
    val ui = TrackerUi
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pet = ui.pets.getOrNull(index)
    val tid = pet?.trackerId

    var zoom by remember { mutableIntStateOf(-1) } // -1 = automatisch nach Genauigkeit
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Display nur auf der Karte wach halten (nicht app-weit: Akku!)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(tid) {
        if (tid != null) {
            runCatching { repo.refreshPosition(tid) }
                .onFailure { AppLog.record(context, "posReport: ${it.javaClass.simpleName}") }
        }
        repo.startChannel()
    }

    fun run(kind: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = kind
        error = null
        scope.launch {
            runCatching { block() }.onFailure {
                AppLog.record(context, "map $kind: ${it.javaClass.simpleName}: ${it.message?.take(120)}")
                error = it.friendlyMessage()
            }
            busy = null
        }
    }

    WearList {
        if (pet == null || tid == null) {
            item { Header("Kein Tracker") }
            item { TextButton(onClick = onFinished) { Text("Zurück") } }
            return@WearList
        }

        item { Header(pet.name) }

        val pos = ui.position[tid]
        val effectiveZoom = if (zoom < 0) autoZoom(pos?.accuracy) else zoom

        item {
            if (pos != null) {
                OsmMap(pos = pos, zoom = effectiveZoom)
            } else {
                Note("Keine Position verfügbar")
            }
        }

        if (pos != null) {
            item {
                val t = pos.time?.let { fmtTime(it) }.orEmpty()
                val acc = pos.accuracy?.let { fmtAccuracy(it) } ?: "Genauigkeit unbekannt"
                Note(listOf(t, acc).filter { it.isNotEmpty() }.joinToString(" · "))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { zoom = (effectiveZoom - 1).coerceAtLeast(3) }) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Zoom $effectiveZoom", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { zoom = (effectiveZoom + 1).coerceAtMost(19) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        item {
            CommandSwitch(
                "Licht (LED)",
                checked = ui.led[tid] == true,
                enabled = busy == null,
            ) { run("led") { repo.toggle(tid, "led") } }
        }
        item {
            CommandSwitch(
                "Ton",
                checked = ui.buzzer[tid] == true,
                enabled = busy == null,
            ) { run("buzzer") { repo.toggle(tid, "buzzer") } }
        }

        error?.let { msg ->
            item { Note(msg, color = MaterialTheme.colorScheme.error) }
        }

        item {
            FilledTonalButton(
                onClick = { run("refresh") { repo.refreshPosition(tid) } },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                label = { Text(if (busy == "refresh") "Lädt…" else "Aktualisieren") },
            )
        }

        if (pos != null) {
            item {
                FilledTonalButton(
                    onClick = {
                        val z = effectiveZoom
                        val uri = Uri.parse(
                            String.format(
                                Locale.US,
                                "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=%d/%.6f/%.6f",
                                pos.lat, pos.lon, z, pos.lat, pos.lon,
                            ),
                        )
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { error = "Kein Browser auf der Uhr" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("In OSM öffnen") },
                )
            }
        }
    }
}
