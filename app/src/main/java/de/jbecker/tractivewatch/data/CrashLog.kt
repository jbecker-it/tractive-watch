package de.jbecker.tractivewatch.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Speichert Abstürze in crash.log und lädt das Log synchron hoch (tmpfiles.org,
 * Fallback paste.rs — beide ohne Anmeldung, halten ~60-90 Tage). Die URL erscheint im Crash-Info-Screen –
 * damit ist ein Watch-Crash auch ohne PC diagnostizierbar.
 *
 * Alle Funktionen werfen niemals eine Exception (runCatching intern),
 * damit der Fehler-Display-Pfad nie selbst abstürzt.
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val PREFS = "crash_state"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Installiert einen globalen Exception-Handler (vor setContent aufrufen). Idempotent. */
    fun install(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                capture(appContext, throwable, thread.name)
                // System-Handling beibehalten (Crash-Dialog), wir loggen nur mit
                prev?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Zeichnet einen Fehler auf:
     * - crash.log wird IMMER mit der neuesten Fassung überschrieben
     * - Kurz-Zusammenfassung wird aktualisiert
     * - Log wird hochgeladen, wenn noch keine URL vorhanden ist
     */
    fun capture(context: Context, t: Throwable, threadName: String? = null) {
        runCatching {
            val appContext = context.applicationContext
            AppLog.record(appContext, "CRASH: ${t.javaClass.simpleName}: ${t.message}")
            val sb = StringBuilder()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(Date())
            sb.append(time).append(" | Thread: ").append(threadName ?: "unknown").append('\n')
            sb.append(t).append('\n')
            t.stackTraceToString().lines().take(40).forEach { sb.append(it).append('\n') }
            var cause = t.cause
            var depth = 0
            while (cause != null && depth < 6) {
                sb.append("Caused by: ").append(cause).append('\n')
                cause.stackTraceToString().lines().take(20)
                    .forEach { sb.append("  ").append(it).append('\n') }
                cause = cause.cause
                depth++
            }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            File(appContext.filesDir, FILE_NAME).writeText(sb.toString())

            if (prefs.getString("url", null) == null) {
                upload(prefs, sb.toString())
            }

            prefs.edit()
                .putString("summary", "${t.javaClass.simpleName}: ${t.message ?: "(keine Meldung)"}")
                .putLong("time", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Lädt das komplette Log (Crash-Log + Event-Trail) erneut hoch und speichert
     * die neue URL. v1.9: funktioniert auch OHNE crash.log (Event-Trail reicht) —
     * v1.8 konnte nur crash.log hochladen und scheiterte sonst still.
     */
    fun reupload(context: Context): String? = runCatching {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val content = fileContent(appContext, 20_000) ?: return@runCatching null
        val url = upload(prefs, content)
        if (url != null) prefs.edit().putString("url", url).apply()
        url
    }.getOrNull()

    /**
     * Upload + URL merken. 0x0.st hat Uploads dauerhaft deaktiviert (Nov 2026,
     * "AI botnet spam"), daher Fallback-Kette:
     * 1) tmpfiles.org (Multipart, ~60 Tage)
     * 2) paste.rs (Text, ~90 Tage)
     */
    private fun upload(prefs: SharedPreferences, content: String): String? {
        val url = tmpfilesUpload(content) ?: pasteRsUpload(content)
        if (url != null) prefs.edit().putString("url", url).apply()
        return url
    }

    /** tmpfiles.org: Antwort {"status":"success","data":{"url":"https://tmpfiles.org/xxx/file"}}. */
    private fun tmpfilesUpload(content: String): String? = runCatching {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "tractive-watch-log.txt",
                content.toRequestBody("text/plain".toMediaType()),
            )
            .build()
        val resp = http.newCall(
            Request.Builder()
                .url("https://tmpfiles.org/api/v1/upload")
                .header("User-Agent", "TractiveWatch-CrashLog/1.10")
                .post(body)
                .build(),
        ).execute()
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            val m = Regex("\"url\"\\s*:\\s*\"(https://tmpfiles\\.org/[^\"]+)\"").find(text)
                ?: throw IOException("tmpfiles.org: unerwartete Antwort: ${text.take(120)}")
            // Seiten-Link in Direkt-Download-Link umwandeln
            m.groupValues[1].replaceFirst("tmpfiles.org/", "tmpfiles.org/dl/")
        }
    }.getOrNull()

    /** paste.rs: Antwort IST die URL als Klartext. */
    private fun pasteRsUpload(content: String): String? = runCatching {
        val resp = http.newCall(
            Request.Builder()
                .url("https://paste.rs/")
                .header("User-Agent", "TractiveWatch-CrashLog/1.10")
                .header("Content-Type", "text/plain")
                .post(content.toRequestBody("text/plain".toMediaType()))
                .build(),
        ).execute()
        resp.use { r ->
            val url = r.body?.string().orEmpty().trim()
            if (url.startsWith("http")) url else throw IOException("paste.rs: unerwartete Antwort: ${url.take(120)}")
        }
    }.getOrNull()

    /** Kurz-Zusammenfassung des letzten Crashes (z. B. "21.11. 14:02 – IllegalStateException: …") */
    fun lastSummary(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val summary = prefs.getString("summary", null) ?: return null
        val time = prefs.getLong("time", 0L)
        val dt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(Date(time))
        return "$dt – $summary"
    }

    /** Hochgeladene Log-URL (tmpfiles.org oder paste.rs), null wenn keine */
    fun lastUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("url", null)

    /**
     * Vollständiges Log von den lokalen Dateien: Crash-Log + Event-Trail (abgeschnitten).
     * v1.9 (Bugfix v1.8): Jede Datei wird EIGENSTÄNDIG gelesen — ein fehlender
     * crash.log warf eine Exception und hat das (potenziell volle) Event-Log
     * komplett unsichtbar gemacht.
     */
    fun fileContent(context: Context, maxChars: Int = 6000): String? =
        runCatching {
            val crash = runCatching { File(context.filesDir, FILE_NAME).readText() }
                .getOrNull()?.takeIf { it.isNotBlank() }
            val app = AppLog.content(context)
            if (crash.isNullOrBlank() && app.isBlank()) return@runCatching null
            val parts = mutableListOf<String>()
            parts += crash ?: "(noch kein Crash aufgezeichnet — gut so)"
            if (app.isNotBlank()) {
                parts += "========== APP-EVENT-LOG ==========\n" + app
            } else {
                parts += "(KEIN Event-Log — das ist ein Fehler: die App-Start-Events fehlen!)"
            }
            parts.joinToString("\n\n").take(maxChars)
        }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
            AppLog.clear(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
