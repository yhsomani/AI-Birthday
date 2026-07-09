package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.example.domain.navigation.RelateDeepLinks
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.events.EventsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.analytics.AnalyticsScreen
import com.example.ui.screens.activity.ActivityHistoryScreen
import com.example.ui.screens.stylecoach.StyleCoachScreen
import com.example.ui.screens.backup.BackupRestoreScreen
import com.example.ui.screens.setup.AutomationSetupScreen

private const val ANIM_DURATION = 300

@Composable
fun RelateNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Splash.route,
    isSignedIn: () -> Boolean = { FirebaseAuth.getInstance().currentUser != null },
) {
    var postAuthDestination by rememberSaveable { mutableStateOf(Screen.Home.route) }

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
                onNavigateToHome = {
                    if (navController.currentDestination?.route == Screen.Splash.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToOnboarding = {
                    if (navController.currentDestination?.route == Screen.Splash.route) {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToAuth = {
                    if (navController.currentDestination?.route == Screen.Splash.route) {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOpenAutomationSetup = {
                    postAuthDestination = Screen.AutomationSetup.route
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onOnboardingComplete = {
                    postAuthDestination = Screen.Home.route
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onStartLocalModeComplete = {
                    postAuthDestination = Screen.Home.route
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthComplete = {
                    val destination = postAuthDestination
                    postAuthDestination = Screen.Home.route
                    navController.navigate(destination) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        authenticatedComposable(
            route = Screen.Home.route,
            navController = navController,
            isSignedIn = isSignedIn,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = RelateDeepLinks.Home.pattern
                }
            )
        ) {
            HomeScreen(
                onNavigateToContact = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAnalytics = {
                    navController.navigate(Screen.Analytics.route)
                },
                onNavigateToActivityHistory = {
                    navController.navigate(Screen.ActivityHistory.route)
                },
                onNavigateToStyleCoach = {
                    navController.navigate(Screen.StyleCoach.route)
                },
                onNavigateToBackupRestore = {
                    navController.navigate(Screen.BackupRestore.route)
                },
                onNavigateToAutomationSetup = {
                    navController.navigate(Screen.AutomationSetup.route)
                },
                onNavigateToMessages = {
                    navController.navigate(Screen.Messages.route)
                },
            )
        }
        relationshipDestinations(
            navController = navController,
            isSignedIn = isSignedIn,
        )
        authenticatedComposable(
            route = Screen.Events.route,
            navController = navController,
            isSignedIn = isSignedIn,
        ) {
            EventsScreen()
        }
        messagesDestinations(
            navController = navController,
            isSignedIn = isSignedIn,
        )
        authenticatedComposable(
            route = Screen.Settings.route,
            navController = navController,
            isSignedIn = isSignedIn,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = RelateDeepLinks.Settings.pattern
                }
            )
        ) {
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
                },
                onNavigateToAutomationSetup = {
                    navController.navigate(Screen.AutomationSetup.route)
                },
                onNavigateToActivityHistory = {
                    navController.navigate(Screen.ActivityHistory.route)
                }
            )
        }
        authenticatedComposable(
            route = Screen.Analytics.route,
            navController = navController,
            isSignedIn = isSignedIn,
        ) {
            AnalyticsScreen(
                onNavigateToActivityHistory = {
                    navController.navigate(Screen.ActivityHistory.route)
                },
                onNavigateToContact = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                },
            )
        }
        authenticatedComposable(
            route = Screen.ActivityHistory.route,
            navController = navController,
            isSignedIn = isSignedIn,
        ) {
            ActivityHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenRoute = { route -> navController.navigate(route) },
            )
        }
        authenticatedComposable(
            route = Screen.StyleCoach.route,
            navController = navController,
            isSignedIn = isSignedIn,
        ) {
            StyleCoachScreen(
                onBack = { navController.popBackStack() }
            )
        }
        authenticatedComposable(
            route = Screen.BackupRestore.route,
            navController = navController,
            isSignedIn = isSignedIn,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = RelateDeepLinks.BackupRestore.pattern
                }
            )
        ) {
            BackupRestoreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        authenticatedComposable(
            route = Screen.AutomationSetup.route,
            navController = navController,
            isSignedIn = isSignedIn,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = RelateDeepLinks.AutomationSetup.pattern
                }
            )
        ) {
            AutomationSetupScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    if (!navController.popBackStack(Screen.Settings.route, inclusive = false)) {
                        navController.navigate(Screen.Settings.route)
                    }
                },
                onOpenStyleCoach = {
                    navController.navigate(Screen.StyleCoach.route)
                },
                onOpenContacts = {
                    navController.navigate(Screen.ContactList.route)
                },
                onOpenMessages = { channelFilter ->
                    navController.navigate(
                        channelFilter?.let { Screen.Messages.createFilteredRoute(it.name) }
                            ?: Screen.Messages.route,
                    )
                },
                onOpenActivityHistory = {
                    navController.navigate(Screen.ActivityHistory.route)
                },
            )
        }
    }
}
