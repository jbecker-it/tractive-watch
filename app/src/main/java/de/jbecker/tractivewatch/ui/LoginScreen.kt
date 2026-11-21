package de.jbecker.tractivewatch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.CrashLog
import de.jbecker.tractivewatch.data.GoogleLoginState
import de.jbecker.tractivewatch.friendlyMessage
import kotlinx.coroutines.launch

/**
 * Login (v1.8: 100% Wear-nativ, E-Mail/Passwort als HAUPTWEG).
 *
 * Der Google-Button ist entfernt: Google blockiert die Nutzung der
 * Tractive-Client-ID in dieser App (ApiException 10 = DEVELOPER_ERROR,
 * endgültige Google-Antwort). E-Mail/Passwort ist der funktionierende Weg.
 * Der Google-Code-Pfad bleibt in MainActivity, falls Google/Tractive
 * die Blockade irgendwann aufheben.
 */
@Composable
fun LoginScreen(
    repo: Repo,
    googleState: GoogleLoginState,
    lateResult: Pair<String?, String?>?,
    lastCrash: String?,
    onShowCrash: () -> Unit,
    onLoggedIn: () -> Unit,
    onGoogleLogin: (callback: (String?, String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Spätes Google-Ergebnis (kam nach Prozess-Neustart an) verarbeiten
    LaunchedEffect(lateResult) {
        val late = lateResult ?: return@LaunchedEffect
        val (code, err) = late
        if (code != null) {
            busy = true
            try {
                repo.doLoginGoogle(code)
                onLoggedIn()
            } catch (e: Exception) {
                error = e.friendlyMessage()
            } finally {
                busy = false
            }
        } else {
            error = err ?: "Anmeldung fehlgeschlagen"
        }
    }

    // Material3-Scaffold behandelt auf Wear OS automatisch den
    // kreisförmigen Cutout (circular insets) → 100% Watch-nativ
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tractive Watch", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(1.dp))
            Text(
                "Ihr Tractive-Konto",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            if (lastCrash != null) {
                Text(
                    "Letzter Absturz: $lastCrash",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onShowCrash) {
                    Text("Details anzeigen")
                }
                val crashUrl = CrashLog.lastUrl(context)
                if (crashUrl != null) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(crashUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) {
                        Text("Crash-Log öffnen")
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-Mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation('•'),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    error = null
                    scope.launch {
                        busy = true
                        try {
                            repo.doLogin(email, password)
                            onLoggedIn()
                        } catch (e: Exception) {
                            error = e.friendlyMessage()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Anmelden…" else "Anmelden")
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Passwort wird nur lokal verschlüsselt gespeichert",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            val err = error
            if (err != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 6,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
