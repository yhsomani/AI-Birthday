package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.R
import com.example.core.prefs.SecurePrefs
import com.google.firebase.auth.FirebaseAuth
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationSmokeTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        runCatching {
            SecurePrefs(context).apply {
                setBiometricLockEnabled(false)
                setGuestMode(false)
            }
        }
    }

    @Test
    fun freshInstallFlow_showsOnboardingAndAuthActions() {
        seedSignedOutFreshInstall()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(context.getString(R.string.onboarding_setup_title))
            composeRule.onNodeWithText(context.getString(R.string.onboarding_continue_to_sign_in))
                .performClick()

            waitForText(context.getString(R.string.auth_sign_in_google))
            composeRule.onNodeWithText(context.getString(R.string.auth_dev_bypass))
                .assertIsDisplayed()
        }
    }

    @Test
    fun guestModeAppShell_bottomNavigationOpensPrimaryScreens() {
        seedGuestAppShell()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(context.getString(R.string.nav_home))
            clickIfVisible(context.getString(R.string.permission_rationale_not_now))

            listOf(
                R.string.nav_contacts,
                R.string.nav_events,
                R.string.nav_messages,
                R.string.analytics,
                R.string.nav_home,
            ).forEach { labelRes ->
                val label = context.getString(labelRes)
                composeRule.onNodeWithText(label).performClick()
                waitForText(label)
            }
        }
    }

    private fun seedSignedOutFreshInstall() {
        FirebaseAuth.getInstance().signOut()
        SecurePrefs(context).apply {
            clearAll()
            setBiometricLockEnabled(false)
        }
    }

    private fun seedGuestAppShell() {
        FirebaseAuth.getInstance().signOut()
        SecurePrefs(context).apply {
            clearAll()
            setOnboardingComplete(true)
            setGuestMode(true)
            setBiometricLockEnabled(false)
            setQuietHoursStart(0)
            setQuietHoursEnd(0)
            setBlackoutDates("[]")
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 8_000L) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun clickIfVisible(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            composeRule.onNodeWithText(text).performClick()
        }
    }
}
