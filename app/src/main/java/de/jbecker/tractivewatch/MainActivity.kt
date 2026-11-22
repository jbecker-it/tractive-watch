package de.jbecker.tractivewatch

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.ui.CrashScreen
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

/**
 * v1.11: Google-Login komplett entfernt (Google blockiert die App dauerhaft mit
 * Fehler 10) — E-Mail/Passwort ist der einzige Login-Weg.
 */
enum class Screen { Login, Home, Map }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        AppLog.record(this, "MainActivity.onCreate (v${BuildConfig.VERSION_NAME})")
        Log.i(TAG, "onCreate (Prozess-Start)")
        setContent {
            TractiveWatchTheme {
                val appContext = applicationContext
                var showCrash by remember { mutableStateOf(false) }
                val lastCrash = remember(showCrash) { CrashLog.lastSummary(appContext) }
                val repo = remember { Repo(appContext) }
                if (showCrash) {
                    CrashScreen(
                        detail = CrashLog.fileContent(appContext)
                            ?: "Kein lokales Crash-Log vorhanden." +
                                (CrashLog.lastUrl(appContext)?.let { "\n\nLog-URL: $it" } ?: ""),
                        onDismiss = { showCrash = false },
                        onReset = {
                            CrashLog.clear(appContext)
                            showCrash = false
                        },
                    )
                } else {
                    Root(
                        repo = repo,
                        lastCrash = lastCrash,
                        onShowCrash = { showCrash = true },
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TractiveWatch"
    }
}

@Composable
fun Root(repo: Repo, lastCrash: String?, onShowCrash: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.Login) }
    var petIndex by remember { mutableStateOf(0) }

    // Wiederherstellung: gespeicherte Credentials vorhanden → direkt Home
    LaunchedEffect(Unit) {
        if (repo.client.hasStoredCredentials && !repo.client.needsReauth) {
            runCatching { repo.loadPets() }
            screen = Screen.Home
        }
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            repo = repo,
            lastCrash = lastCrash,
            onOpenCrashInfo = onShowCrash,
            onLoggedIn = { screen = Screen.Home },
        )
        Screen.Home -> HomeScreen(
            repo = repo,
            onOpenPet = { petIndex = it; screen = Screen.Map },
        )
        Screen.Map -> {
            val pet = TrackerUi.pets.getOrNull(petIndex)
            LaunchedEffect(pet) {
                if (pet == null) screen = Screen.Home
            }
            if (pet != null) {
                MapScreen(repo = repo, pet = pet, onBack = { screen = Screen.Home })
            }
        }
    }
}
