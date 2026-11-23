package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import kotlinx.coroutines.launch

/**
 * v1.12: Login-Screen (Wear Compose, kein Phone-Material3).
 *
 * Wear Compose besitzt kein TextField — die Texteingabe läuft über
 * [WearTextInputActivity] (System-Keyboard). Die Werte werden als Buttons
 * dargestellt; ein Tippen öffnet die System-Eingabe.
 */
@Composable
fun LoginScreen(
    repo: Repo,
    lastCrash: String?,
    onOpenCrashInfo: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScalingLazyListState()

    val emailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(WearTextInputActivity.EXTRA_RESULT)
                ?.takeIf { it.isNotBlank() }
                ?.let { email = it; error = null }
        }
    }
    val passLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(WearTextInputActivity.EXTRA_RESULT)
                ?.takeIf { it.isNotBlank() }
                ?.let { pass = it; error = null }
        }
    }

    fun inputIntent(title: String, initial: String, password: Boolean) =
        Intent(context, WearTextInputActivity::class.java)
            .putExtra(WearTextInputActivity.EXTRA_TITLE, title)
            .putExtra(WearTextInputActivity.EXTRA_INITIAL, initial)
            .putExtra(WearTextInputActivity.EXTRA_PASSWORD, password)

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        ScalingLazyColumn(
            state = scrollState,
            contentPadding = contentPadding,
        ) {
            item {
                Text("Tractive Watch", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Text(
                    "Login mit Tractive-Konto",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lastCrash != null) {
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Crash",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            lastCrash,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenCrashInfo) { Text("Details") }
                    }
                }
            }
            item {
                Button(
                    onClick = { emailLauncher.launch(inputIntent("E-Mail", email, false)) },
                    enabled = !busy,
                ) {
                    Text(if (email.isBlank()) "E-Mail eingeben" else email)
                }
            }
            item {
                Button(
                    onClick = { passLauncher.launch(inputIntent("Passwort", pass, true)) },
                    enabled = !busy,
                ) {
                    Text(if (pass.isBlank()) "Passwort eingeben" else "••••••")
                }
            }
            if (error != null) {
                item {
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (email.isBlank() || pass.isBlank()) {
                            error = "Bitte E-Mail und Passwort eingeben"
                            return@Button
                        }
                        error = null
                        busy = true
                        scope.launch {
                            val outcome = runCatching { repo.doLogin(email.trim(), pass) }
                            busy = false
                            outcome.fold(
                                onSuccess = {
                                    AppLog.record(
                                        context,
                                        "LOGIN: emailLen=${email.length} passLen=${pass.length} -> OK",
                                    )
                                    onLoggedIn()
                                },
                                onFailure = { e ->
                                    AppLog.record(
                                        context,
                                        "LOGIN: emailLen=${email.length} passLen=${pass.length} -> FEHLER ${e.javaClass.simpleName}: ${e.message?.take(240)}",
                                    )
                                    error = e.message ?: "Anmeldung fehlgeschlagen"
                                },
                            )
                        }
                    },
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            colors = ProgressIndicatorDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Anmelden")
                }
            }
            item {
                Text(
                    "Kein Konto? Unter tractive.com registrieren",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
