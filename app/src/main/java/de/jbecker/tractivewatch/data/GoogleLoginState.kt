package de.jbecker.tractivewatch.data

import android.content.Context

/**
 * Persistiert den Zustand "Google-Anmeldung läuft".
 *
 * Wichtig: Wear-OS killt Apps unter Speicheldruck gern — wenn der Prozess während
 * des Google-Sign-Ins stirbt, zeigt die UI bei Neustart weiterhin "läuft…"
 * (mit Abbrechen-Option) statt in einen unklaren Zustand zu fallen.
 */
class GoogleLoginState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("google_login_state", Context.MODE_PRIVATE)

    val pending: Boolean
        get() = prefs.getBoolean("pending", false)

    /** true, wenn der Pending-Zustand länger als 10 Minuten alt ist */
    val isStale: Boolean
        get() = pending &&
            System.currentTimeMillis() - prefs.getLong("pending_since", 0L) > 10 * 60_000L

    fun markPending() {
        prefs.edit()
            .putBoolean("pending", true)
            .putLong("pending_since", System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().remove("pending").remove("pending_since").apply()
    }
}
