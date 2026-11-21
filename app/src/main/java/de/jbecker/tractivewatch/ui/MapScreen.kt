package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtAccuracy
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pet.name, maxLines = 1) },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.height(36.dp)) {
                        Text("←")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape),
            ) {
                val pos = ui.position[pet.trackerId]
                if (pos != null) {
                    OsmMap(pos = pos, zoomOverride = zoomOverride)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Position wird geladen…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (infos.isEmpty()) {
                    Text("Keine Position verfügbar", style = MaterialTheme.typography.labelSmall)
                } else {
                    infos.forEachIndexed { i, s ->
                        if (i > 0) Spacer(Modifier.width(10.dp))
                        Text(s, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToggleButton("LED", ui.led[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "led") } }
                }
                ToggleButton("Piep", ui.buzzer[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "buzzer") } }
                }
                ToggleButton("Live", ui.live[pet.trackerId] ?: false, Modifier.weight(1f)) {
                    scope.launch { runCatching { repo.toggle(pet.trackerId, "live") } }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = {
                        zoomOverride = ((zoomOverride ?: autoZoom(pos?.accuracy)) - 1)
                            .coerceIn(3, 19)
                    },
                    enabled = pos != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                ) { Text("–") }
                Button(
                    onClick = { zoomOverride = null },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                ) { Text("Auto") }
                Button(
                    onClick = {
                        zoomOverride = ((zoomOverride ?: autoZoom(pos?.accuracy)) + 1)
                            .coerceIn(3, 19)
                    },
                    enabled = pos != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                ) { Text("+") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "© OpenStreetMap-Mitwirkende",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
