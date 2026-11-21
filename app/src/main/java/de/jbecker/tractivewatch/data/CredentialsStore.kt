package de.jbecker.tractivewatch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialsStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tractive_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("tractive_secure_prefs", Context.MODE_PRIVATE)
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
