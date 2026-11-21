package de.jbecker.tractivewatch.ui

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.GoogleLoginState
import de.jbecker.tractivewatch.friendlyMessage
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repo: Repo,
    googleState: GoogleLoginState,
    lateResult: Pair<String?, String?>?,
    onLoggedIn: () -> Unit,
    onGoogleLogin: (callback: (String?, String?) -> Unit) -> Unit,
) {
    // Initial-Zustand: Pending aus Persistenz (Prozess-Neustart während des Sign-Ins)
    var pending by remember {
        mutableStateOf(googleState.pending && !googleState.isStale)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showEmailForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Spätes Ergebnis (kam nach Prozess-Neustart an) verarbeiten
    LaunchedEffect(lateResult) {
        val late = lateResult ?: return@LaunchedEffect
        pending = false
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
            error = err ?: "Google-Anmeldung fehlgeschlagen"
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tractive Watch", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "Ihr Tractive-Konto",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            if (pending) {
                Text(
                    "Google-Anmeldung läuft…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        pending = false
                        googleState.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abbrechen")
                }
            } else {
                // Hauptweg: Google-Login (wie in der offiziellen Tractive-App)
                Button(
                    onClick = {
                        error = null
                        pending = true
                        onGoogleLogin { code, err ->
                            pending = false
                            if (code != null) {
                                scope.launch {
                                    busy = true
                                    try {
                                        repo.doLoginGoogle(code)
                                        onLoggedIn()
                                    } catch (e: Exception) {
                                        error = e.friendlyMessage()
                                    } finally {
                                        busy = false
                                    }
                                }
                            } else {
                                error = err ?: "Google-Anmeldung fehlgeschlagen"
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mit Google anmelden")
                }

                TextButton(
                    onClick = {
                        showEmailForm = !showEmailForm
                        error = null
                    },
                    enabled = !busy,
                ) {
                    Text(if (showEmailForm) "E-Mail ausblenden" else "Mit E-Mail & Passwort")
                }
            }

            val err = error
            if (err != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showEmailForm && !pending) {
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "Passwort wird nur lokal verschlüsselt gespeichert",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
