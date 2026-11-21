package de.jbecker.tractivewatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
 * Crash-Info (Launcher-Icon): zeigt Version, letzten Crash, Log-URL,
 * das volle Log UND den Event-Trail (App-EVENT-LOG) — damit ist auch
 * ohne adb logcat sichtbar, an welcher Stelle der Login hängen blieb.
 *
 * v1.6: plain Views (die Compose-Variante zeigte einen schwarzen Screen;
 * die plain-View-Variante v1.4 hat sich auf der Watch als rendern bewährt)
 * + Wear-Plattform-Theme (rund-sicher) + harter Fallback, falls etwas wirft.
 */
class CrashInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
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
                    val pad = (48 * resources.displayMetrics.density).toInt()
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

    private fun buildUi() {
        val version = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
        val summary = runCatching { CrashLog.lastSummary(this) }.getOrNull()
        val url = runCatching { CrashLog.lastUrl(this) }.getOrNull()
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
        val pad = (24 * resources.displayMetrics.density).toInt()

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
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
        })

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
            textSize = 10.5f
            setTypeface(Typeface.MONOSPACE)
            setLineSpacing(0f, 1.15f)
        })
        root.addView(scroll)

        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setOnClickListener { runCatching(action) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }

        root.addView(button("Alles kopieren") {
            clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", allText))
            Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Log neu hochladen + kopieren") {
            val newUrl = runCatching { CrashLog.reupload(this) }.getOrNull()
            if (newUrl != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("tractive-watch", newUrl))
                Toast.makeText(this, "Hochgeladen + kopiert", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Hochladen fehlgeschlagen", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(button("Zurücksetzen") {
            runCatching { CrashLog.clear(this) }
            Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
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
