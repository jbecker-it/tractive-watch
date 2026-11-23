package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.delay

/**
 * v1.12: Home-Screen (Wear Compose).
 *
 * Zeigt den Verbindungsstatus, die Tracker-Liste (Name, Akku, letzte
 * Position) und einen Button zu den Einstellungen. Ein Tippen auf eine
 * Karte öffnet den Befehls-Screen (pet/{index}).
 */
@Composable
fun HomeScreen(
    repo: Repo,
    onOpenPet: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui = TrackerUi
    val scrollState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { repo.loadPets() }
    }
    DisposableEffect(Unit) {
        repo.startChannel()
        onDispose { }
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        ScalingLazyColumn(
            state = scrollState,
            contentPadding = contentPadding,
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(ui.channelStatus.value == "live")
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (ui.channelStatus.value) {
                            "live" -> "Live"
                            "error" -> "getrennt"
                            else -> "Verbinde…"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (ui.pets.isEmpty()) {
                if (ui.loading.value) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            colors = ProgressIndicatorDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                } else {
                    item {
                        Text("Keine Tracker gefunden", style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        Text(
                            "Bitte Zugangsdaten prüfen",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                ui.pets.forEachIndexed { index, pet ->
                    item {
                        PetCard(pet = pet) { onOpenPet(index) }
                    }
                }
            }

            item {
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Einstellungen")
                }
            }
        }
    }
}

@Composable
private fun PetCard(pet: TrackerUi.Pet, onOpen: () -> Unit) {
    val ui = TrackerUi
    val bat = ui.battery[pet.trackerId]
    val pos = ui.position[pet.trackerId]
    val charging = ui.charging[pet.trackerId] == true

    Card(onClick = onOpen) {
        Text(
            pet.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val batColor = when (bat) {
                null -> MaterialTheme.colorScheme.onSurfaceVariant
                in 0..19 -> MaterialTheme.colorScheme.error
                in 20..44 -> Color(0xFFFFB74D)
                else -> Color(0xFF69F0AE)
            }
            Text(
                if (bat != null) "${bat}%" else "–",
                style = MaterialTheme.typography.labelMedium,
                color = batColor,
            )
            if (charging) {
                Text(" ⚡", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (pos != null) {
            val t = pos.time?.let { fmtTime(it) }
            if (!t.isNullOrEmpty()) {
                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
