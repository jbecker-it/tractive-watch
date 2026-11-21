package de.jbecker.tractivewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.jbecker.tractivewatch.data.TrackerUi
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

enum class Screen { Login, Home, Map }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TractiveWatchTheme {
                val repo = remember { Repo(applicationContext) }
                Root(repo)
            }
        }
    }
}

@Composable
fun Root(repo: Repo) {
    var screen by remember { mutableStateOf(Screen.Login) }
    var petIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (repo.client.hasStoredCredentials) {
            runCatching { repo.loadPets() }
            screen = Screen.Home
        }
    }

    when (screen) {
        Screen.Login -> LoginScreen(repo = repo, onLoggedIn = { screen = Screen.Home })
        Screen.Home -> HomeScreen(
            repo = repo,
            onOpenPet = { petIndex = it; screen = Screen.Map },
        )
        Screen.Map -> {
            val pet = TrackerUi.pets.getOrNull(petIndex)
            LaunchedEffect(pet) {
                if (pet == null) screen = Screen.Home
            }
            if (pet != null) {
                MapScreen(repo = repo, pet = pet, onBack = { screen = Screen.Home })
            }
        }
    }
}
