package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.launch

/**
 * Home-Screen (Wear Compose): Verbindungsstatus, Tracker-Liste (Name, Akku,
 * letzte Position) und Einstellungen. Tippen öffnet den Befehls-Screen.
 */
@Composable
fun HomeScreen(
    repo: Repo,
    onOpenPet: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui = TrackerUi
    val scope = rememberCoroutineScope()

    // Nur laden, wenn noch nichts da ist (nach Login hat doLogin schon geladen;
    // beim Zurück-Swipen von pet/map NICHT jedes Mal alles neu holen).
    LaunchedEffect(Unit) {
        repo.startChannel() // zuerst: Live-Daten nicht erst nach dem Laden
        if (ui.pets.isEmpty() && !ui.loading.value) repo.loadPets()
    }

    WearList {
        item { Header("Meine Tiere") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(ui.channelStatus.value == "live")
                Spacer(Modifier.width(6.dp))
                Text(
                    when (ui.channelStatus.value) {
                        "live" -> "Live verbunden"
                        "error" -> "Getrennt"
                        else -> "Verbinde…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            ui.pets.isNotEmpty() -> {
                ui.pets.forEachIndexed { index, pet ->
                    item(key = pet.trackerId) {
                        PetButton(pet = pet) { onOpenPet(index) }
                    }
                }
            }
            ui.loading.value -> item {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            else -> {
                item {
                    Note(
                        ui.loadError.value ?: "Keine Tracker gefunden",
                        color = if (ui.loadError.value != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = { scope.launch { repo.loadPets() } },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        label = { Text("Erneut laden") },
                    )
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("Einstellungen") },
            )
        }
    }
}

@Composable
private fun PetButton(pet: TrackerUi.Pet, onOpen: () -> Unit) {
    val ui = TrackerUi
    val bat = ui.battery[pet.trackerId]
    val charging = ui.charging[pet.trackerId] == true
    val time = ui.position[pet.trackerId]?.time?.let { fmtTime(it) }.orEmpty()

    val batText = when {
        bat == null -> "Akku –"
        charging -> "$bat% ⚡"
        else -> "$bat%"
    }
    val batColor = when {
        bat == null -> MaterialTheme.colorScheme.onSurfaceVariant
        bat < 20 -> MaterialTheme.colorScheme.error
        bat < 45 -> Color(0xFFFFB74D)
        else -> Color(0xFF69F0AE)
    }

    // Tonal (dunkel): farbiger Akkustand bleibt lesbar (auf Primär-Blau nicht)
    FilledTonalButton(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(pet.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(batText, color = batColor)
            if (time.isNotEmpty()) {
                Text(" · $time", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}
