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

    // Ein Hintergrund-Thread: Datei-I/O (bis 30 KB lesen+schreiben) lief vorher
    // bei jedem Ereignis synchron auf dem Main-Thread.
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLog").apply { isDaemon = true }
    }

    fun record(context: Context, event: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date())
        val line = "[$ts] $event\n"
        val file = File(context.applicationContext.filesDir, "app.log")
        runCatching { writer.execute { append(file, line) } }
    }

    /** Synchron schreiben (Crash-Handler: der Prozess stirbt direkt danach). */
    fun recordNow(context: Context, event: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date())
        append(File(context.applicationContext.filesDir, "app.log"), "[$ts] $event\n")
    }

    private fun append(file: File, line: String) {
        runCatching {
            synchronized(LOCK) {
                if (file.exists() && file.length() > MAX_CHARS * 2L) {
                    file.writeText(file.readText().takeLast(MAX_CHARS))
                }
                file.appendText(line)
            }
        }
    }

    fun content(context: Context): String =
        runCatching {
            synchronized(LOCK) { File(context.filesDir, "app.log").readText().takeLast(MAX_CHARS) }
        }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, "app.log").delete() }
    }
}
