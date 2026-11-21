package de.jbecker.tractivewatch

import android.app.Application
import de.jbecker.tractivewatch.data.CrashLog

/**
 * Installiert den Crash-Logger so früh wie möglich (vor jeder Activity),
 * damit auch ein Crash beim Activity-Setup aufgezeichnet und hochgeladen wird.
 */
class TractiveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        de.jbecker.tractivewatch.data.AppLog.record(this, "Prozess gestartet (TractiveApp)")
        CrashLog.install(this)
    }
}
