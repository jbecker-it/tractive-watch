package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Crash-Info (Launcher-Icon): zeigt Version, letzten Crash, Log-URL und das
 * volle Log — als Compose-Layout (rund-sicher, wie der Rest der App).
 * Alle Datenzugriffe sind in runCatching gehüllt, damit dieser Screen nie selbst crasht.
 */
class CrashInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        val summary = runCatching { CrashLog.lastSummary(this) }.getOrNull()
        val url = runCatching { CrashLog.lastUrl(this) }.getOrNull()
        val log = runCatching { CrashLog.fileContent(this, 8000) }.getOrNull()
        val allText = buildString {
            append("Tractive Watch v").append(version).append('\n')
            summary?.let { append(it).append('\n') }
            url?.let { append("Log-URL: ").append(it).append('\n') }
            append("===== CRASH-LOG =====\n")
            log?.let { append(it) } ?: append("(leer — keine lokale Log-Datei)")
        }

        setContent {
            TractiveWatchTheme {
                var uploadedUrl by remember { mutableStateOf(url) }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Crash-Info — v$version", fontSize = 13.sp)

                    Text(
                        summary ?: "(kein Crash aufgezeichnet)",
                        fontSize = 10.sp,
                    )

                    Text(
                        (uploadedUrl?.let { "Log-URL:\n$it" })
                            ?: "(noch keine Log-URL — Button unten hochladen)",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    Text(
                        log ?: "(keine Log-Datei gefunden)",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    Button(onClick = {
                        clipboard.setText(AnnotatedString(allText))
                        Toast.makeText(this@CrashInfoActivity, "Kopiert", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Alles kopieren")
                    }
                    Button(onClick = {
                        val newUrl = runCatching { CrashLog.reupload(this@CrashInfoActivity) }.getOrNull()
                        if (newUrl != null) {
                            uploadedUrl = newUrl
                            clipboard.setText(AnnotatedString(newUrl))
                            Toast.makeText(this@CrashInfoActivity, "Hochgeladen + kopiert", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CrashInfoActivity, "Hochladen fehlgeschlagen", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("Log hochladen + kopieren")
                    }
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, allText)
                            putExtra(Intent.EXTRA_SUBJECT, "Tractive Watch Crash-Log")
                        }
                        runCatching {
                            startActivity(Intent.createChooser(send, "Crash-Log teilen"))
                        }
                    }) {
                        Text("Teilen")
                    }
                    Button(onClick = {
                        CrashLog.clear(this@CrashInfoActivity)
                        Toast.makeText(this@CrashInfoActivity, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Zurücksetzen")
                    }
                }
            }
        }
    }
}
