package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jbecker.tractivewatch.data.CrashLog
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

/**
 * Start-Trampolin (Launcher-Icon).
 *
 * Baut MainActivity VOR dem eigentlichen Start einmal "testweise" im eigenen
 * try/catch. So wird ein "Unable to instantiate activity"-Fehler (Class-Loading-
 * oder Konstruktionsfehler) deterministisch abgefangen, aufgeschrieben UND
 * auf dem Bildschirm lesbar angezeigt — statt im kaum sichtbaren System-Dialog.
 *
 * Wenn die Test-Konstruktion klappt, wird MainActivity normal gestartet.
 */
class TrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)

        // Test-Konstruktion: wirft, wenn Class-Loading oder der Konstruktor fehlschlägt
        val constructionError = runCatching {
            MainActivity::class.java.getDeclaredConstructor().newInstance()
        }.exceptionOrNull()

        if (constructionError == null) {
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (t: Throwable) {
                Log.e(TAG, "startActivity fehlgeschlagen", t)
                CrashLog.capture(this, t)
                showError(t)
                return
            }
            finish()
        } else {
            Log.e(TAG, "MainActivity konnte nicht konstruiert werden", constructionError)
            CrashLog.capture(this, constructionError, "trampoline")
            showError(constructionError)
        }
    }

    private fun showError(error: Throwable) {
        val url = CrashLog.lastUrl(this)
        val logText = CrashLog.fileContent(this, 6000) ?: error.stackTraceToString()
        val allText = buildString {
            append(error.toString())
            append('\n')
            append(logText)
            if (url != null) {
                append('\n')
                append("Log-URL: ")
                append(url)
            }
        }

        setContent {
            TractiveWatchTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("App konnte nicht gestartet werden", fontSize = 13.sp)
                    Text("Bitte diesen Screen fotografieren oder als Screenshot an den Entwickler senden.", fontSize = 9.sp)
                    Text(
                        error.toString().take(400),
                        fontSize = 9.sp,
                    )
                    Text(
                        logText,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (url != null) {
                        Text(
                            "Log-URL:\n$url",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            "(Hochladen fehlgeschlagen — Button unten erneut versuchen)",
                            fontSize = 8.sp,
                        )
                    }

                    val clipboard = LocalClipboardManager.current
                    Button(onClick = { clipboard.setText(AnnotatedString(allText)) }) {
                        Text("Kopieren")
                    }
                    Button(onClick = {
                        val newUrl = runCatching { CrashLog.reupload(this@TrampolineActivity) }.getOrNull()
                        newUrl?.let {
                            clipboard.setText(AnnotatedString(it))
                        }
                    }) {
                        Text("Log erneut hochladen + kopieren")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "TractiveWatch"
    }
}
