package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.Button
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

/**
 * Pet-Screen (Wear Compose) — Befehle für einen einzelnen Tracker:
 * LED / Ton / Live als Schalter, Akku, letzte Position, Karte, Beenden.
 */
@Composable
fun PetScreen(
    repo: Repo,
    index: Int,
    onOpenMap: () -> Unit,
    onFinished: () -> Unit,
) {
    val ui = TrackerUi
    val context = LocalContext.current

    // Nicht remembern: die Liste kann nachgeladen werden.
    val pet = ui.pets.getOrNull(index)
    val tid = pet?.trackerId

    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tid) {
        if (tid != null) {
            runCatching { repo.refreshPosition(tid) }
                .onFailure { AppLog.record(context, "posReport: ${it.javaClass.simpleName}") }
        }
        repo.startChannel()
    }

    fun send(kind: String) {
        val id = tid ?: return
        if (busy != null) return
        busy = kind
        error = null
        // Repo-Scope: der Befehl läuft zu Ende, auch wenn man den Screen
        // verlässt (vorher: ForgottenCoroutineScopeException -> Fehler).
        repo.scope.launch {
            try {
                repo.toggle(id, kind)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.record(context, "toggle($id,$kind): ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                error = e.friendlyMessage()
            } finally {
                busy = null
            }
        }
    }

    WearList {
        if (pet == null || tid == null) {
            item { Header("Kein Tracker") }
            item { TextButton(onClick = onFinished) { Text("Zurück") } }
            return@WearList
        }

        item { Header(pet.name) }

        item {
            val bat = ui.battery[tid]
            val pos = ui.position[tid]
            val parts = buildList {
                if (bat != null) add("Akku $bat%")
                pos?.time?.let { fmtTime(it) }?.takeIf { it.isNotEmpty() }?.let { add(it) }
                pos?.accuracy?.let { add(fmtAccuracy(it)) }
            }
            Note(if (parts.isEmpty()) "Keine Daten" else parts.joinToString(" · "))
        }

        item {
            CommandSwitch(
                "Licht (LED)",
                checked = ui.led[tid] == true,
                enabled = busy == null,
                secondary = if (busy == "led") "sende…" else null,
            ) { send("led") }
        }
        item {
            CommandSwitch(
                "Ton",
                checked = ui.buzzer[tid] == true,
                enabled = busy == null,
                secondary = if (busy == "buzzer") "sende…" else null,
            ) { send("buzzer") }
        }
        item {
            CommandSwitch(
                "Live-Tracking",
                checked = ui.live[tid] == true,
                enabled = busy == null,
                secondary = if (busy == "live") "sende…" else null,
            ) { send("live") }
        }

        error?.let { msg ->
            item { Note(msg, color = MaterialTheme.colorScheme.error) }
        }

        item {
            Button(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                label = { Text("Karte") },
            )
        }

        if (ui.live[tid] == true) {
            item {
                TextButton(
                    onClick = {
                        // Repo-Scope statt Screen-Scope: der Befehl darf nicht
                        // abgebrochen werden, wenn der Screen gleich verschwindet.
                        repo.scope.launch {
                            runCatching { repo.toggle(tid, "live") }
                                .onFailure { AppLog.record(context, "beenden: live-aus: ${it.javaClass.simpleName}") }
                        }
                        onFinished()
                    },
                ) {
                    Text("Live beenden")
                }
            }
        }
    }
}
