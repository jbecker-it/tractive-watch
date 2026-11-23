package de.jbecker.tractivewatch.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import de.jbecker.tractivewatch.Repo
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.friendlyMessage
import kotlinx.coroutines.launch

/**
 * Login-Screen (Wear Compose).
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
    // rememberSaveable: Werte überleben, wenn Wear die Activity im Hintergrund
    // (während die Tastatur offen ist) neu erstellt.
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val emailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(WearTextInputActivity.EXTRA_RESULT)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { email = it; error = null }
        }
    }
    val passLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(WearTextInputActivity.EXTRA_RESULT)
                ?.takeIf { it.isNotEmpty() }
                ?.let { pass = it; error = null }
        }
    }

    fun inputIntent(title: String, initial: String, password: Boolean) =
        Intent(context, WearTextInputActivity::class.java)
            .putExtra(WearTextInputActivity.EXTRA_TITLE, title)
            .putExtra(WearTextInputActivity.EXTRA_INITIAL, initial)
            .putExtra(WearTextInputActivity.EXTRA_PASSWORD, password)

    fun submit() {
        if (email.isBlank() || pass.isEmpty()) {
            error = "Bitte E-Mail und Passwort eingeben"
            return
        }
        error = null
        busy = true
        scope.launch {
            val outcome = runCatching { repo.doLogin(email.trim(), pass) }
            busy = false
            outcome.fold(
                onSuccess = {
                    AppLog.record(context, "LOGIN: emailLen=${email.length} passLen=${pass.length} -> OK")
                    pass = ""
                    onLoggedIn()
                },
                onFailure = { e ->
                    AppLog.record(
                        context,
                        "LOGIN: emailLen=${email.length} passLen=${pass.length} -> FEHLER " +
                            "${e.javaClass.simpleName}: ${e.message?.take(240)}",
                    )
                    error = e.friendlyMessage()
                },
            )
        }
    }

    WearList {
        item { Header("Tractive") }
        item { Note("Mit Tractive-Konto anmelden") }

        item {
            FilledTonalButton(
                onClick = { emailLauncher.launch(inputIntent("E-Mail", email, false)) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = if (email.isBlank()) null else {
                    { Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                label = { Text(if (email.isBlank()) "E-Mail eingeben" else "E-Mail") },
            )
        }
        item {
            FilledTonalButton(
                onClick = { passLauncher.launch(inputIntent("Passwort", "", true)) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                secondaryLabel = if (pass.isEmpty()) null else { { Text("••••••") } },
                label = { Text(if (pass.isEmpty()) "Passwort eingeben" else "Passwort") },
            )
        }

        error?.let { msg ->
            item { Note(msg, color = MaterialTheme.colorScheme.error) }
        }

        item {
            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                icon = if (busy) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            colors = ProgressIndicatorDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                } else null,
                label = { Text(if (busy) "Anmelden…" else "Anmelden") },
            )
        }

        if (lastCrash != null) {
            item {
                OutlinedButton(
                    onClick = onOpenCrashInfo,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    secondaryLabel = {
                        Text(lastCrash, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    label = { Text("Letzter Absturz") },
                )
            }
        }
    }
}
