package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
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
import de.jbecker.tractivewatch.BuildConfig
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.12: Einstellungs-Screen (Wear Compose).
 *
 * Abmelden (loescht Zugangsdaten), App-Log hochladen (ohne Credentials)
 * und Versionsinfo.
 */
@Composable
fun SettingsScreen(
    repo: Repo,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScalingLazyListState()
    var logState by remember { mutableStateOf("–") }

    fun uploadLog() {
        scope.launch {
            logState = "lädt…"
            runCatching { withContext(Dispatchers.IO) { CrashLog.reupload(context) } }
                .fold(
                    onSuccess = { url ->
                        logState = "OK"
                        AppLog.record(context, "EINSTELLUNGEN: Log-Upload OK: ${url?.take(80) ?: "ok"}")
                    },
                    onFailure = { e ->
                        logState = "FEHLER"
                        AppLog.record(context, "EINSTELLUNGEN: Log-Upload FEHLER ${e.javaClass.simpleName}")
                    },
                )
        }
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        ScalingLazyColumn(state = scrollState, contentPadding = contentPadding) {
            item {
                Text("Einstellungen", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Button(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { repo.logout() } }
                            .onFailure { AppLog.record(context, "logout: ${it.javaClass.simpleName}") }
                        onLoggedOut()
                    }
                }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abmelden")
                }
            }
            item {
                Button(onClick = { uploadLog() }) {
                    Text("Log-Upload ($logState)")
                }
            }
            item {
                Text(
                    "Tractive Watch v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
