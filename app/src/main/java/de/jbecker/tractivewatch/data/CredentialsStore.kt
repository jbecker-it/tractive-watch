package de.jbecker.tractivewatch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences = openEncrypted(context)
        ?: run {
            // Keystore-Schlüssel passt nicht mehr zur Datei (z. B. nach Restore):
            // kaputte Datei verwerfen und neu anlegen statt Klartext zu lesen.
            context.deleteSharedPreferences(FILE)
            openEncrypted(context)
        }
        // Letzter Ausweg (Keystore defekt): eigene Datei, nie die verschlüsselte.
        ?: context.getSharedPreferences("${FILE}_fallback", Context.MODE_PRIVATE)

    private fun openEncrypted(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { it.getString("provider", null) } // Entschlüsselung früh testen
    }.getOrNull()

    private companion object {
        const val FILE = "tractive_secure_prefs"
    }

    /** "email" oder "google" – welcher Login-Provider zuletzt genutzt wurde */
    val provider: String?
        get() = prefs.getString("provider", null)

    val email: String?
        get() = prefs.getString("email", null)

    val password: String?
        get() = prefs.getString("password", null)

    fun save(email: String, password: String) {
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .putString("provider", "email")
            .apply()
    }

    fun markGoogle() {
        prefs.edit().putString("provider", "google").apply()
    }

    val userId: String?
        get() = prefs.getString("user_id", null)

    val accessToken: String?
        get() = prefs.getString("access_token", null)

    val tokenExpiresAt: Long?
        get() = prefs.getLong("token_expires_at", 0L).takeIf { it > 0L }

    fun setSession(userId: String?, token: String, expiresAt: Long) {
        prefs.edit()
            .putString("user_id", userId)
            .putString("access_token", token)
            .putLong("token_expires_at", expiresAt)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
