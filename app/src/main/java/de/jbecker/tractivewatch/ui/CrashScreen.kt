package de.jbecker.tractivewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.data.AppLog

/**
 * v1.11 Design-Refresh: Crash-Bildschirm (wird bei In-App-Crash angezeigt).
 * - Warn-Icon + Titel
 * - Detail (scrollbar, max 110dp)
 * - zwei volle Buttons: Zurücksetzen (Hauptaktion) + Schließen
 * - 46dp Buttons, >= 10sp, im runden Sicherheitsbereich
 */
@Composable
fun CrashScreen(
    detail: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Leider ein Absturz",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 110.dp)
                    .verticalScroll(rememberScrollState()),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(12.dp))
            WearPrimaryButton("Zurücksetzen", onClick = onReset)
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Schließen", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
