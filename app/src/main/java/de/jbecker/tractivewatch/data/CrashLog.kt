package de.jbecker.tractivewatch.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Speichert Abstürze in crash.log und lädt das Log synchron zu 0x0.st hoch
 * (hält ~1 Jahr, keine Anmeldung nötig). Die URL erscheint im Crash-Info-Screen –
 * damit ist ein Watch-Crash auch ohne PC diagnostizierbar.
 *
 * Alle Funktionen werfen niemals eine Exception (runCatching intern),
 * damit der Fehler-Display-Pfad nie selbst abstürzt.
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val PREFS = "crash_state"
    private const val UPLOAD_URL = "https://0x0.st/"

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
     * Lädt die aktuelle Log-Datei (erneut) hoch und speichert die neue URL.
     * Gibt die URL zurück, null bei Fehlschlag oder fehlender Datei.
     */
    fun reupload(context: Context): String? = runCatching {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = File(appContext.filesDir, FILE_NAME)
        if (!file.exists()) return@runCatching null
        val url = upload(prefs, file.readText())
        if (url != null) prefs.edit().putString("url", url).apply()
        url
    }.getOrNull()

    private fun upload(prefs: SharedPreferences, content: String): String? = runCatching {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "tractive-watch-crash.log",
                content.toRequestBody("text/plain".toMediaType()),
            )
            .build()
        val resp = http.newCall(
            Request.Builder()
                .url(UPLOAD_URL)
                .header("User-Agent", "TractiveWatch-CrashLog/1.5")
                .post(body)
                .build(),
        ).execute()
        resp.use { r ->
            val url = r.body?.string().orEmpty().trim()
            if (url.startsWith("http")) url else null
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

    /** Hochgeladene Crash-Log-URL (0x0.st), null wenn keine */
    fun lastUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("url", null)

    /** Vollständiges Crash-Log von der lokalen Datei (abgeschnitten) */
    fun fileContent(context: Context, maxChars: Int = 6000): String? =
        runCatching { File(context.filesDir, FILE_NAME).readText().take(maxChars) }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
