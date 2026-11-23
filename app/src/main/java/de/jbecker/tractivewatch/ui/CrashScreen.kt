package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog

/**
 * v1.12: Crash-Screen (Wear Compose).
 *
 * Zeigt die Crash-Protokoll-Zusammenfassung und bietet "Zuruecksetzen"
 * (loescht Zugangsdaten + Protokoll) oder "Verwerfen" (nur Protokoll).
 */
@Composable
fun CrashScreen(
    info: String,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        ScalingLazyColumn(state = scrollState, contentPadding = contentPadding) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Warnung",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Crash-Protokoll", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextButton(onClick = {
                    AppLog.record(context, "CRASH: Reset angefordert")
                    runCatching { CrashLog.clear(context) }
                    onReset()
                }) {
                    Text("Zugangsdaten zuruecksetzen")
                }
            }
            item {
                TextButton(onClick = {
                    AppLog.record(context, "CRASH: Protokoll verwerfen")
                    runCatching { CrashLog.clear(context) }
                    onDismiss()
                }) {
                    Text("Verwerfen")
                }
            }
        }
    }
}
