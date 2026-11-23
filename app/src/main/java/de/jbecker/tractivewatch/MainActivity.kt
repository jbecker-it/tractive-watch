package de.jbecker.tractivewatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import de.jbecker.tractivewatch.data.AppLog
import de.jbecker.tractivewatch.data.CrashLog
import de.jbecker.tractivewatch.ui.HomeScreen
import de.jbecker.tractivewatch.ui.LoginScreen
import de.jbecker.tractivewatch.ui.MapScreen
import de.jbecker.tractivewatch.ui.PetScreen
import de.jbecker.tractivewatch.ui.SettingsScreen
import de.jbecker.tractivewatch.ui.TractiveWatchTheme

/**
 * MainActivity (Wear Compose).
 *
 * AppScaffold am Wurzel (TimeText auf allen Screens), SwipeDismissableNavHost
 * für die Navigation (Swipe rechts = zurück). Fünf Routen: login, home,
 * pet/{index}, map/{index}, settings.
 *
 * v1.13: WICHTIG — SwipeDismissableNavHost braucht den WearNavigator, also
 * [rememberSwipeDismissableNavController] und [androidx.wear.compose.navigation.composable].
 * Mit rememberNavController()/navigation.compose.composable (v1.12) bricht der
 * NavHost STILL ab und zeichnet nichts -> schwarzer Screen, "App startet nicht".
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.record(this, "START: MainActivity.onCreate (v${BuildConfig.VERSION_NAME})")

        setContent {
            TractiveWatchTheme {
                AppScaffold {
                    AppNav()
                }
            }
        }
    }
}

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PET = "pet/{index}"
    const val MAP = "map/{index}"
    const val SETTINGS = "settings"

    fun pet(i: Int) = "pet/$i"
    fun map(i: Int) = "map/$i"
}

/** Ersetzt den kompletten Backstack durch [route] (Login <-> Home). */
private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun AppNav() {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val navController = rememberSwipeDismissableNavController()
    val lastCrash = remember { CrashLog.lastSummary(context) }

    // Gespeicherte Session -> direkt Home (wie bis v1.11), sonst Login.
    val start = remember {
        val restore = runCatching { repo.canRestoreSession }.getOrDefault(false)
        AppLog.record(context, "NAV: Start-Route = ${if (restore) Routes.HOME else Routes.LOGIN}")
        if (restore) Routes.HOME else Routes.LOGIN
    }

    val indexArg = listOf(navArgument("index") { type = NavType.IntType })

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = start,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                repo = repo,
                lastCrash = lastCrash,
                onOpenCrashInfo = {
                    context.startActivity(Intent(context, CrashInfoActivity::class.java))
                },
                onLoggedIn = { navController.resetTo(Routes.HOME) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repo = repo,
                onOpenPet = { i -> navController.navigate(Routes.pet(i)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PET, arguments = indexArg) { backStack ->
            val i = backStack.arguments?.getInt("index") ?: 0
            PetScreen(
                repo = repo,
                index = i,
                onOpenMap = { navController.navigate(Routes.map(i)) },
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.MAP, arguments = indexArg) { backStack ->
            val i = backStack.arguments?.getInt("index") ?: 0
            MapScreen(
                repo = repo,
                index = i,
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repo = repo,
                onOpenCrashInfo = {
                    context.startActivity(Intent(context, CrashInfoActivity::class.java))
                },
                onLoggedOut = { navController.resetTo(Routes.LOGIN) },
            )
        }
    }
}
