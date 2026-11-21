package de.jbecker.tractivewatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import de.jbecker.tractivewatch.data.CrashLog

/**
 * Eigenes Launcher-Icon "Crash-Info": zeigt die installierte Version, den
 * letzten Crash und das Log-URL — auch wenn die Haupt-App beim Start crasht.
 * Absichtlich ohne Compose (plain Views), damit es selbst nie abstürzen kann.
 */
class CrashInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val summary = runCatching { CrashLog.lastSummary(this) }.getOrNull()
            val url = runCatching { CrashLog.lastUrl(this) }.getOrNull()
            val log = runCatching { CrashLog.fileContent(this, 4000) }.getOrNull()

            val info = buildString {
                append("Tractive Watch v").append(BuildConfig.VERSION_NAME)
                    .append("  (VersionCode ").append(BuildConfig.VERSION_CODE).append(")\n\n")
                append("LETZTER ABSTURZ\n")
                append(summary ?: "(kein Crash aufgezeichnet)\n\n")
                if (url != null) {
                    append("LOG-URL (zum Senden an den Entwickler):\n")
                    append(url).append("\n\n")
                } else {
                    append("LOG-URL:\n(nicht vorhanden – Crash kam vor dem Logger, oder kein Crash)\n\n")
                }
                append("===== CRASH-LOG =====\n")
                append(log ?: "(leer – keine lokale Log-Datei)")
            }

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(56, 56, 56, 56)
                addView(
                    ScrollView(this@CrashInfoActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
                        )
                        addView(TextView(this@CrashInfoActivity).apply {
                            this.text = info
                            textSize = 10.5f
                        })
                    },
                )

                fun button(label: String, action: () -> Unit): Button = Button(this@CrashInfoActivity).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { action() }
                }

                addView(button("Log-URL kopieren") {
                    val textToCopy = url ?: info
                    @Suppress("DEPRECATION")
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("CrashLog", textToCopy))
                    Toast.makeText(this@CrashInfoActivity, "Kopiert", Toast.LENGTH_SHORT).show()
                })
                addView(button("Teilen") {
                    val shareText = buildString {
                        append("Tractive Watch v").append(BuildConfig.VERSION_NAME)
                        append(" – Crash-Report:\n")
                        append(summary ?: "(keine Zusammenfassung)")
                        if (url != null) append("\nLog: ").append(url)
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    startActivity(Intent.createChooser(send, "Crash-Log teilen"))
                })
                addView(button("Zurücksetzen") {
                    CrashLog.clear(this@CrashInfoActivity)
                    recreate()
                })
            }
            setContentView(root)
        } catch (t: Throwable) {
            // Letzter Ausweg: zumindest die Fehlermeldung zeigen
            runCatching {
                setContentView(
                    TextView(this).apply {
                        text = "Crash-Info: Anzeige fehlgeschlagen:\n${t.javaClass.name}\n${t.message}"
                        textSize = 11f
                        setPadding(56, 56, 56, 56)
                    },
                )
            }
        }
    }
}
