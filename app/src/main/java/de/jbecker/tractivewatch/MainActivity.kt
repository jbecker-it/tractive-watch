package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.PetScreen
import de.jbecker.tractivewatch.ui.SettingsScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

/**
 * v1.12: MainActivity (Wear Compose).
 *
 * AppScaffold am Wurzel (TimeText auf allen Screens), SwipeDismissableNavHost
 * für die Navigation (Swipe rechts = zurück). Fünf Routen: login, home,
 * pet/{index}, map/{index}, settings.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppLog.record(this, "START: MainActivity.onCreate")

        setContent {
            TractiveWatchTheme {
                AppScaffold {
                    AppNav()
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val context = LocalContext.current
    val repo = remember { Repo(context) }
    val navController = rememberNavController()
    var lastCrash by remember {
        mutableStateOf<String?>(CrashLog.lastSummary(context))
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "login",
    ) {
        composable("login") {
            LoginScreen(
                repo = repo,
                lastCrash = lastCrash,
                onOpenCrashInfo = {
                    context.startActivity(Intent(context, CrashInfoActivity::class.java))
                },
                onLoggedIn = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                repo = repo,
                onOpenPet = { i -> navController.navigate("pet/$i") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            "pet/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { backStack ->
            val i = backStack.arguments?.getInt("index") ?: 0
            PetScreen(
                repo = repo,
                index = i,
                onOpenMap = { navController.navigate("map/$i") },
                onFinished = { navController.popBackStack() },
            )
        }
        composable(
            "map/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { backStack ->
            val i = backStack.arguments?.getInt("index") ?: 0
            MapScreen(
                repo = repo,
                index = i,
                onOpenPet = { navController.navigate("pet/$i") },
                onFinished = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                repo = repo,
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }
    }
}
