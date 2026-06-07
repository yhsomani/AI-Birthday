package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.contacts.ContactListScreen
import com.example.ui.screens.contacts.ContactDetailScreen
import com.example.ui.screens.events.EventsScreen
import com.example.ui.screens.messages.MessagesScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.analytics.AnalyticsScreen
import com.example.ui.screens.wish.WishPreviewScreen
import com.example.ui.screens.stylecoach.StyleCoachScreen
import com.example.ui.screens.backup.BackupRestoreScreen
import com.example.ui.screens.memoryvault.MemoryVaultScreen
import com.example.ui.screens.giftadvisor.GiftAdvisorScreen

private const val ANIM_DURATION = 300

@Composable
fun RelateNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Splash.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(ANIM_DURATION)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(ANIM_DURATION),
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(ANIM_DURATION)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(ANIM_DURATION),
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(ANIM_DURATION)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(ANIM_DURATION),
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(ANIM_DURATION)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(ANIM_DURATION),
                )
        },
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToContact = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }
        composable(Screen.ContactList.route) {
            ContactListScreen(
                onContactClick = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                }
            )
        }
        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            ContactDetailScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onNavigateToWish = { eventId ->
                    navController.navigate(Screen.WishPreview.createRoute(contactId, eventId))
                },
                onNavigateToMemoryVault = { cid ->
                    navController.navigate(Screen.MemoryVault.createRoute(cid))
                },
                onNavigateToGiftAdvisor = { cid ->
                    navController.navigate(Screen.GiftAdvisor.createRoute(cid))
                }
            )
        }
        composable(
            route = Screen.WishPreview.route,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType },
                navArgument("eventId") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            WishPreviewScreen(
                contactId = contactId,
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onSent = {
                    navController.popBackStack()
                },
            )
        }
        composable(Screen.Events.route) {
            EventsScreen()
        }
        composable(Screen.Messages.route) {
            MessagesScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onSignOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToStyleCoach = {
                    navController.navigate(Screen.StyleCoach.route)
                },
                onNavigateToBackupRestore = {
                    navController.navigate(Screen.BackupRestore.route)
                }
            )
        }
        composable(Screen.Analytics.route) {
            AnalyticsScreen()
        }
        composable(Screen.StyleCoach.route) {
            StyleCoachScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.MemoryVault.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            MemoryVaultScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.GiftAdvisor.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
            GiftAdvisorScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
