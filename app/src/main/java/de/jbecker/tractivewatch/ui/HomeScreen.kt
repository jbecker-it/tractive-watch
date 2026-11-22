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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.fmtTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v1.11 Design-Refresh: Home-Screen für die runde Pixel Watch.
 *
 * Regeln (Wear-OS-Guidelines + Pixel Watch 45mm, ~150-170dp sicherer Radius):
 * - alles im runden Sicherheitsbereich — nichts wird am Rand abgeschnitten
 * - 42-44dp Touch-Targets, >= 10sp Text
 * - Status-Zeile (Live-Punkt + Abmelden), dann kompakte Pet-Karte
 * - mehrere Tracker: vertikales Scrollen
 */
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // Status-Zeile: Live-Status links, Abmelden rechts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                TextButton(
                    onClick = { repo.logout() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abmelden", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (ui.pets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.loading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Keine Tracker gefunden", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Bitte Zugangsdaten prüfen",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                ui.pets.forEachIndexed { index, pet ->
                    PetCard(repo = repo, pet = pet) { onOpenPet(index) }
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

    WearCard {
        // Name + Akku (Akku farbcodiert: rot <20, orange <45, grün >=45)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pet.name,
                style = MaterialTheme.typography.titleSmall,
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
                    Spacer(Modifier.width(2.dp))
                    Text("⚡", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD54F))
                }
            }
        }

        val t = pos?.time?.let { fmtTime(it) }
        if (!t.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    t,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        WearPrimaryButton("Karte", icon = Icons.Filled.LocationOn, onClick = onOpen)
    }
}
