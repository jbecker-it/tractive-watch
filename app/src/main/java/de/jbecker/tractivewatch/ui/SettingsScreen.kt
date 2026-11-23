package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import de.jbecker.tractivewatch.BuildConfig
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog

/**
 * Einstellungs-Screen (Wear Compose): Abmelden (mit Bestätigung),
 * App-Log hochladen (ohne Credentials), Crash-Info, Versionsinfo.
 */
@Composable
fun SettingsScreen(
    repo: Repo,
    onOpenCrashInfo: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    var logState by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    WearList {
        item { Header("Einstellungen") }

        item {
            FilledTonalButton(
                onClick = {
                    if (logState == "lädt…") return@FilledTonalButton
                    logState = "lädt…"
                    CrashLog.reuploadAsync(context) { url ->
                        logState = if (url != null) "OK: $url" else "fehlgeschlagen"
                        AppLog.record(context, "EINSTELLUNGEN: Log-Upload ${if (url != null) "OK" else "FEHLER"}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Log hochladen") },
                secondaryLabel = logState?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
            )
        }

        item {
            FilledTonalButton(
                onClick = onOpenCrashInfo,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                label = { Text("Crash-Info") },
            )
        }

        item {
            FilledTonalButton(
                onClick = {
                    if (!confirmLogout) {
                        confirmLogout = true
                        return@FilledTonalButton
                    }
                    runCatching { repo.logout() }
                        .onFailure { AppLog.record(context, "logout: ${it.javaClass.simpleName}") }
                    AppLog.record(context, "EINSTELLUNGEN: abgemeldet")
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (confirmLogout) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                label = { Text(if (confirmLogout) "Wirklich abmelden?" else "Abmelden") },
                secondaryLabel = if (confirmLogout) {
                    { Text("Nochmal tippen") }
                } else null,
            )
        }

        item {
            Note("Tractive Watch v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }
}
