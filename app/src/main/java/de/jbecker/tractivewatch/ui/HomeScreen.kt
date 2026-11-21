package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(repo: Repo, onOpenPet: (Int) -> Unit) {
    val ui = TrackerUi

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { repo.loadPets() }
    }
    DisposableEffect(Unit) {
        repo.startChannel()
        onDispose { }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (ui.channelStatus.value) {
                        "live" -> "● Live"
                        "error" -> "○ getrennt"
                        else -> "○ Verbinde…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ui.channelStatus.value == "live") {
                        Color(0xFF81C784)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                )
                Button(onClick = { repo.logout() }, modifier = Modifier.height(36.dp)) {
                    Text("Abmelden")
                }
            }
            Spacer(Modifier.height(6.dp))

            if (ui.pets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.loading.value) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Keine Tracker gefunden", style = MaterialTheme.typography.bodyMedium)
                            Text("Bitte Zugangsdaten prüfen", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                ui.pets.forEachIndexed { index, pet ->
                    PetCard(repo = repo, pet = pet, onOpen = { onOpenPet(index) })
                    if (index < ui.pets.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun PetCard(repo: Repo, pet: TrackerUi.Pet, onOpen: () -> Unit) {
    val ui = TrackerUi
    val scope = rememberCoroutineScope()
    val bat = ui.battery[pet.trackerId]
    val pos = ui.position[pet.trackerId]
    val charging = ui.charging[pet.trackerId] == true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pet.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "Batt ${bat ?: "–"}%${if (charging) " ⚡" else ""}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val t = pos?.time?.let { fmtTime(it) }
        if (!t.isNullOrEmpty()) {
            Text("Position $t", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
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
        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Text("Karte")
        }
    }
}
