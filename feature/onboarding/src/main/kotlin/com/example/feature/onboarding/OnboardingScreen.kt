package com.example.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") { WelcomeScreen(navController) }
        composable("google_signin") { GoogleSignInScreen(navController) }
        composable("contacts_perm") { ContactsPermScreen(navController) }
        composable("sms_perm") { SmsPermScreen(navController) }
        composable("whatsapp_setup") { WhatsAppSetupScreen(navController) }
        composable("battery_opt") { BatteryOptScreen(navController) }
        composable("writing_style") { WritingStyleScreen(navController) }
        composable("automation_prefs") { AutomationPrefsScreen(navController) }
        composable("import_progress") { ImportProgressScreen(onFinish) }
    }
}
