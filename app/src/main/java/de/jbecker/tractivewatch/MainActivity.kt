package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import de.jbecker.tractivewatch.data.GoogleLoginState
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

enum class Screen { Login, Home, Map }

class MainActivity : ComponentActivity() {

    private val googleState = GoogleLoginState(applicationContext)

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(TRACTIVE_GOOGLE_SERVER_URI)
            .build()
        GoogleSignIn.getClient(this, gso)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate (Prozess-Start)")
        setContent {
            TractiveWatchTheme {
                val repo = remember { Repo(applicationContext) }
                val late = MainActivity.takeLateResult()
                Root(
                    repo = repo,
                    googleState = googleState,
                    lateResult = late,
                    onGoogleLogin = { callback -> startGoogleLogin(callback) },
                )
            }
        }
    }

    /**
     * Startet den Google-Login (Play Services). Der Callback erhält genau eines
     * von (authCode, error) – authCode wird dann an Tractive gesendet.
     */
    fun startGoogleLogin(onResult: (String?, String?) -> Unit) {
        googleCallback = onResult
        googleState.markPending()
        Log.i(TAG, "startGoogleLogin – Sign-In-Intent wird gestartet")
        runCatching {
            startActivity(googleSignInClient.signInIntent)
        }.onFailure { e ->
            Log.e(TAG, "startActivity fehlgeschlagen", e)
            googleState.clear()
            val cb = googleCallback
            googleCallback = null
            cb?.invoke(null, e.message ?: "Google Sign-In nicht verfügbar")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return
        Log.i(TAG, "onActivityResult: resultCode=$resultCode, data=${if (data == null) "null" else "vorhanden"}")
        val cb = googleCallback
        if (cb == null) {
            // Prozess wurde vermutlich neu gestartet → Ergebnis auffangen statt verwerfen
            Log.w(TAG, "Kein Callback mehr vorhanden (Prozess-Neustart?)")
            googleState.clear()
            if (data == null) {
                lateResult = null to "Google-Anmeldung abgebrochen"
            } else {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                task.addOnSuccessListener { account ->
                    val code = account.serverAuthCode
                    lateResult = code to null
                    Log.e(TAG, "Auth-Code nach Neustart erhalten, aber kein Callback – Code wird verworfen: ${code?.take(8)}…")
                }
                task.addOnFailureListener { ex ->
                    val code = (ex as? com.google.android.gms.common.api.ApiException)?.statusCode
                    lateResult = null to "Google-Anmeldung fehlgeschlagen (Fehler ${code ?: "?"})"
                    Log.e(TAG, "Sign-In nach Neustart fehlgeschlagen", ex)
                }
            }
            return
        }
        googleCallback = null
        googleState.clear()
        if (data == null) {
            Log.w(TAG, "Anmeldung abgebrochen (kein Intent-Data)")
            cb(null, "Google-Anmeldung abgebrochen")
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        task.addOnSuccessListener { account ->
            val code = account.serverAuthCode
            Log.i(TAG, "Auth-Code: ${if (code.isNullOrBlank()) "LEER" else code.take(8) + "… (Länge ${code.length})"}")
            if (code.isNullOrBlank()) cb(null, "Kein Auth-Code von Google erhalten")
            else cb(code, null)
        }
        task.addOnFailureListener { e ->
            val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
            Log.e(TAG, "Google-Sign-In fehlgeschlagen: code=$code, message=${e.message}", e)
            cb(null, "Google-Anmeldung fehlgeschlagen (Fehler ${code ?: "?"})")
        }
    }

    companion object {
        private const val TAG = "TractiveWatch"
        private const val RC_GOOGLE_SIGN_IN = 9001

        /** Callback überlebt Activity-Neuerstellungen (gleicher Prozess) */
        @Volatile
        private var googleCallback: ((String?, String?) -> Unit)? = null

        /** Ergebnis, das nach einem Prozess-Neustart einging (Code + Error, genau eines null) */
        @Volatile
        private var lateResult: Pair<String?, String?>? = null

        @Synchronized
        fun takeLateResult(): Pair<String?, String?>? {
            val r = lateResult
            lateResult = null
            return r
        }

        // Server-Callback-URI des Tractive-Google-OAuth-Clients
        // (aus dem offiziellen Tractive-Web-App-Bundle)
        private const val TRACTIVE_GOOGLE_SERVER_URI =
            "https://com.googleusercontent.apps/65204121883-ihhrc2jj6eljii941k85f4lpeorrp62e.apps.googleusercontent.com/"
    }
}

@Composable
fun Root(
    repo: Repo,
    googleState: GoogleLoginState,
    lateResult: Pair<String?, String?>?,
    onGoogleLogin: (callback: (String?, String?) -> Unit) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Login) }
    var petIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (repo.client.hasStoredCredentials && !repo.client.needsReauth) {
            runCatching { repo.loadPets() }
            screen = Screen.Home
        }
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            repo = repo,
            googleState = googleState,
            lateResult = lateResult,
            onLoggedIn = { screen = Screen.Home },
            onGoogleLogin = onGoogleLogin,
        )
        Screen.Home -> HomeScreen(
            repo = repo,
            onOpenPet = { petIndex = it; screen = Screen.Map },
        )
        Screen.Map -> {
            val pet = de.jbecker.tractivewatch.data.TrackerUi.pets.getOrNull(petIndex)
            LaunchedEffect(pet) {
                if (pet == null) screen = Screen.Home
            }
            if (pet != null) {
                MapScreen(repo = repo, pet = pet, onBack = { screen = Screen.Home })
            }
        }
    }
}
