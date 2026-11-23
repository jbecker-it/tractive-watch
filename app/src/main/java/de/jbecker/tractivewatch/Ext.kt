package de.jbecker.tractivewatch

import de.jbecker.tractivewatch.data.NoCredentialsError
import de.jbecker.tractivewatch.data.ReauthRequiredError
import de.jbecker.tractivewatch.data.TractiveApiError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

fun Throwable.friendlyMessage(): String = when (this) {
    is TractiveApiError -> when (code) {
        401, 403 -> "Anmeldung fehlgeschlagen – bitte Zugangsdaten prüfen"
        429 -> "Zu viele Anfragen, bitte kurz warten"
        else -> "Tractive-Fehler $code: $apiMessage"
    }
    is NoCredentialsError -> "Bitte zuerst anmelden"
    is ReauthRequiredError -> "Anmeldung abgelaufen – bitte ab- und erneut anmelden"
    is UnknownHostException, is SocketTimeoutException -> "Keine Netzverbindung"
    is SSLException -> "SSL-Fehler"
    is IOException -> "Netzwerkfehler"
    else -> message ?: "Unbekannter Fehler"
}

fun fmtTime(t: String?): String {
    if (t.isNullOrBlank()) return ""
    val s = t.trim()
    // Tractive liefert Unix-Epochen (Sekunden oder Millisekunden) als String
    if (s.all { it.isDigit() } && s.length in 9..16) {
        val ms = if (s.length >= 13) s.toLongOrNull() else s.toLongOrNull()?.times(1000L)
        if (ms != null) {
            return runCatching {
                val zdt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
                val time = String.format(Locale.getDefault(), "%02d:%02d", zdt.hour, zdt.minute)
                if (zdt.toLocalDate() == java.time.ZonedDateTime.now().toLocalDate()) {
                    time
                } else {
                    String.format(Locale.getDefault(), "%d.%02d. %s", zdt.dayOfMonth, zdt.monthValue, time)
                }
            }.getOrDefault(s)
        }
    }
    return runCatching {
        val time = java.time.OffsetDateTime.parse(s).toLocalTime()
        String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
    }.getOrDefault(s)
}

fun fmtAccuracy(m: Double?): String {
    if (m == null) return ""
    return if (m >= 1000.0) {
        String.format(Locale.getDefault(), "± %.1f km", m / 1000.0)
    } else {
        "± ${m.toInt()} m"
    }
}

/** "gerade eben" / "vor 3 Min" / "vor 2 Std" — relative Zeit für Positionen. */
fun relativeTime(epochSeconds: Long): String {
    val mins = ((System.currentTimeMillis() / 1000 - epochSeconds) / 60).coerceAtLeast(0)
    return when {
        mins < 1 -> "gerade eben"
        mins < 60 -> "vor $mins Min"
        else -> "vor ${mins / 60} Std"
    }
}
