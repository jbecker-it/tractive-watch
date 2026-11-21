package de.jbecker.tractivewatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog

/**
 * Crash-Info (Launcher-Icon): zeigt Version, letzten Crash, die Log-URL
 * (lesbar auf dem Display!), das volle Log UND den Event-Trail — damit ist
 * auch ohne adb logcat sichtbar, an welcher Stelle etwas hängen bleibt.
 *
 * v1.7: kompakt + Log-URL prominent oben (damit sie auf der Uhr abgelesen
 * und an den Entwickler geschickt werden kann) + nach "neu hochladen"
 * wird die neue URL direkt im Screen angezeigt.
 * Wear-Theme (DeviceDefault → rundes Vollbild auf Wear OS) im Manifest.
 */
class CrashInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi(null)
            AppLog.record(this, "Crash-Info: Screen angezeigt")
        } catch (t: Throwable) {
            // Letzter Ausweg: IMMER etwas Lesbares zeigen, egal was schiefgeht
            AppLog.record(this, "Crash-Info: buildUi FATAL ${t.javaClass.simpleName}: ${t.message}")
            runCatching {
                val fallback = TextView(this).apply {
                    text = "Crash-Info konnte nicht geladen werden:\n\n" +
                        "${t.javaClass.name}: ${t.message}\n\n" +
                        "Voller Event-Trail:\n" + AppLog.content(this@CrashInfoActivity)
                    textSize = 11f
                    setTypeface(Typeface.MONOSPACE)
                    val pad = (32 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                setContentView(
                    fallback,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }
    }

    private fun buildUi(urlOverride: String?) {
        val version = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        val summary = runCatching { CrashLog.lastSummary(this) }.getOrNull()
        val url = urlOverride ?: runCatching { CrashLog.lastUrl(this) }.getOrNull()
        val full = runCatching { CrashLog.fileContent(this, 9000) }.getOrNull()
        val allText = buildString {
            append("Tractive Watch ").append(version).append('\n')
            if (summary != null) { appendLine(summary); appendLine() }
            if (url != null) { appendLine("Log-URL: $url"); appendLine() }
            if (full.isNullOrBlank()) {
                appendLine("(noch keine Crash-/Event-Daten — App starten und Log-Schritt ausführen)")
            } else {
                append(full)
            }
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val margin = (6 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(TextView(this).apply {
            text = "Crash-Info — $version"
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
        })

        if (url != null) {
            root.addView(TextView(this).apply {
                text = "Log-URL: $url"
                textSize = 10.5f
                setTypeface(Typeface.MONOSPACE)
                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            })
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            isVerticalScrollBarEnabled = false
        }
        scroll.addView(TextView(this).apply {
            text = allText
            textSize = 9.5f
            setTypeface(Typeface.MONOSPACE)
            setLineSpacing(0f, 1.1f)
        })
        root.addView(scroll)

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = margin
            layoutParams = lp
            setOnClickListener {
                runCatching(action).onFailure {
                    Toast.makeText(this@CrashInfoActivity, "Fehler: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        root.addView(button("Alles kopieren") {
            clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", allText))
            Toast.makeText(this@CrashInfoActivity, "Kopiert — am Handy einfügen", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Log neu hochladen") {
            val newUrl = CrashLog.reupload(this@CrashInfoActivity)
            if (newUrl != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", newUrl))
                buildUi(newUrl) // Screen mit der NEUEN URL neu aufbauen
                Toast.makeText(this@CrashInfoActivity, "Erledigt — URL oben + kopiert", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CrashInfoActivity, "Hochladen fehlgeschlagen (0x0.st)", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(button("Zurücksetzen") {
            CrashLog.clear(this@CrashInfoActivity)
            buildUi(null)
            Toast.makeText(this@CrashInfoActivity, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
        })

        setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}
