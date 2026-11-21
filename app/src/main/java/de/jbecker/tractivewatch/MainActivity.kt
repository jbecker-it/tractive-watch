package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
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
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

enum class Screen { Login, Home, Map }

class MainActivity : ComponentActivity() {

    private var googleCallback: ((String?, String?) -> Unit)? = null

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(TRACTIVE_GOOGLE_SERVER_URI)
            .build()
        GoogleSignIn.getClient(this, gso)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TractiveWatchTheme {
                val repo = remember { Repo(applicationContext) }
                Root(
                    repo = repo,
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
        runCatching {
            startActivity(googleSignInClient.signInIntent)
        }.onFailure { e ->
            val cb = googleCallback
            googleCallback = null
            cb?.invoke(null, e.message ?: "Google Sign-In nicht verfügbar")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return
        val cb = googleCallback ?: return
        googleCallback = null
        if (data == null) {
            cb(null, "Google-Anmeldung abgebrochen")
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        task.addOnSuccessListener { account ->
            val code = account.serverAuthCode
            if (code.isNullOrBlank()) cb(null, "Kein Auth-Code von Google erhalten")
            else cb(code, null)
        }
        task.addOnFailureListener { e ->
            val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
            cb(null, "Google-Anmeldung fehlgeschlagen (Fehler $code)")
        }
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001

        // Server-Callback-URI des Tractive-Google-OAuth-Clients
        // (aus dem offiziellen Tractive-Web-App-Bundle)
        private const val TRACTIVE_GOOGLE_SERVER_URI =
            "https://com.googleusercontent.apps/65204121883-ihhrc2jj6eljii941k85f4lpeorrp62e.apps.googleusercontent.com/"
    }
}

@Composable
fun Root(repo: Repo, onGoogleLogin: (callback: (String?, String?) -> Unit) -> Unit) {
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
