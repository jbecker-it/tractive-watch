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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.friendlyMessage
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repo: Repo,
    onLoggedIn: () -> Unit,
    onGoogleLogin: (callback: (String?, String?) -> Unit) -> Unit,
) {
    var showEmailForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tractive Watch", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mit Ihrem Tractive-Konto anmelden",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))

            // Hauptweg: Google-Login (wie in der offiziellen Tractive-App)
            Button(
                onClick = {
                    error = null
                    onGoogleLogin { code, err ->
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
                Text(if (showEmailForm) "E-Mail-Formular ausblenden" else "Mit E-Mail & Passwort")
            }

            if (showEmailForm) {
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
                val err = error
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Passwort wird nur lokal im KeyStore verschlüsselt gespeichert",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val err = error
                if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
