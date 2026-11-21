package de.jbecker.tractivewatch.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppLog – dauerhafter Event-Trail (app.log) auf der Watch.
 *
 * Jeder wichtige Schritt (App-Start, Google-Login, API-Aufrufe, Crashes)
 * wird hierzeitgestempelt aufgezeichnet. So lässt sich auch OHNE adb logcat
 * exakt nachvollziehen, an welcher Stelle etwas hängen geblieben ist —
 * der Crash-Info-Screen zeigt diesen Trail an.
 *
 * Enthält NIE Secrets (keine Tokens, Codes, Passwörter) — nur Status + Längen.
 */
object AppLog {
    private val LOCK = Any()
    private const val MAX_CHARS = 30_000

    fun record(context: Context, event: String) {
        runCatching {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date())
            val line = "[$ts] $event\n"
            val file = File(context.filesDir, "app.log")
            synchronized(LOCK) {
                val old = if (file.exists()) file.readText() else ""
                val merged = old + line
                file.writeText(if (merged.length > MAX_CHARS) merged.takeLast(MAX_CHARS) else merged)
            }
        }
    }

    fun content(context: Context): String =
        runCatching { File(context.filesDir, "app.log").readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, "app.log").delete() }
    }
}
