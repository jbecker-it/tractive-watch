package de.jbecker.tractivewatch.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.jbecker.tractivewatch.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Start-Smoke-Test: MainActivity muss ohne gespeicherte Session den
 * Login-Screen sichtbar rendern (Regression v1.12: schwarzer Screen, weil
 * SwipeDismissableNavHost ohne WearNavigator still nichts zeichnete).
 */
@RunWith(AndroidJUnit4::class)
class AppStartTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreenIsShownOnFirstStart() {
        rule.onNodeWithText("Tractive").assertIsDisplayed()
        rule.onNodeWithText("E-Mail eingeben").assertIsDisplayed()
    }

    @Test
    fun loginWithoutInputShowsValidationError() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Anmelden"))
        rule.onNodeWithText("Anmelden").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Bitte E-Mail und Passwort eingeben"))
        rule.onNodeWithText("Bitte E-Mail und Passwort eingeben").assertIsDisplayed()
    }
}
