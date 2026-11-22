package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtAccuracy
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.launch

/**
 * v1.11 Design-Refresh: OSM-Karte im runden Format.
 *
 * - Header-Zeile: Zurück-Button + Name (zentriert)
 * - Karte als 150dp-Kreis, Zoom-Buttons als Overlay am rechten Rand
 *   (− / Auto / +) — spart eine komplette Button-Zeile
 * - Toggle-Zeile (LED / Piep / Live) unter der Karte
 * - Info-Zeile (Genauigkeit · Sensor · Uhrzeit) + OSM-Hinweis
 * - alles im runden Sicherheitsbereich, >= 10sp, 34-42dp Targets
 */
@Composable
fun MapScreen(repo: Repo, pet: TrackerUi.Pet, onBack: () -> Unit) {
    val ui = TrackerUi
    val scope = rememberCoroutineScope()
    var zoomOverride by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pet.trackerId) {
        repo.startChannel()
        if (ui.position[pet.trackerId] == null) {
            runCatching {
                val pos = repo.client.posReport(pet.trackerId)
                pos.toPosition()?.let { ui.position[pet.trackerId] = it }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            // Header-Zeile: Zurück | Name (zentriert) | Platzhalter (Symmetrie)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WearRoundButton("Zurück", icon = Icons.Filled.ArrowBack, onClick = onBack)
                Text(
                    pet.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                )
                Box(Modifier.size(34.dp))
            }
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
            ) {
                val pos = ui.position[pet.trackerId]
                if (pos != null) {
                    OsmMap(pos = pos, zoomOverride = zoomOverride)
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Position laden…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Zoom-Steuerung als Overlay am rechten Kartenrand
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WearRoundButton(
                        "Zoom aus",
                        text = "−",
                        enabled = pos != null,
                        onClick = {
                            zoomOverride = ((zoomOverride ?: autoZoom(pos?.accuracy)) - 1).coerceIn(3, 19)
                        },
                    )
                    WearRoundButton(
                        "Auto-Zoom",
                        icon = Icons.Filled.Refresh,
                        onClick = { zoomOverride = null },
                    )
                    WearRoundButton(
                        "Zoom rein",
                        text = "+",
                        enabled = pos != null,
                        onClick = {
                            zoomOverride = ((zoomOverride ?: autoZoom(pos?.accuracy)) + 1).coerceIn(3, 19)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WearToggle("LED", ui.led[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "led") } }
                }
                WearToggle("Piep", ui.buzzer[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "buzzer") } }
                }
                WearToggle("Live", ui.live[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "live") } }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Info-Zeile: eine Zeile, Ellipsis
            val pos = ui.position[pet.trackerId]
            val infos = buildList {
                pos?.let {
                    val a = fmtAccuracy(it.accuracy)
                    if (a.isNotEmpty()) add(a)
                    if (!it.sensor.isNullOrEmpty()) add(it.sensor)
                    val t = fmtTime(it.time)
                    if (t.isNotEmpty()) add(t)
                }
            }
            Text(
                if (infos.isEmpty()) "Keine Position verfügbar" else infos.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "© OpenStreetMap-Mitwirkende",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
