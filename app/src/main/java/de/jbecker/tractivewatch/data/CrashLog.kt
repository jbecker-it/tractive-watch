package de.jbecker.tractivewatch.data

import android.content.Context
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
 * (hält ~1 Jahr, keine Anmeldung nötig). Die URL erscheint auf dem
 * Login-Screen – damit ist ein Watch-Crash auch ohne PC diagnostizierbar.
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val PREFS = "crash_state"
    private const val UPLOAD_URL = "https://0x0.st/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Installiert einen globalen Exception-Handler (vor setContent aufrufen). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handle(appContext, thread, throwable)
            // System-Handling beibehalten (Crash-Dialog), wir loggen nur mit
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun handle(context: Context, thread: Thread, t: Throwable) {
        val sb = StringBuilder()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(Date())
        sb.append(time).append(" | Thread: ").append(thread.name).append('\n')
        sb.append(t).append('\n')
        t.stackTraceToString().lines().take(40).forEach { sb.append(it).append('\n') }
        var cause = t.cause
        var depth = 0
        while (cause != null && depth < 5) {
            sb.append("Caused by: ").append(cause).append('\n')
            cause.stackTraceToString().lines().take(15).forEach { sb.append("  ").append(it).append('\n') }
            cause = cause.cause
            depth++
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = File(context.filesDir, FILE_NAME)

        // Nur hochladen, wenn es noch keine (ältere) URL gibt
        if (prefs.getString("url", null) == null) {
            runCatching {
                file.writeText(sb.toString())
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "tractive-watch-crash.log",
                        sb.toString().toRequestBody("text/plain".toMediaType()),
                    )
                    .build()
                val resp = http.newCall(
                    Request.Builder().url(UPLOAD_URL).post(body).build(),
                ).execute()
                resp.use { r ->
                    val url = r.body?.string().orEmpty().trim()
                    if (url.startsWith("http")) {
                        prefs.edit().putString("url", url).apply()
                    }
                }
            }
        }

        runCatching {
            prefs.edit()
                .putString("summary", "${t.javaClass.simpleName}: ${t.message ?: "(keine Meldung)"}")
                .putLong("time", System.currentTimeMillis())
                .apply()
        }
    }

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
    fun fileContent(context: Context, maxChars: Int = 2500): String? =
        runCatching { File(context.filesDir, FILE_NAME).readText().take(maxChars) }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
