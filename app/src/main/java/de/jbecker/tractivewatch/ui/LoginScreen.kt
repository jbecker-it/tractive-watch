package de.jbecker.tractivewatch.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
 * Login (v1.9: 100% Wear-nativ nach den offiziellen Wear-OS-Guidelines).
 *
 * Fixes zu v1.8 (User-Feedback: "Klick auf Anmelden → passiert gar nichts",
 * Button abgeschnitten):
 * - FEHLERMELDUNG OBEN (zwischen Passwort und Button) → immer sichtbar,
 *   nie unterhalb des sichtbaren Randes. In v1.8 kam sie unter den Button
 *   und wurde vom runden Rand (bzw. der Tastatur) abgeschnitten — der Klick
 *   hatte also gewirkt, die Fehlermeldung war nur unsichtbar.
 * - Button 48dp hoch (Wear-Minimum für Touch-Targets) und kompakteres
 *   Layout insgesamt (keine Subline, keine Fußzeile) → passt in den
 *   runden Sicherheitsbereich, nichts wird abgeschnitten.
 * - vertikal scrollbar als Fallback, falls der Inhalt (z. B. Crash-Banner
 *   + Fehler) doch mal größer ist als der Screen.
 * - imePadding(): Inhalt hebt sich über die Wear-Tastatur.
 *
 * Login: Tractive-E-Mail + Passwort (Hauptweg). Google-Login bleibt nur im
 * Code (MainActivity), da Google die App blockiert (ApiException 10 =
 * DEVELOPER_ERROR) — deshalb kein Google-Button.
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
    val config = LocalConfiguration.current
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val emailFocus = remember { FocusRequester() }
    val fieldWidth = minOf(config.screenWidthDp - 28, 175).dp

    LaunchedEffect(Unit) { emailFocus.requestFocus() }

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

    // Scaffold behandelt auf Wear OS den kreisförmigen Rand (circular insets)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tractive Watch", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (lastCrash != null) {
                Text(
                    "Letzter Crash: $lastCrash",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onShowCrash) {
                    Text("Crash-Details")
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
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("E-Mail") },
                singleLine = true,
                modifier = Modifier
                    .width(fieldWidth)
                    .focusRequester(emailFocus),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Passwort") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = PasswordVisualTransformation('•'),
                modifier = Modifier.width(fieldWidth),
            )
            Spacer(Modifier.height(10.dp))

            // Fehlermeldung OBEN (über dem Button) → immer sichtbar.
            val err = error
            if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier.width(fieldWidth),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
            }

            Button(
                onClick = {
                    if (busy) return@Button
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
                modifier = Modifier
                    .width(fieldWidth)
                    .height(48.dp),
            ) {
                Text(if (busy) "Anmelden…" else "Anmelden")
            }
        }
    }
}
