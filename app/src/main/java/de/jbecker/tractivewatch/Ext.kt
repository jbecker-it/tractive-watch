package de.jbecker.tractivewatch

import de.jbecker.tractivewatch.data.NoCredentialsError
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
    is UnknownHostException, is SocketTimeoutException -> "Keine Netzverbindung"
    is SSLException -> "SSL-Fehler"
    is IOException -> "Netzwerkfehler"
    else -> message ?: "Unbekannter Fehler"
}

fun fmtTime(t: String?): String {
    if (t.isNullOrBlank()) return ""
    return runCatching {
        val time = java.time.OffsetDateTime.parse(t).toLocalTime()
        String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
    }.getOrDefault(t)
}

fun fmtAccuracy(m: Double?): String {
    if (m == null) return ""
    return if (m >= 1000.0) {
        String.format(Locale.getDefault(), "± %.1f km", m / 1000.0)
    } else {
        "± ${m.toInt()} m"
    }
}
